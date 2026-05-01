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
