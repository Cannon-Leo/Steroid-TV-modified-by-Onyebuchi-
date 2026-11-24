#!/bin/bash
# Claude Code notices on the TV, the moment Claude needs you or finishes.
#   little-office/notify.sh on    adds Notification and Stop hooks to ~/.claude/settings.json (backup kept)
#   little-office/notify.sh off   removes them
set -e
HERE="$(cd "$(dirname "$0")" && pwd)"
D="$HOME/.little-office"; mkdir -p "$D"
cp "$HERE/notify_hook.py" "$D/"
PY=/opt/homebrew/bin/python3; [ -x "$PY" ] || PY="$(command -v python3)"
mkdir -p "$HOME/.claude"
"$PY" - "$HOME/.claude/settings.json" "${1:-on}" "$PY $D/notify_hook.py" <<'P'
import json, os, shutil, sys
path, mode, cmd = sys.argv[1], sys.argv[2], sys.argv[3]
try: s = json.load(open(path))
except Exception: s = {}
hooks = s.setdefault("hooks", {})
for ev in ("Notification", "Stop"):
    entries = [e for e in hooks.get(ev, [])
               if not any("notify_hook.py" in h.get("command", "") for h in e.get("hooks", []))]
    if mode == "on":
        entries.append({"hooks": [{"type": "command", "command": cmd, "timeout": 10}]})
    if entries: hooks[ev] = entries
    else: hooks.pop(ev, None)
if not hooks: s.pop("hooks", None)
if os.path.exists(path): shutil.copy(path, path + ".before-little-office")
json.dump(s, open(path, "w"), indent=2)
print("TV notices", "ON" if mode == "on" else "OFF", "for Claude Code")
P
