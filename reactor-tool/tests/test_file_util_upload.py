# -*- coding: utf-8 -*-
import os
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from reactor_tool.util.file_util import upload_file


class _FakeResponse:
    def __init__(self, status: int, body: str):
        self.status = status
        self.body = body

    async def __aenter__(self):
        return self

    async def __aexit__(self, *_args):
        return False

    async def text(self):
        return self.body


class _FakeSession:
    def __init__(self, response):
        self.response = response

    async def __aenter__(self):
        return self

    async def __aexit__(self, *_args):
        return False

    def post(self, *_args, **_kwargs):
        return self.response


class FileUtilUploadTest(unittest.IsolatedAsyncioTestCase):
    async def test_should_write_utf8_long_file_name_to_local_storage(self):
        with tempfile.TemporaryDirectory(prefix="file-util-upload-") as storage_root:
            with patch.dict(os.environ, {"FILE_SERVER_URL": storage_root}, clear=False):
                result = await upload_file(
                    content="report",
                    file_name="测" * 120 + ".txt",
                    file_type="txt",
                    request_id="request-1",
                )

            target = Path(result["downloadUrl"])
            self.assertTrue(target.is_file())
            self.assertLessEqual(len(target.name.encode("utf-8")), 240)
            self.assertTrue(target.name.endswith(".txt"))

    async def test_should_report_http_error_before_parsing_empty_response(self):
        session = _FakeSession(_FakeResponse(500, ""))
        with (
            patch.dict(
                os.environ, {"FILE_SERVER_URL": "http://file-service"}, clear=False
            ),
            patch(
                "reactor_tool.util.file_util.aiohttp.ClientSession",
                return_value=session,
            ),
        ):
            with self.assertRaisesRegex(
                RuntimeError, "text file upload failed with HTTP 500"
            ):
                await upload_file(
                    content="report",
                    file_name="analysis.md",
                    file_type="md",
                    request_id="request-1",
                )


if __name__ == "__main__":
    unittest.main()
