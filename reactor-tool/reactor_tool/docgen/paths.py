"""Reactor docgen path constants (LeAgent LEAGENT_HOME equivalent)."""
from __future__ import annotations

import os
from pathlib import Path

REACTOR_DOCGEN_HOME = Path(
    os.getenv("REACTOR_DOCGEN_HOME")
    or os.getenv("LEAGENT_HOME")
    or (Path.home() / ".reactor-docgen")
)
FONTS_DIR = REACTOR_DOCGEN_HOME / "fonts"
UPLOAD_DIR = Path(os.getenv("REACTOR_DOCGEN_UPLOAD_DIR") or (REACTOR_DOCGEN_HOME / "uploads"))
KNOWLEDGE_DIR = Path(os.getenv("REACTOR_DOCGEN_KNOWLEDGE_DIR") or (REACTOR_DOCGEN_HOME / "knowledge"))
TEMPLATE_STYLES_DIR = REACTOR_DOCGEN_HOME / "templates" / "styles"
OUTPUT_DIR = Path(os.getenv("REACTOR_DOCGEN_OUTPUT_DIR") or (Path.cwd() / "skilloutput" / "docgen"))

for _p in (FONTS_DIR, UPLOAD_DIR, KNOWLEDGE_DIR, TEMPLATE_STYLES_DIR, OUTPUT_DIR):
    try:
        _p.mkdir(parents=True, exist_ok=True)
    except OSError:
        pass

class _FilesSettings:
    upload_dir = str(UPLOAD_DIR)

    def resolved_knowledge_storage_dir(self):
        return str(KNOWLEDGE_DIR)


class _Settings:
    files = _FilesSettings()


def get_settings():
    return _Settings()
