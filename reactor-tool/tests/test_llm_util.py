# -*- coding: utf-8 -*-
import os
import unittest
from unittest.mock import patch

from reactor_tool.util.llm_util import (
    OPENAI_COMPAT_DEFAULT_USER_AGENT,
    _build_openai_compat_headers,
    _prepare_litellm_params,
)


class LlmUtilRoutingTest(unittest.TestCase):

    def test_should_route_gpt52_to_openai_compatible_gateway_even_if_dashscope_env_exists(self):
        with patch.dict(
            os.environ,
            {
                "OPENAI_BASE_URL": "https://www.openclaudecode.cn",
                "OPENAI_API_KEY": "test-openai-key",
                "DASHSCOPE_API_BASE": "https://dashscope.aliyuncs.com/compatible-mode/v1",
                "DASHSCOPE_API_KEY": "test-dashscope-key",
            },
            clear=False,
        ):
            params = _prepare_litellm_params("gpt-5.2")

        self.assertEqual("openai_like", params["custom_llm_provider"])
        self.assertEqual("https://www.openclaudecode.cn/v1", params["api_base"])
        self.assertEqual("test-openai-key", params["api_key"])
        self.assertEqual("gpt-5.2", params["model"])

    def test_should_keep_dashscope_for_qwen_model(self):
        with patch.dict(
            os.environ,
            {
                "OPENAI_BASE_URL": "https://www.openclaudecode.cn",
                "OPENAI_API_KEY": "test-openai-key",
                "DASHSCOPE_API_BASE": "https://dashscope.aliyuncs.com/compatible-mode/v1",
                "DASHSCOPE_API_KEY": "test-dashscope-key",
            },
            clear=False,
        ):
            params = _prepare_litellm_params("qwen3.5-plus")

        self.assertEqual("openai", params["custom_llm_provider"])
        self.assertEqual("https://dashscope.aliyuncs.com/compatible-mode/v1", params["api_base"])
        self.assertEqual("test-dashscope-key", params["api_key"])
        self.assertEqual("qwen3.5-plus", params["model"])

    def test_should_fill_default_user_agent_for_openai_compatible_headers(self):
        headers = _build_openai_compat_headers({"Accept": "application/json"})

        self.assertEqual("application/json", headers["Accept"])
        self.assertEqual(OPENAI_COMPAT_DEFAULT_USER_AGENT, headers["User-Agent"])


if __name__ == "__main__":
    unittest.main()
