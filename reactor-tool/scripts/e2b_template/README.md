# Reactor E2B Template（含 Playwright）

基于官方 `code-interpreter-v1`（保留 `run_code` 内核），预装：

- 数据分析常用包（pandas / numpy / matplotlib …）
- **pypdf / pdfplumber / reportlab / pypdfium2** 及 Poppler / qpdf
- **DOCX/PPTX、OCR、公开源和 GIF** Python 依赖
- **Playwright + Chromium** 及系统依赖
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

检查 YouTube 工具：

```bash
yt-dlp --version
node --version
yt-dlp --flat-playlist --dump-json "ytsearch1:AI agents"
```

## 说明

- 改 `template.py` 后需**重新 build**。
- 沙箱需允许出网才能访问外站；E2B 默认通常有网。
- 模板内已配置系统级和 `user` 用户级 `--js-runtimes node`，不需要在每次调用时重复传入。
- Cookie / 登录态不要 bake 进模板，运行时注入。
