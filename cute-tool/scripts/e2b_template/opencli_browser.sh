#!/usr/bin/env bash

set -euo pipefail

export DISPLAY=:0

if [ "$(id -u)" != "$(id -u user)" ]; then
  exec sudo -u user -H env DISPLAY="$DISPLAY" /usr/local/bin/reactor-opencli-browser "$@"
fi

profile=/home/user/.opencli/chromium-visible
extension=/opt/opencli/extension

test -f "$extension/manifest.json"

if ! xdpyinfo -display "$DISPLAY" >/dev/null 2>&1; then
  /start_command.sh
fi

mkdir -p "$profile"
exec /usr/bin/chromium --no-sandbox --no-first-run --disable-dev-shm-usage \
  --user-data-dir="$profile" \
  --disable-extensions-except="$extension" \
  --load-extension="$extension" "$@"
