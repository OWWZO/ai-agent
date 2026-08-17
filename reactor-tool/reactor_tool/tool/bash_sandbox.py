# -*- coding: utf-8 -*-
"""会话 bash 沙箱：与 code_execution 共用 CODE_SANDBOX_BACKEND（local | e2b）。

- local：本机子进程，cwd=workspaceRoot
- e2b：创建云端沙箱 → 上传 workspace（含 materialize 的 skills/）→ 远端执行 →
  下载 skills/** 回本地 workspace → 再回写 skill 库

Java BashTool 只发 HTTP；本模块负责真实执行。
"""
from __future__ import annotations

import asyncio
import os
import shutil
import time
from pathlib import Path
from typing import Any, List, Optional, Tuple

from loguru import logger

from reactor_tool.model.protocal import BashSandboxRequest, BashSandboxResponse
from reactor_tool.tool.sandbox_backend_config import (
    get_e2b_sandbox_timeout_seconds,
    get_e2b_template,
    get_e2b_workdir,
    get_sandbox_backend,
    require_e2b_api_key,
)

SKILLS_DIR = "skills"


async def run_bash_sandbox(body: BashSandboxRequest) -> BashSandboxResponse:
    started = time.time()
    workspace = Path(body.workspace_root).expanduser().resolve()
    workspace.mkdir(parents=True, exist_ok=True)

    lib_root: Optional[Path] = None
    if body.skill_library_root and str(body.skill_library_root).strip():
        lib_root = Path(body.skill_library_root).expanduser().resolve()

    disabled = {n.strip() for n in (body.disabled_skill_names or []) if n and str(n).strip()}
    materialized: List[str] = []
    synced: List[str] = []
    backend = get_sandbox_backend()

    try:
        # 先灌到「会话工作区」本地树；e2b 会把整棵树上传，local 直接在此 cwd 执行
        if lib_root is not None and lib_root.is_dir():
            materialized = _materialize_skills(lib_root, workspace, disabled)

        if backend == "e2b":
            exit_code, stdout, stderr, truncated, timed_out = await asyncio.to_thread(
                _exec_e2b,
                body.command,
                workspace,
                int(body.timeout_seconds),
                int(body.max_output_chars),
            )
        else:
            exit_code, stdout, stderr, truncated, timed_out = await _exec_local_shell(
                command=body.command,
                cwd=workspace,
                timeout_sec=int(body.timeout_seconds),
                max_output_chars=int(body.max_output_chars),
            )

        if lib_root is not None:
            # e2b 路径下 skills 已从远端下载到 workspace/skills；再回写全局库
            synced = _sync_back_skills(workspace, lib_root)
    finally:
        _cleanup_sandbox_skills(workspace)

    return BashSandboxResponse(
        requestId=body.request_id,
        exitCode=exit_code,
        stdout=stdout,
        stderr=stderr,
        truncated=truncated,
        timedOut=timed_out,
        durationMs=int((time.time() - started) * 1000),
        skillsMaterialized=materialized,
        skillsSyncedBack=synced,
        cwd=".",
    )


# ── skill 物化 / 回写（本地磁盘树）──────────────────────────────────────────


def _materialize_skills(lib_root: Path, workspace: Path, disabled: set[str]) -> List[str]:
    sandbox_skills = workspace / SKILLS_DIR
    if sandbox_skills.exists():
        shutil.rmtree(sandbox_skills, ignore_errors=True)
    sandbox_skills.mkdir(parents=True, exist_ok=True)

    names: List[str] = []
    for child in sorted(lib_root.iterdir()):
        if not child.is_dir() or child.name.startswith("."):
            continue
        if child.name in disabled:
            continue
        target = sandbox_skills / child.name
        shutil.copytree(child, target, dirs_exist_ok=True)
        names.append(child.name)
    logger.info("[bash_sandbox] materialize skills={} -> {}", names, sandbox_skills)
    return names


def _sync_back_skills(workspace: Path, lib_root: Path) -> List[str]:
    sandbox_skills = workspace / SKILLS_DIR
    if not sandbox_skills.is_dir():
        return []
    lib_root.mkdir(parents=True, exist_ok=True)
    synced: List[str] = []
    for child in sorted(sandbox_skills.iterdir()):
        if not child.is_dir() or child.name.startswith("."):
            continue
        target = lib_root / child.name
        if target.exists():
            shutil.rmtree(target, ignore_errors=True)
        shutil.copytree(child, target)
        synced.append(child.name)
    if synced:
        logger.info("[bash_sandbox] sync-back skills={} -> {}", synced, lib_root)
    return synced


