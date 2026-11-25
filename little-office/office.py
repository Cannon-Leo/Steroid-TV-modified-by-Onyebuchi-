#!/opt/homebrew/bin/python3
"""Little Office: a read-only glance at Claude Code, Codex and Gemini (Antigravity) activity for the Steroid TV launcher.

Serves GET /status on the local network (private addresses only) with, per recent session:
project, agent, state (working / needs_you / finished / idle) and one short sentence.
No transcripts, file lists or logs ever leave this Mac. With TV approvals on (approvals.sh on), a
one line summary of a pending permission request goes to paired, signed-in TVs only.
Edit ~/.little-office/config.json to choose pinned projects:
  {"pinned": ["My App", "Website"],            <- project names as the TV shows them
   "names": {"my-app-folder": "My App"}}       <- optional: folder-name fragment -> display name
"""
import glob
import json
import os
import platform
import re
import socket
import sqlite3
import urllib.parse
import subprocess
import threading
import time
from datetime import datetime, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from ipaddress import ip_address

import sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import schema  # strict input schemas (Zod style)
import approvals  # remote approvals from the TV (Claude Code), see approvals.py

HOME = os.path.expanduser("~")
CONFIG = os.path.join(HOME, ".little-office", "config.json")
PORT = 8765
COMPUTER = None  # set at start
RECENT = 6 * 3600          # sessions touched in the last 6 hours are worth a glance
WORKING_QUIET = 150        # a session silent this long is no longer "working"...
TOOL_QUIET = 20 * 60       # ...unless it's waiting on a long-running tool
MAX_EVENTS = 50

events = []                # [{"id","at","agent","project","kind","text"}]
hooked = {}                # (agent, project) -> time a hook reported it (the file watcher then stays quiet)
last_state = {}            # session key -> state
lock = threading.Lock()


def config():
    try:
        with open(CONFIG) as f:
            return json.load(f)
    except Exception:
        return {"pinned": [], "names": {}}


def parse_ts(s):
    try:
        return datetime.fromisoformat(s.replace("Z", "+00:00")).timestamp()
    except Exception:
        return None


def tail_lines(path, max_bytes=400_000):
    with open(path, "rb") as f:
        f.seek(0, 2)
        size = f.tell()
        f.seek(max(0, size - max_bytes))
        data = f.read().decode("utf-8", "ignore")
    lines = data.splitlines()
    if size > max_bytes:
        lines = lines[1:]  # first line is probably cut
    out = []
    for l in lines:
        try:
            out.append(json.loads(l))
        except Exception:
            pass
    return out


def sentence(text, limit=90):
    if not text:
        return ""
    text = re.sub(r"[`*_#>\[\]]", "", text).strip()
    text = re.sub(r"\s+", " ", text)
    m = re.match(r"(.+?[.!?])(\s|$)", text)
    s = m.group(1) if m else text
    return s if len(s) <= limit else s[: limit - 1].rstrip() + "…"


def project_name(cwd, names):
    if not cwd:
        return "Unknown"
    base = os.path.basename(cwd.rstrip("/")) or cwd
    for key, label in names.items():
        if key.lower() in cwd.lower():
            return label
    if re.match(r"^\d{4}-\d{2}-\d{2}$", base) or "scratch" in cwd:
        return "Scratch"
    return base.replace("-", " ").replace("_", " ").strip().title() if base.islower() else base.replace("-", " ")


# ---------- Titles (cached: transcripts can be large) ----------

_titles = {}


def claude_title(path, mtime):
    hit = _titles.get(path)
    if hit and hit[0] == mtime:
        return hit[1]
    title = None
    with open(path, "rb") as f:
        for raw in f:
            if b"title" not in raw[:120]:
                continue
            try:
                d = json.loads(raw)
            except Exception:
                continue
            if d.get("type") in ("custom-title", "ai-title", "summary"):
                title = d.get("customTitle") or d.get("aiTitle") or d.get("title") or d.get("summary") or title
    _titles[path] = (mtime, title)
    return title


