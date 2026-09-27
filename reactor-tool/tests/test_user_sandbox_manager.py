# -*- coding: utf-8 -*-
import sqlite3
import threading
import time
from pathlib import Path
from types import SimpleNamespace

from reactor_tool.tool.user_sandbox_manager import UserSandboxManager


class _RunTracker:
    def __init__(self):
        self.lock = threading.Lock()
        self.started = threading.Event()
        self.calls = 0
        self.active = 0
        self.max_active = 0

    def run(self, command, cwd=None, timeout=None):
        if command == "work":
            with self.lock:
                self.calls += 1
                self.active += 1
                self.max_active = max(self.max_active, self.active)
                self.started.set()
            time.sleep(0.08)
            with self.lock:
                self.active -= 1
        return SimpleNamespace(exit_code=0, stdout="", stderr="")


class _FakeSandbox:
    def __init__(self, sandbox_id="sandbox-1", tracker=None):
        self.sandbox_id = sandbox_id
        self.tracker = tracker or _RunTracker()
        self.commands = self
        self.run_calls: list[tuple[str, str | None, int | None]] = []
        self.pause_calls: list[bool] = []
        self.kill_calls = 0
        self.timeout_calls: list[int] = []

    def run(self, command, cwd=None, timeout=None):
        self.run_calls.append((command, cwd, timeout))
        return self.tracker.run(command, cwd=cwd, timeout=timeout)

    def pause(self, keep_memory=True):
        self.pause_calls.append(bool(keep_memory))
        return True

    def kill(self):
        self.kill_calls += 1

    def set_timeout(self, timeout):
        self.timeout_calls.append(timeout)


def _make_manager(
    db_path: Path,
    sandbox: _FakeSandbox,
    clock: list[float],
    *,
    debounce_sec=3,
    idle_sec=1200,
    connect_calls=None,
    create_calls=None,
):
    connect_calls = connect_calls if connect_calls is not None else []
    create_calls = create_calls if create_calls is not None else []

    def create(*args, **kwargs):
        create_calls.append(kwargs)
        return sandbox

    def connect(sandbox_id, **kwargs):
        connect_calls.append(sandbox_id)
        return sandbox

    return UserSandboxManager(
        db_path=str(db_path),
        create_fn=create,
        connect_fn=connect,
        pause_fn=lambda value, keep_memory: value.pause(keep_memory),
        debounce_sec=debounce_sec,
        idle_sec=idle_sec,
        time_fn=lambda: clock[0],
        start_reaper=False,
    )


def test_same_owner_acquire_creates_once_and_release_never_kills(tmp_path):
    clock = [100.0]
    sandbox = _FakeSandbox()
    creates = []
    manager = _make_manager(
        tmp_path / "sandbox.db", sandbox, clock, create_calls=creates
    )
    try:
        first = manager.acquire("user:one")
        second = manager.acquire("user:one")
        assert first.sandbox is sandbox
        assert second.sandbox is sandbox
        assert len(creates) == 1

        manager.release("user:one")
        manager.release("user:one")
        clock[0] += 3
        manager.process_due_pauses()
        manager.shutdown()
        assert sandbox.kill_calls == 0
    finally:
        manager.shutdown()


def test_new_manager_reconnects_persisted_sandbox(tmp_path):
    db_path = tmp_path / "sandbox.db"
    clock = [100.0]
    sandbox = _FakeSandbox("persisted-id")
    first = _make_manager(db_path, sandbox, clock)
    first.acquire("user:one")
    first.release("user:one")
    first.shutdown()

    connect_calls = []
    creates = []
    second = _make_manager(
        db_path,
        sandbox,
        clock,
        connect_calls=connect_calls,
        create_calls=creates,
    )
    try:
        lease = second.acquire("user:one")
        assert lease.sandbox_id == "persisted-id"
        assert connect_calls == ["persisted-id"]
        assert creates == []
        second.release("user:one")
    finally:
        second.shutdown()


