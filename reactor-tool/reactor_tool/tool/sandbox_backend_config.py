"""Code-execution sandbox backend selection (local subprocess vs E2B cloud)."""

from __future__ import annotations

import os
import shlex
from pathlib import Path
from typing import Any, Literal

from reactor_tool.tool.e2b_file_upload import write_e2b_files

SandboxBackendName = Literal["local", "e2b"]

_DEFAULT_E2B_WORKDIR = "/home/user/workspace"
_DEFAULT_BACKEND: SandboxBackendName = "local"
_DEFAULT_FULL_PAUSE_DEBOUNCE_SEC = 3
_DEFAULT_FS_ONLY_IDLE_SEC = 1200
_DEFAULT_USER_OPERATING_HOLD_SEC = 600
_E2B_DESKTOP_CHROMIUM_COMMAND = "/usr/local/bin/ensure_desktop_chromium.sh"
_E2B_DESKTOP_CHROMIUM_STAGING_PATH = "/tmp/reactor-ensure-desktop-chromium.sh"
_E2B_DESKTOP_CHROMIUM_HEALTH_CHECK = (
    "browser_pid=$(pgrep -u user -f "
    "'^/usr/bin/chromium --no-sandbox --no-first-run "
    "--disable-dev-shm-usage .*--user-data-dir=/home/user/.opencli/chromium-visible' "
    "| head -n1); "
    'test -n "$browser_pid" && kill -0 "$browser_pid" && '
    "grep -z -q '^DISPLAY=:0$' \"/proc/$browser_pid/environ\""
)


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


def get_e2b_full_pause_debounce_sec() -> float:
    raw = (os.getenv("E2B_FULL_PAUSE_DEBOUNCE_SEC") or "").strip()
    if raw:
        return max(0.05, float(raw))
    return float(_DEFAULT_FULL_PAUSE_DEBOUNCE_SEC)


def get_e2b_fs_only_idle_sec() -> float:
    raw = (os.getenv("E2B_FS_ONLY_IDLE_SEC") or "").strip()
    if raw:
        return max(1.0, float(raw))
    return float(_DEFAULT_FS_ONLY_IDLE_SEC)


def get_e2b_user_operating_hold_sec() -> float:
    raw = (os.getenv("E2B_USER_OPERATING_HOLD_SEC") or "").strip()
    if raw:
        return max(1.0, float(raw))
    return float(_DEFAULT_USER_OPERATING_HOLD_SEC)


def get_e2b_sandbox_db_path() -> str:
    raw = (os.getenv("E2B_SANDBOX_DB_PATH") or "").strip()
    if raw:
        return raw
    return (os.getenv("SQLITE_DB_PATH") or "").strip() or "autobots.db"


def build_e2b_create_kwargs(exec_timeout_seconds: float) -> dict[str, Any]:
    """Create 参数沿用当前 template / timeout / proxy，不改沙箱规格。"""
    kwargs: dict[str, Any] = {
        "timeout": get_e2b_sandbox_timeout_seconds(exec_timeout_seconds),
        "lifecycle": {"on_timeout": "pause"},
    }
    template = get_e2b_template()
    if template:
        kwargs["template"] = template
    proxy = get_e2b_proxy()
    if proxy:
        kwargs["proxy"] = proxy
    return kwargs


def create_e2b_sandbox(
    exec_timeout_seconds: float,
    *,
    factory: Any | None = None,
    api_key: str | None = None,
) -> Any:
    kwargs = build_e2b_create_kwargs(exec_timeout_seconds)
    if factory is None:
        from e2b_code_interpreter import Sandbox

        kwargs["api_key"] = api_key or require_e2b_api_key()
        return Sandbox.create(**kwargs)
    kwargs.setdefault("api_key", api_key or "test-key")
    return factory(**kwargs)


def connect_e2b_sandbox(
    sandbox_id: str,
    *,
    timeout_sec: int | None = None,
    factory: Any | None = None,
    api_key: str | None = None,
) -> Any:
    if factory is not None:
        try:
            return factory(sandbox_id, timeout=timeout_sec)
        except TypeError:
            return factory(sandbox_id=sandbox_id, timeout=timeout_sec)
    from e2b_code_interpreter import Sandbox

    kwargs: dict[str, Any] = {"api_key": api_key or require_e2b_api_key()}
    proxy = get_e2b_proxy()
    if proxy:
        kwargs["proxy"] = proxy
    return Sandbox._cls_connect_sandbox(sandbox_id, timeout=timeout_sec, **kwargs)


def pause_e2b_sandbox(sandbox: Any, keep_memory: bool = True) -> bool:
    pause = getattr(sandbox, "pause", None)
    if not callable(pause):
        return False
    try:
        return bool(pause(keep_memory=keep_memory))
    except TypeError:
        return bool(pause())


def set_e2b_sandbox_timeout(sandbox: Any, timeout_sec: int) -> None:
    setter = getattr(sandbox, "set_timeout", None)
    if callable(setter):
        setter(timeout_sec)


