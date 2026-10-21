"""Shared CJK font path discovery for PDF, matplotlib, and other generators.

Pan-Unicode fonts only; avoid Latin-stripped supplemental fallbacks as sole body font.
Discovery is cached for the process lifetime; restart to pick up fonts installed later.
"""

from __future__ import annotations

import os
import sys
from pathlib import Path
from typing import Any


_DISCOVERY_CACHE: dict[bool, str | None] = {}


def cjk_font_search_roots() -> list[str]:
    """Directory roots scanned for known CJK filenames (rglob)."""
    home = str(Path.home())
    roots: list[str | None] = [
        str(Path(os.environ.get("XDG_DATA_HOME", "")).expanduser() / "fonts")
        if os.environ.get("XDG_DATA_HOME")
        else None,
        f"{home}/.local/share/fonts",
        f"{home}/.fonts",
        "/usr/local/share/fonts",
        "/usr/share/fonts",
    ]
    if sys.platform == "darwin":
        roots.extend(
            [
                f"{home}/Library/Fonts",
                "/Library/Fonts",
                "/System/Library/Fonts/Supplemental",
                "/System/Library/Fonts",
            ]
        )
    windir = (os.environ.get("WINDIR") or os.environ.get("SystemRoot") or "").strip()
    if windir:
        win_fonts = str(Path(windir) / "Fonts")
        if Path(win_fonts).is_dir():
            roots.append(win_fonts)
    out: list[str] = []
    seen: set[str] = set()
    for raw in roots:
        if not raw:
            continue
        path = str(Path(raw).expanduser())
        if path not in seen:
            seen.add(path)
            out.append(path)
    return out


def _candidate_names(*, is_bold: bool) -> tuple[str, ...]:
    """Known pan-Unicode CJK font filenames, ordered by preference."""
    common = (
        "PingFang.ttc",
        "STHeiti Medium.ttc",
        "STHeiti Light.ttc",
        "wqy-microhei.ttc",
        "wqy-zenhei.ttc",
    )
    if is_bold:
        return (
            "NotoSansSC-Bold.ttf",
            "NotoSansSC-Bold.otf",
            "NotoSansCJKSC-Bold.otf",
            "NotoSansCJK-Bold.ttc",
            "SourceHanSansSC-Bold.otf",
            "SourceHanSansCN-Bold.otf",
            "SourceHanSans-Bold.ttc",
            "msyhbd.ttc",
            *common,
        )
    return (
        "NotoSansSC-Regular.ttf",
        "NotoSansSC-Regular.otf",
        "NotoSansCJKSC-Regular.otf",
        "NotoSansCJK-Regular.ttc",
        "SourceHanSansSC-Regular.otf",
        "SourceHanSansCN-Regular.otf",
        "SourceHanSans-Regular.ttc",
        "Microsoft YaHei.ttf",
        "msyh.ttc",
        *common,
    )


def discover_cjk_font_file(*, is_bold: bool) -> str | None:
    """Search common CJK file names under system font trees."""
    if is_bold in _DISCOVERY_CACHE:
        return _DISCOVERY_CACHE[is_bold]

    names = _candidate_names(is_bold=is_bold)
    for base in cjk_font_search_roots():
        root = Path(base)
        if not root.is_dir():
            continue
        for n in names:
            direct = root / n
            if direct.is_file():
                _DISCOVERY_CACHE[is_bold] = str(direct)
                return str(direct)
            try:
                matches = root.rglob(n)
                for p in matches:
                    if p.is_file():
                        _DISCOVERY_CACHE[is_bold] = str(p)
                        return str(p)
            except OSError:
                continue
    _DISCOVERY_CACHE[is_bold] = None
    return None


def _managed_font_path(*, is_bold: bool) -> str | None:
    """Font previously auto-downloaded by ``reactor_tool.docgen.fonts.FontManager``.

    Checked here directly (filename convention, no download) to avoid a
    circular import — docgen builds on this module.
    """
    try:
        from reactor_tool.docgen.paths import REACTOR_DOCGEN_HOME as LEAGENT_HOME
    except Exception:  # noqa: BLE001 - constants must never break discovery
        return None
    name = "NotoSansSC-Bold.ttf" if is_bold else "NotoSansSC-Regular.ttf"
    path = LEAGENT_HOME / "fonts" / name
    if path.is_file() and path.stat().st_size > 0:
        return str(path)
    return None


def resolve_cjk_regular_path(*, explicit: str | None = None) -> str | None:
    """First existing regular CJK font: explicit, LEAGENT_CJK_FONT, managed dir, scan."""
    for raw in (explicit, os.environ.get("LEAGENT_CJK_FONT", "").strip() or None):
        if not raw:
            continue
        p = Path(raw)
        if p.is_file():
            return str(p)
    managed = _managed_font_path(is_bold=False)
    if managed:
        return managed
    return discover_cjk_font_file(is_bold=False)


def resolve_cjk_bold_path(*, explicit: str | None = None) -> str | None:
    """First existing bold CJK font: explicit, LEAGENT_CJK_FONT_BOLD, managed dir, scan."""
    for raw in (explicit, os.environ.get("LEAGENT_CJK_FONT_BOLD", "").strip() or None):
        if not raw:
            continue
        p = Path(raw)
        if p.is_file():
            return str(p)
    managed = _managed_font_path(is_bold=True)
    if managed:
        return managed
    return discover_cjk_font_file(is_bold=True)
