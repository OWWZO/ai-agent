# -*- coding: utf-8 -*-
import os
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from reactor_tool.model.protocal import CIRequest
from reactor_tool.tool.code_interpreter_policy import (
    CodeExecutionPermissionError,
    build_permission_policy,
    build_runtime_helpers,
    is_path_sandbox_enabled,
    is_pre_execution_validation_enabled,
    is_runtime_double_check_enabled,
    validate_authorized_path,
    validate_code_against_policy,
)


class CodeInterpreterPermissionPolicyTest(unittest.TestCase):
    def setUp(self):
        # 默认基线显式开启，避免受“.env 默认关闭”影响已有防护行为断言。
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
        self.workspace_root = r"D:\temp\ci-workspace"
        self.output_dir = r"D:\temp\ci-workspace\output"
        self.input_files = [
            {
                "name": "sales.csv",
                "path": r"D:\temp\ci-workspace\sales.csv",
            }
        ]

    def tearDown(self):
        self.env_patcher.stop()

    def test_ci_request_should_default_to_analysis_permission_profile(self):
        request = CIRequest(requestId="req-1")

        self.assertEqual("analysis", request.permission_profile)

    def test_analysis_profile_should_allow_pathlib_import_with_safe_output_path(self):
        policy = build_permission_policy(
            profile="analysis",
            workspace_root=self.workspace_root,
            output_dir=self.output_dir,
            input_files=self.input_files,
        )

        validate_code_against_policy(
            "from pathlib import Path\n"
            "Path(build_output_path('结果.txt')).write_text('x', encoding='utf-8')",
            policy,
        )

    def test_analysis_profile_should_block_pathlib_write_outside_output_dir(self):
        policy = build_permission_policy(
            profile="analysis",
            workspace_root=self.workspace_root,
            output_dir=self.output_dir,
            input_files=self.input_files,
        )

        with self.assertRaises(CodeExecutionPermissionError) as context:
            validate_code_against_policy(
                "from pathlib import Path\n"
                "Path(r'D:\\escape.txt').write_text('x', encoding='utf-8')",
                policy,
            )

        self.assertEqual("path_outside_allowed_roots", context.exception.blocked_reason)

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

    def test_analysis_profile_should_allow_direct_relative_output_write(self):
        policy = build_permission_policy(
            profile="analysis",
            workspace_root=self.workspace_root,
            output_dir=self.output_dir,
            input_files=self.input_files,
        )

        validate_code_against_policy(
            "from pathlib import Path\n"
            "Path('结果.txt').write_text('x', encoding='utf-8')",
            policy,
        )

    def test_analysis_profile_should_allow_direct_input_file_name_read(self):
        policy = build_permission_policy(
            profile="analysis",
            workspace_root=self.workspace_root,
            output_dir=self.output_dir,
            input_files=self.input_files,
        )

        validate_code_against_policy(
            "import pandas as pd\n"
            "df = pd.read_csv('sales.csv')\n"
            "df.head()",
            policy,
        )

    def test_analysis_profile_should_allow_helper_with_derived_output_name(self):
        policy = build_permission_policy(
            profile="analysis",
            workspace_root=self.workspace_root,
            output_dir=self.output_dir,
            input_files=self.input_files,
        )

        validate_code_against_policy(
            "source_name = 'sales.csv'\n"
            "output_name = source_name.replace('.csv', '_分析.xlsx')\n"
            "output_path = build_output_path(output_name)\n"
            "import pandas as pd\n"
            "df = pd.DataFrame({'a': [1]})\n"
            "df.to_excel(output_path)",
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

    def test_analysis_profile_should_block_helper_name_override(self):
        policy = build_permission_policy(
            profile="analysis",
            workspace_root=self.workspace_root,
            output_dir=self.output_dir,
            input_files=self.input_files,
        )

        with self.assertRaises(CodeExecutionPermissionError) as context:
            validate_code_against_policy(
                "def build_output_path(file_name):\n"
                "    return r'D:\\escape.xlsx'\n"
                "import pandas as pd\n"
                "df = pd.DataFrame({'a': [1]})\n"
                "df.to_excel(build_output_path('结果.xlsx'))",
                policy,
            )

        self.assertEqual("helper_name_override", context.exception.blocked_reason)

    def test_analysis_profile_should_block_destructive_path_call(self):
        policy = build_permission_policy(
            profile="analysis",
            workspace_root=self.workspace_root,
            output_dir=self.output_dir,
            input_files=self.input_files,
        )

        with self.assertRaises(CodeExecutionPermissionError) as context:
            validate_code_against_policy(
                "from pathlib import Path\n"
                "Path('结果.txt').unlink()",
                policy,
            )

        self.assertEqual("destructive_operation_denied", context.exception.blocked_reason)

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

    def test_pre_execution_validation_should_skip_when_env_disabled(self):
        policy = build_permission_policy(
            profile="analysis",
            workspace_root=self.workspace_root,
            output_dir=self.output_dir,
            input_files=self.input_files,
        )

        with patch.dict(
            os.environ,
            {"CODE_INTERPRETER_ENABLE_PRE_EXECUTION_VALIDATION": "false"},
            clear=False,
        ):
            validate_code_against_policy("import os\nos.getcwd()", policy)

    def test_path_sandbox_should_allow_path_escape_when_env_disabled(self):
        policy = build_permission_policy(
            profile="analysis",
            workspace_root=self.workspace_root,
            output_dir=self.output_dir,
            input_files=self.input_files,
        )

        with tempfile.TemporaryDirectory() as external_dir:
            external_path = Path(external_dir).joinpath("escape.txt")
            with patch.dict(
                os.environ,
                {"CODE_INTERPRETER_ENABLE_PATH_SANDBOX": "false"},
                clear=False,
            ):
                resolved = validate_authorized_path(
                    str(external_path),
                    policy=policy,
                    access_mode="write",
                )

        self.assertEqual(str(external_path.resolve()), resolved)


class CodeInterpreterToggleDefaultsTest(unittest.TestCase):
    def test_permission_toggles_should_default_to_disabled(self):
        with patch.dict(os.environ, {}, clear=True):
            self.assertFalse(is_pre_execution_validation_enabled())
            self.assertFalse(is_path_sandbox_enabled())
            self.assertFalse(is_runtime_double_check_enabled())


if __name__ == "__main__":
    unittest.main()
