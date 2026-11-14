# -*- coding: utf-8 -*-
"""
web_search.py - 联网搜索（provider 抽象 + 结果清洗 + 短缓存）
------------------------------------------------
为什么放在 Python 而不是 Java 的工具网关：
    搜索服务的密钥配在 RAG 的 .env 里（和 DEEPSEEK_API_KEY 同一处），
    Java 进程读不到它。让 Java 去读环境变量会得到空串，然后报"密钥无效"，
    排查起来非常费劲。

⚠️ 外部网页摘要是**不可信输入**。这里只做第一层清洗（剥标签、限长、只留 http/https），
   第二层是 agent 里给结果套 `<external_untrusted>` 定界标记 + system prompt 声明，
   第三层是前端 MarkdownRenderer 的 sanitize。三层缺一不可（设计文档 §9.3）。

⚠️ 搜索失败必须**如实失败**：返回 {"ok": False, "error": ...}，
   让模型说"没能联网"。绝不静默返回空结果冒充"没搜到"——
   那会让模型拿自己的记忆当联网结果，是最坏的幻觉来源。
"""
import re
import threading
import time
from typing import Any
from urllib.parse import urlsplit

import requests

import config

# 每条摘要上限。5 条 × 300 字 = 1500 字，塞进上下文不会顶爆
SNIPPET_MAX = 300
# 搜索词上限，防止模型把整段正文当搜索词发出去（也是隐私边界）
QUERY_MAX = 200

# 剥标签：先用块级正则干掉脚本/样式/框架，再干掉剩余标签
_BLOCK_RE = re.compile(r"<(script|style|iframe|noscript|object|embed)\b[^>]*>.*?</\1\s*>",
                       re.I | re.S)
_UNCLOSED_BLOCK_RE = re.compile(r"<(script|style|iframe|noscript)\b[^>]*>", re.I)
_TAG_RE = re.compile(r"<[^>]*>")
# 事件属性残片（标签已被剥掉，这里只兜住形如 onerror= 的裸文本）
_EVENT_ATTR_RE = re.compile(r"\bon[a-z]+\s*=", re.I)
_WS_RE = re.compile(r"\s+")
_CTRL_RE = re.compile(r"[\x00-\x08\x0b\x0c\x0e-\x1f\x7f]")

# 缓存：query → (过期时间, 结果列表)。进程内 dict + 锁足够，
# 多实例各存各的，最多多花几次搜索费，不值得为此引入 Redis。
_cache: dict[str, tuple[float, list[dict]]] = {}
_cache_lock = threading.Lock()
_CACHE_MAX_ENTRIES = 200


def clean_text(raw: Any) -> str:
    """
    把外部 HTML 洗成纯文本。

    顺序很关键：先反转义再剥标签。反过来的话 `&lt;script&gt;` 会绕过正则活下来，
    到前端才变成真标签 —— 这正是 PDF 注入那次踩过的形态。
    """
    if raw is None:
        return ""
    text = str(raw)
    # 反转义要在剥标签之前做，但反转义可能产生新的标签，所以后面还要再剥一次
    import html as _html

    text = _html.unescape(text)
    text = _BLOCK_RE.sub(" ", text)
    text = _UNCLOSED_BLOCK_RE.sub(" ", text)
    text = _TAG_RE.sub(" ", text)
    text = _EVENT_ATTR_RE.sub(" ", text)
    # 剥完标签可能又露出实体（比如 &amp;lt;），再转一次；只剩纯文本就没有标签可剥了
    text = _html.unescape(text)
    text = _CTRL_RE.sub("", text)
    text = _WS_RE.sub(" ", text)
    return text.strip()


def is_allowed_url(url: Any) -> bool:
    """只放行 http/https。javascript:/data:/file: 一律丢弃（服务端第一道，前端还会再校验一次）。"""
    if not url or not isinstance(url, str):
        return False
    try:
        parts = urlsplit(url.strip())
    except ValueError:
        return False
    return parts.scheme.lower() in config.WEB_SEARCH_ALLOWED_SCHEMES and bool(parts.netloc)


