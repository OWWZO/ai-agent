# -*- coding: utf-8 -*-
import sqlite3
import threading
import time
from pathlib import Path
from types import SimpleNamespace

from reactor_tool.tool.user_sandbox_manager import UserSandboxManager, _sync_disk


class _RunTracker:
    def __init__(self):
        self.lock = threading.Lock()
        self.started = threading.Event()
        self.calls = 0
        self.active = 0
        self.max_active = 0

    def run(self, command, cwd=None, timeout=None, user=None):
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

    def run(self, command, cwd=None, timeout=None, user=None):
        self.run_calls.append((command, cwd, timeout))
        return self.tracker.run(command, cwd=cwd, timeout=timeout)

    def pause(self, keep_memory=True):
        self.pause_calls.append(bool(keep_memory))
        return True

    def kill(self):
        self.kill_calls += 1

    def set_timeout(self, timeout):
        self.timeout_calls.append(timeout)


class _BrowserAwareSandbox(_FakeSandbox):
    def __init__(self, sandbox_id="sandbox-1"):
        super().__init__(sandbox_id)
        self.browser_running = False
        self.browser_start_count = 0

    def run(self, command, cwd=None, timeout=None, user=None):
        self.run_calls.append((command, cwd, timeout))
        if command == "/usr/local/bin/ensure_desktop_chromium.sh":
            if not self.browser_running:
                self.browser_start_count += 1
                self.browser_running = True
            return SimpleNamespace(exit_code=0, stdout="", stderr="")
        return self.tracker.run(command, cwd=cwd, timeout=timeout)

    def pause(self, keep_memory=True):
        result = super().pause(keep_memory)
        if not keep_memory:
            self.browser_running = False
        return result


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
        hold_sec=600,
        time_fn=lambda: clock[0],
        start_reaper=False,
    )


def _hold_row(db_path, owner="user:one"):
    with sqlite3.connect(db_path) as connection:
        return connection.execute(
            """
            SELECT hold_mode, hold_until, state, in_flight
            FROM e2b_user_sandbox WHERE owner_key = ?
            """,
            (owner,),
        ).fetchone()


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


def test_acquire_keeps_browser_launcher_idempotent_across_pause_modes(tmp_path):
    db_path = tmp_path / "sandbox.db"
    clock = [100.0]
    sandbox = _BrowserAwareSandbox()
    manager = _make_manager(db_path, sandbox, clock, idle_sec=1)
    try:
        manager.acquire("user:one")
        manager.acquire("user:one")
        assert sandbox.browser_start_count == 1

        manager.release("user:one")
        manager.release("user:one")
        clock[0] += 3
        manager.process_due_pauses()
        assert sandbox.pause_calls == [True]
        assert sandbox.browser_running

        manager.acquire("user:one")
        assert sandbox.browser_start_count == 1
        manager.release("user:one")

        clock[0] += 3
        manager.process_due_pauses()
        manager.reap_idle()
        assert sandbox.pause_calls == [True, True, False]
        assert not sandbox.browser_running

        manager.acquire("user:one")
        assert sandbox.browser_start_count == 2
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


def test_compact_skips_sync_without_command_channel():
    class SandboxWithoutCommands:
        commands = None
        run_code_calls = 0

        def run_code(self, *_args, **_kwargs):
            self.run_code_calls += 1
            raise AssertionError("compact sync must not start Jupyter")

    sandbox = SandboxWithoutCommands()

    _sync_disk(sandbox)

    assert sandbox.run_code_calls == 0


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


def test_init_schema_alters_legacy_table(tmp_path):
    db_path = tmp_path / "sandbox.db"
    with sqlite3.connect(db_path) as connection:
        connection.execute(
            """
            CREATE TABLE e2b_user_sandbox (
                owner_key TEXT PRIMARY KEY,
                sandbox_id TEXT,
                state TEXT,
                keep_memory INTEGER,
                last_used_at REAL,
                in_flight INTEGER,
                generation INTEGER,
                updated_at REAL
            )
            """
        )
        connection.execute(
            "INSERT INTO e2b_user_sandbox VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            ("user:one", "sid", "RUNNING", 1, 100, 0, 1, 100),
        )
        connection.commit()
    clock = [100.0]
    manager = _make_manager(db_path, _FakeSandbox(), clock)
    try:
        manager.enter_user_operating("user:one", ttl_sec=600)
        row = _hold_row(db_path)
        assert row[0] == "user_operating"
        assert row[1] == 700.0
    finally:
        manager.shutdown()


def test_hold_suppresses_debounce_until_deadline(tmp_path):
    db_path = tmp_path / "sandbox.db"
    clock = [100.0]
    sandbox = _FakeSandbox()
    manager = _make_manager(db_path, sandbox, clock)
    try:
        manager.acquire("user:one")
        manager.enter_user_operating("user:one", ttl_sec=600)
        manager.release("user:one")

        clock[0] += 3
        manager.process_due_pauses()
        assert sandbox.pause_calls == []
        row = _hold_row(db_path)
        assert row[0] == "user_operating"
        assert row[1] == 700.0

        clock[0] = 700.0
        manager.process_due_pauses()
        assert sandbox.pause_calls == [True]
        row = _hold_row(db_path)
        assert not row[0]
        assert row[1] is None
        assert row[2] == "FULL_PAUSED"
    finally:
        manager.shutdown()


