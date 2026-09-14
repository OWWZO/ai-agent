# Reactor E2B Template（含 Playwright）

基于官方 `code-interpreter-v1`（保留 `run_code` 内核），预装：

- 数据分析常用包（pandas / numpy / matplotlib …）以及 **duckdb / pyarrow**
- **requests / httpx / beautifulsoup4 / charset-normalizer**
- **pypdf / pdfplumber / reportlab / pypdfium2** 及 Poppler / qpdf
- **DOCX/PPTX、OCR、公开源和 GIF** Python 依赖
- **Playwright + Chromium**（Python 与 Node）及系统依赖
- **ffmpeg / jq**，以及 Node：`pptxgenjs` `pdf-lib` `exceljs` `docx` `marked` `html-to-text` `sharp` `react` `react-dom` `react-icons`
- **LibreOffice / Pandoc / Tesseract** 文档转换和 OCR 命令
- **websockets**
- **yt-dlp[default] + Node.js**，用于 YouTube 视频搜索、详情和字幕

## 构建

```bash
cd reactor-tool
# .env 中配置 E2B_API_KEY=e2b_***
uv run python scripts/e2b_template/build.py
```

构建成功后模板别名为：`reactor-code-playwright`

## 启用

```bash
# reactor-tool/.env
CODE_SANDBOX_BACKEND=e2b
E2B_API_KEY=e2b_***
E2B_TEMPLATE=reactor-code-playwright
```

重启 reactor-tool。

## 沙箱内验证

```python
from playwright.sync_api import sync_playwright

with sync_playwright() as p:
    browser = p.chromium.launch(headless=True)
    page = browser.new_page()
    page.goto("https://example.com")
    print(page.title())
    browser.close()
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

## 说明

- 改 `template.py` 后需**重新 build**。运行时 `playwright install` 会随沙箱销毁，不要当预装。
- Chromium 装在 `/home/user/.cache/ms-playwright`，并软链到 `/usr/bin/chromium`。
- 沙箱需允许出网才能访问外站；E2B 默认通常有网。
- 模板内已配置系统级和 `user` 用户级 `--js-runtimes node`，不需要在每次调用时重复传入。
- Cookie / 登录态不要 bake 进模板，运行时注入。
