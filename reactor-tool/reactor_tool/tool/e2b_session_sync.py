# -*- coding: utf-8 -*-
"""会话目录增量同步：本地 workspace ↔ E2B sessions/{sessionId}/。"""

from __future__ import annotations

import hashlib
import json
import shlex
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, Dict, Iterable, List, Optional, Tuple

from loguru import logger

from reactor_tool.tool.e2b_file_upload import write_e2b_files
from reactor_tool.tool.sandbox_backend_config import get_e2b_workdir

SKILLS_DIR = "skills"
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
_E2B_MAX_NAME_BYTES = 200
_E2B_MAX_PATH_BYTES = 1000
_FINGERPRINT_REL = ".reactor/e2b-sync.json"


@dataclass
class FileFingerprint:
    size: int
    mtime_ns: int
    sha256: str = ""


@dataclass
class SyncManifest:
    owner_key: str = ""
    sandbox_id: str = ""
    generation: int = 0
    files: Dict[str, FileFingerprint] = field(default_factory=dict)


def normalize_session_id(session_id: str) -> str:
    return (session_id or "").strip() or "anonymous"


def session_remote_root(session_id: str, workdir: str | None = None) -> str:
    root = (workdir or get_e2b_workdir()).rstrip("/")
    return f"{root}/sessions/{normalize_session_id(session_id)}"


def fingerprint_path(workspace: Path) -> Path:
    return workspace / _FINGERPRINT_REL


def load_manifest(workspace: Path) -> SyncManifest:
    path = fingerprint_path(workspace)
    if not path.is_file():
        return SyncManifest()
    try:
        raw = json.loads(path.read_text(encoding="utf-8"))
    except Exception:
        return SyncManifest()
    files: Dict[str, FileFingerprint] = {}
    for rel, item in dict(raw.get("files") or {}).items():
        if not isinstance(item, dict):
            continue
        files[str(rel).replace("\\", "/")] = FileFingerprint(
            size=int(item.get("size") or 0),
            mtime_ns=int(item.get("mtimeNs") or 0),
            sha256=str(item.get("sha256") or ""),
        )
    return SyncManifest(
        owner_key=str(raw.get("ownerKey") or ""),
        sandbox_id=str(raw.get("sandboxId") or ""),
        generation=int(raw.get("generation") or 0),
        files=files,
    )


def save_manifest(workspace: Path, manifest: SyncManifest) -> None:
    path = fingerprint_path(workspace)
    path.parent.mkdir(parents=True, exist_ok=True)
    payload = {
        "ownerKey": manifest.owner_key,
        "sandboxId": manifest.sandbox_id,
        "generation": manifest.generation,
        "files": {
            rel: {
                "size": item.size,
                "mtimeNs": item.mtime_ns,
                "sha256": item.sha256,
            }
            for rel, item in sorted(manifest.files.items())
        },
    }
    path.write_text(json.dumps(payload, ensure_ascii=True, indent=2), encoding="utf-8")


def file_sig(path: Path) -> Tuple[int, int]:
    st = path.stat()
    return int(st.st_size), int(
        getattr(st, "st_mtime_ns", int(st.st_mtime * 1_000_000_000))
    )


def file_sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        while True:
            chunk = handle.read(65536)
            if not chunk:
                break
            digest.update(chunk)
    return digest.hexdigest()


def utf8_len(text: str) -> int:
    return len((text or "").encode("utf-8"))


def e2b_path_component_ok(name: str) -> bool:
    if not name or name in {".", ".."}:
        return False
    if utf8_len(name) > _E2B_MAX_NAME_BYTES:
        return False
    if "\x00" in name or "/" in name or "\\" in name:
        return False
    return True


def e2b_remote_path_ok(remote_path: str) -> bool:
    if not remote_path or utf8_len(remote_path) > _E2B_MAX_PATH_BYTES:
        return False
    normalized = remote_path.replace("\\", "/").strip()
    for part in normalized.split("/"):
        if not part or part == ".":
            continue
        if not e2b_path_component_ok(part):
            return False
    return True


def should_skip_rel(rel: Path, *, skip_top: set[str] | None = None) -> bool:
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


def scan_local_files(
    root: Path, *, skip_top: set[str] | None = None
) -> Dict[str, Path]:
    found: Dict[str, Path] = {}
    if not root.is_dir():
        return found
    resolved_root = root.resolve()
    for path in root.rglob("*"):
        if not path.is_file() or path.is_symlink():
            continue
        try:
            rel = path.resolve().relative_to(resolved_root)
        except ValueError:
            continue
        if should_skip_rel(rel, skip_top=skip_top):
            continue
        found[rel.as_posix()] = path
    return found


