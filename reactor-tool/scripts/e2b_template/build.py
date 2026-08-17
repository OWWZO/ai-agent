"""Build the Reactor E2B template (requires E2B_API_KEY).

Usage:
  cd reactor-tool
  # set E2B_API_KEY in .env or environment
  uv run python scripts/e2b_template/build.py

Then set in reactor-tool/.env:
  CODE_SANDBOX_BACKEND=e2b
  E2B_API_KEY=e2b_***
  E2B_TEMPLATE=reactor-code-playwright
"""
from __future__ import annotations

import sys
from pathlib import Path

from dotenv import load_dotenv

# Load reactor-tool/.env when run from repo.
_ROOT = Path(__file__).resolve().parents[2]
_SCRIPT_DIR = Path(__file__).resolve().parent
sys.path.insert(0, str(_SCRIPT_DIR))
load_dotenv(_ROOT / ".env")

from e2b import Template, default_build_logger  # noqa: E402

from template import TEMPLATE_ALIAS, template  # noqa: E402


def main() -> int:
    print(f"Building E2B template alias={TEMPLATE_ALIAS!r} ...")
    Template.build(
        template,
        TEMPLATE_ALIAS,
        cpu_count=2,
        memory_mb=4096,
        on_build_logs=default_build_logger(),
    )
    print(
        f"\nDone. Set:\n"
        f"  E2B_TEMPLATE={TEMPLATE_ALIAS}\n"
        f"  CODE_SANDBOX_BACKEND=e2b\n"
        f"and restart reactor-tool.\n"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
