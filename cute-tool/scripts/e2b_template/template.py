"""Composite E2B template for Reactor code execution and desktop workloads.

Base: code-interpreter-v1 (keeps the managed run_code kernel).
Adds: the E2B Desktop environment, the existing data stack, PDF tooling,
Playwright Chromium, HTTP/HTML helpers, DuckDB/Parquet, Node document tools,
and yt-dlp for public YouTube access.

The Desktop example is normally built from Ubuntu directly. This template
adds the same desktop layer on top of code-interpreter-v1 so the existing
Code Interpreter SDK contract remains available.
"""

from __future__ import annotations

from e2b import Template

# Template alias used by Sandbox.create(template=...) / E2B_TEMPLATE env.
TEMPLATE_ALIAS = "reactor-code-playwright-desktop"
_DESKTOP_START_COMMAND = "desktop_start_command.sh"
_OPENCLI_VERSION = "1.8.8"
_OPENCLI_EXTENSION_VERSION = "1.0.24"
_OPENCLI_EXTENSION_SHA256 = (
    "bad9163f32a66224404e302f52f35391672341d2bd0bf30c8bbaae3c6e6e246c"
)

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
    # Desktop packages mirror the official E2B Desktop template while keeping
    # code-interpreter-v1 as the base image for run_code compatibility.
    .run_cmd(
        [
            "export DEBIAN_FRONTEND=noninteractive && apt-get update",
            "export DEBIAN_FRONTEND=noninteractive && apt-get install -y --no-install-recommends "
            "xserver-xorg xorg x11-xserver-utils xvfb x11-utils xauth "
            "xfce4 xfce4-goodies xfce4-terminal util-linux sudo curl git wget "
            "xdotool scrot x11vnc net-tools netcat-openbsd x11-apps "
            "xpdf gedit xpaint tint2 galculator pcmanfm dbus-x11 python3-tk xdg-utils",
            "apt-get clean && rm -rf /var/lib/apt/lists/*",
        ],
        user="root",
    )
    .run_cmd(
        [
            "rm -rf /opt/noVNC",
            "git clone --depth 1 --branch e2b-desktop "
            "https://github.com/e2b-dev/noVNC.git /opt/noVNC",
            "ln -sfn /opt/noVNC/vnc.html /opt/noVNC/index.html",
            "git clone --depth 1 --branch v0.12.0 "
            "https://github.com/novnc/websockify "
            "/opt/noVNC/utils/websockify",
        ],
        user="root",
    )
    .run_cmd(
        "ln -sf /usr/bin/xfce4-terminal.wrapper /etc/alternatives/x-terminal-emulator",
        user="root",
    )
    # The managed kernel is started by systemd, so the shell DISPLAY value
    # alone is not enough for headed Playwright launched through run_code().
    .run_cmd(
        "mkdir -p /etc/systemd/system/jupyter.service.d "
        "/etc/systemd/system/code-interpreter.service.d && "
        "printf '%s\\n' '[Service]' 'Environment=DISPLAY=:0' "
        "> /etc/systemd/system/jupyter.service.d/reactor-desktop.conf && "
        "printf '%s\\n' '[Service]' 'Environment=DISPLAY=:0' "
        "> /etc/systemd/system/code-interpreter.service.d/reactor-desktop.conf",
        user="root",
    )
    .copy(_DESKTOP_START_COMMAND, "/start_command.sh", user="root", mode=0o755)
    .copy(
        "ensure_desktop_chromium.sh",
        "/usr/local/bin/ensure_desktop_chromium.sh",
        user="root",
        mode=0o755,
    )
    .copy(
        "opencli_browser.sh",
        "/usr/local/bin/reactor-opencli-browser",
        user="root",
        mode=0o755,
    )
    .copy(
        "opencli_browser.desktop",
        "/usr/share/applications/reactor-opencli-browser.desktop",
        user="root",
    )
    .set_envs({"DISPLAY": ":0"})
    .run_cmd("mkdir -p /home/user/workspace", user="user")
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
        "ln -sfn /usr/local/bin/reactor-opencli-browser /usr/bin/www-browser && "
        "ln -sfn /usr/local/bin/reactor-opencli-browser /usr/bin/x-www-browser && "
        "test -x /usr/bin/www-browser && "
        "test -x /usr/bin/x-www-browser && "
        "readlink -f /usr/bin/www-browser",
        user="root",
    )
    .run_cmd(
        "mkdir -p /home/user/.config && "
        "xdg-mime default reactor-opencli-browser.desktop x-scheme-handler/http && "
        "xdg-mime default reactor-opencli-browser.desktop x-scheme-handler/https && "
        "xdg-mime default reactor-opencli-browser.desktop text/html && "
        'test "$(xdg-mime query default x-scheme-handler/https)" = '
        "reactor-opencli-browser.desktop",
        user="user",
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
    .run_cmd(
        "node -e \"const [major, minor, patch] = process.versions.node.split('.').map(Number); "
        "if (major < 20 || (major === 20 && (minor < 18 || (minor === 18 && patch < 1)))) "
        "throw new Error('OpenCLI requires Node.js >= 20.18.1');\" && "
        "npm install --global --prefix /home/user/.local "
        f"@jackwener/opencli@{_OPENCLI_VERSION}",
        user="user",
    )
    .run_cmd(
        "ln -sfn /home/user/.local/bin/opencli /usr/local/bin/opencli && "
        "mkdir -p /opt/opencli/extension && "
        "curl -fsSL "
        f"https://github.com/jackwener/OpenCLI/releases/download/v{_OPENCLI_VERSION}/"
        f"opencli-extension-v{_OPENCLI_EXTENSION_VERSION}.zip "
        "-o /tmp/opencli-extension.zip && "
        f"printf '{_OPENCLI_EXTENSION_SHA256}  /tmp/opencli-extension.zip\\n' "
        "| sha256sum -c - && "
        'python -c "import zipfile; '
        "zipfile.ZipFile('/tmp/opencli-extension.zip').extractall('/opt/opencli/extension')\" && "
        "test -f /opt/opencli/extension/manifest.json",
        user="root",
    )
    .run_cmd(
        "opencli --version && "
        "node -e \"const manifest = require('/opt/opencli/extension/manifest.json'); "
        f"if (manifest.version !== '{_OPENCLI_EXTENSION_VERSION}') process.exit(1); "
        "console.log('opencli_extension=' + manifest.version)\"",
        user="user",
    )
    .set_start_cmd(
        "/start_command.sh",
        "curl -fsS http://127.0.0.1:6080/vnc.html >/dev/null && "
        "pgrep -u user -f '^/usr/bin/chromium --no-sandbox --no-first-run "
        "--disable-dev-shm-usage .*--user-data-dir=/home/user/.opencli/chromium-visible' "
        ">/dev/null",
    )
)