def codex_titles():
    names = {}
    try:
        with open(os.path.join(HOME, ".codex", "session_index.jsonl")) as f:
            for l in f:
                try:
                    d = json.loads(l)
                    names[d["id"]] = d.get("thread_name")
                except Exception:
                    pass
    except Exception:
        pass
    return names


# ---------- Claude Code ----------

def claude_sessions(now, names):
    out = []
    for path in glob.glob(os.path.join(HOME, ".claude", "projects", "*", "*.jsonl")):
        mtime = os.path.getmtime(path)
        if now - mtime > RECENT:
            continue
        rows = [r for r in tail_lines(path) if r.get("type") in ("user", "assistant")]
        rows = [r for r in rows if not r.get("isSidechain")]
        if not rows:
            continue
        cwd = next((r.get("cwd") for r in reversed(rows) if r.get("cwd")), "")
        last = rows[-1]
        msg = last.get("message", {}) or {}
        content = msg.get("content")
        blocks = content if isinstance(content, list) else []
        kinds = [b.get("type") for b in blocks]
        quiet = now - mtime

        # the most recent thing it said or did, as one sentence
        activity = ""
        for r in reversed(rows[-40:]):
            m = r.get("message", {}) or {}
            for b in reversed(m.get("content") if isinstance(m.get("content"), list) else []):
                if b.get("type") == "tool_use":
                    inp = b.get("input") or {}
                    d = inp.get("description") or inp.get("summary") or inp.get("action_summary")
                    if d:
                        activity = sentence(d); break
                if b.get("type") == "text" and r.get("type") == "assistant" and b.get("text", "").strip():
                    activity = sentence(b["text"]); break
            if activity:
                break

        asks = False
        if last.get("type") == "assistant":
            for b in blocks:
                if b.get("type") == "tool_use" and b.get("name") in ("AskUserQuestion", "ExitPlanMode"):
                    asks = True
                if b.get("type") == "tool_use" and b.get("name", "").endswith("wrap_up") and (b.get("input") or {}).get("ask"):
                    asks = True
            text = " ".join(b.get("text", "") for b in blocks if b.get("type") == "text").strip()
            if msg.get("stop_reason") == "end_turn" and text.endswith("?"):
                asks = True

        if last.get("type") == "assistant" and "tool_use" in kinds:
            # A pending tool looks the same whether it's running long or awaiting approval,
            # so only an explicit question counts as "needs you".
            state = "needs_you" if asks else "working" if quiet < TOOL_QUIET else "idle"
        elif last.get("type") == "user":
            state = "working" if quiet < WORKING_QUIET else "idle"
        else:
            state = "needs_you" if asks else "finished"
        title = claude_title(path, mtime)
        project = project_name(cwd, names)
        if project == "Scratch" and title:
            project = title
        out.append({"key": "claude:" + path, "agent": "Claude", "project": project, "title": title or "",
                    "state": state, "text": activity or (title or ""), "at": mtime})
    return out


# ---------- Codex ----------

