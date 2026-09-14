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


_E2B_PROXY_DISABLED = frozenset({"", "0", "none", "off", "direct", "false"})


def get_e2b_proxy() -> str | None:
    """返回 E2B SDK 专用代理地址。

    E2B_PROXY 优先；未单独配置时兼容复用现有网页抓取代理。
    显式留空 / off / direct / none 表示直连，不回退到网页代理。
    返回值会直接传给 Sandbox/Template SDK，不修改进程级 HTTP_PROXY。
    """
    if "E2B_PROXY" in os.environ:
        value = os.environ.get("E2B_PROXY", "").strip()
        if value.lower() in _E2B_PROXY_DISABLED:
            return None
        return value
    fallback = (os.getenv("REACTOR_WEB_FETCH_PROXY") or "").strip()
    return fallback or None
