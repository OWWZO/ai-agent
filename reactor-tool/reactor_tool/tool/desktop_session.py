# -*- coding: utf-8 -*-
"""打开/关闭 owner 级 E2B 桌面会话：get_host(6080) + user_operating hold。"""

from __future__ import annotations

import time
from typing import Any

from reactor_tool.model.protocal import (
    DesktopSessionCloseRequest,
    DesktopSessionOpenRequest,
    DesktopSessionOpenResponse,
)
from reactor_tool.tool.sandbox_backend_config import get_sandbox_backend
from reactor_tool.tool.user_sandbox_manager import get_user_sandbox_manager

VNC_PORT = 6080
_VNC_HEALTH_CHECK = (
    f"curl -fsS --max-time 1 http://127.0.0.1:{VNC_PORT}/vnc.html >/dev/null"
)
_START_DESKTOP_AND_WAIT = (
    "/start_command.sh && "
    "for attempt in $(seq 1 20); do "
    f"if {_VNC_HEALTH_CHECK}; then exit 0; fi; "
    "sleep 1; "
    "done; "
    "echo 'noVNC service failed to start on port 6080' >&2; "
    "exit 1"
)


class DesktopSessionError(Exception):
    def __init__(self, status_code: int, message: str):
        super().__init__(message)
        self.status_code = status_code
        self.message = message


def open_desktop_session(body: DesktopSessionOpenRequest) -> DesktopSessionOpenResponse:
    if get_sandbox_backend() != "e2b":
        raise DesktopSessionError(
            400, "desktop_session requires CODE_SANDBOX_BACKEND=e2b"
        )
    owner = (body.owner_key or "").strip() or "visitor:anonymous"
    ttl = int(body.ttl_seconds)
    manager = get_user_sandbox_manager()
    lease = manager.acquire(
        owner,
        timeout_sec=max(120, ttl),
        update_timeout=False,
    )
    hold_set = False
    try:
        hold_until = manager.enter_user_operating(owner, ttl_sec=ttl)
        if hold_until is None:
            raise DesktopSessionError(500, "failed to enter user operating hold")
        hold_set = True
        _ensure_desktop_ready(lease.sandbox)
        host = _vnc_host(lease.sandbox)
        url = f"https://{host}/vnc.html?autoconnect=1&resize=scale"
        return DesktopSessionOpenResponse(url=url, port=VNC_PORT, hold_until=hold_until)
    except Exception:
        if hold_set:
            manager.exit_user_operating(owner)
        raise
    finally:
        manager.release(owner)


def close_desktop_session(body: DesktopSessionCloseRequest) -> dict[str, Any]:
    if get_sandbox_backend() != "e2b":
        return {"ok": True}
    owner = (body.owner_key or "").strip() or "visitor:anonymous"
    get_user_sandbox_manager().exit_user_operating(owner)
    return {"ok": True}


def _vnc_host(sandbox: Any) -> str:
    get_host = getattr(sandbox, "get_host", None)
    if not callable(get_host):
        raise DesktopSessionError(500, "sandbox does not expose get_host")
    host = str(get_host(VNC_PORT) or "").strip()
    if not host:
        raise DesktopSessionError(500, "sandbox get_host returned empty host")
    return host


def _ensure_desktop_ready(sandbox: Any) -> None:
    commands = getattr(sandbox, "commands", None)
    run = getattr(commands, "run", None) if commands is not None else None
    if not callable(run):
        raise DesktopSessionError(503, "sandbox does not support desktop commands")

    try:
        health = run(_VNC_HEALTH_CHECK, timeout=5)
        if _command_succeeded(health):
            return
    except Exception:
        pass

    try:
        result = run(_START_DESKTOP_AND_WAIT, user="root", timeout=45)
    except Exception as exc:
        if _desktop_ready_after_start(run):
            return
        raise DesktopSessionError(
            503, f"desktop service failed to start on port {VNC_PORT}: {exc}"
        ) from exc

    if _command_succeeded(result):
        return
    if _desktop_ready_after_start(run):
        return

    detail = " ".join(
        part.strip()
        for part in (
            str(getattr(result, "stdout", "") or ""),
            str(getattr(result, "stderr", "") or ""),
        )
        if part and part.strip()
    )
    message = f"desktop service failed to start on port {VNC_PORT}"
    if detail:
        message = f"{message}: {detail}"
    raise DesktopSessionError(503, message)


def _desktop_ready_after_start(run: Any) -> bool:
    # E2B can time out the command stream after the service itself has started.
    for attempt in range(2):
        try:
            if _command_succeeded(run(_VNC_HEALTH_CHECK, timeout=3)):
                return True
        except Exception:
            pass
        if attempt == 0:
            time.sleep(0.5)
    return False


def _command_succeeded(result: Any) -> bool:
    return getattr(result, "exit_code", 0) in (None, 0) and not getattr(
        result, "error", None
    )