def mkdir_remote(sandbox: Any, remote_root: str) -> None:
    commands = getattr(sandbox, "commands", None)
    run = getattr(commands, "run", None) if commands is not None else None
    if not callable(run):
        raise RuntimeError("E2B sandbox does not support commands.run")
    result = run(
        f"mkdir -p {shlex.quote(remote_root)}/skills "
        f"{shlex.quote(remote_root)}/input "
        f"{shlex.quote(remote_root)}/output",
        timeout=30,
    )
    exit_code = getattr(result, "exit_code", None)
    if exit_code not in (None, 0) or getattr(result, "error", None):
        detail = " ".join(
            part.strip()
            for part in (
                str(getattr(result, "stdout", "") or ""),
                str(getattr(result, "stderr", "") or ""),
            )
            if part and part.strip()
        )
        raise RuntimeError(detail or f"E2B mkdir failed with exit code {exit_code}")


def run_remote_python(sandbox: Any, script: str, *, timeout: int = 60) -> str:
    commands = getattr(sandbox, "commands", None)
    run = getattr(commands, "run", None) if commands is not None else None
    if not callable(run):
        raise RuntimeError("E2B sandbox does not support commands.run")
    result = run(f"python -c {shlex.quote(script)}", timeout=timeout)
    exit_code = getattr(result, "exit_code", None)
    if exit_code not in (None, 0) or getattr(result, "error", None):
        detail = " ".join(
            part.strip()
            for part in (
                str(getattr(result, "stdout", "") or ""),
                str(getattr(result, "stderr", "") or ""),
            )
            if part and part.strip()
        )
        raise RuntimeError(
            detail or f"E2B Python CLI failed with exit code {exit_code}"
        )
    return str(getattr(result, "stdout", "") or "")


def write_files(sandbox: Any, files: list[dict[str, Any]], *, label: str) -> None:
    files_api = getattr(sandbox, "files", None)
    if files_api is None:
        raise RuntimeError("E2B sandbox has no files API")
    safe = [item for item in files if e2b_remote_path_ok(str(item.get("path") or ""))]
    if not safe:
        return
    write_e2b_files(files_api, safe, label=label)


def remove_remote_file(sandbox: Any, remote_path: str) -> None:
    files_api = getattr(sandbox, "files", None)
    remover = getattr(files_api, "remove", None) if files_api is not None else None
    if callable(remover):
        remover(remote_path)
        return
    commands = getattr(sandbox, "commands", None)
    run = getattr(commands, "run", None) if commands is not None else None
    if callable(run):
        run(f"rm -f {shlex.quote(remote_path)}", timeout=30)


def push_workspace(
    sandbox: Any,
    workspace: Path,
    session_id: str,
    *,
    owner_key: str,
    sandbox_id: str,
    generation: int,
    extra_files: Optional[Dict[str, Path]] = None,
) -> Tuple[int, int, int, SyncManifest]:
    """推本地工作区到 sessions/{sessionId}/。返回 (upload, skip, removed, manifest)。"""
    remote_root = session_remote_root(session_id)
    mkdir_remote(sandbox, remote_root)
    manifest = load_manifest(workspace)
    if manifest.sandbox_id != sandbox_id or manifest.generation != generation:
        manifest = SyncManifest(
            owner_key=owner_key,
            sandbox_id=sandbox_id,
            generation=generation,
        )
    else:
        manifest.owner_key = owner_key
        manifest.sandbox_id = sandbox_id
        manifest.generation = generation

    local_files = scan_local_files(workspace)
    if extra_files:
        local_files.update(extra_files)

    uploaded_n = 0
    skipped_n = 0
    batch: list[dict[str, Any]] = []
    pending: Dict[str, FileFingerprint] = {}
    current_rels = set(local_files)

    for rel, path in sorted(local_files.items()):
        remote_path = f"{remote_root}/{rel}"
        if not e2b_remote_path_ok(remote_path):
            logger.warning(
                "[e2b_sync] skip unsafe path bytes={} name={!r}",
                utf8_len(path.name),
                path.name[:120],
            )
            skipped_n += 1
            continue
        size, mtime_ns = file_sig(path)
        prev = manifest.files.get(rel)
        if prev is not None and prev.size == size and prev.mtime_ns == mtime_ns:
            skipped_n += 1
            continue
        digest = file_sha256(path)
        if prev is not None and prev.sha256 == digest:
            manifest.files[rel] = FileFingerprint(size, mtime_ns, digest)
            skipped_n += 1
            continue
        batch.append({"path": remote_path, "data": path.read_bytes()})
        pending[rel] = FileFingerprint(size, mtime_ns, digest)
        uploaded_n += 1
        if len(batch) >= 32:
            write_files(sandbox, batch, label="session_sync")
            manifest.files.update(pending)
            batch = []
            pending = {}
    if batch:
        write_files(sandbox, batch, label="session_sync")
        manifest.files.update(pending)

    removed_n = 0
    stale = [
        rel
        for rel in list(manifest.files)
        if rel not in current_rels and not rel.startswith(f"{SKILLS_DIR}/")
    ]
    for rel in stale:
        remove_remote_file(sandbox, f"{remote_root}/{rel}")
        manifest.files.pop(rel, None)
        removed_n += 1

    save_manifest(workspace, manifest)
    return uploaded_n, skipped_n, removed_n, manifest


