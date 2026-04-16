# -*- coding: utf-8 -*-
import os
import sys
import tempfile
import textwrap
import unittest
from pathlib import Path
from unittest.mock import AsyncMock, patch

from reactor_tool.model.protocal import ScriptRunnerRequest
from reactor_tool.tool.script_runner import run_script_request
from reactor_tool.tool.script_runtime import build_command


class ScriptRunnerTest(unittest.IsolatedAsyncioTestCase):
    async def test_python_runtime_should_upload_generated_files(self):
        with tempfile.TemporaryDirectory(prefix="skill-script-runner-") as temp_dir:
            skill_dir = self._create_python_skill(Path(temp_dir), """
                import json
                import os
                from pathlib import Path

                arguments = json.loads(os.environ["SKILL_ARGUMENTS_JSON"])
                output_dir = Path("output")
                output_dir.mkdir(parents=True, exist_ok=True)
                target_file = output_dir / "summary.md"
                target_file.write_text(f"table={arguments['table']}", encoding="utf-8")
                print("python runtime ok")
            """)

            request = ScriptRunnerRequest(
                request_id="req-python",
                skill_name="demo-skill",
                skill_base_path=str(skill_dir),
                script_name="summarize",
                script_path="scripts/summarize.py",
                runtime="python",
                arguments={"table": "sales_order"},
                argv=[],
                timeout_seconds=5,
            )

            async_upload = AsyncMock(side_effect=self._fake_upload)
            with patch("reactor_tool.tool.script_runner.upload_file_by_path", new=async_upload):
                response = await run_script_request(request)

            self.assertTrue(response.success)
            self.assertEqual(0, response.exit_code)
            self.assertIn("python runtime ok", response.stdout)
            self.assertEqual("脚本执行成功", response.summary)
            self.assertEqual(1, len(response.file_info))
            self.assertEqual("summary.md", response.file_info[0].file_name)
            self.assertTrue(async_upload.await_count >= 1)

    async def test_python_runtime_should_return_timeout(self):
        with tempfile.TemporaryDirectory(prefix="skill-script-timeout-") as temp_dir:
            skill_dir = self._create_python_skill(Path(temp_dir), """
                import time
                time.sleep(2)
                print("done")
            """)

            request = ScriptRunnerRequest(
                request_id="req-timeout",
                skill_name="demo-skill",
                skill_base_path=str(skill_dir),
                script_name="sleepy",
                script_path="scripts/summarize.py",
                runtime="python",
                timeout_seconds=1,
            )

            response = await run_script_request(request)

            self.assertFalse(response.success)
            self.assertEqual(-1, response.exit_code)
            self.assertEqual("脚本执行超时", response.summary)
            self.assertIn("timed out", response.stderr)

    async def test_should_keep_success_when_upload_generated_file_failed(self):
        with tempfile.TemporaryDirectory(prefix="skill-script-upload-warning-") as temp_dir:
            skill_dir = self._create_python_skill(Path(temp_dir), """
                from pathlib import Path

                output_dir = Path("output")
                output_dir.mkdir(parents=True, exist_ok=True)
                (output_dir / "summary.md").write_text("hello", encoding="utf-8")
                print("python runtime ok")
            """)

            request = ScriptRunnerRequest(
                request_id="req-upload-warning",
                skill_name="demo-skill",
                skill_base_path=str(skill_dir),
                script_name="summarize",
                script_path="scripts/summarize.py",
                runtime="python",
                timeout_seconds=5,
            )

            async_upload = AsyncMock(side_effect=RuntimeError("upload unavailable"))
            with patch("reactor_tool.tool.script_runner.upload_file_by_path", new=async_upload):
                response = await run_script_request(request)

            self.assertTrue(response.success)
            self.assertEqual(0, response.exit_code)
            self.assertEqual("脚本执行成功（产物上传失败）", response.summary)
            self.assertIn("upload unavailable", response.stderr)
            self.assertEqual(0, len(response.file_info))

    async def test_should_save_generated_files_to_local_directory_when_file_server_url_is_path(self):
        with tempfile.TemporaryDirectory(prefix="skill-script-local-output-") as temp_dir:
            skill_dir = self._create_python_skill(Path(temp_dir), """
                from pathlib import Path

                output_dir = Path("output")
                output_dir.mkdir(parents=True, exist_ok=True)
                (output_dir / "summary.md").write_text("local output", encoding="utf-8")
                print("python runtime ok")
            """)
            local_storage_dir = Path(temp_dir) / "skilloutput"

            request = ScriptRunnerRequest(
                request_id="req-local-output",
                skill_name="demo-skill",
                skill_base_path=str(skill_dir),
                script_name="summarize",
                script_path="scripts/summarize.py",
                runtime="python",
                timeout_seconds=5,
            )

            with patch.dict(os.environ, {"FILE_SERVER_URL": str(local_storage_dir)}, clear=False):
                response = await run_script_request(request)

            self.assertTrue(response.success)
            self.assertEqual(0, response.exit_code)
            self.assertEqual("脚本执行成功", response.summary)
            self.assertEqual(1, len(response.file_info))
            saved_file = Path(response.file_info[0].domain_url)
            self.assertTrue(saved_file.exists())
            self.assertEqual("summary.md", saved_file.name)
            self.assertIn("req-local-output", saved_file.as_posix())

    async def test_should_sanitize_request_id_when_saving_generated_files_to_local_directory(self):
        with tempfile.TemporaryDirectory(prefix="skill-script-local-output-") as temp_dir:
            skill_dir = self._create_python_skill(Path(temp_dir), """
                from pathlib import Path

                output_dir = Path("output")
                output_dir.mkdir(parents=True, exist_ok=True)
                (output_dir / "summary.md").write_text("local output", encoding="utf-8")
                print("python runtime ok")
            """)
            local_storage_dir = Path(temp_dir) / "skilloutput"

            request = ScriptRunnerRequest(
                request_id="reactorsession-1776000433417-1425:1776000435350-6356",
                skill_name="demo-skill",
                skill_base_path=str(skill_dir),
                script_name="summarize",
                script_path="scripts/summarize.py",
                runtime="python",
                timeout_seconds=5,
            )

            with patch.dict(os.environ, {"FILE_SERVER_URL": str(local_storage_dir)}, clear=False):
                response = await run_script_request(request)

            self.assertTrue(response.success)
            self.assertEqual(0, response.exit_code)
            self.assertEqual(1, len(response.file_info))
            saved_file = Path(response.file_info[0].domain_url)
            self.assertTrue(saved_file.exists())
            self.assertEqual("summary.md", saved_file.name)
            self.assertNotIn(":", saved_file.parent.name)
            self.assertIn("reactorsession-1776000433417-1425_1776000435350-6356", saved_file.parent.name)

    async def test_should_reject_path_escape(self):
        with tempfile.TemporaryDirectory(prefix="skill-script-escape-") as temp_dir:
            skill_dir = self._create_python_skill(Path(temp_dir), "print('ok')")
            request = ScriptRunnerRequest(
                request_id="req-escape",
                skill_name="demo-skill",
                skill_base_path=str(skill_dir),
                script_name="escape",
                script_path="../escape.py",
                runtime="python",
                timeout_seconds=5,
            )

            response = await run_script_request(request)

            self.assertFalse(response.success)
            self.assertEqual(-1, response.exit_code)
            self.assertEqual("脚本执行被拒绝", response.summary)
            self.assertIn("outside registered skill directory", response.stderr)

    def test_shell_runtime_should_build_command(self):
        script_path = Path("/tmp/demo.sh")
        with patch.dict(os.environ, {"SKILL_SHELL_BIN": "custom-shell"}, clear=False):
            command = build_command("shell", script_path, ["--flag"])

        self.assertEqual(["custom-shell", str(script_path), "--flag"], command)

    def _create_python_skill(self, root_dir: Path, script_content: str) -> Path:
        skill_dir = root_dir / "demo-skill"
        scripts_dir = skill_dir / "scripts"
        scripts_dir.mkdir(parents=True, exist_ok=True)
        (skill_dir / "SKILL.md").write_text(
            textwrap.dedent(
                """
                ---
                name: demo-skill
                description: demo
                ---

                # Demo Skill
                """
            ).strip() + "\n",
            encoding="utf-8",
        )
        (scripts_dir / "summarize.py").write_text(
            textwrap.dedent(script_content).strip() + "\n",
            encoding="utf-8",
        )
        return skill_dir

    async def _fake_upload(self, file_path: str, request_id: str):
        path = Path(file_path)
        return {
            "fileName": path.name,
            "ossUrl": f"https://oss.example/{request_id}/{path.name}",
            "domainUrl": f"https://domain.example/{request_id}/{path.name}",
            "downloadUrl": f"https://download.example/{request_id}/{path.name}",
            "fileSize": path.stat().st_size,
        }


if __name__ == "__main__":
    unittest.main()
