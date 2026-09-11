# -*- coding: utf-8 -*-
"""
main.py - FastAPI 后端入口
------------------------------------------------
接口列表：
  POST /upload     上传 PDF 文档（解析 -> 切分 -> 向量化 -> 入库）
  GET  /documents  获取已上传文档列表
  POST /chat       RAG 问答接口（向量检索 + DeepSeek 流式回答）
  GET  /history    获取聊天会话历史

启动方式（在 backend 目录下）：
  uvicorn main:app --host 127.0.0.1 --port 8000
  说明：服务只监听回环地址，仅由 Java 后端（AiController）经 127.0.0.1 转发调用；
       生产环境请在 .env 配置 RAG_API_TOKEN，与 Java 端 ai.rag-token 保持一致。
"""
import asyncio
import os
import shutil
import uuid
from contextlib import asynccontextmanager
from typing import Any

# ChromaDB 需要 sqlite3 >= 3.35.0，旧系统（如 Alibaba Cloud Linux 3）用 pysqlite3-binary 替代
try:
    import pysqlite3
    import sys
    sys.modules['sqlite3'] = pysqlite3
except ImportError:
    pass

import uvicorn
from fastapi import Depends, FastAPI, File, Header, HTTPException, Query, UploadFile
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import StreamingResponse
from pydantic import BaseModel, Field

import config
import db
import pdf_parser
from llm_client import LLMClient
from vector_store import EmbeddingClient, VectorStore

# ---------------- 全局单例（启动时初始化） ----------------
vector_store: VectorStore | None = None
llm: LLMClient | None = None


async def verify_token(x_rag_token: str | None = Header(default=None, alias="X-RAG-Token")):
    """全局依赖：校验调用方令牌。

    - 已配置 RAG_API_TOKEN：请求头 X-RAG-Token 必须完全一致，否则 401。
    - 未配置：放行（仅适用于本机开发，服务默认只监听 127.0.0.1）。
    """
    if not config.RAG_API_TOKEN:
        return
    if x_rag_token != config.RAG_API_TOKEN:
        raise HTTPException(status_code=401, detail="无效的调用令牌")


@asynccontextmanager
async def lifespan(_: FastAPI):
    """应用启动时初始化向量库与大模型客户端（若 .env 未配置会在此处给出明确报错）"""
    global vector_store, llm
    vector_store = VectorStore(EmbeddingClient())  # 初始化 ChromaDB + Embedding 客户端
    llm = LLMClient()                              # 初始化 DeepSeek 客户端
    print(f"[启动] 向量库就绪，当前共有 {vector_store.count()} 个文本块")
    if not config.RAG_API_TOKEN:
        print("[启动][警告] 未配置 RAG_API_TOKEN，接口不校验调用令牌（仅建议本机开发使用）")
    yield


app = FastAPI(
    title="企业级RAG智能文档问答系统",
    description="上传 PDF 建立企业知识库，基于 DeepSeek + ChromaDB 实现流式 RAG 问答",
    version="1.0.0",
    lifespan=lifespan,
    dependencies=[Depends(verify_token)],
)

# 允许前端开发服务器跨域访问（博客通过 Java 代理转发，这里保留开发期跨域）
app.add_middleware(
    CORSMiddleware,
    allow_origins=["http://localhost:5173", "http://127.0.0.1:5173", "https://codeup.asia"],
    allow_methods=["*"],
    allow_headers=["*"],
)


# ---------------- 请求模型 ----------------
class ChatRequest(BaseModel):
    question: str = Field(..., min_length=1, max_length=2000, description="用户问题")
    doc_id: str | None = Field(None, description="限定检索的文档 id，为空则检索全部文档")
    article_id: str | None = Field(None, description="限定检索的博客文章 id，为空则检索全部")
    top_k: int = Field(config.TOP_K, ge=1, le=20, description="检索返回的相关片段数量")


class ArticleIn(BaseModel):
    id: int | str
    title: str
    content_md: str
    slug: str = ""
    category: str = ""


