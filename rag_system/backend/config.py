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

# 同一篇文章在检索结果里最多占几段（0 = 不限制）。
# 相邻块之间有 CHUNK_OVERLAP 重叠，同一篇的连续片段相似度极高，
# 不限制时 top5 常被同一篇占满 → 「站内有哪些文章讲过 X」这类问题答不全。
# 只作用于站内全局检索（search_knowledge 工具）；读单篇文章时不生效。
RETRIEVE_PER_ARTICLE = int(os.getenv("RETRIEVE_PER_ARTICLE", "2"))

# ---------------- 服务调用鉴权（生产必须配置） ----------------
# 必须与 Java 后端 application(-prod).yml 的 ai.rag-token / 环境变量 RAG_API_TOKEN 完全一致。
# 为空时服务不校验令牌（仅适用于本机开发：服务默认只监听 127.0.0.1）。
RAG_API_TOKEN = os.getenv("RAG_API_TOKEN", "")

# ---------------- Java 内部接口（用量上报） ----------------
# RAG 不直连 MySQL，用量通过 Java 的 /internal/ai/usage 落库。
JAVA_INTERNAL_BASE = os.getenv("JAVA_INTERNAL_BASE", "http://127.0.0.1:8080")

# 上报失败不影响用户，只打日志；联调时可设 0 关闭。
USAGE_REPORT_ENABLED = os.getenv("USAGE_REPORT_ENABLED", "1") != "0"

# 是否请求模型返回 usage（stream_options.include_usage）。
# 少数厂商不认这个参数，代码里会自动降级重试。
LLM_INCLUDE_USAGE = os.getenv("LLM_INCLUDE_USAGE", "1") != "0"

# ---------------- Agent 循环参数（见设计文档 §6.2） ----------------
# 步数再多收益递减，成本线性上涨；实测 3 步内能解决绝大多数站内问题。
AGENT_MAX_STEPS = int(os.getenv("AGENT_MAX_STEPS", "5"))

# 总时间预算，必须 < Java 的 300s < Nginx 的 300s，给收尾留余量。
AGENT_TOTAL_MS = int(os.getenv("AGENT_TOTAL_MS", "240000"))

# 单次工具调用超时。超时即视为失败，把错误文本回填给模型，让它自己换路子。
AGENT_TOOL_TIMEOUT = int(os.getenv("AGENT_TOOL_TIMEOUT", "10"))

# 决策段温度：固定偏低，因为"选哪个工具"不该有随机性。
# 输出段的温度不在这里配 —— 用后台「AI 模型」里每个模型自己的 temperature，
# 否则管理员在后台改了温度却不生效，等于给了个假开关。
AGENT_TEMPERATURE_DECIDE = float(os.getenv("AGENT_TEMPERATURE_DECIDE", "0.2"))

# 带几轮历史进上下文。多轮记忆的最小可用值，再多会显著抬高每轮 prompt 成本。
AGENT_HISTORY_TURNS = int(os.getenv("AGENT_HISTORY_TURNS", "3"))

# 是否启用工具（Agent 模式）。设 0 可一键退回"纯 RAG 检索问答"的老行为，用于线上排障。
AGENT_TOOLS_ENABLED = os.getenv("AGENT_TOOLS_ENABLED", "1") != "0"

# ---------------- 联网搜索（P3，见设计文档 §9） ----------------
# 总开关。关掉后 web_search 工具**根本不进 tools 列表** —— 模型连看都看不到，
# 也就不会为了一个注定失败的调用白花 token。
WEB_SEARCH_ENABLED = os.getenv("WEB_SEARCH_ENABLED", "1") != "0"

# provider 顺序即降级链（逗号分隔）。目前支持：tavily、bocha（博查）。
WEB_SEARCH_PROVIDERS = [
    p.strip().lower()
    for p in os.getenv("WEB_SEARCH_PROVIDERS", "tavily,bocha").split(",")
    if p.strip()
]

# 全局密钥。若多个 provider 各用不同 key，可另配 WEB_SEARCH_API_KEY_TAVILY /
# WEB_SEARCH_API_KEY_BOCHA，优先于这个值。
# **留空 = 不启用联网搜索**（工具不挂载），而不是挂一个每次都失败的摆设。
WEB_SEARCH_API_KEY = os.getenv("WEB_SEARCH_API_KEY", "").strip()

# 单个 provider 的超时。设计文档要求 3 秒 —— 搜索是"锦上添花"，
# 为了它把整个回答拖慢到十几秒，用户体感是"AI 卡住了"。
WEB_SEARCH_TIMEOUT = float(os.getenv("WEB_SEARCH_TIMEOUT", "3"))

# 结果缓存秒数。同一个问题被反复问（含多人问同一件事）不必重复付费。
WEB_SEARCH_CACHE_SECONDS = int(os.getenv("WEB_SEARCH_CACHE_SECONDS", "1800"))

# 单轮对话最多调几次联网搜索。检索轮次有上限，联网次数也要有 ——
# 否则一个"帮我查一堆资料"的提问就能刷掉几十次搜索费。
WEB_SEARCH_MAX_CALLS = int(os.getenv("WEB_SEARCH_MAX_CALLS", "2"))

# 只信任这些协议，其他（file:// javascript: data:）一律丢弃
WEB_SEARCH_ALLOWED_SCHEMES = ("http", "https")

