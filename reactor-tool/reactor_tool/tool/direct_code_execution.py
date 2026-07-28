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


async def execute_code(request: CodeExecutionRequest) -> dict:
    workspace = _workspace_path(request.request_id)
    if request.reset_workspace and workspace.exists():
        shutil.rmtree(workspace)
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


def _workspace_path(request_id: str) -> Path:
    root = Path(os.getenv("CODE_EXECUTION_WORKSPACE_ROOT") or ".reactor-code-execution")
    safe_id = "".join(char if char.isalnum() or char in "-_" else "_" for char in request_id)[:120] or "anonymous"
    return (root / safe_id).resolve()


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
