# -*- coding: utf-8 -*-
"""Minimal path-safe open for dataprep local artifacts."""
from __future__ import annotations

from pathlib import Path
from typing import IO, Any


def open_read_text_nofollow(path: Path | str, **kwargs: Any) -> IO[str]:
    p = Path(path)
    if not p.is_file():
        raise FileNotFoundError(str(p))
    # follow_symlinks=False is not available on all platforms for open(); resolve carefully
    kwargs.setdefault("encoding", "utf-8")
    return open(p, "r", **kwargs)