def codex_sessions(now, names):
    out = []
    titles = codex_titles()
    for path in glob.glob(os.path.join(HOME, ".codex", "sessions", "*", "*", "*", "*.jsonl")):
        mtime = os.path.getmtime(path)
        if now - mtime > RECENT:
            continue
        rows = tail_lines(path)
        cwd = ""
        try:
            with open(path) as f:
                first = json.loads(f.readline())
            cwd = (first.get("payload") or {}).get("cwd", "")
        except Exception:
            pass
        started = completed = None
        activity = ""
        last_msg = ""
        for r in rows:
            p = r.get("payload") or {}
            t = p.get("type")
            if t == "turn_context" and p.get("cwd"):
                cwd = p["cwd"]
            if t == "task_started":
                started, completed = r.get("timestamp"), None
            if t == "task_complete":
                completed = r.get("timestamp")
                last_msg = p.get("last_agent_message") or last_msg
            if t == "message" and p.get("role") == "assistant":
                txt = " ".join(c.get("text", "") for c in p.get("content", []) if isinstance(c, dict))
                if txt.strip():
                    activity = sentence(txt)
            if t == "reasoning":
                summ = " ".join(s.get("text", "") for s in (p.get("summary") or []) if isinstance(s, dict))
                if summ.strip():
                    activity = sentence(summ)
        quiet = now - mtime
        if started and not completed:
            state = "working" if quiet < TOOL_QUIET else "idle"
        else:
            state = "needs_you" if last_msg.strip().endswith("?") else "finished"
            if last_msg:
                activity = sentence(last_msg)
        sid = re.findall(r"[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}", os.path.basename(path))
        title = next((titles[i] for i in reversed(sid) if titles.get(i)), None)
        project = project_name(cwd, names)
        # Codex threads that live in a dated scratch folder are better known by their thread title
        if title and (not cwd or "/Documents/Codex/" in cwd or project in ("Unknown", "Scratch")):
            project = title
        out.append({"key": "codex:" + path, "agent": "Codex", "project": project, "title": title or "",
                    "state": state, "text": activity or (title or ""), "at": mtime})
    return out


# ---------- Gemini (Antigravity): reads its own conversation index, read-only ----------

AG_DB = os.path.join(HOME, ".gemini", "antigravity", "conversation_summaries.db")


def ag_time(s):
    """Antigravity writes '2026-11-24 02:48:03.27476+00:00' (any number of fraction digits)."""
    try:
        m = re.match(r"(\d{4}-\d{2}-\d{2})[ T](\d{2}:\d{2}:\d{2})(?:\.(\d+))?(.*)$", s or "")
        if not m:
            return None
        frac = (m.group(3) or "0")[:6].ljust(6, "0")
        tz = m.group(4) or "+00:00"
        if tz == "Z":
            tz = "+00:00"
        return datetime.fromisoformat(f"{m.group(1)}T{m.group(2)}.{frac}{tz}").timestamp()
    except Exception:
        return None


def gemini_waiting(cid):
    """True when the task's last step never finished (Antigravity marks finished steps 3, stopped ones 6 or 7)."""
    try:
        con = sqlite3.connect(f"file:{os.path.join(os.path.dirname(AG_DB), 'conversations', cid + '.db')}?mode=ro", uri=True, timeout=1)
        row = con.execute("select status from steps order by idx desc limit 1").fetchone()
        con.close()
        return bool(row) and row[0] not in (3, 6, 7)
    except Exception:
        return False


def gemini_sessions(now, names):
    if not os.path.exists(AG_DB):
        return []
    out = []
    try:
        con = sqlite3.connect(f"file:{AG_DB}?mode=ro", uri=True, timeout=2)
        rows = con.execute("select conversation_id, title, preview, last_modified_time, workspace_uris, status, "
                           "not_fully_idle, killed, last_user_input_time, nesting_depth from conversation_summaries").fetchall()
        con.close()
    except Exception as e:
        print("gemini read failed:", e, flush=True)
        return []
    for cid, title, preview, modified, ws, status, busy, killed, last_input, depth in rows:
        if depth:          # sub-agents roll up into their parent task
            continue
        at = ag_time(modified)
        # the task's own file changes while Antigravity works (the summary updates less often)
        live = 0
        try:
            live = os.path.getmtime(os.path.join(os.path.dirname(AG_DB), "conversations", cid + ".db"))
        except OSError:
            pass
        at = max(at or 0, live) or None
        if at is None or now - at > RECENT:
            continue
        cwd = ""
        try:
            uris = json.loads(ws) if ws else []
            if uris:
                cwd = urllib.parse.unquote(urllib.parse.urlparse(uris[0]).path)
        except Exception:
            pass
        st = (status or "").upper()
        quiet = now - at
        if any(k in st for k in ("WAIT", "PENDING", "APPROVAL", "USER_INPUT", "BLOCKED")):
            state = "needs_you"
        elif (any(k in st for k in ("RUNNING", "BUSY", "ACTIVE", "EXECUT")) or busy) and quiet < TOOL_QUIET:
            state = "working"
        elif live and now - live < 45:
            state = "working"      # writing steps right now
        elif live and now - live < 20 * 60 and gemini_waiting(cid):
            state = "needs_you"    # stopped part way through a step: waiting on you (an approval or a question)
        elif killed:
            state = "finished"
        else:
            inp = ag_time(last_input) or 0
            state = "finished" if at > inp + 5 else "idle"
        out.append({"key": "gemini:" + cid, "agent": "Gemini", "project": project_name(cwd, names) if cwd else (title or "Antigravity"),
                    "title": title or "", "state": state, "text": sentence(title or preview or ""), "at": at})
    return out


