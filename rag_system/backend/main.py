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
import concurrent.futures
import json
import os
import shutil
import threading
import time
import uuid
from contextlib import asynccontextmanager, suppress
from typing import Any

# ChromaDB 需要 sqlite3 >= 3.35.0，旧系统（Alibaba Cloud Linux 3 是 3.26）靠 pysqlite3 顶替。
# ⚠️ 必须排在最前面 —— 后面 fastapi / uvicorn / chromadb 任何一个先导入都会锁死旧 sqlite3。
import sqlite_compat  # noqa: F401

import uvicorn
from fastapi import Depends, FastAPI, File, Header, HTTPException, Query, Request, UploadFile
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import StreamingResponse
from pydantic import BaseModel, Field

import agent
import agent_tools
import config
import db
import pdf_parser
import usage_reporter
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
    # 工具层复用同一个向量库实例：search_knowledge 在进程内直接查，省一次 HTTP 往返
    agent_tools.bind_vector_store(vector_store)
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
# ⚠️ 这两个 max_length 是"前后端契约"，改任一处都要同步前端：
#    - question 上限 8000：输入框限 2000（AiFloat.vue 的 maxlength），
#      但划词「搜一下」这类**程序拼出来**的提问会带内容（前端截到 1500 字再拼）。
#      留 8000 是兜底：以前这里是 2000，选一段 3000 字点「搜一下」直接 422 —— 用户毫无头绪。
#    - free 上限 20000：文章页「AI 总结本文」会送正文，前端截 18000 字（ArticleDetail.vue
#      的 MAX_SUMMARY_CHARS）。以前这里是 6000，于是**任何超过 6000 字的文章总结必 422**。
#      20000 而非正好 18000，是给"标题 + 模板前缀"留余量。
class ChatRequest(BaseModel):
    question: str = Field(..., min_length=1, max_length=8000, description="用户问题")
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
    question: str = Field(..., min_length=1, max_length=20000, description="用户问题/代码/文章内容")
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


# ---------------- 接口二点五：健康检查 ----------------
@app.get("/health")
def health(probe: bool = False):
    """健康检查：供 Java/运维确认服务、向量库与 **embedding 可用性**

    `?probe=1` 跳过 60 秒缓存、立刻重测一次 embedding（换完 key 验证用）。
    这里刻意用普通 `bool` 默认值而不是 `Query(...)`：后者在 Python 层面是个对象，
    单元测试直接调 `health()` 时会被当成真值，把缓存绕过掉。
    """
    chunks = 0
    try:
        if vector_store is not None:
            chunks = vector_store.count()
    except Exception:
        pass
    embed = _probe_embedding(force=probe)
    cfg = llm.current() if llm is not None else {}
    return {
        # ok = 服务与检索链路都正常；degraded = 进程活着但语义检索不可用。
        # 不直接报 ok 的原因：embedding 挂掉时所有检索都会失败，AI 只会答
        # 「站内没有相关内容」—— 服务"看起来健康"是把故障藏起来，不是健康。
        "status": "degraded" if embed.get("ok") is False else "ok",
        "chunks": chunks,
        # 真打一次 embedding 的结果。之前的盲区：chunks 读的是 Chroma 本地元数据，
        # key 失效（401 / code 30014）时它照样返回 1055，于是全站 AI 静默失效。
        "embedding": embed,
        "token_required": bool(config.RAG_API_TOKEN),
        "agent": True,
        # 当前生效模型来自哪里：registry=后台注册表，env=.env 兜底。
        # 部署后先看这一项，能立刻判断"后台配的模型到底有没有生效"
        "model": cfg.get("model"),
        "modelSource": cfg.get("source"),
        "supportTools": cfg.get("support_tools"),
        # 联网搜索是否可用（开关 + 至少一个 provider 有密钥）。
        # 部署后看这一项就知道"web_search 到底挂上没挂上"，不用等用户提问才发现。
        "webSearch": _web_search_available(),
    }


# ---------------- embedding 可用性探测 ----------------