def ensure_e2b_code_interpreter_ready(sandbox: Any) -> None:
    """Restart Jupyter when a resumed E2B sandbox leaves its service stopped."""
    commands = getattr(sandbox, "commands", None)
    run = getattr(commands, "run", None) if commands is not None else None
    if not callable(run):
        raise RuntimeError("E2B sandbox does not support commands.run")

    try:
        health = run(
            "curl -fsS --max-time 1 http://127.0.0.1:49999/health >/dev/null",
            timeout=5,
        )
        if getattr(health, "exit_code", 0) in (None, 0) and not getattr(
            health, "error", None
        ):
            return
    except Exception:
        pass

    command = (
        "sudo systemctl daemon-reload && sudo systemctl start jupyter && "
        "for attempt in $(seq 1 15); do "
        "if curl -fsS --max-time 1 http://127.0.0.1:49999/health >/dev/null; "
        "then exit 0; fi; sleep 1; done; "
        "echo 'Code Interpreter Jupyter service failed to start on port 49999' >&2; "
        "exit 1"
    )
    try:
        result = run(command, timeout=35)
    except Exception as exc:
        raise RuntimeError(
            f"Code Interpreter Jupyter service failed to start: {exc}"
        ) from exc
    exit_code = getattr(result, "exit_code", 0)
    if exit_code not in (None, 0):
        detail = " ".join(
            part.strip()
            for part in (
                str(getattr(result, "stdout", "") or ""),
                str(getattr(result, "stderr", "") or ""),
            )
            if part and part.strip()
        )
        raise RuntimeError(detail or "Code Interpreter Jupyter service failed to start")


def ensure_e2b_desktop_chromium_ready(sandbox: Any) -> None:
    """Ensure the shared visible Chromium profile has a headed browser."""
    commands = getattr(sandbox, "commands", None)
    run = getattr(commands, "run", None) if commands is not None else None
    if not callable(run):
        raise RuntimeError("E2B sandbox does not support commands.run")

    try:
        result = _run_desktop_chromium_launcher(sandbox, run)
    except Exception as exc:
        try:
            health = run(_E2B_DESKTOP_CHROMIUM_HEALTH_CHECK, timeout=5)
            if getattr(health, "exit_code", 0) in (None, 0) and not getattr(
                health, "error", None
            ):
                return
        except Exception:
            pass
        raise RuntimeError(f"Desktop Chromium failed to start: {exc}") from exc

    exit_code = getattr(result, "exit_code", 0)
    if exit_code in (None, 0) and not getattr(result, "error", None):
        return
    detail = " ".join(
        part.strip()
        for part in (
            str(getattr(result, "stdout", "") or ""),
            str(getattr(result, "stderr", "") or ""),
        )
        if part and part.strip()
    )
    raise RuntimeError(detail or "Desktop Chromium failed to start")


def _run_desktop_chromium_launcher(sandbox: Any, run: Any) -> Any:
    try:
        result = run(_E2B_DESKTOP_CHROMIUM_COMMAND, user="root", timeout=120)
    except Exception as exc:
        if not _is_missing_desktop_chromium_launcher(exc):
            raise
        _install_desktop_chromium_launcher(sandbox, run)
        return run(_E2B_DESKTOP_CHROMIUM_COMMAND, user="root", timeout=120)

    detail = " ".join(
        str(part or "")
        for part in (
            getattr(result, "stdout", ""),
            getattr(result, "stderr", ""),
            getattr(result, "error", ""),
        )
    )
    exit_code = getattr(result, "exit_code", 0)
    if (exit_code not in (None, 0) or getattr(result, "error", None)) and (
        _is_missing_desktop_chromium_launcher(detail)
    ):
        _install_desktop_chromium_launcher(sandbox, run)
        return run(_E2B_DESKTOP_CHROMIUM_COMMAND, user="root", timeout=120)
    return result


def _is_missing_desktop_chromium_launcher(error: Any) -> bool:
    detail = str(error or "").casefold()
    return _E2B_DESKTOP_CHROMIUM_COMMAND.casefold() in detail and (
        "no such file" in detail or "not found" in detail
    )


def _install_desktop_chromium_launcher(sandbox: Any, run: Any) -> None:
    launcher = (
        Path(__file__).resolve().parents[2]
        / "scripts"
        / "e2b_template"
        / "ensure_desktop_chromium.sh"
    )
    if not launcher.is_file():
        raise RuntimeError(
            "Desktop Chromium launcher is missing locally; rebuild the E2B template "
            "or deploy scripts/e2b_template/ensure_desktop_chromium.sh"
        )

    files_api = getattr(sandbox, "files", None)
    if files_api is None:
        raise RuntimeError(
            "E2B sandbox has no files API to restore its desktop launcher"
        )
    write_e2b_files(
        files_api,
        [
            {
                "path": _E2B_DESKTOP_CHROMIUM_STAGING_PATH,
                "data": launcher.read_bytes(),
            }
        ],
        label="e2b_desktop_chromium_bootstrap",
    )
    install = (
        f"install -o root -g root -m 0755 "
        f"{shlex.quote(_E2B_DESKTOP_CHROMIUM_STAGING_PATH)} "
        f"{shlex.quote(_E2B_DESKTOP_CHROMIUM_COMMAND)} && "
        f"rm -f {shlex.quote(_E2B_DESKTOP_CHROMIUM_STAGING_PATH)}"
    )
    result = run(install, user="root", timeout=30)
    exit_code = getattr(result, "exit_code", 0)
    if exit_code not in (None, 0) or getattr(result, "error", None):
        detail = " ".join(
            str(part or "")
            for part in (
                getattr(result, "stdout", ""),
                getattr(result, "stderr", ""),
                getattr(result, "error", ""),
            )
        )
        raise RuntimeError(detail or "Failed to restore Desktop Chromium launcher")
