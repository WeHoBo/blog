# -*- coding: utf-8 -*-
"""
retrieval_selftest.py - 检索「同篇配额」的离线自测
--------------------------------------------------
问题背景：切块时相邻块补了 CHUNK_OVERLAP 重叠，同一篇文章的连续片段相似度极高。
线上库 1055 块 / 34 篇（平均每篇 31 块），一个笼统的问题经常被同一篇的 4~5 段占满
top_k，于是「站内有哪些文章讲过 X」只能答出一篇。

这里只测纯函数 select_diverse —— 不需要 Chroma、不需要 embedding、不发网络请求，
所以可以在任何机器上直接跑：

    python rag_system/backend/retrieval_selftest.py
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

# 生产机的 sqlite 版本太老，必须先换掉（见 sqlite_compat.py），否则 import chromadb 直接抛错
import sqlite_compat  # noqa: E402,F401

from vector_store import select_diverse  # noqa: E402


def hit(article_id, title="", distance=0.2, text=""):
    """造一条检索命中"""
    h = {"text": text or f"片段{article_id}", "title": title, "distance": distance,
         "filename": "", "page": 0}
    if article_id:
        h["source_type"] = "article"
        h["article_id"] = article_id
        h["url"] = f"/post/{article_id}"
    else:
        h["source_type"] = "pdf"
    return h


def ok(msg):
    print(f"  OK  {msg}")


def main() -> None:
    print("[1] 不传配额 → 原样取前 top_k 条")
    res = select_diverse([hit(1), hit(1), hit(1), hit(2)], 3, None)
    assert len(res) == 3 and [h["article_id"] for h in res] == [1, 1, 1], res
    ok("max_per_article=None 时不做裁剪（保持旧行为）")

    print("[2] 主力场景：同一篇占满 top5 → 裁剪后覆盖多篇")
    hits = [hit(1), hit(1), hit(1), hit(1), hit(1), hit(2), hit(3), hit(4)]
    res = select_diverse(hits, 5, 2)
    ids = [h["article_id"] for h in res]
    assert ids == [1, 1, 2, 3, 4], ids
    assert len(set(ids)) == 4, ids
    ok(f"top5 从「1 篇 ×5 段」变成「4 篇」，顺序仍按相似度：{ids}")

    print("[3] 配额用完的片段被丢弃，不会挤掉后面的文章")
    hits = [hit(1), hit(1), hit(1), hit(1), hit(2), hit(2), hit(2), hit(3)]
    res = select_diverse(hits, 4, 2)
    ids = [h["article_id"] for h in res]
    assert ids == [1, 1, 2, 2], ids
    ok(f"未越过 top_k 的裁剪结果：{ids}")

    print("[4] PDF 片段不受配额限制（同一份长 PDF 需要多段上下文）")
    hits = [hit(None), hit(None), hit(None), hit(None), hit(1), hit(1), hit(1)]
    res = select_diverse(hits, 6, 2)
    assert [h["source_type"] for h in res].count("pdf") == 4, res
    ok("4 段 PDF 全部保留，只对 article_id 生效")

    print("[5] 边界：空输入 / top_k=0 / 配额=0")
    assert select_diverse([], 5, 2) == []
    assert select_diverse([hit(1)], 0, 2) == []
    assert len(select_diverse([hit(1), hit(1), hit(1)], 3, 0)) == 3
    ok("空列表、top_k=0、配额=0 都不会抛错，且配额 0 等同于不限制")

    print("[6] 候选不够填满 top_k 时如实少返回，不补造数据")
    res = select_diverse([hit(1), hit(1), hit(1)], 5, 2)
    assert len(res) == 2, res
    ok("只有 1 篇文章时返回 2 段（而不是硬凑 5 段）")

    print("[7] 真实库规模下的覆盖率对比（1055 块 / 34 篇，每篇 31 块）")
    # 模拟最坏情况：一篇的连续 15 块（重叠导致分数都很高）排在前面
    sim = []
    for i in range(15):
        sim.append(hit(7, distance=0.10 + i * 0.001))
    for aid in range(1, 34):
        sim.append(hit(aid, distance=0.30 + aid * 0.001))
    before = select_diverse(sim, 5, None)
    after = select_diverse(sim, 5, 2)
    n_before = len({h["article_id"] for h in before})
    n_after = len({h["article_id"] for h in after})
    assert n_before == 1 and n_after == 4, (n_before, n_after)
    ok(f"覆盖文章数 {n_before} → {n_after}（top_k=5、单篇上限 2）")

    print("\n全部通过：检索配额逻辑符合预期。")


if __name__ == "__main__":
    main()
