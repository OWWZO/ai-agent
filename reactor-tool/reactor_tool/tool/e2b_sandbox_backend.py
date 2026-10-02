"""E2B cloud sandbox backend for code_interpreter / code_execution.

Flow: acquire a user sandbox → incrementally sync the session workspace →
run_code (persistent kernel) → download produced files → release the lease.
"""

from __future__ import annotations

import base64
import json
import mimetypes
import os
import tempfile
import time
from pathlib import Path
from typing import Any

from reactor_tool.tool.code_interpreter_policy import CodeInterpreterPermissionPolicy
from reactor_tool.tool.e2b_session_sync import (
    download_changed_files,
    mkdir_remote,
    push_workspace,
    record_uploaded,
    session_remote_root,
    snapshot_remote_files,
)
from reactor_tool.tool.python_sandbox_executor import (
    PythonSandboxExecutionError,
    PythonSandboxExecutionResult,
)
from reactor_tool.tool.sandbox_backend_config import (
    ensure_e2b_code_interpreter_ready,
    require_e2b_api_key,
)
from reactor_tool.tool.user_sandbox_manager import (
    UserSandboxManager,
    get_user_sandbox_manager,
)


class E2BPythonSandboxExecutor:
    """Persistent E2B sandbox session with the same surface as the local executor."""

    def __init__(
        self,
        policy: CodeInterpreterPermissionPolicy,
        timeout_seconds: float = 120.0,
        initial_variables: dict[str, Any] | None = None,
        *,
        sandbox_factory: Any | None = None,
        owner_key: str | None = None,
        session_id: str | None = None,
        manager: UserSandboxManager | None = None,
    ):
        self._policy = policy
        self._timeout_seconds = float(timeout_seconds)
        self._initial_variables = dict(initial_variables or {})
        self._sandbox_factory = sandbox_factory
        self._owner_key = (owner_key or "").strip() or "visitor:anonymous"
        self._session_id = (session_id or "").strip() or "anonymous"
        self._manager_injected = manager is not None
        self._local_workspace = Path(policy.workspace_root).resolve()
        self._local_output = Path(policy.output_dir).resolve()
        self._remote_workspace = session_remote_root(self._session_id)
        self._remote_output = self._to_remote(self._local_output)
        self._remote_input_map = {
            name: self._to_remote(Path(path))
            for name, path in policy.input_file_paths.items()
        }
        self._manager = (
            manager if manager is not None else self._build_manager(sandbox_factory)
        )
        self._sandbox: Any | None = None
        self._lease: Any | None = None
        self._bootstrapped = False
        self._started = False
        self._produced_by_path: dict[str, dict[str, Any]] = {}
        self._sandbox_id = ""
        self._generation = 0

    def execute(
        self, code: str, source_file: str | None = None
    ) -> PythonSandboxExecutionResult:
        self._ensure_started()
        assert self._sandbox is not None
        with self._manager.session_gate(self._owner_key, self._session_id):
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

            produced = self._download_produced_files(before)
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
        lease = self._lease
        self._lease = None
        self._sandbox = None
        self._started = False
        self._bootstrapped = False
        self._sandbox_id = ""
        self._generation = 0
        if lease is None:
            return
        self._manager.release(self._owner_key)

    def _ensure_started(self) -> None:
        if self._started and self._sandbox is not None:
            return
        if self._sandbox_factory is None and not self._manager_injected:
            require_e2b_api_key()
        lease = self._manager.acquire(
            self._owner_key, max(1, int(self._timeout_seconds))
        )
        try:
            ensure_e2b_code_interpreter_ready(lease.sandbox)
        except Exception:
            self._manager.release(self._owner_key)
            raise
        self._lease = lease
        self._sandbox = lease.sandbox
        self._sandbox_id = lease.sandbox_id
        self._generation = lease.generation
        self._remote_workspace = session_remote_root(self._session_id)
        self._started = True
        self._prepare_remote_layout()
        self._sync_workspace_to_remote()

    def _prepare_remote_layout(self) -> None:
        assert self._sandbox is not None
        mkdir_remote(self._sandbox, self._remote_workspace)

    def _build_manager(self, sandbox_factory: Any | None) -> UserSandboxManager:
        if sandbox_factory is None:
            return get_user_sandbox_manager()

        reactor_dir = self._local_workspace / ".reactor"
        reactor_dir.mkdir(parents=True, exist_ok=True)
        fd, db_path = tempfile.mkstemp(
            prefix="e2b-sandbox-",
            suffix=".sqlite",
            dir=str(reactor_dir),
        )
        os.close(fd)

        def create_for_test(*_args: Any, **kwargs: Any) -> Any:
            return sandbox_factory(**kwargs)

        return UserSandboxManager(
            db_path=db_path,
            create_fn=create_for_test,
            start_reaper=False,
        )

    def _to_remote(self, local_path: Path) -> str:
        local_resolved = local_path.resolve()
        try:
            relative = local_resolved.relative_to(self._local_workspace)
        except ValueError:
            # Outside workspace: place under remote workspace by name only.
            return f"{self._remote_workspace}/{local_resolved.name}"
        remote = f"{self._remote_workspace}/{relative.as_posix()}".rstrip("/")
        return remote if remote else self._remote_workspace

    def _sync_workspace_to_remote(self) -> None:
        assert self._sandbox is not None
        push_workspace(
            self._sandbox,
            self._local_workspace,
            self._session_id,
            owner_key=self._owner_key,
            sandbox_id=self._sandbox_id,
            generation=self._generation,
        )

    def _snapshot_remote_files(self) -> dict[str, tuple[int, int]]:
        assert self._sandbox is not None
        return snapshot_remote_files(
            self._sandbox,
            self._remote_workspace,
            skip_top=("input",),
        )

    def _download_produced_files(
        self,
        before: dict[str, tuple[int, int]],
    ) -> list[dict[str, Any]]:
        assert self._sandbox is not None
        downloaded: list[Path] = []
        download_changed_files(
            self._sandbox,
            self._remote_workspace,
            self._local_workspace,
            before,
            produced_paths=downloaded,
            skip_top=("input", "skills"),
        )
        produced: list[dict[str, Any]] = []
        for local_path in downloaded:
            try:
                rel = local_path.resolve().relative_to(self._local_workspace).as_posix()
            except ValueError:
                continue
            data = local_path.read_bytes()
            record_uploaded(
                self._local_workspace,
                rel,
                local_path,
                owner_key=self._owner_key,
                sandbox_id=self._sandbox_id,
                generation=self._generation,
            )
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
                    "relative_path": local_path.relative_to(
                        self._local_workspace
                    ).as_posix(),
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
        # Kernel is persistent: inject path helpers + variables on first execute only.
        # cwd is always the current session workspace (bash-aligned).
        remote_inputs = {
            name: self._remote_input_map.get(name, path)
            for name, path in self._policy.input_file_paths.items()
        }
        remote_source = self._resolve_remote_source_file(source_file)
        remote_cwd = self._remote_workspace
        bootstrap = ""
        if include_bootstrap:
            analysis_helpers = ""
            if self._policy.profile != "workspace":
                analysis_helpers = """
def build_output_path(file_name: str) -> str:
    target = Path(output_dir).joinpath(file_name)
    target.parent.mkdir(parents=True, exist_ok=True)
    return str(target)

def resolve_input_path(file_name: str) -> str:
    key = (file_name or "").strip().replace("\\\\", "/")
    if not key:
        raise FileNotFoundError("文件路径不能为空")
    if key in input_file_paths:
        return input_file_paths[key]
    base = Path(key).name
    if base in input_file_paths:
        return input_file_paths[base]
    root = Path(workspace_root)
    exact = root.joinpath(key)
    if exact.is_file():
        return str(exact)
    staged = root.joinpath("input", base)
    if staged.is_file():
        return str(staged)
    skip = {".git", ".venv", "venv", "node_modules", "__pycache__"}
    hits = []
    for path in root.rglob(base):
        if not path.is_file():
            continue
        rel = path.relative_to(root)
        if any(part in skip or part.startswith(".") for part in rel.parts[:-1]):
            continue
        hits.append(path)
    if len(hits) == 1:
        return str(hits[0])
    if len(hits) > 1:
        names = ", ".join(item.relative_to(root).as_posix() for item in hits)
        raise FileNotFoundError(f"工作区内有多个同名文件：{base}；candidates: {names}")
    raise FileNotFoundError(f"未找到输入文件：{key}")
"""
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
os.chdir({remote_cwd!r})
{analysis_helpers}

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
            bootstrap = f"import os\nos.chdir({remote_cwd!r})\n"

        # Align with ``python script.py``: inject script metadata before user code.
        meta = f"__name__ = '__main__'\n__file__ = {remote_source!r}\n"
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
