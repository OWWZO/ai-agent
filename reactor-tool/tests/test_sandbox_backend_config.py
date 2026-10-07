# -*- coding: utf-8 -*-
import os
import unittest
from pathlib import Path

from reactor_tool.tool.sandbox_backend_config import (
    ensure_e2b_code_interpreter_ready,
    ensure_e2b_desktop_chromium_ready,
    get_e2b_fs_only_idle_sec,
    get_e2b_full_pause_debounce_sec,
    get_e2b_proxy,
    get_e2b_user_operating_hold_sec,
)


class GetE2BProxyTest(unittest.TestCase):
    def setUp(self):
        self._prev_e2b = os.environ.get("E2B_PROXY")
        self._prev_web_fetch = os.environ.get("PROXY")
        self._prev_debounce = os.environ.get("E2B_FULL_PAUSE_DEBOUNCE_SEC")
        self._prev_idle = os.environ.get("E2B_FS_ONLY_IDLE_SEC")
        self._prev_hold = os.environ.get("E2B_USER_OPERATING_HOLD_SEC")

    def tearDown(self):
        self._restore("E2B_PROXY", self._prev_e2b)
        self._restore("PROXY", self._prev_web_fetch)
        self._restore("E2B_FULL_PAUSE_DEBOUNCE_SEC", self._prev_debounce)
        self._restore("E2B_FS_ONLY_IDLE_SEC", self._prev_idle)
        self._restore("E2B_USER_OPERATING_HOLD_SEC", self._prev_hold)

    @staticmethod
    def _restore(key, value):
        if value is None:
            os.environ.pop(key, None)
        else:
            os.environ[key] = value

    def test_prefers_e2b_proxy(self):
        os.environ["E2B_PROXY"] = " http://e2b-proxy:8080 "
        os.environ["PROXY"] = "http://fallback:8080"
        self.assertEqual("http://e2b-proxy:8080", get_e2b_proxy())

    def test_falls_back_to_web_fetch_proxy(self):
        os.environ.pop("E2B_PROXY", None)
        os.environ["PROXY"] = "http://fallback:8080"
        self.assertEqual("http://fallback:8080", get_e2b_proxy())

    def test_explicit_empty_e2b_proxy_disables_fallback(self):
        os.environ["E2B_PROXY"] = ""
        os.environ["PROXY"] = "http://fallback:8080"
        self.assertIsNone(get_e2b_proxy())

    def test_explicit_off_e2b_proxy_disables_fallback(self):
        os.environ["E2B_PROXY"] = "off"
        os.environ["PROXY"] = "http://fallback:8080"
        self.assertIsNone(get_e2b_proxy())

    def test_pause_and_idle_defaults(self):
        os.environ.pop("E2B_FULL_PAUSE_DEBOUNCE_SEC", None)
        os.environ.pop("E2B_FS_ONLY_IDLE_SEC", None)
        os.environ.pop("E2B_USER_OPERATING_HOLD_SEC", None)
        self.assertEqual(3.0, get_e2b_full_pause_debounce_sec())
        self.assertEqual(1200.0, get_e2b_fs_only_idle_sec())
        self.assertEqual(600.0, get_e2b_user_operating_hold_sec())

    def test_pause_and_idle_environment_overrides(self):
        os.environ["E2B_FULL_PAUSE_DEBOUNCE_SEC"] = "7.5"
        os.environ["E2B_FS_ONLY_IDLE_SEC"] = "45"
        os.environ["E2B_USER_OPERATING_HOLD_SEC"] = "90"
        self.assertEqual(7.5, get_e2b_full_pause_debounce_sec())
        self.assertEqual(45.0, get_e2b_fs_only_idle_sec())
        self.assertEqual(90.0, get_e2b_user_operating_hold_sec())

    def test_ensure_code_interpreter_returns_when_jupyter_is_healthy(self):
        calls = []

        class Commands:
            def run(self, command, timeout=None, user=None):
                calls.append((command, timeout, user))
                return type(
                    "Result", (), {"exit_code": 0, "stdout": "", "stderr": ""}
                )()

        sandbox = type("Sandbox", (), {"commands": Commands()})()
        ensure_e2b_code_interpreter_ready(sandbox)

        self.assertEqual(1, len(calls))
        self.assertIn("127.0.0.1:49999/health", calls[0][0])
        self.assertNotIn("systemctl start jupyter", calls[0][0])
        self.assertEqual(5, calls[0][1])

    def test_ensure_code_interpreter_starts_jupyter_when_inactive(self):
        calls = []

        class Commands:
            def run(self, command, timeout=None, user=None):
                calls.append((command, timeout, user))
                code = 7 if len(calls) == 1 else 0
                return type(
                    "Result", (), {"exit_code": code, "stdout": "", "stderr": ""}
                )()

        sandbox = type("Sandbox", (), {"commands": Commands()})()
        ensure_e2b_code_interpreter_ready(sandbox)

        self.assertEqual(2, len(calls))
        self.assertIn("127.0.0.1:49999/health", calls[0][0])
        self.assertIn("systemctl daemon-reload", calls[1][0])
        self.assertIn("systemctl start jupyter", calls[1][0])
        self.assertGreaterEqual(calls[1][1], 20)

    def test_ensure_code_interpreter_rejects_unhealthy_jupyter(self):
        calls = []

        class Commands:
            def run(self, command, timeout=None):
                calls.append(command)
                return type(
                    "Result",
                    (),
                    {"exit_code": 1, "stdout": "", "stderr": "jupyter did not start"},
                )()

        sandbox = type("Sandbox", (), {"commands": Commands()})()
        with self.assertRaisesRegex(RuntimeError, "(?i)jupyter"):
            ensure_e2b_code_interpreter_ready(sandbox)
        self.assertEqual(2, len(calls))

    def test_ensure_code_interpreter_requires_command_channel(self):
        sandbox = type("Sandbox", (), {"run_code": lambda *_args, **_kwargs: None})()
        with self.assertRaisesRegex(RuntimeError, "commands.run"):
            ensure_e2b_code_interpreter_ready(sandbox)

    def test_ensure_desktop_chromium_runs_independent_launcher(self):
        calls = []

        class Commands:
            def run(self, command, timeout=None, user=None):
                calls.append((command, timeout, user))
                return type(
                    "Result", (), {"exit_code": 0, "stdout": "", "stderr": ""}
                )()

        sandbox = type("Sandbox", (), {"commands": Commands()})()
        ensure_e2b_desktop_chromium_ready(sandbox)

        self.assertEqual(
            calls,
            [("/usr/local/bin/ensure_desktop_chromium.sh", 120, "root")],
        )

    def test_ensure_desktop_chromium_bootstraps_missing_template_launcher(self):
        calls = []
        writes = []

        class Commands:
            def run(self, command, timeout=None, user=None):
                calls.append((command, timeout, user))
                if (
                    command == "/usr/local/bin/ensure_desktop_chromium.sh"
                    and len(calls) == 1
                ):
                    raise RuntimeError(
                        "/bin/bash: line 1: /usr/local/bin/ensure_desktop_chromium.sh: "
                        "No such file or directory"
                    )
                return type(
                    "Result",
                    (),
                    {
                        "exit_code": 1 if "chromium-visible" in command else 0,
                        "stdout": "",
                        "stderr": "",
                    },
                )()

        class Files:
            def write_files(self, files, request_timeout=None):
                writes.extend(files)

        sandbox = type("Sandbox", (), {"commands": Commands(), "files": Files()})()
        ensure_e2b_desktop_chromium_ready(sandbox)

        self.assertEqual(3, len(calls))
        self.assertEqual("/usr/local/bin/ensure_desktop_chromium.sh", calls[0][0])
        self.assertIn("install -o root -g root -m 0755", calls[1][0])
        self.assertEqual("/usr/local/bin/ensure_desktop_chromium.sh", calls[2][0])
        self.assertEqual("/tmp/reactor-ensure-desktop-chromium.sh", writes[0]["path"])
        self.assertIn(b"nohup /usr/bin/chromium", writes[0]["data"])

    def test_ensure_desktop_chromium_reports_launcher_failure(self):
        class Commands:
            def run(self, command, timeout=None, user=None):
                return type(
                    "Result",
                    (),
                    {
                        "exit_code": 1,
                        "stdout": "",
                        "stderr": "chromium did not start",
                    },
                )()

        sandbox = type("Sandbox", (), {"commands": Commands()})()
        with self.assertRaisesRegex(RuntimeError, "chromium did not start"):
            ensure_e2b_desktop_chromium_ready(sandbox)

    def test_ensure_desktop_chromium_accepts_ready_browser_after_stream_timeout(self):
        calls = []

        class Commands:
            def run(self, command, timeout=None, user=None):
                calls.append((command, timeout, user))
                if command == "/usr/local/bin/ensure_desktop_chromium.sh":
                    raise TimeoutError("command stream timed out after browser start")
                return type(
                    "Result", (), {"exit_code": 0, "stdout": "", "stderr": ""}
                )()

        sandbox = type("Sandbox", (), {"commands": Commands()})()
        ensure_e2b_desktop_chromium_ready(sandbox)

        self.assertEqual(2, len(calls))
        self.assertIn("chromium-visible", calls[1][0])
        self.assertEqual(5, calls[1][1])

    def test_desktop_launcher_does_not_depend_on_opencli_cli(self):
        template_dir = Path(__file__).resolve().parents[1] / "scripts" / "e2b_template"
        launcher = (template_dir / "ensure_desktop_chromium.sh").read_text()
        desktop_start = (template_dir / "desktop_start_command.sh").read_text()
        template = (template_dir / "template.py").read_text()

        self.assertIn("nohup /usr/bin/chromium", launcher)
        self.assertIn('--user-data-dir="$profile"', launcher)
        self.assertIn('--disable-extensions-except="$extension"', launcher)
        self.assertIn('--load-extension="$extension"', launcher)
        self.assertIn("flock --close", launcher)
        self.assertIn("about:blank", launcher)
        self.assertNotIn("opencli_browser.sh", launcher)
        self.assertNotRegex(launcher, r"(^|[^A-Za-z])opencli(\s|$)")
        self.assertIn("/usr/local/bin/ensure_desktop_chromium.sh", desktop_start)
        self.assertIn('"ensure_desktop_chromium.sh"', template)
        self.assertIn("--user-data-dir=/home/user/.opencli/chromium-visible'", template)


if __name__ == "__main__":
    unittest.main()
