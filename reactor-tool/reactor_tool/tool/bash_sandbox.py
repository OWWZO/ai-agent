# -*- coding: utf-8 -*-
"""会话 bash 沙箱：与 code_execution 共用 CODE_SANDBOX_BACKEND（local | e2b）。

- skills：从 skillLibraryRoot（runtime/skills）直传沙箱，不经 workspace copytree
- local：workspace/skills 目录链接到库
- e2b：UserSandboxManager 按 ownerKey 复用沙箱；远程 cwd=sessions/{sessionId}/
"""

from __future__ import annotations

import asyncio
import os
import re
import shutil
import time
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple

from loguru import logger

from reactor_tool.model.protocal import BashSandboxRequest, BashSandboxResponse
from reactor_tool.util.file_util import upload_file_by_path
from reactor_tool.tool.sandbox_backend_config import get_sandbox_backend
from reactor_tool.tool.e2b_session_sync import (
    SKILLS_DIR,
    download_changed_files,
    e2b_path_component_ok as _e2b_path_component_ok,
    e2b_remote_path_ok as _e2b_remote_path_ok,
    file_sha256 as _file_sha256,
    file_sig as _file_sig,
    mkdir_remote,
    push_workspace,
    record_uploaded,
    run_remote_python,
    session_remote_root,
    should_skip_rel as _should_skip_rel,
    snapshot_remote_files,
    utf8_len as _utf8_len,
    write_files as _sync_write_files,
)
from reactor_tool.tool.user_sandbox_manager import get_user_sandbox_manager

# 增量推送时跳过的顶层/任意段目录名（对齐常见构建产物，避免拖垮 sync）
_SKIP_DIR_NAMES = frozenset(
    {
        ".venv",
        "venv",
        "node_modules",
        "__pycache__",
        ".git",
        ".cache",
        ".pytest_cache",
        ".mypy_cache",
        "dist",
        "build",
    }
)
# Linux NAME_MAX=255 **字节**；中文 UTF-8 约 3B/字，任务描述当文件名极易超限
_E2B_MAX_NAME_BYTES = 200
_E2B_MAX_PATH_BYTES = 1000

_SKILL_PATH_RE = re.compile(
    r"(?:^|[^\w.-])(?:\./)?skills[/\\]+([A-Za-z0-9._-]+)",
    re.IGNORECASE,
)


def _command_needs_skills(command: str) -> bool:
    """启发式：命令文本是否引用 skills/ 路径（含 Windows 反斜杠）。"""
    text = command or ""
    lowered = text.lower()
    return "skills/" in lowered or "skills\\" in lowered


def _command_referenced_skill_names(command: str) -> List[str]:
    """从命令解析 skills/<name>；保序去重（大小写不敏感）。"""
    names: List[str] = []
    seen: set[str] = set()
    for match in _SKILL_PATH_RE.finditer(command or ""):
        raw = match.group(1)
        key = raw.lower()
        if key in seen:
            continue
        seen.add(key)
        names.append(raw)
    return names


def _resolve_skills_to_push(
    command: str, lib_root: Path, disabled: set[str]
) -> List[str]:
    """命令引用 ∩ 库内启用 skill；返回库目录真实名。"""
    enabled = _list_enabled_skill_names(lib_root, disabled)
    enabled_map = {name.lower(): name for name in enabled}
    resolved: List[str] = []
    seen: set[str] = set()
    for raw in _command_referenced_skill_names(command):
        actual = enabled_map.get(raw.lower())
        if actual is None or actual in seen:
            continue
        seen.add(actual)
        resolved.append(actual)
    return resolved


