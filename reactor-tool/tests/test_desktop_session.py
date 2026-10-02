# -*- coding: utf-8 -*-
from types import SimpleNamespace
from unittest.mock import patch

from reactor_tool.api.sandbox_proxy import proxy_path_name
from reactor_tool.model.protocal import (
    DesktopSessionCloseRequest,
    DesktopSessionOpenRequest,
)
from reactor_tool.tool.desktop_session import (
    DesktopSessionError,
    close_desktop_session,
    open_desktop_session,
)
from reactor_tool.tool.user_sandbox_manager import (
    UserSandboxManager,
    reset_user_sandbox_manager,
)
from tests.test_user_sandbox_manager import _FakeSandbox, _hold_row, _make_manager


class _DesktopSandbox(_FakeSandbox):
    def __init__(
        self,
        *,
        desktop_ready=True,
        startup_succeeds=True,
        startup_raises_after_start=False,
    ):
        super().__init__()
        self.commands = _DesktopCommands(
            desktop_ready, startup_succeeds, startup_raises_after_start
        )
        self.paused = False

    def get_host(self, port):
        self.host_calls = getattr(self, "host_calls", [])
        self.host_calls.append(port)
        return f"{port}-sandbox-1.e2b.app"

    def pause(self, keep_memory=True):
        self.paused = True
        return super().pause(keep_memory)


class _DesktopCommands:
    def __init__(self, desktop_ready, startup_succeeds, startup_raises_after_start):
        self.desktop_ready = desktop_ready
        self.startup_succeeds = startup_succeeds
        self.startup_raises_after_start = startup_raises_after_start
        self.calls = []
        self.start_calls = 0

    def run(self, command, cwd=None, timeout=None, user=None, envs=None):
        self.calls.append((command, cwd, timeout, user, envs))
        if "/start_command.sh" in command:
            self.start_calls += 1
            self.desktop_ready = (
                self.startup_succeeds or self.startup_raises_after_start
            )
            if self.startup_raises_after_start:
                raise TimeoutError("E2B command stream timed out")
            return SimpleNamespace(
                exit_code=0 if self.startup_succeeds else 1,
                stdout="",
                stderr="" if self.startup_succeeds else "desktop startup failed",
            )
        if "http://127.0.0.1:6080/vnc.html" in command:
            return SimpleNamespace(
                exit_code=0 if self.desktop_ready else 7,
                stdout="",
                stderr="",
            )
        return SimpleNamespace(exit_code=0, stdout="", stderr="")


def _make_desktop_manager(db_path, sandbox, clock, connect_calls):
    def connect(sandbox_id, **kwargs):
        connect_calls.append(sandbox_id)
        sandbox.paused = False
        sandbox.commands.desktop_ready = False
        return sandbox

    return UserSandboxManager(
        db_path=str(db_path),
        create_fn=lambda *args, **kwargs: sandbox,
        connect_fn=connect,
        pause_fn=lambda value, keep_memory: value.pause(keep_memory),
        debounce_sec=3,
        idle_sec=1200,
        hold_sec=600,
        time_fn=lambda: clock[0],
        start_reaper=False,
    )


def test_proxy_path_name_nested_desktop_routes():
    assert proxy_path_name("/v1/tool/bash") == "bash"
    assert proxy_path_name("/v1/tool/desktop_session/open") == "desktop_session/open"
    assert proxy_path_name("/v1/tool/desktop_session/close") == "desktop_session/close"


def test_open_requires_e2b_backend(tmp_path):
    sandbox = _DesktopSandbox()
    manager = _make_manager(tmp_path / "sandbox.db", sandbox, [100.0])
    reset_user_sandbox_manager(manager)
    try:
        with patch(
            "reactor_tool.tool.desktop_session.get_sandbox_backend",
            return_value="local",
        ):
            try:
                open_desktop_session(
                    DesktopSessionOpenRequest.model_validate(
                        {
                            "requestId": "r1",
                            "ownerKey": "user:one",
                            "sessionId": "s1",
                        }
                    )
                )
                raise AssertionError("expected DesktopSessionError")
            except DesktopSessionError as exc:
                assert exc.status_code == 400
        assert sandbox.pause_calls == []
    finally:
        reset_user_sandbox_manager()
        manager.shutdown()


def test_open_sets_hold_returns_url_and_release_does_not_pause(tmp_path):
    db_path = tmp_path / "sandbox.db"
    clock = [100.0]
    sandbox = _DesktopSandbox()
    manager = _make_manager(db_path, sandbox, clock)
    reset_user_sandbox_manager(manager)
    try:
        with patch(
            "reactor_tool.tool.desktop_session.get_sandbox_backend",
            return_value="e2b",
        ):
            result = open_desktop_session(
                DesktopSessionOpenRequest.model_validate(
                    {
                        "requestId": "r1",
                        "ownerKey": "user:one",
                        "sessionId": "s1",
                        "ttlSeconds": 600,
                    }
                )
            )
        assert result.port == 6080
        assert result.hold_until == 700.0
        assert result.url == (
            "https://6080-sandbox-1.e2b.app/vnc.html?autoconnect=1&resize=scale"
        )
        assert sandbox.host_calls == [6080]
        health_calls = [
            call[0]
            for call in sandbox.commands.calls
            if call[0].startswith("curl -fsS")
        ]
        assert health_calls == [
            "curl -fsS --max-time 1 http://127.0.0.1:6080/vnc.html >/dev/null"
        ]
        assert sandbox.timeout_calls == [720]
        row = _hold_row(db_path)
        assert row[0] == "user_operating"
        assert row[1] == 700.0
        assert row[3] == 0
        assert sandbox.commands.start_calls == 0

        clock[0] += 3
        manager.process_due_pauses()
        assert sandbox.pause_calls == []
    finally:
        reset_user_sandbox_manager()
        manager.shutdown()


