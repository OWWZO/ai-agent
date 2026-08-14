# -*- coding: utf-8 -*-
"""Resolve input/output paths for docread tools under session workspace roots."""
from __future__ import annotations

import os
import re
from pathlib import Path
from typing import Iterable

from reactor_tool.docgen.paths import OUTPUT_DIR, UPLOAD_DIR

_UNSAFE = re.compile(r'[<>:"|?*\x00-\x1f]+')


def _safe_segment(value: str, fallback: str = "default") -> str:
    text = (value or "").strip() or fallback
    text = text.replace("\\", "/").split("/")[-1]
    text = _UNSAFE.sub("_", text)
    return text or fallback


def session_roots(request_id: str, workspace_root: str | None = None) -> list[Path]:
    # 所有输入候选都围绕请求 ID 组织，既支持工作区文件，也避免不同会话默认目录互相污染。
    rid = _safe_segment(request_id, "default")
    roots: list[Path] = []
    if workspace_root:
        try:
            roots.append(Path(workspace_root).expanduser().resolve())
        except OSError:
            pass
    # Reactor workspace default: skilloutput/{sessionId}
    cwd = Path.cwd()
    file_save_path = Path(os.getenv("FILE_SAVE_PATH", "file_db_dir")).expanduser()
    if not file_save_path.is_absolute():
        file_save_path = cwd / file_save_path
    for candidate in (
        cwd / "skilloutput" / rid,
        cwd / "skilloutput" / rid / "output",
        OUTPUT_DIR / rid,
        OUTPUT_DIR,
        UPLOAD_DIR / rid,
        UPLOAD_DIR,
        file_save_path / rid,
        file_save_path,
        cwd,
    ):
        try:
            roots.append(candidate.resolve())
        except OSError:
            continue
    # dedupe preserve order
    seen: set[str] = set()
    out: list[Path] = []
    for r in roots:
        key = str(r)
        if key in seen:
            continue
        seen.add(key)
        out.append(r)
    return out


def _is_under(path: Path, root: Path) -> bool:
    try:
        path.resolve().relative_to(root.resolve())
        return True
    except (ValueError, OSError):
        return False


def resolve_input_path(
    raw: str,
    *,
    request_id: str,
    workspace_root: str | None = None,
    must_exist: bool = True,
) -> Path:
    # 先生成候选路径，再要求存在；返回前统一 resolve，避免相对路径和 .. 绕过目录比较。
    if not raw or not str(raw).strip():
        raise FileNotFoundError("file_path is required")
    text = str(raw).strip().replace("\\", "/")
    p = Path(text).expanduser()
    roots = session_roots(request_id, workspace_root)

    candidates: list[Path] = []
    if p.is_absolute():
        candidates.append(p)
    else:
        for root in roots:
            candidates.append((root / text).resolve())
            candidates.append((root / Path(text).name).resolve())

    for cand in candidates:
        try:
            resolved = cand.resolve()
        except OSError:
            continue
        if must_exist and not resolved.exists():
            continue
        # Allow absolute paths that exist (local debug); prefer under roots when possible
        if resolved.is_absolute() and (not must_exist or resolved.exists()):
            if any(_is_under(resolved, r) for r in roots) or resolved.exists():
                return resolved

    raise FileNotFoundError(f"File not found: {raw}")


def resolve_output_path(
    raw: str | None,
    *,
    request_id: str,
    workspace_root: str | None = None,
    default_name: str = "docread_output.bin",
) -> Path:
    # 输出目录允许创建，输入目录由 resolve_input_path 单独校验，避免读写策略混用。
    rid = _safe_segment(request_id, "default")
    base = Path(workspace_root).expanduser().resolve() if workspace_root else (Path.cwd() / "skilloutput" / rid)
    base.mkdir(parents=True, exist_ok=True)
    if not raw or not str(raw).strip():
        return (base / default_name).resolve()
    text = str(raw).strip().replace("\\", "/")
    p = Path(text).expanduser()
    if p.is_absolute():
        p.parent.mkdir(parents=True, exist_ok=True)
        return p.resolve()
    out = (base / text).resolve()
    out.parent.mkdir(parents=True, exist_ok=True)
    return out


def rewrite_path_params(
    params: dict,
    path_keys: Iterable[str],
    *,
    request_id: str,
    workspace_root: str | None,
    create: bool = False,
) -> dict:
    out = dict(params)
    for key in path_keys:
        val = out.get(key)
        if not val or not isinstance(val, str):
            continue
        if create:
            out[key] = str(
                resolve_output_path(val, request_id=request_id, workspace_root=workspace_root, default_name=Path(val).name)
            )
        else:
            try:
                out[key] = str(
                    resolve_input_path(val, request_id=request_id, workspace_root=workspace_root, must_exist=True)
                )
            except FileNotFoundError:
                # leave unresolved for tools that create missing paths later
                if create:
                    out[key] = str(
                        resolve_output_path(val, request_id=request_id, workspace_root=workspace_root)
                    )
                else:
                    raise
    return out
