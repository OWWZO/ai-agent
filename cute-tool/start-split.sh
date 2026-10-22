#!/usr/bin/env bash
# 拆分启动：sandbox(1602, workers=1) + api(1601, 多 worker)
# Java / Nginx 仍只打 1601；api 把 bash/code_execution 反代到 sandbox。

set -euo pipefail
cd "$(dirname "$0")"

API_PORT="${REACTOR_TOOL_API_PORT:-1601}"
SANDBOX_PORT="${REACTOR_TOOL_SANDBOX_PORT:-1602}"
API_WORKERS="${REACTOR_TOOL_WORKERS:-3}"

export REACTOR_SANDBOX_URL="${REACTOR_SANDBOX_URL:-http://127.0.0.1:${SANDBOX_PORT}}"

echo "Starting sandbox on :${SANDBOX_PORT} (workers=1) ..."
REACTOR_TOOL_ROLE=sandbox \
REACTOR_TOOL_WORKERS=1 \
REACTOR_TOOL_PORT="${SANDBOX_PORT}" \
REACTOR_TOOL_HOST=127.0.0.1 \
  bash ./start.sh &
SANDBOX_PID=$!

cleanup() {
  kill "${SANDBOX_PID}" 2>/dev/null || true
  wait "${SANDBOX_PID}" 2>/dev/null || true
}
trap cleanup EXIT INT TERM

sleep 2
echo "Starting api on :${API_PORT} (workers=${API_WORKERS}) ..."
REACTOR_TOOL_ROLE=api \
REACTOR_TOOL_WORKERS="${API_WORKERS}" \
REACTOR_TOOL_PORT="${API_PORT}" \
REACTOR_TOOL_HOST=0.0.0.0 \
  bash ./start.sh
