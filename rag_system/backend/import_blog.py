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


def main() -> None:
    parser = argparse.ArgumentParser(description="导入博客文章到 RAG")
    parser.add_argument("--host", default=os.getenv("BLOG_DB_HOST", "127.0.0.1"))
    parser.add_argument("--user", default=os.getenv("BLOG_DB_USER", "root"))
    parser.add_argument("--password", default=os.getenv("BLOG_DB_PASSWORD", ""))
    parser.add_argument("--db", default=os.getenv("BLOG_DB_NAME", "blog"))
    parser.add_argument("--batch", type=int, default=20, help="每批发送的文章数")
    args = parser.parse_args()

    articles = fetch_articles(args.host, args.user, args.password, args.db)
    print(f"[导入] 从 MySQL 读到 {len(articles)} 篇已发布文章")

    total = 0
    for i in range(0, len(articles), args.batch):
        batch = articles[i:i + args.batch]
        payload = [{
            "id": a["id"],
            "title": a["title"],
            "content_md": a["content_md"] or "",
            "slug": a["slug"] or "",
            "category": a["category"] or "",
        } for a in batch]
        resp = requests.post(f"{RAG_BASE}/ingest-articles", json=payload, timeout=120)
        resp.raise_for_status()
        data = resp.json()
        total += data.get("chunks", 0)
        print(f"[导入] 批次 {i // args.batch + 1}: {len(batch)} 篇, {data.get('chunks', 0)} 块")
    print(f"[完成] 共 {len(articles)} 篇文章, {total} 个文本块已入库")


if __name__ == "__main__":
    main()
