# -*- coding: utf-8 -*-
import json
import os
import sys
import types
import unittest
import importlib.util
from pathlib import Path
from unittest.mock import MagicMock, patch

from fastapi import FastAPI
from fastapi.testclient import TestClient
from sse_starlette.sse import AppStatus

from reactor_tool.tool.mrag.storage.models.mrag_session_model import MRagSessionModel

if "litellm" not in sys.modules:
    litellm_stub = types.ModuleType("litellm")

    async def _stub_acompletion(*args, **kwargs):
        raise RuntimeError("litellm stub should not be called in MRAG history persistence tests")

    litellm_stub.acompletion = _stub_acompletion
    sys.modules["litellm"] = litellm_stub

if "trafilatura" not in sys.modules:
    trafilatura_stub = types.ModuleType("trafilatura")
    trafilatura_stub.extract = lambda *args, **kwargs: ""
    sys.modules["trafilatura"] = trafilatura_stub

tool_module_spec = importlib.util.spec_from_file_location(
    "mrag_tool_router_history_under_test",
    Path(__file__).resolve().parents[1] / "reactor_tool" / "api" / "tool.py",
)
tool_module = importlib.util.module_from_spec(tool_module_spec)
assert tool_module_spec is not None and tool_module_spec.loader is not None
tool_module_spec.loader.exec_module(tool_module)
tool_router = tool_module.router


class MragHistoryPersistenceTest(unittest.TestCase):

    def setUp(self):
        AppStatus.should_exit = False
        AppStatus.should_exit_event = None
        app = FastAPI()
        app.include_router(tool_router, prefix="/v1/tool")
        self.client = TestClient(app)

    def tearDown(self):
        AppStatus.should_exit = False
        AppStatus.should_exit_event = None

    def test_should_persist_turn_and_update_session_when_session_id_present(self):
        session_store = MagicMock()
        turn_store = MagicMock()
        session_store.get_session.side_effect = [
            None,
            MRagSessionModel(
                session_id="mrag-session-1",
                title="新对话",
                kb_scope=["kb-1"],
                cover_kb_id="kb-1",
                latest_question="",
                latest_answer_preview="",
                turn_count=0,
                status="RUNNING",
            ),
        ]
        turn_store.list_turns.return_value = [MagicMock()]

        with patch.dict(os.environ, {"DEFAULT_KB_ID": "kb-default"}, clear=False):
            with patch.object(tool_module, "build_mrag_agent") as build_mrag_agent:
                with patch.object(tool_module, "get_mrag_session_store", return_value=session_store):
                    with patch.object(tool_module, "get_mrag_turn_store", return_value=turn_store):
                        agent = build_mrag_agent.return_value
                        agent.run.return_value = iter([
                            {
                                "choices": [
                                    {
                                        "delta": {"content": "这是回答正文。"},
                                        "finishReason": "stop",
                                        "index": 0,
                                    }
                                ]
                            }
                        ])

                        with self.client.stream(
                            "POST",
                            "/v1/tool/mragQuery",
                            json={
                                "session_id": "mrag-session-1",
                                "kb_id": "kb-1",
                                "question": "解释鉴权流程",
                                "image_urls": [],
                            },
                        ) as response:
                            lines = [line for line in response.iter_lines() if line]

        self.assertEqual(200, response.status_code)
        events = [line.removeprefix("data: ") for line in lines if line.startswith("data: ")]
        self.assertEqual("[DONE]", events[-1])
        turn_store.create_turn.assert_called_once()
        turn_store.update_turn.assert_called_once()
        session_store.create_session.assert_called_once()
        session_store.update_session.assert_called_once()
        updated_session = session_store.update_session.call_args.args[0]
        self.assertEqual("解释鉴权流程", updated_session.latest_question)
        self.assertEqual("这是回答正文。", updated_session.latest_answer_preview)
        updated_turn = turn_store.update_turn.call_args.args[0]
        self.assertEqual("SUCCESS", updated_turn.status)
        self.assertEqual("这是回答正文。", updated_turn.answer_markdown)
