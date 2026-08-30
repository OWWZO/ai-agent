# -*- coding: utf-8 -*-
import os
import unittest
from unittest.mock import patch

import httpx

from reactor_tool.util.llm_util import (
    OPENAI_COMPAT_DEFAULT_USER_AGENT,
    _build_openai_compat_headers,
    _build_http_timeout,
    _prepare_litellm_params,
    _raw_openai_like_request,
    ask_llm,
)


class LlmUtilRoutingTest(unittest.TestCase):
    def test_should_route_gpt52_to_openai_compatible_gateway_even_if_dashscope_env_exists(
        self,
    ):
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
        self.assertEqual(
            "https://dashscope.aliyuncs.com/compatible-mode/v1", params["api_base"]
        )
        self.assertEqual("test-dashscope-key", params["api_key"])
        self.assertEqual("qwen3.5-plus", params["model"])

    def test_should_fill_default_user_agent_for_openai_compatible_headers(self):
        headers = _build_openai_compat_headers({"Accept": "application/json"})

        self.assertEqual("application/json", headers["Accept"])
        self.assertEqual(OPENAI_COMPAT_DEFAULT_USER_AGENT, headers["User-Agent"])


class LlmUtilAsyncHeaderTest(unittest.IsolatedAsyncioTestCase):
    @staticmethod
    def _rate_limit_error(retry_after=None):
        request = httpx.Request("POST", "https://gateway.example/v1/chat/completions")
        headers = {"Retry-After": retry_after} if retry_after is not None else {}
        response = httpx.Response(429, headers=headers, request=request)
        return httpx.HTTPStatusError(
            "upstream rate limited", request=request, response=response
        )

    async def test_should_use_raw_http_for_openai_prefixed_model_when_api_base_is_not_dashscope(
        self,
    ):
        captured_raw_call = {}

        async def fake_raw_openai_like_request(*args, **kwargs):
            captured_raw_call.update(kwargs)
            yield "ok"

        async def fake_acompletion(*args, **kwargs):
            raise AssertionError(
                "non-dashscope api_base should not fallback to litellm primary path"
            )

        with (
            patch.dict(
                os.environ,
                {
                    "OPENAI_BASE_URL": "https://www.openclaudecode.cn/v1/chat/completions",
                    "OPENAI_API_KEY": "test-openai-key",
                },
                clear=False,
            ),
            patch(
                "reactor_tool.util.llm_util._raw_openai_like_request",
                new=fake_raw_openai_like_request,
            ),
            patch(
                "reactor_tool.util.llm_util.acompletion",
                new=fake_acompletion,
            ),
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

    def test_should_use_separate_http_timeout_budgets(self):
        timeout = _build_http_timeout(600000)

        self.assertEqual(30, timeout.connect)
        self.assertEqual(600, timeout.read)
        self.assertEqual(60, timeout.write)
        self.assertEqual(30, timeout.pool)

    async def test_raw_request_should_ignore_environment_proxy(self):
        client_options = {}

        class FakeResponse:
            status_code = 200

            async def aiter_lines(self):
                yield 'data: {"choices":[{"delta":{"content":"ok"}}]}'
                yield "data: [DONE]"

        class FakeStream:
            async def __aenter__(self):
                return FakeResponse()

            async def __aexit__(self, *_args):
                return False

        class FakeAsyncClient:
            def __init__(self, **kwargs):
                client_options.update(kwargs)

            async def __aenter__(self):
                return self

            async def __aexit__(self, *_args):
                return False

            def stream(self, *_args, **_kwargs):
                return FakeStream()

        with patch(
            "reactor_tool.util.llm_util.httpx.AsyncClient",
            new=FakeAsyncClient,
        ):
            chunks = [
                chunk
                async for chunk in _raw_openai_like_request(
                    messages=[{"role": "user", "content": "hello"}],
                    params={
                        "api_base": "https://gateway.example/v1",
                        "api_key": "test-key",
                    },
                    stream=True,
                    only_content=True,
                )
            ]

        self.assertFalse(client_options["trust_env"])
        self.assertEqual(["ok"], chunks)

    async def test_should_retry_interrupted_stream_without_duplicate_prefix(self):
        attempts = 0

        async def fake_raw_openai_like_request(*args, **kwargs):
            nonlocal attempts
            attempts += 1
            if attempts == 1:
                yield "partial"
                raise httpx.RemoteProtocolError("incomplete chunked read")
            yield "partial answer"

        with (
            patch.dict(
                os.environ,
                {
                    "OPENAI_BASE_URL": "https://gateway.example/v1/chat/completions",
                    "OPENAI_API_KEY": "test-openai-key",
                    "LLM_MAX_RETRIES": "1",
                    "OPENAI_COMPAT_ALLOW_LITELLM_FALLBACK": "false",
                },
                clear=False,
            ),
            patch(
                "reactor_tool.util.llm_util._raw_openai_like_request",
                new=fake_raw_openai_like_request,
            ),
        ):
            chunks = [
                chunk
                async for chunk in ask_llm(
                    messages="hello",
                    model="openai/test-model",
                    stream=True,
                    only_content=True,
                    api_base="https://gateway.example/v1/chat/completions",
                    api_key="test-openai-key",
                )
            ]

        self.assertEqual(2, attempts)
        self.assertEqual("partial answer", "".join(chunks))

    async def test_should_honor_retry_after_before_retrying_rate_limit(self):
        attempts = 0
        sleep_calls = []

        async def fake_raw_openai_like_request(*args, **kwargs):
            nonlocal attempts
            attempts += 1
            if attempts == 1:
                raise self._rate_limit_error("7")
            yield "ok"

        async def fake_sleep(delay):
            sleep_calls.append(delay)

        async def fake_acompletion(*args, **kwargs):
            raise AssertionError("429 must not immediately fall back to LiteLLM")

        with (
            patch.dict(
                os.environ,
                {
                    "OPENAI_BASE_URL": "https://gateway.example/v1/chat/completions",
                    "OPENAI_API_KEY": "test-openai-key",
                    "LLM_MAX_RETRIES": "1",
                    "OPENAI_COMPAT_ALLOW_LITELLM_FALLBACK": "true",
                },
                clear=False,
            ),
            patch(
                "reactor_tool.util.llm_util._raw_openai_like_request",
                new=fake_raw_openai_like_request,
            ),
            patch(
                "reactor_tool.util.llm_util.acompletion",
                new=fake_acompletion,
            ),
            patch("reactor_tool.util.llm_util.asyncio.sleep", new=fake_sleep),
        ):
            chunks = [
                chunk
                async for chunk in ask_llm(
                    messages="hello",
                    model="openai/test-model",
                    stream=True,
                    only_content=True,
                    api_base="https://gateway.example/v1/chat/completions",
                    api_key="test-openai-key",
                )
            ]

        self.assertEqual(2, attempts)
        self.assertEqual([7.0], sleep_calls)
        self.assertEqual("ok", "".join(chunks))

    async def test_should_use_exponential_backoff_for_rate_limit_without_retry_after(
        self,
    ):
        attempts = 0
        sleep_calls = []

        async def fake_raw_openai_like_request(*args, **kwargs):
            nonlocal attempts
            attempts += 1
            if attempts <= 3:
                raise self._rate_limit_error()
            yield "ok"

        async def fake_sleep(delay):
            sleep_calls.append(delay)

        with (
            patch.dict(
                os.environ,
                {
                    "OPENAI_BASE_URL": "https://gateway.example/v1/chat/completions",
                    "OPENAI_API_KEY": "test-openai-key",
                    "LLM_MAX_RETRIES": "3",
                    "OPENAI_COMPAT_ALLOW_LITELLM_FALLBACK": "false",
                },
                clear=False,
            ),
            patch(
                "reactor_tool.util.llm_util._raw_openai_like_request",
                new=fake_raw_openai_like_request,
            ),
            patch("reactor_tool.util.llm_util.asyncio.sleep", new=fake_sleep),
        ):
            chunks = [
                chunk
                async for chunk in ask_llm(
                    messages="hello",
                    model="openai/test-model",
                    stream=True,
                    only_content=True,
                    api_base="https://gateway.example/v1/chat/completions",
                    api_key="test-openai-key",
                )
            ]

        self.assertEqual(4, attempts)
        self.assertEqual([4.0, 8.0, 16.0], sleep_calls)
        self.assertEqual("ok", "".join(chunks))


if __name__ == "__main__":
    unittest.main()
