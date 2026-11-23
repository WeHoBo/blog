# -*- coding: utf-8 -*-
"""
import_blog.py - 将博客 MySQL 中的文章批量导入 RAG 向量库
--------------------------------------------------------
用法（在 backend 目录下）：
    python import_blog.py [--host 127.0.0.1] [--user root] [--password xxx] [--db blog]
    python import_blog.py            # 优先读环境变量 BLOG_DB_*，缺省连本机

原理：直连博客 MySQL 读取 article 表（status=1 已发布、is_deleted=0），
拼上分类名后调用本服务 /ingest-articles 入库。
"""
import argparse
import os
import sys

import requests

# 本地 RAG 服务地址（与 main.py 启动端口一致）
RAG_BASE = os.getenv("RAG_BASE", "http://127.0.0.1:8000")

# 调用令牌：main.py 把 verify_token 挂在 app 级依赖上，/ingest-articles 也要过。
# 以前 RAG_API_TOKEN 为空时脚本能跑，配上 token 后就会 401 —— 所以必须显式带上。
# 用 config 读而不是 os.getenv：config 会 load_dotenv(backend/.env)，
# 直接 os.getenv 在 systemd 之外手动执行时读不到 .env 里的值。
try:
    import config
    RAG_TOKEN = config.RAG_API_TOKEN
except Exception:
    RAG_TOKEN = os.getenv("RAG_API_TOKEN", "")


def _headers() -> dict:
    return {"X-RAG-Token": RAG_TOKEN} if RAG_TOKEN else {}


def fetch_articles(host: str, user: str, password: str, db: str) -> list[dict]:
    """直连 MySQL 读取已发布文章（含分类名）"""
    import pymysql

    conn = pymysql.connect(
        host=host, port=3306, user=user, password=password, database=db,
        charset="utf8mb4", cursorclass=pymysql.cursors.DictCursor,
    )
    try:
        with conn.cursor() as cur:
            cur.execute(
                """
                SELECT a.id, a.title, a.slug, a.content_md, c.name AS category
                FROM article a
                LEFT JOIN category c ON a.category_id = c.id
                WHERE a.status = 1 AND a.is_deleted = 0
                ORDER BY a.id
                """
            )
            return list(cur.fetchall())
    finally:
        conn.close()


def prune_index(keep_ids: list) -> None:
    """
    全量对账：删掉「库里还有、但已经不在公开名单里」的文章向量。

    /ingest-articles 的语义是「传进来的文章各自覆盖自己」，所以一篇文章从公开变成草稿后，
    它根本不会出现在这份名单里 —— 旧向量既不会被替换、也不会被删除，永久留在库中。
    结果是：读者在文章页已经看不到的内容，问 AI 仍然能被引用出来。
    """
    try:
        resp = requests.post(
            f"{RAG_BASE}/prune-index",
            json={"keep_ids": list(keep_ids)},
            headers=_headers(),
            timeout=300,
        )
        resp.raise_for_status()
        data = resp.json()
        removed = data.get("removed") or []
        print(f"[对账] 库中 {data.get('existing', 0)} 篇, 应保留 {data.get('kept', 0)} 篇,"
              f" 清理残留 {len(removed)} 篇" + (f": {removed}" if removed else ""))
    except Exception as e:
        # 对账失败不该让整次导入看起来失败：文章确实已经进库了，只是残留还没清
        print(f"[对账] 失败（不影响导入结果，可稍后单独重跑）: {e}")


def main() -> None:
    parser = argparse.ArgumentParser(description="导入博客文章到 RAG")
    parser.add_argument("--host", default=os.getenv("BLOG_DB_HOST", "127.0.0.1"))
    parser.add_argument("--user", default=os.getenv("BLOG_DB_USER", "root"))
    parser.add_argument("--password", default=os.getenv("BLOG_DB_PASSWORD", ""))
    parser.add_argument("--db", default=os.getenv("BLOG_DB_NAME", "blog"))
    parser.add_argument("--batch", type=int, default=20, help="每批发送的文章数")
    parser.add_argument(
        "--no-prune",
        action="store_true",
        help="跳过全量对账。默认会删掉「库里有、但已不在公开名单里」的文章向量 —— "
             "这是清理下架文章残留的唯一入口，除非你明确知道自己在做什么，否则别加这个参数",
    )
    parser.add_argument(
        "--prune-only",
        action="store_true",
        help="只做对账、不重新入库。下架了几篇文章想立刻清掉残留时用这个 —— "
             "不用把上千个块重新 embedding 一遍（省钱也省时间）",
    )
    args = parser.parse_args()

    articles = fetch_articles(args.host, args.user, args.password, args.db)
    print(f"[导入] 从 MySQL 读到 {len(articles)} 篇已发布文章")
    print(f"[导入] 调用令牌: {'已携带' if RAG_TOKEN else '未配置（服务端也未配 token 时才能通过）'}")

    if args.prune_only:
        prune_index([a["id"] for a in articles])
        return

    total = 0
    failed_total = 0
    for i in range(0, len(articles), args.batch):
        batch = articles[i:i + args.batch]
        payload = [{
            "id": a["id"],
            "title": a["title"],
            "content_md": a["content_md"] or "",
            "slug": a["slug"] or "",
            "category": a["category"] or "",
        } for a in batch]
        resp = requests.post(
            f"{RAG_BASE}/ingest-articles",
            json=payload,
            headers=_headers(),
            timeout=180,
        )
        resp.raise_for_status()
        data = resp.json()
        total += data.get("chunks", 0)
        failed = data.get("failed") or []
        failed_total += len(failed)
        print(f"[导入] 批次 {i // args.batch + 1}: {len(batch)} 篇, {data.get('chunks', 0)} 块"
              + (f", 失败 {len(failed)} 篇" if failed else ""))
        # ⚠️ 必须把 failed 打出来：接口对单篇失败只放进这个数组，
        # HTTP 依然 200。不打的话就是"导入完成"但检索永远搜不到某几篇。
        for f in failed:
            print(f"        x id={f.get('id')} {str(f.get('title', ''))[:28]} -> {f.get('error')}")
    print(f"[完成] 共 {len(articles)} 篇文章, {total} 个文本块已入库, 失败 {failed_total} 篇")

    # ---------- 全量对账：清掉已下架文章的残留向量 ----------
    if not args.no_prune:
        prune_index([a["id"] for a in articles])

    if failed_total:
        sys.exit(1)


if __name__ == "__main__":
    main()
