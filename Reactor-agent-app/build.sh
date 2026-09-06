#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="${COMPOSE_ENV_FILE:-${ROOT_DIR}/reactor-tool/.env}"

if [[ ! -f "${ENV_FILE}" ]]; then
  echo "Missing ${ENV_FILE}. Copy reactor-tool/.env_template to reactor-tool/.env and fill in the deployment values." >&2
  exit 1
fi

docker compose \
  --env-file "${ENV_FILE}" \
  --file "${ROOT_DIR}/docker-compose.yml" \
  build "$@"
