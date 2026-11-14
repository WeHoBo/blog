# -*- coding: utf-8 -*-
"""
usage_reporter.py - 把本轮 token 用量回报给 Java 后端
------------------------------------------------
为什么绕一圈：RAG 不直连 MySQL（避免 .env 里出现库口令），
用量统一由 Java 写 ai_usage_daily。

设计要点：
  * fire-and-forget：上报失败只打日志，绝不影响用户已经看到的答案
  * 超时 3s：内部回环调用，超时说明 Java 不健康，不该拖住流
  * 令牌可选：生产两侧都配 RAG_API_TOKEN；本机为空时 Java 侧放行
"""
import requests

import config


def report_usage(user_id, model_code: str | None,
                 prompt_tokens: int, completion_tokens: int) -> None:
    """上报一轮问答的用量。user_id 为空（未登录/老客户端）时直接跳过。"""
    if not config.USAGE_REPORT_ENABLED or not user_id:
        return

    url = f"{config.JAVA_INTERNAL_BASE.rstrip('/')}/internal/ai/usage"
    headers = {"Content-Type": "application/json"}
    if config.RAG_API_TOKEN:
        headers["X-RAG-Token"] = config.RAG_API_TOKEN

    payload = {
        "userId": int(user_id),
        "modelCode": model_code or config.DEEPSEEK_MODEL,
        "promptTokens": int(prompt_tokens or 0),
        "completionTokens": int(completion_tokens or 0),
    }

    try:
        resp = requests.post(url, json=payload, headers=headers, timeout=3)
        if resp.status_code != 200:
            print(f"[用量上报] HTTP {resp.status_code}: {resp.text[:120]}")
    except Exception as e:
        # 记账失败不能影响用户，也不能影响后续请求
        print(f"[用量上报] 失败（忽略）: {e}")


def resolve_model_code(header_model: str | None) -> str:
    """优先用 Java 透传的模型标识，缺失时回落 .env 配置，保证统计口径不为空。"""
    if header_model and header_model.strip():
        return header_model.strip()
    return config.DEEPSEEK_MODEL


def report_trace(user_id, model_code: str | None, question: str, steps: list[dict],
                 prompt_tokens: int, completion_tokens: int, total_ms: int,
                 status: str = "ok", error_msg: str | None = None) -> bool:
    """
    一轮问答结束时上报：Java 侧在**同一个事务**里写 agent_trace 明细 + ai_usage_daily 汇总。

    比 report_usage 多写了明细，所以正常路径都走这里；只有「流程中途挂掉、
    拿不到步骤链」的异常分支才退回 report_usage。

    @return 是否上报成功（调用方据此决定要不要退回 report_usage）
    """
    if not config.USAGE_REPORT_ENABLED or not user_id:
        return False

    import json

    url = f"{config.JAVA_INTERNAL_BASE.rstrip('/')}/internal/ai/trace"
    headers = {"Content-Type": "application/json"}
    if config.RAG_API_TOKEN:
        headers["X-RAG-Token"] = config.RAG_API_TOKEN

    payload = {
        "userId": int(user_id),
        "question": (question or "")[:2000],
        "model": model_code or config.DEEPSEEK_MODEL,
        "steps": json.dumps(steps or [], ensure_ascii=False),
        "stepCount": len(steps or []),
        "promptTokens": int(prompt_tokens or 0),
        "completionTokens": int(completion_tokens or 0),
        "totalMs": int(total_ms or 0),
        "status": status or "ok",
        "errorMsg": (error_msg or "")[:480] or None,
    }

    try:
        resp = requests.post(url, json=payload, headers=headers, timeout=3)
        if resp.status_code != 200:
            print(f"[轨迹上报] HTTP {resp.status_code}: {resp.text[:120]}")
            return False
        body = resp.json()
        return body.get("code") == 200
    except Exception as e:
        print(f"[轨迹上报] 失败（忽略）: {e}")
        return False
