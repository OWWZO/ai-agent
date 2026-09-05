# -*- coding: utf-8 -*-
import importlib
import sys
import types
import unittest
from pathlib import Path
from unittest.mock import AsyncMock, patch

from reactor_tool.model.code import ActionOutput


class FakeFinalAnswerStep:
    def __init__(self, output: str):
        self.output = output


def load_code_interpreter_module():
    # 这些依赖只影响真正的模型执行；当前测试只验证产物回传逻辑，补最小桩即可。
    if "smolagents" not in sys.modules:
        smolagents_stub = types.ModuleType("smolagents")
        for name in (
            "ChatMessage",
            "LiteLLMModel",
            "OpenAIServerModel",
            "FinalAnswerStep",
            "PythonInterpreterTool",
            "ChatMessageStreamDelta",
        ):
            setattr(smolagents_stub, name, type(name, (), {}))
        sys.modules["smolagents"] = smolagents_stub

    if "reactor_tool.tool.ci_agent" not in sys.modules:
        ci_agent_stub = types.ModuleType("reactor_tool.tool.ci_agent")
        ci_agent_stub.CIAgent = type("CIAgent", (), {})
        sys.modules["reactor_tool.tool.ci_agent"] = ci_agent_stub

    if "reactor_tool.util.llm_util" not in sys.modules:
        llm_util_stub = types.ModuleType("reactor_tool.util.llm_util")
        llm_util_stub.ask_llm_sync_iter = lambda *args, **kwargs: iter(())
        sys.modules["reactor_tool.util.llm_util"] = llm_util_stub

    return importlib.import_module("reactor_tool.tool.code_interpreter")


class CodeInterpreterOutputUploadTest(unittest.IsolatedAsyncioTestCase):
    async def test_should_upload_generated_image_files_from_output_dir(self):
        code_interpreter_module = load_code_interpreter_module()
        prompt_template = {"task_template": "{{ task }}"}

        async def fake_upload_file_by_path(file_path: str, request_id: str):
            file_name = Path(file_path).name
            return {
                "fileName": file_name,
                "domainUrl": f"preview/{request_id}/{file_name}",
                "downloadUrl": f"download/{request_id}/{file_name}",
            }

        async def fake_upload_file(
            content: str, file_name: str, file_type: str, request_id: str
        ):
            return {
                "fileName": file_name,
                "domainUrl": f"preview/{request_id}/{file_name}",
                "downloadUrl": f"download/{request_id}/{file_name}",
            }

        class FakeAgent:
            def __init__(self, output_dir: str):
                self.output_dir = Path(output_dir)

            def run(self, task: str, stream: bool = True, max_steps: int = 10):
                self.output_dir.joinpath("chart.png").write_bytes(b"fake-png")
                yield FakeFinalAnswerStep("图表已生成")

            def get_produced_files(self):
                return [{"file_path": str(self.output_dir.joinpath("chart.png"))}]

            def close_sandbox(self):
                return None

        def fake_create_ci_agent(*args, **kwargs):
            return FakeAgent(kwargs["output_dir"])

        with (
            patch(
                "reactor_tool.tool.code_interpreter.download_all_files_in_path",
                new=AsyncMock(return_value=[]),
            ),
            patch(
                "reactor_tool.tool.code_interpreter.get_prompt",
                return_value=prompt_template,
            ),
            patch(
                "reactor_tool.tool.code_interpreter.create_ci_agent",
                side_effect=fake_create_ci_agent,
            ),
            patch(
                "reactor_tool.tool.code_interpreter.upload_file_by_path",
                new=AsyncMock(side_effect=fake_upload_file_by_path),
            ),
            patch(
                "reactor_tool.tool.code_interpreter.upload_file",
                new=AsyncMock(side_effect=fake_upload_file),
            ),
            patch(
                "reactor_tool.tool.code_interpreter.FinalAnswerStep",
                new=FakeFinalAnswerStep,
            ),
        ):
            outputs = [
                item
                async for item in code_interpreter_module.code_interpreter_agent(
                    task="生成图表",
                    request_id="req-image-output",
                    report_file_name="图表报告.md",
                    stream=True,
                )
            ]

        result = next(item for item in outputs if isinstance(item, ActionOutput))
        self.assertIsInstance(result, ActionOutput)
        self.assertEqual("图表已生成", result.content)
        self.assertEqual(
            ["chart.png", "图表报告.md"],
            [item["fileName"] for item in result.file_list],
        )

    async def test_should_keep_final_answer_when_report_upload_fails(self):
        code_interpreter_module = load_code_interpreter_module()
        prompt_template = {"task_template": "{{ task }}"}

        class FakeAgent:
            def run(self, task: str, stream: bool = True, max_steps: int = 10):
                yield FakeFinalAnswerStep("结论仍然可用")

            def get_produced_files(self):
                return []

            def close_sandbox(self):
                return None

        with (
            patch(
                "reactor_tool.tool.code_interpreter.download_all_files_in_path",
                new=AsyncMock(return_value=[]),
            ),
            patch(
                "reactor_tool.tool.code_interpreter.get_prompt",
                return_value=prompt_template,
            ),
            patch(
                "reactor_tool.tool.code_interpreter.create_ci_agent",
                return_value=FakeAgent(),
            ),
            patch(
                "reactor_tool.tool.code_interpreter.upload_file",
                new=AsyncMock(side_effect=RuntimeError("HTTP 500")),
            ),
            patch(
                "reactor_tool.tool.code_interpreter.FinalAnswerStep",
                new=FakeFinalAnswerStep,
            ),
        ):
            outputs = [
                item
                async for item in code_interpreter_module.code_interpreter_agent(
                    task="生成结论",
                    request_id="req-report-upload-failure",
                    report_file_name="结论报告.md",
                    stream=True,
                )
            ]

        result = next(item for item in outputs if isinstance(item, ActionOutput))
        self.assertEqual("结论仍然可用", result.content)
        self.assertEqual([], result.file_list)


if __name__ == "__main__":
    unittest.main()
