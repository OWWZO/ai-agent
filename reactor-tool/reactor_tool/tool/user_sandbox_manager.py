# -*- coding: utf-8 -*-
"""用户级 E2B 沙箱生命周期：同 ownerKey 复用，pause 而非 kill。"""

from __future__ import annotations

import sqlite3
import threading
from contextlib import contextmanager
from dataclasses import dataclass
from typing import Any, Callable, Dict, Iterator, Optional, Tuple

from loguru import logger

from reactor_tool.tool.sandbox_backend_config import (
    build_e2b_create_kwargs,
    connect_e2b_sandbox,
    create_e2b_sandbox,
    get_e2b_full_pause_debounce_sec,
    get_e2b_fs_only_idle_sec,
    get_e2b_sandbox_db_path,
    pause_e2b_sandbox,
    set_e2b_sandbox_timeout,
)

STATE_MISSING = "MISSING"
STATE_CREATING = "CREATING"
STATE_RUNNING = "RUNNING"
STATE_FULL_PAUSING = "FULL_PAUSING"
STATE_FULL_PAUSED = "FULL_PAUSED"
STATE_COMPACTING = "COMPACTING"
STATE_FS_ONLY_PAUSED = "FS_ONLY_PAUSED"

CreateFn = Callable[..., Any]
ConnectFn = Callable[..., Any]
PauseFn = Callable[..., bool]
TimeFn = Callable[[], float]


@dataclass
class SandboxLease:
    owner_key: str
    sandbox: Any
    sandbox_id: str
    generation: int


_manager_guard = threading.Lock()
_manager: Optional["UserSandboxManager"] = None


def get_user_sandbox_manager() -> "UserSandboxManager":
    global _manager
    with _manager_guard:
        if _manager is None:
            _manager = UserSandboxManager()
        return _manager


def reset_user_sandbox_manager(
    manager: Optional["UserSandboxManager"] = None,
) -> None:
    global _manager
    with _manager_guard:
        if _manager is not None:
            _manager.shutdown()
        _manager = manager


