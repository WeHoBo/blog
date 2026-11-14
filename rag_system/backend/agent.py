# -*- coding: utf-8 -*-
"""
agent.py - Agent 循环与 SSE 事件流
------------------------------------------------
产出的是 (event, data) 二元组，不直接拼 SSE 文本 —— 拼装和心跳留给 main.py，
这样这里可以单独测试（把事件收集成 list 断言即可）。

流程（见设计文档 §6）：
    决策段（非流式，带 tools）
      ├─ 有 tool_calls → 并行执行 → 回填 role=tool → 下一轮
      └─ 没有 tool_calls → 模型直接答了 → 把这段文本当 token 发出去，结束
    输出段（流式，不带 tools）→ 逐字发 token

⚠️ 为什么"直接答了"不再走一次流式：那会为同一个问题付两遍输出 token。
代价是这种情况下列表现不了打字机效果，但换来的是账单不虚高 ——
而这一轮回答本来就短（需要长回答的问题几乎都会先检索）。

⚠️ 结算写在 run_chat 的 finally 里：用户中途点停止、网络断开都要记账，
否则"跑了 40 秒被关掉"的那部分 token 就永远查不到出处。
"""
import asyncio
import json
import time
from typing import Any, AsyncIterator

import config
import usage_reporter
from agent_tools import available_tools, run_tool, tool_message_content, web_available
from llm_client import LLMClient, system_prompt

# 单次工具结果回填给模型的最大字符数，防止某个工具把上下文顶爆
MAX_TOOL_RESULT_CHARS = 6000

# 来源卡片分类型上限。按类型分别限，不按总数截 ——
# 否则"搜一下"这种场景里站内一旦命中很多，外部结果就全被挤掉了；
# 反过来站内文章也不会被外部结果顶掉。超出的丢弃靠后的（第一次检索结果优先）。
SOURCE_CAP_INTERNAL = 6
SOURCE_CAP_WEB = 5


def _sse(event: str, data: dict) -> tuple[str, dict]:
    return event, data


def _collect_sources(bucket: list[dict], sources: Any) -> None:
    """把各工具返回的来源合并去重。同一篇文章的多个片段只留一张卡片。"""
    if not isinstance(sources, list):
        return
    seen = {(s.get("type"), s.get("url") or s.get("title")) for s in bucket}
    for s in sources:
        if not isinstance(s, dict):
            continue
        key = (s.get("type"), s.get("url") or s.get("title"))
        if key in seen:
            continue
        seen.add(key)
        bucket.append(s)


def _truncate(text: str) -> str:
    return text if len(text) <= MAX_TOOL_RESULT_CHARS else text[:MAX_TOOL_RESULT_CHARS] + "…（结果过长，已截断）"


def _cap_sources(sources: list[dict]) -> list[dict]:
    """来源卡片分类型限量，避免两次联网（最多 8×2 条）把抽屉塞满。"""
    out: list[dict] = []
    web = 0
    inner = 0
    for s in sources:
        if s.get("type") == "web":
            if web >= SOURCE_CAP_WEB:
                continue
            web += 1
        else:
            if inner >= SOURCE_CAP_INTERNAL:
                continue
            inner += 1
        out.append(s)
    return out


async def _stream_answer(llm: LLMClient, messages: list[Any],
                         usage: dict[str, Any]) -> AsyncIterator[tuple[str, dict]]:
    """
    输出段。temperature 传 None：用后台模型配置里的值，管理员的那个输入框才真的有效。

    state=usage：让 LLMClient 把"实际用的哪个模型、是否降级"写回来。
    这个 dict 最终会进 _settle，记账必须按实际模型算钱（P4）。
    """
    async for delta in llm.stream_chat(messages, usage, temperature=None, state=usage):
        if delta:
            yield _sse("token", {"text": delta})


def _budget_left_ms(started: float) -> int:
    return config.AGENT_TOTAL_MS - int((time.monotonic() - started) * 1000)


