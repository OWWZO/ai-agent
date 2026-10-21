# -*- coding: utf-8 -*-
"""util/embed seam 回归。

三处原始实现的失败策略并不相同（``sop_workspace`` 抛异常，
``plan_sop`` / ``table_rag.qdrant_recall`` 直接让上层拿 None），
所以本 seam **只负责取向量，不决定失败语义**：返回 None 由调用方决定
抛错还是降级。这里固化"长度不匹配必须返回 None"这一关键约束。
"""
import os
import unittest
from unittest.mock import MagicMock, patch

from reactor_tool.util.embed import embed_text, embed_texts


class EmbedSeamTest(unittest.TestCase):
    def test_http_service_preferred_and_length_mismatch_rejected(self):
        env = {"EMBEDDING_URL": "http://embed.local/v1", "TR_EMBEDDING_URL": "http://legacy.local"}
        client = MagicMock()
        client.get_vector_batch.return_value = [[0.1], [0.2]]
        with patch.dict(os.environ, env, clear=False):
            with patch(
                "reactor_tool.util.embed.EmbeddingClient", return_value=client
            ) as cls:
                self.assertEqual(embed_texts(["a", "b"]), [[0.1], [0.2]])
                # EMBEDDING_URL 优先于 TR_EMBEDDING_URL
                cls.assert_called_once_with("http://embed.local/v1")

                # 少返回一条时不能凑一个错位结果给上层做相似度
                client.get_vector_batch.return_value = [[0.1]]
                self.assertIsNone(embed_texts(["a", "b"]))

    def test_tr_embedding_url_used_when_primary_absent(self):
        client = MagicMock()
        client.get_vector_batch.return_value = [[0.5]]
        with patch.dict(os.environ, {"EMBEDDING_URL": "", "TR_EMBEDDING_URL": "http://tr.local"}, clear=False):
            with patch("reactor_tool.util.embed.EmbeddingClient", return_value=client) as cls:
                self.assertEqual(embed_texts(["only"]), [[0.5]])
                cls.assert_called_once_with("http://tr.local")

    def test_falls_back_to_local_text_model(self):
        model = MagicMock()
        model.encode_text_batch.return_value = [[1.0, 2.0]]
        with patch.dict(os.environ, {"EMBEDDING_URL": "", "TR_EMBEDDING_URL": ""}, clear=False):
            with patch(
                "reactor_tool.tool.mrag.embedding.text_embedding.get_text_embedding_model",
                return_value=model,
            ):
                self.assertEqual(embed_texts(["hello"]), [[1.0, 2.0]])

    def test_empty_result_normalized_to_none(self):
        model = MagicMock()
        model.encode_text_batch.return_value = []
        with patch.dict(os.environ, {"EMBEDDING_URL": "", "TR_EMBEDDING_URL": ""}, clear=False):
            with patch(
                "reactor_tool.tool.mrag.embedding.text_embedding.get_text_embedding_model",
                return_value=model,
            ):
                self.assertIsNone(embed_texts(["hello"]))

    def test_empty_input_returns_none_without_touching_services(self):
        with patch("reactor_tool.util.embed.EmbeddingClient") as client_cls:
            self.assertIsNone(embed_texts([]))
            client_cls.assert_not_called()

    def test_embed_text_returns_first_vector(self):
        with patch("reactor_tool.util.embed.embed_texts", return_value=[[9.9]]) as fn:
            self.assertEqual(embed_text("q"), [9.9])
            fn.assert_called_once_with(["q"])

        with patch("reactor_tool.util.embed.embed_texts", return_value=None):
            self.assertIsNone(embed_text("q"))


if __name__ == "__main__":
    unittest.main()
