# -*- coding: utf-8 -*-
"""reactor-tool 进程角色：all（默认单进程全量）| api（多进程）| sandbox（单进程跑 bash/code_execution）。"""
from __future__ import annotations

import os
from typing import Literal

ServiceRole = Literal["all", "api", "sandbox"]

_VALID = frozenset({"all", "api", "sandbox"})
_DEFAULT_SANDBOX_URL = "http://127.0.0.1:1602"


def get_service_role() -> ServiceRole:
    raw = (os.getenv("REACTOR_TOOL_ROLE") or "all").strip().lower()
    if raw not in _VALID:
        raise ValueError(
            f"Unsupported REACTOR_TOOL_ROLE={raw!r}; expected one of {sorted(_VALID)}"
        )
    return raw  # type: ignore[return-value]


def get_sandbox_base_url() -> str:
    """API 角色把 bash/code_execution 反代到此地址（sandbox 进程）。"""
    return (os.getenv("REACTOR_SANDBOX_URL") or _DEFAULT_SANDBOX_URL).strip().rstrip("/")


def sandbox_requires_single_worker(role: ServiceRole | None = None) -> bool:
    return (role or get_service_role()) == "sandbox"
