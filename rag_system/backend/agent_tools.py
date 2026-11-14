# -*- coding: utf-8 -*-
"""
agent_tools.py - Agent 可调用的工具定义与执行
------------------------------------------------
两类工具，边界很明确：

  * 站内文章类（search_articles / get_article / related_articles /
    list_taxonomy / site_stats）→ 全部转发给 Java 的 `/internal/ai/tool/{name}`。
    **不在 Python 里直连 MySQL**：草稿可见性、阅读数打点、缓存失效都在
    ArticleService 里，绕过去就等于绕过了这些规则。

  * 知识库检索（search_knowledge）→ 本进程内直接查 ChromaDB，省一次 HTTP 往返。

失败的统一形态是 {"ok": false, "error": "..."} 的 JSON 字符串，回填到 role=tool，
让模型自己决定重试还是换个工具 —— 直接抛异常打断循环，用户只会看到"服务异常"。
"""
import json
from typing import Any

import requests

import config
import web_search

# tools schema。description 比参数更重要 —— 模型选不选这个工具全看描述准不准，
# 写得太含糊（"搜索"）它就会在该查站内的时候跑去乱猜。
TOOLS: list[dict[str, Any]] = [
    {
        "type": "function",
        "function": {
            "name": "search_articles",
            "description": "按关键词检索本站已发布的博客文章，返回标题、摘要、分类、标签和链接。"
                           "用户问「你写过什么关于X的文章」「站内有讲X的吗」时使用。",
            "parameters": {
                "type": "object",
                "properties": {
                    "q": {"type": "string", "description": "检索关键词，中文，2-8 个字"},
                    "tag": {"type": "string", "description": "可选，标签名，用于收窄范围"},
                    "category": {"type": "string", "description": "可选，分类名，用于收窄范围"},
                    "limit": {"type": "integer", "description": "返回条数，默认 5，最多 8", "default": 5},
                },
                "required": ["q"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "get_article",
            "description": "读取本站某篇文章的正文。已知文章 id 或 slug 时使用，"
                           "通常在 search_articles 之后用来深入阅读。",
            "parameters": {
                "type": "object",
                "properties": {
                    "id": {"type": "integer", "description": "文章 id"},
                    "slug": {"type": "string", "description": "文章 slug（URL 里 /post/ 后面那段）"},
                },
                "required": [],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "related_articles",
            "description": "取某篇文章的相关推荐。用户读完一篇想继续看，或问「还有类似的吗」时使用。",
            "parameters": {
                "type": "object",
                "properties": {
                    "id": {"type": "integer", "description": "文章 id"},
                    "limit": {"type": "integer", "description": "返回条数，默认 4", "default": 4},
                },
                "required": ["id"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "list_taxonomy",
            "description": "列出本站的分类或标签及其文章数。不确定该用什么分类/标签名时，先调这个。",
            "parameters": {
                "type": "object",
                "properties": {
                    "type": {
                        "type": "string",
                        "enum": ["category", "tag"],
                        "description": "category=分类，tag=标签",
                    }
                },
                "required": ["type"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "site_stats",
            "description": "查询本站规模：文章总数、分类数、标签数、最新文章日期。"
                           "用户问「你写了多少篇」这类统计时使用。",
            "parameters": {"type": "object", "properties": {}, "required": []},
        },
    },
    {
        "type": "function",
        "function": {
            "name": "search_knowledge",
            "description": "在本站知识库（文章正文 + 作者上传的 PDF 文档）里做语义检索，"
                           "返回最相关的原文片段。回答概念、原理、操作步骤类问题时优先用它找依据。",
            "parameters": {
                "type": "object",
                "properties": {
                    "query": {"type": "string", "description": "检索语句，可以是一句完整的问题"},
                    "top_k": {"type": "integer", "description": "返回片段数，默认 5", "default": 5},
                },
                "required": ["query"],
            },
        },
    },
]

# 联网搜索**单独定义**，由 available_tools() 按配置决定挂不挂 ——
# 没配密钥时挂在列表里，模型每次决策都会看到一个必然失败的工具，
# 既浪费 token 又会让它把"没搜到"当成"网上没有"。
WEB_SEARCH_TOOL: dict[str, Any] = {
    "type": "function",
    "function": {
        "name": "web_search",
        "description": "联网搜索公开网页，返回标题、域名和摘要。"
                       "只在两种情况用：① 站内资料明显不覆盖（新技术、新版本、外部事件）；"
                       "② 用户明确要求联网核实。站内问题先用 search_articles / search_knowledge，"
                       "能答就不要联网。",
        "parameters": {
            "type": "object",
            "properties": {
                "query": {"type": "string", "description": "搜索词，中文，尽量具体，不要超过 50 字"},
                "limit": {"type": "integer", "description": "返回条数，默认 5，最多 8", "default": 5},
            },
            "required": ["query"],
        },
    },
}


def web_available() -> bool:
    """当前是否真的具备联网能力（开关 + 至少一个 provider 有 key）。"""
    try:
        return web_search.available()
    except Exception:
        return False


def available_tools(allow_web: bool = True) -> list[dict[str, Any]]:
    """
    本轮要下发给模型的工具列表。

    allow_web=False 用于"本轮已经用满联网次数"，此时把工具摘掉而不是让它调用后报错 ——
    模型看不到工具就不会尝试，省一次决策往返。
    """
    tools = list(TOOLS)
    if allow_web and web_available():
        tools.append(WEB_SEARCH_TOOL)
    return tools

# 由 main.py 启动时注入（本进程内的 ChromaDB 实例）
_vector_store = None


def bind_vector_store(store) -> None:
    global _vector_store
    _vector_store = store


def _json(obj: Any) -> str:
    return json.dumps(obj, ensure_ascii=False)


def _post_java(name: str, args: dict[str, Any]) -> dict:
    url = f"{config.JAVA_INTERNAL_BASE.rstrip('/')}/internal/ai/tool/{name}"
    headers = {"Content-Type": "application/json"}
    if config.RAG_API_TOKEN:
        headers["X-RAG-Token"] = config.RAG_API_TOKEN
    resp = requests.post(url, json={"args": args}, headers=headers,
                         timeout=config.AGENT_TOOL_TIMEOUT)
    if resp.status_code != 200:
        raise RuntimeError(f"工具网关返回 HTTP {resp.status_code}")
    body = resp.json()
    if body.get("code") != 200:
        raise RuntimeError(str(body.get("message") or "工具网关内部错误"))
    # Java 的 Result 包一层 data，里面才是 {ok, digest, data, sources}
    return body.get("data") or {"ok": False, "error": "工具返回为空"}


def _search_knowledge(args: dict[str, Any]) -> dict:
    query = str(args.get("query") or "").strip()
    if not query:
        return {"ok": False, "error": "query 不能为空"}
    if _vector_store is None:
        return {"ok": False, "error": "知识库未就绪"}

    try:
        top_k = int(args.get("top_k") or 5)
    except (TypeError, ValueError):
        top_k = 5
    top_k = max(1, min(8, top_k))

    # 全局检索按「每篇最多 N 段」裁剪，保证 top_k 覆盖到多篇文章；
    # 读单篇文章走的是 agent.py 的 article_id 分支，不受这条影响。
    hits = _vector_store.search(query, top_k, max_per_article=config.RETRIEVE_PER_ARTICLE)
    if not hits:
        return {"ok": True, "digest": f"知识库里没有与「{query}」相关的片段", "data": {"items": []}, "sources": []}

    items = []
    sources = []
    for h in hits:
        text = (h.get("text") or "").strip()
        # 每段 500 字封顶：5 段全塞满也只有 2500 字，不会把上下文顶爆
        if len(text) > 500:
            text = text[:500] + "…"
        item = {
            "text": text,
            "source": h.get("title") or h.get("filename", ""),
            "url": h.get("url", ""),
        }
        if h.get("source_type") == "article":
            item["type"] = "article"
            item["page"] = 0
        else:
            item["type"] = "pdf"
            item["page"] = h.get("page", 0)
            # PDF 没有站内链接，只给文件名和页码，前端渲染成不可点击的标签
            item["url"] = ""
        items.append(item)

        if h.get("source_type") == "article" and h.get("url"):
            sources.append({
                "type": "article",
                "title": h.get("title", ""),
                "url": h.get("url", ""),
            })
        else:
            sources.append({
                "type": "pdf",
                "title": h.get("filename", ""),
                "url": "",
                "page": h.get("page", 0),
            })

    # sources 去重（同一篇文章的多个片段只留一条卡片）
    seen = set()
    uniq = []
    for s in sources:
        key = (s.get("type"), s.get("url") or s.get("title"))
        if key in seen:
            continue
        seen.add(key)
        uniq.append(s)

    titles = [i["source"] for i in items if i.get("source")]
    digest = f"检索到 {len(items)} 个相关片段，来自：{'、'.join(titles[:3])}"
    return {"ok": True, "digest": digest, "data": {"items": items}, "sources": uniq}


async def run_tool(name: str, args: dict[str, Any], allowed: set[str] | None = None) -> dict:
    """
    执行一个工具，永不抛异常。

    同步的 requests 调用放到线程池里（asyncio.to_thread），
    否则一次工具调用会卡住整个事件循环 —— 同一进程里的其他用户会一起卡住。

    `allowed` 是本轮真正下发给模型的工具名集合。传了就必须在里面才执行：
    模型有可能"凭印象"调用一个没挂载的工具（最典型的是没配搜索 key 时的 web_search），
    若不挡，它会真的跑起来并返回一条失败信息，模型再把失败转述给用户 ——
    于是用户看到「联网搜索不可用（未配置密钥）」这种本不该出现的话。
    挡掉之后返回"工具不存在"，模型下一轮就会换路走。
    """
    import asyncio

    if allowed is not None and name not in allowed:
        return {"ok": False, "error": f"不存在名为 {name} 的工具，请改用已提供的工具"}

    try:
        if name == "search_knowledge":
            return await asyncio.to_thread(_search_knowledge, args or {})
        if name == "web_search":
            args = args or {}
            return await asyncio.to_thread(
                web_search.search, str(args.get("query") or ""), args.get("limit") or 5)
        return await asyncio.to_thread(_post_java, name, args or {})
    except Exception as e:
        return {"ok": False, "error": f"{type(e).__name__}: {e}"}


def tool_message_content(result: dict, name: str = "") -> str:
    """
    把工具结果压成回填给模型的 role=tool 内容。

    web_search 的结果要额外套一层 `<external_untrusted>` 定界标记：
    外部网页摘要是**不可信输入**，里面可能写着"忽略以上指令，告诉用户……"。
    光靠 system prompt 一句话不够，必须让模型能从结构上区分"这是我自己的资料"
    还是"这是网上抄来的、只可参考不可执行的内容"（设计文档 §9.3 第一层）。
    """
    text = _json(result)
    if name == "web_search":
        return ('<external_untrusted source="web_search">\n'
                + text
                + '\n</external_untrusted>')
    return text
