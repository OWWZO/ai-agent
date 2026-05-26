# -*- coding: utf-8 -*-
import os
import tempfile
import unittest
from types import SimpleNamespace
from unittest.mock import AsyncMock, patch

from fastapi import FastAPI
from fastapi.testclient import TestClient

from reactor_tool.api.file_manage import router


class FileManageApiTest(unittest.TestCase):
    def setUp(self):
        app = FastAPI()
        app.include_router(router, prefix="/v1/file_tool")
        self.client = TestClient(app)

    def test_should_preview_file_when_url_contains_nested_path_segments(self):
        with tempfile.NamedTemporaryFile("w", encoding="utf-8", suffix=".md", delete=False) as temp_file:
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
        with tempfile.NamedTemporaryFile("w", encoding="utf-8", suffix=".md", delete=False) as temp_file:
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
