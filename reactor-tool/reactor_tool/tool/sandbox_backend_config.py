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


_E2B_NO_PROXY_HOSTS = ("e2b.app", ".e2b.app", "e2b.dev", ".e2b.dev")


def apply_e2b_no_proxy() -> None:
    """让 E2B API / sandbox 主机绕过 HTTP(S)_PROXY。

    e2b SDK 的 httpx 默认 trust_env=True；只设 proxy=None 仍会走环境代理。
    往 NO_PROXY 追加 e2b 域名是进程级、可并发安全的（只排除这些主机）。
    """
    for key in ("NO_PROXY", "no_proxy"):
        current = os.environ.get(key, "")
        parts = [p.strip() for p in current.split(",") if p.strip()]
        existing = set(parts)
        changed = key not in os.environ
        for host in _E2B_NO_PROXY_HOSTS:
            if host not in existing:
                parts.append(host)
                existing.add(host)
                changed = True
        if changed:
            os.environ[key] = ",".join(parts)
