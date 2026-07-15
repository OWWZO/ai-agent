# -*- coding: utf-8 -*-
import os
import unittest
from unittest.mock import patch

from reactor_tool.tool.mrag.rerank.text_reranker import APITextReranker


class MragRerankTest(unittest.TestCase):

    def test_should_truncate_documents_before_calling_rerank_api(self):
        long_text = "乙" * 9000

        with patch.dict(
            os.environ,
            {
                "TEXT_RERANKER_API_KEY": "test-key",
                "TEXT_RERANKER_MODEL_NAME": "test-model",
                "TEXT_RERANKER_MAX_DOCUMENT_LENGTH": "8000",
            },
            clear=False,
        ):
            reranker = APITextReranker()
            request_data = reranker._prepare_request_data("问题", [long_text])

        self.assertEqual(8000, len(request_data["documents"][0]))
        self.assertEqual("问题", request_data["query"])
        self.assertTrue(request_data["return_documents"])
        self.assertEqual(1, request_data["top_n"])

    def test_should_extract_scores_from_top_level_results(self):
        reranker = APITextReranker.__new__(APITextReranker)

        response_data = {
            "results": [
                {"index": 1, "relevance_score": 0.93},
                {"index": 0, "relevance_score": 0.75},
            ]
        }

        self.assertEqual(response_data["results"], reranker._extract_scores(response_data))

    def test_should_extract_scores_from_legacy_output_results(self):
        reranker = APITextReranker.__new__(APITextReranker)

        response_data = {
            "output": {
                "results": [
                    {"index": 0, "relevance_score": 0.88},
                ]
            }
        }

        self.assertEqual(response_data["output"]["results"], reranker._extract_scores(response_data))


if __name__ == "__main__":
    unittest.main()
