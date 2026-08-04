"""Persistent subprocess runner for code-interpreter Python actions.

The parent process communicates with this module through JSON Lines. User code
never writes protocol data directly because stdout and stderr are captured for
each execution request.
"""
from __future__ import annotations

import ast
import contextlib
import io
import json
import mimetypes
import sys
import time
import traceback
from pathlib import Path
from typing import Any

# Force UTF-8 on the protocol streams. The parent launches this module with
# ``-I`` (isolated), which ignores PYTHONIOENCODING; on Windows the default
# console encoding is often GBK and would corrupt non-ASCII JSON payloads.
def _configure_stdio() -> None:
    for stream in (sys.stdin, sys.stdout, sys.stderr):
        reconfigure = getattr(stream, "reconfigure", None)
        if not callable(reconfigure):
            continue
        try:
            reconfigure(encoding="utf-8", errors="replace")
        except Exception:
            pass


_configure_stdio()

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


# 不参与产物采集：输入目录、沙箱元文件、隐藏路径
_EXCLUDED_TOP_DIRS = frozenset({"input"})
_EXCLUDED_FILE_NAMES = frozenset({"__last_source__.py"})


def _is_harvestable_file(path: Path, workspace_root: Path) -> bool:
    if path.is_symlink() or not path.is_file():
        return False
    try:
        relative = path.resolve().relative_to(workspace_root)
    except ValueError:
        return False
    parts = relative.parts
    if not parts or any(part.startswith(".") for part in parts):
        return False
    if parts[0] in _EXCLUDED_TOP_DIRS:
        return False
    if relative.name in _EXCLUDED_FILE_NAMES or relative.name.startswith("__last_source__"):
        return False
    return True


def _snapshot_workspace_files(workspace_root: Path) -> dict[Path, tuple[int, int]]:
    if not workspace_root.is_dir():
        return {}
    files: dict[Path, tuple[int, int]] = {}
    # 快照只记录可采集普通文件，排除输入目录、隐藏目录和 runner 自己的源文件，避免把内部状态当成用户产物。
    for path in workspace_root.rglob("*"):
        if not _is_harvestable_file(path, workspace_root):
            continue
        resolved = path.resolve()
        stat = resolved.stat()
        files[resolved] = (stat.st_size, stat.st_mtime_ns)
    return files


def _produced_files(workspace_root: Path,
                    before: dict[Path, tuple[int, int]],
                    after: dict[Path, tuple[int, int]]) -> list[dict[str, Any]]:
    produced: list[dict[str, Any]] = []
    # 通过 size + mtime_ns 对比识别新增和修改文件；删除文件不属于可上传产物，因此不会出现在结果中。
    for path in sorted(after, key=lambda item: str(item).lower()):
        if after[path] == before.get(path):
            continue
        mime_type, _ = mimetypes.guess_type(path.name)
        produced.append({
            "file_path": str(path),
            "relative_path": path.relative_to(workspace_root).as_posix(),
            "name": path.name,
            "size": after[path][0],
            "mime_type": mime_type or "application/octet-stream",
        })
    return produced


def _sanitize_text(value: str) -> str:
    """Drop lone surrogates so UTF-8 / JSON never blow up on Windows."""
    if not value:
        return value
    return value.encode("utf-8", errors="replace").decode("utf-8")


def _write_response(response: dict[str, Any]) -> None:
    # ensure_ascii=True keeps the wire protocol pure ASCII so a mismatched
    # child-side encoding cannot produce Invalid \\escape on the next request.
    payload = json.dumps(response, ensure_ascii=True, default=str)
    sys.__stdout__.write(payload + "\n")
    sys.__stdout__.flush()


def _truncate(value: str, limit: int = 200_000) -> tuple[str, bool]:
    if len(value) <= limit:
        return value, False
    return value[:limit] + "\n...[truncated]", True


def _safe_json(value: Any) -> Any:
    try:
        json.dumps(value, ensure_ascii=True, default=str)
        return value
    except (TypeError, ValueError):
        return _sanitize_text(str(value))


