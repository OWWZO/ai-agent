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

        self.assertEqual(8000, len(request_data["input"]["documents"][0]))


if __name__ == "__main__":
    unittest.main()