def _cleanup_sandbox_skills(workspace: Path) -> None:
    sandbox_skills = workspace / SKILLS_DIR
    if sandbox_skills.exists():
        shutil.rmtree(sandbox_skills, ignore_errors=True)


# ── local backend ───────────────────────────────────────────────────────────


def _shell_argv(command: str) -> List[str]:
    if os.name == "nt":
        comspec = os.environ.get("ComSpec") or "cmd.exe"
        return [comspec, "/c", command]
    for candidate in ("bash", "sh"):
        path = shutil.which(candidate)
        if path:
            return [path, "-lc", command]
    return ["/bin/sh", "-lc", command]


def _enrich_path_env(env: dict) -> None:
    import sys

    py_dir = str(Path(sys.executable).resolve().parent)
    path = env.get("PATH") or env.get("Path") or ""
    if py_dir not in path:
        env["PATH"] = py_dir + os.pathsep + path
    env["SKILL_PYTHON"] = sys.executable
    env["PYTHON"] = sys.executable


async def _exec_local_shell(
    command: str,
    cwd: Path,
    timeout_sec: int,
    max_output_chars: int,
) -> Tuple[Optional[int], str, str, bool, bool]:
    argv = _shell_argv(command)
    env = os.environ.copy()
    env["SKILL_WORKSPACE"] = str(cwd)
    env["PYTHONIOENCODING"] = "utf-8"
    env.setdefault("LANG", "C.UTF-8")
    _enrich_path_env(env)

    process = await asyncio.create_subprocess_exec(
        *argv,
        cwd=str(cwd),
        env=env,
        stdout=asyncio.subprocess.PIPE,
        stderr=asyncio.subprocess.PIPE,
    )
    try:
        stdout_b, stderr_b = await asyncio.wait_for(
            process.communicate(),
            timeout=max(1, timeout_sec),
        )
        timed_out = False
    except asyncio.TimeoutError:
        process.kill()
        await process.communicate()
        return None, "", f"execution timed out after {timeout_sec}s", False, True

    stdout, t1 = _decode_and_truncate(stdout_b, max_output_chars)
    stderr, t2 = _decode_and_truncate(stderr_b, max_output_chars)
    code = process.returncode if process.returncode is not None else -1
    return code, stdout, stderr, t1 or t2, timed_out


# ── e2b backend ─────────────────────────────────────────────────────────────


def _exec_e2b(
    command: str,
    workspace: Path,
    timeout_sec: int,
    max_output_chars: int,
) -> Tuple[Optional[int], str, str, bool, bool]:
    """同步：创建 E2B 沙箱、上传工作区、执行、下载 skills 回本地。"""
    from e2b_code_interpreter import Sandbox

    remote_root = get_e2b_workdir()
    create_kwargs: dict[str, Any] = {
        "api_key": require_e2b_api_key(),
        "timeout": get_e2b_sandbox_timeout_seconds(float(timeout_sec)),
    }
    template = get_e2b_template()
    if template:
        create_kwargs["template"] = template

    sandbox = Sandbox.create(**create_kwargs)
    try:
        _e2b_mkdir(sandbox, remote_root)
        _e2b_upload_workspace(sandbox, workspace, remote_root)

        exit_code, stdout, stderr, timed_out = _e2b_run_command(
            sandbox, command, remote_root, timeout_sec
        )
        stdout, t1 = _truncate_text(stdout, max_output_chars)
        stderr, t2 = _truncate_text(stderr, max_output_chars)

        # 把远端 skills/ 拉回本地 workspace，供后续 sync-back 到 runtime 库
        _e2b_download_skills(sandbox, remote_root, workspace)
        return exit_code, stdout, stderr, t1 or t2, timed_out
    finally:
        try:
            sandbox.kill()
        except Exception:
            pass


def _e2b_mkdir(sandbox: Any, remote_root: str) -> None:
    commands = getattr(sandbox, "commands", None)
    if commands is not None and callable(getattr(commands, "run", None)):
        commands.run(f"mkdir -p {remote_root}/skills {remote_root}/input", timeout=30)
        return
    sandbox.run_code(
        "from pathlib import Path\n"
        f"Path({remote_root!r}).mkdir(parents=True, exist_ok=True)\n"
        f"Path({remote_root!r}, 'skills').mkdir(parents=True, exist_ok=True)\n"
        f"Path({remote_root!r}, 'input').mkdir(parents=True, exist_ok=True)\n",
        timeout=30,
    )


