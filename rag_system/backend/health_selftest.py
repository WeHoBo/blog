# -*- coding: utf-8 -*-
"""
health_selftest.py - /health 的 embedding 自测
-----------------------------------------------
背景（真事故）：/health 以前只报 `chunks`，而那个数字读的是 Chroma 本地元数据，
和 embedding 服务无关。EMBEDDING_API_KEY 失效（401 / code 30014）时它照样返回
1055 块，服务一片绿，可所有语义检索都在 500 —— 用户看到的是「站内没有相关内容」。
排查时最贵的一步就是"健康检查骗了我"。

所以这里锁三条：
  1. embedding 正常 → status=ok、embedding.ok=True
  2. embedding 报 401 → status=degraded、embedding.ok=False、error 里带异常类名
  3. 探测结果缓存 60s，但 ?probe=1 能强制重测（换完 key 立刻验证）
  4. 探测超时不能把 /health 拖死（用 0.1s 阈值模拟）

不连真库、不发真请求：把 main.vector_store 换成一个假对象即可。

    python rag_system/backend/health_selftest.py
"""
import os
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

# 同 main.py：生产机要先换 sqlite3（main 里也有，但这里显式写出来，避免以后改动 main 的导入顺序时踩到）
import sqlite_compat  # noqa: E402,F401

import main  # noqa: E402


class FakeAuthError(Exception):
    """模拟 openai 的 AuthenticationError：类名进错误串，排查时能一眼认出是 key 问题"""


class FakeEmbedding:
    def __init__(self):
        self.mode = "ok"
        self.calls = 0

    def embed_query(self, text):
        self.calls += 1
        if self.mode == "ok":
            return [0.01] * 8
        if self.mode == "auth":
            raise FakeAuthError("Error code: 401 - {'code': 30014, 'message': 'Token is invalid.'}")
        if self.mode == "empty":
            return []
        if self.mode == "slow":
            time.sleep(0.6)
            return [0.01] * 8
        raise AssertionError(self.mode)


class FakeStore:
    def __init__(self, embedding):
        self.embedding = embedding

    def count(self):
        return 1055


def ok(msg):
    print(f"  OK  {msg}")


def reset(embedding):
    main.vector_store = FakeStore(embedding)
    main._embed_probe.update({"ok": None, "error": "", "model": "", "at": 0.0})
    main._embed_ok_before = None


def main_():
    print("[1] embedding 正常 → status=ok")
    emb = FakeEmbedding()
    reset(emb)
    h = main.health()
    assert h["status"] == "ok", h
    assert h["embedding"]["ok"] is True, h
    assert emb.calls == 1, emb.calls
    ok(f"status=ok，探测 1 次，chunks={h['chunks']}")

    print("[2] 缓存生效：60 秒内重复调用不再打接口")
    h2 = main.health()
    assert emb.calls == 1, f"应复用缓存，实际调用 {emb.calls} 次"
    assert h2["embedding"]["ok"] is True
    ok("第二次调用走了缓存，没有多花一次 embedding 请求")

    print("[3] key 失效（401）→ status=degraded，且错误里带异常类名")
    emb.mode = "auth"
    h3 = main.health(probe=True)
    assert h3["status"] == "degraded", h3
    assert h3["embedding"]["ok"] is False, h3
    assert "FakeAuthError" in h3["embedding"]["error"], h3["embedding"]
    assert "30014" in h3["embedding"]["error"], h3["embedding"]
    ok(f"status=degraded，error={h3['embedding']['error'][:60]}…")

    print("[4] 修好之后不用重启：?probe=1 立刻变绿")
    emb.mode = "ok"
    h4 = main.health(probe=True)
    assert h4["status"] == "ok" and h4["embedding"]["ok"] is True, h4
    ok("force=True 绕过缓存，状态即时恢复（换完 key 不用等 60 秒）")

    print("[5] 空向量也算失败（服务通了但没真正算出来）")
    emb.mode = "empty"
    h5 = main.health(probe=True)
    assert h5["embedding"]["ok"] is False, h5
    assert "空向量" in h5["embedding"]["error"], h5
    ok("返回 [] 被判定为不可用，不会误报健康")

    print("[6] 探测超时 → 判失败，但 /health 本身要快速返回")
    emb.mode = "slow"
    old = main._EMBED_PROBE_TIMEOUT
    main._EMBED_PROBE_TIMEOUT = 0.1
    try:
        started = time.time()
        h6 = main.health(probe=True)
        spent = time.time() - started
    finally:
        main._EMBED_PROBE_TIMEOUT = old
    assert h6["embedding"]["ok"] is False, h6
    assert "超时" in h6["embedding"]["error"], h6
    assert spent < 0.5, f"/health 被卡了 {spent:.2f}s"
    ok(f"超时判定正确，/health 在 {spent * 1000:.0f}ms 内返回")

    print("[7] 向量库未初始化时如实报错，不假装健康")
    main.vector_store = None
    main._embed_probe.update({"ok": None, "error": "", "model": "", "at": 0.0})
    h7 = main.health()
    assert h7["embedding"]["ok"] is False, h7
    assert "未初始化" in h7["embedding"]["error"], h7
    ok("vector_store=None → degraded，并提示去查 EMBEDDING_* 配置")

    print("\n全部通过：/health 能如实反映 embedding 可用性。")


if __name__ == "__main__":
    main_()
