#!/bin/bash
# Claude Code notices on the TV (Notification and Stop hooks).  notify.sh on | off
set -e
HERE="$(cd "$(dirname "$0")" && pwd)"
D="$HOME/.little-office"; mkdir -p "$D"
cp "$HERE/notify_hook.py" "$HERE/hooks.py" "$D/"
PY=/opt/homebrew/bin/python3; [ -x "$PY" ] || PY="$(command -v python3)"
"$PY" "$D/hooks.py" "${1:-on}" notify
