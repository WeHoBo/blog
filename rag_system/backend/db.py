# -*- coding: utf-8 -*-
"""
db.py - SQLite3 业务数据库封装
------------------------------------------------
存储两类数据：
  1. documents   : 已上传文档的元信息（id、文件名、上传时间、切分块数）
  2. chat_history: 每轮 RAG 问答的会话历史（问题、回答、时间）

说明：sqlite3 连接默认只允许创建它的线程使用，而 FastAPI 会以多线程
并发处理请求，因此这里采用「单连接 + check_same_thread=False + 全局锁」
的方式保证并发安全，实现简单且对本地项目足够。
"""
import sqlite3
import threading
import uuid
from datetime import datetime

from config import DB_PATH, DATA_DIR

# ---------------- 全局单连接与线程锁 ----------------
_conn: sqlite3.Connection | None = None
_lock = threading.Lock()


def _init_tables(conn: sqlite3.Connection) -> None:
    """建表（幂等），首次连接时执行"""
    conn.executescript(
        """
        -- 文档元信息表
        CREATE TABLE IF NOT EXISTS documents (
            id          TEXT PRIMARY KEY,   -- 文档唯一 id（uuid 字符串）
            filename    TEXT NOT NULL,      -- 原始文件名
            chunk_count INTEGER NOT NULL DEFAULT 0,  -- 切分后的文本块数量
            created_at  TEXT NOT NULL       -- 上传时间（ISO 格式）
        );

        -- 聊天会话历史表
        CREATE TABLE IF NOT EXISTS chat_history (
            id         INTEGER PRIMARY KEY AUTOINCREMENT,
            question   TEXT NOT NULL,       -- 用户问题
            answer     TEXT NOT NULL,       -- 大模型回答
            created_at TEXT NOT NULL        -- 问答时间（ISO 格式）
        );
        """
    )
    conn.commit()


def get_conn() -> sqlite3.Connection:
    """获取全局连接（首次调用时初始化数据库）"""
    global _conn
    if _conn is None:
        import os
        os.makedirs(DATA_DIR, exist_ok=True)
        _conn = sqlite3.connect(DB_PATH, check_same_thread=False)
        _conn.row_factory = sqlite3.Row  # 让查询结果可以按字段名访问
        _init_tables(_conn)
    return _conn


# ---------------- 文档 CRUD ----------------
def add_document(filename: str, chunk_count: int) -> dict:
    """写入一条文档元信息，返回完整记录（含自动生成的 id）"""
    doc_id = uuid.uuid4().hex
    created_at = datetime.now().isoformat(timespec="seconds")
    with _lock:
        conn = get_conn()
        conn.execute(
            "INSERT INTO documents (id, filename, chunk_count, created_at) VALUES (?, ?, ?, ?)",
            (doc_id, filename, chunk_count, created_at),
        )
        conn.commit()
    return {"id": doc_id, "filename": filename, "chunk_count": chunk_count, "created_at": created_at}


def list_documents() -> list[dict]:
    """按上传时间倒序返回全部文档"""
    with _lock:
        rows = get_conn().execute(
            "SELECT id, filename, chunk_count, created_at FROM documents ORDER BY created_at DESC"
        ).fetchall()
    return [dict(r) for r in rows]


# ---------------- 聊天历史 CRUD ----------------
def add_chat(question: str, answer: str) -> None:
    """保存一轮问答记录"""
    created_at = datetime.now().isoformat(timespec="seconds")
    with _lock:
        conn = get_conn()
        conn.execute(
            "INSERT INTO chat_history (question, answer, created_at) VALUES (?, ?, ?)",
            (question, answer, created_at),
        )
        conn.commit()


def list_chats(limit: int = 50) -> list[dict]:
    """按时间倒序返回最近 limit 条问答记录（前端展示历史用）"""
    with _lock:
        rows = get_conn().execute(
            "SELECT id, question, answer, created_at FROM chat_history ORDER BY id DESC LIMIT ?",
            (limit,),
        ).fetchall()
    return [dict(r) for r in rows]
