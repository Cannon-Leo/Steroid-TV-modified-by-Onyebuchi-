"""Remote approvals for Little Office: answer Claude Code permission prompts from the TV remote.

Flow: Claude Code runs approve_hook.py when it is about to ask permission. The hook hands a short
summary to this service (localhost only) and waits. A paired TV fetches pending requests, shows
them with Approve and Reject, and posts the answer back. No answer in time: the hook steps aside
and the normal prompt appears on the computer, so nothing ever runs without someone saying yes.

Security
  Pairing: the TV asks to pair; this computer shows an 8 digit code on its own screen; the code is
  typed on the TV. The code never crosses the network: the TV proves it with an HMAC, and the
  device token comes back encrypted with a key derived from the code. 3 wrong tries end the attempt.
  Requests: every TV call is signed (HMAC SHA256 of method, path, time, nonce and body with the
  device token). Old (over 90 s) or repeated requests are refused. Private network addresses only.
  Tokens expire after 90 days. `office.py devices` lists them,
  `office.py revoke <name|all>` removes them.
"""
import hashlib
import hmac
import json
import os
import platform
import secrets
import subprocess
import threading
import time

import schema

HOME = os.path.expanduser("~")
DEVICES = os.path.join(HOME, ".little-office", "devices.json")
SKEW = 90            # seconds a signed request may be off
PAIR_TTL = 180       # a pairing attempt is open this long
HOLD = 240           # a request waits this long for the TV, then the computer's own prompt takes over

MAX_DEVICES = 5      # paired TVs at most
DAYS = 90            # pairing lasts 90 days (re-pair from the TV; nothing lasts forever)
AUDIT = os.path.join(HOME, ".little-office", "audit.log")

lock = threading.Lock()
pending = {}         # id -> request dict (+ "decision", "event")
pairing = {}         # device -> {"code", "nonce", "name", "days", "at", "tries"}
seen_nonces = {}     # nonce -> time


def _load():
    try:
        with open(DEVICES) as f:
            return json.load(f)
    except Exception:
        return {}


def _save(d):
    os.makedirs(os.path.dirname(DEVICES), exist_ok=True)
    tmp = DEVICES + ".tmp"
    with open(tmp, "w") as f:
        json.dump(d, f, indent=2)
    os.chmod(tmp, 0o600)
    os.replace(tmp, DEVICES)


def audit(event, **kv):
    """Append one line to the audit log (pairings, revokes, decisions, refusals). Kept under 256 KB."""
    try:
        os.makedirs(os.path.dirname(AUDIT), exist_ok=True)
        if os.path.exists(AUDIT) and os.path.getsize(AUDIT) > 256_000:
            os.replace(AUDIT, AUDIT + ".1")
        line = time.strftime("%Y-%m-%d %H:%M:%S ") + event + " " + " ".join(f"{k}={str(v)[:80]}" for k, v in kv.items())
        with open(AUDIT, "a") as f:
            f.write(line + "\n")
        os.chmod(AUDIT, 0o600)
    except Exception:
        pass


def paired_devices():
    now = time.time()
    return {k: v for k, v in _load().items() if v.get("expires", 0) > now}


def _mac(key, msg):
    return hmac.new(key, msg.encode(), hashlib.sha256).digest()


def show_code(name, code):
    """Show the pairing code on this computer's screen (never sent over the network)."""
    text = f"Little Office: pairing code for {name} is {code[:4]} {code[4:]}"
    print(text, flush=True)
    try:
        if platform.system() == "Darwin":
            subprocess.Popen(["/usr/bin/osascript", "-e",
                              f'display dialog "Type this code on {name} to let it approve Claude Code requests:\\n\\n'
                              f'{code[:4]} {code[4:]}" with title "Little Office" buttons {{"OK"}} giving up after {PAIR_TTL}'])
        elif platform.system() == "Windows":
            subprocess.Popen(["powershell", "-NoProfile", "-Command",
                              "Add-Type -AssemblyName PresentationFramework;"
                              f"[System.Windows.MessageBox]::Show('Type this code on {name}: {code[:4]} {code[4:]}','Little Office')"])
    except Exception:
        pass


# ---------- pairing (unsigned, private network only) ----------

def pair_start(body, ip=""):
    ok, b = schema.PAIR_START.safe_parse(body)
    if not ok:
        return 400, {"error": "bad request"}
    if len(paired_devices()) >= MAX_DEVICES and b["device"] not in paired_devices():
        audit("pair_refused", ip=ip, why="too many devices")
        return 409, {"error": "too many paired TVs; revoke one on the computer"}
    code = "".join(secrets.choice("0123456789") for _ in range(8))
    with lock:
        pairing.clear()  # one pairing at a time
        pairing[b["device"]] = {"code": code, "nonce": b["nonce"], "name": b["name"], "days": DAYS, "at": time.time(), "tries": 0, "ip": ip}
    audit("pair_start", ip=ip, name=b["name"])
    show_code(b["name"].replace('"', ""), code)
    return 200, {"ok": True, "ttl": PAIR_TTL}


