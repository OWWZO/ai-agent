#!/usr/bin/env bash

set -euo pipefail

export DISPLAY=:0
export XDG_RUNTIME_DIR="${XDG_RUNTIME_DIR:-/tmp/runtime-user}"

mkdir -p "$XDG_RUNTIME_DIR"
chmod 700 "$XDG_RUNTIME_DIR"

if ! xdpyinfo -display "$DISPLAY" >/dev/null 2>&1; then
  nohup Xvfb "$DISPLAY" -ac -screen 0 1024x768x24 -nolisten tcp \
    >/tmp/xvfb.log 2>&1 </dev/null &
  for attempt in $(seq 1 15); do
    if xdpyinfo -display "$DISPLAY" >/dev/null 2>&1; then
      break
    fi
    sleep 1
  done
fi

xdpyinfo -display "$DISPLAY" >/dev/null

if ! pgrep -f "startxfce4" >/dev/null 2>&1; then
  nohup dbus-launch --exit-with-session startxfce4 \
    >/tmp/xfce4.log 2>&1 </dev/null &
  sleep 5
fi

if ! pgrep -x x11vnc >/dev/null 2>&1; then
  nohup x11vnc \
    -bg \
    -display "$DISPLAY" \
    -forever \
    -wait 50 \
    -shared \
    -rfbport 5900 \
    -nopw \
    -noxdamage \
    -noxfixes \
    -nowf \
    -noscr \
    -ping 1 \
    -repeat \
    -speeds lan \
    >/tmp/x11vnc.log 2>&1 </dev/null
  sleep 2
fi

if ! pgrep -f "novnc_proxy.*6080" >/dev/null 2>&1; then
  cd /opt/noVNC/utils
  nohup ./novnc_proxy \
    --vnc localhost:5900 \
    --listen 6080 \
    --web /opt/noVNC \
    --heartbeat 30 >/tmp/novnc.log 2>&1 </dev/null &
  sleep 2
fi

if [ "${ENSURE_DESKTOP_CHROMIUM_SKIP_BROWSER:-0}" != "1" ]; then
  /usr/local/bin/ensure_desktop_chromium.sh
fi
