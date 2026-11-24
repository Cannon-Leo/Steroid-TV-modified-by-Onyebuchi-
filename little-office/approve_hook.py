#!/usr/bin/env python3
"""Claude Code PermissionRequest hook: ask the paired TV before the computer shows its own prompt.

Prints an allow or deny decision when the TV answers. Exits quietly (so the normal prompt appears
on the computer) when no TV is paired, Little Office isn't running, the TV doesn't answer in time,
or the request is one that should always be decided at the keyboard.
"""
import json
import re
import sys
import time
import urllib.request

BASE = "http://127.0.0.1:8765"
HOLD = 240

# Always decided at the computer, never from the sofa.
RISKY = re.compile(r"\bsudo\b|\brm\s+-[a-z]*r[a-z]*f|\brm\s+-[a-z]*f[a-z]*r|git\s+push\b.*(--force|-f\b)|"
                   r"\|\s*(ba|z)?sh\b|\bmkfs|\bdd\s+if=|chmod\s+-R\s+777|\.ssh/|\.env\b|keychain|"
                   r"git\s+reset\s+--hard|DROP\s+(TABLE|DATABASE)|\bshutdown\b|\breboot\b", re.I)


def call(method, path, body=None, timeout=35):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(BASE + path, data=data, method=method, headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=timeout) as r:
        return json.loads(r.read() or b"{}")


def describe(tool, ti):
    if tool == "Bash":
        cmd = str(ti.get("command", ""))
        return "Run a command", cmd.strip().splitlines()[0][:120] if cmd.strip() else "", cmd
    if tool in ("Edit", "MultiEdit", "Write", "NotebookEdit"):
        path = str(ti.get("file_path") or ti.get("notebook_path") or "")
        verb = "Create or replace" if tool == "Write" else "Edit"
        return f"{verb} a file", path.split("/")[-1], path
    if tool in ("WebFetch", "WebSearch"):
        u = str(ti.get("url") or ti.get("query") or "")
        return "Look something up online", u[:120], u
    if tool == "ExitPlanMode":
        return "Start working on the plan", "", str(ti.get("plan", ""))[:400]
    return f"Use {tool}", "", json.dumps(ti)[:400]


def main():
    try:
        data = json.load(sys.stdin)
    except Exception:
        return
    tool, ti = data.get("tool_name", ""), data.get("tool_input", {}) or {}
    what, short, detail = describe(tool, ti)
    if RISKY.search(json.dumps(ti)):
        return  # keyboard only
    try:
        r = call("POST", "/approval/new", {"tool": re.sub(r"[^\w.:\-]", "_", tool)[:80] or "Tool",
                                           "summary": (f"{what}: {short}" if short else what)[:280],
                                           "detail": detail[:1500], "cwd": str(data.get("cwd", ""))[:1000]}, timeout=3)
    except Exception:
        return  # no TV paired, or Little Office not running
    rid = r.get("id")
    if not rid:
        return
    start = time.time()
    try:
        while time.time() - start < HOLD:
            d = call("GET", f"/approval/{rid}/wait?t=25")
            if d.get("decision") in ("allow", "deny"):
                out = {"behavior": d["decision"]}
                if d["decision"] == "deny":
                    out["message"] = f"Rejected from the TV ({d.get('by') or 'Little Office'})."
                print(json.dumps({"hookSpecificOutput": {"hookEventName": "PermissionRequest", "decision": out}}))
                return
    except Exception:
        pass
    try:
        call("POST", f"/approval/{rid}/cancel", {}, timeout=3)
    except Exception:
        pass


if __name__ == "__main__":
    main()