async def run_bash_sandbox(body: BashSandboxRequest) -> BashSandboxResponse:
    started = time.time()
    workspace = Path(body.workspace_root).expanduser().resolve()
    workspace.mkdir(parents=True, exist_ok=True)

    lib_root: Optional[Path] = None
    if body.skill_library_root and str(body.skill_library_root).strip():
        lib_root = Path(body.skill_library_root).expanduser().resolve()

    disabled = {
        n.strip() for n in (body.disabled_skill_names or []) if n and str(n).strip()
    }
    skill_names: List[str] = []
    synced: List[str] = []
    produced_paths: List[Path] = []
    backend = get_sandbox_backend()
    before_local = _snapshot_local_workspace(workspace) if backend != "e2b" else None

    try:
        if backend == "e2b":
            if lib_root is not None and lib_root.is_dir():
                skill_names = _resolve_skills_to_push(body.command, lib_root, disabled)
            session_id = (
                body.session_id or body.request_id or ""
            ).strip() or "anonymous"
            owner_key = (body.owner_key or "").strip() or "visitor:anonymous"
            (
                exit_code,
                stdout,
                stderr,
                truncated,
                timed_out,
                synced,
            ) = await asyncio.to_thread(
                _exec_e2b,
                session_id,
                body.command,
                workspace,
                lib_root,
                disabled,
                int(body.timeout_seconds),
                int(body.max_output_chars),
                None,
                produced_paths,
                owner_key,
            )
        else:
            if lib_root is not None and lib_root.is_dir():
                skill_names = _link_skills_for_local(lib_root, workspace, disabled)
            exit_code, stdout, stderr, truncated, timed_out = await _exec_local_shell(
                command=body.command,
                cwd=workspace,
                timeout_sec=int(body.timeout_seconds),
                max_output_chars=int(body.max_output_chars),
            )
            produced_paths = _changed_local_workspace_files(
                workspace, before_local or {}
            )
            # junction/symlink 指向库目录时，新建 skill 已落在 lib；扫一遍上报名称即可
            if lib_root is not None and lib_root.is_dir():
                synced = _detect_new_or_changed_skills(lib_root, skill_names)
    finally:
        if backend != "e2b":
            _unlink_local_skills_view(workspace)

    produced_files = []
    file_info = []
    for path in produced_paths:
        item = await upload_file_by_path(str(path), body.request_id)
        if item:
            file_info.append(item)
            produced_files.append(
                {
                    "file_name": item.get("fileName"),
                    "url": item.get("domainUrl")
                    or item.get("ossUrl")
                    or item.get("downloadUrl"),
                    "file_size": item.get("fileSize"),
                    "relative_path": _relative_workspace_path(path, workspace),
                }
            )

    return BashSandboxResponse(
        requestId=body.request_id,
        exitCode=exit_code,
        stdout=stdout,
        stderr=stderr,
        truncated=truncated,
        timedOut=timed_out,
        durationMs=int((time.time() - started) * 1000),
        skillsMaterialized=skill_names,
        skillsSyncedBack=synced,
        fileInfo=file_info,
        producedFiles=produced_files,
        cwd=".",
    )


# ── skill 库（无 workspace 中转拷贝）────────────────────────────────────────


def _list_enabled_skill_names(lib_root: Path, disabled: set[str]) -> List[str]:
    names: List[str] = []
    for child in sorted(lib_root.iterdir()):
        if not child.is_dir() or child.name.startswith("."):
            continue
        if child.name in disabled:
            continue
        names.append(child.name)
    return names


def _link_skills_for_local(
    lib_root: Path, workspace: Path, disabled: set[str]
) -> List[str]:
    """local：workspace/skills/<name> → runtime/skills/<name>，避免 copytree。"""
    skills_view = workspace / SKILLS_DIR
    _remove_skills_view(skills_view)
    skills_view.mkdir(parents=True, exist_ok=True)

    names: List[str] = []
    for child in sorted(lib_root.iterdir()):
        if not child.is_dir() or child.name.startswith("."):
            continue
        if child.name in disabled:
            continue
        _link_dir(child, skills_view / child.name)
        names.append(child.name)
    logger.info("[bash_sandbox] local link skills={} -> {}", names, skills_view)
    return names