# ------------------------------------------------------------------
# 对外入口
# ------------------------------------------------------------------

async def run_chat(question: str, llm: LLMClient, *,
                   user_id=None, model_code: str | None = None,
                   article_id: str | int | None = None) -> AsyncIterator[tuple[str, dict]]:
    """
    站内问答主流程。article_id 非空表示用户在某个文章详情页里提问。

    外层只负责结算：内层生成器把 usage / steps / status 写进共享字典，
    不管内层是正常结束、抛异常还是被前端中止，finally 一定会跑。
    """
    # usage 同时充当 LLMClient 的 state（会混入 model / degraded_from 两个字符串键），
    # 所以标注成 Any：纯 int 的标注会让后面 usage.get("model") 看起来像类型错误
    usage: dict[str, Any] = {}
    steps: list[dict] = []
    status = {"value": "aborted", "error": None}
    started = time.monotonic()

    try:
        async for ev in _run_chat(question, llm, article_id=article_id,
                                  usage=usage, steps=steps, status=status):
            yield ev
    except Exception as e:
        status["value"] = "error"
        status["error"] = f"{type(e).__name__}: {e}"
        raise
    finally:
        await _settle(question, user_id, model_code, steps, usage, status, started)


async def run_free(question: str, llm: LLMClient, mode: str = "explain",
                   user_id=None, model_code: str | None = None) -> AsyncIterator[tuple[str, dict]]:
    """自由问答：不检索、不调工具，同样走 SSE 让前端只维护一套解析。"""
    from llm_client import CODE_EXPLAIN_PROMPT

    # usage 同时充当 LLMClient 的 state（会混入 model / degraded_from 两个字符串键），
    # 所以标注成 Any：纯 int 的标注会让后面 usage.get("model") 看起来像类型错误
    usage: dict[str, Any] = {}
    steps: list[dict] = []
    status = {"value": "aborted", "error": None}
    started = time.monotonic()

    try:
        system = CODE_EXPLAIN_PROMPT if mode == "explain" else (
            "你是一名友好的技术助手，请用简洁清晰的中文回答。回答使用 Markdown 格式。")
        messages = [
            {"role": "system", "content": system},
            {"role": "user", "content": question},
        ]
        yield _sse("status", {"phase": "thinking"})
        async for ev in _stream_answer(llm, messages, usage):
            yield ev
        yield _sse("sources", [])
        yield _sse("done", _done_payload(steps, usage, started))
        status["value"] = "ok"
    finally:
        await _settle(question, user_id, model_code, steps, usage, status, started)


# ------------------------------------------------------------------
# 内层：真正干活
# ------------------------------------------------------------------