def test_debounce_pause_can_be_cancelled_then_runs_once(tmp_path):
    clock = [100.0]
    sandbox = _FakeSandbox()
    manager = _make_manager(tmp_path / "sandbox.db", sandbox, clock)
    try:
        manager.release("user:missing")
        lease = manager.acquire("user:one")
        manager.release("user:one")

        clock[0] += 1
        manager.process_due_pauses()
        assert sandbox.pause_calls == []

        manager.acquire("user:one")
        manager.release("user:one")
        clock[0] += 2
        manager.process_due_pauses()
        assert sandbox.pause_calls == []

        clock[0] += 1
        manager.process_due_pauses()
        assert sandbox.pause_calls == [True]
        assert sandbox.kill_calls == 0
    finally:
        manager.shutdown()


def test_full_paused_acquire_reconnects_without_compacting(tmp_path):
    clock = [100.0]
    sandbox = _FakeSandbox()
    connect_calls = []
    manager = _make_manager(
        tmp_path / "sandbox.db", sandbox, clock, connect_calls=connect_calls
    )
    try:
        manager.acquire("user:one")
        manager.release("user:one")
        clock[0] += 3
        manager.process_due_pauses()
        assert sandbox.pause_calls == [True]

        clock[0] += 600
        lease = manager.acquire("user:one")
        assert lease.sandbox_id == "sandbox-1"
        assert connect_calls == ["sandbox-1"]
        assert sandbox.pause_calls == [True]
        manager.release("user:one")
    finally:
        manager.shutdown()


def test_full_paused_idle_reaps_to_fs_only_pause(tmp_path):
    clock = [100.0]
    sandbox = _FakeSandbox()
    manager = _make_manager(tmp_path / "sandbox.db", sandbox, clock)
    try:
        manager.acquire("user:one")
        manager.release("user:one")
        clock[0] += 3
        manager.process_due_pauses()
        clock[0] += 1260
        manager.reap_idle()

        assert sandbox.pause_calls == [True, False]
        assert ("sync", None, 30) in sandbox.run_calls
        with sqlite3.connect(tmp_path / "sandbox.db") as connection:
            state = connection.execute(
                "SELECT state FROM e2b_user_sandbox WHERE owner_key = ?",
                ("user:one",),
            ).fetchone()[0]
        assert state == "FS_ONLY_PAUSED"
    finally:
        manager.shutdown()


def test_reap_does_not_pause_while_in_flight(tmp_path):
    clock = [100.0]
    sandbox = _FakeSandbox()
    manager = _make_manager(tmp_path / "sandbox.db", sandbox, clock)
    try:
        manager.acquire("user:one")
        manager.acquire("user:one")
        manager.release("user:one")
        clock[0] += 10
        manager.reap_idle()
        assert sandbox.pause_calls == []
        manager.release("user:one")
    finally:
        manager.shutdown()


def test_session_gate_serializes_same_session_and_overlaps_different_sessions(tmp_path):
    clock = [100.0]
    tracker = _RunTracker()
    sandbox = _FakeSandbox(tracker=tracker)
    manager = _make_manager(tmp_path / "sandbox.db", sandbox, clock)

    def execute(session_id):
        lease = manager.acquire("user:one")
        try:
            with manager.session_gate("user:one", session_id):
                lease.sandbox.commands.run("work")
        finally:
            manager.release("user:one")

    try:
        first = threading.Thread(target=execute, args=("same",))
        second = threading.Thread(target=execute, args=("same",))
        first.start()
        assert tracker.started.wait(1)
        second.start()
        time.sleep(0.02)
        assert tracker.calls == 1
        first.join()
        second.join()
        assert tracker.max_active == 1

        tracker.started.clear()
        tracker.calls = 0
        tracker.max_active = 0
        first = threading.Thread(target=execute, args=("one",))
        second = threading.Thread(target=execute, args=("two",))
        first.start()
        second.start()
        first.join()
        second.join()
        assert tracker.max_active == 2
    finally:
        manager.shutdown()
