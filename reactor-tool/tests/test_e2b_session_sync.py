import shlex
from types import SimpleNamespace

import pytest

from reactor_tool.tool.e2b_session_sync import mkdir_remote, snapshot_remote_files


class _Commands:
    def __init__(self, stdout="", exit_code=0):
        self.stdout = stdout
        self.exit_code = exit_code
        self.calls = []

    def run(self, command, cwd=None, timeout=None):
        self.calls.append((command, cwd, timeout))
        return SimpleNamespace(
            exit_code=self.exit_code,
            stdout=self.stdout,
            stderr="",
        )


class _Sandbox:
    def __init__(self, commands):
        self.commands = commands
        self.run_code_calls = 0

    def run_code(self, *_args, **_kwargs):
        self.run_code_calls += 1
        raise AssertionError("workspace sync must not call run_code")


def test_snapshot_uses_python_cli_and_parses_file_signatures():
    commands = _Commands('__SESSION_SNAPSHOT__{"report.txt":[12,34]}\n')
    sandbox = _Sandbox(commands)

    result = snapshot_remote_files(sandbox, "/workspace/session")

    assert result == {"report.txt": (12, 34)}
    assert commands.calls[0][0].startswith("python -c ")
    assert "Path('/workspace/session')" in shlex.split(commands.calls[0][0])[2]
    assert sandbox.run_code_calls == 0


def test_snapshot_fails_without_commands_api():
    sandbox = SimpleNamespace(run_code=lambda *_args, **_kwargs: None)

    with pytest.raises(RuntimeError, match="commands.run"):
        snapshot_remote_files(sandbox, "/workspace/session")


def test_mkdir_requires_commands_api_without_run_code_fallback():
    sandbox = SimpleNamespace(
        run_code=lambda *_args, **_kwargs: pytest.fail("run_code")
    )

    with pytest.raises(RuntimeError, match="commands.run"):
        mkdir_remote(sandbox, "/workspace/session")


def test_mkdir_reports_shell_failure():
    sandbox = _Sandbox(_Commands(exit_code=1))

    with pytest.raises(RuntimeError, match="mkdir failed"):
        mkdir_remote(sandbox, "/workspace/session")