def _sandbox_np_ptp(arr: Any, *args: Any, **kwargs: Any) -> Any:
    """NumPy 2.x removed ndarray.ptp; route legacy calls through np.ptp."""
    import numpy as np

    return np.ptp(arr, *args, **kwargs)


class _PtpCallRewriter(ast.NodeTransformer):
    """Rewrite ``obj.ptp(...)`` into ``__sandbox_np_ptp__(obj, ...)``.

    ``numpy.ndarray.ptp`` is a removed stub on NumPy 2.x and the C type is
    immutable, so monkey-patching is impossible.
    """

    def visit_Call(self, node: ast.Call) -> ast.AST:
        node = self.generic_visit(node)
        func = node.func
        if isinstance(func, ast.Attribute) and func.attr == "ptp":
            return ast.copy_location(
                ast.Call(
                    func=ast.Name(id="__sandbox_np_ptp__", ctx=ast.Load()),
                    args=[func.value, *node.args],
                    keywords=list(node.keywords),
                ),
                node,
            )
        return node


def _compile_user_code(source: str):
    # 编译前仅做 AST 级兼容改写，不执行用户表达式；运行时权限校验仍由 I/O guard 独立承担。
    tree = ast.parse(source, filename="<code_interpreter>", mode="exec")
    tree = _PtpCallRewriter().visit(tree)
    ast.fix_missing_locations(tree)
    return compile(tree, "<code_interpreter>", "exec")


def main() -> int:
    policy: CodeInterpreterPermissionPolicy | None = None
    workspace_root: Path | None = None
    output_dir: Path | None = None
    globals_env: dict[str, Any] | None = None

    for raw_line in sys.stdin:
        try:
            request = json.loads(raw_line)
            request_type = request.get("type")
            if request_type == "init":
                policy = _policy_from_payload(request["policy"])
                workspace_root = Path(policy.workspace_root).resolve()
                output_dir = Path(policy.output_dir).resolve()
                workspace_root.mkdir(parents=True, exist_ok=True)
                output_dir.mkdir(parents=True, exist_ok=True)
                globals_env = {"__name__": "__sandbox__"}
                globals_env.update(policy.to_runtime_variables())
                globals_env.update(build_runtime_helpers(policy))
                globals_env["__sandbox_np_ptp__"] = _sandbox_np_ptp
                globals_env.update(dict(request.get("initial_variables") or {}))
                # init 只建立一次共享 globals；后续 execute 可以保留变量，同时每次仍重新建立文件快照。
                _write_response({"type": "ready"})
                continue
            if request_type == "close":
                _write_response({"type": "closed"})
                return 0
            if (
                request_type != "execute"
                or policy is None
                or workspace_root is None
                or output_dir is None
                or globals_env is None
            ):
                raise ValueError("runner is not initialized")

            before = _snapshot_workspace_files(workspace_root)
            started_at = time.monotonic()
            stdout = io.StringIO()
            stderr = io.StringIO()
            status = "success"
            error = None
            try:
                # stdout/stderr 必须被捕获，用户 print 不能污染父子进程 JSONL 协议；权限守卫包在 exec 外层。
                with contextlib.redirect_stdout(stdout), contextlib.redirect_stderr(stderr):
                    with activate_runtime_io_guard(policy):
                        source = str(request.get("code") or "")
                        exec(_compile_user_code(source), globals_env)
            except BaseException as exc:
                status = "error"
                error = _sanitize_text(str(exc))
                stderr.write(_sanitize_text(traceback.format_exc()))
            after = _snapshot_workspace_files(workspace_root)
            # 即使用户代码失败也采集 after，保留失败前已经生成的文件供上层诊断或展示。
            stdout_text, stdout_truncated = _truncate(_sanitize_text(stdout.getvalue()))
            stderr_text, stderr_truncated = _truncate(_sanitize_text(stderr.getvalue()))
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
                "produced_files": _produced_files(workspace_root, before, after),
            })
        except BaseException as exc:
            _write_response({
                "type": "result",
                "status": "crash",
                "stdout": "",
                "stderr": _sanitize_text(traceback.format_exc()),
                "error": _sanitize_text(str(exc)),
                "produced_files": [],
            })
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
