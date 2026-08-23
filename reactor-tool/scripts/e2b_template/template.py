"""E2B custom template for Reactor code_execution / code_interpreter.

Base: code-interpreter-v1 (keeps run_code kernel).
Adds: common data stack + Playwright Chromium + yt-dlp for public YouTube access.
"""

from __future__ import annotations

from e2b import Template

# Template alias used by Sandbox.create(template=...) / E2B_TEMPLATE env.
TEMPLATE_ALIAS = "reactor-code-playwright"

# Keep in sync with code_interpreter authorized analysis libs where practical.
_PIP_PACKAGES = [
    "playwright",
    "websockets",
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
    # YouTube extraction requires yt-dlp's default JS challenge support.
    "yt-dlp[default]>=2026.07.04",
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
            "nodejs",
        ],
        no_install_recommends=True,
    )
    # Download browser binaries into the image (not at runtime).
    .run_cmd("python -m playwright install chromium", user="root")
    # yt-dlp needs an explicit JS runtime when Node.js is used in the image.
    .run_cmd(
        "mkdir -p /home/user/.config/yt-dlp && "
        "printf '%s\\n' '--js-runtimes node' > /etc/yt-dlp.conf && "
        "printf '%s\\n' '--js-runtimes node' > /home/user/.config/yt-dlp/config && "
        "chown -R user:user /home/user/.config/yt-dlp",
        user="root",
    )
    .run_cmd("yt-dlp --version && node --version", user="root")
)
