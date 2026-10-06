"""Opt-in E2B diagnostic for the desktop Chromium startup path.

Run with ``RUN_E2B_DESKTOP_DIAGNOSTIC=1``.  This test intentionally uses the
same foreground command as production, with bash tracing enabled, so a timeout
report shows both the elapsed time and the last script stage reached.
"""

from __future__ import annotations

import json
import os
import sys
import tempfile
import time
import unittest
from pathlib import Path
from typing import Any

from dotenv import load_dotenv


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
_BROWSER_PATTERN = (
    "^/usr/bin/chromium --no-sandbox --no-first-run "
    "--disable-dev-shm-usage .*--user-data-dir=/home/user/.opencli/chromium-visible"
)
_BROWSER_HEALTH_CHECK = (
    "browser_pid=$(pgrep -u user -f "
    f"'{_BROWSER_PATTERN}' | head -n1); "
    'test -n "$browser_pid" && kill -0 "$browser_pid" && '
    "grep -z -q '^DISPLAY=:0$' \"/proc/$browser_pid/environ\""
)
_LOG_DUMP = """for file in \
/tmp/reactor-desktop-chromium.log \
/tmp/reactor-desktop-chromium-start.log \
/tmp/xfce4.log \
/tmp/novnc.log; do
  if [ -f "$file" ]; then
    echo "===== $file ====="
    tail -n 80 "$file"
  fi
done"""


