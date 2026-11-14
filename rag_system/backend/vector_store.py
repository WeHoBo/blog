# -*- coding: utf-8 -*-
"""
vector_store.py - ChromaDB 向量库封装 + Embedding 客户端
--------------------------------------------------------
1. EmbeddingClient：调用任意「OpenAI 兼容格式」的 embedding 服务
   为文本生成向量（DeepSeek 官方无 embedding 接口，需在 .env 配置
   第三方或本地 embedding 服务，例如 SiliconFlow / one-api 网关）。
2. VectorStore：封装 ChromaDB 本地持久化向量库，提供
   文档块写入、按文档删除、语义检索（cosine 距离）能力。
   向量完全由代码生成后传入，未依赖 chroma 内置 embedding 函数，便于理解底层原理。
"""
import os
import time
from typing import Any

import chromadb

from config import CHROMA_DIR, EMBEDDING_API_KEY, EMBEDDING_BASE_URL, EMBEDDING_MODEL

COLLECTION_NAME = "documents"  # 向量库集合名称


def select_diverse(
    hits: list[dict[str, Any]],
    top_k: int,
    max_per_article: int | None = None,
) -> list[dict[str, Any]]:
    """按「同一篇文章最多留几段」筛选已排序的命中。

    为什么需要：切块时相邻块之间补了 CHUNK_OVERLAP（默认 80 字）重叠，
    同一篇文章的相邻片段天然高度相似。库里的真实分布是 1055 块 / 34 篇，
    平均每篇 31 块 —— 一个笼统的问题（「站内 AI 是怎么做的」）经常被同一篇的
    连续 4~5 段占满 top_k，读起来像是"只检索到一篇文章"，
    「有哪些文章讲过这个」这类问题必然答不全。

    规则：
      1. 按传入顺序（相似度从高到低）遍历，不做二次排序 —— 分数由 Chroma 给，
         这里只做配额裁剪，不引入自己拍脑袋的权重；
      2. 同一 article_id 最多留 max_per_article 段，超额丢弃；
      3. **PDF 片段不受限制**（没有 article_id）：同一份长 PDF 的跨章节问题
         本来就需要多段上下文，按文件名卡配额会把答案掐断；
      4. 结果仍按原顺序返回，长度不超过 top_k。

    Args:
        hits: 已按相似度降序的命中（VectorStore.search 的产物）
        top_k: 最多返回多少段
        max_per_article: 单篇文章的段数上限；None / <=0 表示不限制
    """
    if not hits:
        return []
    limit = max(0, int(top_k))
    if limit == 0:
        return []
    cap = int(max_per_article) if max_per_article else 0
    if cap <= 0:
        return hits[:limit]

    kept: list[dict[str, Any]] = []
    used: dict[str, int] = {}
    for h in hits:
        article_id = h.get("article_id")
        if article_id:
            key = str(article_id)
            if used.get(key, 0) >= cap:
                continue
            used[key] = used.get(key, 0) + 1
        kept.append(h)
        if len(kept) >= limit:
            break
    return kept


class EmbeddingClient:
    """OpenAI 兼容的 Embedding 客户端"""

    def __init__(self) -> None:
        if not (EMBEDDING_BASE_URL and EMBEDDING_API_KEY and EMBEDDING_MODEL):
            raise RuntimeError(
                "未配置 Embedding 服务！请在 backend/.env 中填写 "
                "EMBEDDING_BASE_URL / EMBEDDING_API_KEY / EMBEDDING_MODEL 三项。"
            )
        from openai import OpenAI

        self._client = OpenAI(base_url=EMBEDDING_BASE_URL, api_key=EMBEDDING_API_KEY, timeout=60)
        self.model = EMBEDDING_MODEL

    def embed_documents(self, texts: list[str]) -> list[list[float]]:
        """批量生成文本向量。

        ⚠️ 必须分批 + 重试。一篇长文能切出 40+ 块，一次性丢给 embedding 接口会撞
        「单请求条数上限」或 TPM 限流，客户端 60s 超时也会整篇失败 —— 后果是
        /ingest-articles 的 failed 里静静躺着一篇文章，检索时永远搜不到它（不报错，
        只是"不知道"). 分批后单篇最坏只损失一个子批，并有一次重试兜住 429/超时。
        批大小可用 EMBED_BATCH_SIZE 覆盖（默认 16）。
        """
        if not texts:
            return []
        size = max(1, int(os.getenv("EMBED_BATCH_SIZE", "16")))
        out: list[list[float]] = []
        for i in range(0, len(texts), size):
            part = texts[i:i + size]
            last_err: Exception | None = None
            for attempt in range(2):
                try:
                    resp = self._client.embeddings.create(model=self.model, input=part)
                    # 按输入顺序返回向量
                    out.extend(item.embedding for item in resp.data)
                    last_err = None
                    break
                except Exception as e:  # noqa: BLE001 - 任何失败都值得重试一次
                    last_err = e
                    if attempt == 0:
                        time.sleep(1.5)
            if last_err is not None:
                raise last_err
        return out

    def embed_query(self, text: str) -> list[float]:
        """为单条查询文本生成向量"""
        return self.embed_documents([text])[0]


