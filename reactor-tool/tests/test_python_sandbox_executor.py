import tempfile
import unittest
from pathlib import Path

from reactor_tool.tool.code_interpreter_policy import build_permission_policy
from reactor_tool.tool.python_sandbox_executor import PythonSandboxExecutionError, PythonSandboxExecutor


class PythonSandboxExecutorTest(unittest.TestCase):
    def test_should_keep_python_state_and_report_output_files(self):
        with tempfile.TemporaryDirectory() as workspace:
            workspace_root = Path(workspace)
            output_dir = workspace_root / "output"
            output_dir.mkdir()
            policy = build_permission_policy(
                profile="analysis",
                workspace_root=str(workspace_root),
                output_dir=str(output_dir),
                input_files=[],
            )
            executor = PythonSandboxExecutor(policy, timeout_seconds=15)
            try:
                first = executor.execute(
                    "from pathlib import Path\n"
                    "counter = 41\n"
                    "Path(build_output_path('result.txt')).write_text('ok', encoding='utf-8')\n"
                    "print('created')"
                )
                second = executor.execute("print(counter + 1)")

                self.assertIn("created", first.stdout)
                self.assertEqual(["result.txt"], [item["name"] for item in first.produced_files])
                self.assertIn("42", second.stdout)
                self.assertTrue((output_dir / "result.txt").is_file())
                self.assertEqual(["result.txt"], [item["name"] for item in executor.produced_files()])
            finally:
                executor.close()

    def test_should_keep_produced_file_when_code_fails_after_writing(self):
        with tempfile.TemporaryDirectory() as workspace:
            workspace_root = Path(workspace)
            output_dir = workspace_root / "output"
            output_dir.mkdir()
            policy = build_permission_policy(
                profile="analysis",
                workspace_root=str(workspace_root),
                output_dir=str(output_dir),
                input_files=[],
            )
            executor = PythonSandboxExecutor(policy, timeout_seconds=15)
            try:
                with self.assertRaisesRegex(PythonSandboxExecutionError, "expected failure"):
                    executor.execute(
                        "from pathlib import Path\n"
                        "Path(build_output_path('partial.txt')).write_text('partial', encoding='utf-8')\n"
                        "raise RuntimeError('expected failure')"
                    )

                self.assertTrue((output_dir / "partial.txt").is_file())
                self.assertEqual(["partial.txt"], [item["name"] for item in executor.produced_files()])
            finally:
                executor.close()


if __name__ == "__main__":
    unittest.main()