# ---------- Which agents exist on this Mac, and the last thing each did ----------

def ago(t, now):
    m = int((now - t) // 60)
    if m < 1:
        return "just now"
    if m < 60:
        return f"{m} min ago"
    h = m // 60
    return f"{h} h ago" if h < 48 else f"{h // 24} days ago"


WINDOWS = platform.system() == "Windows"


def computer_name():
    """A friendly name for this computer, shown on the TV ("MacBook Pro", "DESKTOP-ABC")."""
    if not WINDOWS:
        try:
            n = subprocess.run(["scutil", "--get", "ComputerName"], capture_output=True, text=True, timeout=2).stdout.strip()
            if n:
                return n
        except Exception:
            pass
    return platform.node().split(".")[0] or "Computer"


def app_running(name):
    try:
        if WINDOWS:
            out = subprocess.run(["tasklist", "/FI", f"IMAGENAME eq {name}.exe", "/NH"], capture_output=True, text=True,
                                 timeout=4, creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0)).stdout
            return f"{name}.exe".lower() in out.lower()
        return subprocess.run(["pgrep", "-x", name], capture_output=True, timeout=2).returncode == 0
    except Exception:
        return False


def agents(now):
    out = []
    # Claude: Claude Code in Terminal keeps local logs; the Claude app's sessions run in the cloud
    has_cli = os.path.isdir(os.path.join(HOME, ".claude", "projects"))
    app = app_running("Claude")
    if has_cli:
        note = ""
    elif app:
        note = "App open. Its sessions run in the cloud, so their status isn't on this Mac"
    else:
        note = "Not on this computer"
    out.append({"name": "Claude", "installed": has_cli or app, "note": note})
    has_codex = os.path.isdir(os.path.join(HOME, ".codex"))
    out.append({"name": "Codex", "installed": has_codex, "note": "" if has_codex else "Not on this computer"})
    note, inst = "Not on this computer", False
    if os.path.exists(AG_DB):
        inst = True
        try:
            con = sqlite3.connect(f"file:{AG_DB}?mode=ro", uri=True, timeout=2)
            r = con.execute("select title, last_modified_time from conversation_summaries where nesting_depth = 0 "
                            "order by last_modified_time desc limit 1").fetchone()
            con.close()
            t = ag_time(r[1]) if r else None
            note = f"Last: {sentence(r[0], 48)} · {ago(t, now)}" if r and t else "No tasks yet"
        except Exception:
            note = "Antigravity found"
    out.append({"name": "Gemini", "installed": inst, "note": note})
    return out


# ---------- Snapshot + events ----------

def snapshot():
    now = time.time()
    cfg = config()
    names = cfg.get("names", {})
    sessions = claude_sessions(now, names) + codex_sessions(now, names) + gemini_sessions(now, names)
    with lock:
        for s in sessions:
            prev = last_state.get(s["key"])
            if (prev is not None and prev != s["state"] and s["state"] in ("needs_you", "finished")
                    and now - hooked.get((s["agent"], s["project"]), 0) > 120):
                events.append({"id": int(now * 1000) + len(events), "at": now, "agent": s["agent"],
                               "project": s["project"], "kind": s["state"],
                               "text": s["text"] or ("Needs your input" if s["state"] == "needs_you" else "Finished")})
            last_state[s["key"]] = s["state"]
        del events[:-MAX_EVENTS]
        evs = list(events)
    sessions.sort(key=lambda s: -s["at"])
    for s in sessions:
        s.pop("key", None)
    return {"generated_at": now, "pinned": cfg.get("pinned", []), "sessions": sessions, "events": evs,
            "agents": agents(now), "computer": COMPUTER, "approvals": approvals.count()}