class VectorStore:
    """ChromaDB 持久化向量库封装"""

    def __init__(self, embedding: EmbeddingClient) -> None:
        # PersistentClient 将数据持久化到本地目录，重启后不丢失
        self._client = chromadb.PersistentClient(path=CHROMA_DIR)
        # cosine 距离适合语义相似度检索
        self.collection = self._client.get_or_create_collection(
            COLLECTION_NAME, metadata={"hnsw:space": "cosine"}
        )
        self.embedding = embedding

    def count(self) -> int:
        """向量库中文本块总数"""
        return self.collection.count()

    def add_document_chunks(self, doc_id: str, filename: str, chunks: list[dict[str, Any]]) -> int:
        """
        将一个文档的所有文本块向量化后写入向量库。
        chunks: [{"text": 文本, "page": 页码}, ...]
        向量 id 规则: {doc_id}_{序号}，保证全局唯一且可整文档删除。
        """
        texts = [c["text"] for c in chunks]
        # 批量调用 embedding 服务生成向量
        embeddings = self.embedding.embed_documents(texts)
        self.collection.add(
            ids=[f"{doc_id}_{i}" for i in range(len(texts))],
            embeddings=embeddings,
            documents=texts,
            metadatas=[
                {"doc_id": doc_id, "filename": filename, "page": c["page"], "chunk_index": i,
                 "source_type": "pdf"}
                for i, c in enumerate(chunks)
            ],
        )
        return len(texts)

    def add_article_chunks(self, article_id: str, title: str, slug: str, category: str,
                           chunks: list[dict[str, Any]]) -> int:
        """
        将一篇博客文章的所有文本块向量化后写入向量库（source_type=article）。
        """
        texts = [c["text"] for c in chunks]
        embeddings = self.embedding.embed_documents(texts)
        prefix = f"a{article_id}_"
        self.collection.add(
            ids=[f"{prefix}{i}" for i in range(len(texts))],
            embeddings=embeddings,
            documents=texts,
            metadatas=[
                {"article_id": article_id, "title": title, "slug": slug, "category": category,
                 "page": c.get("page", 0), "chunk_index": i, "source_type": "article"}
                for i, c in enumerate(chunks)
            ],
        )
        return len(texts)

    def delete_document(self, doc_id: str) -> None:
        """删除某个文档对应的全部向量块"""
        self.collection.delete(where={"doc_id": doc_id})

    def delete_article(self, article_id: str) -> None:
        """删除某篇博客文章对应的全部向量块"""
        self.collection.delete(where={"article_id": article_id})

    def search(
        self,
        query: str,
        top_k: int = 5,
        doc_id: str | None = None,
        max_per_article: int | None = None,
    ) -> list[dict[str, Any]]:
        """
        语义检索：对问题向量化后在向量库中找最相似的 top_k 个文本块。
        doc_id 不为空时限定只在该文档内检索。

        max_per_article 不为空时启用「同一篇文章最多 N 段」的配额（见 select_diverse）。
        ⚠️ 单独读某一篇文章（agent.py 的 article_id 场景）**不要**传这个参数：
        那种场景下多段来自同一篇是正确行为，卡配额反而会让答案缺上下文。
        """
        query_embedding = self.embedding.embed_query(query)
        where = {"doc_id": doc_id} if doc_id else None
        # 有配额时先多取候选，否则筛掉超额片段后可能填不满 top_k（白丢召回）。
        # 只放大 Chroma 的返回条数，不多花一次 embedding 调用 —— 贵的是那个。
        fetch = top_k
        if max_per_article and max_per_article > 0:
            fetch = min(max(top_k * 3, top_k), 50)
        result = self.collection.query(
            query_embeddings=[query_embedding],
            n_results=fetch,
            where=where,
            include=["documents", "metadatas", "distances"],
        )
        # 组装成易用的列表结构
        hits: list[dict[str, Any]] = []
        for i, text in enumerate(result["documents"][0]):
            meta = result["metadatas"][0][i]
            hit = {
                "text": text,
                "filename": meta.get("filename", ""),
                "page": meta.get("page", 0),
                "distance": round(result["distances"][0][i], 4),  # cosine 距离，越小越相似
            }
            if meta.get("source_type") == "article":
                hit["source_type"] = "article"
                hit["article_id"] = meta.get("article_id")
                hit["title"] = meta.get("title", "")
                hit["slug"] = meta.get("slug", "")
                hit["category"] = meta.get("category", "")
                # 引用链接优先用 slug（站内规范 URL /post/{slug}，页面 canonical 也是它）；
                # 元信息里没有 slug 的老数据才退回 /article/{id}。
                # 两条路由现在渲染同一个详情组件，所以无论走哪条展示都是一致的。
                _slug = str(meta.get("slug") or "").strip()
                hit["url"] = f"/post/{_slug}" if _slug else f"/article/{meta.get('article_id')}"
            else:
                hit["source_type"] = "pdf"
            hits.append(hit)
        # 配额裁剪放在组装之后：要拿到 article_id 才能按文章分组
        return select_diverse(hits, top_k, max_per_article)
