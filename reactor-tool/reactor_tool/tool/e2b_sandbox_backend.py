"""E2B cloud sandbox backend for code_interpreter / code_execution.

Flow: create sandbox → upload local workspace → run_code (persistent kernel) →
diff remote files → download produced files to local workspace → kill sandbox.
"""
from __future__ import annotations

import base64
import json
import mimetypes
import time
from pathlib import Path
from typing import Any

from reactor_tool.tool.code_interpreter_policy import CodeInterpreterPermissionPolicy
from reactor_tool.tool.python_sandbox_executor import (
    PythonSandboxExecutionError,
    PythonSandboxExecutionResult,
)
from reactor_tool.tool.sandbox_backend_config import (
    get_e2b_sandbox_timeout_seconds,
    get_e2b_template,
    get_e2b_workdir,
    require_e2b_api_key,
)

_EXCLUDED_TOP_DIRS = frozenset({"input"})
_EXCLUDED_FILE_NAMES = frozenset({"__last_source__.py"})
_SNAPSHOT_SCRIPT = r"""
def __reactor_sandbox_snapshot__():
    import json
    from pathlib import Path
    root = Path(%(workspace_root)s)
    exclude_tops = set(%(exclude_tops)s)
    exclude_names = set(%(exclude_names)s)
    files = {}
    if root.is_dir():
        for path in root.rglob("*"):
            if not path.is_file() or path.is_symlink():
                continue
            try:
                rel = path.resolve().relative_to(root.resolve())
            except ValueError:
                continue
            parts = rel.parts
            if not parts or any(part.startswith(".") for part in parts):
                continue
            if parts[0] in exclude_tops:
                continue
            if rel.name in exclude_names or rel.name.startswith("__last_source__"):
                continue
            st = path.stat()
            files[rel.as_posix()] = [int(st.st_size), int(st.st_mtime_ns)]
    print("__SANDBOX_SNAPSHOT__" + json.dumps(files, ensure_ascii=True))

__reactor_sandbox_snapshot__()
del __reactor_sandbox_snapshot__
"""


