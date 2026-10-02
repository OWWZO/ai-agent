"""Verify lazy Jupyter startup, desktop Chromium, and both pause modes."""

from __future__ import annotations

import os
import sys
import tempfile
import time
from contextlib import ExitStack
from pathlib import Path

from dotenv import load_dotenv
from e2b_code_interpreter import Sandbox

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT))

from reactor_tool.tool.bash_sandbox import _exec_e2b  # noqa: E402
from reactor_tool.tool.sandbox_backend_config import connect_e2b_sandbox  # noqa: E402
from reactor_tool.tool.user_sandbox_manager import (  # noqa: E402
    UserSandboxManager,
    reset_user_sandbox_manager,
)


TEMPLATE_ALIAS = "reactor-code-playwright-desktop"
SMOKE_TIMEOUT_SECONDS = 1800


def _assert_command(sandbox, command: str, expected: str) -> str:
    result = sandbox.commands.run(command, timeout=60)
    stdout = str(result.stdout or "")
    if result.exit_code != 0 or expected not in stdout:
        raise RuntimeError(
            f"command check failed: exit={result.exit_code} "
            f"stdout={stdout!r} stderr={result.stderr!r}"
        )
    return stdout.strip()


def _assert_jupyter_inactive(sandbox) -> None:
    _assert_command(
        sandbox,
        "if systemctl is-active --quiet jupyter; then "
        "echo jupyter_active; exit 1; fi; "
        "! curl -fsS --max-time 1 http://127.0.0.1:49999/health >/dev/null && "
        "DISPLAY=:0 xdpyinfo -display :0 >/dev/null && "
        "curl -fsS --max-time 2 http://127.0.0.1:6080/vnc.html >/dev/null && "
        "echo jupyter_inactive desktop_ready",
        "jupyter_inactive desktop_ready",
    )


def _desktop_browser_check_command() -> str:
    return """set -e
browser_pid=""
window_id=""
for attempt in $(seq 1 30); do
  browser_pid="$(pgrep -u user -f '^/usr/bin/chromium --no-sandbox --no-first-run --disable-dev-shm-usage .*--user-data-dir=/home/user/.opencli/chromium-visible' | head -n1 || true)"
  if [ -n "$browser_pid" ]; then
    window_id="$(DISPLAY=:0 xdotool search --onlyvisible --pid "$browser_pid" | head -n1 || true)"
    if [ -n "$window_id" ]; then break; fi
  fi
  sleep 1
done
test -n "$browser_pid"
grep -z -q '^DISPLAY=:0$' "/proc/$browser_pid/environ"
test -n "$window_id"
printf '%s\\n' "$browser_pid" > /tmp/opencli-desktop-browser.pid
printf 'desktop_browser_pid=%s\\n' "$browser_pid"
echo desktop_browser_ready"""


def _opencli_browser_command() -> str:
    return """set -e
export DISPLAY=:0
opencli doctor >/tmp/opencli-doctor.log 2>&1 || true
profile_list=""
connected_count=0
for attempt in $(seq 1 20); do
  profile_list="$(opencli profile list 2>/dev/null || true)"
  connected_count="$(printf '%s\\n' "$profile_list" | awk '
    /^Connected Browser Bridge profiles/ { connected = 1; next }
    /^Disconnected saved profiles/ { exit }
    connected && NF { count++ }
    END { print count + 0 }
  ')"
  if [ "$connected_count" = 1 ]; then break; fi
  sleep 1
done
test "$connected_count" = 1
connected_profile="$(printf '%s\\n' "$profile_list" | awk '
  /^Connected Browser Bridge profiles/ { connected = 1; next }
  /^Disconnected saved profiles/ { exit }
  connected && NF { print $1; exit }
')"
opencli profile use "$connected_profile"
opencli doctor
browser_pid="$(pgrep -u user -f '^/usr/bin/chromium --no-sandbox --no-first-run --disable-dev-shm-usage .*--user-data-dir=/home/user/.opencli/chromium-visible' | head -n1)"
window_id="$(DISPLAY=:0 xdotool search --onlyvisible --pid "$browser_pid" | head -n1)"
DISPLAY=:0 xdotool windowactivate --sync "$window_id"
opencli browser e2b-smoke bind
opencli browser e2b-smoke open https://example.com
title_output="$(opencli browser e2b-smoke get title)"
printf '%s\\n' "$title_output" | grep -F 'Example Domain' >/dev/null

window_id=""
for attempt in $(seq 1 10); do
  window_id="$(DISPLAY=:0 xdotool search --onlyvisible --name 'Example Domain' 2>/dev/null | head -n1 || true)"
  if [ -n "$window_id" ]; then
    break
  fi
  sleep 1
done
test -n "$window_id"

browser_pid="$(pgrep -u user -f '^/usr/bin/chromium --no-sandbox --no-first-run --disable-dev-shm-usage .*--user-data-dir=/home/user/.opencli/chromium-visible' | head -n1)"
expected_pid="$(cat /tmp/opencli-desktop-browser.pid)"
test "$browser_pid" = "$expected_pid"
grep -z -q '^DISPLAY=:0$' "/proc/$browser_pid/environ"
echo opencli_visible_window=passed
echo opencli_browser=ready"""