class UserSandboxManager:
    def __init__(
        self,
        *,
        db_path: str | None = None,
        create_fn: CreateFn | None = None,
        connect_fn: ConnectFn | None = None,
        pause_fn: PauseFn | None = None,
        debounce_sec: float | None = None,
        idle_sec: float | None = None,
        time_fn: TimeFn | None = None,
        start_reaper: bool = True,
    ):
        self._db_path = db_path or get_e2b_sandbox_db_path()
        self._create_fn = create_fn
        self._connect_fn = connect_fn
        self._pause_fn = pause_fn
        self._debounce_sec = (
            get_e2b_full_pause_debounce_sec()
            if debounce_sec is None
            else max(0.05, float(debounce_sec))
        )
        self._idle_sec = (
            get_e2b_fs_only_idle_sec()
            if idle_sec is None
            else max(1.0, float(idle_sec))
        )
        self._time = time_fn or __import__("time").time
        self._db_lock = threading.Lock()
        self._meta_lock = threading.Lock()
        self._owner_locks: Dict[str, threading.RLock] = {}
        self._session_locks: Dict[Tuple[str, str], threading.Lock] = {}
        self._live: Dict[str, Any] = {}
        self._pending_pause_at: Dict[str, float] = {}
        self._conn = sqlite3.connect(self._db_path, check_same_thread=False)
        self._conn.row_factory = sqlite3.Row
        self._conn.execute("PRAGMA journal_mode=WAL")
        self._init_schema()
        self._stop = threading.Event()
        self._reaper: threading.Thread | None = None
        if start_reaper:
            tick = min(1.0, max(0.05, self._debounce_sec / 2.0))
            self._reaper = threading.Thread(
                target=self._reaper_loop,
                args=(tick,),
                name="e2b-user-sandbox-reaper",
                daemon=True,
            )
            self._reaper.start()

    def shutdown(self) -> None:
        self._stop.set()
        if self._reaper is not None:
            self._reaper.join(timeout=2)
            self._reaper = None
        with self._db_lock:
            try:
                self._conn.close()
            except Exception:
                pass

    def acquire(self, owner_key: str, timeout_sec: int = 120) -> SandboxLease:
        owner = _normalize_owner(owner_key)
        lock = self._owner_lock(owner)
        with lock:
            self._pending_pause_at.pop(owner, None)
            row = self._load(owner)
            sandbox = self._live.get(owner)
            generation = int(row["generation"] or 1) if row else 1
            sandbox_id = str(row["sandbox_id"] or "") if row else ""
            state = str(row["state"] or STATE_MISSING) if row else STATE_MISSING

            if sandbox is None and sandbox_id:
                if state in {
                    STATE_FULL_PAUSED,
                    STATE_FS_ONLY_PAUSED,
                    STATE_FULL_PAUSING,
                    STATE_COMPACTING,
                    STATE_RUNNING,
                    STATE_CREATING,
                }:
                    try:
                        sandbox = self._connect(sandbox_id, timeout_sec)
                    except Exception as exc:
                        if not _is_gone(exc):
                            logger.warning(
                                "[user_sandbox] connect failed owner={} id={}: {}",
                                owner,
                                sandbox_id,
                                exc,
                            )
                        sandbox = None
                        generation += 1
                        sandbox_id = ""

            if sandbox is None:
                self._upsert(
                    owner,
                    sandbox_id=sandbox_id,
                    state=STATE_CREATING,
                    keep_memory=1,
                    last_used_at=self._time(),
                    in_flight=(int(row["in_flight"]) if row else 0),
                    generation=generation,
                )
                sandbox = self._create(timeout_sec)
                sandbox_id = _sandbox_id(sandbox)
                generation = generation if row else 1

            try:
                set_e2b_sandbox_timeout(
                    sandbox,
                    int(build_e2b_create_kwargs(float(timeout_sec))["timeout"]),
                )
            except Exception as exc:
                logger.debug("[user_sandbox] set_timeout skipped: {}", exc)

            in_flight = (int(row["in_flight"]) if row else 0) + 1
            self._live[owner] = sandbox
            self._upsert(
                owner,
                sandbox_id=sandbox_id,
                state=STATE_RUNNING,
                keep_memory=1,
                last_used_at=self._time(),
                in_flight=in_flight,
                generation=generation,
            )
            return SandboxLease(
                owner_key=owner,
                sandbox=sandbox,
                sandbox_id=sandbox_id,
                generation=generation,
            )

    def release(self, owner_key: str) -> None:
        owner = _normalize_owner(owner_key)
        lock = self._owner_lock(owner)
        with lock:
            row = self._load(owner)
            if row is None:
                return
            in_flight = max(0, int(row["in_flight"] or 0) - 1)
            self._upsert(
                owner,
                sandbox_id=str(row["sandbox_id"] or ""),
                state=str(row["state"] or STATE_RUNNING),
                keep_memory=int(row["keep_memory"] or 1),
                last_used_at=float(row["last_used_at"] or self._time()),
                in_flight=in_flight,
                generation=int(row["generation"] or 1),
            )
            if in_flight == 0:
                self._pending_pause_at[owner] = self._time() + self._debounce_sec

    @contextmanager
    def session_gate(self, owner_key: str, session_id: str) -> Iterator[None]:
        key = (_normalize_owner(owner_key), (session_id or "").strip() or "anonymous")
        with self._meta_lock:
            lock = self._session_locks.get(key)
            if lock is None:
                lock = threading.Lock()
                self._session_locks[key] = lock
        lock.acquire()
        try:
            yield
        finally:
            lock.release()

    def process_due_pauses(self) -> None:
        now = self._time()
        due = [owner for owner, at in list(self._pending_pause_at.items()) if at <= now]
        for owner in due:
            self._full_pause(owner)

    def reap_idle(self) -> None:
        self.process_due_pauses()
        now = self._time()
        with self._db_lock:
            rows = list(
                self._conn.execute(
                    "SELECT owner_key FROM e2b_user_sandbox WHERE state = ?",
                    (STATE_FULL_PAUSED,),
                ).fetchall()
            )
        for row in rows:
            owner = str(row["owner_key"])
            self._compact_if_idle(owner, now)

    def _full_pause(self, owner: str) -> None:
        lock = self._owner_lock(owner)
        with lock:
            if owner not in self._pending_pause_at:
                return
            row = self._load(owner)
            if row is None:
                self._pending_pause_at.pop(owner, None)
                return
            if int(row["in_flight"] or 0) > 0:
                self._pending_pause_at.pop(owner, None)
                return
            state = str(row["state"] or "")
            if state in {STATE_FULL_PAUSED, STATE_FS_ONLY_PAUSED, STATE_COMPACTING}:
                self._pending_pause_at.pop(owner, None)
                return
            sandbox = self._live.get(owner)
            if sandbox is None:
                self._pending_pause_at.pop(owner, None)
                return
            self._upsert(
                owner,
                sandbox_id=str(row["sandbox_id"] or ""),
                state=STATE_FULL_PAUSING,
                keep_memory=1,
                last_used_at=float(row["last_used_at"] or self._time()),
                in_flight=0,
                generation=int(row["generation"] or 1),
            )
            try:
                self._pause(sandbox, True)
            except Exception as exc:
                if _is_busy(exc):
                    logger.warning(
                        "[user_sandbox] pause busy owner={}, keep RUNNING", owner
                    )
                    self._upsert(
                        owner,
                        sandbox_id=str(row["sandbox_id"] or ""),
                        state=STATE_RUNNING,
                        keep_memory=1,
                        last_used_at=float(row["last_used_at"] or self._time()),
                        in_flight=0,
                        generation=int(row["generation"] or 1),
                    )
                    self._pending_pause_at[owner] = self._time() + self._debounce_sec
                    return
                logger.exception("[user_sandbox] pause failed owner={}", owner)
                self._upsert(
                    owner,
                    sandbox_id=str(row["sandbox_id"] or ""),
                    state=STATE_RUNNING,
                    keep_memory=1,
                    last_used_at=float(row["last_used_at"] or self._time()),
                    in_flight=0,
                    generation=int(row["generation"] or 1),
                )
                return
            self._live.pop(owner, None)
            self._pending_pause_at.pop(owner, None)
            self._upsert(
                owner,
                sandbox_id=str(row["sandbox_id"] or ""),
                state=STATE_FULL_PAUSED,
                keep_memory=1,
                last_used_at=float(row["last_used_at"] or self._time()),
                in_flight=0,
                generation=int(row["generation"] or 1),
            )

    def _compact_if_idle(self, owner: str, now: float) -> None:
        lock = self._owner_lock(owner)
        with lock:
            row = self._load(owner)
            if row is None:
                return
            if int(row["in_flight"] or 0) > 0:
                return
            if str(row["state"] or "") != STATE_FULL_PAUSED:
                return
            last_used = float(row["last_used_at"] or 0)
            if now - last_used < self._idle_sec:
                return
            sandbox_id = str(row["sandbox_id"] or "")
            generation = int(row["generation"] or 1)
            self._upsert(
                owner,
                sandbox_id=sandbox_id,
                state=STATE_COMPACTING,
                keep_memory=1,
                last_used_at=last_used,
                in_flight=0,
                generation=generation,
            )
            try:
                sandbox = self._connect(sandbox_id, 120)
                _sync_disk(sandbox)
                self._pause(sandbox, False)
            except Exception as exc:
                logger.exception(
                    "[user_sandbox] compact failed owner={} id={}: {}",
                    owner,
                    sandbox_id,
                    exc,
                )
                self._upsert(
                    owner,
                    sandbox_id=sandbox_id,
                    state=STATE_FULL_PAUSED,
                    keep_memory=1,
                    last_used_at=last_used,
                    in_flight=0,
                    generation=generation,
                )
                return
            self._live.pop(owner, None)
            self._upsert(
                owner,
                sandbox_id=sandbox_id,
                state=STATE_FS_ONLY_PAUSED,
                keep_memory=0,
                last_used_at=last_used,
                in_flight=0,
                generation=generation,
            )

    def _reaper_loop(self, tick: float) -> None:
        while not self._stop.wait(tick):
            try:
                self.reap_idle()
            except Exception:
                logger.exception("[user_sandbox] reaper failed")

    def _create(self, timeout_sec: int) -> Any:
        kwargs = build_e2b_create_kwargs(float(timeout_sec))
        if self._create_fn is not None:
            try:
                return self._create_fn(timeout_sec, **kwargs)
            except TypeError:
                return self._create_fn(**kwargs)
        return create_e2b_sandbox(float(timeout_sec))

    def _connect(self, sandbox_id: str, timeout_sec: int) -> Any:
        if self._connect_fn is not None:
            try:
                return self._connect_fn(sandbox_id, timeout=timeout_sec)
            except TypeError:
                return self._connect_fn(sandbox_id)
        return connect_e2b_sandbox(sandbox_id, timeout_sec=timeout_sec)

    def _pause(self, sandbox: Any, keep_memory: bool) -> bool:
        if self._pause_fn is not None:
            return bool(self._pause_fn(sandbox, keep_memory))
        return pause_e2b_sandbox(sandbox, keep_memory=keep_memory)

    def _owner_lock(self, owner: str) -> threading.RLock:
        with self._meta_lock:
            lock = self._owner_locks.get(owner)
            if lock is None:
                lock = threading.RLock()
                self._owner_locks[owner] = lock
            return lock

    def _init_schema(self) -> None:
        with self._db_lock:
            self._conn.execute(
                """
                CREATE TABLE IF NOT EXISTS e2b_user_sandbox (
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
            self._conn.commit()

    def _load(self, owner: str) -> Optional[sqlite3.Row]:
        with self._db_lock:
            return self._conn.execute(
                "SELECT * FROM e2b_user_sandbox WHERE owner_key = ?",
                (owner,),
            ).fetchone()

    def _upsert(
        self,
        owner: str,
        *,
        sandbox_id: str,
        state: str,
        keep_memory: int,
        last_used_at: float,
        in_flight: int,
        generation: int,
    ) -> None:
        now = self._time()
        with self._db_lock:
            self._conn.execute(
                """
                INSERT INTO e2b_user_sandbox (
                    owner_key, sandbox_id, state, keep_memory,
                    last_used_at, in_flight, generation, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(owner_key) DO UPDATE SET
                    sandbox_id = excluded.sandbox_id,
                    state = excluded.state,
                    keep_memory = excluded.keep_memory,
                    last_used_at = excluded.last_used_at,
                    in_flight = excluded.in_flight,
                    generation = excluded.generation,
                    updated_at = excluded.updated_at
                """,
                (
                    owner,
                    sandbox_id,
                    state,
                    keep_memory,
                    last_used_at,
                    in_flight,
                    generation,
                    now,
                ),
            )
            self._conn.commit()


def _normalize_owner(owner_key: str) -> str:
    return (owner_key or "").strip() or "visitor:anonymous"


def _sandbox_id(sandbox: Any) -> str:
    sid = getattr(sandbox, "sandbox_id", None)
    if sid:
        return str(sid)
    return f"anon-{id(sandbox)}"


def _is_gone(exc: BaseException) -> bool:
    text = str(exc).casefold()
    name = type(exc).__name__.casefold()
    return (
        "404" in text
        or "not found" in text
        or "does not exist" in text
        or "notfound" in name
    )


def _is_busy(exc: BaseException) -> bool:
    text = str(exc).casefold()
    return "503" in text or "busy" in text or "unavailable" in text


def _sync_disk(sandbox: Any) -> None:
    commands = getattr(sandbox, "commands", None)
    run = getattr(commands, "run", None) if commands is not None else None
    if callable(run):
        run("sync", timeout=30)
        return
    run_code = getattr(sandbox, "run_code", None)
    if callable(run_code):
        run_code("import os\nos.sync()\n", timeout=30)
