#!/usr/bin/env bash

# 清理外部项目残留的虚拟环境变量，避免解释器串环境
unset VIRTUAL_ENV

# systemd / start-split 传入的角色参数必须压过 .env，否则 sandbox 会被 WORKERS=3 覆盖
_LAUNCH_ROLE="${REACTOR_TOOL_ROLE:-}"
_LAUNCH_HOST="${REACTOR_TOOL_HOST:-}"
_LAUNCH_PORT="${REACTOR_TOOL_PORT:-}"
_LAUNCH_WORKERS="${REACTOR_TOOL_WORKERS:-}"

# 激活当前项目虚拟环境
. .venv/bin/activate

# 优先加载项目 .env，避免后续默认值覆盖线上配置
if [[ -f ".env" ]]; then
  set -a
  # shellcheck disable=SC1091
  . ./.env
  set +a
fi

[[ -n "${_LAUNCH_ROLE}" ]] && export REACTOR_TOOL_ROLE="${_LAUNCH_ROLE}"
[[ -n "${_LAUNCH_HOST}" ]] && export REACTOR_TOOL_HOST="${_LAUNCH_HOST}"
[[ -n "${_LAUNCH_PORT}" ]] && export REACTOR_TOOL_PORT="${_LAUNCH_PORT}"
[[ -n "${_LAUNCH_WORKERS}" ]] && export REACTOR_TOOL_WORKERS="${_LAUNCH_WORKERS}"

# EnvironmentFile 里的 WORKERS=3 可能压过 unit 的 Environment=；sandbox 角色强制单进程
if [[ "${REACTOR_TOOL_ROLE:-}" == "sandbox" ]]; then
  export REACTOR_TOOL_WORKERS=1
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

# 相对文件存储路径不能跟随 versioned release 漂移；生产环境优先落到持久化目录。
if [[ "${FILE_SAVE_PATH}" != /* && -d "/opt/reactor/data" ]]; then
  export FILE_SAVE_PATH="/opt/reactor/data/${FILE_SAVE_PATH}"
fi

if [[ -z "${FILE_SERVER_URL:-}" || ! "${FILE_SERVER_URL}" =~ ^https?:// ]]; then
  export FILE_SERVER_URL="http://127.0.0.1:1601/v1/file_tool"
fi

# FILE_SAVE_PATH 负责本地落盘目录，FILE_SERVER_URL 必须保持为可访问的 HTTP 地址。
mkdir -p "$FILE_SAVE_PATH"

# 运行Python服务器
python server.py \
  --host "${REACTOR_TOOL_HOST:-0.0.0.0}" \
  --port "${REACTOR_TOOL_PORT:-1601}" \
  --workers "${REACTOR_TOOL_WORKERS:-3}" \
  --role "${REACTOR_TOOL_ROLE:-all}"
