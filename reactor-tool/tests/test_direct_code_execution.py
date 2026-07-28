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



if __name__ == "__main__":
    unittest.main()
