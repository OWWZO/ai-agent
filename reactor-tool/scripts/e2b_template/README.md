# Reactor E2B Template（含 Playwright）

基于官方 `code-interpreter-v1`（保留 `run_code` 内核），预装：

- 数据分析常用包（pandas / numpy / matplotlib …）
- **Playwright + Chromium** 及系统依赖

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

## 说明

- 改 `template.py` 后需**重新 build**。
- 沙箱需允许出网才能访问外站；E2B 默认通常有网。
- Cookie / 登录态不要 bake 进模板，运行时注入。