def main() -> int:
    load_dotenv(ROOT / ".env")
    os.environ["E2B_TIMEOUT_SEC"] = str(SMOKE_TIMEOUT_SECONDS)
    template_alias = (os.getenv("E2B_TEMPLATE") or TEMPLATE_ALIAS).strip()
    with ExitStack() as stack:
        sandbox = Sandbox.create(
            template=template_alias,
            timeout=SMOKE_TIMEOUT_SECONDS,
        )
        sandbox_ref = [sandbox]
        stack.callback(lambda: sandbox_ref[0].kill())
        workspace = Path(
            stack.enter_context(tempfile.TemporaryDirectory(prefix="e2b-lazy-smoke-"))
        )

        _assert_jupyter_inactive(sandbox)
        print(f"sandbox={sandbox.sandbox_id}")

        manager = UserSandboxManager(
            db_path=str(workspace / "sandbox.sqlite"),
            create_fn=lambda *_args, **_kwargs: sandbox_ref[0],
            connect_fn=lambda *_args, **_kwargs: sandbox_ref[0],
            idle_sec=1,
            start_reaper=False,
        )
        reset_user_sandbox_manager(manager)
        stack.callback(reset_user_sandbox_manager)
        owner = f"smoke:{sandbox.sandbox_id}"
        session_id = "desktop-browser-smoke"

        bash_result = _exec_e2b(
            session_id,
            "printf 'bash_before_code' > bash-before-code.txt",
            workspace,
            None,
            set(),
            30,
            64000,
            owner_key=owner,
        )
        if bash_result[0] != 0:
            raise RuntimeError(f"Initial Bash failed: {bash_result[2]}")
        if (workspace / "bash-before-code.txt").read_text() != "bash_before_code":
            raise RuntimeError("Bash output was not downloaded through Files API")
        _assert_jupyter_inactive(sandbox)
        print("bash_before_code=passed jupyter=inactive desktop=ready")

        _assert_command(
            sandbox,
            "test -f /opt/opencli/extension/manifest.json && "
            "opencli --version && echo opencli_installed",
            "opencli_installed",
        )
        _assert_command(
            sandbox,
            'test "$(readlink -f /usr/bin/www-browser)" = '
            "/usr/local/bin/reactor-opencli-browser && "
            'test "$(xdg-mime query default x-scheme-handler/https)" = '
            "reactor-opencli-browser.desktop && echo desktop_browser_uses_opencli",
            "desktop_browser_uses_opencli",
        )
        _assert_command(
            sandbox,
            "test -x /usr/bin/www-browser && "
            "test -x /usr/bin/x-www-browser && echo desktop_browser_wrapper_ready",
            "desktop_browser_wrapper_ready",
        )
        desktop_browser = _assert_command(
            sandbox,
            _desktop_browser_check_command(),
            "desktop_browser_ready",
        )
        initial_browser_pid = next(
            line.split("=", 1)[1]
            for line in desktop_browser.splitlines()
            if line.startswith("desktop_browser_pid=")
        )
        print(f"desktop_browser={desktop_browser}")
        opencli_result = _exec_e2b(
            session_id,
            _opencli_browser_command(),
            workspace,
            None,
            set(),
            60,
            64000,
            owner_key=owner,
        )
        if (
            opencli_result[0] != 0
            or "opencli_visible_window=passed" not in opencli_result[1]
            or "opencli_browser=ready" not in opencli_result[1]
        ):
            raise RuntimeError(
                "OpenCLI browser check failed: "
                f"stdout={opencli_result[1]!r} stderr={opencli_result[2]!r}"
            )
        output = f"opencli={opencli_result[1].strip()}"
        print(output.encode("ascii", "backslashreplace").decode("ascii"))

        time.sleep(3.1)
        manager.process_due_pauses()
        sandbox_ref[0] = connect_e2b_sandbox(
            sandbox_ref[0].sandbox_id,
            timeout_sec=300,
        )
        sandbox = sandbox_ref[0]

        _assert_jupyter_inactive(sandbox)
        resumed_bash = _exec_e2b(
            session_id,
            "printf 'bash_after_resume' > bash-after-resume.txt",
            workspace,
            None,
            set(),
            30,
            64000,
            owner_key=owner,
        )
        if resumed_bash[0] != 0:
            raise RuntimeError(f"Resumed Bash failed: {resumed_bash[2]}")
        if (workspace / "bash-after-resume.txt").read_text() != "bash_after_resume":
            raise RuntimeError("Resumed Bash output was not synced")
        _assert_jupyter_inactive(sandbox)
        resumed_browser = _assert_command(
            sandbox,
            _desktop_browser_check_command(),
            "desktop_browser_ready",
        )
        resumed_browser_pid = next(
            line.split("=", 1)[1]
            for line in resumed_browser.splitlines()
            if line.startswith("desktop_browser_pid=")
        )
        if resumed_browser_pid != initial_browser_pid:
            raise RuntimeError(
                "Full-memory resume replaced the Chromium process: "
                f"before={initial_browser_pid} after={resumed_browser_pid}"
            )
        resumed_opencli = _exec_e2b(
            session_id,
            "opencli browser e2b-smoke get url && echo opencli_after_resume=passed",
            workspace,
            None,
            set(),
            60,
            64000,
            owner_key=owner,
        )
        if (
            resumed_opencli[0] != 0
            or "opencli_after_resume=passed" not in resumed_opencli[1]
        ):
            raise RuntimeError(f"Resumed OpenCLI failed: {resumed_opencli[2]}")

        time.sleep(3.1)
        manager.process_due_pauses()
        cold_pause_sandbox = connect_e2b_sandbox(
            sandbox_ref[0].sandbox_id,
            timeout_sec=SMOKE_TIMEOUT_SECONDS,
        )
        cold_pause_sandbox.commands.run("sync", timeout=30)
        if not cold_pause_sandbox.pause(keep_memory=False):
            raise RuntimeError("E2B filesystem-only pause failed")
        sandbox_ref[0] = connect_e2b_sandbox(
            sandbox_ref[0].sandbox_id,
            timeout_sec=SMOKE_TIMEOUT_SECONDS,
        )
        sandbox = sandbox_ref[0]
        filesystem_resumed = _exec_e2b(
            session_id,
            "printf 'bash_after_filesystem_resume' > bash-after-filesystem-resume.txt",
            workspace,
            None,
            set(),
            30,
            64000,
            owner_key=owner,
        )
        if filesystem_resumed[0] != 0:
            raise RuntimeError(
                f"Filesystem-only resume failed: {filesystem_resumed[2]}"
            )
        if (
            workspace / "bash-after-filesystem-resume.txt"
        ).read_text() != "bash_after_filesystem_resume":
            raise RuntimeError("Filesystem-only resume output was not synced")
        filesystem_browser = _assert_command(
            sandbox,
            _desktop_browser_check_command(),
            "desktop_browser_ready",
        )
        filesystem_browser_pid = next(
            line.split("=", 1)[1]
            for line in filesystem_browser.splitlines()
            if line.startswith("desktop_browser_pid=")
        )
        if filesystem_browser_pid == initial_browser_pid:
            raise RuntimeError(
                "Filesystem-only resume kept the pre-pause Chromium process: "
                f"pid={filesystem_browser_pid}"
            )
        print(f"filesystem_resume_browser={filesystem_browser}")
        filesystem_opencli = _exec_e2b(
            session_id,
            _opencli_browser_command(),
            workspace,
            None,
            set(),
            60,
            64000,
            owner_key=owner,
        )
        if (
            filesystem_opencli[0] != 0
            or "opencli_visible_window=passed" not in filesystem_opencli[1]
            or "opencli_browser=ready" not in filesystem_opencli[1]
        ):
            raise RuntimeError(
                "Filesystem-only resume OpenCLI check failed: "
                f"stdout={filesystem_opencli[1]!r} stderr={filesystem_opencli[2]!r}"
            )

    print("smoke_test=passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
