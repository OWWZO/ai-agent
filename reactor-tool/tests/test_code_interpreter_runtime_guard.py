# -*- coding: utf-8 -*-
import os
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import pandas as pd

from reactor_tool.tool.code_interpreter_policy import (
    CodeExecutionPermissionError,
    build_permission_policy,
)
from reactor_tool.tool.code_interpreter_runtime_guard import activate_runtime_io_guard


class CodeInterpreterRuntimeGuardTest(unittest.TestCase):
    def setUp(self):
        # 这些用例验证 guard 本身行为，显式开启运行时双检避免默认值干扰。
        self.env_patcher = patch.dict(
            os.environ,
            {
                "CODE_INTERPRETER_ENABLE_PRE_EXECUTION_VALIDATION": "true",
                "CODE_INTERPRETER_ENABLE_PATH_SANDBOX": "true",
                "CODE_INTERPRETER_ENABLE_RUNTIME_DOUBLE_CHECK": "true",
            },
            clear=False,
        )
        self.env_patcher.start()
        self.temp_dir = tempfile.TemporaryDirectory()
        self.workspace_root = self.temp_dir.name
        self.output_dir = str(Path(self.workspace_root).joinpath("output"))
        Path(self.output_dir).mkdir(parents=True, exist_ok=True)
        self.input_path = Path(self.workspace_root).joinpath("sales.csv")
        self.input_path.write_text("name,value\nA,1\n", encoding="utf-8")
        self.policy = build_permission_policy(
            profile="analysis",
            workspace_root=self.workspace_root,
            output_dir=self.output_dir,
            input_files=[
                {
                    "name": "sales.csv",
                    "path": str(self.input_path),
                }
            ],
        )

    def tearDown(self):
        self.temp_dir.cleanup()
        self.env_patcher.stop()

    def test_guard_should_normalize_relative_output_write_for_pathlib(self):
        with activate_runtime_io_guard(self.policy):
            Path("分析结果.txt").write_text("ok", encoding="utf-8")

        self.assertTrue(Path(self.output_dir).joinpath("分析结果.txt").exists())

    def test_guard_should_normalize_relative_input_read_for_pandas(self):
        with activate_runtime_io_guard(self.policy):
            dataframe = pd.read_csv("sales.csv")

        self.assertEqual(["name", "value"], list(dataframe.columns))
        self.assertEqual("A", dataframe.iloc[0]["name"])

    def test_guard_should_allow_absolute_input_path_for_pandas(self):
        with activate_runtime_io_guard(self.policy):
            dataframe = pd.read_csv(self.input_path)

        self.assertEqual(1, int(dataframe.iloc[0]["value"]))

    def test_guard_should_block_path_escape(self):
        with self.assertRaises(CodeExecutionPermissionError) as context:
            with activate_runtime_io_guard(self.policy):
                Path("../escape.txt").write_text("x", encoding="utf-8")

        self.assertEqual("path_outside_allowed_roots", context.exception.blocked_reason)

    def test_guard_should_block_overwriting_input_file(self):
        with self.assertRaises(CodeExecutionPermissionError) as context:
            with activate_runtime_io_guard(self.policy):
                Path(str(self.input_path)).write_text("bad", encoding="utf-8")

        self.assertEqual("input_file_read_only", context.exception.blocked_reason)

    def test_guard_should_be_passthrough_when_runtime_double_check_env_disabled(self):
        with tempfile.TemporaryDirectory() as external_dir:
            external_path = Path(external_dir).joinpath("escape.txt")
            with patch.dict(
                os.environ,
                {"CODE_INTERPRETER_ENABLE_RUNTIME_DOUBLE_CHECK": "false"},
                clear=False,
            ):
                with activate_runtime_io_guard(self.policy):
                    external_path.write_text("x", encoding="utf-8")

            self.assertTrue(external_path.exists())
            self.assertEqual("x", external_path.read_text(encoding="utf-8"))


if __name__ == "__main__":
    unittest.main()
