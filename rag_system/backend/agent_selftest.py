# -*- coding: utf-8 -*-
"""
agent_selftest.py - Agent 循环的离线自测（不花钱、不联网）
------------------------------------------------
用假 LLM 和假工具跑完整的 Agent 循环，断言 SSE 事件序列与终止条件。
真实模型调用无法覆盖这些分支（模型不一定配合），但恰恰是它们最容易写错。

运行：
    cd rag_system && .venv/Scripts/python.exe backend/agent_selftest.py
"""
import asyncio
import os
import sys
import time
import types
from types import SimpleNamespace

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

# 生产机的 sqlite 太老，必须先换掉（见 sqlite_compat.py），否则后面的 chromadb 导入会抛错
import sqlite_compat  # noqa: E402,F401

import agent  # noqa: E402
import config  # noqa: E402

PASSED = 0


def ok(label: str) -> None:
    global PASSED
    PASSED += 1
    print(f"  OK  {label}")


def msg(content=None, tool_calls=None):
    """伪造 openai 的 ChatCompletionMessage：只需要 content / tool_calls 两个属性"""
    return types.SimpleNamespace(content=content, tool_calls=tool_calls)


def call(cid: str, name: str, args: str):
    return types.SimpleNamespace(id=cid, function=types.SimpleNamespace(name=name, arguments=args))


class FakeLLM:
    """按脚本依次返回决策结果；输出段固定吐三段文字"""

    def __init__(self, decisions):
        self.decisions = decisions
        self.index = 0
        # 每轮实际下发的工具名，用来断言"次数用满后工具被摘掉"
        self.seen_tools: list[list[str]] = []

    @staticmethod
    def build_rag_prompt(question, contexts):
        # 真正拼提示词的逻辑在 LLMClient 里，自测只关心循环能否走通
        return [{"role": "system", "content": "rag"}, {"role": "user", "content": question}]

    async def chat_once(self, messages, tools=None, temperature=0.2, state=None):
        self.seen_tools.append([t["function"]["name"] for t in (tools or [])])
        if state is not None:
            # 真实 LLMClient 会写回实际模型；假的不写的话，记账断言会走不到那条分支
            state["model"] = "fake-model"
            state["degraded_from"] = None
        # decisions 里存的本来就是 (message, usage) 二元组，直接返回，别再包一层
        item = self.decisions[min(self.index, len(self.decisions) - 1)]
        self.index += 1
        return item

    async def stream_chat(self, messages, usage_sink=None, temperature=0.3, state=None):
        for piece in ("根据资料，", "RAG 是检索增强生成。", ""):
            if piece:
                yield piece
        if usage_sink is not None:
            usage_sink["prompt_tokens"] = usage_sink.get("prompt_tokens", 0) + 50
            usage_sink["completion_tokens"] = usage_sink.get("completion_tokens", 0) + 12
        if state is not None:
            state["model"] = "fake-model"
            state["degraded_from"] = None


async def collect(gen):
    return [item async for item in gen]


def events_of(items, name):
    return [d for e, d in items if e == name]


def install_fake_tools(result):
    """替换真正的 HTTP 工具调用，避免自测依赖 Java 服务

    ⚠️ 替身签名必须跟 `agent_tools.run_tool(name, args, allowed)` 一致。
    少一个参数会在自测里抛 TypeError，而线上照跑不误 —— 这种「只有测试挂」的失配
    正是加参数时最容易漏的地方。
    """

    async def fake_run_tool(name_, args, allowed=None):
        return result

    agent.run_tool = fake_run_tool


