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

    def test_should_use_output_dir_as_process_cwd(self):
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
                result = executor.execute(
                    "import os\n"
                    "with open('cwd_result.txt', 'w', encoding='utf-8') as handle:\n"
                    "    handle.write('from-cwd')\n"
                    "print(os.getcwd())\n"
                )
                self.assertEqual(["cwd_result.txt"], [item["name"] for item in result.produced_files])
                self.assertTrue((output_dir / "cwd_result.txt").is_file())
                self.assertEqual(
                    str(output_dir.resolve()),
                    str(Path(result.stdout.strip()).resolve()),
                )
            finally:
                executor.close()

    def test_should_harvest_workspace_root_files_and_skip_input(self):
        with tempfile.TemporaryDirectory() as workspace:
            workspace_root = Path(workspace)
            output_dir = workspace_root / "output"
            output_dir.mkdir()
            (workspace_root / "input").mkdir()
            (workspace_root / "input" / "seed.csv").write_text("a,1\n", encoding="utf-8")
            (workspace_root / "__last_source__.py").write_text("print(1)\n", encoding="utf-8")
            policy = build_permission_policy(
                profile="workspace",
                workspace_root=str(workspace_root),
                output_dir=str(output_dir),
                input_files=[],
            )
            executor = PythonSandboxExecutor(policy, timeout_seconds=15)
            try:
                result = executor.execute(
                    "from pathlib import Path\n"
                    "Path(workspace_root).joinpath('root_chart.png').write_bytes(b'png')\n"
                    "Path(workspace_root).joinpath('input', 'ignored.txt').write_text('no', encoding='utf-8')\n"
                )
                self.assertEqual(["root_chart.png"], [item["name"] for item in result.produced_files])
                self.assertEqual("root_chart.png", result.produced_files[0]["relative_path"])
                self.assertTrue((workspace_root / "root_chart.png").is_file())
            finally:
                executor.close()


if __name__ == "__main__":
    unittest.main()
