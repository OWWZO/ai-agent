# -*- coding: utf-8 -*-
import asyncio
import io
import os
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import AsyncMock, patch

from fastapi import FastAPI
from fastapi.testclient import TestClient
from starlette.datastructures import UploadFile

from reactor_tool.api.file_manage import router
from reactor_tool.db.file_table_op import FileDB, FileInfoOp


class FileManageApiTest(unittest.TestCase):
    def setUp(self):
        app = FastAPI()
        app.include_router(router, prefix="/v1/file_tool")
        self.client = TestClient(app)

    def test_should_preview_file_when_url_contains_nested_path_segments(self):
        with tempfile.NamedTemporaryFile(
            "w", encoding="utf-8", suffix=".md", delete=False
        ) as temp_file:
            temp_file.write("# 测试文件\n")
            file_path = temp_file.name

        try:
            file_info = SimpleNamespace(file_path=file_path)
            with patch(
                "reactor_tool.api.file_manage.FileInfoOp.get_by_file_id",
                new=AsyncMock(side_effect=[file_info]),
            ) as get_by_file_id:
                response = self.client.get(
                    "/v1/file_tool/preview/session-001/colbymchenry/demo.md"
                )

            self.assertEqual(200, response.status_code)
            self.assertEqual("# 测试文件", response.text.strip())
            get_by_file_id.assert_awaited_once()
        finally:
            if os.path.exists(file_path):
                os.remove(file_path)

    def test_should_fallback_to_legacy_file_id_for_nested_path_segments(self):
        with tempfile.NamedTemporaryFile(
            "w", encoding="utf-8", suffix=".md", delete=False
        ) as temp_file:
            temp_file.write("# 历史文件\n")
            file_path = temp_file.name

        try:
            file_info = SimpleNamespace(file_path=file_path)
            with patch(
                "reactor_tool.api.file_manage.FileInfoOp.get_by_file_id",
                new=AsyncMock(side_effect=[None, file_info]),
            ) as get_by_file_id:
                response = self.client.get(
                    "/v1/file_tool/preview/session-002/colbymchenry/legacy.md"
                )

            self.assertEqual(200, response.status_code)
            self.assertEqual("# 历史文件", response.text.strip())
            self.assertEqual(2, get_by_file_id.await_count)
        finally:
            if os.path.exists(file_path):
                os.remove(file_path)

    def test_should_inject_three_runtime_for_html_preview_that_uses_window_three(self):
        html = (
            '<!doctype html><html><head><meta charset="utf-8"></head><body>'
            "<script>const THREE = window.THREE; if (!THREE) throw new Error('missing');</script>"
            "</body></html>"
        )
        with tempfile.NamedTemporaryFile(
            "w", encoding="utf-8", suffix=".html", delete=False
        ) as temp_file:
            temp_file.write(html)
            file_path = temp_file.name

        try:
            file_info = SimpleNamespace(file_path=file_path)
            with patch(
                "reactor_tool.api.file_manage.FileInfoOp.get_by_file_id",
                new=AsyncMock(side_effect=[file_info]),
            ):
                response = self.client.get(
                    "/v1/file_tool/preview/session-003/scene.html"
                )

            self.assertEqual(200, response.status_code)
            self.assertTrue(response.headers["content-type"].startswith("text/html"))
            self.assertTrue(
                response.headers["content-disposition"].startswith("inline")
            )
            self.assertIn(
                "https://cdn.jsdelivr.net/npm/three@0.160.0/build/three.min.js",
                response.text,
            )
            self.assertLess(
                response.text.index("three.min.js"),
                response.text.index("const THREE = window.THREE"),
            )
        finally:
            if os.path.exists(file_path):
                os.remove(file_path)

    def test_should_leave_html_without_three_usage_unchanged(self):
        html = "<html><body><h1>Plain page</h1></body></html>"
        with tempfile.NamedTemporaryFile(
            "w", encoding="utf-8", suffix=".html", delete=False
        ) as temp_file:
            temp_file.write(html)
            file_path = temp_file.name

        try:
            file_info = SimpleNamespace(file_path=file_path)
            with patch(
                "reactor_tool.api.file_manage.FileInfoOp.get_by_file_id",
                new=AsyncMock(side_effect=[file_info]),
            ):
                response = self.client.get(
                    "/v1/file_tool/preview/session-004/plain.html"
                )

            self.assertEqual(200, response.status_code)
            self.assertEqual(html, response.text)
            self.assertNotIn("three.min.js", response.text)
        finally:
            if os.path.exists(file_path):
                os.remove(file_path)

    def test_should_store_binary_upload_under_session_directory(self):
        with tempfile.TemporaryDirectory(prefix="file-manage-local-") as temp_dir:
            original_work_dir = FileDB._work_dir
            FileDB._work_dir = temp_dir
            upload_file = UploadFile(
                filename="poster.png", file=io.BytesIO(b"fake-image-bytes")
            )

            try:
                with patch.object(
                    FileInfoOp,
                    "add",
                    new=AsyncMock(side_effect=lambda file_info: file_info),
                ):
                    file_info = asyncio.run(
                        FileInfoOp.add_by_file(
                            file=upload_file,
                            file_id="file-id-001",
                            request_id="session-1779798194080-9667",
                        )
                    )

                saved_path = Path(file_info.file_path)
                self.assertTrue(saved_path.exists())
                self.assertEqual("session-1779798194080-9667", saved_path.parent.name)
                self.assertEqual("poster.png", saved_path.name)
            finally:
                FileDB._work_dir = original_work_dir
