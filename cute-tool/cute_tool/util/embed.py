# -*- coding: utf-8 -*-
"""文本向量化 seam。

``plan_sop._resolve_query_vector`` / ``sop_workspace._embed_texts`` /
``table_rag.qdrant_recall.get_qd_recall`` 三处各自实现了
"优先 ``EMBEDDING_URL`` / ``TR_EMBEDDING_URL``，否则本地 TEXT_EMBEDDING_*" 的
降级链，且各自的合法性判定（长度不匹配 / 空向量）处理不同。

:func:`embed_texts` 统一取 batch；:func:`embed_text` 是单条包装。
两个函数在无法得到有效向量时都返回 ``None`` / 空列表，由调用方决定是否降级
或报错 —— 与三处原实现的"失败交给上层"约定一致。
"""

from typing import Optional

from cute_tool.util.env import env_str
from cute_tool.util.qdrant_utils import EmbeddingClient


def embed_texts(texts: list[str]) -> Optional[list[list[float]]]:
    """批量取文本向量；HTTP 服务不可用或返回长度不匹配时返回 None。"""
    if not texts:
        return None
    embedding_url = env_str("EMBEDDING_URL") or env_str("TR_EMBEDDING_URL")
    if embedding_url:
        vectors = EmbeddingClient(embedding_url).get_vector_batch(texts)
        # 长度不匹配说明服务少返回了条目；此时不能凑一个错位结果给上层做相似度。
        if not vectors or len(vectors) != len(texts):
            return None
        return vectors

    from cute_tool.tool.mrag.embedding.text_embedding import (
        get_text_embedding_model,
    )

    model = get_text_embedding_model()
    vectors = model.encode_text_batch(texts)
    return vectors or None


def embed_text(text: str) -> Optional[list[float]]:
    """单条取文本向量；失败返回 None。"""
    vectors = embed_texts([text])
    if not vectors:
        return None
    return vectors[0]