class FreeChatRequest(BaseModel):
    question: str = Field(..., min_length=1, max_length=6000, description="用户问题/代码/文章内容")
    mode: str = Field("explain", description="explain=代码解释/总结；chat=自由对话")


# ---------------- 接口一：上传 PDF ----------------
@app.post("/upload")
async def upload_pdf(file: UploadFile = File(..., description="要上传的 PDF 文件")):
    """接收 PDF -> 保存副本 -> 解析提取文本 -> 切分 -> 向量化入库 -> 记录元信息"""
    filename = file.filename or "unnamed.pdf"
    if not filename.lower().endswith(".pdf"):
        raise HTTPException(status_code=400, detail="仅支持上传 PDF 文件")

    # 1) 保存上传文件副本（用 uuid 前缀避免同名覆盖）
    save_name = f"{uuid.uuid4().hex}_{os.path.basename(filename)}"
    save_path = os.path.join(config.UPLOAD_DIR, save_name)
    with open(save_path, "wb") as f:
        shutil.copyfileobj(file.file, f)

    try:
        # 2) 解析 PDF（CPU 密集 + 文件 IO，放线程池避免阻塞事件循环）
        page_texts = await asyncio.to_thread(pdf_parser.extract_text, save_path)
        # 3) 文本切分（块大小与重叠度来自 .env 配置）
        chunks = await asyncio.to_thread(
            pdf_parser.split_text, page_texts, config.CHUNK_SIZE, config.CHUNK_OVERLAP
        )
        # 4) 批量向量化并写入 ChromaDB（网络 + 磁盘 IO，同样放线程池）
        doc_id = uuid.uuid4().hex
        await asyncio.to_thread(
            vector_store.add_document_chunks, doc_id, filename, chunks
        )
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))
    except Exception as e:
        # 失败时清理已保存的文件副本
        if os.path.exists(save_path):
            os.remove(save_path)
        raise HTTPException(status_code=500, detail=f"文档处理失败: {e}")

    # 5) SQLite 记录文档元信息
    doc = db.add_document(filename, len(chunks))
    print(f"[上传] 文档《{filename}》切分为 {len(chunks)} 块，已写入向量库")
    return {"message": "上传成功", "document": doc}


# ---------------- 接口二：文档列表 ----------------
@app.get("/documents")
def get_documents():
    """返回已上传文档列表（id、文件名、块数、上传时间）"""
    return {"documents": db.list_documents()}


# ---------------- 接口三：博客文章批量入库 ----------------
@app.post("/ingest-articles")
async def ingest_articles(articles: list[ArticleIn]):
    """接收博客文章列表 -> 切分 -> 向量化入库（幂等：重复入库同一篇文章会先清旧块）"""
    total_chunks = 0
    failed = []
    for art in articles:
        if not art.content_md or not art.content_md.strip():
            continue
        article_id = str(art.id)
        try:
            # 清掉该文章的旧向量块，保证重复导入不产生重复
            await asyncio.to_thread(vector_store.delete_article, article_id)
            # 复用 PDF 的切分逻辑：把整篇 markdown 作为一页切块
            chunks = await asyncio.to_thread(
                pdf_parser.split_text, [(1, art.content_md)], config.CHUNK_SIZE, config.CHUNK_OVERLAP
            )
            # 过滤空文本块（SiliconFlow embedding 拒绝空字符串）
            chunks = [c for c in chunks if c.get("text") and c["text"].strip()]
            n = await asyncio.to_thread(
                vector_store.add_article_chunks, article_id, art.title,
                art.slug, art.category, chunks
            )
            total_chunks += n
        except Exception as e:
            failed.append({"id": article_id, "title": art.title, "error": str(e)})
    print(f"[入库] 文章 {len(articles)} 篇，成功块 {total_chunks}，失败 {len(failed)} 篇: {failed}")
    return {"message": "入库完成", "articles": len(articles), "chunks": total_chunks, "failed": failed}


