# -*- coding: utf-8 -*-
import os
import unittest
from unittest.mock import patch

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


if __name__ == "__main__":
    unittest.main()
