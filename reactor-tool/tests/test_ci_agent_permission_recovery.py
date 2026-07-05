# -*- coding: utf-8 -*-
import unittest
from unittest.mock import patch

from smolagents import PythonInterpreterTool
from smolagents.memory import ActionStep
from smolagents.models import ChatMessageStreamDelta

from reactor_tool.tool.ci_agent import CIAgent
from reactor_tool.tool.code_interpreter_policy import CodeExecutionPermissionError


class DummyStreamModel:
    def __init__(self):
        self.calls = 0
        self.model_id = "dummy-ci-model"

    def generate_stream(self, messages, **kwargs):
        del messages, kwargs
        self.calls += 1
        if self.calls == 1:
            content = """Thought: 先尝试直接写文件
Code:
<code>
from pathlib import Path
dynamic_name = '结果.txt'
Path(dynamic_name).write_text('hello', encoding='utf-8')
</code>"""
        else:
            content = """Thought: 改成受控输出
Code:
<code>
result = 'permission-fixed'
result
</code>"""
        yield ChatMessageStreamDelta(content=content)


class FakeFinalAnswerCheck:
    def __init__(self, *args, **kwargs):
        del args, kwargs

    def check_is_final_answer(self):
        return True, "permission-fixed"


class CIAgentPermissionRecoveryTest(unittest.TestCase):
    def test_agent_should_retry_after_permission_error(self):
        model = DummyStreamModel()

        def before_execute(code_action: str):
            if "permission-fixed" in code_action:
                return
            raise CodeExecutionPermissionError(
                "unresolved_path",
                "无法静态确认文件访问路径，请改用 build_output_path()/resolve_input_path()/build_workspace_path()。",
                detail="call: Path(dynamic_name).write_text('hello', encoding='utf-8')",
            )

        agent = CIAgent(
            tools=[PythonInterpreterTool()],
            model=model,
            additional_authorized_imports=["pathlib"],
            before_execute=before_execute,
        )

        with patch("reactor_tool.tool.ci_agent.FinalAnswerCheck", FakeFinalAnswerCheck):
            output = agent.run("生成一个文本结果", max_steps=2)

        self.assertEqual("permission-fixed", output)
        self.assertEqual(2, model.calls)

        action_steps = [step for step in agent.memory.steps if isinstance(step, ActionStep)]
        self.assertEqual(2, len(action_steps))
        self.assertIsNotNone(action_steps[0].error)
        self.assertIn("build_output_path", action_steps[0].observations or "")
        self.assertIn("Path(dynamic_name).write_text", action_steps[0].observations or "")
        self.assertEqual("permission-fixed", action_steps[1].action_output)


if __name__ == "__main__":
    unittest.main()