def test_hold_acquire_release_does_not_extend_deadline(tmp_path):
    db_path = tmp_path / "sandbox.db"
    clock = [100.0]
    sandbox = _FakeSandbox()
    manager = _make_manager(db_path, sandbox, clock)
    try:
        manager.acquire("user:one")
        manager.enter_user_operating("user:one", ttl_sec=600)
        manager.release("user:one")
        original = _hold_row(db_path)[1]

        clock[0] = 250.0
        manager.acquire("user:one")
        manager.release("user:one")
        assert _hold_row(db_path)[1] == original
        assert sandbox.pause_calls == []

        clock[0] = 700.0
        manager.process_due_pauses()
        assert sandbox.pause_calls == [True]
    finally:
        manager.shutdown()


def test_hold_expiry_waits_for_in_flight_then_pauses_immediately(tmp_path):
    db_path = tmp_path / "sandbox.db"
    clock = [100.0]
    sandbox = _FakeSandbox()
    manager = _make_manager(db_path, sandbox, clock)
    try:
        manager.acquire("user:one")
        manager.enter_user_operating("user:one", ttl_sec=600)
        manager.release("user:one")
        manager.acquire("user:one")

        clock[0] = 700.0
        manager.process_due_pauses()
        assert sandbox.pause_calls == []
        row = _hold_row(db_path)
        assert row[0] == "user_operating"
        assert row[1] == 700.0
        assert row[3] == 1

        manager.release("user:one")
        assert sandbox.pause_calls == [True]
        row = _hold_row(db_path)
        assert not row[0]
        assert row[2] == "FULL_PAUSED"
    finally:
        manager.shutdown()


def test_exit_user_operating_restores_debounce(tmp_path):
    db_path = tmp_path / "sandbox.db"
    clock = [100.0]
    sandbox = _FakeSandbox()
    manager = _make_manager(db_path, sandbox, clock)
    try:
        manager.acquire("user:one")
        manager.enter_user_operating("user:one", ttl_sec=600)
        manager.release("user:one")
        manager.exit_user_operating("user:one")
        assert _hold_row(db_path)[0] == ""

        clock[0] += 3
        manager.process_due_pauses()
        assert sandbox.pause_calls == [True]
    finally:
        manager.shutdown()


def test_second_enter_resets_hold_until(tmp_path):
    db_path = tmp_path / "sandbox.db"
    clock = [100.0]
    sandbox = _FakeSandbox()
    manager = _make_manager(db_path, sandbox, clock)
    try:
        manager.acquire("user:one")
        manager.enter_user_operating("user:one", ttl_sec=600)
        manager.release("user:one")
        assert _hold_row(db_path)[1] == 700.0

        clock[0] = 200.0
        manager.acquire("user:one")
        manager.enter_user_operating("user:one", ttl_sec=600)
        manager.release("user:one")
        assert _hold_row(db_path)[1] == 800.0

        clock[0] = 700.0
        manager.process_due_pauses()
        assert sandbox.pause_calls == []

        clock[0] = 800.0
        manager.process_due_pauses()
        assert sandbox.pause_calls == [True]
    finally:
        manager.shutdown()


def test_expired_hold_reconnects_when_live_missing(tmp_path):
    db_path = tmp_path / "sandbox.db"
    clock = [100.0]
    sandbox = _FakeSandbox("persisted-id")
    first = _make_manager(db_path, sandbox, clock)
    first.acquire("user:one")
    first.enter_user_operating("user:one", ttl_sec=600)
    first.release("user:one")
    first.shutdown()

    clock[0] = 700.0
    connect_calls = []
    second = _make_manager(db_path, sandbox, clock, connect_calls=connect_calls)
    try:
        second.process_due_pauses()
        assert connect_calls == ["persisted-id"]
        assert sandbox.pause_calls == [True]
        row = _hold_row(db_path)
        assert not row[0]
        assert row[2] == "FULL_PAUSED"
    finally:
        second.shutdown()


def test_hold_full_pause_then_idle_still_compacts(tmp_path):
    db_path = tmp_path / "sandbox.db"
    clock = [100.0]
    sandbox = _FakeSandbox()
    manager = _make_manager(db_path, sandbox, clock)
    try:
        manager.acquire("user:one")
        manager.enter_user_operating("user:one", ttl_sec=600)
        manager.release("user:one")
        clock[0] = 700.0
        manager.process_due_pauses()
        assert sandbox.pause_calls == [True]

        clock[0] += 1260
        manager.reap_idle()
        assert sandbox.pause_calls == [True, False]
        assert _hold_row(db_path)[2] == "FS_ONLY_PAUSED"
    finally:
        manager.shutdown()


def test_hold_busy_pause_keeps_deadline(tmp_path):
    db_path = tmp_path / "sandbox.db"
    clock = [100.0]
    sandbox = _FakeSandbox()
    attempts = {"n": 0}

    def pause_fn(value, keep_memory):
        attempts["n"] += 1
        if attempts["n"] == 1:
            raise RuntimeError("503 busy")
        return value.pause(keep_memory)

    manager = UserSandboxManager(
        db_path=str(db_path),
        create_fn=lambda *args, **kwargs: sandbox,
        connect_fn=lambda sandbox_id, **kwargs: sandbox,
        pause_fn=pause_fn,
        debounce_sec=3,
        idle_sec=1200,
        hold_sec=600,
        time_fn=lambda: clock[0],
        start_reaper=False,
    )
    try:
        manager.acquire("user:one")
        manager.enter_user_operating("user:one", ttl_sec=600)
        manager.release("user:one")
        clock[0] = 700.0
        manager.process_due_pauses()
        assert sandbox.pause_calls == []
        row = _hold_row(db_path)
        assert row[0] == "user_operating"
        assert row[1] == 700.0
        assert row[2] == "RUNNING"

        manager.process_due_pauses()
        assert sandbox.pause_calls == [True]
        row = _hold_row(db_path)
        assert not row[0]
        assert row[2] == "FULL_PAUSED"
    finally:
        manager.shutdown()
