# Reactor E2B Desktop Template（含 Code Interpreter）

基于官方 `code-interpreter-v1`，叠加 E2B Desktop 的 Ubuntu 图形环境，保留当前
`e2b_code_interpreter.Sandbox`、`run_code()`、Bash 和文件同步能力。预装：

- Ubuntu/XFCE、Xvfb、x11vnc 和 noVNC（端口 `6080`）
- xdotool、scrot、LibreOffice、文件管理器和常用桌面工具
- 数据分析常用包（pandas / numpy / matplotlib …）以及 **duckdb / pyarrow**
- **requests / httpx / beautifulsoup4 / charset-normalizer**
- **pypdf / pdfplumber / reportlab / pypdfium2** 及 Poppler / qpdf
- **DOCX/PPTX、OCR、公开源和 GIF** Python 依赖
- **Playwright + Chromium**（Python 与 Node）及系统依赖
- 桌面浏览器入口 `www-browser` / `x-www-browser` 使用 OpenCLI 共享 profile 和扩展
- **ffmpeg / jq**，以及 Node：`pptxgenjs` `pdf-lib` `exceljs` `docx` `marked` `html-to-text` `sharp` `react` `react-dom` `react-icons`
- **OpenCLI 1.8.8 + Browser Bridge 扩展 1.0.24**（扩展文件预装，桌面 Chromium 自动保持可用）
- **LibreOffice / Pandoc / Tesseract** 文档转换和 OCR 命令
- **websockets**
- **yt-dlp[default] + Node.js**，用于 YouTube 视频搜索、详情和字幕

## 构建

```bash
cd reactor-tool
# .env 中配置 E2B_API_KEY=e2b_***
uv run python scripts/e2b_template/build.py
```

构建成功后模板别名为：`reactor-code-playwright-desktop`

## 启用

```bash
# reactor-tool/.env
CODE_SANDBOX_BACKEND=e2b
E2B_API_KEY=e2b_***
E2B_TEMPLATE=reactor-code-playwright-desktop
```

重启 reactor-tool。

## 沙箱内验证

运行一次完整 smoke test（会创建并销毁临时沙箱）：

```bash
uv run python scripts/e2b_template/smoke_test.py
```

沙箱启动时启动 Desktop 服务、确保可见 Chromium 并等待 noVNC `6080` ready。Jupyter 不属于 Bash 或桌面
启动依赖；首次 Python `Code Execute` 会检查并按需启动 Jupyter。Bash 同步通过 E2B
Commands 和 Files API 完成。

检查 Desktop、Code Interpreter 和数据分析依赖：

```bash
curl -fsS http://127.0.0.1:6080/vnc.html >/dev/null
DISPLAY=:0 xdotool getdisplaygeometry
python -c "import pandas, numpy, matplotlib; print('analysis_ok')"
```

```python
from playwright.async_api import async_playwright

async with async_playwright() as p:
    browser = await p.chromium.launch(headless=False)
    page = await browser.new_page()
    await page.goto("https://example.com")
    print(await page.title())
    await browser.close()
```

检查 Node 包（从 workspace 目录 `require` 即可，模块在 `/home/user/node_modules`）：

```bash
node -e "require('playwright'); require('pptxgenjs'); require('sharp'); console.log('ok')"
ffmpeg -version | head -n 1
```

检查 YouTube 工具：

```bash
yt-dlp --version
node --version
yt-dlp --flat-playlist --dump-json "ytsearch1:AI agents"
```

## OpenCLI 浏览器

模板预装 `opencli` 和解压到 `/opt/opencli/extension` 的 Browser Bridge 扩展。沙箱启动时，
`/usr/local/bin/ensure_desktop_chromium.sh` 直接启动桌面 Chromium；恢复后首次获取沙箱时也会检查并按需补起。
桌面默认浏览器、`www-browser`、`x-www-browser` 与 Agent 共用 `/home/user/.opencli/chromium-visible`，并加载同一扩展。
浏览器启动完成后，Agent 才执行 `opencli doctor` 和 `opencli browser ...`；
不要用 `xvfb-run` 创建一个不会显示在桌面预览里的独立 X display。
Smoke test 会通过桌面启动器打开测试页，再验证 OpenCLI 读取到相同标题且 Xfce 桌面上的 Chromium 窗口可见。
完整命令流程见仓库根目录的 `runtime/skills/e2b-opencli-browser/SKILL.md`。

每个新沙箱的 profile 独立且初始未登录；不要把 Cookie 或登录态写入模板。扩展会请求 `cookies`、`debugger`、`tabs` 和所有网址的访问权限。

旧模板 `reactor-code-playwright` 保留在 E2B 项目中，可作为回滚模板；新模板验证通过后
才将运行环境中的 `E2B_TEMPLATE` 切换到 Desktop 版本。

## 说明

- 改 `template.py` 后需**重新 build**。运行时 `playwright install` 会随沙箱销毁，不要当预装。
- Chromium 装在 `/home/user/.cache/ms-playwright`，并软链到 `/usr/bin/chromium`。
- 沙箱需允许出网才能访问外站；E2B 默认通常有网。
- 模板内已配置系统级和 `user` 用户级 `--js-runtimes node`，不需要在每次调用时重复传入。
- Cookie / 登录态不要 bake 进模板，运行时注入。