def _link_dir(src: Path, dst: Path) -> None:
    dst.parent.mkdir(parents=True, exist_ok=True)
    if dst.exists() or dst.is_symlink():
        _remove_path(dst)
    src_abs = str(src.resolve())
    dst_abs = str(dst)
    if os.name == "nt":
        # 目录 junction 无需管理员；mklink /J 比 _winapi.CreateJunction 更稳
        import subprocess

        completed = subprocess.run(
            ["cmd", "/c", "mklink", "/J", dst_abs, src_abs],
            capture_output=True,
            text=True,
        )
        if completed.returncode != 0:
            raise OSError(
                f"mklink /J failed: {completed.stderr or completed.stdout or completed.returncode}"
            )
        return
    os.symlink(src_abs, dst_abs, target_is_directory=True)


def _unlink_local_skills_view(workspace: Path) -> None:
    _remove_skills_view(workspace / SKILLS_DIR)


def _remove_skills_view(skills_view: Path) -> None:
    if not skills_view.exists() and not skills_view.is_symlink():
        return
    if skills_view.is_symlink() or _is_junction(skills_view):
        skills_view.unlink(missing_ok=True)  # type: ignore[call-arg]
        if skills_view.exists():
            skills_view.rmdir()
        return
    # 子项可能是 junction：逐个摘链，避免 rmtree 误伤库
    if skills_view.is_dir():
        for child in list(skills_view.iterdir()):
            _remove_path(child)
        try:
            skills_view.rmdir()
        except OSError:
            shutil.rmtree(skills_view, ignore_errors=True)


def _remove_path(path: Path) -> None:
    if path.is_symlink() or _is_junction(path):
        try:
            path.unlink()
        except OSError:
            try:
                path.rmdir()
            except OSError:
                pass
        return
    if path.is_dir():
        shutil.rmtree(path, ignore_errors=True)
        return
    if path.exists():
        path.unlink(missing_ok=True)  # type: ignore[call-arg]


def _is_junction(path: Path) -> bool:
    if os.name != "nt":
        return False
    try:
        return bool(path.is_dir() and (path.stat().st_file_attributes & 0x400))  # type: ignore[attr-defined]
    except Exception:
        return False


def _detect_new_or_changed_skills(lib_root: Path, before_names: List[str]) -> List[str]:
    """local 链接模式下：回报执行后库中新增的 skill 名（变更文件已在原目录）。"""
    before = set(before_names)
    after = set(_list_enabled_skill_names(lib_root, set()))
    return sorted(after - before)


def _incremental_write_skill_file(lib_root: Path, rel: str, data: bytes) -> bool:
    """写入 runtime/skills 下单文件；内容相同则跳过。返回是否发生写入。"""
    rel_path = Path(rel.replace("\\", "/"))
    if not rel_path.parts or any(p in (".", "..") for p in rel_path.parts):
        return False
    if any(part.startswith(".") for part in rel_path.parts):
        return False
    target = (lib_root / rel_path).resolve()
    try:
        target.relative_to(lib_root.resolve())
    except ValueError:
        return False
    if target.exists() and target.is_file():
        try:
            if target.read_bytes() == data:
                return False
        except OSError:
            pass
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_bytes(data)
    return True


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


