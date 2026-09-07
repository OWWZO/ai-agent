# -*- coding: utf-8 -*-
import os
import unittest
from unittest.mock import AsyncMock, Mock, patch

from reactor_tool.model.document import Doc
from reactor_tool.tool.search_component.search_engine import (
    DDGSearch,
    MixSearch,
    SearchBase,
)


class SearchEngineIntegrationTest(unittest.IsolatedAsyncioTestCase):
    @patch("reactor_tool.tool.search_component.search_engine.DDGS")
    async def test_should_normalize_ddg_results_into_docs(self, mock_ddgs):
        mock_client = Mock()
        mock_client.text.return_value = [
            {
                "title": "Result A",
                "href": "https://example.com/a",
                "body": "snippet a",
            }
        ]
        mock_ddgs.return_value = mock_client

        with patch.dict(
            os.environ,
            {"REACTOR_WEB_FETCH_PROXY": "http://127.0.0.1:7890"},
            clear=False,
        ):
            docs = await DDGSearch().search(
                "deepsearch 替换搜索引擎", request_id="req-1"
            )

        self.assertEqual(1, len(docs))
        self.assertEqual("Result A", docs[0].title)
        self.assertEqual("https://example.com/a", docs[0].link)
        self.assertEqual("snippet a", docs[0].content)
        self.assertEqual("ddg", docs[0].data["search_engine"])
        self.assertEqual(
            "http://127.0.0.1:7890",
            mock_ddgs.call_args.kwargs["proxy"],
        )

    async def test_should_forward_proxy_to_direct_page_fetch(self):
        captured = {}

        class FakeResponse:
            content_type = "text/html"

            async def __aenter__(self):
                return self

            async def __aexit__(self, *args):
                return None

            async def read(self):
                return b"<html><body>article</body></html>"

        class FakeSession:
            async def __aenter__(self):
                return self

            async def __aexit__(self, *args):
                return None

            def get(self, url, **kwargs):
                captured["url"] = url
                captured["request"] = kwargs
                return FakeResponse()

        with patch.dict(
            os.environ,
            {"REACTOR_WEB_FETCH_PROXY": "http://127.0.0.1:7890"},
            clear=False,
        ):
            with patch(
                "reactor_tool.tool.search_component.search_engine.aiohttp.ClientSession",
                FakeSession,
            ):
                content = await SearchBase._fetch_content_with_direct_http(
                    "https://example.com/article",
                    15,
                )

        self.assertEqual("article", content)
        self.assertEqual("http://127.0.0.1:7890", captured["request"]["proxy"])

    @patch.object(SearchBase, "_fetch_content_with_direct_http", new_callable=AsyncMock)
    @patch.object(SearchBase, "_fetch_content_with_jina_reader", new_callable=AsyncMock)
    async def test_should_use_jina_reader_content_when_available(
        self, mock_jina, mock_direct
    ):
        mock_jina.return_value = "clean article body"
        mock_direct.return_value = "fallback body"
        docs = [
            Doc(
                doc_type="web_page",
                title="A",
                link="https://example.com/a",
                content="snippet",
            )
        ]

        parsed = await SearchBase.parser(docs=docs, timeout=15)

        self.assertEqual("clean article body", parsed[0].content)
        mock_direct.assert_not_awaited()

    @patch.object(SearchBase, "_fetch_content_with_direct_http", new_callable=AsyncMock)
    @patch.object(SearchBase, "_fetch_content_with_jina_reader", new_callable=AsyncMock)
    async def test_should_skip_jina_reader_when_disabled(self, mock_jina, mock_direct):
        mock_jina.return_value = "clean article body"
        mock_direct.return_value = "fallback body"
        docs = [
            Doc(
                doc_type="web_page",
                title="A",
                link="https://example.com/a",
                content="snippet",
            )
        ]

        parsed = await SearchBase.parser(docs=docs, timeout=15, use_jina_reader=False)

        self.assertEqual("fallback body", parsed[0].content)
        mock_jina.assert_not_awaited()
        mock_direct.assert_awaited_once()

    @patch.object(SearchBase, "_fetch_content_with_direct_http", new_callable=AsyncMock)
    @patch.object(SearchBase, "_fetch_content_with_jina_reader", new_callable=AsyncMock)
    async def test_should_fallback_to_direct_http_when_jina_reader_returns_empty(
        self, mock_jina, mock_direct
    ):
        mock_jina.return_value = ""
        mock_direct.return_value = "fallback body"
        docs = [
            Doc(
                doc_type="web_page",
                title="A",
                link="https://example.com/a",
                content="snippet",
            )
        ]

        parsed = await SearchBase.parser(docs=docs, timeout=15)

        self.assertEqual("fallback body", parsed[0].content)
        mock_direct.assert_awaited()

    @patch.object(DDGSearch, "search", new_callable=AsyncMock)
    @patch.object(SearchBase, "parser", new_callable=AsyncMock)
    async def test_search_and_dedup_should_drop_empty_and_duplicate_content(
        self, mock_parser, mock_search
    ):
        docs = [
            Doc(
                doc_type="web_page",
                title="A",
                link="https://example.com/a",
                content="same",
            ),
            Doc(
                doc_type="web_page",
                title="B",
                link="https://example.com/b",
                content="same",
            ),
            Doc(
                doc_type="web_page", title="C", link="https://example.com/c", content=""
            ),
        ]
        mock_search.return_value = docs
        mock_parser.return_value = docs

        deduped = await DDGSearch().search_and_dedup("AI Agent", request_id="req-2")

        self.assertEqual(1, len(deduped))
        self.assertEqual("https://example.com/a", deduped[0].link)

    @patch.object(DDGSearch, "search_and_dedup", new_callable=AsyncMock)
    async def test_mix_search_should_delegate_to_ddg_when_enabled(self, mock_ddg):
        mock_ddg.return_value = [
            Doc(
                doc_type="web_page",
                title="A",
                link="https://example.com/a",
                content="body",
            )
        ]

        docs = await MixSearch().search(
            query="AI Agent",
            use_ddg=True,
            use_bing=False,
            use_jina=False,
            use_sogou=False,
            use_serp=False,
            use_exa=False,
        )

        self.assertEqual(1, len(docs))
        self.assertEqual("https://example.com/a", docs[0].link)

    @patch.object(DDGSearch, "search_and_dedup", new_callable=AsyncMock)
    async def test_mix_search_should_forward_jina_reader_flag_to_child_engines(
        self, mock_ddg
    ):
        mock_ddg.return_value = [
            Doc(
                doc_type="web_page",
                title="A",
                link="https://example.com/a",
                content="body",
            )
        ]

        await MixSearch().search(
            query="AI Agent",
            use_ddg=True,
            use_bing=False,
            use_jina=False,
            use_sogou=False,
            use_serp=False,
            use_exa=False,
            use_jina_reader=False,
        )

        self.assertFalse(mock_ddg.await_args.kwargs["use_jina_reader"])


if __name__ == "__main__":
    unittest.main()
