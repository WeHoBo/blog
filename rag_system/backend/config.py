# -*- coding: utf-8 -*-
"""
config.py - 全局配置
------------------------------------------------
统一从 .env 读取所有配置项（DeepSeek API、Embedding 服务、RAG 参数），
并集中定义项目内各数据目录的路径，避免各模块重复加载环境变量。
"""
import os

from dotenv import load_dotenv

# 项目根目录 = backend/ 目录
BASE_DIR = os.path.dirname(os.path.abspath(__file__))

# 加载 backend/.env 中的环境变量（已存在则不会覆盖系统环境变量）
load_dotenv(os.path.join(BASE_DIR, ".env"))

# ---------------- 数据目录（运行时自动创建） ----------------
# 默认放在 backend/data 下；设置环境变量 RAG_DATA_DIR 可覆盖（供测试等场景使用独立数据目录）
DATA_DIR = os.getenv("RAG_DATA_DIR") or os.path.join(BASE_DIR, "data")
UPLOAD_DIR = os.path.join(DATA_DIR, "uploads")   # 上传的 PDF 副本
CHROMA_DIR = os.path.join(DATA_DIR, "chroma")    # ChromaDB 持久化目录
DB_PATH = os.path.join(DATA_DIR, "rag.db")       # SQLite 数据库文件

for _d in (DATA_DIR, UPLOAD_DIR, CHROMA_DIR):
    os.makedirs(_d, exist_ok=True)

# ---------------- DeepSeek 大模型配置（必填） ----------------
DEEPSEEK_API_KEY = os.getenv("DEEPSEEK_API_KEY", "")
DEEPSEEK_BASE_URL = os.getenv("DEEPSEEK_BASE_URL", "https://api.deepseek.com")
DEEPSEEK_MODEL = os.getenv("DEEPSEEK_MODEL", "deepseek-chat")

# ---------------- Embedding 向量服务配置（必填） ----------------
# 注意：DeepSeek 官方不提供 embedding 接口，这里必须指向一个
# OpenAI 兼容格式的 embedding 服务（如 SiliconFlow、one-api 网关、本地服务等）。
EMBEDDING_BASE_URL = os.getenv("EMBEDDING_BASE_URL", "")
EMBEDDING_API_KEY = os.getenv("EMBEDDING_API_KEY", "")
EMBEDDING_MODEL = os.getenv("EMBEDDING_MODEL", "")

# ---------------- RAG 参数（可在 .env 中调整） ----------------
CHUNK_SIZE = int(os.getenv("CHUNK_SIZE", "500"))        # 文本块大小（字符数）
CHUNK_OVERLAP = int(os.getenv("CHUNK_OVERLAP", "80"))   # 相邻文本块重叠度（字符数）
TOP_K = int(os.getenv("TOP_K", "5"))                    # 向量检索返回的相关片段数量

# ---------------- 服务调用鉴权（生产必须配置） ----------------
# 必须与 Java 后端 application(-prod).yml 的 ai.rag-token / 环境变量 RAG_API_TOKEN 完全一致。
# 为空时服务不校验令牌（仅适用于本机开发：服务默认只监听 127.0.0.1）。
RAG_API_TOKEN = os.getenv("RAG_API_TOKEN", "")
