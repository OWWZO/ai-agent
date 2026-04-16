# -*- coding: utf-8 -*-
"""
skill 脚本运行时支持。
"""
import asyncio
import json
import os
import shutil
import sys
import tempfile
from dataclasses import dataclass
from pathlib import Path
from typing import Dict, List


SUPPORTED_RUNTIMES = {"python", "node", "shell", "powershell", "bat"}


@dataclass
class PreparedWorkspace:
    """脚本执行前准备好的隔离工作区。"""

    temp_dir: Path
    skill_root: Path
    arguments_file: Path
    baseline_files: Dict[str, tuple[int, int]]


@dataclass
class RuntimeExecutionResult:
    """运行时执行结果。"""

    success: bool
    exit_code: int
    stdout: str
    stderr: str
    summary: str


def prepare_workspace(skill_base_path: str, arguments: Dict) -> PreparedWorkspace:
    """复制 skill 目录到临时工作区，并写入参数文件。"""
    source_root = Path(skill_base_path).resolve()
    temp_dir = Path(tempfile.mkdtemp(prefix="skill-runner-"))
    workspace_root = temp_dir / source_root.name
    shutil.copytree(source_root, workspace_root)

    internal_dir = workspace_root / ".skill"
    internal_dir.mkdir(parents=True, exist_ok=True)
    arguments_file = internal_dir / "arguments.json"
    arguments_file.write_text(
        json.dumps(arguments or {}, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )

    baseline_files = snapshot_regular_files(workspace_root)
    return PreparedWorkspace(
        temp_dir=temp_dir,
        skill_root=workspace_root,
        arguments_file=arguments_file,
        baseline_files=baseline_files,
    )


def cleanup_workspace(prepared_workspace: PreparedWorkspace | None):
    """清理临时工作区。"""
    if not prepared_workspace:
        return
    shutil.rmtree(prepared_workspace.temp_dir, ignore_errors=True)


def resolve_script_path(skill_base_path: str, script_path: str) -> tuple[Path, Path]:
    """校验脚本路径没有逃逸 skill 根目录。"""
    skill_root = Path(skill_base_path).resolve()
    if not skill_root.is_dir():
        raise ValueError(f"skill base path does not exist: {skill_root}")

    resolved_script = (skill_root / script_path).resolve()
    if skill_root not in {resolved_script, *resolved_script.parents}:
        raise ValueError("script path is outside registered skill directory")
    if not resolved_script.is_file():
        raise ValueError(f"script file does not exist: {resolved_script}")
    return skill_root, resolved_script


def resolve_workspace_script(prepared_workspace: PreparedWorkspace, script_path: str) -> Path:
    """将相对脚本路径映射到临时工作区。"""
    workspace_script = (prepared_workspace.skill_root / script_path).resolve()
    if prepared_workspace.skill_root not in {workspace_script, *workspace_script.parents}:
        raise ValueError("workspace script path is outside isolated skill directory")
    return workspace_script


def snapshot_regular_files(root_path: Path) -> Dict[str, tuple[int, int]]:
    """记录当前工作区的文件快照，用于识别新增产物。"""
    snapshot: Dict[str, tuple[int, int]] = {}
    for file_path in root_path.rglob("*"):
        if file_path.is_file():
            relative_path = file_path.relative_to(root_path).as_posix()
            stat_result = file_path.stat()
            snapshot[relative_path] = (int(stat_result.st_size), int(stat_result.st_mtime_ns))
    return snapshot


def collect_generated_files(prepared_workspace: PreparedWorkspace) -> List[Path]:
    """收集执行后新增或变更的文件。"""
    current_snapshot = snapshot_regular_files(prepared_workspace.skill_root)
    generated_files: List[Path] = []
    for relative_path, meta in current_snapshot.items():
        if relative_path.startswith(".skill/"):
            continue
        if prepared_workspace.baseline_files.get(relative_path) != meta:
            generated_files.append(prepared_workspace.skill_root / relative_path)
    return sorted(generated_files, key=lambda item: item.as_posix())


def build_command(runtime: str, script_path: Path, argv: List[str]) -> List[str]:
    """根据 runtime 构造真实命令。"""
    normalized_runtime = (runtime or "").strip().lower()
    if normalized_runtime not in SUPPORTED_RUNTIMES:
        raise ValueError(f"unsupported runtime: {runtime}")

    if normalized_runtime == "python":
        executable = _resolve_executable(
            "SKILL_PYTHON_BIN",
            [_resolve_project_python(), sys.executable, "python", "python3"],
        )
        return [executable, str(script_path), *argv]
    if normalized_runtime == "node":
        executable = _resolve_executable("SKILL_NODE_BIN", ["node"])
        return [executable, str(script_path), *argv]
    if normalized_runtime == "shell":
        executable = _resolve_executable("SKILL_SHELL_BIN", ["bash", "sh"])
        return [executable, str(script_path), *argv]
    if normalized_runtime == "powershell":
        executable = _resolve_executable("SKILL_POWERSHELL_BIN", ["pwsh", "powershell"])
        return [executable, "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", str(script_path), *argv]

    executable = _resolve_executable("SKILL_BAT_BIN", [os.environ.get("ComSpec"), "cmd"])
    return [executable, "/c", str(script_path), *argv]


async def execute_script(
    runtime: str,
    script_path: Path,
    working_directory: Path,
    arguments: Dict,
    arguments_file: Path,
    argv: List[str],
    timeout_seconds: int,
) -> RuntimeExecutionResult:
    """执行 skill 脚本，并统一返回 stdout / stderr / exit_code。"""
    command = build_command(runtime, script_path, argv or [])
    env = os.environ.copy()
    env["SKILL_ARGUMENTS_JSON"] = json.dumps(arguments or {}, ensure_ascii=False)
    env["SKILL_ARGUMENTS_FILE"] = str(arguments_file)
    env["SKILL_WORKSPACE"] = str(working_directory)
    env["SKILL_OUTPUT_DIR"] = str(working_directory / "output")

    process = await asyncio.create_subprocess_exec(
        *command,
        cwd=str(working_directory),
        env=env,
        stdout=asyncio.subprocess.PIPE,
        stderr=asyncio.subprocess.PIPE,
    )
    try:
        stdout_bytes, stderr_bytes = await asyncio.wait_for(
            process.communicate(),
            timeout=max(1, int(timeout_seconds)),
        )
    except asyncio.TimeoutError:
        process.kill()
        await process.communicate()
        return RuntimeExecutionResult(
            success=False,
            exit_code=-1,
            stdout="",
            stderr=f"execution timed out after {timeout_seconds} seconds",
            summary="脚本执行超时",
        )

    stdout = _decode_output(stdout_bytes)
    stderr = _decode_output(stderr_bytes)
    success = process.returncode == 0
    return RuntimeExecutionResult(
        success=success,
        exit_code=process.returncode if process.returncode is not None else -1,
        stdout=stdout,
        stderr=stderr,
        summary="脚本执行成功" if success else "脚本执行失败",
    )


def _resolve_executable(env_key: str, candidates: List[str | None]) -> str:
    """优先取环境变量，其次按候选命令查找运行时。"""
    override = os.getenv(env_key)
    if override:
        return override

    for candidate in candidates:
        if not candidate:
            continue
        resolved = shutil.which(candidate)
        if resolved:
            return resolved
        if Path(candidate).exists():
            return candidate
    raise FileNotFoundError(f"runtime executable not found for {env_key}")


def _resolve_project_python() -> str | None:
    """优先返回 reactor-tool 本地 .venv 的 Python，避免被外层启动器解释器影响。"""
    project_root = Path(__file__).resolve().parents[2]
    windows_python = project_root / ".venv" / "Scripts" / "python.exe"
    unix_python = project_root / ".venv" / "bin" / "python"
    for candidate in (windows_python, unix_python):
        if candidate.exists():
            return str(candidate)
    return None


def _decode_output(content: bytes | None) -> str:
    """优先按 UTF-8 解码输出，失败时降级替换。"""
    if not content:
        return ""
    try:
        return content.decode("utf-8")
    except UnicodeDecodeError:
        return content.decode("utf-8", errors="replace")
