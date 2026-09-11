# -*- coding: utf-8 -*-
"""
llm_client.py - DeepSeek 大模型调用封装
------------------------------------------------
通过 openai 官方 SDK 以「OpenAI 兼容接口」方式调用 DeepSeek：
  - 模型: deepseek-chat
  - 支持流式输出（stream=True），逐 token 返回
RAG 提示词也在本模块组装：指令 + 检索到的上下文片段 + 用户问题。
"""
from typing import Any

from config import DEEPSEEK_API_KEY, DEEPSEEK_BASE_URL, DEEPSEEK_MODEL

# 系统提示词：约束大模型只能参考给定上下文回答，禁止幻觉
SYSTEM_PROMPT = (
    "你是一名严谨的企业文档问答助手。"
    "请只依据下面提供的「资料片段」回答问题，回答要准确、简洁、条理清晰。"
    "如果资料片段中没有相关信息，请直接回答：抱歉，我未在文档中找到相关信息，不要编造内容。"
    "不要提及你参考了哪些片段。"
)

# 自由问答提示词：用于代码解释、文章总结等非检索场景
CODE_EXPLAIN_PROMPT = (
    "你是一名资深编程导师。用户会发来代码片段或文本。"
    "如果是代码：请解释它的作用、执行流程、关键方法与容易出错的地方，条理清晰，可适当给示例。"
    "如果是文章内容：请做简洁的中文总结，提炼核心知识点。"
    "回答使用 Markdown 格式，代码用代码块包裹并标注语言。"
)


class LLMClient:
    """DeepSeek 对话模型客户端"""

    def __init__(self) -> None:
        if not DEEPSEEK_API_KEY:
            raise RuntimeError("未配置 DEEPSEEK_API_KEY，请在 backend/.env 中填写！")
        from openai import AsyncOpenAI

        # 使用异步客户端：流式接口返回异步流，配合 FastAPI 的 async for 迭代不阻塞事件循环
        self._client = AsyncOpenAI(api_key=DEEPSEEK_API_KEY, base_url=DEEPSEEK_BASE_URL, timeout=120)
        self.model = DEEPSEEK_MODEL

    @staticmethod
    def build_rag_prompt(question: str, contexts: list[dict[str, Any]]) -> list[dict[str, str]]:
        """
        组装 RAG 提示词：系统指令 + 检索到的资料片段 + 用户问题。
        contexts: VectorStore.search() 返回的检索结果列表
        """
        # 拼接资料片段，标注来源（文件名 + 页码），便于模型理解上下文
        parts = []
        for i, ctx in enumerate(contexts, start=1):
            parts.append(f"【片段{i}】来源《{ctx['filename']}》第{ctx['page']}页\n{ctx['text']}")
        context_block = "\n\n".join(parts) if parts else "（未检索到相关资料片段）"

        user_content = (
            f"资料片段：\n{context_block}\n\n"
            f"问题：{question}\n\n"
            "请仅基于上述资料片段作答，若资料中无相关信息请明确说明。"
        )
        return [
            {"role": "system", "content": SYSTEM_PROMPT},
            {"role": "user", "content": user_content},
        ]

    async def stream_chat(self, messages: list[dict[str, str]]) -> Any:
        """
        调用 DeepSeek 流式接口（异步生成器）。
        返回一个异步生成器，每次 yield 一段增量文本。
        """
        stream = await self._client.chat.completions.create(
            model=self.model,
            messages=messages,
            stream=True,  # 开启流式输出
            temperature=0.3,  # 低温度，答案更稳定、更忠实于资料
        )
        async for chunk in stream:
            # 流式响应中每个 chunk 都带增量内容，最后一个空
            if chunk.choices and chunk.choices[0].delta and chunk.choices[0].delta.content:
                yield chunk.choices[0].delta.content
