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
from typing import Any

import chromadb

from config import CHROMA_DIR, EMBEDDING_API_KEY, EMBEDDING_BASE_URL, EMBEDDING_MODEL

COLLECTION_NAME = "documents"  # 向量库集合名称


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
        """批量生成文本向量（一次请求最多约 2048 条）"""
        resp = self._client.embeddings.create(model=self.model, input=texts)
        # 按输入顺序返回向量列表
        return [item.embedding for item in resp.data]

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
        self, query: str, top_k: int = 5, doc_id: str | None = None
    ) -> list[dict[str, Any]]:
        """
        语义检索：对问题向量化后在向量库中找最相似的 top_k 个文本块。
        doc_id 不为空时限定只在该文档内检索。
        """
        query_embedding = self.embedding.embed_query(query)
        where = {"doc_id": doc_id} if doc_id else None
        result = self.collection.query(
            query_embeddings=[query_embedding],
            n_results=top_k,
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
        return hits