_cache = {"at": 0, "data": None}


def cached_snapshot():
    """The TV asks every few seconds; reading the logs at most every 3 s keeps that cheap."""
    if time.time() - _cache["at"] > 3 or _cache["data"] is None:
        _cache["data"] = snapshot()
        _cache["at"] = time.time()
    return _cache["data"]


def add_notice(b):
    """A hook (Claude Code) says the agent needs you or finished: a notice for the TV right away."""
    project = project_name(b["cwd"], config().get("names", {}))
    now = time.time()
    with lock:
        hooked[(b["agent"], project)] = now
        events.append({"id": int(now * 1000) + len(events), "at": now, "agent": b["agent"], "project": project,
                       "kind": b["kind"], "text": " ".join(b["text"].split())[:140] or
                       ("Needs your input" if b["kind"] == "needs_you" else "Finished")})
        del events[:-MAX_EVENTS]
    _cache["at"] = 0  # the next /status carries it
    return 200, {"ok": True}


PHOTO_NAME = re.compile(r"[\w .()\-]{1,80}\.(jpg|jpeg|png|webp)", re.I)


def photo_list():
    """The photos to offer the TV: from config photos_folder, minus photos_skip; names checked, symlinks ignored."""
    cfg = config()
    folder = os.path.expanduser(str(cfg.get("photos_folder") or ""))
    skip = set(cfg.get("photos_skip") or [])
    if not folder or not os.path.isdir(folder):
        return []
    out = []
    for n in sorted(os.listdir(folder)):
        p = os.path.join(folder, n)
        if n in skip or not PHOTO_NAME.fullmatch(n) or os.path.islink(p) or not os.path.isfile(p):
            continue
        if os.path.getsize(p) > 25_000_000:
            continue
        out.append((n, p))
    return out[:300]


def watcher():
    while True:
        try:
            snapshot()
        except Exception as e:
            print("snapshot failed:", e, flush=True)
        time.sleep(10)


class Guard:
    """Guardrails in front of every request: rate limits, lockouts after bad signatures, host checks."""
    def __init__(self):
        self.hits = {}       # (ip, bucket) -> [times]
        self.blocked = {}    # ip -> until
        self.lock = threading.Lock()

    def allow(self, ip, bucket, limit, window):
        now = time.time()
        with self.lock:
            if self.blocked.get(ip, 0) > now:
                return False
            q = [t for t in self.hits.get((ip, bucket), []) if now - t < window]
            if len(q) >= limit:
                self.hits[(ip, bucket)] = q
                return False
            q.append(now)
            self.hits[(ip, bucket)] = q
            if len(self.hits) > 5000:  # never grow without bound
                self.hits = {k: v for k, v in self.hits.items() if v and now - v[-1] < 600}
            return True

    def failed(self, ip):
        """Count a failed signature; ten in ten minutes blocks that address for fifteen minutes."""
        if not self.allow(ip, "fail", 10, 600):
            with self.lock:
                self.blocked[ip] = time.time() + 900
            approvals.audit("blocked", ip=ip, why="repeated bad signatures")


guard = Guard()
LOCAL_HOSTS = {"127.0.0.1:8765", "localhost:8765"}
MAX_BODY = 8192


