"""Shared helpers for authenticated platform tools.

Credentials are read from the reactor-tool process environment only. This
module deliberately never writes credentials to disk or includes them in
errors returned to the agent.
"""

from __future__ import annotations

import datetime as dt
import json
import os
import re
import shutil
import subprocess
import sys
from typing import Any, Iterable

import yaml


MAX_OUTPUT_CHARS = 48_000
MAX_COMMAND_OUTPUT_CHARS = 4 * 1024 * 1024
SECRET_PATTERN = re.compile(
    r"(?i)(auth_token|ct0|reddit_session|xq_a_token|xq_id_token|cookie)"
    r"(\s*[:=]\s*)([^;\s,}\"']+)"
)


class SocialPlatformError(RuntimeError):
    """Expected platform failure safe to expose at the tool boundary."""

    def __init__(self, code: str, message: str):
        self.code = code
        self.message = scrub_text(message)
        super().__init__(self.message)


def utc_now() -> str:
    return dt.datetime.now(dt.timezone.utc).isoformat().replace("+00:00", "Z")


def scrub_text(value: Any, secrets: Iterable[str] = ()) -> str:
    """Remove credential-shaped values and cap text before it reaches logs/LLM."""
    text = "" if value is None else str(value)
    for secret in secrets:
        if secret:
            text = text.replace(secret, "[redacted]")
    text = SECRET_PATTERN.sub(r"\1=[redacted]", text)
    text = re.sub(r"\s+", " ", text).strip()
    return text[:2_000]


def require_secret(env_name: str) -> str:
    value = os.getenv(env_name, "").strip()
    if not value:
        raise SocialPlatformError("AUTH_REQUIRED", f"{env_name} is not configured")
    return value


def bounded_text(value: Any, limit: int = 4_000) -> str:
    text = "" if value is None else str(value)
    text = re.sub(r"\s+", " ", text).strip()
    return text[:limit]


def bounded_limit(value: Any, default: int = 10, maximum: int = 50) -> int:
    try:
        parsed = int(value)
    except (TypeError, ValueError):
        parsed = default
    return max(1, min(parsed, maximum))


def success_result(
    platform: str,
    operation: str,
    items: list[dict[str, Any]],
    *,
    warnings: list[str] | None = None,
    **extra: Any,
) -> dict[str, Any]:
    result: dict[str, Any] = {
        "ok": True,
        "platform": platform,
        "operation": operation,
        "items": items,
        "warnings": [scrub_text(item) for item in (warnings or [])],
        "retrieved_at": utc_now(),
    }
    result.update(extra)
    return _bound_result(result)


def _bound_result(result: dict[str, Any]) -> dict[str, Any]:
    """Keep platform output bounded even when an upstream detail is unusually large."""
    encoded = json.dumps(result, ensure_ascii=False, default=str)
    if len(encoded) <= MAX_OUTPUT_CHARS:
        return result

    result.setdefault("warnings", []).append("result was shortened to stay within the output limit")
    items = result.get("items")
    if isinstance(items, list):
        for item in items:
            if not isinstance(item, dict):
                continue
            for key, value in list(item.items()):
                if isinstance(value, str) and len(value) > 2_000:
                    item[key] = value[:2_000] + "...[truncated]"
                elif isinstance(value, list) and len(value) > 20:
                    item[key] = value[:20]
        while (
            len(items) > 1
            and len(json.dumps(result, ensure_ascii=False, default=str)) > MAX_OUTPUT_CHARS
        ):
            items.pop()
        if len(json.dumps(result, ensure_ascii=False, default=str)) > MAX_OUTPUT_CHARS:
            result["items"] = [
                {"truncated": True, "message": "platform result exceeded the output limit"}
            ]
    return result


def error_result(platform: str, operation: str, error: SocialPlatformError) -> dict[str, Any]:
    return {
        "ok": False,
        "platform": platform,
        "operation": operation,
        "items": [],
        "warnings": [],
        "retrieved_at": utc_now(),
        "error": {"code": error.code, "message": scrub_text(error.message)},
    }


