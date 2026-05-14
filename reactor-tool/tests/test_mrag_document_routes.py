# -*- coding: utf-8 -*-
import tempfile
import unittest
from datetime import datetime
from pathlib import Path
from unittest.mock import MagicMock, patch

from fastapi import FastAPI
from fastapi.testclient import TestClient

from reactor_tool.tool.mrag.api.routes import document as document_module
from reactor_tool.tool.mrag.enums.source_type_enums import SourceTypeEnum
from reactor_tool.tool.mrag.enums.task_status_enums import TaskStatusEnum
from reactor_tool.tool.mrag.storage.models.kb_doc_model import (
    CANONICAL_FULL_TEXT_CHUNK_TYPE,
    KBDocModel,
)
from reactor_tool.tool.mrag.storage.models.kb_file_model import KBFileModel


class MragDocumentRoutesTest(unittest.TestCase):

    def setUp(self):
        app = FastAPI()
        app.include_router(document_module.router, prefix="/v1")
        self.client = TestClient(app)

    def test_should_return_ready_full_content_for_completed_file(self):
        file_store = MagicMock()
        file_store.get_file.return_value = KBFileModel(
            kb_id="kb-1",
            file_id="file-1",
            file_url="http://127.0.0.1:1601/download/req/demo.pdf",
            title="demo.pdf",
            source_type=SourceTypeEnum.FILE.value,
            task_status={"global_status": TaskStatusEnum.SUCCESS.value},
            file_status=TaskStatusEnum.SUCCESS.value,
            doc_count=3,
            create_time=datetime(2026, 5, 14, 9, 0, 0),
            modify_time=datetime(2026, 5, 14, 9, 5, 0),
            deleted=0,
        )
        doc_store = MagicMock()
        doc_store.get_canonical_doc.return_value = KBDocModel(
            kb_id="kb-1",
            doc_id="file-1#canonical",
            text="# 正文标题\n\n这里是正文内容。",
            chunk_type=CANONICAL_FULL_TEXT_CHUNK_TYPE,
            file_id="file-1",
            title="demo.pdf",
            file_url="http://127.0.0.1:1601/download/req/demo.pdf",
            parent_id="file-1",
            deleted=0,
            create_time="2026-05-14T09:00:00",
            modify_time="2026-05-14T09:05:00",
            creator=None,
            modifier=None,
        )

        with patch.object(document_module, "get_kb_file_store", return_value=file_store):
            with patch.object(document_module, "get_kb_doc_store", return_value=doc_store):
                response = self.client.post(
                    "/v1/documents/get_file_full_content",
                    json={"kb_id": "kb-1", "file_id": "file-1"},
                )

        self.assertEqual(200, response.status_code)
        payload = response.json()
        self.assertEqual(200, payload["code"])
        self.assertEqual("READY", payload["data"]["content_status"])
        self.assertEqual("# 正文标题\n\n这里是正文内容。", payload["data"]["content"])
        self.assertEqual("markdown", payload["data"]["content_format"])
        self.assertEqual("file", payload["data"]["source_type"])

    def test_should_create_knowledge_base_without_explicit_kb_id(self):
        kb_store = MagicMock()

        with patch.object(document_module, "get_kb_store", return_value=kb_store):
            response = self.client.post(
                "/v1/documents/create_knowledge_base",
                json={
                    "kb_name": "产品资料库",
                    "kb_desc": "用于销售问答",
                },
            )

        self.assertEqual(200, response.status_code)
        payload = response.json()
        self.assertEqual(200, payload["code"])
        self.assertEqual("产品资料库", payload["data"]["kb_name"])
        self.assertEqual("用于销售问答", payload["data"]["kb_desc"])
        self.assertTrue(payload["data"]["kb_id"])
        kb_store.create_kb.assert_called_once()

    def test_should_return_processing_failed_and_unavailable_content_states(self):
        cases = [
            {
                "name": "processing",
                "file_status": TaskStatusEnum.RUNNING.value,
                "task_status": {"global_status": TaskStatusEnum.RUNNING.value},
                "doc": None,
                "expected_status": "PROCESSING",
                "expected_message": "正文仍在生成中",
            },
            {
                "name": "failed",
                "file_status": TaskStatusEnum.FAILED.value,
                "task_status": {
                    "global_status": TaskStatusEnum.FAILED.value,
                    "error_message": "解析失败",
                },
                "doc": None,
                "expected_status": "FAILED",
                "expected_message": "解析失败",
            },
            {
                "name": "unavailable",
                "file_status": TaskStatusEnum.SUCCESS.value,
                "task_status": {"global_status": TaskStatusEnum.SUCCESS.value},
                "doc": None,
                "expected_status": "UNAVAILABLE",
                "expected_message": "当前文件暂无可回显正文",
            },
        ]

        for case in cases:
            with self.subTest(case["name"]):
                file_store = MagicMock()
                file_store.get_file.return_value = KBFileModel(
                    kb_id="kb-1",
                    file_id="file-1",
                    file_url="https://example.com/article",
                    title="官网说明",
                    source_type=SourceTypeEnum.URL.value,
                    task_status=case["task_status"],
                    file_status=case["file_status"],
                    doc_count=0,
                    create_time=datetime(2026, 5, 14, 9, 0, 0),
                    modify_time=datetime(2026, 5, 14, 9, 5, 0),
                    deleted=0,
                )
                doc_store = MagicMock()
                doc_store.get_canonical_doc.return_value = case["doc"]

                with patch.object(document_module, "get_kb_file_store", return_value=file_store):
                    with patch.object(document_module, "get_kb_doc_store", return_value=doc_store):
                        response = self.client.post(
                            "/v1/documents/get_file_full_content",
                            json={"kb_id": "kb-1", "file_id": "file-1"},
                        )

                self.assertEqual(200, response.status_code)
                payload = response.json()["data"]
                self.assertEqual(case["expected_status"], payload["content_status"])
                self.assertEqual("", payload["content"])
                self.assertIn(case["expected_message"], payload["error_message"])

    def test_should_delete_knowledge_base_and_cleanup_related_records(self):
        kb_store = MagicMock()
        kb_store.delete_kb.return_value = True
        file_store = MagicMock()
        file_store.delete_by_kb_id.return_value = 2
        doc_store = MagicMock()
        doc_store.delete_by_kb_id.return_value = 2
        vector_store = MagicMock()

        with patch.object(document_module, "get_kb_store", return_value=kb_store):
            with patch.object(document_module, "get_kb_file_store", return_value=file_store):
                with patch.object(document_module, "get_kb_doc_store", return_value=doc_store):
                    with patch.object(document_module, "VectorStore", return_value=vector_store):
                        response = self.client.post(
                            "/v1/documents/delete_knowledge_base",
                            json={"kb_id": "kb-1"},
                        )

        self.assertEqual(200, response.status_code)
        payload = response.json()
        self.assertEqual("kb-1", payload["data"]["kb_id"])
        self.assertEqual(2, payload["data"]["deleted_file_count"])
        file_store.delete_by_kb_id.assert_called_once_with("kb-1")
        doc_store.delete_by_kb_id.assert_called_once_with("kb-1")
        vector_store.delete_text_by_kb_id.assert_called_once_with("kb-1")
        vector_store.delete_image_by_kb_id.assert_called_once_with("kb-1")
        vector_store.delete_page_by_kb_id.assert_called_once_with("kb-1")

    def test_should_mark_web_source_record_as_url_during_ingestion(self):
        kb_file_store = MagicMock()

        with tempfile.TemporaryDirectory(prefix="mrag-web-test-") as temp_dir:
            source_file = Path(temp_dir) / "source.md"
            source_file.write_text("# 网页标题\n\n正文", encoding="utf-8")

            class ProcessorStub:
                def __init__(self, kb_id, file_id, work_dir, file_path, file_url):
                    self.file_path = file_path

                def process(self):
                    return None

            with patch.object(document_module.tempfile, "gettempdir", return_value=temp_dir):
                with patch.object(document_module, "get_kb_file_store", return_value=kb_file_store):
                    with patch("reactor_tool.tool.mrag.utils.crawl_utils.crawl", return_value="# 网页标题\n\n正文"):
                        with patch.object(document_module, "DocumentProcessor", ProcessorStub, create=True):
                            document_module.add_web_url("https://example.com/article", "kb-1")

        self.assertTrue(kb_file_store.add_file.called)
        created_file = kb_file_store.add_file.call_args.args[0]
        self.assertEqual(SourceTypeEnum.URL.value, created_file.source_type)


if __name__ == "__main__":
    unittest.main()
