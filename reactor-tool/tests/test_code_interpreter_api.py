# -*- coding: utf-8 -*-
import unittest
from unittest.mock import AsyncMock, patch

from fastapi import FastAPI
from fastapi.testclient import TestClient

from reactor_tool.api.tool import router
from reactor_tool.model.code import ActionOutput


class CodeInterpreterApiTest(unittest.TestCase):
    def test_should_forward_permission_profile_to_code_interpreter_agent(self):
        app = FastAPI()
        app.include_router(router)
        captured_kwargs = {}

        async def fake_agent(**kwargs):
            captured_kwargs.update(kwargs)
            yield ActionOutput(content="ok", file_list=[])

        with patch("reactor_tool.tool.code_interpreter.code_interpreter_agent", new=fake_agent), patch(
            "reactor_tool.api.tool.upload_file",
            new=AsyncMock(return_value={"fileName": "code_output.md"}),
        ):
            client = TestClient(app)
            response = client.post(
                "/code_interpreter",
                json={
                    "requestId": "req-api-1",
                    "task": "生成一个汇总文件",
                    "permissionProfile": "workspace",
                    "stream": False,
                },
            )

        self.assertEqual(200, response.status_code)
        self.assertEqual("workspace", captured_kwargs.get("permission_profile"))


if __name__ == "__main__":
    unittest.main()
