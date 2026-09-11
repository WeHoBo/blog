# -*- coding: utf-8 -*-
"""
pdf_parser.py - PDF 解析与文本切分
------------------------------------------------
1. 使用 PyMuPDF(fitz) 逐页提取 PDF 文本，跳过空白页；
2. 对长文本做「块大小 + 重叠度」的切分（chunk），
   切分策略：先按段落（空行）分，段落过长再按句子边界细分，
   最后为相邻块补上尾部重叠，避免跨块语义被截断。
"""
import re
from typing import Any

import pymupdf  # PyMuPDF

# 句子结束标点：中文句号、问号、感叹号、分号 + 英文句点
_SENTENCE_END = re.compile(r"(?<=[。！？；.!?])\s*")


def extract_text(pdf_path: str) -> list[tuple[int, str]]:
    """
    提取 PDF 全部文本。
    返回: [(页码(从1开始), 该页文本), ...]，空白页会被跳过。
    """
    pages: list[tuple[int, str]] = []
    with pymupdf.open(pdf_path) as doc:  # PyMuPDF 打开 PDF 文档
        for page in doc:
            text = page.get_text("text").strip()
            if text:  # 跳过扫描件/图片页等无文字页面
                pages.append((page.number + 1, text))
    if not pages:
        raise ValueError("未能从 PDF 中提取到任何文本，请确认文件不是扫描图片型 PDF")
    return pages


def _split_long_paragraph(paragraph: str, chunk_size: int) -> list[str]:
    """
    当单个段落超过块大小时，按句子边界进一步切分，
    句子本身超长则按字符硬切。
    """
    segments = _SENTENCE_END.split(paragraph)
    result: list[str] = []
    current = ""
    for seg in segments:
        seg = seg.strip()
        if not seg:
            continue
        if len(current) + len(seg) <= chunk_size:
            current += seg
        else:
            if current:
                result.append(current)
            # 单句超长，按字符硬切（每 chunk_size 字符一段）
            while len(seg) > chunk_size:
                result.append(seg[:chunk_size])
                seg = seg[chunk_size:]
            current = seg
    if current:
        result.append(current)
    return result


def split_text(
    page_texts: list[tuple[int, str]],
    chunk_size: int = 500,
    chunk_overlap: int = 80,
) -> list[dict[str, Any]]:
    """
    对解析出的文本做块切分。
    入参: page_texts = extract_text() 的结果
    返回: [{"text": 文本块, "page": 来源页码}, ...]
    """
    # 1) 先把所有页的文本按空行切分成段落列表，同时记录段落所属页码
    paragraphs: list[dict[str, Any]] = []
    for page_no, text in page_texts:
        for para in re.split(r"\n\s*\n", text):
            para = para.replace("\n", "").strip()  # 段落内部换行合并，便于切块
            if para:
                paragraphs.append({"text": para, "page": page_no})

    # 2) 贪心合并段落成接近 chunk_size 的块
    merged: list[dict[str, Any]] = []
    current_text, current_page = "", 0
    for para in paragraphs:
        if len(current_text) + len(para["text"]) <= chunk_size:
            current_text += para["text"]
            current_page = current_page or para["page"]
        else:
            if current_text:
                merged.append({"text": current_text, "page": current_page})
            if len(para["text"]) > chunk_size:
                # 段落过长：按句子细分后逐块加入
                for seg in _split_long_paragraph(para["text"], chunk_size):
                    merged.append({"text": seg, "page": para["page"]})
                current_text, current_page = "", 0
            else:
                current_text, current_page = para["text"], para["page"]
    if current_text:
        merged.append({"text": current_text, "page": current_page})

    # 3) 相邻块之间补充重叠：把上一块尾部 chunk_overlap 字符拼到当前块开头
    chunks: list[dict[str, Any]] = []
    prev_tail = ""
    for item in merged:
        text = prev_tail + item["text"]
        chunks.append({"text": text, "page": item["page"]})
        # 记录当前块尾部，供下一块做重叠
        prev_tail = item["text"][-chunk_overlap:] if len(item["text"]) > chunk_overlap else item["text"]
    return chunks