class E2BDesktopStartupDiagnosticsTest(unittest.TestCase):
    """Measure the real E2B desktop startup path; skipped by default."""

    def test_foreground_launcher_timeout_phase(self) -> None:
        load_dotenv(ROOT / ".env")
        if os.getenv("RUN_E2B_DESKTOP_DIAGNOSTIC") != "1":
            self.skipTest("set RUN_E2B_DESKTOP_DIAGNOSTIC=1 to run against E2B")
        if not (os.getenv("E2B_API_KEY") or "").strip():
            self.skipTest("E2B_API_KEY is not configured")

        from e2b_code_interpreter import Sandbox

        template = (
            os.getenv("E2B_TEMPLATE") or "reactor-code-playwright-desktop"
        ).strip()
        sandbox_timeout = int(os.getenv("E2B_DIAGNOSTIC_SANDBOX_TIMEOUT", "300"))
        launcher_timeout = int(os.getenv("E2B_DIAGNOSTIC_LAUNCHER_TIMEOUT", "120"))
        records: list[dict[str, Any]] = []
        sandbox = None

        create_started = time.monotonic()
        try:
            sandbox = Sandbox.create(template=template, timeout=sandbox_timeout)
            records.append(
                {
                    "phase": "Sandbox.create",
                    "status": "ok",
                    "duration_sec": round(time.monotonic() - create_started, 3),
                    "sandbox_id": str(getattr(sandbox, "sandbox_id", "")),
                }
            )
            self._install_current_desktop_start_command(sandbox, records)
            self._record_command(
                sandbox,
                records,
                "display",
                "DISPLAY=:0 xdpyinfo -display :0 >/dev/null",
            )
            self._record_command(
                sandbox,
                records,
                "novnc",
                "curl -fsS --max-time 2 http://127.0.0.1:6080/vnc.html >/dev/null",
            )
            self._record_command(
                sandbox,
                records,
                "browser_before_launcher",
                _BROWSER_HEALTH_CHECK,
                allow_nonzero=True,
            )

            if os.getenv("E2B_DIAGNOSTIC_FORCE_COLD") == "1":
                self._record_command(
                    sandbox,
                    records,
                    "reset_desktop_runtime",
                    "pkill -u user -f "
                    f"'{_BROWSER_PATTERN}' || true; "
                    "pkill -x novnc_proxy || true; "
                    "pkill -x x11vnc || true; "
                    "pkill -x startxfce4 || true; "
                    "pkill -x xfce4-session || true; "
                    "pkill -x xfwm4 || true; "
                    "pkill -x xfce4-panel || true; "
                    "pkill -x xfdesktop || true; "
                    "pkill -x xfsettingsd || true; "
                    "pkill -x xfce4-notifyd || true; "
                    "pkill -x Xvfb || true; "
                    "rm -f /tmp/reactor-desktop-chromium.lock",
                    timeout=10,
                    user="root",
                )
                self._record_command(
                    sandbox,
                    records,
                    "display_reset_assertion",
                    "if DISPLAY=:0 xdpyinfo -display :0 >/dev/null 2>&1; then "
                    "echo display_still_running >&2; exit 1; "
                    "else echo display_stopped; fi",
                )
                self._record_command(
                    sandbox,
                    records,
                    "browser_reset_assertion",
                    f"if {_BROWSER_HEALTH_CHECK}; then "
                    "echo browser_still_ready >&2; exit 1; "
                    "else echo browser_stopped; fi",
                )
            self._record_manager_acquire(sandbox, records)

            # bash -x preserves production behavior while exposing the last
            # command reached before the E2B bidi stream stops responding.
            self._record_command(
                sandbox,
                records,
                "foreground_launcher_bash_x",
                "sudo -n -u user -H env DISPLAY=:0 "
                "ENSURE_DESKTOP_CHROMIUM_LOCKED=1 "
                "bash -x /usr/local/bin/ensure_desktop_chromium.sh",
                timeout=launcher_timeout,
                user="root",
            )
            self._record_command(
                sandbox,
                records,
                "browser_after_launcher",
                _BROWSER_HEALTH_CHECK,
            )
            self._record_command(
                sandbox,
                records,
                "startup_logs",
                _LOG_DUMP,
                timeout=10,
            )
        except Exception as exc:
            records.append(
                {
                    "phase": "test",
                    "status": "exception",
                    "duration_sec": round(time.monotonic() - create_started, 3),
                    "error_type": type(exc).__name__,
                    "error": str(exc),
                }
            )
            print(json.dumps(records, ensure_ascii=True, indent=2))
            raise
        finally:
            if sandbox is not None:
                try:
                    sandbox.kill()
                except Exception as exc:
                    records.append(
                        {
                            "phase": "sandbox.kill",
                            "status": "exception",
                            "error_type": type(exc).__name__,
                            "error": str(exc),
                        }
                    )

        print(json.dumps(records, ensure_ascii=True, indent=2))
        failed = [
            item
            for item in records
            if item.get("status") not in {"ok", "not_ready", "skipped"}
        ]
        if failed:
            self.fail(
                "E2B desktop startup diagnostic failed:\n"
                + json.dumps(records, ensure_ascii=True, indent=2)
            )

    @staticmethod
    def _install_current_desktop_start_command(
        sandbox: Any, records: list[dict[str, Any]]
    ) -> None:
        from reactor_tool.tool.e2b_file_upload import write_e2b_files

        source = ROOT / "scripts" / "e2b_template" / "desktop_start_command.sh"
        staging = "/tmp/reactor-diagnostic-desktop-start-command.sh"
        write_e2b_files(
            sandbox.files,
            [{"path": staging, "data": source.read_bytes()}],
            label="e2b_desktop_startup_diagnostic",
        )
        started = time.monotonic()
        result = sandbox.commands.run(
            "install -o root -g root -m 0755 "
            f"{staging} /start_command.sh && rm -f {staging}",
            user="root",
            timeout=10,
        )
        if getattr(result, "exit_code", 1) != 0 or getattr(result, "error", None):
            raise RuntimeError(
                str(getattr(result, "stderr", "") or "")
                or str(getattr(result, "error", "") or "")
                or "failed to install diagnostic desktop start command"
            )
        records.append(
            {
                "phase": "install_current_desktop_start_command",
                "status": "ok",
                "duration_sec": round(time.monotonic() - started, 3),
            }
        )

    @staticmethod
    def _record_manager_acquire(sandbox: Any, records: list[dict[str, Any]]) -> None:
        from reactor_tool.tool.user_sandbox_manager import UserSandboxManager

        started = time.monotonic()
        manager = None
        command_calls: list[dict[str, Any]] = []

        class TracedCommands:
            def __init__(self, delegate: Any):
                self._delegate = delegate

            def run(self, command: str, *args: Any, **kwargs: Any) -> Any:
                call_started = time.monotonic()
                try:
                    result = self._delegate.run(command, *args, **kwargs)
                    command_calls.append(
                        {
                            "command": str(command),
                            "timeout": kwargs.get("timeout"),
                            "user": kwargs.get("user"),
                            "status": "ok",
                            "duration_sec": round(time.monotonic() - call_started, 3),
                            "exit_code": getattr(result, "exit_code", None),
                        }
                    )
                    return result
                except Exception as exc:
                    command_calls.append(
                        {
                            "command": str(command),
                            "timeout": kwargs.get("timeout"),
                            "user": kwargs.get("user"),
                            "status": "exception",
                            "duration_sec": round(time.monotonic() - call_started, 3),
                            "error_type": type(exc).__name__,
                            "error": str(exc),
                        }
                    )
                    raise

        class TracedSandbox:
            def __init__(self, delegate: Any):
                self._delegate = delegate
                self.commands = TracedCommands(delegate.commands)

            def __getattr__(self, name: str) -> Any:
                return getattr(self._delegate, name)

        traced_sandbox = TracedSandbox(sandbox)
        try:
            with tempfile.TemporaryDirectory(
                prefix="e2b-desktop-diagnostic-"
            ) as directory:
                manager = UserSandboxManager(
                    db_path=str(Path(directory) / "sandbox.sqlite"),
                    create_fn=lambda *_args, **_kwargs: traced_sandbox,
                    connect_fn=lambda *_args, **_kwargs: traced_sandbox,
                    start_reaper=False,
                )
                try:
                    lease = manager.acquire("diagnostic-owner", timeout_sec=120)
                    manager.release(lease.owner_key)
                finally:
                    manager.shutdown()
                    manager = None
            stream_timeout = any(
                item.get("error_type") == "TimeoutException" for item in command_calls
            )
            records.append(
                {
                    "phase": "manager.acquire_after_cold_start",
                    "status": "stream_timeout" if stream_timeout else "ok",
                    "duration_sec": round(time.monotonic() - started, 3),
                    "stream_timeout": stream_timeout,
                    "commands": command_calls,
                }
            )
        except Exception as exc:
            records.append(
                {
                    "phase": "manager.acquire_after_cold_start",
                    "status": "exception",
                    "duration_sec": round(time.monotonic() - started, 3),
                    "error_type": type(exc).__name__,
                    "error": str(exc),
                    "commands": command_calls,
                }
            )
        finally:
            if manager is not None:
                manager.shutdown()

    @staticmethod
    def _record_command(
        sandbox: Any,
        records: list[dict[str, Any]],
        phase: str,
        command: str,
        *,
        timeout: int = 10,
        user: str | None = None,
        allow_nonzero: bool = False,
    ) -> None:
        started = time.monotonic()
        try:
            result = sandbox.commands.run(command, timeout=timeout, user=user)
            exit_code = getattr(result, "exit_code", 1)
            records.append(
                {
                    "phase": phase,
                    "status": (
                        "ok"
                        if exit_code == 0
                        else "not_ready"
                        if allow_nonzero
                        else "failed"
                    ),
                    "duration_sec": round(time.monotonic() - started, 3),
                    "exit_code": exit_code,
                    "stdout": str(getattr(result, "stdout", "") or "")[-4000:],
                    "stderr": str(getattr(result, "stderr", "") or "")[-4000:],
                    "error": str(getattr(result, "error", "") or ""),
                }
            )
        except Exception as exc:
            records.append(
                {
                    "phase": phase,
                    "status": "not_ready" if allow_nonzero else "exception",
                    "duration_sec": round(time.monotonic() - started, 3),
                    "error_type": type(exc).__name__,
                    "error": str(exc),
                }
            )


if __name__ == "__main__":
    unittest.main()
