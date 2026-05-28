# -*- coding: utf-8 -*-
import unittest
from unittest.mock import AsyncMock, patch

from reactor_tool.model.protocal import WebFetchRequest
from reactor_tool.tool.web_fetcher import DownloadedPage, WebFetcher


class WebFetcherTest(unittest.IsolatedAsyncioTestCase):
    async def test_should_extract_markdown_content_with_trafilatura(self):
        fetcher = WebFetcher()
        request = WebFetchRequest(requestId="req-web-001", url="https://example.com/article")
        page = DownloadedPage(
            final_url="https://example.com/article",
            raw_content="<html><head><title>测试标题</title></head><body><article>正文</article></body></html>",
            content_type="text/html",
        )

        with patch.object(fetcher, "_download_page", new=AsyncMock(return_value=page)):
            with patch("reactor_tool.tool.web_fetcher.trafilatura.extract", return_value="# 标题\n\n正文内容"):
                result = await fetcher.fetch(request)

        self.assertEqual("测试标题", result.title)
        self.assertEqual("https://example.com/article", result.final_url)
        self.assertEqual("markdown", result.content_format)
        self.assertEqual("trafilatura", result.content_source)
        self.assertFalse(result.truncated)
        self.assertEqual("测试标题.md", result.file_name)
        self.assertGreater(result.word_count, 0)

    async def test_should_fallback_to_beautifulsoup_when_trafilatura_returns_empty(self):
        fetcher = WebFetcher()
        request = WebFetchRequest(requestId="req-web-002", url="https://example.com/fallback")
        page = DownloadedPage(
            final_url="https://example.com/fallback",
            raw_content="""
            <html>
              <head><title>Fallback 页面</title></head>
              <body>
                <main>
                  <h1>Fallback 页面</h1>
                  <p>第一段正文。</p>
                  <p>第二段正文。</p>
                </main>
              </body>
            </html>
            """,
            content_type="text/html",
        )

        with patch.object(fetcher, "_download_page", new=AsyncMock(return_value=page)):
            with patch("reactor_tool.tool.web_fetcher.trafilatura.extract", return_value=""):
                result = await fetcher.fetch(request)

        self.assertEqual("beautifulsoup", result.content_source)
        self.assertIn("第一段正文。", result.full_content)
        self.assertIn("第二段正文。", result.full_content)

    async def test_should_truncate_inline_content_without_affecting_full_content(self):
        fetcher = WebFetcher(inline_content_limit=10)
        request = WebFetchRequest(requestId="req-web-003", url="https://example.com/long")
        page = DownloadedPage(
            final_url="https://example.com/long",
            raw_content="<html><head><title>Long 页面</title></head><body><article>很长的正文</article></body></html>",
            content_type="text/html",
        )

        with patch.object(fetcher, "_download_page", new=AsyncMock(return_value=page)):
            with patch("reactor_tool.tool.web_fetcher.trafilatura.extract", return_value="0123456789ABCDEFGHIJ"):
                result = await fetcher.fetch(request)

        self.assertTrue(result.truncated)
        self.assertEqual("0123456789ABCDEFGHIJ", result.full_content)
        self.assertIn("内容已截断", result.inline_content)

    async def test_should_accept_markdown_text_response(self):
        fetcher = WebFetcher()
        request = WebFetchRequest(requestId="req-web-004", url="https://raw.githubusercontent.com/openai/openai-python/main/README.md")
        page = DownloadedPage(
            final_url="https://raw.githubusercontent.com/openai/openai-python/main/README.md",
            raw_content="# 项目标题\n\n这是一段 Markdown 正文。",
            content_type="text/plain; charset=utf-8",
        )

        with patch.object(fetcher, "_download_page", new=AsyncMock(return_value=page)):
            result = await fetcher.fetch(request)

        self.assertEqual("plain_text", result.content_source)
        self.assertEqual("text", result.content_format)
        self.assertEqual("README.md", result.file_name)
        self.assertIn("Markdown 正文", result.full_content)

    def test_should_reject_binary_content_type(self):
        fetcher = WebFetcher()

        self.assertFalse(fetcher._is_supported_content_type("application/pdf"))
        self.assertFalse(fetcher._is_supported_content_type("image/png"))
        self.assertTrue(fetcher._is_supported_content_type("text/plain; charset=utf-8"))


if __name__ == "__main__":
    unittest.main()