class E2BPythonSandboxExecutor:
    """Persistent E2B sandbox session with the same surface as the local executor."""

    def __init__(
        self,
        policy: CodeInterpreterPermissionPolicy,
        timeout_seconds: float = 120.0,
        initial_variables: dict[str, Any] | None = None,
        *,
        sandbox_factory: Any | None = None,
    ):
        self._policy = policy
        self._timeout_seconds = float(timeout_seconds)
        self._initial_variables = dict(initial_variables or {})
        self._sandbox_factory = sandbox_factory
        self._sandbox: Any | None = None
        self._bootstrapped = False
        self._started = False
        self._produced_by_path: dict[str, dict[str, Any]] = {}
        self._remote_workspace = get_e2b_workdir()
        self._local_workspace = Path(policy.workspace_root).resolve()
        self._local_output = Path(policy.output_dir).resolve()
        self._remote_output = self._to_remote(self._local_output)
        self._remote_input_map = {
            name: self._to_remote(Path(path))
            for name, path in policy.input_file_paths.items()
        }

    def execute(self, code: str, source_file: str | None = None) -> PythonSandboxExecutionResult:
        self._ensure_started()
        assert self._sandbox is not None
        self._sync_workspace_to_remote()
        before = self._snapshot_remote_files()
        started_at = time.monotonic()
        wrapped = self._wrap_user_code(
            code,
            include_bootstrap=not self._bootstrapped,
            source_file=source_file,
        )
        self._bootstrapped = True
        try:
            execution = self._sandbox.run_code(
                wrapped,
                timeout=max(1, int(self._timeout_seconds)),
            )
        except Exception as exc:
            # Surface timeout-like failures consistently for callers.
            message = str(exc).lower()
            if "timeout" in message or "timed out" in message:
                raise TimeoutError(
                    f"Python sandbox exceeded {self._timeout_seconds:.0f}s"
                ) from exc
            raise

        duration_ms = int((time.monotonic() - started_at) * 1000)
        stdout, stderr = _extract_logs(execution)
        error_obj = getattr(execution, "error", None)
        result_value = _extract_text_result(execution)
        chart_files = self._materialize_chart_results(execution)

        after = self._snapshot_remote_files()
        produced = self._download_produced_files(before, after)
        for item in chart_files:
            produced.append(item)
            self._produced_by_path[item["file_path"]] = item
        for item in produced:
            path = str(item.get("file_path") or "")
            if path:
                self._produced_by_path[path] = item

        if error_obj is not None:
            error_text = _format_execution_error(error_obj)
            if stderr:
                stderr = f"{stderr}\n{error_text}".strip()
            else:
                stderr = error_text
            raise PythonSandboxExecutionError(
                {
                    "stdout": stdout,
                    "stderr": stderr,
                    "error": error_text,
                    "produced_files": produced,
                    "result": result_value,
                    "duration_ms": duration_ms,
                    "returncode": 1,
                }
            )

        return PythonSandboxExecutionResult(
            stdout=stdout,
            stderr=stderr,
            produced_files=produced,
            result=result_value,
            duration_ms=duration_ms,
            stdout_truncated=False,
            stderr_truncated=False,
            returncode=0,
        )

    def produced_files(self) -> list[dict[str, Any]]:
        return list(self._produced_by_path.values())

    def close(self) -> None:
        sandbox = self._sandbox
        self._sandbox = None
        self._started = False
        self._bootstrapped = False
        if sandbox is None:
            return
        kill = getattr(sandbox, "kill", None)
        if callable(kill):
            try:
                kill()
            except Exception:
                pass

    def _ensure_started(self) -> None:
        if self._started and self._sandbox is not None:
            return
        create_kwargs: dict[str, Any] = {
            "timeout": get_e2b_sandbox_timeout_seconds(self._timeout_seconds),
        }
        template = get_e2b_template()
        if template:
            create_kwargs["template"] = template

        factory = self._sandbox_factory
        if factory is None:
            from e2b_code_interpreter import Sandbox

            create_kwargs["api_key"] = require_e2b_api_key()
            factory = Sandbox.create
        else:
            # Injected factory (unit tests): do not require a real E2B_API_KEY.
            create_kwargs.setdefault("api_key", "test-key")

        self._sandbox = factory(**create_kwargs)
        self._started = True
        self._prepare_remote_layout()
        self._sync_workspace_to_remote()

    def _prepare_remote_layout(self) -> None:
        assert self._sandbox is not None
        # Ensure workspace / output / input exist before first upload.
        mkdir_script = (
            "from pathlib import Path\n"
            f"Path({self._remote_workspace!r}).mkdir(parents=True, exist_ok=True)\n"
            f"Path({self._remote_output!r}).mkdir(parents=True, exist_ok=True)\n"
            f"Path({self._remote_workspace!r}, 'input').mkdir(parents=True, exist_ok=True)\n"
        )
        self._sandbox.run_code(mkdir_script, timeout=30)

    def _to_remote(self, local_path: Path) -> str:
        local_resolved = local_path.resolve()
        try:
            relative = local_resolved.relative_to(self._local_workspace)
        except ValueError:
            # Outside workspace: place under remote workspace by name only.
            return f"{self._remote_workspace}/{local_resolved.name}"
        remote = f"{self._remote_workspace}/{relative.as_posix()}".rstrip("/")
        return remote if remote else self._remote_workspace

    def _to_local(self, remote_relative: str) -> Path:
        rel = remote_relative.replace("\\", "/").lstrip("/")
        return (self._local_workspace / rel).resolve()

    def _sync_workspace_to_remote(self) -> None:
        assert self._sandbox is not None
        if not self._local_workspace.is_dir():
            return
        batch: list[dict[str, Any]] = []
        for path in self._local_workspace.rglob("*"):
            if not path.is_file() or path.is_symlink():
                continue
            try:
                relative = path.resolve().relative_to(self._local_workspace)
            except ValueError:
                continue
            if any(part.startswith(".") for part in relative.parts):
                continue
            remote_path = f"{self._remote_workspace}/{relative.as_posix()}"
            batch.append({"path": remote_path, "data": path.read_bytes()})
            if len(batch) >= 32:
                self._write_files(batch)
                batch = []
        if batch:
            self._write_files(batch)

    def _write_files(self, files: list[dict[str, Any]]) -> None:
        assert self._sandbox is not None
        write_files = getattr(self._sandbox.files, "write_files", None)
        if callable(write_files):
            write_files(files)
            return
        for item in files:
            self._sandbox.files.write(item["path"], item["data"])

    def _snapshot_remote_files(self) -> dict[str, tuple[int, int]]:
        assert self._sandbox is not None
        script = _SNAPSHOT_SCRIPT % {
            "workspace_root": repr(self._remote_workspace),
            "exclude_tops": repr(sorted(_EXCLUDED_TOP_DIRS)),
            "exclude_names": repr(sorted(_EXCLUDED_FILE_NAMES)),
        }
        execution = self._sandbox.run_code(script, timeout=60)
        stdout, _ = _extract_logs(execution)
        marker = "__SANDBOX_SNAPSHOT__"
        for line in reversed(stdout.splitlines()):
            if marker in line:
                payload = line.split(marker, 1)[1].strip()
                raw = json.loads(payload or "{}")
                return {
                    str(key).replace("\\", "/"): (int(value[0]), int(value[1]))
                    for key, value in raw.items()
                }
        return {}

    def _download_produced_files(
        self,
        before: dict[str, tuple[int, int]],
        after: dict[str, tuple[int, int]],
    ) -> list[dict[str, Any]]:
        assert self._sandbox is not None
        produced: list[dict[str, Any]] = []
        for rel, meta in sorted(after.items(), key=lambda item: item[0].lower()):
            if meta == before.get(rel):
                continue
            remote_path = f"{self._remote_workspace}/{rel}"
            try:
                content = self._sandbox.files.read(remote_path, format="bytes")
            except TypeError:
                content = self._sandbox.files.read(remote_path)
            if isinstance(content, str):
                data = content.encode("utf-8")
            elif isinstance(content, (bytes, bytearray)):
                data = bytes(content)
            else:
                data = bytes(content)
            local_path = self._to_local(rel)
            local_path.parent.mkdir(parents=True, exist_ok=True)
            local_path.write_bytes(data)
            mime_type, _ = mimetypes.guess_type(local_path.name)
            produced.append(
                {
                    "file_path": str(local_path),
                    "relative_path": rel,
                    "name": local_path.name,
                    "size": len(data),
                    "mime_type": mime_type or "application/octet-stream",
                }
            )
        return produced

    def _materialize_chart_results(self, execution: Any) -> list[dict[str, Any]]:
        results = list(getattr(execution, "results", None) or [])
        produced: list[dict[str, Any]] = []
        chart_index = 0
        self._local_output.mkdir(parents=True, exist_ok=True)
        for item in results:
            png = getattr(item, "png", None)
            if not png:
                continue
            name = f"e2b_chart_{chart_index}.png"
            local_path = self._local_output / name
            local_path.write_bytes(base64.b64decode(png))
            produced.append(
                {
                    "file_path": str(local_path),
                    "relative_path": local_path.relative_to(self._local_workspace).as_posix(),
                    "name": name,
                    "size": local_path.stat().st_size,
                    "mime_type": "image/png",
                }
            )
            chart_index += 1
        return produced

    def _wrap_user_code(
        self,
        code: str,
        *,
        include_bootstrap: bool,
        source_file: str | None = None,
    ) -> str:
        # Kernel is persistent: inject path helpers + variables on first execute only,
        # then always chdir to remote output so relative writes match local behavior.
        remote_inputs = {
            name: self._remote_input_map.get(name, path)
            for name, path in self._policy.input_file_paths.items()
        }
        remote_source = self._resolve_remote_source_file(source_file)
        bootstrap = ""
        if include_bootstrap:
            bootstrap = f"""
import os
from pathlib import Path

workspace_root = {self._remote_workspace!r}
output_dir = {self._remote_output!r}
input_file_paths = {json.dumps(remote_inputs, ensure_ascii=True)}
permission_profile = {self._policy.profile!r}
input_files = [{{"name": n, "path": p}} for n, p in input_file_paths.items()]
Path(workspace_root).mkdir(parents=True, exist_ok=True)
Path(output_dir).mkdir(parents=True, exist_ok=True)
Path(workspace_root, "input").mkdir(parents=True, exist_ok=True)
os.chdir(output_dir)

def build_output_path(file_name: str) -> str:
    target = Path(output_dir).joinpath(file_name)
    target.parent.mkdir(parents=True, exist_ok=True)
    return str(target)

def resolve_input_path(file_name: str) -> str:
    key = (file_name or "").strip()
    if key not in input_file_paths:
        raise FileNotFoundError(f"未找到输入文件：{{key}}")
    return input_file_paths[key]

def read_text_file(file_path: str, encoding: str = "utf-8") -> str:
    return Path(file_path).read_text(encoding=encoding)

def write_text_file(file_path: str, content: str, encoding: str = "utf-8") -> str:
    target = Path(file_path)
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(content, encoding=encoding)
    return str(target)

def build_workspace_path(relative_path: str) -> str:
    if permission_profile != "workspace":
        raise PermissionError("当前权限档位不允许构建工作区任意路径，请改用 build_output_path().")
    target = Path(workspace_root).joinpath(relative_path)
    target.parent.mkdir(parents=True, exist_ok=True)
    return str(target)

"""
            for key, value in self._initial_variables.items():
                if not str(key).isidentifier():
                    continue
                try:
                    bootstrap += f"{key} = {json.dumps(value, ensure_ascii=True)}\n"
                except (TypeError, ValueError):
                    bootstrap += f"{key} = {repr(str(value))}\n"
        else:
            bootstrap = f"import os\nos.chdir({self._remote_output!r})\n"

        # Align with ``python script.py``: inject script metadata before user code.
        meta = (
            f"__name__ = '__main__'\n"
            f"__file__ = {remote_source!r}\n"
        )
        return f"{bootstrap}\n{meta}\n{code}\n"

    def _resolve_remote_source_file(self, source_file: str | None) -> str:
        if source_file and str(source_file).strip():
            local = Path(str(source_file).strip())
            try:
                if not local.is_absolute():
                    local = (self._local_workspace / local).resolve()
                else:
                    local = local.resolve()
                return self._to_remote(local)
            except Exception:
                pass
        return f"{self._remote_workspace}/__code_execution__.py"


