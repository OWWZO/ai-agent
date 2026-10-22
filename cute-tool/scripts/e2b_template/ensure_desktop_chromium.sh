#!/usr/bin/env bash

set -euo pipefail

export DISPLAY=:0

profile=/home/user/.opencli/chromium-visible
extension=/opt/opencli/extension
browser_pattern='^/usr/bin/chromium --no-sandbox --no-first-run --disable-dev-shm-usage .*--user-data-dir=/home/user/.opencli/chromium-visible'
browser_log=/tmp/reactor-desktop-chromium.log

if [ "${ENSURE_DESKTOP_CHROMIUM_LOCKED:-}" != "1" ]; then
  umask 000
  exec flock --close /tmp/reactor-desktop-chromium.lock \
    env ENSURE_DESKTOP_CHROMIUM_LOCKED=1 "$0" "$@"
fi
umask 022

test -f "$extension/manifest.json"
test -x /usr/bin/chromium

wait_for_display() {
  for attempt in $(seq 1 30); do
    if xdpyinfo -display "$DISPLAY" >/dev/null 2>&1; then
      return 0
    fi
    sleep 1
  done
  return 1
}

if ! wait_for_display; then
  test -x /start_command.sh
  if [ "$(id -u)" = "$(id -u user)" ]; then
    sudo -n env DISPLAY="$DISPLAY" ENSURE_DESKTOP_CHROMIUM_SKIP_BROWSER=1 \
      /start_command.sh
  else
    ENSURE_DESKTOP_CHROMIUM_SKIP_BROWSER=1 /start_command.sh
  fi
fi

if [ "$(id -u)" != "$(id -u user)" ]; then
  exec sudo -u user -H env DISPLAY="$DISPLAY" \
    ENSURE_DESKTOP_CHROMIUM_LOCKED=1 \
    /usr/local/bin/ensure_desktop_chromium.sh "$@"
fi

find_browser_pid() {
  pgrep -u user -f -- "$browser_pattern" | head -n 1 || true
}

wait_for_browser() {
  local browser_pid
  for attempt in $(seq 1 30); do
    browser_pid="$(find_browser_pid)"
    if [ -n "$browser_pid" ] \
      && kill -0 "$browser_pid" 2>/dev/null \
      && grep -z -q '^DISPLAY=:0$' "/proc/$browser_pid/environ"; then
      return 0
    fi
    sleep 1
  done
  if [ -f "$browser_log" ]; then
    cat "$browser_log" >&2
  fi
  echo "Chromium did not become ready for profile $profile" >&2
  return 1
}

if ! xdpyinfo -display "$DISPLAY" >/dev/null 2>&1; then
  test -x /start_command.sh
  sudo -n env DISPLAY="$DISPLAY" ENSURE_DESKTOP_CHROMIUM_SKIP_BROWSER=1 \
    /start_command.sh
fi

if [ -z "$(find_browser_pid)" ]; then
  mkdir -p "$profile"
  nohup /usr/bin/chromium \
    --no-sandbox \
    --no-first-run \
    --disable-dev-shm-usage \
    --user-data-dir="$profile" \
    --disable-extensions-except="$extension" \
    --load-extension="$extension" \
    about:blank >"$browser_log" 2>&1 < /dev/null 9>&- &
fi

wait_for_browser