def pair_finish(body, ip=""):
    ok, b = schema.PAIR_FINISH.safe_parse(body)
    if not ok:
        return 400, {"error": "bad request"}
    device, proof = b["device"], b["proof"]
    with lock:
        p = pairing.get(device)
        if not p or time.time() - p["at"] > PAIR_TTL:
            pairing.pop(device, None)
            return 410, {"error": "expired"}
        key = p["code"].encode()
        want = _mac(key, f"pair|{device}|{p['nonce']}").hex()
        if not hmac.compare_digest(want, proof):
            p["tries"] += 1
            if p["tries"] >= 3:
                pairing.pop(device, None)
                audit("pair_locked", ip=ip, name=p["name"])
                return 403, {"error": "too many tries"}
            return 401, {"error": "wrong code", "left": 3 - p["tries"]}
        pairing.pop(device, None)
    token = secrets.token_bytes(32)
    wrap = _mac(key, f"wrap|{device}|{p['nonce']}")
    expires = time.time() + p["days"] * 86400
    d = _load()
    d[device] = {"name": p["name"], "token": token.hex(), "expires": expires, "paired": time.time()}
    _save(d)
    audit("paired", ip=ip, name=p["name"], days=p["days"])
    return 200, {"token": bytes(a ^ b for a, b in zip(token, wrap)).hex(), "expires": int(expires)}


# ---------- signed requests from a paired TV ----------

def verify(method, path, headers, body_text):
    """Returns the device id when the request is signed by a paired, unexpired TV, else None."""
    ok, h = schema.SIGNED_HEADERS.safe_parse({"device": headers.get("X-LO-Device"), "time": headers.get("X-LO-Time"),
                                              "nonce": headers.get("X-LO-Nonce"), "sig": headers.get("X-LO-Sig")})
    if not ok:
        return None
    if abs(time.time() - int(h["time"])) > SKEW:
        return None
    dev = paired_devices().get(h["device"])
    if not dev:
        return None
    want = _mac(bytes.fromhex(dev["token"]), f"{method}\n{path}\n{h['time']}\n{h['nonce']}\n{body_text}").hex()
    if not hmac.compare_digest(want, h["sig"]):
        return None
    now = time.time()
    with lock:
        for n in [n for n, t in seen_nonces.items() if now - t > 2 * SKEW]:
            del seen_nonces[n]
        if h["nonce"] in seen_nonces or len(seen_nonces) > 10000:
            return None   # replayed (or flooded)
        seen_nonces[h["nonce"]] = now
    return h["device"]


def count():
    _expire()
    with lock:
        return sum(1 for r in pending.values() if r["decision"] is None)


def listing():
    _expire()
    with lock:
        return [{k: r[k] for k in ("id", "at", "agent", "project", "tool", "summary", "detail")}
                for r in pending.values() if r["decision"] is None]


def decide(rid, body, device):
    ok, b = schema.DECISION.safe_parse(body)
    if not ok or not schema.REQUEST_ID.safe_parse(rid)[0]:
        return 400, {"error": "bad request"}
    decision = b["decision"]
    with lock:
        r = pending.get(rid)
        if not r or r["decision"] is not None:
            return 404, {"error": "gone"}
        r["decision"] = decision
        r["by"] = paired_devices().get(device, {}).get("name", "TV")
        r["event"].set()
    audit("decision", id=rid, decision=decision, by=r["by"], what=r["summary"])
    return 200, {"ok": True}


def forget(device):
    audit("unpaired_from_tv", name=_load().get(device, {}).get("name", "?"))
    d = _load()
    d.pop(device, None)
    _save(d)
    return 200, {"ok": True}


# ---------- the local hook (127.0.0.1 only) ----------

def new_request(body, project):
    ok, body = schema.HOOK_REQUEST.safe_parse(body)
    if not ok:
        return 400, {"error": "bad request"}
    if not paired_devices():
        return 409, {"error": "no paired TV"}
    rid = secrets.token_hex(8)
    with lock:
        if sum(1 for r in pending.values() if r["decision"] is None) >= 20:
            return 429, {"error": "too many waiting"}
        pending[rid] = {"id": rid, "at": time.time(), "agent": "Claude", "project": project,
                        "tool": str(body.get("tool", ""))[:40], "summary": str(body.get("summary", ""))[:140],
                        "detail": str(body.get("detail", ""))[:400], "decision": None, "event": threading.Event()}
    return 200, {"id": rid}


def wait(rid, seconds):
    if not schema.REQUEST_ID.safe_parse(rid)[0]:
        return 400, {"decision": None}
    seconds = max(1, min(30, seconds))
    with lock:
        r = pending.get(rid)
    if not r:
        return 404, {"decision": None}
    r["event"].wait(min(30, max(1, seconds)))
    with lock:
        if r["decision"] is not None or time.time() - r["at"] > HOLD:
            pending.pop(rid, None)
    return 200, {"decision": r["decision"], "by": r.get("by")}


def cancel(rid):
    if not schema.REQUEST_ID.safe_parse(rid)[0]:
        return 400, {"error": "bad request"}
    with lock:
        pending.pop(rid, None)
    return 200, {"ok": True}


def _expire():
    now = time.time()
    with lock:
        for rid in [k for k, r in pending.items() if now - r["at"] > HOLD + 30]:
            del pending[rid]


def cli(args):
    if args and args[0] == "devices":
        for k, v in _load().items():
            left = (v["expires"] - time.time()) / 86400
            print(f"{v['name']:24} {k[:12]}  {'expired' if left < 0 else f'{left:.0f} days left'}")
        return True
    if args and args[0] == "revoke":
        d = _load()
        target = " ".join(args[1:])
        keep = {} if target == "all" else {k: v for k, v in d.items() if v["name"] != target and k != target}
        _save(keep)
        audit("revoked", target=target, count=len(d) - len(keep))
        print(f"removed {len(d) - len(keep)} device(s)")
        return True
    return False
