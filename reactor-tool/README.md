# Reactor Tool

`python >= 3.11`

## 项目结构

```
.
├── reactor_tool
│   ├── api                             # api 服务
│   ├── model                           # 协议和 DataClass
│   ├── prompt                          # Prompt 仓库
│   ├── tool                            # 工具执行逻辑
│   └── util                            # 工具类
├── .env_template                       # 环境变量
├── server.py                           # FastAPI 服务启动
└── start.sh                            # 启动脚本

```

## 项目启动

python 环境和依赖安装  
```bash
pip install uv
cd reactor-tool
uv sync
source .venv/bin/activate
```

首次启动，需要初始化数据库（后续不再需要）
```bash

cd reactor-tool

python -m reactor_tool.db.db_engine
```

启动服务
```bash

cd reactor-tool

cp .env_template .env
# 填写环境变量

./start.sh
```

Windows 推荐启动方式
```powershell
cd reactor-tool
.\\start.ps1
```

说明：

- 如果你这个环境是从其他项目复制过来的，或者此前在别的项目里激活过虚拟环境，直接用 `uv run python server.py` 可能出现 `VIRTUAL_ENV does not match the project environment path .venv` 的 warning。
- 这类 warning 一般不是业务失败的根因，但它说明当前 shell 上下文被别的项目污染了。
- `start.ps1` / `start.sh` 会主动清理外部 `VIRTUAL_ENV`，并强制使用当前项目自己的 `.venv`、单进程模式启动。
- 启动脚本会把本地文件落盘目录设置为 `FILE_SAVE_PATH=skilloutput`，同时保留 `FILE_SERVER_URL=http://127.0.0.1:1601/v1/file_tool` 作为前端可访问的 HTTP 文件服务地址。
- 不要把 `FILE_SERVER_URL` 配置成本地磁盘目录，否则前端拿到的 `domainUrl/downloadUrl` 会变成不可访问路径，文件组件点击后将无法预览。
- 图片生成工具依赖 `IMAGE_GENERATION_BASE_URL`、`IMAGE_GENERATION_API_KEY`、`IMAGE_GENERATION_MODEL`；如果和通用 LLM 走同一个 OpenAI 兼容网关，可以在 `.env` 里直接映射到 `OPENAI_*`。

## DeepSearch 说明

- Query 分解与 `extend/search/report` 三阶段 SSE 协议保持不变。
- DeepSearch 的 LLM 调用支持独立配置 `DEEPSEARCH_BASE_URL`、`DEEPSEARCH_API_KEY`；留空时自动回退到 `OPENAI_BASE_URL`、`OPENAI_API_KEY`。
- 默认搜索提供方已切换为 `DuckDuckGo`，通过 `USE_SEARCH_ENGINE=ddg` 启用。
- 页面正文优先通过 `Jina Reader` 抓取，失败时会自动回退到原始 HTTP 页面解析。
- 可通过 `DDG_REGION`、`DDG_SAFESEARCH`、`JINA_API_KEY`、`JINA_READER_TIMEOUT` 调整抓取行为。
- Java 侧 `deep_search` 调用、数据库持久化与前端历史回放展示无需额外改造。

## Web Fetch 说明

- `POST /v1/tool/web_fetch` 用于抓取单个 `http://` 或 `https://` URL，不负责搜索、批量抓取或浏览器渲染。
- HTML 页面优先使用 `trafilatura` 输出 Markdown，提取失败时自动回退到 `BeautifulSoup.get_text()`；`raw.githubusercontent.com`、`r.jina.ai` 这类返回 Markdown/纯文本的地址会直接按文本内容落盘。
- 每次成功抓取都会强制把完整正文保存为 Markdown 文件产物，并通过 `fileInfo` 返回；内联 `data.content` 仅用于摘要展示，过长时会被截断。
- 文件名优先使用网页标题生成，标题缺失时回退到 URL slug，便于 Java 侧 artifact 落账和后续历史复用。