def domain_of(url: str) -> str:
    """
    从 URL 解析域名，**在服务端算好**再下发。

    为什么不交给前端：外部结果经常缺协议头、带端口、大小写混写，
    前端解析失败是静默的（显示空白），排查时根本看不出哪里错了。
    """
    if not url:
        return ""
    raw = url.strip()
    if "://" not in raw:
        raw = "https://" + raw
    try:
        host = urlsplit(raw).hostname or ""
    except ValueError:
        return ""
    host = host.lower().strip(".")
    if host.startswith("www."):
        host = host[4:]
    return host


# ------------------------------------------------------------------
# provider 实现
# ------------------------------------------------------------------

def _key_for(provider: str) -> str:
    """provider 专属 key 优先，回落到全局 key。"""
    import os

    specific = os.getenv(f"WEB_SEARCH_API_KEY_{provider.upper()}", "").strip()
    return specific or config.WEB_SEARCH_API_KEY


# 各 provider 的默认端点。换厂商/自建代理时用 WEB_SEARCH_BASE_URL_{PROVIDER} 覆盖，
# 单 provider 部署也可以直接用全局 WEB_SEARCH_BASE_URL（多 provider 时它会作用到每一个，
# 所以那种情况请用带后缀的那个）。
_DEFAULT_ENDPOINT = {
    "tavily": "https://api.tavily.com/search",
    "bocha": "https://api.bochaai.com/v1/web-search",
}


def _endpoint(provider: str) -> str:
    import os

    for name in (f"WEB_SEARCH_BASE_URL_{provider.upper()}", "WEB_SEARCH_BASE_URL"):
        value = os.getenv(name, "").strip()
        if value:
            return value
    return _DEFAULT_ENDPOINT[provider]


def _call_tavily(query: str, limit: int, key: str) -> list[dict]:
    resp = requests.post(
        _endpoint("tavily"),
        json={"api_key": key, "query": query, "max_results": limit,
              "include_answer": False, "search_depth": "basic"},
        timeout=config.WEB_SEARCH_TIMEOUT,
        headers={"Content-Type": "application/json"},
    )
    if resp.status_code != 200:
        raise RuntimeError(f"tavily HTTP {resp.status_code}")
    data = resp.json() or {}
    out = []
    for r in (data.get("results") or [])[:limit]:
        out.append({
            "title": r.get("title") or "",
            "url": r.get("url") or "",
            "snippet": r.get("content") or "",
            "publishedAt": r.get("published_date") or "",
        })
    return out


def _call_bocha(query: str, limit: int, key: str) -> list[dict]:
    resp = requests.post(
        _endpoint("bocha"),
        json={"query": query, "count": limit, "summary": True},
        timeout=config.WEB_SEARCH_TIMEOUT,
        headers={"Content-Type": "application/json",
                 "Authorization": f"Bearer {key}"},
    )
    if resp.status_code != 200:
        raise RuntimeError(f"bocha HTTP {resp.status_code}")
    data = resp.json() or {}
    pages = ((data.get("data") or {}).get("webPages") or {}).get("value") or []
    out = []
    for r in pages[:limit]:
        out.append({
            "title": r.get("name") or "",
            "url": r.get("url") or "",
            # 博查的 summary 字段是 AI 摘要，比 snippet 更长更好用
            "snippet": r.get("summary") or r.get("snippet") or "",
            "publishedAt": r.get("datePublished") or r.get("dateLastCrawled") or "",
        })
    return out


_PROVIDERS = {
    "tavily": _call_tavily,
    "bocha": _call_bocha,
}


# ------------------------------------------------------------------
# 对外
# ------------------------------------------------------------------

def available() -> bool:
    """
    是否具备联网搜索能力：开关打开 + 至少一个 provider 有 key + provider 名字认得出。

    这个函数决定 web_search 工具**要不要出现在 tools 列表里**。返回 False 时
    模型完全看不到这个工具，也就不会浪费一次决策去调一个注定失败的接口。
    """
    if not config.WEB_SEARCH_ENABLED:
        return False
    for p in config.WEB_SEARCH_PROVIDERS:
        if p in _PROVIDERS and _key_for(p):
            return True
    return False


def _cache_get(key: str) -> list[dict] | None:
    now = time.time()
    with _cache_lock:
        item = _cache.get(key)
        if not item:
            return None
        expire_at, value = item
        if expire_at < now:
            _cache.pop(key, None)
            return None
        return value


