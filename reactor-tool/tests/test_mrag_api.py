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
        app = FastAPI()
        app.include_router(tool_router, prefix="/v1/tool")
        self.client = TestClient(app)

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


if __name__ == "__main__":
    unittest.main()
