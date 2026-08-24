"""Direct Python execution for calling agents."""

from __future__ import annotations

import os
import shutil
import base64
import ast
from pathlib import Path

from reactor_tool.model.protocal import CodeExecutionRequest
from reactor_tool.tool.code_interpreter_policy import build_permission_policy
from reactor_tool.tool.python_sandbox_executor import (
    PythonSandboxExecutionError,
    PythonSandboxExecutor,
)
from reactor_tool.util.file_util import download_all_files_in_path, upload_file_by_path

# reactor-tool 包根：.../reactor-tool/reactor_tool/tool/this.py → parents[2] = reactor-tool
_REACTOR_TOOL_ROOT = Path(__file__).resolve().parents[2]
_DEFAULT_SKILL_OUTPUT = _REACTOR_TOOL_ROOT / "skilloutput"


async def execute_code(request: CodeExecutionRequest) -> dict:
    # 代码执行的生命周期固定为“准备工作区 -> staging 输入 -> 语法检查 -> 沙箱执行 -> 上传产物”。
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
    source_file.write_text(
        source.encode("utf-8", errors="replace").decode("utf-8"),
        encoding="utf-8",
    )
    try:
        # 先做静态语法检查，语法错误不启动沙箱，也不产生运行时错误的误导信息。
        ast.parse(source, filename=str(source_file))
    except SyntaxError as exc:
        return _envelope(
            request,
            "error",
            str(exc),
            "",
            "",
            [],
            [],
            str(workspace),
            error_type="syntax",
            source_file=str(source_file),
            syntax_diagnostics=[
                {"line": exc.lineno, "column": exc.offset, "message": exc.msg}
            ],
        )
    # 裸文件名优先落会话工作区；URL 才下载到 input/。解析结果交给权限策略供 resolve_input_path 使用。
    imported_files = await download_all_files_in_path(
        request.file_names,
        str(input_dir),
        workspace_root=str(workspace),
    )
    policy = build_permission_policy(
        profile=request.permission_profile,
        workspace_root=str(workspace),
        output_dir=str(output_dir),
        input_files=[item for item in imported_files if item.get("file_path")],
    )
    # 权限策略和超时在执行器创建时固化，执行阶段只负责运行代码并收集结果。
    executor = PythonSandboxExecutor(policy, request.timeout_seconds, request.inputs)
    execution_result = None
    execution_error = None
    try:
        # 注入 __file__/__name__：优先 workspaceFile 真实路径，否则 __last_source__.py。
        logical_source = source_file
        if request.workspace_file:
            candidate = (workspace / request.workspace_file).resolve()
            if workspace in candidate.parents and candidate.is_file():
                logical_source = candidate
        execution_result = executor.execute(source, source_file=str(logical_source))
        status, error, stdout, stderr = (
            "ok",
            None,
            execution_result.stdout,
            execution_result.stderr,
        )
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
        # 即使超时或执行器抛出异常，也要先快照产物再关闭进程/资源，保证结果可回传。
        produced_files = executor.produced_files()
        executor.close()
    file_info = []
    for produced in produced_files:
        uploaded = await upload_file_by_path(
            str(produced.get("file_path") or ""), request.request_id
        )
        if uploaded:
            file_info.append(uploaded)
    return _envelope(
        request,
        status,
        error,
        stdout,
        stderr,
        produced_files,
        file_info,
        str(workspace),
        error_type="runtime" if status == "error" else None,
        result=getattr(execution_result or execution_error, "result", None),
        duration_ms=getattr(execution_result or execution_error, "duration_ms", 0),
        returncode=getattr(execution_result or execution_error, "returncode", 1),
        stdout_truncated=getattr(execution_result, "stdout_truncated", False),
        stderr_truncated=getattr(execution_result, "stderr_truncated", False),
        source_file=str(source_file),
    )


def _workspace_path(request: CodeExecutionRequest) -> Path:
    """统一会话工作区：skilloutput/{sessionId}（与 Java workspace_write 对齐）。"""
    if request.workspace_root and str(request.workspace_root).strip():
        # 显式工作区由调用方负责生命周期；此处只归一化路径，不再追加 session 子目录。
        return Path(str(request.workspace_root).strip()).expanduser().resolve()

    safe_id = _safe_session_id(request.request_id)
    env_root = (os.getenv("CODE_EXECUTION_WORKSPACE_ROOT") or "").strip()
    if env_root:
        # 测试/运维可指定根目录，但仍按安全 sessionId 隔离不同请求。
        return (Path(env_root).expanduser().resolve() / safe_id).resolve()

    skill_root = (
        os.getenv("SKILL_OUTPUT_ROOT") or os.getenv("REACTOR_SKILL_OUTPUT_ROOT") or ""
    ).strip()
    if skill_root:
        return (Path(skill_root).expanduser().resolve() / safe_id).resolve()

    return (_DEFAULT_SKILL_OUTPUT / safe_id).resolve()


def _safe_session_id(request_id: str) -> str:
    return (
        "".join(
            char if char.isalnum() or char in "-_" else "_"
            for char in (request_id or "")
        )[:120]
        or "anonymous"
    )


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
    # workspaceFile 是持久化工作区内的相对引用，解析后必须做 containment 校验以拒绝 .. 穿越。
    if workspace not in candidate.parents or not candidate.is_file():
        raise ValueError("workspaceFile 必须是会话工作区内已有的源码文件")
    return candidate.read_text(encoding="utf-8")


def _stage_inline_files(workspace: Path, files: list[dict]) -> None:
    for item in files:
        relative_path = str(item.get("path") or "").strip()
        if not relative_path:
            continue
        target = (workspace / relative_path).resolve()
        # 内联文件与源码共用会话工作区，但不能借相对路径写到工作区之外。
        if workspace not in target.parents:
            raise ValueError("files.path 必须位于工作区内")
        target.parent.mkdir(parents=True, exist_ok=True)
        content = str(item.get("content") or "")
        if item.get("encoding") == "base64":
            target.write_bytes(base64.b64decode(content))
        else:
            target.write_text(content, encoding="utf-8")


def _envelope(
    request,
    status,
    error,
    stdout,
    stderr,
    produced_files,
    file_info,
    workspace,
    **extra,
):
    # 统一成功、语法错误、超时和运行时异常的返回形状，前端无需按异常类型拆分解析逻辑。
    return {
        "requestId": request.request_id,
        "status": status,
        "error": error,
        "stdout": stdout,
        "stderr": stderr,
        "producedFiles": produced_files,
        "fileInfo": file_info,
        "workspace": workspace,
        "sourceLength": len(request.source),
        **extra,
    }
