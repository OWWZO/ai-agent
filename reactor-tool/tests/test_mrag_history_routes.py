# -*- coding: utf-8 -*-
import unittest
from datetime import datetime
from unittest.mock import MagicMock, patch

from fastapi import FastAPI
from fastapi.testclient import TestClient

from reactor_tool.tool.mrag.api.routes import history as history_module
from reactor_tool.tool.mrag.storage.models.mrag_session_model import MRagSessionModel
from reactor_tool.tool.mrag.storage.models.mrag_turn_model import MRagTurnModel


class MragHistoryRoutesTest(unittest.TestCase):

    def setUp(self):
        app = FastAPI()
        app.include_router(history_module.router, prefix="/v1")
        self.client = TestClient(app)

    def test_should_create_session_with_kb_scope(self):
        session_store = MagicMock()

        with patch.object(history_module, "get_mrag_session_store", return_value=session_store):
            response = self.client.post(
                "/v1/mrag/sessions/create",
                json={"kb_id": "kb-1"},
            )

        self.assertEqual(200, response.status_code)
        payload = response.json()["data"]
        self.assertEqual(["kb-1"], payload["kb_scope"])
        self.assertEqual("kb-1", payload["cover_kb_id"])
        session_store.create_session.assert_called_once()

    def test_should_return_session_detail_with_turns(self):
        session_store = MagicMock()
        turn_store = MagicMock()
        session_store.get_session.return_value = MRagSessionModel(
            session_id="mrag-session-1",
            title="SDK 鉴权流程",
            kb_scope=["kb-1"],
            cover_kb_id="kb-1",
            latest_question="如果 token 过期怎么办？",
            latest_answer_preview="前端应该先尝试刷新 token。",
            turn_count=2,
            status="SUCCESS",
            create_time=datetime(2026, 7, 15, 10, 0, 0),
            modify_time=datetime(2026, 7, 15, 10, 5, 0),
        )
        turn_store.list_turns.return_value = [
            MRagTurnModel(
                turn_id="turn-1",
                session_id="mrag-session-1",
                question="SDK 鉴权流程是什么？",
                answer_markdown="先登录，再带 token 请求。",
                status="SUCCESS",
                create_time=datetime(2026, 7, 15, 10, 0, 0),
                modify_time=datetime(2026, 7, 15, 10, 1, 0),
            )
        ]

        with patch.object(history_module, "get_mrag_session_store", return_value=session_store):
            with patch.object(history_module, "get_mrag_turn_store", return_value=turn_store):
                response = self.client.post(
                    "/v1/mrag/sessions/detail",
                    json={"session_id": "mrag-session-1"},
                )

        self.assertEqual(200, response.status_code)
        payload = response.json()["data"]
        self.assertEqual("mrag-session-1", payload["session"]["session_id"])
        self.assertEqual(1, len(payload["turns"]))
        self.assertEqual("SDK 鉴权流程是什么？", payload["turns"][0]["question"])