# /health 可能被监控按分钟拉，真打一次 embedding 是要花钱和耗时的，所以缓存 60 秒。
_EMBED_PROBE_TTL = 60.0
# 探测卡住不能把 /health 一起拖死（EmbeddingClient 自身超时是 60s，太长了）
_EMBED_PROBE_TIMEOUT = 8.0
_embed_probe: dict[str, Any] = {"ok": None, "error": "", "model": "", "at": 0.0}
# 上一次探测结果，只用来判断"是否发生翻转"，避免每分钟重复刷同一行日志
_embed_ok_before: bool | None = None
_embed_probe_lock = threading.Lock()


def _probe_embedding(force: bool = False) -> dict[str, Any]:
    """真打一次 embedding 接口，确认「向量化」这条路是活的。

    为什么必须真打：`chunks` 读的是 Chroma 的本地元数据，跟 embedding 服务无关。
    key 失效（401 / code 30014 Token is invalid）时它照样返回 1055，
    于是 /health 一片绿、后台看不出异常，而所有语义检索都在 500，
    AI 一律回答「站内没有相关内容」—— 这是本项目真踩过的事故，
    排查时最费时间的一点就是"健康检查骗了我"。

    返回：{ok, error, model}，ok 为 False 表示检索链路不可用。
    结果缓存 60s；`?probe=1` 可强制重测（改完 key 立刻验证用）。
    """
    global _embed_ok_before
    now = time.time()
    with _embed_probe_lock:
        if (not force and _embed_probe["ok"] is not None
                and now - _embed_probe["at"] < _EMBED_PROBE_TTL):
            return {"ok": _embed_probe["ok"], "error": _embed_probe["error"],
                    "model": _embed_probe["model"]}

        model = getattr(config, "EMBEDDING_MODEL", "") or ""
        ok: bool | None
        error = ""

        if vector_store is None:
            ok, error = False, "向量库未初始化（多为 .env 缺 EMBEDDING_BASE_URL/KEY/MODEL）"
        else:
            ok, error = _call_embedding_once()

        if _embed_ok_before is None:
            if ok:
                print(f"[Health] embedding 探测通过（{model or '未配置模型'}）")
            else:
                print(f"[Health] ⚠️ embedding 不可用（{model or '未配置模型'}）：{error}")
                print("[Health] ⚠️ 语义检索会全部失败，AI 将无法基于站内内容回答 —— 请检查 EMBEDDING_API_KEY")
        elif _embed_ok_before is not ok:
            if ok:
                print(f"[Health] embedding 已恢复（{model or '未配置模型'}）")
            else:
                print(f"[Health] ⚠️ embedding 刚刚失效（{model or '未配置模型'}）：{error}")
        _embed_ok_before = ok

        _embed_probe.update({"ok": ok, "error": error, "model": model, "at": now})
        return {"ok": ok, "error": error, "model": model}


def _call_embedding_once() -> tuple[bool, str]:
    """发一条最短的 embedding 请求。成功返回 (True, "")，失败返回 (False, 原因)。

    单独抽出来是为了让探测和"是不是超时"分开处理：超时后线程还在跑（客户端自身
    60s 超时），这里不 join，避免把 /health 也拖到 60 秒。
    """
    executor = concurrent.futures.ThreadPoolExecutor(max_workers=1)
    try:
        future = executor.submit(vector_store.embedding.embed_query, "ping")
        vector = future.result(timeout=_EMBED_PROBE_TIMEOUT)
        if not vector:
            return False, "embedding 返回了空向量"
        return True, ""
    except concurrent.futures.TimeoutError:
        return False, f"embedding 探测超时（>{_EMBED_PROBE_TIMEOUT:.0f}s）"
    except Exception as e:
        # 401 会以 AuthenticationError 出现；把类名带上，排查时一眼能认出是 key 问题
        return False, f"{type(e).__name__}: {e}"
    finally:
        executor.shutdown(wait=False)


def _web_search_available() -> bool:
    try:
        import web_search
        return web_search.available()
    except Exception:
        return False


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


