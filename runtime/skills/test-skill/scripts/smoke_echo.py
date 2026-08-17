#!/usr/bin/env python3
"""Deterministic helper for test-skill smoke output."""

from __future__ import annotations

import argparse
import sys


def build_report(echo: str) -> str:
    echo = (echo or "skill smoke test").strip()
    return f"""# Test Skill Smoke Report

## Status
OK

## Checks
- skill loaded: yes
- frontmatter valid: yes
- output format: markdown

## Echo
{echo}

## Next
Skill pipeline is healthy. You can package with package_skill.py or continue
building a real skill.
"""


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Emit test-skill smoke report")
    parser.add_argument(
        "--echo",
        default="skill smoke test",
        help="One-line paraphrase of the user request",
    )
    args = parser.parse_args(argv)
    sys.stdout.write(build_report(args.echo))
    if not build_report(args.echo).endswith("\n"):
        sys.stdout.write("\n")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
