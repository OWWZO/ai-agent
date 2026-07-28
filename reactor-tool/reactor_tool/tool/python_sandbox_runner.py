"""Persistent subprocess runner for code-interpreter Python actions.

The parent process communicates with this module through JSON Lines. User code
never writes protocol data directly because stdout and stderr are captured for
each execution request.
"""
from __future__ import annotations

import contextlib
import io
import json
import mimetypes
import sys
import time
import traceback
from pathlib import Path
from typing import Any

# The runner is launched as a script with ``-I`` from an arbitrary workspace.
PROJECT_ROOT = Path(__file__).resolve().parents[2]
if str(PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(PROJECT_ROOT))

from reactor_tool.tool.code_interpreter_policy import (
    CodeInterpreterPermissionPolicy,
    build_runtime_helpers,
)
from reactor_tool.tool.code_interpreter_runtime_guard import activate_runtime_io_guard


def _policy_from_payload(payload: dict[str, Any]) -> CodeInterpreterPermissionPolicy:
    return CodeInterpreterPermissionPolicy(
        profile=payload["profile"],
        workspace_root=payload["workspace_root"],
        output_dir=payload["output_dir"],
        input_file_paths=dict(payload.get("input_file_paths") or {}),
        allowed_read_paths=tuple(payload.get("allowed_read_paths") or ()),
        allowed_read_roots=tuple(payload.get("allowed_read_roots") or ()),
        allowed_write_roots=tuple(payload.get("allowed_write_roots") or ()),
        authorized_imports=tuple(payload.get("authorized_imports") or ()),
    )


def _snapshot_output_files(output_dir: Path) -> dict[Path, tuple[int, int]]:
    if not output_dir.is_dir():
        return {}
    files: dict[Path, tuple[int, int]] = {}
    for path in output_dir.rglob("*"):
        if path.is_symlink() or not path.is_file() or any(part.startswith(".") for part in path.relative_to(output_dir).parts):
            continue
        stat = path.stat()
        files[path.resolve()] = (stat.st_size, stat.st_mtime_ns)
    return files


def _produced_files(output_dir: Path,
                    before: dict[Path, tuple[int, int]],
                    after: dict[Path, tuple[int, int]]) -> list[dict[str, Any]]:
    produced: list[dict[str, Any]] = []
    for path in sorted(after, key=lambda item: str(item).lower()):
        if after[path] == before.get(path):
            continue
        mime_type, _ = mimetypes.guess_type(path.name)
        produced.append({
            "file_path": str(path),
            "relative_path": path.relative_to(output_dir).as_posix(),
            "name": path.name,
            "size": after[path][0],
            "mime_type": mime_type or "application/octet-stream",
        })
    return produced


def _write_response(response: dict[str, Any]) -> None:
    sys.__stdout__.write(json.dumps(response, ensure_ascii=False) + "\n")
    sys.__stdout__.flush()


def _truncate(value: str, limit: int = 200_000) -> tuple[str, bool]:
    if len(value) <= limit:
        return value, False
    return value[:limit] + "\n...[truncated]", True


def _safe_json(value: Any) -> Any:
    try:
        json.dumps(value)
        return value
    except (TypeError, ValueError):
        return str(value)


def main() -> int:
    policy: CodeInterpreterPermissionPolicy | None = None
    output_dir: Path | None = None
    globals_env: dict[str, Any] | None = None

    for raw_line in sys.stdin:
        try:
            request = json.loads(raw_line)
            request_type = request.get("type")
            if request_type == "init":
                policy = _policy_from_payload(request["policy"])
                output_dir = Path(policy.output_dir).resolve()
                output_dir.mkdir(parents=True, exist_ok=True)
                globals_env = {"__name__": "__sandbox__"}
                globals_env.update(policy.to_runtime_variables())
                globals_env.update(build_runtime_helpers(policy))
                globals_env.update(dict(request.get("initial_variables") or {}))
                _write_response({"type": "ready"})
                continue
            if request_type == "close":
                _write_response({"type": "closed"})
                return 0
            if request_type != "execute" or policy is None or output_dir is None or globals_env is None:
                raise ValueError("runner is not initialized")

            before = _snapshot_output_files(output_dir)
            started_at = time.monotonic()
            stdout = io.StringIO()
            stderr = io.StringIO()
            status = "success"
            error = None
            try:
                with contextlib.redirect_stdout(stdout), contextlib.redirect_stderr(stderr):
                    with activate_runtime_io_guard(policy):
                        exec(compile(str(request.get("code") or ""), "<code_interpreter>", "exec"), globals_env)
            except BaseException as exc:
                status = "error"
                error = str(exc)
                stderr.write(traceback.format_exc())
            after = _snapshot_output_files(output_dir)
            stdout_text, stdout_truncated = _truncate(stdout.getvalue())
            stderr_text, stderr_truncated = _truncate(stderr.getvalue())
            _write_response({
                "type": "result",
                "status": status,
                "stdout": stdout_text,
                "stderr": stderr_text,
                "stdout_truncated": stdout_truncated,
                "stderr_truncated": stderr_truncated,
                "error": error,
                "result": _safe_json(globals_env.get("result")),
                "duration_ms": int((time.monotonic() - started_at) * 1000),
                "returncode": 0 if status == "success" else 1,
                "produced_files": _produced_files(output_dir, before, after),
            })
        except BaseException as exc:
            _write_response({
                "type": "result",
                "status": "crash",
                "stdout": "",
                "stderr": traceback.format_exc(),
                "error": str(exc),
                "produced_files": [],
            })
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