def _exec_e2b(
    session_id: str,
    command: str,
    workspace: Path,
    lib_root: Optional[Path],
    disabled: set[str],
    timeout_sec: int,
    max_output_chars: int,
    use_skill_session: bool | None = None,
    produced_paths: Optional[List[Path]] = None,
    owner_key: str | None = None,
) -> Tuple[Optional[int], str, str, bool, bool, List[str]]:
    """复用 owner 沙箱，在会话目录内增量同步并执行命令。"""
    owner = (owner_key or "").strip() or "visitor:anonymous"
    sid = (session_id or "").strip() or "anonymous"
    manager = get_user_sandbox_manager()
    lease = manager.acquire(owner, timeout_sec=timeout_sec)
    try:
        with manager.session_gate(owner, sid):
            sandbox = lease.sandbox
            remote_root = session_remote_root(sid)
            mkdir_remote(sandbox, remote_root)
            _, _, _, manifest = push_workspace(
                sandbox,
                workspace,
                sid,
                owner_key=owner,
                sandbox_id=lease.sandbox_id,
                generation=lease.generation,
                extra_files={},
            )
            uploaded = {
                f"{remote_root}/{rel}": (item.size, item.mtime_ns)
                for rel, item in manifest.files.items()
            }

            if lib_root is not None and lib_root.is_dir():
                only_names = set(_resolve_skills_to_push(command, lib_root, disabled))
                _e2b_push_skills_incremental(
                    sandbox,
                    lib_root,
                    remote_root,
                    disabled,
                    uploaded,
                    only_names=only_names,
                )
                for skill_name in only_names:
                    skill_dir = lib_root / skill_name
                    if not skill_dir.is_dir():
                        continue
                    for path in skill_dir.rglob("*"):
                        if not path.is_file() or path.is_symlink():
                            continue
                        try:
                            rel = path.resolve().relative_to(skill_dir.resolve())
                        except ValueError:
                            continue
                        if any(part.startswith(".") for part in rel.parts):
                            continue
                        if any(part in _SKIP_DIR_NAMES for part in rel.parts):
                            continue
                        record_uploaded(
                            workspace,
                            f"{SKILLS_DIR}/{skill_name}/{rel.as_posix()}",
                            path,
                            owner_key=owner,
                            sandbox_id=lease.sandbox_id,
                            generation=lease.generation,
                        )

            before = snapshot_remote_files(
                sandbox, remote_root, skip_top=("input", "skills")
            )
            exit_code, stdout, stderr, timed_out = _e2b_run_command(
                sandbox, command, remote_root, timeout_sec
            )
            changed = download_changed_files(
                sandbox,
                remote_root,
                workspace,
                before,
                produced_paths,
                skip_top=("input", "skills"),
            )
            for rel in changed:
                local_path = workspace / rel
                if local_path.is_file():
                    record_uploaded(
                        workspace,
                        rel,
                        local_path,
                        owner_key=owner,
                        sandbox_id=lease.sandbox_id,
                        generation=lease.generation,
                    )

            synced: List[str] = []
            if lib_root is not None:
                synced = _e2b_incremental_sync_skills(
                    sandbox, remote_root, lib_root, uploaded
                )

            stdout, truncated_stdout = _truncate_text(stdout, max_output_chars)
            stderr, truncated_stderr = _truncate_text(stderr, max_output_chars)
            return (
                exit_code,
                stdout,
                stderr,
                truncated_stdout or truncated_stderr,
                timed_out,
                synced,
            )
    finally:
        manager.release(owner)


def _snapshot_local_workspace(workspace: Path) -> Dict[str, Tuple[int, int]]:
    snapshot: Dict[str, Tuple[int, int]] = {}
    if not workspace.is_dir():
        return snapshot
    for path in workspace.rglob("*"):
        if not path.is_file() or path.is_symlink():
            continue
        try:
            rel = path.resolve().relative_to(workspace.resolve())
        except ValueError:
            continue
        if _should_skip_rel(rel, skip_top={SKILLS_DIR, "input"}):
            continue
        snapshot[rel.as_posix()] = _file_sig(path)
    return snapshot


def _changed_local_workspace_files(
    workspace: Path, before: Dict[str, Tuple[int, int]]
) -> List[Path]:
    changed: List[Path] = []
    for rel, signature in _snapshot_local_workspace(workspace).items():
        if before.get(rel) != signature:
            changed.append(workspace / rel)
    return changed


def _relative_workspace_path(path: Path, workspace: Path) -> str:
    try:
        return path.resolve().relative_to(workspace.resolve()).as_posix()
    except ValueError:
        return path.name