def _e2b_upload_workspace(sandbox: Any, workspace: Path, remote_root: str) -> None:
    if not workspace.is_dir():
        return
    batch: list[dict[str, Any]] = []
    for path in workspace.rglob("*"):
        if not path.is_file() or path.is_symlink():
            continue
        try:
            rel = path.resolve().relative_to(workspace.resolve())
        except ValueError:
            continue
        if any(part.startswith(".") for part in rel.parts):
            continue
        remote_path = f"{remote_root}/{rel.as_posix()}"
        batch.append({"path": remote_path, "data": path.read_bytes()})
        if len(batch) >= 32:
            _e2b_write_files(sandbox, batch)
            batch = []
    if batch:
        _e2b_write_files(sandbox, batch)
    logger.info("[bash_sandbox] e2b uploaded workspace={}", workspace)


def _e2b_write_files(sandbox: Any, files: list[dict[str, Any]]) -> None:
    files_api = getattr(sandbox, "files", None)
    if files_api is None:
        raise RuntimeError("E2B sandbox has no files API")
    write_files = getattr(files_api, "write_files", None)
    if callable(write_files):
        write_files(files)
        return
    for item in files:
        files_api.write(item["path"], item["data"])


def _e2b_run_command(
    sandbox: Any,
    command: str,
    remote_root: str,
    timeout_sec: int,
) -> Tuple[Optional[int], str, str, bool]:
    """优先 commands.run；否则用 run_code + subprocess（code-interpreter 模板一定有）。"""
    commands = getattr(sandbox, "commands", None)
    if commands is not None and callable(getattr(commands, "run", None)):
        try:
            result = commands.run(
                command,
                cwd=remote_root,
                timeout=max(1, int(timeout_sec)),
            )
            exit_code = getattr(result, "exit_code", None)
            if exit_code is None:
                exit_code = getattr(result, "error", None) and 1 or 0
            stdout = str(getattr(result, "stdout", "") or "")
            stderr = str(getattr(result, "stderr", "") or "")
            return int(exit_code) if exit_code is not None else 0, stdout, stderr, False
        except Exception as exc:
            message = str(exc).lower()
            if "timeout" in message or "timed out" in message:
                return None, "", f"execution timed out after {timeout_sec}s", True
            # fall through to run_code path
            logger.warning("[bash_sandbox] commands.run failed, fallback run_code: {}", exc)

    # Fallback: shell via Python in the code-interpreter kernel
    # Use list form to avoid quote hell; run through bash -lc for skill scripts.
    script = f"""
import subprocess, shlex
cmd = {command!r}
try:
    r = subprocess.run(
        ["bash", "-lc", cmd],
        cwd={remote_root!r},
        capture_output=True,
        text=True,
        timeout={max(1, int(timeout_sec))},
    )
    print("__BASH_EXIT__" + str(r.returncode))
    print("__BASH_STDOUT_BEGIN__")
    print(r.stdout or "", end="")
    print("__BASH_STDOUT_END__")
    print("__BASH_STDERR_BEGIN__")
    print(r.stderr or "", end="")
    print("__BASH_STDERR_END__")
except subprocess.TimeoutExpired:
    print("__BASH_EXIT__timeout")
    print("__BASH_STDOUT_BEGIN__")
    print("__BASH_STDOUT_END__")
    print("__BASH_STDERR_BEGIN__")
    print("execution timed out after {timeout_sec}s")
    print("__BASH_STDERR_END__")
"""
    try:
        execution = sandbox.run_code(script, timeout=max(5, int(timeout_sec) + 10))
    except Exception as exc:
        message = str(exc).lower()
        if "timeout" in message or "timed out" in message:
            return None, "", f"execution timed out after {timeout_sec}s", True
        return 1, "", str(exc), False

    stdout_log, stderr_log = _extract_e2b_logs(execution)
    combined = (stdout_log or "") + "\n" + (stderr_log or "")
    exit_code: Optional[int] = 1
    timed_out = False
    out = ""
    err = ""
    if "__BASH_EXIT__timeout" in combined:
        timed_out = True
        exit_code = None
        err = f"execution timed out after {timeout_sec}s"
    else:
        for line in combined.splitlines():
            if line.startswith("__BASH_EXIT__"):
                raw = line[len("__BASH_EXIT__") :].strip()
                try:
                    exit_code = int(raw)
                except ValueError:
                    exit_code = 1
                break
        out = _extract_between(combined, "__BASH_STDOUT_BEGIN__", "__BASH_STDOUT_END__")
        err = _extract_between(combined, "__BASH_STDERR_BEGIN__", "__BASH_STDERR_END__")
        if not out and not err and exit_code != 0:
            err = combined or "bash failed in e2b"
    return exit_code, out, err, timed_out


