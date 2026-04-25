# -*- coding: utf-8 -*-
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
        raise RuntimeError("litellm stub should not be called in embedding proxy tests")

    litellm_stub.acompletion = _stub_acompletion
    sys.modules["litellm"] = litellm_stub

tool_module_spec = importlib.util.spec_from_file_location(
    "embedding_tool_router_under_test",
    Path(__file__).resolve().parents[1] / "reactor_tool" / "api" / "tool.py",
)
tool_module = importlib.util.module_from_spec(tool_module_spec)
assert tool_module_spec is not None and tool_module_spec.loader is not None
tool_module_spec.loader.exec_module(tool_module)
tool_router = tool_module.router


class EmbeddingProxyApiTest(unittest.TestCase):

    def setUp(self):
        app = FastAPI()
        app.include_router(tool_router, prefix="/v1/tool")
        self.client = TestClient(app)

    def test_should_return_normalized_vectors_and_metadata(self):
        fake_model = types.SimpleNamespace(
            encode_text_batch=lambda texts: [[3.0, 4.0], [0.0, 2.0]]
        )
        with patch.dict(os.environ, {"TEXT_EMBEDDING_MODEL_NAME": "text-embedding-v4"}, clear=False):
            with patch("reactor_tool.tool.mrag.embedding.text_embedding.get_text_embedding_model", return_value=fake_model):
                response = self.client.post(
                    "/v1/tool/embedding/text",
                    json={"inputs": ["customer_id 含义", "订单状态字段说明"], "normalize": True},
                )

        self.assertEqual(200, response.status_code)
        body = response.json()
        self.assertEqual("text-embedding-v4", body["model"])
        self.assertEqual(2, body["dimension"])
        self.assertEqual(2, len(body["vectors"]))
        self.assertAlmostEqual(0.6, body["vectors"][0][0], places=6)
        self.assertAlmostEqual(0.8, body["vectors"][0][1], places=6)
        self.assertAlmostEqual(0.0, body["vectors"][1][0], places=6)
        self.assertAlmostEqual(1.0, body["vectors"][1][1], places=6)

    def test_should_return_provider_error_payload(self):
        with patch("reactor_tool.tool.mrag.embedding.text_embedding.get_text_embedding_model", side_effect=RuntimeError("boom")):
            response = self.client.post(
                "/v1/tool/embedding/text",
                json={"inputs": ["customer_id 含义"], "normalize": False},
            )

        self.assertEqual(502, response.status_code)
        self.assertIn("共享文本向量服务调用失败", response.json()["message"])

    def test_should_return_timeout_error_payload(self):
        with patch("reactor_tool.tool.mrag.embedding.text_embedding.get_text_embedding_model", side_effect=TimeoutError("timeout")):
            response = self.client.post(
                "/v1/tool/embedding/text",
                json={"inputs": ["customer_id 含义"], "normalize": False},
            )

        self.assertEqual(504, response.status_code)
        self.assertIn("共享文本向量服务调用超时", response.json()["message"])


if __name__ == "__main__":
    unittest.main()
