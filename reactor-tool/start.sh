#!/usr/bin/env bash

# 清理外部项目残留的虚拟环境变量，避免解释器串环境
unset VIRTUAL_ENV

# 激活当前项目虚拟环境
. .venv/bin/activate

# 优先加载项目 .env，避免后续默认值覆盖线上配置
if [[ -f ".env" ]]; then
  set -a
  # shellcheck disable=SC1091
  . ./.env
  set +a
fi

export ENV="${ENV:-prod}"
export PYTHONIOENCODING="utf-8"
export SKILL_PYTHON_BIN="${SKILL_PYTHON_BIN:-$(pwd)/.venv/bin/python}"

if [[ -z "${FILE_SAVE_PATH:-}" ]]; then
  if [[ -n "${FILE_SERVER_URL:-}" && ! "${FILE_SERVER_URL}" =~ ^https?:// ]]; then
    export FILE_SAVE_PATH="$FILE_SERVER_URL"
  else
    export FILE_SAVE_PATH="$(pwd)/skilloutput"
  fi
fi

if [[ -z "${FILE_SERVER_URL:-}" || ! "${FILE_SERVER_URL}" =~ ^https?:// ]]; then
  export FILE_SERVER_URL="http://127.0.0.1:1601/v1/file_tool"
fi

# FILE_SAVE_PATH 负责本地落盘目录，FILE_SERVER_URL 必须保持为可访问的 HTTP 地址。
mkdir -p "$FILE_SAVE_PATH"

# 运行Python服务器
python server.py --workers "${REACTOR_TOOL_WORKERS:-5}"
