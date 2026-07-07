# -*- coding: utf-8 -*-
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import MagicMock, call, patch

from reactor_tool.tool.mrag.document.processor import DocumentProcessor
from reactor_tool.tool.mrag.document.parser import DocumentParser
from reactor_tool.tool.mrag.storage.models.kb_doc_model import (
    CANONICAL_FULL_TEXT_CHUNK_TYPE,
)


class DocumentProcessorPersistenceTest(unittest.TestCase):

    def test_should_list_nested_image_files_without_returning_directories(self):
        with tempfile.TemporaryDirectory(prefix="mrag-parser-") as temp_dir:
            file_path = Path(temp_dir) / "demo.pdf"
            file_path.write_bytes(b"%PDF-1.4")
            parser = DocumentParser(temp_dir, str(file_path))

            nested_dir = Path(parser.images_dir) / "chunk_1"
            nested_dir.mkdir(parents=True, exist_ok=True)
            nested_image = nested_dir / "figure.png"
            nested_image.write_bytes(b"nested-image")
            top_image = Path(parser.images_dir) / "cover.png"
            top_image.write_bytes(b"top-image")

            image_paths = parser.parsed_images()

        self.assertEqual(
            {
                str(nested_image.resolve()),
                str(top_image.resolve()),
            },
            set(image_paths),
        )
        self.assertNotIn(str(nested_dir.resolve()), image_paths)

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

    def test_should_replace_nested_markdown_image_paths_with_uploaded_urls(self):
        with tempfile.TemporaryDirectory(prefix="mrag-processor-") as temp_dir:
            images_dir = Path(temp_dir) / "images"
            pages_dir = Path(temp_dir) / "pages"
            nested_dir = images_dir / "chunk_1"
            nested_dir.mkdir(parents=True, exist_ok=True)
            pages_dir.mkdir(parents=True, exist_ok=True)

            nested_image = nested_dir / "figure.png"
            nested_image.write_bytes(b"nested-image")
            page_image = pages_dir / "page_1.png"
            page_image.write_bytes(b"page-image")

            markdown_path = Path(temp_dir) / "demo.md"
            markdown_path.write_text(
                "![figure.png](images/chunk_1/figure.png)\n![page_1.png](images/page_1.png)",
                encoding="utf-8",
            )

            processor = DocumentProcessor.__new__(DocumentProcessor)
            processor._uid = "file-1"
            processor._image_urls = {}
            processor._parser = SimpleNamespace(
                parse=lambda: None,
                parsed_images=lambda: [str(nested_image)],
                parsed_pages=lambda: [str(page_image)],
                md_file_path=str(markdown_path),
                images_dir=str(images_dir),
                pages_dir=str(pages_dir),
            )

            with patch(
                "reactor_tool.tool.mrag.document.processor.oss_utils.upload_local_storage",
                side_effect=[
                    "http://127.0.0.1:1601/storage/file-1/chunk_1/figure.png",
                    "http://127.0.0.1:1601/storage/file-1/page_1.png",
                ],
            ) as upload_mock:
                processor.pre_process()
                rewritten_markdown = markdown_path.read_text(encoding="utf-8")

        self.assertEqual(
            {
                "chunk_1/figure.png": "http://127.0.0.1:1601/storage/file-1/chunk_1/figure.png",
                "page_1.png": "http://127.0.0.1:1601/storage/file-1/page_1.png",
            },
            processor._image_urls,
        )
        self.assertEqual(
            "![figure.png](http://127.0.0.1:1601/storage/file-1/chunk_1/figure.png)\n"
            "![page_1.png](http://127.0.0.1:1601/storage/file-1/page_1.png)",
            rewritten_markdown,
        )
        upload_mock.assert_has_calls(
            [
                call(str(nested_image), file_id="file-1"),
                call(str(page_image), file_id="file-1"),
            ]
        )


if __name__ == "__main__":
    unittest.main()