def _e2b_download_skills(sandbox: Any, remote_root: str, workspace: Path) -> None:
    """把远端 skills/** 拉回本地 workspace/skills。"""
    remote_skills = f"{remote_root}/{SKILLS_DIR}"
    list_script = f"""
import json
from pathlib import Path
root = Path({remote_skills!r})
files = []
if root.is_dir():
    for p in root.rglob("*"):
        if p.is_file() and not p.is_symlink():
            try:
                rel = p.resolve().relative_to(root.resolve()).as_posix()
            except ValueError:
                continue
            files.append(rel)
print("__SKILLS_LIST__" + json.dumps(files, ensure_ascii=True))
"""
    try:
        execution = sandbox.run_code(list_script, timeout=60)
    except Exception as exc:
        logger.warning("[bash_sandbox] e2b list skills failed: {}", exc)
        return
    stdout, _ = _extract_e2b_logs(execution)
    rels: list[str] = []
    for line in reversed((stdout or "").splitlines()):
        if "__SKILLS_LIST__" in line:
            import json

            payload = line.split("__SKILLS_LIST__", 1)[1].strip()
            try:
                rels = list(json.loads(payload or "[]"))
            except Exception:
                rels = []
            break

    local_skills = workspace / SKILLS_DIR
    if local_skills.exists():
        shutil.rmtree(local_skills, ignore_errors=True)
    local_skills.mkdir(parents=True, exist_ok=True)

    files_api = getattr(sandbox, "files", None)
    if files_api is None:
        return
    for rel in rels:
        remote_path = f"{remote_skills}/{rel}"
        try:
            try:
                content = files_api.read(remote_path, format="bytes")
            except TypeError:
                content = files_api.read(remote_path)
            if isinstance(content, str):
                data = content.encode("utf-8")
            else:
                data = bytes(content)
            local_path = local_skills / rel
            local_path.parent.mkdir(parents=True, exist_ok=True)
            local_path.write_bytes(data)
        except Exception as exc:
            logger.warning("[bash_sandbox] e2b download {} failed: {}", remote_path, exc)
    logger.info("[bash_sandbox] e2b downloaded skills count={}", len(rels))


def _extract_e2b_logs(execution: Any) -> Tuple[str, str]:
    logs = getattr(execution, "logs", None)
    if logs is None:
        return str(getattr(execution, "text", "") or ""), ""
    stdout_parts = getattr(logs, "stdout", None) or []
    stderr_parts = getattr(logs, "stderr", None) or []
    if isinstance(stdout_parts, str):
        stdout = stdout_parts
    else:
        stdout = "".join(str(x) for x in stdout_parts)
    if isinstance(stderr_parts, str):
        stderr = stderr_parts
    else:
        stderr = "".join(str(x) for x in stderr_parts)
    return stdout, stderr


def _extract_between(text: str, begin: str, end: str) -> str:
    if begin not in text or end not in text:
        return ""
    start = text.index(begin) + len(begin)
    stop = text.index(end, start)
    chunk = text[start:stop]
    if chunk.startswith("\n"):
        chunk = chunk[1:]
    return chunk


def _decode_and_truncate(raw: Optional[bytes], max_chars: int) -> Tuple[str, bool]:
    if not raw:
        return "", False
    return _truncate_text(raw.decode("utf-8", errors="replace"), max_chars)


def _truncate_text(text: str, max_chars: int) -> Tuple[str, bool]:
    if text is None:
        return "", False
    if len(text) <= max_chars:
        return text, False
    head = max_chars // 2
    tail = max_chars - head - 20
    if tail < 0:
        return text[:max_chars], True
    return text[:head] + "\n…[truncated]…\n" + text[-tail:], True