def main() -> None:
    config.AGENT_TOOLS_ENABLED = True
    config.AGENT_MAX_STEPS = 5

    item_ok = {"ok": True, "digest": "命中 2 篇：《RAG 入门》、《向量库选型》",
               "data": {"items": [{"id": 1}, {"id": 2}]},
               "sources": [{"type": "article", "title": "RAG 入门", "url": "/post/rag"},
                           {"type": "article", "title": "RAG 入门", "url": "/post/rag"}]}

    # ---------- 场景 1：模型先调工具，再输出答案 ----------
    print("[1] 工具调用 → 输出答案")
    install_fake_tools(item_ok)
    llm = FakeLLM([
        (msg(tool_calls=[call("c1", "search_articles", '{"q":"RAG"}')]), {"prompt_tokens": 100, "completion_tokens": 20}),
        (msg(content=None, tool_calls=None), {}),   # 不应走到这里（第二轮前已够）
    ])
    # 第二轮让它不再调工具，直接进入输出段
    llm.decisions[1] = (msg(content=None, tool_calls=None), {})
    ev = asyncio.run(collect(agent.run_chat("你写过 RAG 吗", llm)))

    kinds = [e for e, _ in ev]
    assert kinds[0] == "status", kinds
    assert "tool" in kinds and "token" in kinds and kinds[-1] == "done", kinds
    starts = [d for d in events_of(ev, "tool") if d["state"] == "start"]
    dones = [d for d in events_of(ev, "tool") if d["state"] == "done"]
    assert len(starts) == 1 and len(dones) == 1, (starts, dones)
    assert dones[0]["name"] == "search_articles" and dones[0]["ok"] is True, dones
    ok("事件顺序 status → tool(start/done) → token → sources → done")

    src = events_of(ev, "sources")[0]
    assert len(src) == 1, src   # 同 URL 去重
    ok("来源按 url 去重（两个同名片段只留 1 张卡片）")

    done = events_of(ev, "done")[0]
    assert done["steps"] == 1, done
    assert done["promptTokens"] == 150 and done["completionTokens"] == 32, done
    ok("done 汇总了两段的 token（决策 100+20，输出 50+12）")

    # ---------- 场景 2：模型认为不用查，直接答 ----------
    print("[2] 模型直接回答（不调工具）")
    install_fake_tools(item_ok)
    llm = FakeLLM([(msg(content="你好，我是好啵 AI。", tool_calls=None), {"prompt_tokens": 80, "completion_tokens": 10})])
    ev = asyncio.run(collect(agent.run_chat("你是谁", llm)))
    tokens = "".join(d["text"] for d in events_of(ev, "token"))
    assert tokens == "你好，我是好啵 AI。", tokens
    assert events_of(ev, "tool") == [], "不该有工具事件"
    assert llm.index == 1, f"只应调用 1 次决策，实际 {llm.index}"
    ok("不重复生成（只调 1 次模型，避免同一问题付两遍输出 token）")

    # ---------- 场景 3：工具失败回填，循环继续 ----------
    print("[3] 工具失败")
    install_fake_tools({"ok": False, "error": "工具内部错误：TimeoutException"})
    llm = FakeLLM([
        (msg(tool_calls=[call("c2", "search_articles", '{"q":"x"}')]), {"prompt_tokens": 70, "completion_tokens": 15}),
        (msg(content=None, tool_calls=None), {}),
    ])
    ev = asyncio.run(collect(agent.run_chat("查一下", llm)))
    dones = [d for d in events_of(ev, "tool") if d["state"] == "done"]
    assert dones[0]["ok"] is False and "Timeout" in dones[0]["error"], dones
    assert events_of(ev, "done"), "工具失败也必须正常收尾"
    ok("工具失败只标 ok=false，不中断整轮")

    # ---------- 场景 4：模型一直要调工具 → 用满步数强制收尾 ----------
    print("[4] 步数用满")
    install_fake_tools(item_ok)
    config.AGENT_MAX_STEPS = 3
    llm = FakeLLM([(msg(tool_calls=[call("c3", "search_articles", '{"q":"y"}')]), {"prompt_tokens": 60, "completion_tokens": 10})])
    ev = asyncio.run(collect(agent.run_chat("一直查", llm)))
    assert llm.index == 3, f"应当恰好 3 轮决策，实际 {llm.index}"
    assert len(events_of(ev, "done")) == 1
    assert events_of(ev, "token"), "步数用满后仍要输出答案"
    ok("达到 max_steps 后强制输出答案")

    # ---------- 场景 5：开关关闭 → 退回纯检索问答 ----------
    print("[5] AGENT_TOOLS_ENABLED=0")
    config.AGENT_TOOLS_ENABLED = False
    llm = FakeLLM([])
    ev = asyncio.run(collect(agent.run_chat("退回老模式", llm)))
    assert events_of(ev, "tool") == []
    assert events_of(ev, "token") and events_of(ev, "done")
    ok("关闭工具后仍走 SSE，只是不再调用工具")

    # ---------- 场景 6：参数是非法 JSON ----------
    print("[6] 工具参数非法 JSON")
    print("  parse_args:", agent._parse_args("{不是json"), agent._parse_args(""))
    assert agent._parse_args("{不是json") == {}
    ok("非法 JSON 退化成空参数，而不是抛异常中断整轮")

    web_search_cases()
    fallback_cases()

    print(f"\n全部通过：{PASSED} 项")