def _cache_put(key: str, value: list[dict]) -> None:
    ttl = max(0, config.WEB_SEARCH_CACHE_SECONDS)
    if ttl == 0:
        return
    with _cache_lock:
        if len(_cache) >= _CACHE_MAX_ENTRIES:
            # 简单粗暴：清掉一半。比实现 LRU 划算，反正是省钱用的旁路缓存
            for k in list(_cache.keys())[: _CACHE_MAX_ENTRIES // 2]:
                _cache.pop(k, None)
        _cache[key] = (time.time() + ttl, value)


def clear_cache() -> None:
    with _cache_lock:
        _cache.clear()


def search(query: str, limit: int = 5) -> dict:
    """
    执行一次联网搜索。永不抛异常，失败返回 ok=False。

    返回结构与其它工具一致：{ok, digest, data:{items}, sources}
    """
    q = clean_text(query)[:QUERY_MAX].strip()
    if not q:
        return {"ok": False, "error": "query 不能为空"}
    try:
        limit = max(1, min(8, int(limit)))
    except (TypeError, ValueError):
        limit = 5

    if not config.WEB_SEARCH_ENABLED:
        return {"ok": False, "error": "联网搜索已被管理员关闭"}

    cache_key = f"{q.lower()}|{limit}"
    cached = _cache_get(cache_key)
    if cached is not None:
        return _build_result(q, cached, cached=True)

    errors = []
    raw_hits: list[dict] = []
    for provider in config.WEB_SEARCH_PROVIDERS:
        fn = _PROVIDERS.get(provider)
        if fn is None:
            errors.append(f"{provider}: 未知 provider")
            continue
        key = _key_for(provider)
        if not key:
            errors.append(f"{provider}: 未配置密钥")
            continue
        try:
            raw_hits = fn(q, limit, key)
            if raw_hits:
                break
            errors.append(f"{provider}: 无结果")
        except Exception as e:
            # 超时是最常见的一种：3 秒没回就换下一个，不在这里重试
            errors.append(f"{provider}: {type(e).__name__}")
            print(f"[WebSearch] {provider} 失败（换下一个）: {e}")

    items = _normalize(raw_hits, limit)
    if not items:
        # 全链路失败 → 如实失败。让模型说"没能联网"，而不是拿记忆冒充搜索结果
        return {"ok": False, "error": "联网搜索不可用（" + "；".join(errors[:3]) + "）"}

    _cache_put(cache_key, items)
    return _build_result(q, items)


def _normalize(raw_hits: list[dict], limit: int) -> list[dict]:
    """清洗 + 过滤 + 去重，只留前端能安全渲染的字段。"""
    items: list[dict] = []
    seen: set[str] = set()
    for r in raw_hits:
        url = (r.get("url") or "").strip()
        if not is_allowed_url(url):
            continue
        # 去重按 URL（去掉 #锚点 与末尾斜杠），不是按标题 —— 同一标题不同站点是正常的
        dedup_key = url.split("#", 1)[0].rstrip("/").lower()
        if dedup_key in seen:
            continue
        seen.add(dedup_key)

        snippet = clean_text(r.get("snippet"))
        if len(snippet) > SNIPPET_MAX:
            snippet = snippet[:SNIPPET_MAX] + "…"
        items.append({
            "title": clean_text(r.get("title"))[:120] or domain_of(url) or "未命名",
            "url": url,
            # 域名必须服务端算好：外部结果常缺协议头或带端口，前端解析会静默失败
            "domain": domain_of(url),
            "snippet": snippet,
            "publishedAt": clean_text(r.get("publishedAt"))[:32],
        })
        if len(items) >= limit:
            break
    return items


def _build_result(query: str, items: list[dict], cached: bool = False) -> dict:
    domains = []
    for it in items:
        if it["domain"] and it["domain"] not in domains:
            domains.append(it["domain"])
    digest = f"联网搜索「{query}」命中 {len(items)} 条"
    if domains:
        digest += "，来源：" + "、".join(domains[:3])
    if cached:
        digest += "（缓存）"

    sources = [{
        "type": "web",
        "title": it["title"],
        "url": it["url"],
        "domain": it["domain"],
    } for it in items]

    return {"ok": True, "digest": digest, "data": {"items": items}, "sources": sources}
