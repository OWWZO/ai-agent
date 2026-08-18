# -*- coding: utf-8 -*-
"""会话 bash 沙箱：与 code_execution 共用 CODE_SANDBOX_BACKEND（local | e2b）。

- skills：从 skillLibraryRoot（runtime/skills）直传沙箱，不经 workspace copytree
- local：workspace/skills 目录链接到库
- e2b 双路径：
  - 默认（命令不含 skills/ 且 session 未升级）：一次性建→推 workspace→exec→kill（对齐 code_execution）
  - skill 会话池：命令含 skills/ 首次升级后强粘性；复用沙箱 + workspace/skills 增量推送 + skills 回写；
    每次访问刷新 idle TTL（默认 5min）
"""
from __future__ import annotations

import atexit
import asyncio
import os
import shutil
import threading
import time
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple

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
_DEFAULT_IDLE_TTL_SEC = 300  # 5 minutes，对齐「会话复用 + 空闲回收」
# 增量推送时跳过的顶层/任意段目录名（对齐常见构建产物，避免拖垮 sync）
_SKIP_DIR_NAMES = frozenset({
    SKILLS_DIR,  # workspace 树扫描时跳过；skills 走独立增量通道
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
})
# Linux NAME_MAX=255 **字节**；中文 UTF-8 约 3B/字，任务描述当文件名极易超限
_E2B_MAX_NAME_BYTES = 200
_E2B_MAX_PATH_BYTES = 1000


def _idle_ttl_sec() -> int:
    raw = (os.getenv("BASH_SANDBOX_IDLE_TTL_SEC") or "").strip()
    if raw:
        return max(30, int(raw))
    return _DEFAULT_IDLE_TTL_SEC


def _command_needs_skills(command: str) -> bool:
    """启发式：命令文本是否引用 skills/ 路径（含 Windows 反斜杠）。"""
    text = command or ""
    lowered = text.lower()
    return "skills/" in lowered or "skills\\" in lowered


