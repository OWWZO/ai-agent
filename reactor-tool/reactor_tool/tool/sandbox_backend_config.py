"""Code-execution sandbox backend selection (local subprocess vs E2B cloud)."""
from __future__ import annotations

import os
from typing import Literal

SandboxBackendName = Literal["local", "e2b"]

_DEFAULT_E2B_WORKDIR = "/home/user/workspace"
_DEFAULT_BACKEND: SandboxBackendName = "local"


def get_sandbox_backend() -> SandboxBackendName:
    raw = (os.getenv("CODE_SANDBOX_BACKEND") or _DEFAULT_BACKEND).strip().lower()
    if raw in {"local", "e2b"}:
        return raw  # type: ignore[return-value]
    raise ValueError(
        f"Unsupported CODE_SANDBOX_BACKEND={raw!r}; expected 'local' or 'e2b'"
    )


def require_e2b_api_key() -> str:
    api_key = (os.getenv("E2B_API_KEY") or "").strip()
    if not api_key:
        raise RuntimeError(
            "CODE_SANDBOX_BACKEND=e2b 但未配置 E2B_API_KEY；"
            "生产环境请配置密钥，本地调试可设 CODE_SANDBOX_BACKEND=local"
        )
    return api_key


def get_e2b_template() -> str | None:
    value = (os.getenv("E2B_TEMPLATE") or "").strip()
    return value or None


def get_e2b_workdir() -> str:
    value = (os.getenv("E2B_WORKDIR") or _DEFAULT_E2B_WORKDIR).strip()
    return value.rstrip("/") or _DEFAULT_E2B_WORKDIR


def get_e2b_sandbox_timeout_seconds(exec_timeout_seconds: float) -> int:
    """Sandbox lifetime (seconds). Must outlive a single exec timeout."""
    raw = (os.getenv("E2B_TIMEOUT_SEC") or "").strip()
    if raw:
        return max(60, int(raw))
    return max(300, int(exec_timeout_seconds) + 120)