# ------------------------------------------------------------------
# P3：联网搜索
# ------------------------------------------------------------------

def web_search_cases() -> None:
    import agent_tools
    import web_search

    config.AGENT_TOOLS_ENABLED = True
    config.AGENT_MAX_STEPS = 5
    config.WEB_SEARCH_ENABLED = True
    config.WEB_SEARCH_PROVIDERS = ["tavily", "bocha"]
    config.WEB_SEARCH_API_KEY = ""

    # ---------- 场景 7：没有密钥时工具根本不挂载 ----------
    print("[7] 无密钥时不挂载 web_search")
    names = [t["function"]["name"] for t in agent_tools.available_tools()]
    assert "web_search" not in names, names
    assert "search_knowledge" in names
    ok("没配密钥 → 工具不下发（模型看不到就不会白调一次）")

    config.WEB_SEARCH_API_KEY = "test-key"
    names = [t["function"]["name"] for t in agent_tools.available_tools()]
    assert "web_search" in names, names
    ok("配了密钥 → web_search 出现在 tools 列表里")

    # 跑满次数后从列表里摘掉
    names = [t["function"]["name"] for t in agent_tools.available_tools(allow_web=False)]
    assert "web_search" not in names, names
    ok("allow_web=False → 本轮摘掉该工具（不靠调用后报错来限次）")

    # ---------- 场景 7.5：提示词也要跟着分支，否则"名字"会漏出去 ----------
    print("[7.5] 无密钥时提示词里连 web_search 这个名字都不出现")
    import llm_client
    assert "web_search" not in llm_client.system_prompt(False)
    assert "web_search" in llm_client.system_prompt(True)
    # 评测集的 forbidden 依赖这几个标记串，两版都必须保留，否则注入检测会失效
    for k in ("search_knowledge", "site_stats", "list_taxonomy", "external_untrusted"):
        assert k in llm_client.system_prompt(False), k
    ok("提示词按有无联网能力分支；无 key 版仍保留评测所需的工具名")

    # ---------- 场景 7.6：检索失败 ≠ 站内没有 ----------
    print("[7.6] 提示词区分「检索失败」和「站内没有」")
    # 背景：embedding key 失效时工具返回 ok=false，模型把"查不了"说成"站内没有"，
    # 用户看到的是"博主没写过这个"，而不是"服务坏了"。两版提示词都必须把这条讲死。
    for variant, prompt in (("无联网", llm_client.system_prompt(False)),
                            ("有联网", llm_client.system_prompt(True))):
        for key in ("「检索失败」和「站内没有」是两件完全不同的事",
                    "ok 为 false", "站内检索暂时不可用", "冒充站内资料"):
            assert key in prompt, f"{variant}版提示词缺少「{key}」"
    ok("两版都写明：工具报错就说「检索暂时不可用」，不许拿记忆冒充站内资料")

    res = asyncio.run(agent_tools.run_tool("web_search", {"query": "x"}, {"search_knowledge", "site_stats"}))
    assert res.get("ok") is False, res
    assert "不存在" in str(res.get("error")), res
    ok("模型硬调未挂载的工具 → 被白名单挡下（不会返回「联网搜索不可用」让人一头雾水）")

    # ---------- 场景 8：单轮联网次数上限 ----------
    print("[8] 单轮联网次数上限")
    config.WEB_SEARCH_MAX_CALLS = 2
    agent.run_tool = _fake_async_tool({
        "ok": True, "digest": "命中 1 条", "data": {"items": []},
        "sources": [{"type": "web", "title": "T", "url": "https://a.com/x", "domain": "a.com"}],
    })
    llm = FakeLLM([(msg(tool_calls=[call("w1", "web_search", '{"query":"x"}')]),
                    {"prompt_tokens": 50, "completion_tokens": 10})])
    asyncio.run(collect(agent.run_chat("一直联网", llm)))
    # 第 1、2 轮能看到 web_search，第 3 轮开始摘掉
    assert len(llm.seen_tools) >= 3, llm.seen_tools
    assert "web_search" in llm.seen_tools[0] and "web_search" in llm.seen_tools[1], llm.seen_tools
    assert "web_search" not in llm.seen_tools[2], llm.seen_tools
    ok("前 2 轮可联网，第 3 轮起工具被摘掉")

    # ---------- 场景 9：外部结果套不可信标记 ----------
    print("[9] 外部结果的定界标记")
    wrapped = agent_tools.tool_message_content({"ok": True, "digest": "x"}, "web_search")
    assert "<external_untrusted" in wrapped and "</external_untrusted>" in wrapped, wrapped
    plain = agent_tools.tool_message_content({"ok": True, "digest": "x"}, "search_articles")
    assert "external_untrusted" not in plain
    ok("web_search 结果被 <external_untrusted> 包裹，站内工具不会")

    # ---------- 场景 10：provider 清洗 / 降级 / 缓存 / 失败 ----------
    print("[10] provider 行为（假接口，不联网）")

    def fake_tavily(query, limit, key):
        return [
            {"title": "知乎回答 <script>alert(1)</script>", "url": "https://www.zhihu.com/question/1",
             "snippet": "摘要里有 &lt;b&gt;标签&lt;/b&gt; 和 onerror= 残片", "publishedAt": "2026-11-01"},
            {"title": "重复项", "url": "https://www.zhihu.com/question/1/", "snippet": "同 URL 不同写法"},
            {"title": "非法协议", "url": "javascript:alert(1)", "snippet": "应被丢弃"},
            {"title": "第二个站", "url": "http://example.com/a#frag", "snippet": "正常摘要"},
        ]

    web_search._PROVIDERS["tavily"] = fake_tavily
    web_search.clear_cache()

    res = web_search.search("DeepSeek V4", 5)
    assert res["ok"] is True, res
    items = res["data"]["items"]
    assert len(items) == 2, [i["url"] for i in items]      # js 协议被丢、重复 URL 被并
    assert items[0]["domain"] == "zhihu.com", items[0]
    assert "<script" not in items[0]["title"], items[0]["title"]
    assert "<" not in items[0]["snippet"], items[0]["snippet"]
    assert items[1]["domain"] == "example.com", items[1]
    ok("剥标签 + 丢弃非 http(s) + 按 URL 去重 + 服务端算域名")

    srcs = res["sources"]
    assert all(s["type"] == "web" and s["url"].startswith(("http://", "https://")) for s in srcs), srcs
    ok("来源卡片都是 type=web 且协议合法")

    # 缓存命中：同样的 query 不该再调 provider
    calls = {"n": 0}

    def counting_tavily(query, limit, key):
        calls["n"] += 1
        return []

    web_search._PROVIDERS["tavily"] = counting_tavily
    res2 = web_search.search("DeepSeek V4", 5)
    assert res2["ok"] is True and calls["n"] == 0, (res2["ok"], calls["n"])
    ok("30 分钟内同样的问题命中缓存，不重复付费")

    # 主 provider 抛异常 → 降级到备用
    web_search.clear_cache()
    web_search._PROVIDERS["tavily"] = lambda q, l, k: (_ for _ in ()).throw(RuntimeError("boom"))
    web_search._PROVIDERS["bocha"] = lambda q, l, k: [
        {"title": "博查结果", "url": "https://example.org/p", "snippet": "备用 provider 生效"}
    ]
    res3 = web_search.search("降级测试", 3)
    assert res3["ok"] is True and res3["sources"][0]["domain"] == "example.org", res3
    ok("主 provider 失败自动降级到备用")

    # 全链路失败 → 如实失败，不返回空结果冒充"没搜到"
    web_search.clear_cache()
    web_search._PROVIDERS["tavily"] = lambda q, l, k: []
    web_search._PROVIDERS["bocha"] = lambda q, l, k: []
    res4 = web_search.search("全失败", 3)
    assert res4["ok"] is False and "联网搜索不可用" in res4["error"], res4
    ok("全链路失败返回 ok=false，让模型如实说没联网")

    # 关掉开关 → 直接拒绝
    config.WEB_SEARCH_ENABLED = False
    res5 = web_search.search("关闭后", 3)
    assert res5["ok"] is False, res5
    config.WEB_SEARCH_ENABLED = True
    ok("WEB_SEARCH_ENABLED=0 时拒绝调用")

    http_case(web_search)

    # ---------- 场景 12：来源卡片分类型限量 ----------
    print("[12] 来源卡片限量")
    many = ([{"type": "web", "title": f"w{i}", "url": f"https://w{i}.com/a", "domain": f"w{i}.com"}
             for i in range(10)]
            + [{"type": "article", "title": f"a{i}", "url": f"/post/{i}"} for i in range(10)])
    capped = agent._cap_sources(many)
    web_kept = [s for s in capped if s["type"] == "web"]
    art_kept = [s for s in capped if s["type"] != "web"]
    assert len(web_kept) == agent.SOURCE_CAP_WEB, len(web_kept)
    assert len(art_kept) == agent.SOURCE_CAP_INTERNAL, len(art_kept)
    # 保留靠前的（第一次检索结果优先），不是随机截断
    assert [s["title"] for s in web_kept] == [f"w{i}" for i in range(agent.SOURCE_CAP_WEB)]
    assert [s["title"] for s in art_kept] == [f"a{i}" for i in range(agent.SOURCE_CAP_INTERNAL)]
    ok("两次联网最多 8×2 条也不会把抽屉塞满，站内/站外各自限量、互不挤占")

    # 真实事件流里也生效（证明接在 yield 上，不只是函数本身对）
    agent.run_tool = _fake_async_tool({
        "ok": True, "digest": "命中很多", "data": {"items": []},
        "sources": [{"type": "web", "title": f"w{i}", "url": f"https://w{i}.com/a",
                     "domain": f"w{i}.com"} for i in range(8)],
    })
    llm = FakeLLM([(msg(tool_calls=[call("w9", "web_search", '{"query":"x"}')]),
                    {"prompt_tokens": 10, "completion_tokens": 5})])
    evs = asyncio.run(collect(agent.run_chat("多结果", llm)))
    src_events = events_of(evs, "sources")
    assert src_events, evs
    assert len(src_events[-1]) == agent.SOURCE_CAP_WEB, len(src_events[-1])
    ok("sources 事件带出去的就是限量后的列表")