def record_uploaded(
    workspace: Path,
    rel: str,
    path: Path,
    *,
    owner_key: str,
    sandbox_id: str,
    generation: int,
) -> None:
    manifest = load_manifest(workspace)
    manifest.owner_key = owner_key
    manifest.sandbox_id = sandbox_id
    manifest.generation = generation
    size, mtime_ns = file_sig(path)
    manifest.files[rel.replace("\\", "/")] = FileFingerprint(
        size, mtime_ns, file_sha256(path)
    )
    save_manifest(workspace, manifest)


def snapshot_remote_files(
    sandbox: Any,
    remote_root: str,
    *,
    skip_top: Iterable[str] = ("input",),
) -> Dict[str, Tuple[int, int]]:
    skip = list(skip_top)
    script = f"""
import json
from pathlib import Path
root = Path({remote_root!r})
skip_top = set({skip!r})
skip_dirs = set({sorted(_SKIP_DIR_NAMES)!r})
files = {{}}
if root.is_dir():
    for path in root.rglob('*'):
        if not path.is_file() or path.is_symlink():
            continue
        try:
            rel = path.resolve().relative_to(root.resolve())
        except ValueError:
            continue
        parts = rel.parts
        if not parts or parts[0] in skip_top or any(p.startswith('.') for p in parts):
            continue
        if any(p in skip_dirs for p in parts):
            continue
        st = path.stat()
        files[rel.as_posix()] = [int(st.st_size), int(st.st_mtime_ns)]
print('__SESSION_SNAPSHOT__' + json.dumps(files, ensure_ascii=True))
"""
    stdout = run_remote_python(sandbox, script, timeout=60)
    for line in reversed(stdout.splitlines()):
        if "__SESSION_SNAPSHOT__" in line:
            raw = json.loads(line.split("__SESSION_SNAPSHOT__", 1)[1].strip() or "{}")
            return {str(k): (int(v[0]), int(v[1])) for k, v in raw.items()}
        if "__BASH_WORKSPACE_SNAPSHOT__" in line:
            raw = json.loads(
                line.split("__BASH_WORKSPACE_SNAPSHOT__", 1)[1].strip() or "{}"
            )
            return {str(k): (int(v[0]), int(v[1])) for k, v in raw.items()}
        if "__SANDBOX_SNAPSHOT__" in line:
            raw = json.loads(line.split("__SANDBOX_SNAPSHOT__", 1)[1].strip() or "{}")
            return {str(k): (int(v[0]), int(v[1])) for k, v in raw.items()}
    raise RuntimeError("E2B workspace snapshot output was missing its marker")


def download_changed_files(
    sandbox: Any,
    remote_root: str,
    workspace: Path,
    before: Dict[str, Tuple[int, int]],
    produced_paths: Optional[List[Path]] = None,
    *,
    skip_top: Iterable[str] = ("input", SKILLS_DIR),
) -> List[str]:
    after = snapshot_remote_files(sandbox, remote_root, skip_top=skip_top)
    files_api = getattr(sandbox, "files", None)
    if files_api is None:
        return []
    changed_rels: List[str] = []
    for rel, signature in sorted(after.items()):
        if before.get(rel) == signature:
            continue
        remote_path = f"{remote_root}/{rel}"
        try:
            try:
                content = files_api.read(remote_path, format="bytes")
            except TypeError:
                content = files_api.read(remote_path)
            data = (
                content.encode("utf-8") if isinstance(content, str) else bytes(content)
            )
            local_path = workspace / rel
            local_path.parent.mkdir(parents=True, exist_ok=True)
            local_path.write_bytes(data)
            if produced_paths is not None:
                produced_paths.append(local_path)
            changed_rels.append(rel)
        except Exception as exc:
            logger.warning("[e2b_sync] download {} failed: {}", remote_path, exc)
    return changed_rels