def _extract_logs(execution: Any) -> tuple[str, str]:
    logs = getattr(execution, "logs", None)
    stdout_parts = list(getattr(logs, "stdout", None) or [])
    stderr_parts = list(getattr(logs, "stderr", None) or [])
    stdout = "\n".join(_message_text(part) for part in stdout_parts)
    stderr = "\n".join(_message_text(part) for part in stderr_parts)
    # Some SDK versions also expose text fields.
    if not stdout and getattr(execution, "text", None):
        stdout = str(execution.text)
    return _sanitize_text(stdout), _sanitize_text(stderr)


def _message_text(part: Any) -> str:
    if part is None:
        return ""
    if isinstance(part, str):
        return part
    for attr in ("line", "text", "content", "message"):
        value = getattr(part, attr, None)
        if value:
            return str(value)
    return str(part)


def _extract_text_result(execution: Any) -> Any:
    results = list(getattr(execution, "results", None) or [])
    for item in results:
        text = getattr(item, "text", None)
        if text:
            return text
        raw = getattr(item, "json", None)
        if raw is not None:
            return raw
    return None


def _format_execution_error(error_obj: Any) -> str:
    name = getattr(error_obj, "name", None) or type(error_obj).__name__
    value = getattr(error_obj, "value", None) or str(error_obj)
    traceback = getattr(error_obj, "traceback", None) or ""
    parts = [f"{name}: {value}".strip(": ")]
    if traceback:
        parts.append(str(traceback))
    return _sanitize_text("\n".join(parts))


def _sanitize_text(value: str) -> str:
    if not value:
        return value
    return value.encode("utf-8", errors="replace").decode("utf-8")
