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


def codex_notify(mode):
    """Codex: a notify program in ~/.codex/config.toml (Codex has no approval hook, so notices only).
    Written even before Codex is installed, so it works the day you install it. An existing notify
    line of your own is left alone."""
    path = os.path.join(HOME, ".codex", "config.toml")
    try:
        text = open(path, encoding="utf-8").read()
    except FileNotFoundError:
        text = ""
    ours = "notify_hook.py" in text
    lines = [l for l in text.splitlines() if not (l.strip().startswith("notify") and "notify_hook.py" in l)]
    if mode == "on":
        if any(l.strip().startswith("notify") for l in lines):
            print("Codex already has its own notify program; left it alone")
            return
        py = command("notify_hook.py").split('" "')[0].strip('"')
        entry = "notify = " + json.dumps([py, os.path.join(D, "notify_hook.py")])
        # top level keys must come before any [table] in TOML
        first_table = next((i for i, l in enumerate(lines) if l.strip().startswith("[")), len(lines))
        lines.insert(first_table, entry)
    elif not ours:
        return
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        f.write("\n".join(lines).strip() + "\n")


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
    if only in ("all", "notify"):
        codex_notify(mode)
    what = {"all": "notices (Claude Code, Codex) and TV approvals (Claude Code)", "notify": "notices (Claude Code, Codex)", "approve": "TV approvals (Claude Code)"}[only]
    print(f"{what}: {'ON' if mode == 'on' else 'OFF'} on this computer")


if __name__ == "__main__":
    main()