async def _run_chat(question: str, llm: LLMClient, *, article_id,
                    usage: dict[str, Any], steps: list[dict], status: dict) -> AsyncIterator[tuple[str, dict]]:
    started = time.monotonic()
    sources: list[dict] = []
    step = 0
    answered_directly = False

    yield _sse("status", {"phase": "thinking"})

    # 提示词按"当前到底有没有联网能力"选版本：没有 key 时连 web_search 这个名字都不出现，
    # 否则模型会照着名字硬调一个未挂载的工具，用户就会看到「联网搜索不可用（未配置密钥）」。
    system = system_prompt(web_available())
    if article_id:
        system += (f"\n\n【当前上下文】用户正在阅读文章 id={article_id}。"
                   "如果问题里的「这篇文章」「上面说的」指代不明，先用 get_article 读取它再回答。")
    messages: list[Any] = [
        {"role": "system", "content": system},
        {"role": "user", "content": question},
    ]

    # ---------- 关闭工具时退回纯检索问答（线上排障用开关，不是死代码） ----------
    if not config.AGENT_TOOLS_ENABLED:
        contexts = await asyncio.to_thread(_vector_search, question, config.TOP_K, article_id)
        messages = llm.build_rag_prompt(question, contexts)
        _collect_sources(sources, _contexts_to_sources(contexts))
        async for ev in _stream_answer(llm, messages, usage):
            yield ev
        yield _sse("sources", _cap_sources(sources))
        yield _sse("done", _done_payload(steps, usage, started))
        status["value"] = "ok"
        return

    # ---------- 决策 / 执行循环 ----------
    # 联网搜索次数单独计数：总步数上限管不住它 ——
    # 一轮里并行发两个 web_search 就是两次搜索费，得单独设闸。
    web_calls = 0

    while step < config.AGENT_MAX_STEPS:
        if _budget_left_ms(started) <= 5000:
            messages.append({"role": "user",
                             "content": "时间不多了，请用已获取的信息直接回答，不要再调用工具。"})
            break
        step += 1
        tools = available_tools(allow_web=web_calls < config.WEB_SEARCH_MAX_CALLS)
        # 本轮真正下发的工具名 —— run_tool 用它挡住"模型凭名字硬调未挂载工具"
        allowed_names = {t["function"]["name"] for t in tools}
        try:
            msg, call_usage = await llm.chat_once(
                messages, tools, temperature=config.AGENT_TEMPERATURE_DECIDE, state=usage)
        except Exception as e:
            print(f"[Agent] 决策调用失败 step={step}: {e}")
            # 决策段挂了不代表没法回答：用已有信息走输出段，比直接报错好
            messages.append({"role": "user", "content": "请基于目前掌握的信息直接回答。"})
            break

        _add_usage(usage, call_usage)
        tool_calls = getattr(msg, "tool_calls", None)

        if not tool_calls:
            # 模型认为不需要查资料，直接给了答案
            if getattr(msg, "content", None):
                yield _sse("token", {"text": msg.content})
                answered_directly = True
            else:
                messages.append(msg)
                messages.append({"role": "user", "content": "请直接回答问题。"})
            break

        messages.append(msg)
        calls = []
        for call in tool_calls:
            fn = getattr(call, "function", None)
            name = getattr(fn, "name", "") if fn else ""
            raw_args = getattr(fn, "arguments", "") if fn else ""
            args = _parse_args(raw_args)
            calls.append((call, name, args))
            yield _sse("tool", {"step": step, "name": name, "args": args, "state": "start"})

        # 一次返回多个工具调用时并行执行，最慢的那个决定耗时（文档 §6.3）
        results = await asyncio.gather(
            *[run_tool(name, args, allowed_names) for _, name, args in calls])

        for (call, name, args), result in zip(calls, results):
            ok = bool(result.get("ok"))
            if name == "web_search":
                web_calls += 1
            _collect_sources(sources, result.get("sources"))
            steps.append({
                "step": step,
                "tool": name,
                "args": args,
                "ok": ok,
                "error": None if ok else result.get("error"),
            })
            done_payload: dict[str, Any] = {"step": step, "name": name, "state": "done", "ok": ok}
            if ok:
                digest = result.get("digest") or ""
                if digest:
                    done_payload["digest"] = digest[:120]
            else:
                done_payload["error"] = str(result.get("error") or "工具执行失败")[:200]
            yield _sse("tool", done_payload)

            messages.append({
                "role": "tool",
                "tool_call_id": getattr(call, "id", ""),
                "content": _truncate(tool_message_content(result, name)),
            })
    else:
        # while 正常跑完（不是 break）说明步数用满了
        messages.append({"role": "user",
                         "content": "已达到检索轮次上限，请用现有信息直接回答，并在末尾注明「已达检索轮次上限」。"})

    # ---------- 输出段 ----------
    if not answered_directly:
        async for ev in _stream_answer(llm, messages, usage):
            yield ev

    yield _sse("sources", _cap_sources(sources))
    yield _sse("done", _done_payload(steps, usage, started))
    status["value"] = "ok"


# ------------------------------------------------------------------
# 结算 / 辅助
# ------------------------------------------------------------------

