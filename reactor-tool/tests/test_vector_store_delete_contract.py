# -*- coding: utf-8 -*-
"""向量存储删除路径回归。

背景：``BaseCollectionVectorStore.delete_by_file_ids`` 曾是非抽象方法且只
``pass``，而 ``QdrantGenericVectorStore``（image/page 集合）没有覆写它。
``VectorStore.delete_by_file_ids`` 因此对 image/page 静默什么都不做 ——
元数据里的文件被删了，向量还在，检索会召回幽灵内容。

这组测试锁定三件事：
1. 基类两个方法确实是 abstractmethod；
2. 三个包装器都真正实现了删除；
3. 删除请求带上了 kb_id + file_id 过滤条件。
"""
import unittest
from unittest.mock import MagicMock

from reactor_tool.tool.mrag.storage.base_vector_store import BaseCollectionVectorStore
from reactor_tool.tool.mrag.storage.qdrant_vector_store import (
    QdrantGenericVectorStore,
    QdrantTextVectorStore,
)


class VectorStoreDeleteContractTest(unittest.TestCase):
    def test_delete_and_scroll_are_abstract_on_base(self):
        """忘记覆写时必须直接报错，而不是静默返回 None。"""
        self.assertIn("delete_by_file_ids", BaseCollectionVectorStore.__abstractmethods__)
        self.assertIn("scroll_payloads", BaseCollectionVectorStore.__abstractmethods__)

    def test_all_concrete_wrappers_implement_delete_by_file_ids(self):
        for cls in (QdrantTextVectorStore, QdrantGenericVectorStore):
            self.assertEqual(
                cls.__abstractmethods__,
                frozenset(),
                f"{cls.__name__} 仍有未实现的方法",
            )

    def test_generic_store_deletes_with_kb_and_file_filter(self):
        backend = MagicMock()
        backend.delete_vectors.return_value = True
        store = QdrantGenericVectorStore(
            vector_store=backend,
            collection_name="img",
            embedding_size=8,
            index_fields=["kb_id"],
        )
        self.assertTrue(store.delete_by_file_ids("kb-1", ["f-1", "f-2"]))
        backend.delete_vectors.assert_called_once_with(
            collection_name="img",
            filter_conditions={"kb_id": "kb-1", "file_id": ["f-1", "f-2"]},
        )

    def test_text_store_deletes_with_kb_and_file_filter(self):
        backend = MagicMock()
        backend.delete_vectors.return_value = True
        store = QdrantTextVectorStore(
            vector_store=backend, collection_name="txt", vector_size=8
        )
        self.assertTrue(store.delete_by_file_ids("kb-9", ["f-9"]))
        backend.delete_vectors.assert_called_once_with(
            collection_name="txt",
            filter_conditions={"kb_id": "kb-9", "file_id": ["f-9"]},
        )

    def test_generic_store_scrolls_by_kb(self):
        backend = MagicMock()
        backend.scroll_vectors.return_value = ([{"payload": 1}], "next")
        store = QdrantGenericVectorStore(
            vector_store=backend,
            collection_name="page",
            embedding_size=8,
            index_fields=["kb_id"],
        )
        points, offset = store.scroll_payloads("kb-2", limit=10, offset="cur")
        self.assertEqual(points, [{"payload": 1}])
        self.assertEqual(offset, "next")
        backend.scroll_vectors.assert_called_once_with(
            collection_name="page",
            limit=10,
            offset="cur",
            filter_conditions={"kb_id": "kb-2"},
        )


class VectorStoreFacadeDeleteTest(unittest.TestCase):
    def test_facade_deletes_from_all_three_collections(self):
        """VectorStore.delete_by_file_ids 必须打到 text + image + page 三处。"""
        from reactor_tool.tool.mrag.storage.vector_store import VectorStore

        facade = object.__new__(VectorStore)
        text, image, page = MagicMock(), MagicMock(), MagicMock()
        facade._text_store = text
        facade._image_store = image
        facade._page_store = page
        # image_vector_enabled 读的是 self.config.image_vector_enabled
        facade.config = MagicMock()
        facade.config.image_vector_enabled = True

        facade.delete_by_file_ids("kb-1", ["f-1"])

        text.delete_by_file_ids.assert_called_once_with("kb-1", ["f-1"])
        image.delete_by_file_ids.assert_called_once_with("kb-1", ["f-1"])
        page.delete_by_file_ids.assert_called_once_with("kb-1", ["f-1"])


if __name__ == "__main__":
    unittest.main()
