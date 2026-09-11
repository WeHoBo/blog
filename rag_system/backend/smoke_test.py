# -*- coding: utf-8 -*-
"""
临时冒烟测试脚本（验证完毕可删除）：
用假 embedding（随机向量）与假 LLM 流替代真实 API，验证全链路：
1. pdf_parser 解析 + 切分
2. 上传 -> ChromaDB 入库 -> 检索
3. /upload /documents /chat(流式) /history 四个接口

注意：脚本使用独立的临时数据目录（RAG_DATA_DIR），
不会影响 backend/data 下的真实数据，可与正在运行的服务并存。
"""
import io
import os
import sys
import tempfile
import threading
import uuid

# 必须在导入 config 之前设置：使用独立临时数据目录，避免污染真实数据
_TMP_DATA = tempfile.mkdtemp(prefix="rag_smoke_")
os.environ["RAG_DATA_DIR"] = _TMP_DATA

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import pymupdf
from fastapi.testclient import TestClient


def make_pdf(path: str, text: str) -> None:
    """用 PyMuPDF 生成一个测试用 PDF（自动换行排版）"""
    doc = pymupdf.open()
    page = doc.new_page()
    rect = page.rect + (40, 40, -40, -40)
    page.insert_textbox(rect, text, fontsize=11)
    doc.save(path)
    doc.close()


# ---------- 准备测试 PDF ----------
long_text = ("企业知识管理系统使用说明。" * 80) + "\n\n" + ("RAG检索增强生成技术白皮书。" * 80)
pdf_path = os.path.join(os.path.dirname(__file__), "test_sample.pdf")
make_pdf(pdf_path, long_text)

# ---------- 测试 pdf_parser ----------
import pdf_parser
page_texts = pdf_parser.extract_text(pdf_path)
chunks = pdf_parser.split_text(page_texts, chunk_size=200, chunk_overlap=40)
assert len(chunks) > 1, "切分应产生多个块"
assert all(len(c["text"]) <= 200 + 40 for c in chunks)
assert all("page" in c for c in chunks)
print(f"[pdf_parser] OK: 共 {len(chunks)} 块, 首块 {len(chunks[0]['text'])} 字符")

# ---------- 构造 mock embedding / LLM ----------
class FakeEmbedding:
    """固定种子的随机向量，保证相同文本得到相同向量（便于检索验证）"""
    def __init__(self, dim: int = 64):
        self.dim = dim
        self.seed = 42
    def _vec(self, text: str):
        rnd = __import__("random").Random(self.seed + hash(text) % (2**32))
        v = [rnd.random() for _ in range(self.dim)]
        return v
    def embed_documents(self, texts):
        return [self._vec(t) for t in texts]
    def embed_query(self, text):
        return self._vec(text)

class FakeLLM:
    def __init__(self):
        self.calls = 0
    def build_rag_prompt(self, question, contexts):
        return [{"role": "system", "content": ""}, {"role": "user", "content": question}]
    async def stream_chat(self, messages):
        # 模拟 DeepSeek 流式返回（按 5 字符一段）
        import asyncio
        for i in range(0, 30, 5):
            await asyncio.sleep(0.01)
            yield "测试回答" + "。" * 0

# ---------- 用 mock 替换 main 里的依赖并导入 ----------
import main as app_module
app_module.EmbeddingClient = FakeEmbedding
app_module.LLMClient = FakeLLM
app_module.llm = FakeLLM()
# 直接构造 VectorStore（走真实 ChromaDB）
from vector_store import VectorStore
app_module.vector_store = VectorStore(FakeEmbedding())

client = TestClient(app_module.app)  # 不触发 lifespan，直接注入单例

# ---------- 1. 上传 ----------
with open(pdf_path, "rb") as f:
    resp = client.post("/upload", files={"file": ("测试文档.pdf", f, "application/pdf")})
print("[upload]", resp.status_code, resp.json())
assert resp.status_code == 200, resp.text
doc_id = resp.json()["document"]["id"]
assert resp.json()["document"]["chunk_count"] > 1

# ---------- 2. 文档列表 ----------
resp = client.get("/documents")
docs = resp.json()["documents"]
print("[documents]", resp.status_code, docs)
assert len(docs) == 1 and docs[0]["id"] == doc_id

# ---------- 3. 聊天（流式） ----------
with client.stream("POST", "/chat", json={"question": "RAG是什么？", "top_k": 3}) as resp:
    body = "".join(resp.iter_text())
print("[chat]", resp.status_code, body)
assert resp.status_code == 200 and len(body) > 0

# ---------- 4. 历史 ----------
resp = client.get("/history")
hist = resp.json()["history"]
print("[history]", resp.status_code, hist)
assert len(hist) == 1 and hist[0]["question"] == "RAG是什么？"

# ---------- 5. 非 pdf 上传被拒绝 ----------
resp = client.post("/upload", files={"file": ("a.txt", io.BytesIO(b"hi"), "text/plain")})
print("[upload-reject]", resp.status_code)
assert resp.status_code == 400

os.remove(pdf_path)

# 清理临时数据目录（先释放 ChromaDB 文件句柄）
import gc
del app_module.vector_store
gc.collect()
import shutil
shutil.rmtree(_TMP_DATA, ignore_errors=True)

print("\n=== 全部冒烟测试通过 ===")
