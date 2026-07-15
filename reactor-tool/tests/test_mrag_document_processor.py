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

    def test_should_store_image_ocr_and_caption_as_text_proxy_when_image_vector_disabled(self):
        with tempfile.TemporaryDirectory(prefix="mrag-processor-") as temp_dir:
            images_dir = Path(temp_dir) / "images"
            images_dir.mkdir(parents=True, exist_ok=True)
            image_path = images_dir / "chart.png"
            image_path.write_bytes(b"fake-image")

            processor = DocumentProcessor.__new__(DocumentProcessor)
            processor._kb_id = "kb-1"
            processor._uid = "file-1"
            processor._file_path = str(Path(temp_dir) / "demo.pdf")
            processor._file_url = "http://127.0.0.1:1601/storage/demo.pdf"
            processor._filename = "demo.pdf"
            processor._image_vector_enabled = False
            processor._vector_store = MagicMock()
            processor._text_embedding = MagicMock()
            processor._bm25_embedding = MagicMock()
            processor._ocr = MagicMock()
            processor._parser = SimpleNamespace(
                parsed_images=lambda: [str(image_path)],
                images_dir=str(images_dir),
            )

            processor._ocr.ocr.return_value = "图中包含销售额数据"
            processor._text_embedding.encode_text_batch.return_value = [[0.1, 0.2], [0.3, 0.4]]
            processor._bm25_embedding.encode_text_batch.return_value = [{"indices": [1], "values": [1.0]}, {"indices": [2], "values": [0.8]}]

            with patch.object(
                processor,
                "_get_uploaded_asset_url",
                return_value="http://127.0.0.1:1601/storage/chart.png",
            ), patch(
                "reactor_tool.tool.mrag.document.processor.generate_caption",
                return_value="这是一张销售趋势图",
            ):
                processor._process_image()

        processor._vector_store.add_image_chunks.assert_not_called()
        processor._vector_store.add_text_chunks.assert_called_once()
        chunk_batch = processor._vector_store.add_text_chunks.call_args.args[0]
        self.assertEqual(["ocr_text", "caption"], [chunk["chunk_type"] for chunk in chunk_batch])
        self.assertEqual(["file-1", "file-1"], [chunk["file_id"] for chunk in chunk_batch])
        self.assertEqual(["file-1-0", "file-1-0"], [chunk["image_id"] for chunk in chunk_batch])
        self.assertEqual(
            ["http://127.0.0.1:1601/storage/chart.png", "http://127.0.0.1:1601/storage/chart.png"],
            [chunk["image_url"] for chunk in chunk_batch],
        )

    def test_should_skip_page_ocr_and_caption_when_image_vector_disabled(self):
        with tempfile.TemporaryDirectory(prefix="mrag-processor-") as temp_dir:
            pages_dir = Path(temp_dir) / "pages"
            pages_dir.mkdir(parents=True, exist_ok=True)
            page_path = pages_dir / "page_1.png"
            page_path.write_bytes(b"fake-page")

            processor = DocumentProcessor.__new__(DocumentProcessor)
            processor._uid = "file-1"
            processor._image_vector_enabled = False
            processor._vector_store = MagicMock()
            processor._ocr = MagicMock()
            processor._parser = SimpleNamespace(
                parsed_pages=lambda: [str(page_path)],
                pages_dir=str(pages_dir),
            )

            processor._process_page()

        processor._ocr.ocr.assert_not_called()
        processor._vector_store.add_page_chunks.assert_not_called()
        processor._vector_store.add_text_chunks.assert_not_called()


if __name__ == "__main__":
    unittest.main()