def http_case(web_search) -> None:
    """
    用本地假 HTTP 服务跑一次**真实**的 requests 调用。

    前面几个场景替换的是 provider 函数，等于跳过了 HTTP 这一层；
    而请求体字段名写错（比如 tavily 要 max_results、博查要 count）、
    Authorization 头漏掉，恰恰是接第三方 API 最容易出错的地方。
    """
    import json
    import threading
    from http.server import BaseHTTPRequestHandler, HTTPServer

    captured: dict = {}

    class Handler(BaseHTTPRequestHandler):
        def do_POST(self):  # noqa: N802
            length = int(self.headers.get("Content-Length") or 0)
            captured["body"] = json.loads(self.rfile.read(length) or b"{}")
            captured["auth"] = self.headers.get("Authorization")
            if captured["body"].get("query") == "boom":
                self.send_response(500)
                self.end_headers()
                self.wfile.write(b'{"error":"upstream"}')
                return
            payload = {"results": [
                {"title": "HTTP 返回的标题", "url": "https://www.example.net/post/1",
                 "content": "HTTP 返回的摘要", "published_date": "2026-11-10"},
            ]}
            raw = json.dumps(payload, ensure_ascii=False).encode()
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(raw)))
            self.end_headers()
            self.wfile.write(raw)

        def log_message(self, *a):   # 静音，别把自测输出淹了
            return

    server = HTTPServer(("127.0.0.1", 0), Handler)
    port = server.server_address[1]
    threading.Thread(target=server.serve_forever, daemon=True).start()

    old_base = os.environ.get("WEB_SEARCH_BASE_URL_TAVILY")
    os.environ["WEB_SEARCH_BASE_URL_TAVILY"] = f"http://127.0.0.1:{port}/search"
    # 上一个场景把 _PROVIDERS 换成了假函数，这里要换回真实现 ——
    # 本场景的目的恰恰是验证真实的 requests 调用
    web_search._PROVIDERS["tavily"] = web_search._call_tavily
    try:
        config.WEB_SEARCH_PROVIDERS = ["tavily"]
        config.WEB_SEARCH_API_KEY = "k-123"
        web_search.clear_cache()

        print("[11] 真实 HTTP 调用（本地假服务）")
        res = web_search.search("真实路径", 3)
        assert res["ok"] is True, res
        item = res["data"]["items"][0]
        assert item["title"] == "HTTP 返回的标题", item
        assert item["domain"] == "example.net", item       # www. 被去掉
        assert item["publishedAt"] == "2026-11-10", item
        ok("响应解析正确（title/url/content/published_date 字段映射没写错）")

        assert captured["body"].get("query") == "真实路径", captured
        assert captured["body"].get("max_results") == 3, captured
        assert captured["body"].get("api_key") == "k-123", captured
        ok("请求体字段名正确（tavily 用 max_results + api_key，不是 count/token）")

        # 上游 500 → 全链路失败（本次只配了一个 provider）
        web_search.clear_cache()
        res2 = web_search.search("boom", 3)
        assert res2["ok"] is False and "tavily" in res2["error"], res2
        ok("上游非 200 时如实失败，错误里带 provider 名便于排障")
    finally:
        server.shutdown()
        if old_base is None:
            os.environ.pop("WEB_SEARCH_BASE_URL_TAVILY", None)
        else:
            os.environ["WEB_SEARCH_BASE_URL_TAVILY"] = old_base


