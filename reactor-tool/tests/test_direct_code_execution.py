import os
import tempfile
import unittest
from pathlib import Path
from unittest.mock import AsyncMock, patch

from reactor_tool.model.protocal import CodeExecutionRequest
from reactor_tool.tool.direct_code_execution import execute_code


class DirectCodeExecutionTest(unittest.IsolatedAsyncioTestCase):
    async def test_should_return_stdout_and_uploaded_produced_file(self):
        with tempfile.TemporaryDirectory() as workspace, patch.dict(
            "os.environ", {"CODE_EXECUTION_WORKSPACE_ROOT": workspace}, clear=False
        ), patch(
            "reactor_tool.tool.direct_code_execution.upload_file_by_path",
            new=AsyncMock(return_value={
                "fileName": "result.txt", "ossUrl": "download/result.txt",
                "domainUrl": "preview/result.txt", "fileSize": 2,
            }),
        ) as upload:
            result = await execute_code(CodeExecutionRequest(
                requestId="session-direct-execution",
                source=(
                    "from pathlib import Path\n"
                    "Path(build_output_path('result.txt')).write_text('ok', encoding='utf-8')\n"
                    "print(inputs_value * 2)"
                ),
                inputs={"inputs_value": 21},
            ))

        self.assertEqual("ok", result["status"])
        self.assertIn("42", result["stdout"])
        self.assertEqual(["result.txt"], [item["name"] for item in result["producedFiles"]])
        self.assertEqual("result.txt", result["fileInfo"][0]["fileName"])
        upload.assert_awaited_once()

    async def test_should_preserve_stdout_and_files_when_source_fails(self):
        with tempfile.TemporaryDirectory() as workspace, patch.dict(
            "os.environ", {"CODE_EXECUTION_WORKSPACE_ROOT": workspace}, clear=False
        ), patch("reactor_tool.tool.direct_code_execution.upload_file_by_path", new=AsyncMock(return_value={"fileName": "partial.txt"})):
            result = await execute_code(CodeExecutionRequest(
                requestId="session-direct-error",
                source=("from pathlib import Path\n"
                        "print('before failure')\n"
                        "Path(build_output_path('partial.txt')).write_text('x')\n"
                        "raise RuntimeError('boom')"),
            ))

        self.assertEqual("error", result["status"])
        self.assertIn("before failure", result["stdout"])
        self.assertEqual(["partial.txt"], [item["name"] for item in result["producedFiles"]])

    async def test_should_upload_relative_cwd_file_and_workspace_root_file(self):
        uploads = AsyncMock(side_effect=[
            {"fileName": "chart.png", "ossUrl": "d/chart.png", "domainUrl": "p/chart.png", "fileSize": 3},
            {"fileName": "notes.txt", "ossUrl": "d/notes.txt", "domainUrl": "p/notes.txt", "fileSize": 2},
        ])
        with tempfile.TemporaryDirectory() as workspace, patch.dict(
            "os.environ", {"CODE_EXECUTION_WORKSPACE_ROOT": workspace}, clear=False
        ), patch("reactor_tool.tool.direct_code_execution.upload_file_by_path", new=uploads):
            result = await execute_code(CodeExecutionRequest(
                requestId="session-cwd-root-harvest",
                permissionProfile="workspace",
                source=(
                    "from pathlib import Path\n"
                    "Path('chart.png').write_bytes(b'png')\n"
                    "Path(workspace_root).joinpath('notes.txt').write_text('ok', encoding='utf-8')\n"
                ),
            ))

        self.assertEqual("ok", result["status"])
        self.assertEqual(
            {"chart.png", "notes.txt"},
            {item["name"] for item in result["producedFiles"]},
        )
        self.assertEqual(2, uploads.await_count)

    async def test_should_use_skilloutput_session_workspace_by_default(self):
        with tempfile.TemporaryDirectory() as skill_output:
            env = {key: value for key, value in os.environ.items() if key != "CODE_EXECUTION_WORKSPACE_ROOT"}
            env["SKILL_OUTPUT_ROOT"] = skill_output
            with patch.dict("os.environ", env, clear=True), patch(
                "reactor_tool.tool.direct_code_execution.upload_file_by_path",
                new=AsyncMock(return_value={"fileName": "out.txt"}),
            ):
                result = await execute_code(CodeExecutionRequest(
                    requestId="session-skill-align",
                    source=(
                        "from pathlib import Path\n"
                        "Path(build_output_path('out.txt')).write_text('ok', encoding='utf-8')\n"
                    ),
                ))
                self.assertEqual("ok", result["status"])
                expected = Path(skill_output) / "session-skill-align"
                self.assertEqual(str(expected.resolve()), str(Path(result["workspace"]).resolve()))
                self.assertTrue((expected / "output" / "out.txt").is_file())

    async def test_should_honor_explicit_workspace_root(self):
        with tempfile.TemporaryDirectory() as session_root, patch(
            "reactor_tool.tool.direct_code_execution.upload_file_by_path",
            new=AsyncMock(return_value={"fileName": "a.txt"}),
        ):
            result = await execute_code(CodeExecutionRequest(
                requestId="ignored-for-path",
                workspaceRoot=session_root,
                source=(
                    "from pathlib import Path\n"
                    "Path(build_output_path('a.txt')).write_text('x', encoding='utf-8')\n"
                ),
            ))
            self.assertEqual("ok", result["status"])
            self.assertEqual(str(Path(session_root).resolve()), str(Path(result["workspace"]).resolve()))
            self.assertTrue((Path(session_root) / "output" / "a.txt").is_file())


if __name__ == "__main__":
    unittest.main()