def _e2b_push_skills_incremental(
    sandbox: Any,
    lib_root: Path,
    remote_root: str,
    disabled: set[str],
    uploaded: Dict[str, Tuple[int, int]],
    only_names: Optional[set[str]] = None,
) -> Tuple[int, int]:
    """runtime/skills → 沙箱 skills/：只推新增/变更的 skill 文件。

    only_names 为 None 时推全部启用包；空集合不推；非空则只推这些名。
    """
    if only_names is not None and not only_names:
        return 0, 0
    remote_skills = f"{remote_root}/{SKILLS_DIR}"
    batch: list[dict[str, Any]] = []
    pending_sigs: Dict[str, Tuple[int, int]] = {}
    uploaded_n = 0
    skipped_n = 0
    for skill_dir in sorted(lib_root.iterdir()):
        if not skill_dir.is_dir() or skill_dir.name.startswith("."):
            continue
        if skill_dir.name in disabled:
            continue
        if only_names is not None and skill_dir.name not in only_names:
            continue
        for path in skill_dir.rglob("*"):
            if not path.is_file() or path.is_symlink():
                continue
            try:
                rel = path.resolve().relative_to(skill_dir.resolve())
            except ValueError:
                continue
            if any(part.startswith(".") for part in rel.parts):
                continue
            if any(part in _SKIP_DIR_NAMES for part in rel.parts):
                continue
            remote_path = f"{remote_skills}/{skill_dir.name}/{rel.as_posix()}"
            if not _e2b_remote_path_ok(remote_path):
                logger.warning(
                    "[bash_sandbox] skip unsafe skill path for e2b: skill={} name={!r}",
                    skill_dir.name,
                    path.name[:120],
                )
                skipped_n += 1
                continue
            sig = _file_sig(path)
            if uploaded.get(remote_path) == sig:
                skipped_n += 1
                continue
            batch.append({"path": remote_path, "data": path.read_bytes()})
            pending_sigs[remote_path] = sig
            uploaded_n += 1
            if len(batch) >= 32:
                _e2b_write_files(sandbox, batch)
                uploaded.update(pending_sigs)
                batch = []
                pending_sigs = {}
    if batch:
        _e2b_write_files(sandbox, batch)
        uploaded.update(pending_sigs)
    return uploaded_n, skipped_n


def _e2b_upload_tree(
    sandbox: Any,
    local_root: Path,
    remote_root: str,
    *,
    skip_top_dirs: set[str],
    label: str,
) -> None:
    """无会话 manifest 的一次性上传（测试/兼容）；生产路径走 incremental。"""
    if not local_root.is_dir():
        return
    batch: list[dict[str, Any]] = []
    count = 0
    for path in local_root.rglob("*"):
        if not path.is_file() or path.is_symlink():
            continue
        try:
            rel = path.resolve().relative_to(local_root.resolve())
        except ValueError:
            continue
        if _should_skip_rel(rel, skip_top=skip_top_dirs):
            continue
        remote_path = f"{remote_root}/{rel.as_posix()}"
        if not _e2b_remote_path_ok(remote_path):
            logger.warning(
                "[bash_sandbox] skip unsafe path in upload_tree label={} name={!r}",
                label,
                path.name[:120],
            )
            continue
        batch.append({"path": remote_path, "data": path.read_bytes()})
        count += 1
        if len(batch) >= 32:
            _e2b_write_files(sandbox, batch)
            batch = []
    if batch:
        _e2b_write_files(sandbox, batch)
    logger.info("[bash_sandbox] e2b uploaded {}={} files={}", label, local_root, count)


def _e2b_write_files(sandbox: Any, files: list[dict[str, Any]]) -> None:
    _sync_write_files(sandbox, files, label="bash_sandbox")


