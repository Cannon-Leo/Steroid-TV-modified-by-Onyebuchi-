#!/usr/bin/env python3
"""Register Little Office's Claude Code hooks on THIS computer (Mac, Windows or Linux).

  python3 hooks.py on              notices (Notification, Stop) and TV approvals (PermissionRequest)
  python3 hooks.py off             removes them
  python3 hooks.py on notify       only the notices      python3 hooks.py on approve   only approvals

Edits ~/.claude/settings.json (a backup is kept next to it). Run it on every computer where you use
Claude Code, so every paired computer sends notices and approval requests to the TV.
"""
import json
import os
import shutil
import sys

HOME = os.path.expanduser("~")
D = os.path.join(HOME, ".little-office")
SETTINGS = os.path.join(HOME, ".claude", "settings.json")
WIN = os.name == "nt"


def command(script):
    py = sys.executable
    if WIN and py.lower().endswith("pythonw.exe"):
        py = py[:-len("pythonw.exe")] + "python.exe"  # hooks answer on stdout: needs the console python
    return f'"{py}" "{os.path.join(D, script)}"'


def main():
    mode = sys.argv[1] if len(sys.argv) > 1 else "on"
    only = sys.argv[2] if len(sys.argv) > 2 else "all"
    os.makedirs(os.path.dirname(SETTINGS), exist_ok=True)
    try:
        with open(SETTINGS, encoding="utf-8") as f:
            s = json.load(f)
    except Exception:
        s = {}
    hooks = s.setdefault("hooks", {})
    plan = []
    if only in ("all", "notify"):
        plan += [("Notification", "notify_hook.py", None, 10), ("Stop", "notify_hook.py", None, 10)]
    if only in ("all", "approve"):
        plan += [("PermissionRequest", "approve_hook.py", "*", 300)]
    for event, script, matcher, timeout in plan:
        entries = [e for e in hooks.get(event, [])
                   if not any(script in h.get("command", "") for h in e.get("hooks", []))]
        if mode == "on":
            e = {"hooks": [{"type": "command", "command": command(script), "timeout": timeout}]}
            if matcher:
                e = {"matcher": matcher, **e}
            entries.append(e)
        if entries:
            hooks[event] = entries
        else:
            hooks.pop(event, None)
    if not hooks:
        s.pop("hooks", None)
    if os.path.exists(SETTINGS):
        shutil.copy(SETTINGS, SETTINGS + ".before-little-office")
    with open(SETTINGS, "w", encoding="utf-8") as f:
        json.dump(s, f, indent=2)
    what = {"all": "notices and TV approvals", "notify": "notices", "approve": "TV approvals"}[only]
    print(f"Claude Code {what}: {'ON' if mode == 'on' else 'OFF'} on this computer")


if __name__ == "__main__":
    main()