class Handler(BaseHTTPRequestHandler):
    timeout = 10                       # slow or stalled clients are dropped
    server_version = "LittleOffice"
    sys_version = ""

    def where(self):
        ip = self.client_address[0]
        try:
            a = ip_address(ip)
            private = a.is_private or a.is_loopback
        except ValueError:
            private = False
        return ip, ip == "127.0.0.1", private

    def host_ok(self, local):
        """Refuse requests whose Host is a web domain (DNS rebinding from a browser)."""
        host = (self.headers.get("Host") or "").lower()
        if local:
            return host in LOCAL_HOSTS
        name = host.rsplit(":", 1)[0].strip("[]")
        try:
            ip_address(name)
            return True
        except ValueError:
            return name.endswith(".local") or name == ""

    def do_GET(self):
        ip, local, private = self.where()
        path = urllib.parse.urlparse(self.path).path
        if not private or not self.host_ok(local and path.startswith("/approval/")) or not guard.allow(ip, "all", 240, 60):
            return self.reply(429 if private else 404, {"error": "refused"})
        if path == "/test-event" and local:
            # local-only: inject one sample notice to check the TV's notification
            with lock:
                events.append({"id": int(time.time() * 1000), "at": time.time(), "agent": "Claude",
                               "project": "Demo project", "kind": "needs_you", "text": "Asking which design to keep"})
            self.send_response(204)
            self.end_headers()
            return
        if local and path.startswith("/approval/") and path.endswith("/wait"):
            q = urllib.parse.parse_qs(urllib.parse.urlparse(self.path).query)
            ok, t = schema.WAIT_SECONDS.safe_parse(int(q.get("t", ["25"])[0]) if q.get("t", ["25"])[0].isdigit() else -1)
            return self.reply(*approvals.wait(path.split("/")[2], t if ok else 25))
        if path == "/approvals/count":
            return self.reply(200, {"n": approvals.count()})
        if path == "/approvals":
            v = approvals.verify_full("GET", path, self.headers, "")
            if not v:
                guard.failed(ip)
                return self.reply(401, {"error": "not paired"})
            # signed for this TV and this request, so nothing on the network can change what is shown
            return self.reply(200, {"requests": approvals.listing()}, sign=v)
        if path == "/photos" or path.startswith("/photo/"):
            # your photos and profile picture, for paired TVs only (signed requests)
            if not approvals.verify("GET", path, self.headers, ""):
                guard.failed(ip)
                return self.reply(401, {"error": "not paired"})
            files = photo_list()
            if path == "/photos":
                return self.reply(200, {"photos": [{"name": n, "size": os.path.getsize(p)} for n, p in files],
                                        "name": str(config().get("name", ""))[:40]})
            idx = path.split("/")[2]
            if not idx.isdigit() or int(idx) >= len(files):
                return self.reply(404, {"error": "not found"})
            return self.send_file(files[int(idx)][1])
        if path != "/status":
            return self.reply(404, {"error": "not found"})
        self.reply(200, cached_snapshot())

    def do_POST(self):
        ip, local, private = self.where()
        path = urllib.parse.urlparse(self.path).path
        if not private or not guard.allow(ip, "all", 240, 60):
            return self.reply(429 if private else 404, {"error": "refused"})
        hook = local and path.startswith("/approval/")
        if not self.host_ok(hook):
            return self.reply(403, {"error": "refused"})
        # JSON only: a web page can't send this without a preflight we never answer
        if (self.headers.get("Content-Type") or "").split(";")[0].strip() != "application/json":
            return self.reply(415, {"error": "json only"})
        try:
            n = int(self.headers.get("Content-Length") or 0)
        except ValueError:
            n = -1
        if n < 0 or n > MAX_BODY:
            return self.reply(413, {"error": "too large"})
        text = self.rfile.read(n).decode("utf-8", "replace") if n else ""
        try:
            body = json.loads(text) if text else {}
        except ValueError:
            return self.reply(400, {"error": "bad json"})
        if hook and path == "/approval/new":
            ok, b = schema.HOOK_REQUEST.safe_parse(body)
            if not ok:
                return self.reply(400, {"error": "bad request"})
            return self.reply(*approvals.new_request(b, project_name(b["cwd"], config().get("names", {}))))
        if local and path == "/notify" and (self.headers.get("Host") or "").lower() in LOCAL_HOSTS:
            ok, b = schema.NOTIFY.safe_parse(body)
            if not ok:
                return self.reply(400, {"error": "bad request"})
            if not guard.allow(ip, "notify", 60, 60):
                return self.reply(429, {"error": "slow down"})
            return self.reply(*add_notice(b))
        if hook and path.endswith("/cancel"):
            return self.reply(*approvals.cancel(path.split("/")[2]))
        if path == "/pair/start":
            # pairing pops a dialog on this computer: a few attempts per address, and per computer
            if not guard.allow(ip, "pair", 4, 600) or not guard.allow("*", "pair", 10, 600):
                return self.reply(429, {"error": "too many pairing attempts; wait a few minutes"})
            return self.reply(*approvals.pair_start(body, ip))
        if path == "/pair/finish":
            if not guard.allow(ip, "finish", 10, 600):
                return self.reply(429, {"error": "too many tries"})
            return self.reply(*approvals.pair_finish(body, ip))
        device = approvals.verify("POST", path, self.headers, text)
        if not device:
            guard.failed(ip)
            approvals.audit("refused", ip=ip, path=path[:40])
            return self.reply(401, {"error": "not paired"})
        if path.startswith("/approvals/") and path.count("/") == 2:
            return self.reply(*approvals.decide(path.split("/")[2], body, device))
        if path == "/forget" and schema.EMPTY.safe_parse(body)[0]:
            return self.reply(*approvals.forget(device))
        self.reply(404, {"error": "not found"})

    def do_PUT(self): self.reply(405, {"error": "method not allowed"})
    do_DELETE = do_PATCH = do_OPTIONS = do_HEAD = do_PUT

    def send_file(self, p):
        size = os.path.getsize(p)
        ext = p.lower().rsplit(".", 1)[-1]
        self.send_response(200)
        self.send_header("Content-Type", {"png": "image/png", "webp": "image/webp"}.get(ext, "image/jpeg"))
        self.send_header("Content-Length", str(size))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        with open(p, "rb") as f:
            while True:
                b = f.read(65536)
                if not b:
                    break
                self.wfile.write(b)

    def reply(self, code, obj, sign=None):
        body = json.dumps(obj).encode()
        self.send_response(code)
        if sign:
            self.send_header("X-LO-Reply", approvals.sign_reply(sign[0], sign[1], urllib.parse.urlparse(self.path).path, body))
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.send_header("X-Content-Type-Options", "nosniff")
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, *a):
        pass


