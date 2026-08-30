# -*- coding: utf-8 -*-
"""OpenAI 兼容文本 Embedding 实现（TEXT_EMBEDDING_* 环境变量）。"""

import os
from typing import List

import dotenv
from loguru import logger
from openai import DefaultHttpxClient, OpenAI

from .embedding import TextEmbedding
from reactor_tool.tool.mrag.utils.retry_utils import call_with_retry

dotenv.load_dotenv()


class OpenAITextEmbedding(TextEmbedding):
    """通过 OpenAI embeddings API 批量编码文本。"""

    def _encode_text_batch(self, texts: List[str]) -> list[list[float]]:
        """批量编码文本为向量；单条超长按 TEXT_EMBEDDING_MAX_TEXT_LENGTH 截断。"""
        if not texts:
            return []

        max_text_length = int(os.getenv("TEXT_EMBEDDING_MAX_TEXT_LENGTH", 8000))
        texts = [text[:max_text_length] for text in texts]

        try:
            client = OpenAI(
                base_url=os.getenv("TEXT_EMBEDDING_BASE_URL"),
                api_key=os.getenv("TEXT_EMBEDDING_API_KEY"),
                http_client=DefaultHttpxClient(trust_env=False),
            )

            batch_size = 10
            embeddings = []
            model_name = os.getenv("TEXT_EMBEDDING_MODEL_NAME")
            timeout = int(os.getenv("API_TIMEOUT", 300))
            for i in range(0, len(texts), batch_size):
                batch_texts = texts[i : i + batch_size]
                response = call_with_retry(
                    lambda batch=batch_texts: client.embeddings.create(
                        model=model_name,
                        input=batch,
                        timeout=timeout,
                    ),
                    label=f"mrag-text-embedding:{model_name or 'unknown'}",
                )
                response_data = response.data
                if not response_data:
                    raise ValueError("文本编码失败: 未返回任何数据")

                for embedding in response_data:
                    embeddings.append(embedding.embedding)
            return embeddings

        except Exception as e:
            import traceback

            logger.error(f"文本编码失败: {e}\n{traceback.format_exc()}")
            raise Exception(f"文本编码失败: {e}") from e


def get_text_embedding_model() -> TextEmbedding:
    """获取文本embedding模型"""
    embedding_type = (os.getenv("TEXT_EMBEDDING_TYPE") or "").strip().lower()
    if embedding_type in {"openai", "openai_compatible", "openai-compatible"}:
        return OpenAITextEmbedding()
    else:
        raise ValueError(f"不支持的文本embedding模型: {embedding_type}")
