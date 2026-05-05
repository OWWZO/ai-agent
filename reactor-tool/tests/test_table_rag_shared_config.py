# -*- coding: utf-8 -*-
import os
import unittest
from unittest.mock import patch

from reactor_tool.tool.table_rag.qdrant_recall import get_qd_recall
from reactor_tool.util.qdrant_utils import resolve_table_rag_qdrant_config


class TableRagSharedConfigTest(unittest.TestCase):

    def test_should_not_fallback_to_shared_qdrant_config(self):
        with patch.dict(
            os.environ,
            {
                "QDRANT_URL": "https://shared-qdrant.example.com",
                "QDRANT_PORT": "6334",
                "QDRANT_API_KEY": "shared-key",
                "QDRANT_PREFER_GRPC": "true",
            },
            clear=True,
        ):
            config = resolve_table_rag_qdrant_config()

        self.assertIsNone(config["url"])
        self.assertIsNone(config["host"])
        self.assertEqual(0, config["port"])
        self.assertIsNone(config["api_key"])
        self.assertFalse(config["prefer_grpc"])

    def test_should_ignore_table_rag_qdrant_override(self):
        with patch.dict(
            os.environ,
            {
                "QDRANT_URL": "https://shared-qdrant.example.com",
                "QDRANT_PORT": "6334",
                "QDRANT_API_KEY": "shared-key",
                "TR_QDRANT_HOST": "override-qdrant.internal",
                "TR_QDRANT_PORT": "7001",
                "TR_QDRANT_API_KEY": "override-key",
                "TR_QDRANT_PREFER_GRPC": "false",
            },
            clear=True,
        ):
            config = resolve_table_rag_qdrant_config()

        self.assertIsNone(config["url"])
        self.assertIsNone(config["host"])
        self.assertEqual(0, config["port"])
        self.assertIsNone(config["api_key"])
        self.assertFalse(config["prefer_grpc"])

    def test_should_fallback_to_data_agent_qdrant_config_only(self):
        with patch.dict(
            os.environ,
            {
                "QDRANT_URL": "https://shared-qdrant.example.com",
                "QDRANT_PORT": "6334",
                "QDRANT_API_KEY": "shared-key",
                "DATA_AGENT_QDRANT_URL": "https://data-agent-qdrant.example.com",
                "DATA_AGENT_QDRANT_PORT": "7009",
                "DATA_AGENT_QDRANT_API_KEY": "data-agent-key",
                "DATA_AGENT_QDRANT_PREFER_GRPC": "false",
            },
            clear=True,
        ):
            config = resolve_table_rag_qdrant_config()

        self.assertEqual("https://data-agent-qdrant.example.com", config["url"])
        self.assertEqual(7009, config["port"])
        self.assertEqual("data-agent-key", config["api_key"])
        self.assertFalse(config["prefer_grpc"])

    def test_should_keep_legacy_embedding_url_override(self):
        with patch.dict(os.environ, {"TR_EMBEDDING_URL": "http://legacy.local/embedding"}, clear=True):
            with patch("reactor_tool.tool.table_rag.qdrant_recall.EmbeddingClient") as embedding_client_cls:
                with patch("reactor_tool.tool.table_rag.qdrant_recall.get_text_embedding_model") as get_text_embedding_model:
                    with patch("reactor_tool.tool.table_rag.qdrant_recall.QdrantRecall") as qdrant_recall_cls:
                        embedding_client = embedding_client_cls.return_value
                        embedding_client.get_vector.return_value = [0.1, 0.2]
                        qdrant_recall = qdrant_recall_cls.return_value
                        qdrant_recall.search.return_value = [{"modelCode": "sales"}]

                        result = get_qd_recall("订单状态", ["sales"])

        self.assertEqual([{"modelCode": "sales"}], result)
        embedding_client_cls.assert_called_once_with("http://legacy.local/embedding")
        embedding_client.get_vector.assert_called_once_with("订单状态")
        get_text_embedding_model.assert_not_called()


if __name__ == "__main__":
    unittest.main()
