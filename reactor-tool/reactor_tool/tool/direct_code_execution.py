"""LeAgent-style direct Python execution for calling agents."""
from __future__ import annotations

import os
import shutil
import base64
import ast
from pathlib import Path

from reactor_tool.model.protocal import CodeExecutionRequest
from reactor_tool.tool.code_interpreter_policy import build_permission_policy
from reactor_tool.tool.python_sandbox_executor import PythonSandboxExecutionError, PythonSandboxExecutor
from reactor_tool.util.file_util import download_all_files_in_path, upload_file_by_path

# reactor-tool 包根：.../reactor-tool/reactor_tool/tool/this.py → parents[2] = reactor-tool
_REACTOR_TOOL_ROOT = Path(__file__).resolve().parents[2]
_DEFAULT_SKILL_OUTPUT = _REACTOR_TOOL_ROOT / "skilloutput"


async def execute_code(request: CodeExecutionRequest) -> dict:
    workspace = _workspace_path(request)
    if request.reset_workspace:
        _reset_code_execution_dirs(workspace)
    input_dir = workspace / "input"
    output_dir = workspace / "output"
    input_dir.mkdir(parents=True, exist_ok=True)
    output_dir.mkdir(parents=True, exist_ok=True)
    _stage_inline_files(workspace, request.files)
    source = _resolve_source(request, workspace)
    source_file = workspace / "__last_source__.py"
    source_file.write_text(source, encoding="utf-8")
    try:
        ast.parse(source, filename=str(source_file))
    except SyntaxError as exc:
        return _envelope(request, "error", str(exc), "", "", [], [], str(workspace),
                         error_type="syntax", source_file=str(source_file),
                         syntax_diagnostics=[{"line": exc.lineno, "column": exc.offset, "message": exc.msg}])
    imported_files = await download_all_files_in_path(request.file_names, str(input_dir))
    policy = build_permission_policy(
        profile=request.permission_profile,
        workspace_root=str(workspace),
        output_dir=str(output_dir),
        input_files=[item for item in imported_files if item.get("file_path")],
    )
    executor = PythonSandboxExecutor(policy, request.timeout_seconds, request.inputs)
    execution_result = None
    execution_error = None
    try:
        execution_result = executor.execute(source)
        status, error, stdout, stderr = "ok", None, execution_result.stdout, execution_result.stderr
    except TimeoutError as exc:
        execution_error = exc
        status, error, stdout, stderr = "timeout", str(exc), "", ""
    except PythonSandboxExecutionError as exc:
        execution_error = exc
        status, error, stdout, stderr = "error", str(exc), exc.stdout, exc.stderr
    except Exception as exc:
        execution_error = exc
        status, error, stdout, stderr = "error", str(exc), "", ""
    finally:
        produced_files = executor.produced_files()
        executor.close()
    file_info = []
    for produced in produced_files:
        uploaded = await upload_file_by_path(str(produced.get("file_path") or ""), request.request_id)
        if uploaded:
            file_info.append(uploaded)
    return _envelope(request, status, error, stdout, stderr, produced_files, file_info, str(workspace),
                     error_type="runtime" if status == "error" else None,
                     result=getattr(execution_result or execution_error, "result", None),
                     duration_ms=getattr(execution_result or execution_error, "duration_ms", 0),
                     returncode=getattr(execution_result or execution_error, "returncode", 1),
                     stdout_truncated=getattr(execution_result, "stdout_truncated", False),
                     stderr_truncated=getattr(execution_result, "stderr_truncated", False),
                     source_file=str(source_file))


def _workspace_path(request: CodeExecutionRequest) -> Path:
    """统一会话工作区：skilloutput/{sessionId}（与 Java workspace_write 对齐）。"""
    if request.workspace_root and str(request.workspace_root).strip():
        return Path(str(request.workspace_root).strip()).expanduser().resolve()

    safe_id = _safe_session_id(request.request_id)
    env_root = (os.getenv("CODE_EXECUTION_WORKSPACE_ROOT") or "").strip()
    if env_root:
        # 兼容测试/运维：显式根目录下再按 session 隔离
        return (Path(env_root).expanduser().resolve() / safe_id).resolve()

    skill_root = (os.getenv("SKILL_OUTPUT_ROOT") or os.getenv("REACTOR_SKILL_OUTPUT_ROOT") or "").strip()
    if skill_root:
        return (Path(skill_root).expanduser().resolve() / safe_id).resolve()

    return (_DEFAULT_SKILL_OUTPUT / safe_id).resolve()


def _safe_session_id(request_id: str) -> str:
    return "".join(char if char.isalnum() or char in "-_" else "_" for char in (request_id or ""))[:120] or "anonymous"


def _reset_code_execution_dirs(workspace: Path) -> None:
    """只清理代码执行子目录，不删除会话里 workspace_write 等其它产物。"""
    for name in ("input", "output"):
        target = workspace / name
        if target.exists():
            shutil.rmtree(target, ignore_errors=True)
    source_file = workspace / "__last_source__.py"
    if source_file.is_file():
        source_file.unlink(missing_ok=True)


def _resolve_source(request: CodeExecutionRequest, workspace: Path) -> str:
    if not request.workspace_file:
        return request.source
    candidate = (workspace / request.workspace_file).resolve()
    if workspace not in candidate.parents or not candidate.is_file():
        raise ValueError("workspaceFile 必须是会话工作区内已有的源码文件")
    return candidate.read_text(encoding="utf-8")


def _stage_inline_files(workspace: Path, files: list[dict]) -> None:
    for item in files:
        relative_path = str(item.get("path") or "").strip()
        if not relative_path:
            continue
        target = (workspace / relative_path).resolve()
        if workspace not in target.parents:
            raise ValueError("files.path 必须位于工作区内")
        target.parent.mkdir(parents=True, exist_ok=True)
        content = str(item.get("content") or "")
        if item.get("encoding") == "base64":
            target.write_bytes(base64.b64decode(content))
        else:
            target.write_text(content, encoding="utf-8")


def _envelope(request, status, error, stdout, stderr, produced_files, file_info, workspace, **extra):
    return {"requestId": request.request_id, "status": status, "error": error, "stdout": stdout,
            "stderr": stderr, "producedFiles": produced_files, "fileInfo": file_info,
            "workspace": workspace, "sourceLength": len(request.source), **extra}