def _e2b_run_command(
    sandbox: Any,
    command: str,
    remote_root: str,
    timeout_sec: int,
) -> Tuple[Optional[int], str, str, bool]:
    commands = getattr(sandbox, "commands", None)
    run = getattr(commands, "run", None) if commands is not None else None
    if not callable(run):
        return 1, "", "E2B sandbox does not support commands.run", False
    try:
        result = run(
            command,
            envs={"DISPLAY": ":0"},
            cwd=remote_root,
            timeout=max(1, int(timeout_sec)),
        )
    except Exception as exc:
        message = str(exc).lower()
        if "timeout" in message or "timed out" in message:
            return None, "", f"execution timed out after {timeout_sec}s", True
        logger.warning("[bash_sandbox] commands.run failed: {}", exc)
        return 1, "", str(exc), False
    exit_code = getattr(result, "exit_code", None)
    if exit_code is None:
        exit_code = getattr(result, "error", None) and 1 or 0
    stdout = str(getattr(result, "stdout", "") or "")
    stderr = str(getattr(result, "stderr", "") or "")
    return int(exit_code) if exit_code is not None else 0, stdout, stderr, False


def _e2b_incremental_sync_skills(
    sandbox: Any,
    remote_root: str,
    lib_root: Path,
    uploaded: Optional[Dict[str, Tuple[int, int]]] = None,
) -> List[str]:
    """远端 skills → runtime/skills：按 sha256 拉取新增/内容变更文件，直接写库。"""
    remote_skills = f"{remote_root}/{SKILLS_DIR}"
    list_script = f"""
import hashlib
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
            if any(part.startswith(".") for part in Path(rel).parts):
                continue
            digest = hashlib.sha256()
            with p.open("rb") as handle:
                while True:
                    chunk = handle.read(65536)
                    if not chunk:
                        break
                    digest.update(chunk)
            st = p.stat()
            files.append({{
                "rel": rel,
                "size": int(st.st_size),
                "sha256": digest.hexdigest(),
            }})
print("__SKILLS_META__" + json.dumps(files, ensure_ascii=True))
"""
    try:
        stdout = run_remote_python(sandbox, list_script, timeout=60)
    except Exception as exc:
        logger.warning("[bash_sandbox] e2b list skills failed: {}", exc)
        return []

    remote_files: list[dict[str, Any]] = []
    for line in reversed((stdout or "").splitlines()):
        if "__SKILLS_META__" in line:
            import json

            payload = line.split("__SKILLS_META__", 1)[1].strip()
            try:
                remote_files = list(json.loads(payload or "[]"))
            except Exception:
                remote_files = []
            break

    lib_root.mkdir(parents=True, exist_ok=True)
    files_api = getattr(sandbox, "files", None)
    if files_api is None:
        return []

    synced_skills: set[str] = set()
    downloaded = 0
    skipped = 0
    for item in remote_files:
        rel = str(item.get("rel") or "").strip().replace("\\", "/")
        if not rel or "/" not in rel and rel.startswith("."):
            continue
        skill_name = rel.split("/", 1)[0]
        if not skill_name or skill_name.startswith("."):
            continue
        local_path = lib_root / rel
        remote_hash = str(item.get("sha256") or "").strip().lower()
        if remote_hash and local_path.is_file():
            try:
                if _file_sha256(local_path) == remote_hash:
                    skipped += 1
                    continue
            except OSError:
                pass

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
            if _incremental_write_skill_file(lib_root, rel, data):
                synced_skills.add(skill_name)
                downloaded += 1
                if uploaded is not None:
                    uploaded[remote_path] = _file_sig(lib_root / rel)
            else:
                skipped += 1
        except Exception as exc:
            logger.warning(
                "[bash_sandbox] e2b incremental download {} failed: {}",
                remote_path,
                exc,
            )

    synced = sorted(synced_skills)
    logger.info(
        "[bash_sandbox] e2b incremental sync skills downloaded={} skipped={} synced={}",
        downloaded,
        skipped,
        synced,
    )
    return synced


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