# ---------------- SSE 工具 ----------------
async def _sse_stream(events, request: Request):
    """
    把 agent 产出的 (event, data) 异步序列包成真正的 SSE 字节流。

    两个必须做的事：
      1. 心跳：每 15s 发一行 `: ping`。Nginx 默认 60s 空闲就断连，
         而一次工具调用最长 10s、一轮问答可能几十秒没输出，不心跳会被掐。
      2. 断连取消：前端点停止后，这里要尽快把后台任务 cancel 掉，
         否则模型还在吐 token、还在烧钱，用户那边已经什么都不显示了。
    """
    queue: asyncio.Queue = asyncio.Queue()

    async def pump():
        try:
            async for item in events:
                await queue.put(item)
        except asyncio.CancelledError:
            raise
        except Exception as e:
            print(f"[SSE] 生成器异常: {e}")
            await queue.put(("error", {"code": "INTERNAL", "message": f"服务内部错误：{e}"}))
        finally:
            await queue.put(None)

    task = asyncio.create_task(pump())
    try:
        while True:
            try:
                item = await asyncio.wait_for(queue.get(), timeout=15)
            except asyncio.TimeoutError:
                if await request.is_disconnected():
                    break
                yield ": ping\n\n"
                continue
            if item is None:
                break
            event, data = item
            yield f"event: {event}\ndata: {json.dumps(data, ensure_ascii=False)}\n\n"
    finally:
        if not task.done():
            task.cancel()
        with suppress(asyncio.CancelledError, Exception):
            await task


SSE_HEADERS = {"Cache-Control": "no-cache", "X-Accel-Buffering": "no", "Connection": "keep-alive"}


# ---------------- 接口四：自由问答（代码解释 / 全文总结，SSE） ----------------
@app.post("/free-chat")
async def free_chat(
    req: FreeChatRequest,
    request: Request,
    x_ai_user_id: str | None = Header(default=None, alias="X-AI-User-Id"),
    x_ai_model: str | None = Header(default=None, alias="X-AI-Model"),
):
    """自由问答：不检索、不调工具，直接把用户内容交给 LLM。仍然走 SSE，前端只需一套解析。"""
    model_code = usage_reporter.resolve_model_code(x_ai_model)
    events = agent.run_free(req.question, llm, req.mode,
                            user_id=x_ai_user_id, model_code=model_code)
    return StreamingResponse(_sse_stream(_tap_history(events, f"[free] {req.question[:100]}"),
                                         request),
                             media_type="text/event-stream; charset=utf-8",
                             headers=SSE_HEADERS)


# ---------------- 接口五：Agent 问答（SSE） ----------------
@app.post("/chat")
async def chat(
    req: ChatRequest,
    request: Request,
    x_ai_user_id: str | None = Header(default=None, alias="X-AI-User-Id"),
    x_ai_model: str | None = Header(default=None, alias="X-AI-Model"),
):
    """
    Agent 问答：模型自己决定要不要检索、检索什么，最多 5 轮工具调用。

    与旧版的区别：不再"先向量检索再拼提示词"，检索变成了 search_knowledge 工具，
    由模型按需调用 —— 问"你能做什么"这种问题就不必白跑一次向量库。
    """
    model_code = usage_reporter.resolve_model_code(x_ai_model)
    events = agent.run_chat(req.question, llm,
                            user_id=x_ai_user_id, model_code=model_code,
                            article_id=req.article_id)
    return StreamingResponse(_sse_stream(_tap_history(events, req.question), request),
                             media_type="text/event-stream; charset=utf-8",
                             headers=SSE_HEADERS)


async def _tap_history(events, question: str):
    """
    旁路记录回答全文，流结束后写进本地 SQLite 历史（只用于 /history 调试与回溯）。

    ⚠️ 这张表没有 user_id，所以**不能**反过来当多轮对话记忆用 ——
    那会把别人的提问塞进你的上下文。多轮记忆要等 trace 表按用户查（见文档 §6.2）。
    """
    answer: list[str] = []

    def _on_token(text: str) -> None:
        answer.append(text)

    async for event, data in events:
        if event == "token":
            _on_token(str(data.get("text") or ""))
        yield event, data

    if answer:
        with suppress(Exception):
            await asyncio.to_thread(db.add_chat, question, "".join(answer))


# ---------------- 接口四：聊天历史 ----------------
@app.get("/history")
def get_history(limit: int = Query(default=50, ge=1, le=200)):
    """返回最近 limit 条问答历史"""
    return {"history": db.list_chats(limit)}


if __name__ == "__main__":
    # 支持直接 python main.py 启动：只监听回环地址，且禁用 reload（自动重载仅用于本地调试）
    uvicorn.run("main:app", host="127.0.0.1", port=8000, reload=False)