def test_open_after_bash_pause_restarts_desktop_before_returning_url(tmp_path):
    db_path = tmp_path / "sandbox.db"
    clock = [100.0]
    sandbox = _DesktopSandbox()
    connect_calls = []
    manager = _make_desktop_manager(db_path, sandbox, clock, connect_calls)
    reset_user_sandbox_manager(manager)
    try:
        lease = manager.acquire("user:one")
        manager.release("user:one")
        clock[0] += 3
        manager.process_due_pauses()
        assert sandbox.pause_calls == [True]

        with patch(
            "reactor_tool.tool.desktop_session.get_sandbox_backend",
            return_value="e2b",
        ):
            result = open_desktop_session(
                DesktopSessionOpenRequest.model_validate(
                    {
                        "requestId": "r2",
                        "ownerKey": "user:one",
                        "sessionId": "s1",
                        "ttlSeconds": 600,
                    }
                )
            )

        assert connect_calls == [lease.sandbox_id]
        assert sandbox.commands.start_calls == 1
        assert sandbox.commands.desktop_ready
        assert result.url == (
            "https://6080-sandbox-1.e2b.app/vnc.html?autoconnect=1&resize=scale"
        )
        assert _hold_row(db_path)[0:2] == ("user_operating", 703.0)
    finally:
        reset_user_sandbox_manager()
        manager.shutdown()


def test_open_accepts_desktop_ready_after_start_command_times_out(tmp_path):
    db_path = tmp_path / "sandbox.db"
    clock = [100.0]
    sandbox = _DesktopSandbox(
        desktop_ready=False,
        startup_raises_after_start=True,
    )
    manager = _make_manager(db_path, sandbox, clock)
    reset_user_sandbox_manager(manager)
    try:
        with patch(
            "reactor_tool.tool.desktop_session.get_sandbox_backend",
            return_value="e2b",
        ):
            result = open_desktop_session(
                DesktopSessionOpenRequest.model_validate(
                    {"requestId": "r-timeout", "ownerKey": "user:one"}
                )
            )

        assert result.port == 6080
        assert result.hold_until == 700.0
        assert sandbox.commands.start_calls == 1
        assert sandbox.commands.desktop_ready
        assert sandbox.host_calls == [6080]
    finally:
        reset_user_sandbox_manager()
        manager.shutdown()


def test_open_does_not_return_url_when_desktop_cannot_start(tmp_path):
    db_path = tmp_path / "sandbox.db"
    clock = [100.0]
    sandbox = _DesktopSandbox(desktop_ready=False, startup_succeeds=False)
    manager = _make_manager(db_path, sandbox, clock)
    reset_user_sandbox_manager(manager)
    try:
        with patch(
            "reactor_tool.tool.desktop_session.get_sandbox_backend",
            return_value="e2b",
        ):
            try:
                open_desktop_session(
                    DesktopSessionOpenRequest.model_validate(
                        {"requestId": "r3", "ownerKey": "user:one"}
                    )
                )
                raise AssertionError("expected DesktopSessionError")
            except DesktopSessionError as exc:
                assert exc.status_code == 503
                assert "desktop startup failed" in exc.message

        assert sandbox.commands.start_calls == 1
        assert _hold_row(db_path)[0] == ""
    finally:
        reset_user_sandbox_manager()
        manager.shutdown()


def test_close_is_idempotent_and_restores_debounce(tmp_path):
    db_path = tmp_path / "sandbox.db"
    clock = [100.0]
    sandbox = _DesktopSandbox()
    manager = _make_manager(db_path, sandbox, clock)
    reset_user_sandbox_manager(manager)
    try:
        with patch(
            "reactor_tool.tool.desktop_session.get_sandbox_backend",
            return_value="e2b",
        ):
            open_desktop_session(
                DesktopSessionOpenRequest.model_validate(
                    {
                        "requestId": "r1",
                        "ownerKey": "user:one",
                    }
                )
            )
            first = close_desktop_session(
                DesktopSessionCloseRequest.model_validate(
                    {"requestId": "r1", "ownerKey": "user:one"}
                )
            )
            second = close_desktop_session(
                DesktopSessionCloseRequest.model_validate(
                    {"requestId": "r1", "ownerKey": "user:one"}
                )
            )
        assert first == {"ok": True}
        assert second == {"ok": True}
        assert _hold_row(db_path)[0] == ""

        clock[0] += 3
        manager.process_due_pauses()
        assert sandbox.pause_calls == [True]
    finally:
        reset_user_sandbox_manager()
        manager.shutdown()