if __name__ == "__main__":
    if approvals.cli(sys.argv[1:]):  # office.py devices | office.py revoke <name|all>
        sys.exit(0)
    if not os.path.exists(CONFIG):
        os.makedirs(os.path.dirname(CONFIG), exist_ok=True)
        with open(CONFIG, "w") as f:
            json.dump({"pinned": [], "names": {}}, f, indent=2)
    snapshot()  # baseline, so existing states don't fire notifications
    threading.Thread(target=watcher, daemon=True).start()
    COMPUTER = computer_name()
    # Announce on the network so the TV finds this computer even if its address changes.
    # Each computer uses its own name, so a Mac and a Windows PC can both be found at once.
    service = f"Little Office {COMPUTER}"[:60]
    if WINDOWS:
        try:
            from zeroconf import ServiceInfo, Zeroconf  # installed by install-windows.ps1
            ip = socket.gethostbyname(socket.gethostname())
            try:  # prefer the address used to reach the network
                sk = socket.socket(socket.AF_INET, socket.SOCK_DGRAM); sk.connect(("192.168.1.1", 9)); ip = sk.getsockname()[0]; sk.close()
            except Exception:
                pass
            zc = Zeroconf()
            zc.register_service(ServiceInfo("_littleoffice._tcp.local.", f"{service}._littleoffice._tcp.local.",
                                            addresses=[socket.inet_aton(ip)], port=PORT, server=f"{socket.gethostname()}.local."))
        except Exception as e:
            print("announce failed:", e, flush=True)
    else:
        subprocess.Popen(["/usr/bin/dns-sd", "-R", service, "_littleoffice._tcp", "local", str(PORT)],
                         stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    ThreadingHTTPServer(("0.0.0.0", PORT), Handler).serve_forever()
