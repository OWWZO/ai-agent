"""E2B custom template for Reactor code_execution / code_interpreter.

Base: code-interpreter-v1 (keeps run_code kernel).
Adds: common data stack + Playwright Chromium for browser automation.
"""
from __future__ import annotations

from e2b import Template

# Template alias used by Sandbox.create(template=...) / E2B_TEMPLATE env.
TEMPLATE_ALIAS = "reactor-code-playwright"

# Keep in sync with code_interpreter authorized analysis libs where practical.
_PIP_PACKAGES = [
    "playwright",
    "pandas",
    "numpy",
    "matplotlib",
    "seaborn",
    "openpyxl",
    "scipy",
    "scikit-learn",
    "plotly",
    "altair",
    "tabulate",
    "pillow",
]

template = (
    Template()
    .from_template("code-interpreter-v1")
    .pip_install(_PIP_PACKAGES)
    # System libs required by Chromium headless on Debian-based sandboxes.
    .apt_install(
        [
            "libnss3",
            "libnspr4",
            "libatk1.0-0",
            "libatk-bridge2.0-0",
            "libcups2",
            "libdrm2",
            "libdbus-1-3",
            "libxkbcommon0",
            "libxcomposite1",
            "libxdamage1",
            "libxfixes3",
            "libxrandr2",
            "libgbm1",
            "libasound2",
            "libpango-1.0-0",
            "libcairo2",
            "libatspi2.0-0",
            "libxshmfence1",
            "fonts-liberation",
            "fonts-noto-cjk",
        ],
        no_install_recommends=True,
    )
    # Download browser binaries into the image (not at runtime).
    .run_cmd("python -m playwright install chromium", user="root")
)