def _fake_async_tool(result):
    async def fake_run_tool(name_, args, allowed=None):
        return result
    return fake_run_tool


# ------------------------------------------------------------------
# P4：模型降级链
# ------------------------------------------------------------------
class _StatusErr(Exception):
    """模拟 openai 的 APIStatusError —— 只需要 status_code，不碰真实网络。"""

    def __init__(self, code: int):
        self.status_code = code
        super().__init__(f"HTTP {code}")


def _cfg(model: str) -> dict:
    return {"model": model, "base_url": "http://" + model, "api_key": "k",
            "temperature": 0.3, "max_tokens": 0, "support_tools": True, "source": "registry"}


def _new_client(primary: str, backup: str | None):
    from llm_client import LLMClient

    client = LLMClient()
    client.current = lambda: _cfg(primary)
    client.fallback = (lambda: _cfg(backup)) if backup else (lambda: None)
    return client


def fallback_cases() -> None:
    from llm_client import LLMClient, ModelConfigError, _retryable

    print("[13] 模型降级链（P4）")

    # ---------- 判定表：哪些错误值得换模型 ----------
    assert _retryable(_StatusErr(429))
    assert _retryable(_StatusErr(500)) and _retryable(_StatusErr(503))
    assert not _retryable(_StatusErr(400))
    assert not _retryable(_StatusErr(401)) and not _retryable(_StatusErr(403))
    assert not _retryable(_StatusErr(404))
    assert _retryable(ModelConfigError("缺 key"))
    assert not _retryable(RuntimeError("别的东西"))
    ok("429/5xx/缺 key 才降级；400/401/403/404 如实报错（换模型只会把错误藏起来）")

    # ---------- 没配降级：原异常原样抛出，不能吞 ----------
    client = _new_client("primary", None)

    async def always_429(cfg, messages, tools, temperature):
        raise _StatusErr(429)

    client._chat_once_with = always_429
    raised = None
    try:
        asyncio.run(client.chat_once([], None))
    except _StatusErr as e:
        raised = e
    assert raised is not None and raised.status_code == 429
    ok("未配置降级模型时，429 原样抛出（不假装成功）")

    # ---------- 配了降级：主模型 429 → 备用模型成功，state 记下实情 ----------
    seen = []

    async def primary_fails(cfg, messages, tools, temperature):
        seen.append(cfg["model"])
        if cfg["model"] == "primary":
            raise _StatusErr(429)
        return SimpleNamespace(content="备用模型答的"), {"prompt_tokens": 3, "completion_tokens": 4}

    client = _new_client("primary", "backup")
    client._chat_once_with = primary_fails
    state: dict = {}
    msg, usage = asyncio.run(client.chat_once([], None, state=state))
    assert msg.content == "备用模型答的", msg
    assert seen == ["primary", "backup"], seen
    assert state["model"] == "backup" and state["degraded_from"] == "primary", state
    ok("主模型 429 → 自动换备用模型重试一次，state 记录实际模型与来源")

    # ---------- 不可降级错误：即使配了 fallback 也不该偷偷换 ----------
    async def bad_request(cfg, messages, tools, temperature):
        raise _StatusErr(400)

    client = _new_client("primary", "backup")
    client._chat_once_with = bad_request
    raised = None
    try:
        asyncio.run(client.chat_once([], None))
    except _StatusErr as e:
        raised = e
    assert raised is not None and raised.status_code == 400
    ok("400（参数/内容问题）不降级 —— 掩盖它只会让人查不到真因")

    # ---------- 两个都挂：错误信息里要能同时看到两次原因 ----------
    client = _new_client("primary", "backup")
    client._chat_once_with = always_429
    err_text = ""
    try:
        asyncio.run(client.chat_once([], None))
    except RuntimeError as e:
        err_text = str(e)
    assert "primary" in err_text and "backup" in err_text, err_text
    ok("主备均失败时抛出包含两次原因的错误（否则只会看到第二个错）")

    # ---------- 流式：一个字都没发出去时，可以降级 ----------
    async def stream_primary_fails(cfg, messages, usage_sink, temperature):
        if cfg["model"] == "primary":
            raise _StatusErr(503)
        yield "备用"
        yield "模型"

    client = _new_client("primary", "backup")
    client._stream_with = stream_primary_fails
    st: dict = {}
    pieces = asyncio.run(collect(client.stream_chat([], state=st)))
    assert pieces == ["备用", "模型"], pieces
    assert st["model"] == "backup" and st["degraded_from"] == "primary", st
    ok("流式调用在未吐字时降级成功（用户看到的是备用模型的完整回答）")

    # ---------- 流式：已经吐字后再失败，绝不能从头重来 ----------
    called = []

    async def stream_partial(cfg, messages, usage_sink, temperature):
        called.append(cfg["model"])
        yield "前半句"
        raise _StatusErr(500)

    client = _new_client("primary", "backup")
    client._stream_with = stream_partial
    raised = None
    try:
        asyncio.run(collect(client.stream_chat([], state={})))
    except _StatusErr as e:
        raised = e
    assert raised is not None, "已吐字后应直接报错"
    assert called == ["primary"], called
    ok("已吐字后失败不重试 —— 否则用户会看到两段从头开始的重复内容")

    # ---------- 记账必须按实际模型，否则按主模型单价算错钱 ----------
    captured = {}

    def fake_trace(user_id, model_code, question, steps, pt, ct, ms, status, error):
        captured["model"] = model_code
        return True

    old_trace = agent.usage_reporter.report_trace
    agent.usage_reporter.report_trace = fake_trace
    try:
        asyncio.run(agent._settle(
            "q", 7, "primary", [],
            {"model": "backup", "degraded_from": "primary",
             "prompt_tokens": 1, "completion_tokens": 2},
            {"value": "ok"}, time.monotonic()))
    finally:
        agent.usage_reporter.report_trace = old_trace
    assert captured.get("model") == "backup", captured
    ok("结算按降级后实际使用的模型记账（model_code 被 usage.model 覆盖）")

    # ---------- done 事件如实告知前端发生了降级 ----------
    d = agent._done_payload([], {"model": "backup", "degraded_from": "primary",
                                 "prompt_tokens": 1, "completion_tokens": 1}, time.monotonic())
    assert d["degraded"] is True and d["model"] == "backup", d
    d2 = agent._done_payload([], {"model": "primary", "degraded_from": None}, time.monotonic())
    assert d2["degraded"] is False, d2
    ok("done 事件带 degraded/model，前端可提示「已切换备用模型」")

    # ---------- 跨语言契约：Java /internal/ai/model 下发的 fallback 结构 ----------
    # 这里锁的是字段名（modelId/baseUrl/apiKey…）。写错了不会报错，只会静默不降级，
    # 所以必须有一条断言盯着它。
    import model_registry

    payload = {
        "modelId": "primary-x", "baseUrl": "http://primary", "apiKey": "k1",
        "temperature": 0.3, "maxTokens": 100, "supportTools": True,
        "fallback": {
            "modelId": "backup-y", "baseUrl": "http://backup", "apiKey": "k2",
            "temperature": 0.5, "maxTokens": 200, "supportTools": False,
        },
    }
    old_fetch = model_registry._fetch
    model_registry._fetch = lambda: payload
    model_registry.invalidate()
    try:
        c = LLMClient()
        fb = c.fallback()
        assert fb is not None, "含 fallback 的响应应解析出降级配置"
        assert fb["model"] == "backup-y" and fb["base_url"] == "http://backup", fb
        assert fb["api_key"] == "k2" and fb["max_tokens"] == 200, fb
        assert fb["support_tools"] is False, fb
        # 主模型与降级模型各自独立：主模型仍是 primary
        assert c.current()["model"] == "primary-x"

        payload.pop("fallback")
        model_registry.invalidate()
        assert c.fallback() is None, "没有 fallback 字段时应返回 None（不是空 dict）"
    finally:
        model_registry._fetch = old_fetch
        model_registry.invalidate()
    ok("Java 下发的 fallback 字段名与 Python 解析一致（写错只会静默不降级）")



if __name__ == "__main__":
    main()
