#!/bin/bash
# Turn TV approvals for Claude Code on or off on this computer (install.sh and install-windows.ps1 do it already).
#   little-office/approvals.sh on    registers the PermissionRequest hook in ~/.claude/settings.json
#   little-office/approvals.sh off   removes it (pairings stay; revoke them with office.py revoke all)
set -e
HERE="$(cd "$(dirname "$0")" && pwd)"
D="$HOME/.little-office"; mkdir -p "$D"
cp "$HERE/hooks.py" "$HERE/approve_hook.py" "$HERE/approvals.py" "$HERE/schema.py" "$HERE/office.py" "$D/"
PY=/opt/homebrew/bin/python3; [ -x "$PY" ] || PY="$(command -v python3)"
"$PY" "$D/hooks.py" "${1:-on}" approve
launchctl kickstart -k "gui/$(id -u)/com.steroid.littleoffice" 2>/dev/null || true
