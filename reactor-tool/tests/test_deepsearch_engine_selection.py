# -*- coding: utf-8 -*-
import os
import asyncio
import unittest
from unittest.mock import patch

from reactor_tool.model.document import Doc
from reactor_tool.tool.deepsearch import DeepSearch


class DeepSearchEngineSelectionTest(unittest.TestCase):
    def test_should_default_to_ddg_when_env_is_empty(self):
        with patch.dict(os.environ, {"USE_SEARCH_ENGINE": ""}, clear=False):
            search = DeepSearch()
        self.assertEqual(["ddg"], search.engines)

    def test_should_respect_explicit_search_engines_argument(self):
        search = DeepSearch(engines=["ddg"])
        self.assertEqual(["ddg"], search.engines)

    def test_should_disable_jina_reader_for_deepsearch_by_default(self):
        search = DeepSearch(engines=["ddg"])
        self.assertFalse(search._search_single_query.keywords["use_jina_reader"])

    async def _run_search_without_blocking(self):
        search = DeepSearch(engines=["ddg"])

        async def fake_search(query, request_id):
            await asyncio.sleep(0.08)
            return [Doc(doc_type="web_page", title=query, content=query)]

        search._search_single_query = fake_search
        search_finished_at = None
        heartbeat_at = None

        async def heartbeat():
            nonlocal heartbeat_at
            await asyncio.sleep(0.02)
            heartbeat_at = asyncio.get_running_loop().time()

        search_task = asyncio.create_task(
            search._search_queries_and_dedup(["query"], request_id="req-1")
        )
        heartbeat_task = asyncio.create_task(heartbeat())
        await search_task
        search_finished_at = asyncio.get_running_loop().time()
        await heartbeat_task

        return heartbeat_at, search_finished_at

    def test_search_should_not_block_event_loop(self):
        heartbeat_at, search_finished_at = asyncio.run(self._run_search_without_blocking())

        self.assertLess(heartbeat_at, search_finished_at)


if __name__ == "__main__":
    unittest.main()