def command_output(
    command: list[str],
    *,
    env: dict[str, str],
    timeout_seconds: int = 90,
    secrets: Iterable[str] = (),
) -> str:
    """Run one upstream CLI command without invoking a shell."""
    executable = shutil.which(command[0])
    if not executable:
        raise SocialPlatformError(
            "DEPENDENCY_MISSING", f"required command is not installed: {command[0]}"
        )
    try:
        completed = subprocess.run(
            [executable, *command[1:]],
            stdin=subprocess.DEVNULL,
            capture_output=True,
            text=True,
            encoding="utf-8",
            errors="replace",
            timeout=timeout_seconds,
            check=False,
            env=env,
        )
    except subprocess.TimeoutExpired as exc:
        raise SocialPlatformError("TIMEOUT", f"{command[0]} timed out") from exc
    except OSError as exc:
        raise SocialPlatformError("UPSTREAM_ERROR", f"{command[0]} could not start") from exc

    stdout = (completed.stdout or "")[:MAX_COMMAND_OUTPUT_CHARS]
    stderr = (completed.stderr or "")[:MAX_COMMAND_OUTPUT_CHARS]
    if completed.returncode != 0:
        detail = scrub_text(stderr or stdout or "command failed", secrets)
        lower = detail.casefold()
        if any(marker in lower for marker in ("timed out", "curl: (28)", "timeout")):
            code = "NETWORK_TIMEOUT"
        elif any(
            marker in lower
            for marker in ("401", "403", "not authenticated", "unauthorized", "cookie")
        ):
            code = "AUTH_REQUIRED"
        else:
            code = "UPSTREAM_ERROR"
        raise SocialPlatformError(code, f"{command[0]} failed: {detail}")
    return stdout.strip()


def parse_structured_output(text: str) -> Any:
    """Parse explicit JSON first, with YAML fallback for older CLI versions."""
    if not text.strip():
        raise SocialPlatformError("EMPTY_RESPONSE", "upstream command returned no data")
    try:
        return json.loads(text)
    except json.JSONDecodeError:
        pass

    # Some CLI versions print a short diagnostic before the structured payload.
    for line in reversed(text.splitlines()):
        candidate = line.strip()
        if not candidate or candidate[0] not in "[{":
            continue
        try:
            return json.loads(candidate)
        except json.JSONDecodeError:
            continue

    try:
        parsed = yaml.safe_load(text)
    except yaml.YAMLError as exc:
        raise SocialPlatformError(
            "INVALID_RESPONSE", "upstream command returned invalid data"
        ) from exc
    if parsed is None:
        raise SocialPlatformError("EMPTY_RESPONSE", "upstream command returned no data")
    return parsed


def unwrap_cli_payload(payload: Any) -> Any:
    """Unwrap the shared structured-output envelope used by public CLIs."""
    if not isinstance(payload, dict):
        return payload
    if payload.get("ok") is False:
        error = payload.get("error")
        if isinstance(error, dict):
            code = str(error.get("code") or "UPSTREAM_ERROR")
            message = str(error.get("message") or "upstream command failed")
        else:
            code = "UPSTREAM_ERROR"
            message = str(error or "upstream command failed")
        raise SocialPlatformError(code, message)
    if payload.get("ok") is True and "data" in payload:
        return payload.get("data")
    return payload


def first_value(mapping: dict[str, Any], *keys: str) -> Any:
    for key in keys:
        value = mapping.get(key)
        if value not in (None, ""):
            return value
    return ""


def as_list(payload: Any, *keys: str) -> list[Any]:
    if isinstance(payload, list):
        return payload
    if isinstance(payload, dict):
        for key in keys:
            value = payload.get(key)
            if isinstance(value, list):
                return value
    return []


def configure_utf8_streams() -> None:
    """Keep diagnostic output safe on Windows terminals when called directly."""
    if sys.platform != "win32":
        return
    for stream in (sys.stdout, sys.stderr):
        reconfigure = getattr(stream, "reconfigure", None)
        if reconfigure:
            try:
                reconfigure(encoding="utf-8", errors="replace")
            except Exception:
                pass
