"""E2B custom template for Reactor code_execution / code_interpreter.

Base: code-interpreter-v1 (keeps run_code kernel).
Adds: common data stack + PDF tooling + Playwright Chromium + HTTP/HTML
helpers + DuckDB/Parquet + Node PPTX/browser tools + yt-dlp for public
YouTube access.
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
    "pyyaml",
    "sqlalchemy",
    "statsmodels",
    # PDF skills use both Python libraries and command-line tools below.
    "pypdf",
    "pdfplumber",
    "reportlab",
    "pypdfium2",
    "pdf2image",
    "pymupdf",
    # Document skills use these parsers and validation/rendering helpers.
    "python-docx",
    "python-pptx",
    "markitdown[pptx]",
    "defusedxml",
    "lxml",
    "markdown2",
    # OCR, animated image, and public-source skills.
    "pytesseract",
    "imageio",
    "feedparser",
    # HTTP / HTML without starting Chromium.
    "requests",
    "httpx",
    "beautifulsoup4",
    "charset-normalizer",
    # SQL-on-files and parquet for agent analysis.
    "duckdb",
    "pyarrow",
    # YouTube extraction requires yt-dlp's default JS challenge support.
    "yt-dlp[default]>=2026.07.04",
]

# Installed under /home/user/node_modules so require() from /home/user/workspace works.
_NPM_PACKAGES = [
    "playwright",
    "pptxgenjs",
    "pdf-lib",
    "sharp",
    "react",
    "react-dom",
    "react-icons",
    "exceljs",
    "docx",
    "marked",
    "html-to-text",
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
            "ffmpeg",
            "jq",
            "poppler-utils",
            "qpdf",
            "libreoffice",
            "pandoc",
            "tesseract-ocr",
            "tesseract-ocr-chi-sim",
            "tesseract-ocr-chi-tra",
            "gcc",
            "libc6-dev",
        ],
        no_install_recommends=True,
    )
    # Browsers must land in the runtime user's cache. `set_envs` does not persist
    # into the sandbox, so installing as root would hide Chrome under /root.
    .run_cmd("python -m playwright install chromium", user="user")
    .run_cmd(
        "python -c "
        "'from pathlib import Path; "
        "from playwright.sync_api import sync_playwright; "
        "pw = sync_playwright().start(); "
        "exe = Path(pw.chromium.executable_path); "
        "assert exe.is_file(), exe; "
        "browser = pw.chromium.launch(headless=True); "
        'print("chromium_ok", browser.version); '
        "browser.close(); "
        "pw.stop(); "
        'Path("/tmp/chromium.path").write_text(str(exe))\'',
        user="user",
    )
    .run_cmd(
        "test -s /tmp/chromium.path && "
        'ln -sfn "$(cat /tmp/chromium.path)" /usr/bin/chromium && '
        'ln -sfn "$(cat /tmp/chromium.path)" /usr/bin/chromium-browser && '
        "test -x /usr/bin/chromium && readlink -f /usr/bin/chromium",
        user="root",
    )
    .run_cmd(
        "python -c "
        "'import requests, httpx, bs4, duckdb, pyarrow, charset_normalizer; "
        'print("py_ok")\'',
        user="user",
    )
    # Workspace is /home/user/workspace; Node walks up to /home/user/node_modules.
    .run_cmd(
        "PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1 npm install --prefix /home/user "
        + " ".join(_NPM_PACKAGES),
        user="user",
    )
    .run_cmd("/home/user/node_modules/.bin/playwright install chromium", user="user")
    .run_cmd(
        "node -e "
        "\"require('playwright'); require('pptxgenjs'); require('pdf-lib'); "
        "require('sharp'); require('exceljs'); require('docx'); require('marked'); "
        "require('html-to-text'); require('react'); require('react-dom'); "
        "require('react-icons/fa'); console.log('node_ok')\"",
        user="user",
    )
    .run_cmd(
        "node -e "
        "\"const {chromium}=require('playwright'); "
        "(async()=>{const b=await chromium.launch({headless:true,executablePath:'/usr/bin/chromium'}); "
        "console.log('node_chromium_ok', b.version()); await b.close();})()"
        '.catch(e=>{console.error(e); process.exit(1);})"',
        user="user",
    )
    .run_cmd("ffmpeg -version | head -n 1 && jq --version", user="root")
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
