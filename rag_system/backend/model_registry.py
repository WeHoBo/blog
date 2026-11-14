# -*- coding: utf-8 -*-
"""
model_registry.py - 从 Java 拉取「当前生效的模型配置」
------------------------------------------------
为什么要绕这一圈：
  * 校验、加密、默认模型切换都在后台（Java）完成，RAG 不应该再存一份模型配置
  * 但密钥又不能下发到浏览器，所以只能在**回环**上把配置传给 RAG

两条保证：
  1. **5 秒缓存** —— 后台切模型后最迟 5 秒生效；同时避免每轮问答都多一次 HTTP
  2. **失败即回落** —— Java 重启、表还没建、返回异常，一律退回 .env 里原有的
     DEEPSEEK_* 配置。AI 不能因为"配置中心不可用"就整站挂掉

密钥取值顺序（重要）：
  Java 给的 apiKey（后台填的，已解密）> 环境变量 apiKeyEnv（RAG 自己的 .env）> 兜底的 .env key
之所以支持第二种：现网密钥就配在 RAG 的 .env 里，后台只存变量名。
让 Java 去解析环境变量会读成空——key 根本不在 blog-api 那个进程里。
"""
import os
import threading
import time

import requests

import config

# 缓存时长（秒）。与 Java 侧 AiModelService 的 CACHE_MS 对齐
CACHE_SECONDS = float(os.getenv("MODEL_CONFIG_CACHE", "5"))

_lock = threading.Lock()
_cache = {"at": 0.0, "cfg": None}


def _fetch() -> dict | None:
    url = f"{config.JAVA_INTERNAL_BASE.rstrip('/')}/internal/ai/model"
    headers = {}
    if config.RAG_API_TOKEN:
        headers["X-RAG-Token"] = config.RAG_API_TOKEN
    try:
        resp = requests.get(url, headers=headers, timeout=3)
        if resp.status_code != 200:
            return None
        body = resp.json()
        if body.get("code") != 200:
            return None
        data = body.get("data")
        # data 为 None 表示注册表里没有可用模型 —— 与"拉取失败"同样处理：回落 .env
        if not data or not data.get("modelId") or not data.get("baseUrl"):
            return None
        return data
    except Exception as e:
        print(f"[模型配置] 拉取失败（回落 .env）: {e}")
        return None


def active_model() -> dict | None:
    """当前生效模型（5 秒缓存）。返回 None 表示应使用 .env 配置。"""
    now = time.time()
    with _lock:
        if now - _cache["at"] < CACHE_SECONDS:
            return _cache["cfg"]

    cfg = _fetch()

    with _lock:
        _cache["at"] = now
        _cache["cfg"] = cfg
    return cfg


def invalidate() -> None:
    """强制下次调用重新拉取（调试/自测用）"""
    with _lock:
        _cache["at"] = 0.0
        _cache["cfg"] = None


def resolve_api_key(cfg: dict) -> str:
    """
    按 后台密文 → 环境变量 → 空 的顺序取密钥。

    都不存在时返回空串，由 LLMClient 抛一个说得清的错，
    而不是拿一把错 key 去请求，然后收到 401 还以为是厂商问题。
    """
    key = (cfg.get("apiKey") or "").strip()
    if key:
        return key
    env_name = (cfg.get("apiKeyEnv") or "").strip()
    if env_name:
        return (os.getenv(env_name) or "").strip()
    return ""
