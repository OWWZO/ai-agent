# -*- coding: utf-8 -*-
import unittest
from unittest.mock import AsyncMock, patch

from fastapi import FastAPI
from fastapi.testclient import TestClient

from reactor_tool.api.tool import router
from reactor_tool.tool.web_fetcher import WebFetchResult


class WebFetchApiTest(unittest.TestCase):
    def setUp(self):
        app = FastAPI()
        app.include_router(router, prefix="/v1/tool")
        self.client = TestClient(app)

    def test_should_return_unified_response_and_file_info(self):
        fetch_result = WebFetchResult(
            title="测试网页",
            final_url="https://example.com/final",
            full_content="# 标题\n\n完整正文",
            inline_content="# 标题\n\n完整正文",
            content_format="markdown",
            word_count=4,
            truncated=False,
            content_source="trafilatura",
            metadata={"description": "页面描述", "siteName": "Example"},
            file_name="测试网页.md",
        )

        with patch("reactor_tool.api.tool.WebFetcher.fetch", new=AsyncMock(return_value=fetch_result)):
            with patch(
                "reactor_tool.api.tool.upload_file",
                new=AsyncMock(
                    return_value={
                        "fileName": "测试网页.md",
                        "ossUrl": "https://file.example.com/download/测试网页.md",
                        "domainUrl": "https://file.example.com/preview/测试网页.md",
                        "fileSize": 128,
                    }
                ),
            ) as upload_mock:
                response = self.client.post(
                    "/v1/tool/web_fetch",
                    json={
                        "requestId": "req-web-api-001",
                        "url": "https://example.com/page",
                    },
                )

        self.assertEqual(200, response.status_code)
        body = response.json()
        self.assertEqual(200, body["code"])
        self.assertEqual("req-web-api-001", body["requestId"])
        self.assertEqual("测试网页", body["data"]["title"])
        self.assertEqual("https://example.com/final", body["data"]["finalUrl"])
        self.assertEqual("markdown", body["data"]["contentFormat"])
        self.assertFalse(body["data"]["truncated"])
        self.assertEqual("测试网页.md", body["fileInfo"][0]["fileName"])
        upload_mock.assert_awaited_once()

    def test_should_return_400_when_fetcher_rejects_request(self):
        with patch("reactor_tool.api.tool.WebFetcher.fetch", new=AsyncMock(side_effect=ValueError("web_fetch 仅支持 HTML、Markdown 或纯文本内容"))):
            response = self.client.post(
                "/v1/tool/web_fetch",
                json={
                    "requestId": "req-web-api-002",
                    "url": "https://example.com/file.pdf",
                },
            )

        self.assertEqual(400, response.status_code)
        body = response.json()
        self.assertEqual(400, body["code"])
        self.assertEqual("web_fetch 仅支持 HTML、Markdown 或纯文本内容", body["message"])


if __name__ == "__main__":
    unittest.main()
