# -*- coding: utf-8 -*-
import json
import os
import sys
import types
import unittest
import importlib.util
from pathlib import Path
from unittest.mock import patch

from fastapi import FastAPI
from fastapi.testclient import TestClient
from sse_starlette.sse import AppStatus

from reactor_tool.tool.mrag.storage.qdrant_vector_store import QdrantVectorStore

if "litellm" not in sys.modules:
    litellm_stub = types.ModuleType("litellm")

    async def _stub_acompletion(*args, **kwargs):
        raise RuntimeError("litellm stub should not be called in MRAG API tests")

    litellm_stub.acompletion = _stub_acompletion
    sys.modules["litellm"] = litellm_stub

tool_module_spec = importlib.util.spec_from_file_location(
    "mrag_tool_router_under_test",
    Path(__file__).resolve().parents[1] / "reactor_tool" / "api" / "tool.py",
)
tool_module = importlib.util.module_from_spec(tool_module_spec)
assert tool_module_spec is not None and tool_module_spec.loader is not None
tool_module_spec.loader.exec_module(tool_module)
tool_router = tool_module.router


class MragApiTest(unittest.TestCase):

    def setUp(self):
        # sse-starlette 会缓存全局退出事件；测试之间复用时需要重置，避免事件循环串用。
        AppStatus.should_exit = False
        AppStatus.should_exit_event = None
        app = FastAPI()
        app.include_router(tool_router, prefix="/v1/tool")
        self.client = TestClient(app)

    def tearDown(self):
        AppStatus.should_exit = False
        AppStatus.should_exit_event = None

    def test_should_stream_openai_compatible_chunks_and_done(self):
        with patch.dict(os.environ, {"DEFAULT_KB_ID": "kb-test"}, clear=False):
            with patch.object(tool_module, "build_mrag_agent") as build_mrag_agent:
                agent = build_mrag_agent.return_value
                agent.run.return_value = iter([
                    {
                        "choices": [
                            {
                                "delta": {"content": "多模态检索会先召回图文片段。"},
                                "finishReason": None,
                                "index": 0,
                            }
                        ]
                    },
                    {
                        "choices": [
                            {
                                "delta": {"content": "最终结果支持 Markdown 图片引用。"},
                                "finishReason": "stop",
                                "index": 0,
                            }
                        ]
                    },
                ])

                with self.client.stream(
                        "POST",
                        "/v1/tool/mragQuery",
                        json={"question": "总结多模态检索核心能力", "image_urls": []},
                ) as response:
                    lines = [line for line in response.iter_lines() if line]

        self.assertEqual(200, response.status_code)
        events = [line.removeprefix("data: ") for line in lines if line.startswith("data: ")]
        self.assertEqual("[DONE]", events[-1])

        first_chunk = json.loads(events[0])
        second_chunk = json.loads(events[1])
        self.assertEqual("多模态检索会先召回图文片段。", first_chunk["choices"][0]["delta"]["content"])
        self.assertIsNone(first_chunk["choices"][0]["finishReason"])
        self.assertEqual("stop", second_chunk["choices"][0]["finishReason"])

    def test_should_allow_agent_to_append_image_markdown_as_plain_string_chunk(self):
        with patch.dict(os.environ, {"DEFAULT_KB_ID": "kb-test"}, clear=False):
            with patch.object(tool_module, "build_mrag_agent") as build_mrag_agent:
                agent = build_mrag_agent.return_value
                agent.run.return_value = iter([
                    {
                        "choices": [
                            {
                                "delta": {"content": "命中了知识库中的图片结果。"},
                                "finishReason": None,
                                "index": 0,
                            }
                        ]
                    },
                    "\n\n![图片](http://127.0.0.1:1601/preview/req/cat.png)",
                ])

                with self.client.stream(
                        "POST",
                        "/v1/tool/mragQuery",
                        json={"question": "有没有猫的图片", "image_urls": []},
                ) as response:
                    lines = [line for line in response.iter_lines() if line]

        self.assertEqual(200, response.status_code)
        events = [line.removeprefix("data: ") for line in lines if line.startswith("data: ")]
        self.assertEqual("[DONE]", events[-1])

        first_chunk = json.loads(events[0])
        second_chunk = json.loads(events[1])
        self.assertEqual("命中了知识库中的图片结果。", first_chunk["choices"][0]["delta"]["content"])
        self.assertEqual(
            "\n\n![图片](http://127.0.0.1:1601/preview/req/cat.png)",
            second_chunk["choices"][0]["delta"]["content"],
        )
        self.assertIsNone(second_chunk["choices"][0]["finishReason"])

    def test_should_reject_blank_question(self):
        response = self.client.post(
            "/v1/tool/mragQuery",
            json={"question": "   ", "image_urls": []},
        )
        payload = response.json()

        self.assertEqual(422, response.status_code)
        self.assertIn("question", json.dumps(payload, ensure_ascii=False))

    def test_should_return_explicit_failure_chunk_when_upstream_error(self):
        with patch.dict(os.environ, {"DEFAULT_KB_ID": "kb-test"}, clear=False):
            with patch.object(tool_module, "build_mrag_agent") as build_mrag_agent:
                agent = build_mrag_agent.return_value

                def raise_error(*args, **kwargs):
                    raise RuntimeError("mock upstream unavailable")
                    yield  # pragma: no cover

                agent.run.side_effect = raise_error

                with self.client.stream(
                        "POST",
                        "/v1/tool/mragQuery",
                        json={"question": "测试异常场景", "image_urls": []},
                ) as response:
                    lines = [line for line in response.iter_lines() if line]

        self.assertEqual(200, response.status_code)
        events = [line.removeprefix("data: ") for line in lines if line.startswith("data: ")]
        self.assertEqual("[DONE]", events[-1])
        error_chunk = json.loads(events[0])
        self.assertEqual("stop", error_chunk["choices"][0]["finishReason"])
        self.assertIn("MRAG 检索失败", error_chunk["choices"][0]["delta"]["content"])

    def test_should_build_mrag_qdrant_client_from_shared_config(self):
        with patch.dict(
            os.environ,
            {
                "QDRANT_URL": "https://shared-qdrant.example.com",
                "QDRANT_PORT": "6334",
                "QDRANT_API_KEY": "shared-key",
                "QDRANT_PREFER_GRPC": "true",
            },
            clear=False,
        ):
            with patch("reactor_tool.tool.mrag.storage.qdrant_vector_store.build_qdrant_client") as build_client:
                sentinel_client = object()
                build_client.return_value = sentinel_client

                vector_store = QdrantVectorStore(config={})

        self.assertIs(sentinel_client, vector_store.client)
        build_client.assert_called_once()
        _, kwargs = build_client.call_args
        self.assertEqual("https://shared-qdrant.example.com", kwargs["url"])
        self.assertEqual(6334, kwargs["port"])
        self.assertEqual("shared-key", kwargs["api_key"])
        self.assertTrue(kwargs["prefer_grpc"])


if __name__ == "__main__":
    unittest.main()
