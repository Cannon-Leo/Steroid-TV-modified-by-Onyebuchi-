#!/usr/bin/env python3
"""Claude Code Notification and Stop hook: tell the TV right away.

Notification fires when Claude needs you (a question, a permission, or waiting for input);
Stop fires when Claude finishes its turn. The TV shows a small side notice that slides back
by itself. Only the project name, the kind and one short sentence are sent, and only to the
Little Office service on this computer, which serves them to your home network.
Never blocks Claude: any problem and it simply exits.
"""
import json
import os
import re
import sys
import urllib.request


def last_assistant_sentence(path):
    """The first sentence of Claude's last reply, from the session's transcript (tail only)."""
    try:
        with open(path, "rb") as f:
            f.seek(0, 2)
            f.seek(max(0, f.tell() - 200_000))
            lines = f.read().decode("utf-8", "replace").splitlines()
        for line in reversed(lines):
            try:
                o = json.loads(line)
            except ValueError:
                continue
            msg = o.get("message") or {}
            if o.get("type") != "assistant" or msg.get("role") != "assistant":
                continue
            for part in msg.get("content") or []:
                if isinstance(part, dict) and part.get("type") == "text" and part.get("text", "").strip():
                    t = re.sub(r"[`*_#>\[\]]", "", part["text"]).strip()
                    parts = [p for p in re.split(r"(?<=[.!?])\s|\n", t) if p.strip()]
                    out = parts[0] if parts else ""
                    if len(out) < 30 and len(parts) > 1:  # "Done." alone says little: add the next sentence
                        out += " " + parts[1]
                    return out[:140]
    except Exception:
        pass
    return ""


def codex(arg):
    """Codex calls its notify program with one JSON argument when a turn completes."""
    try:
        d = json.loads(arg)
    except ValueError:
        return
    if d.get("type") != "agent-turn-complete":
        return
    text = re.sub(r"[`*_#>\[\]]", "", str(d.get("last-assistant-message") or "")).strip()
    parts = [p for p in re.split(r"(?<=[.!?])\s|\n", text) if p.strip()]
    send("Codex", "finished", (parts[0] if parts else "Finished")[:280], str(d.get("cwd") or os.getcwd()))


def send(agent, kind, text, cwd):
    body = json.dumps({"agent": agent, "kind": kind, "text": text[:280], "cwd": cwd[:1000]}).encode()
    try:
        req = urllib.request.Request("http://127.0.0.1:8765/notify", data=body, method="POST",
                                     headers={"Content-Type": "application/json"})
        urllib.request.urlopen(req, timeout=2).read()
    except Exception:
        pass


def main():
    if len(sys.argv) > 1 and sys.argv[1].lstrip().startswith("{"):
        return codex(sys.argv[1])
    try:
        data = json.load(sys.stdin)
    except Exception:
        return
    event = data.get("hook_event_name", "")
    if event == "Notification":
        kind = "needs_you"
        text = str(data.get("message") or "Needs your input")
    elif event == "Stop":
        if data.get("stop_hook_active"):
            return
        kind = "finished"
        text = last_assistant_sentence(str(data.get("transcript_path") or "")) or "Finished"
    else:
        return
    body = json.dumps({"agent": "Claude", "kind": kind, "text": text[:280], "cwd": str(data.get("cwd") or os.getcwd())[:1000]}).encode()
    try:
        req = urllib.request.Request("http://127.0.0.1:8765/notify", data=body, method="POST",
                                     headers={"Content-Type": "application/json"})
        urllib.request.urlopen(req, timeout=2).read()
    except Exception:
        pass


if __name__ == "__main__":
    main()
