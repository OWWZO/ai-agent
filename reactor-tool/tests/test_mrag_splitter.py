# -*- coding: utf-8 -*-
import os
import unittest
from unittest.mock import patch

from reactor_tool.tool.mrag.document.splitter import (
    DefaultDocumentSplitter,
    MarkdownDocumentSplitter,
)


class MragSplitterTest(unittest.TestCase):

    def test_should_force_split_long_plain_text_under_hard_limit(self):
        long_text = "甲" * 1200

        with patch.dict(
            os.environ,
            {
                "CHUNK_SIZE": "500",
                "CHUNK_OVERLAP": "100",
                "CHUNK_HARD_MAX_SIZE": "800",
            },
            clear=False,
        ):
            splitter = DefaultDocumentSplitter()
            chunks = splitter.split(long_text)

        self.assertGreater(len(chunks), 1)
        self.assertTrue(all(len(chunk) <= 800 for chunk in chunks))

    def test_should_force_split_long_markdown_section_under_hard_limit(self):
        markdown_text = "# 标题\n\n" + ("这是超长段落。" * 300)

        with patch.dict(
            os.environ,
            {
                "CHUNK_SIZE": "500",
                "CHUNK_OVERLAP": "100",
                "CHUNK_HARD_MAX_SIZE": "800",
            },
            clear=False,
        ):
            splitter = MarkdownDocumentSplitter()
            chunks = splitter.split(markdown_text)

        self.assertGreater(len(chunks), 1)
        self.assertTrue(all(len(chunk) <= 800 for chunk in chunks))


if __name__ == "__main__":
    unittest.main()
