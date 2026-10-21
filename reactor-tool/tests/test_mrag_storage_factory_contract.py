# -*- coding: utf-8 -*-
"""mrag storage 工厂与配置层回归。

阶段 4 把两处"只有一个实现的假 seam"收敛掉了：
- 元数据仓储：5 个 ABC + 5 个 impl + 5 个复制分支 -> 一张注册表；
- 向量后端：三张各含一项的注册表 + 三个零调用 register_* -> 直接构造。

这里固化收敛后的契约：共享 engine、只接受 qdrant、包装器参数映射正确。
"""
import unittest
from unittest.mock import MagicMock, patch

from reactor_tool.tool.mrag.storage import store_factory
from reactor_tool.tool.mrag.storage.qdrant_vector_store import (
    QdrantGenericVectorStore,
    QdrantTextVectorStore,
)
from reactor_tool.tool.mrag.storage.vector_store_factory import VectorStoreFactory


class StoreFactoryTest(unittest.TestCase):
    def test_all_five_stores_share_one_engine(self):
        """5 个仓储必须共用同一个 engine，而不是各自 create_all 一份 schema。"""
        engine = MagicMock()
        with patch.dict(store_factory._STORES["sqlite"], {}, clear=False):
            with patch.object(store_factory, "_engine", engine):
                instances = [
                    store_factory.get_kb_file_store(),
                    store_factory.get_kb_store(),
                    store_factory.get_kb_doc_store(),
                    store_factory.get_mrag_session_store(),
                    store_factory.get_mrag_turn_store(),
                ]
        for inst in instances:
            self.assertIs(inst._engine, engine)

    def test_store_kinds_are_all_registered(self):
        self.assertEqual(
            set(store_factory._STORES["sqlite"]),
            {"kb_file", "kb", "kb_doc", "mrag_session", "mrag_turn"},
        )


class StoreTypeLazyValidationTest(unittest.TestCase):
    """STORE_TYPE 配错时不能在导入期炸掉整个服务。

    store_factory 被 api/tool.py 顶层导入，导入期 raise 会让 code_interpreter /
    deepsearch 等与 MRAG 无关的接口一并不可用。旧实现是按需抛错的。
    """

    def test_bad_store_type_does_not_break_import(self):
        import importlib
        import os
        import sys
        from unittest.mock import patch

        module_name = "reactor_tool.tool.mrag.storage.store_factory"
        with patch.dict(os.environ, {"STORE_TYPE": "mysql"}):
            sys.modules.pop(module_name, None)
            try:
                module = importlib.import_module(module_name)
                with self.assertRaises(ValueError) as ctx:
                    module.get_kb_store()
                self.assertIn("mysql", str(ctx.exception))
            finally:
                sys.modules.pop(module_name, None)
                importlib.import_module(module_name)


class VectorStoreFactoryTest(unittest.TestCase):
    def test_rejects_non_qdrant_backend(self):
        for call in (
            lambda: VectorStoreFactory.create_vector_store("chroma"),
            lambda: VectorStoreFactory.create_text_store("c", 8, store_type="chroma"),
            lambda: VectorStoreFactory.create_image_store("c", 8, store_type="chroma"),
        ):
            with self.assertRaises(ValueError):
                call()

    def test_text_store_maps_collection_and_size(self):
        backend = MagicMock()
        store = VectorStoreFactory.create_text_store(
            "text_col", 768, vector_store=backend
        )
        self.assertIsInstance(store, QdrantTextVectorStore)
        self.assertEqual(store.collection_name, "text_col")
        self.assertEqual(store.vector_size, 768)
        self.assertIs(store.vector_store, backend)

    def test_image_store_maps_collection_size_and_index_fields(self):
        backend = MagicMock()
        store = VectorStoreFactory.create_image_store(
            "page_col",
            512,
            index_fields=["kb_id", "page_id"],
            vector_store=backend,
        )
        self.assertIsInstance(store, QdrantGenericVectorStore)
        self.assertEqual(store.collection_name, "page_col")
        self.assertEqual(store.embedding_size, 512)
        self.assertEqual(store.index_fields, ["kb_id", "page_id"])
        self.assertIs(store.vector_store, backend)

    def test_image_store_forwards_vector_key(self):
        """旧实现按位置传了 vector_key；改写时漏转发会让非默认键名被静默忽略。"""
        store = VectorStoreFactory.create_image_store(
            "img_col", 512, vector_key="image_vector", vector_store=MagicMock()
        )
        self.assertEqual(store.vector_key, "image_vector")

    def test_image_store_defaults_vector_key_to_vector(self):
        store = VectorStoreFactory.create_image_store(
            "img_col", 512, vector_store=MagicMock()
        )
        self.assertEqual(store.vector_key, "vector")

    def test_image_store_keeps_base_default_index_fields(self):
        """不传 index_fields 时必须沿用基类默认，而不是被覆盖成空列表。"""
        store = VectorStoreFactory.create_image_store(
            "img_col", 512, vector_store=MagicMock()
        )
        self.assertEqual(store.index_fields, ["kb_id", "doc_id"])


class BaseVectorStoreNoDeadStateTest(unittest.TestCase):
    def test_base_store_does_not_hold_config(self):
        """BaseVectorStore 曾把 config 存成死状态（唯一后端从不读取）。"""
        import inspect

        from reactor_tool.tool.mrag.storage.base_vector_store import BaseVectorStore

        source = inspect.getsource(BaseVectorStore.__init__)
        self.assertNotIn("self.config =", source)


if __name__ == "__main__":
    unittest.main()
