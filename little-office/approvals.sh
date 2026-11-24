#!/bin/bash
# Turn TV approvals for Claude Code on or off on this Mac.
#   little-office/approvals.sh on    registers the PermissionRequest hook in ~/.claude/settings.json
#   little-office/approvals.sh off   removes it (pairings stay; revoke them with office.py revoke all)
set -e
HERE="$(cd "$(dirname "$0")" && pwd)"
D="$HOME/.little-office"; mkdir -p "$D"
cp "$HERE/approve_hook.py" "$HERE/approvals.py" "$HERE/schema.py" "$HERE/office.py" "$D/"
PY=/opt/homebrew/bin/python3; [ -x "$PY" ] || PY="$(command -v python3)"
mkdir -p "$HOME/.claude"
"$PY" - "$HOME/.claude/settings.json" "$1" "$PY $D/approve_hook.py" <<'P'
import json, os, sys
path, mode, cmd = sys.argv[1], sys.argv[2], sys.argv[3]
try: s = json.load(open(path))
except Exception: s = {}
hooks = s.setdefault("hooks", {})
entries = [e for e in hooks.get("PermissionRequest", [])
           if not any("approve_hook.py" in h.get("command", "") for h in e.get("hooks", []))]
if mode == "on":
    entries.append({"matcher": "*", "hooks": [{"type": "command", "command": cmd, "timeout": 300}]})
if entries: hooks["PermissionRequest"] = entries
else: hooks.pop("PermissionRequest", None)
if not hooks: s.pop("hooks", None)
if os.path.exists(path): os.replace(path, path + ".before-little-office")
json.dump(s, open(path, "w"), indent=2)
print("TV approvals", "ON" if mode == "on" else "OFF", "for Claude Code (backup: settings.json.before-little-office)")
P
launchctl kickstart -k "gui/$(id -u)/com.steroid.littleoffice" 2>/dev/null || true
