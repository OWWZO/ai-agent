# -*- coding: utf-8 -*-
import unittest

from reactor_tool.model.protocal import CIRequest
from reactor_tool.tool.code_interpreter_policy import (
    CodeExecutionPermissionError,
    build_permission_policy,
    build_runtime_helpers,
    validate_code_against_policy,
)


class CodeInterpreterPermissionPolicyTest(unittest.TestCase):
    def setUp(self):
        self.workspace_root = r"D:\temp\ci-workspace"
        self.output_dir = r"D:\temp\ci-workspace\output"
        self.input_files = [
            {
                "name": "sales.csv",
                "path": r"D:\temp\ci-workspace\sales.csv",
            }
        ]

    def test_ci_request_should_default_to_analysis_permission_profile(self):
        request = CIRequest(requestId="req-1")

        self.assertEqual("analysis", request.permission_profile)

    def test_analysis_profile_should_block_pathlib_import(self):
        policy = build_permission_policy(
            profile="analysis",
            workspace_root=self.workspace_root,
            output_dir=self.output_dir,
            input_files=self.input_files,
        )

        with self.assertRaises(CodeExecutionPermissionError) as context:
            validate_code_against_policy(
                "from pathlib import Path\nPath('demo.txt').write_text('x', encoding='utf-8')",
                policy,
            )

        self.assertEqual("unauthorized_import", context.exception.blocked_reason)

    def test_analysis_profile_should_block_write_outside_output_dir(self):
        policy = build_permission_policy(
            profile="analysis",
            workspace_root=self.workspace_root,
            output_dir=self.output_dir,
            input_files=self.input_files,
        )

        with self.assertRaises(CodeExecutionPermissionError) as context:
            validate_code_against_policy(
                "import pandas as pd\ndf = pd.DataFrame({'a': [1]})\ndf.to_excel(r'D:\\escape.xlsx')",
                policy,
            )

        self.assertEqual("path_outside_allowed_roots", context.exception.blocked_reason)

    def test_analysis_profile_should_allow_build_output_path_helper(self):
        policy = build_permission_policy(
            profile="analysis",
            workspace_root=self.workspace_root,
            output_dir=self.output_dir,
            input_files=self.input_files,
        )

        validate_code_against_policy(
            "import pandas as pd\ndf = pd.DataFrame({'a': [1]})\ndf.to_excel(build_output_path('结果.xlsx'))",
            policy,
        )

    def test_workspace_profile_should_allow_workspace_local_write(self):
        policy = build_permission_policy(
            profile="workspace",
            workspace_root=self.workspace_root,
            output_dir=self.output_dir,
            input_files=self.input_files,
        )

        validate_code_against_policy(
            "from pathlib import Path\nPath(workspace_root + r'\\notes.txt').write_text('ok', encoding='utf-8')",
            policy,
        )

    def test_runtime_helper_should_reject_path_escape(self):
        policy = build_permission_policy(
            profile="analysis",
            workspace_root=self.workspace_root,
            output_dir=self.output_dir,
            input_files=self.input_files,
        )
        helpers = build_runtime_helpers(policy)

        with self.assertRaises(CodeExecutionPermissionError) as context:
            helpers["build_output_path"]("../escape.txt")

        self.assertEqual("path_outside_allowed_roots", context.exception.blocked_reason)


if __name__ == "__main__":
    unittest.main()
