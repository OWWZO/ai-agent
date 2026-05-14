# -*- coding: utf-8 -*-
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import MagicMock, patch

from reactor_tool.tool.mrag.document.processor import DocumentProcessor
from reactor_tool.tool.mrag.storage.models.kb_doc_model import (
    CANONICAL_FULL_TEXT_CHUNK_TYPE,
)


class DocumentProcessorPersistenceTest(unittest.TestCase):

    def test_should_persist_canonical_full_text_after_preprocess(self):
        with tempfile.TemporaryDirectory(prefix="mrag-processor-") as temp_dir:
            markdown_path = Path(temp_dir) / "demo.md"
            markdown_path.write_text("# 标题\n\n这里是正文。", encoding="utf-8")

            processor = DocumentProcessor.__new__(DocumentProcessor)
            processor._kb_id = "kb-1"
            processor._uid = "file-1"
            processor._file_url = "http://127.0.0.1:1601/download/req/demo.pdf"
            processor._filename = "demo.pdf"
            processor._parser = SimpleNamespace(
                md_file_path=str(markdown_path),
                parsed_text=lambda: markdown_path.read_text(encoding="utf-8"),
            )

            kb_doc_store = MagicMock()
            with patch(
                "reactor_tool.tool.mrag.document.processor.get_kb_doc_store",
                return_value=kb_doc_store,
            ):
                processor._persist_canonical_full_text()

        kb_doc_store.upsert_canonical_doc.assert_called_once()
        persisted_doc = kb_doc_store.upsert_canonical_doc.call_args.args[0]
        self.assertEqual("kb-1", persisted_doc.kb_id)
        self.assertEqual("file-1", persisted_doc.file_id)
        self.assertEqual(CANONICAL_FULL_TEXT_CHUNK_TYPE, persisted_doc.chunk_type)
        self.assertEqual("# 标题\n\n这里是正文。", persisted_doc.text)


if __name__ == "__main__":
    unittest.main()