async def _settle(question: str, user_id, model_code, steps: list[dict],
                  usage: dict[str, Any], status: dict, started: float) -> None:
    """
    一轮结束后的记账。绝不能影响用户，所以全部包在 try 里。

    shield：结算正在进行时如果又被取消（前端连按停止），
    不该让 HTTP 请求被砍在半路，否则这次调用就彻底没有记录。

    ⚠️ 记账用 usage["model"]（LLMClient 写回的实际模型）优先于调用方传进来的 model_code：
    降级之后真正跑的是备用模型，还按主模型单价算就是记错账 ——
    而错的方向通常是"多算钱"，用户看到账单会觉得我们在乱扣。
    """
    if not user_id:
        return
    actual_model = usage.get("model") or model_code
    if usage.get("degraded_from"):
        print(f"[Agent] 本轮发生模型降级：{usage['degraded_from']} → {actual_model}")
    total_ms = int((time.monotonic() - started) * 1000)
    prompt_tokens = usage.get("prompt_tokens", 0)
    completion_tokens = usage.get("completion_tokens", 0)
    try:
        saved = await asyncio.shield(asyncio.to_thread(
            usage_reporter.report_trace,
            user_id, actual_model, question, steps,
            prompt_tokens, completion_tokens,
            total_ms, status.get("value") or "aborted", status.get("error"),
        ))
        if not saved:
            # 明细写不进去（比如 steps 撞上 JSON 列约束）时，至少要保住账单
            await asyncio.shield(asyncio.to_thread(
                usage_reporter.report_usage,
                user_id, actual_model, prompt_tokens, completion_tokens,
            ))
    except Exception as e:
        print(f"[Agent] 轨迹上报失败（忽略）: {e}")


def _done_payload(steps: list[dict], usage: dict[str, Any], started: float) -> dict:
    return {
        "steps": len(steps),
        "promptTokens": usage.get("prompt_tokens", 0),
        "completionTokens": usage.get("completion_tokens", 0),
        "tokens": usage.get("prompt_tokens", 0) + usage.get("completion_tokens", 0),
        "ms": int((time.monotonic() - started) * 1000),
        # 前端据此在消息下挂一行"已切换备用模型"——不告知的话，用户只会觉得
        # "这次回答怎么变差了"，而不知道是降级导致的（P4）
        "degraded": bool(usage.get("degraded_from")),
        "model": usage.get("model") or "",
    }


def _add_usage(total: dict[str, int], delta: dict[str, int]) -> None:
    total["prompt_tokens"] = total.get("prompt_tokens", 0) + int(delta.get("prompt_tokens") or 0)
    total["completion_tokens"] = total.get("completion_tokens", 0) + int(delta.get("completion_tokens") or 0)


def _parse_args(raw: str) -> dict:
    if not raw:
        return {}
    try:
        parsed = json.loads(raw)
        return parsed if isinstance(parsed, dict) else {}
    except Exception:
        # 模型偶尔会吐非法 JSON，此时给空参数它还能重试；直接抛就整轮没了
        return {}


def _vector_search(question: str, top_k: int, article_id) -> list[dict]:
    import agent_tools

    store = getattr(agent_tools, "_vector_store", None)
    if store is None:
        return []
    hits = store.search(question, top_k)
    if article_id:
        hits = [h for h in hits if str(h.get("article_id", "")) == str(article_id)]
    return hits


def _contexts_to_sources(contexts: list[dict]) -> list[dict]:
    out, seen = [], set()
    for c in contexts:
        if c.get("source_type") == "article" and c.get("url"):
            key = c["url"]
            if key in seen:
                continue
            seen.add(key)
            out.append({"type": "article", "title": c.get("title", ""), "url": key})
        else:
            key = (c.get("filename"), c.get("page"))
            if key in seen:
                continue
            seen.add(key)
            out.append({"type": "pdf", "title": c.get("filename", ""), "url": "", "page": c.get("page", 0)})
    return out
