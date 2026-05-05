# -*- coding: utf-8 -*-
import os
import unittest
from unittest.mock import patch

from reactor_tool.util.llm_util import (
    OPENAI_COMPAT_DEFAULT_USER_AGENT,
    _build_openai_compat_headers,
    _prepare_litellm_params,
    ask_llm,
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

class LlmUtilAsyncHeaderTest(unittest.IsolatedAsyncioTestCase):
    async def test_should_use_raw_http_for_openai_prefixed_model_when_api_base_is_not_dashscope(self):
        captured_raw_call = {}

        async def fake_raw_openai_like_request(*args, **kwargs):
            captured_raw_call.update(kwargs)
            yield "ok"

        async def fake_acompletion(*args, **kwargs):
            raise AssertionError("non-dashscope api_base should not fallback to litellm primary path")

        with patch.dict(
            os.environ,
            {
                "OPENAI_BASE_URL": "https://www.openclaudecode.cn/v1/chat/completions",
                "OPENAI_API_KEY": "test-openai-key",
            },
            clear=False,
        ), patch(
            "reactor_tool.util.llm_util._raw_openai_like_request",
            new=fake_raw_openai_like_request,
        ), patch(
            "reactor_tool.util.llm_util.acompletion",
            new=fake_acompletion,
        ):
            async for _ in ask_llm(
                messages="hello",
                model="openai/z-ai/glm-4.5-air:free",
                stream=False,
                only_content=True,
                api_base="https://www.openclaudecode.cn/v1/chat/completions",
                api_key="test-openai-key",
            ):
                pass

        self.assertEqual(
            OPENAI_COMPAT_DEFAULT_USER_AGENT,
            captured_raw_call["params"]["extra_headers"]["User-Agent"],
        )
        self.assertEqual(
            "https://www.openclaudecode.cn/v1/chat/completions",
            captured_raw_call["params"]["api_base"],
        )


if __name__ == "__main__":
    unittest.main()
