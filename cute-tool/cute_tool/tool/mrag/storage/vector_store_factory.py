# -*- coding: utf-8 -*-
"""向量存储构造。

仓库里只有 Qdrant 一种后端，原先却有一套"插件化"工厂：三个各含一项的注册表、
三个零调用的 ``register_*`` 方法、以及一个未传 ``vector_store`` 时会悄悄新建
第二个 ``QdrantClient`` 的隐式分支。这里直接构造，把"唯一后端"这件事写清楚。

保留类名与方法名，调用方（``vector_store.py``）无需改动。
"""

from typing import List, Optional

from .base_vector_store import BaseCollectionVectorStore
from .config_manager import VectorStoreConfig
from .qdrant_vector_store import (
    QdrantGenericVectorStore,
    QdrantTextVectorStore,
    QdrantVectorStore,
)


class VectorStoreFactory:
    """构造 Qdrant 后端及其集合包装器。"""

    @classmethod
    def create_vector_store(
        cls, store_type: Optional[str] = None, config: Optional[VectorStoreConfig] = None
    ) -> QdrantVectorStore:
        """创建底层 Qdrant 客户端封装。

        ``store_type`` 参数为兼容旧调用保留；当前只支持 qdrant。
        """
        if store_type is not None and store_type != "qdrant":
            raise ValueError(
                f"Unsupported vector store type: {store_type}. Available types: ['qdrant']"
            )
        # 传入的 config 目前不被 QdrantVectorStore 读取，但保留参数以免调用方
        # 需要为此改签名；真正决定连接的是环境变量。
        return QdrantVectorStore(config or VectorStoreConfig())

    @classmethod
    def create_text_store(
        cls,
        collection_name: str,
        vector_size: int,
        store_type: Optional[str] = None,
        vector_store: Optional[QdrantVectorStore] = None,
    ) -> BaseCollectionVectorStore:
        """创建文本集合包装器。"""
        cls._reject_other_backend(store_type)
        return QdrantTextVectorStore(
            vector_store=vector_store or cls.create_vector_store(),
            collection_name=collection_name,
            vector_size=vector_size,
        )

    @classmethod
    def create_image_store(
        cls,
        collection_name: str,
        vector_size: int,
        vector_key: str = "vector",
        index_fields: Optional[List] = None,
        store_type: Optional[str] = None,
        vector_store: Optional[QdrantVectorStore] = None,
    ) -> BaseCollectionVectorStore:
        """创建图像/页面集合包装器（两者共用通用实现）。"""
        cls._reject_other_backend(store_type)
        return QdrantGenericVectorStore(
            vector_store=vector_store or cls.create_vector_store(),
            collection_name=collection_name,
            vector_key=vector_key,
            embedding_size=vector_size,
            # 不传时保持 BaseCollectionVectorStore 的默认 ["kb_id","doc_id"]，
            # 不要用 `or []` 把它覆盖成空列表 —— 那会丢掉默认索引。
            index_fields=index_fields,
        )

    @staticmethod
    def _reject_other_backend(store_type: Optional[str]) -> None:
        if store_type is not None and store_type != "qdrant":
            raise ValueError(
                f"Unsupported store type: {store_type}. Available types: ['qdrant']"
            )