# ---------------- 接口四：自由问答（非 RAG，代码解释/全文总结用，流式） ----------------
@app.post("/free-chat")
async def free_chat(req: FreeChatRequest):
    """自由问答：不检索向量库，直接把用户内容交给 LLM（用于解释选中代码、总结全文）。"""
    from llm_client import CODE_EXPLAIN_PROMPT

    system_prompt = CODE_EXPLAIN_PROMPT if req.mode == "explain" else (
        "你是一名友好的技术助手，请用简洁清晰的中文回答。回答使用 Markdown 格式。"
    )
    messages = [
        {"role": "system", "content": system_prompt},
        {"role": "user", "content": req.question},
    ]

    async def generate():
        answer = ""
        async for delta in llm.stream_chat(messages):
            answer += delta
            yield delta
        await asyncio.to_thread(db.add_chat, f"[free] {req.question[:100]}", answer)

    return StreamingResponse(
        generate(),
        media_type="text/plain; charset=utf-8",
        headers={"Cache-Control": "no-cache", "X-Accel-Buffering": "no"},
    )


# ---------------- 接口五：RAG 问答（流式） ----------------
@app.post("/chat")
async def chat(req: ChatRequest):
    """RAG 问答：向量检索相关资料 -> 组装提示词 -> DeepSeek 流式回答 -> 保存历史"""
    # 1) 向量检索：对问题生成向量，在 ChromaDB 中召回 top_k 个相关片段
    contexts = await asyncio.to_thread(
        vector_store.search, req.question, req.top_k, req.doc_id
    )
    if req.article_id:
        # 只保留属于该文章的结果（向量层无法直接 where 在 query 上过滤，这里二次过滤）
        contexts = [c for c in contexts if str(c.get("article_id", "")) == str(req.article_id)]
    # 2) 组装 RAG 提示词
    messages = llm.build_rag_prompt(req.question, contexts)

    async def generate():
        """流式生成器：边接收 DeepSeek 输出边转发给前端，结束后落库"""
        # 向量库为空或未召回任何片段时，不再调用大模型，直接提示用户
        if not contexts:
            answer = "抱歉，向量库中还没有相关资料。请先上传文档或等待文章导入后再提问。"
            await asyncio.to_thread(db.add_chat, req.question, answer)
            yield answer
            return

        answer = ""
        # 3) 调用 DeepSeek 流式接口，逐段转发
        async for delta in llm.stream_chat(messages):
            answer += delta
            yield delta
        # 4) 流结束后将本轮问答写入 SQLite 历史
        await asyncio.to_thread(db.add_chat, req.question, answer)

        # 5) 追加来源信息（JSON 行，前端据此展示相关文章卡片）
        sources = []
        seen = set()
        for c in contexts:
            if c.get("source_type") == "article":
                key = c.get("article_id")
                if key in seen:
                    continue
                seen.add(key)
                sources.append({
                    "type": "article",
                    "article_id": c.get("article_id"),
                    "title": c.get("title", ""),
                    "slug": c.get("slug", ""),
                    "url": c.get("url", ""),
                    "category": c.get("category", ""),
                })
            else:
                key = c.get("filename")
                if key in seen:
                    continue
                seen.add(key)
                sources.append({
                    "type": "pdf",
                    "filename": c.get("filename", ""),
                    "page": c.get("page", 0),
                })
        import json as _json
        yield "\n@@SOURCES@@\n" + _json.dumps(sources, ensure_ascii=False)
        await asyncio.to_thread(db.add_chat, req.question, answer)

    # 以 text/plain 流式返回，前端用 ReadableStream 逐块读取
    return StreamingResponse(
        generate(),
        media_type="text/plain; charset=utf-8",
        headers={"Cache-Control": "no-cache", "X-Accel-Buffering": "no"},
    )


# ---------------- 接口四：聊天历史 ----------------
@app.get("/history")
def get_history(limit: int = Query(default=50, ge=1, le=200)):
    """返回最近 limit 条问答历史"""
    return {"history": db.list_chats(limit)}


if __name__ == "__main__":
    # 支持直接 python main.py 启动：只监听回环地址，且禁用 reload（自动重载仅用于本地调试）
    uvicorn.run("main:app", host="127.0.0.1", port=8000, reload=False)
