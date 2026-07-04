# -*- coding: utf-8 -*-
import unittest
from unittest.mock import MagicMock

from reactor_tool.tool.mrag.storage.qdrant_vector_store import QdrantGenericVectorStore, QdrantTextVectorStore


class MragEvalStoreScrollTest(unittest.TestCase):

    def test_should_scroll_text_payloads_with_kb_filter(self):
        base_store = MagicMock()
        base_store.scroll_vectors.return_value = ([{"payload": {"kb_id": "kb-1"}}], "next")

        store = QdrantTextVectorStore(base_store, "text_collection", 768)
        points, next_offset = store.scroll_payloads("kb-1", limit=50, offset="cursor-1")

        self.assertEqual("next", next_offset)
        self.assertEqual("kb-1", points[0]["payload"]["kb_id"])
        base_store.scroll_vectors.assert_called_once_with(
            collection_name="text_collection",
            limit=50,
            offset="cursor-1",
            filter_conditions={"kb_id": "kb-1"},
        )

    def test_should_scroll_generic_payloads_with_kb_filter(self):
        base_store = MagicMock()
        base_store.scroll_vectors.return_value = ([{"payload": {"kb_id": "kb-2"}}], None)

        store = QdrantGenericVectorStore(base_store, "page_collection", "vector", 768, ["kb_id", "page_id"])
        points, next_offset = store.scroll_payloads("kb-2", limit=25, offset=None)

        self.assertIsNone(next_offset)
        self.assertEqual("kb-2", points[0]["payload"]["kb_id"])
        base_store.scroll_vectors.assert_called_once_with(
            collection_name="page_collection",
            limit=25,
            offset=None,
            filter_conditions={"kb_id": "kb-2"},
        )


if __name__ == "__main__":
    unittest.main()