async def run_bash_sandbox(body: BashSandboxRequest) -> BashSandboxResponse:
    started = time.time()
    workspace = Path(body.workspace_root).expanduser().resolve()
    workspace.mkdir(parents=True, exist_ok=True)

    lib_root: Optional[Path] = None
    if body.skill_library_root and str(body.skill_library_root).strip():
        lib_root = Path(body.skill_library_root).expanduser().resolve()

    disabled = {n.strip() for n in (body.disabled_skill_names or []) if n and str(n).strip()}
    skill_names: List[str] = []
    synced: List[str] = []
    backend = get_sandbox_backend()

    try:
        if backend == "e2b":
            use_skill_session = _ensure_skill_mode(body.request_id, body.command)
            if use_skill_session and lib_root is not None and lib_root.is_dir():
                skill_names = _list_enabled_skill_names(lib_root, disabled)
            exit_code, stdout, stderr, truncated, timed_out, synced = await asyncio.to_thread(
                _exec_e2b,
                body.request_id,
                body.command,
                workspace,
                lib_root,
                disabled,
                int(body.timeout_seconds),
                int(body.max_output_chars),
                use_skill_session,
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
            # junction/symlink 指向库目录时，新建 skill 已落在 lib；扫一遍上报名称即可
            if lib_root is not None and lib_root.is_dir():
                synced = _detect_new_or_changed_skills(lib_root, skill_names)
    finally:
        if backend != "e2b":
            _unlink_local_skills_view(workspace)

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


def _link_skills_for_local(lib_root: Path, workspace: Path, disabled: set[str]) -> List[str]:
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


# ── e2b backend：默认一次性；skill 强粘性后会话级复用 ───────────────────────


@dataclass
class _SessionSandbox:
    session_id: str
    sandbox: Any
    remote_root: str
    # remote_path -> (size, mtime_ns)；会话内增量推送的已上传指纹
    uploaded: Dict[str, Tuple[int, int]] = field(default_factory=dict)
    last_used_at: float = field(default_factory=time.time)
    lock: threading.Lock = field(default_factory=threading.Lock)


_pool_guard = threading.Lock()
_pool: Dict[str, _SessionSandbox] = {}
# 强粘性：一旦本进程内某 session 的 bash 命令命中 skills/，后续全部走会话池+skill 推送
_skill_mode_guard = threading.Lock()
_skill_mode_sessions: set[str] = set()
_reaper_started = False
_reaper_lock = threading.Lock()


def _normalize_session_id(session_id: str) -> str:
    return (session_id or "").strip() or "anonymous"


def _session_in_skill_mode(session_id: str) -> bool:
    sid = _normalize_session_id(session_id)
    with _skill_mode_guard:
        return sid in _skill_mode_sessions


def _mark_skill_mode(session_id: str) -> bool:
    """标记 session 进入 skill 强粘性。返回是否为首次升级。"""
    sid = _normalize_session_id(session_id)
    with _skill_mode_guard:
        if sid in _skill_mode_sessions:
            return False
        _skill_mode_sessions.add(sid)
        return True


def _ensure_skill_mode(session_id: str, command: str) -> bool:
    """若已粘性或本次命令引用 skills/，则进入/保持 skill 模式并返回 True。"""
    if _session_in_skill_mode(session_id):
        return True
    if not _command_needs_skills(command):
        return False
    first = _mark_skill_mode(session_id)
    if first:
        logger.info(
            "[bash_sandbox] skill-mode upgrade session={} commandHint={!r}",
            _normalize_session_id(session_id),
            (command or "")[:120],
        )
    return True


def _clear_skill_mode_sessions() -> None:
    with _skill_mode_guard:
        _skill_mode_sessions.clear()


def _ensure_reaper() -> None:
    global _reaper_started
    with _reaper_lock:
        if _reaper_started:
            return
        _reaper_started = True

        def _loop() -> None:
            while True:
                time.sleep(min(30, max(5, _idle_ttl_sec() // 10)))
                try:
                    _reap_idle_sessions()
                except Exception:
                    logger.exception("[bash_sandbox] session reaper failed")

        t = threading.Thread(target=_loop, name="bash-sandbox-reaper", daemon=True)
        t.start()


def _reap_idle_sessions() -> None:
    ttl = _idle_ttl_sec()
    now = time.time()
    expired: List[_SessionSandbox] = []
    with _pool_guard:
        for sid, entry in list(_pool.items()):
            if now - entry.last_used_at >= ttl:
                expired.append(_pool.pop(sid))
    for entry in expired:
        _destroy_session_sandbox(entry, reason="idle-ttl")


def _destroy_session_sandbox(entry: _SessionSandbox, reason: str) -> None:
    logger.info(
        "[bash_sandbox] destroy session={} reason={} idleAgeMs={}",
        entry.session_id,
        reason,
        int((time.time() - entry.last_used_at) * 1000),
    )
    try:
        kill = getattr(entry.sandbox, "kill", None)
        if callable(kill):
            kill()
    except Exception:
        pass


def _shutdown_all_sessions() -> None:
    with _pool_guard:
        entries = list(_pool.values())
        _pool.clear()
    for entry in entries:
        _destroy_session_sandbox(entry, reason="process-exit")
    _clear_skill_mode_sessions()


atexit.register(_shutdown_all_sessions)


def _create_e2b_sandbox(timeout_sec: int) -> Any:
    from e2b_code_interpreter import Sandbox

    idle = _idle_ttl_sec()
    # 沙箱云端 lifetime 必须盖住空闲 TTL，否则未到我们的 reap 就被 E2B 杀掉
    lifetime = max(
        get_e2b_sandbox_timeout_seconds(float(timeout_sec)),
        idle + int(timeout_sec) + 120,
    )
    create_kwargs: dict[str, Any] = {
        "api_key": require_e2b_api_key(),
        "timeout": lifetime,
    }
    template = get_e2b_template()
    if template:
        create_kwargs["template"] = template
    return Sandbox.create(**create_kwargs)


def _touch_e2b_timeout(sandbox: Any, timeout_sec: int) -> None:
    """复用时尽量续命，避免云端先于 idle TTL 回收。"""
    idle = _idle_ttl_sec()
    lifetime = max(
        get_e2b_sandbox_timeout_seconds(float(timeout_sec)),
        idle + int(timeout_sec) + 120,
    )
    setter = getattr(sandbox, "set_timeout", None)
    if callable(setter):
        try:
            setter(lifetime)
        except Exception as exc:
            logger.debug("[bash_sandbox] set_timeout skipped: {}", exc)


def _acquire_session_sandbox(session_id: str, timeout_sec: int) -> Tuple[_SessionSandbox, bool]:
    """返回 (entry, created)。同 session 复用 RUNNING 实例。"""
    _ensure_reaper()
    _reap_idle_sessions()
    sid = (session_id or "").strip() or "anonymous"

    with _pool_guard:
        entry = _pool.get(sid)
        if entry is None:
            entry = _SessionSandbox(
                session_id=sid,
                sandbox=None,
                remote_root=get_e2b_workdir(),
            )
            _pool[sid] = entry

    with entry.lock:
        if entry.sandbox is not None:
            _touch_e2b_timeout(entry.sandbox, timeout_sec)
            entry.last_used_at = time.time()
            logger.info("[bash_sandbox] reuse session={}", sid)
            return entry, False

        try:
            sandbox = _create_e2b_sandbox(timeout_sec)
            entry.sandbox = sandbox
            entry.remote_root = get_e2b_workdir()
            entry.uploaded.clear()
            entry.last_used_at = time.time()
            _e2b_mkdir(sandbox, entry.remote_root)
            logger.info("[bash_sandbox] create session={} remote={}", sid, entry.remote_root)
            return entry, True
        except Exception:
            with _pool_guard:
                cur = _pool.get(sid)
                if cur is entry and entry.sandbox is None:
                    _pool.pop(sid, None)
            raise


def _mark_session_dead(session_id: str) -> None:
    sid = (session_id or "").strip() or "anonymous"
    with _pool_guard:
        entry = _pool.pop(sid, None)
    if entry is not None:
        with entry.lock:
            _destroy_session_sandbox(entry, reason="mark-dead")


def _exec_e2b(
    session_id: str,
    command: str,
    workspace: Path,
    lib_root: Optional[Path],
    disabled: set[str],
    timeout_sec: int,
    max_output_chars: int,
    use_skill_session: bool | None = None,
) -> Tuple[Optional[int], str, str, bool, bool, List[str]]:
    """按 skill 模式分流：一次性建跑杀 vs 会话池+skills。"""
    if use_skill_session is None:
        use_skill_session = _ensure_skill_mode(session_id, command)
    if use_skill_session:
        return _exec_e2b_skill_session(
            session_id, command, workspace, lib_root, disabled, timeout_sec, max_output_chars
        )
    return _exec_e2b_ephemeral(
        session_id, command, workspace, timeout_sec, max_output_chars
    )


def _create_ephemeral_e2b_sandbox(timeout_sec: int) -> Any:
    """一次性沙箱：lifetime 只盖住本次命令，不按 idle TTL 拉长。"""
    from e2b_code_interpreter import Sandbox

    create_kwargs: dict[str, Any] = {
        "api_key": require_e2b_api_key(),
        "timeout": get_e2b_sandbox_timeout_seconds(float(timeout_sec)),
    }
    template = get_e2b_template()
    if template:
        create_kwargs["template"] = template
    return Sandbox.create(**create_kwargs)


def _exec_e2b_ephemeral(
    session_id: str,
    command: str,
    workspace: Path,
    timeout_sec: int,
    max_output_chars: int,
) -> Tuple[Optional[int], str, str, bool, bool, List[str]]:
    """无 skill：create → 推 workspace → exec → kill（对齐 code_execution）。"""
    sid = _normalize_session_id(session_id)
    sandbox = _create_ephemeral_e2b_sandbox(timeout_sec)
    remote_root = get_e2b_workdir()
    uploaded: Dict[str, Tuple[int, int]] = {}
    try:
        _e2b_mkdir(sandbox, remote_root)
        ws_up, ws_skip = _e2b_push_workspace_incremental(
            sandbox, workspace, remote_root, uploaded
        )
        logger.info(
            "[bash_sandbox] ephemeral push session={} workspace(up={},skip={}) skills=skipped",
            sid,
            ws_up,
            ws_skip,
        )
        exit_code, stdout, stderr, timed_out = _e2b_run_command(
            sandbox, command, remote_root, timeout_sec
        )
        stdout, t1 = _truncate_text(stdout, max_output_chars)
        stderr, t2 = _truncate_text(stderr, max_output_chars)
        logger.info(
            "[bash_sandbox] ephemeral done session={} exit={} timedOut={}",
            sid,
            exit_code,
            timed_out,
        )
        return exit_code, stdout, stderr, t1 or t2, timed_out, []
    finally:
        try:
            kill = getattr(sandbox, "kill", None)
            if callable(kill):
                kill()
        except Exception:
            logger.debug("[bash_sandbox] ephemeral kill failed session={}", sid)


def _exec_e2b_skill_session(
    session_id: str,
    command: str,
    workspace: Path,
    lib_root: Optional[Path],
    disabled: set[str],
    timeout_sec: int,
    max_output_chars: int,
) -> Tuple[Optional[int], str, str, bool, bool, List[str]]:
    """skill 强粘性会话：懒建/复用 → workspace/skills 增量推送 → exec → skills 增量回写。"""
    entry, created = _acquire_session_sandbox(session_id, timeout_sec)
    synced: List[str] = []

    with entry.lock:
        sandbox = entry.sandbox
        remote_root = entry.remote_root
        assert sandbox is not None
        try:
            ws_up, ws_skip = _e2b_push_workspace_incremental(
                sandbox, workspace, remote_root, entry.uploaded
            )
            sk_up, sk_skip = (0, 0)
            if lib_root is not None and lib_root.is_dir():
                sk_up, sk_skip = _e2b_push_skills_incremental(
                    sandbox, lib_root, remote_root, disabled, entry.uploaded
                )
            logger.info(
                "[bash_sandbox] skill-session push session={} created={} workspace(up={},skip={}) skills(up={},skip={})",
                entry.session_id,
                created,
                ws_up,
                ws_skip,
                sk_up,
                sk_skip,
            )

            exit_code, stdout, stderr, timed_out = _e2b_run_command(
                sandbox, command, remote_root, timeout_sec
            )
            stdout, t1 = _truncate_text(stdout, max_output_chars)
            stderr, t2 = _truncate_text(stderr, max_output_chars)

            if lib_root is not None:
                synced = _e2b_incremental_sync_skills(sandbox, remote_root, lib_root)

            entry.last_used_at = time.time()
            logger.info(
                "[bash_sandbox] skill-session done session={} created={} exit={} timedOut={} synced={}",
                entry.session_id,
                created,
                exit_code,
                timed_out,
                synced,
            )
            return exit_code, stdout, stderr, t1 or t2, timed_out, synced
        except Exception:
            # 通道异常：置 dead，下次重建（对齐 kimicode markDead）
            logger.exception("[bash_sandbox] exec channel failed session={}", entry.session_id)
            entry.sandbox = None
            entry.uploaded.clear()
            try:
                kill = getattr(sandbox, "kill", None)
                if callable(kill):
                    kill()
            except Exception:
                pass
            with _pool_guard:
                if _pool.get(entry.session_id) is entry:
                    _pool.pop(entry.session_id, None)
            raise


def _file_sig(path: Path) -> Tuple[int, int]:
    st = path.stat()
    return int(st.st_size), int(getattr(st, "st_mtime_ns", int(st.st_mtime * 1_000_000_000)))


def _should_skip_rel(rel: Path, *, skip_top: set[str] | None = None) -> bool:
    if not rel.parts:
        return True
    if skip_top and rel.parts[0] in skip_top:
        return True
    for part in rel.parts:
        if part.startswith("."):
            return True
        if part in _SKIP_DIR_NAMES:
            return True
    return False


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


def _utf8_len(text: str) -> int:
    return len((text or "").encode("utf-8"))


def _e2b_path_component_ok(name: str) -> bool:
    """单段文件/目录名是否可安全写入 E2B（按 UTF-8 字节，不是 Unicode 字符数）。"""
    if not name or name in {".", ".."}:
        return False
    if _utf8_len(name) > _E2B_MAX_NAME_BYTES:
        return False
    # 拒绝 NUL / 路径分隔渗入单段
    if "\x00" in name or "/" in name or "\\" in name:
        return False
    return True


def _e2b_remote_path_ok(remote_path: str) -> bool:
    if not remote_path or _utf8_len(remote_path) > _E2B_MAX_PATH_BYTES:
        return False
    # 必须按 POSIX 分段：Windows 上 Path("/home/...") 会把首段弄成 "\\home" 导致误杀
    normalized = remote_path.replace("\\", "/").strip()
    for part in normalized.split("/"):
        if not part or part == ".":
            continue
        if not _e2b_path_component_ok(part):
            return False
    return True


def _e2b_push_workspace_incremental(
    sandbox: Any,
    workspace: Path,
    remote_root: str,
    uploaded: Dict[str, Tuple[int, int]],
) -> Tuple[int, int]:
    """宿主机 workspace → 沙箱：只推新增/变更（跳过 skills/、构建缓存、超长非法文件名）。"""
    if not workspace.is_dir():
        return 0, 0
    batch: list[dict[str, Any]] = []
    pending_sigs: Dict[str, Tuple[int, int]] = {}
    uploaded_n = 0
    skipped_n = 0
    for path in workspace.rglob("*"):
        if not path.is_file() or path.is_symlink():
            continue
        try:
            rel = path.resolve().relative_to(workspace.resolve())
        except ValueError:
            continue
        if _should_skip_rel(rel, skip_top={SKILLS_DIR}):
            continue
        remote_path = f"{remote_root}/{rel.as_posix()}"
        if not _e2b_remote_path_ok(remote_path):
            logger.warning(
                "[bash_sandbox] skip unsafe workspace path for e2b (name too long or invalid): "
                "bytes={} name={!r}",
                _utf8_len(path.name),
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


def _e2b_push_skills_incremental(
    sandbox: Any,
    lib_root: Path,
    remote_root: str,
    disabled: set[str],
    uploaded: Dict[str, Tuple[int, int]],
) -> Tuple[int, int]:
    """runtime/skills → 沙箱 skills/：只推新增/变更的 skill 文件。"""
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
    files_api = getattr(sandbox, "files", None)
    if files_api is None:
        raise RuntimeError("E2B sandbox has no files API")
    safe: list[dict[str, Any]] = []
    for item in files:
        path = str(item.get("path") or "")
        if not _e2b_remote_path_ok(path):
            logger.warning(
                "[bash_sandbox] drop unsafe e2b write path bytes={} path={!r}",
                _utf8_len(Path(path).name),
                Path(path).name[:120],
            )
            continue
        safe.append(item)
    if not safe:
        return
    write_files = getattr(files_api, "write_files", None)
    if callable(write_files):
        write_files(safe)
        return
    for item in safe:
        files_api.write(item["path"], item["data"])

def _e2b_run_command(
    sandbox: Any,
    command: str,
    remote_root: str,
    timeout_sec: int,
) -> Tuple[Optional[int], str, str, bool]:
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
            logger.warning("[bash_sandbox] commands.run failed, fallback run_code: {}", exc)

    script = f"""
import subprocess
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


def _e2b_incremental_sync_skills(sandbox: Any, remote_root: str, lib_root: Path) -> List[str]:
    """远端 skills → runtime/skills：只拉取新增/内容变更文件，直接写库。"""
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
            if any(part.startswith(".") for part in Path(rel).parts):
                continue
            st = p.stat()
            files.append({{"rel": rel, "size": int(st.st_size)}})
print("__SKILLS_META__" + json.dumps(files, ensure_ascii=True))
"""
    try:
        execution = sandbox.run_code(list_script, timeout=60)
    except Exception as exc:
        logger.warning("[bash_sandbox] e2b list skills failed: {}", exc)
        return []

    stdout, _ = _extract_e2b_logs(execution)
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
        remote_size = item.get("size")
        if local_path.is_file():
            try:
                if remote_size is not None and local_path.stat().st_size == int(remote_size):
                    # size 相同再比 hash，避免误跳过；大文件只比 size 也可，这里对同 size 再读本地 hash 前先拉远端
                    # 为少一次网络：同 size 默认跳过（skill 脚本变更几乎都会改 size）
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
            else:
                skipped += 1
        except Exception as exc:
            logger.warning("[bash_sandbox] e2b incremental download {} failed: {}", remote_path, exc)

    synced = sorted(synced_skills)
    logger.info(
        "[bash_sandbox] e2b incremental sync skills downloaded={} skipped={} synced={}",
        downloaded,
        skipped,
        synced,
    )
    return synced


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
