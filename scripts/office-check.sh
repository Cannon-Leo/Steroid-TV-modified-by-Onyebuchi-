#!/bin/bash
# Little Office, end to end: is every link working? Run on each computer, with the TV on.
#   scripts/office-check.sh            check everything and send a test notice to the TV
#   scripts/office-check.sh approve    also send a harmless test approval request (answer it on the TV)
. "$(dirname "$0")/env.sh"
ok() { printf "  \033[32m✓\033[0m %s\n" "$1"; }
no() { printf "  \033[31m✗\033[0m %s\n" "$1"; }
D="$HOME/.little-office"

echo "1. Little Office on this computer"
S=$(curl -sf -m 5 http://127.0.0.1:8765/status) && ok "service running" || { no "service not running: run little-office/install.sh"; exit 1; }
echo "$S" | "$PY" -c "import json,sys;d=json.load(sys.stdin);print('    computer:',d.get('computer'),'| sessions:',len(d['sessions']),'| events:',len(d['events']),'| approvals waiting:',d.get('approvals',0))
for a in d.get('agents',[]): print('    ',a.get('name'),'installed' if a.get('installed') else 'not found','·',a.get('note',''))"

echo "2. Claude Code on this computer"
if command -v claude >/dev/null 2>&1; then ok "Claude Code CLI installed ($(command -v claude))"; else no "no Claude Code CLI here: sessions in the Claude app or on the web run in the cloud and can't reach this computer"; fi
[ -d "$HOME/.claude/projects" ] && ok "local Claude Code sessions found ($(ls "$HOME/.claude/projects" | wc -l | tr -d ' ') projects)" || no "no local Claude Code sessions yet (~/.claude/projects)"
grep -q notify_hook.py "$HOME/.claude/settings.json" 2>/dev/null && ok "notice hooks registered" || no "notice hooks missing: python3 $D/hooks.py on"
grep -q approve_hook.py "$HOME/.claude/settings.json" 2>/dev/null && ok "approval hook registered" || no "approval hook missing: python3 $D/hooks.py on"
"$PY" -c "import json,time;d=json.load(open('$D/devices.json'));n=[v['name'] for v in d.values() if v['expires']>time.time()];print('    paired TVs:',', '.join(n) or 'none (pair in Little Office on the TV)')" 2>/dev/null || no "no paired TV yet: pair in Little Office on the TV"

echo "3. The TV"
tv get-state >/dev/null 2>&1 && ok "TV connected over adb" || { no "TV not reachable over adb (scripts/connect.sh)"; exit 1; }
tv shell dumpsys activity services tv.steroid.home 2>/dev/null | grep -q OfficeService && ok "notice service running on the TV" || no "notice service not running on the TV (open the home screen once)"
tv shell appops get tv.steroid.home SYSTEM_ALERT_WINDOW 2>/dev/null | grep -q allow && ok "allowed to show notices over other apps" || no "not allowed to show over other apps: run scripts/buchi.sh install"

echo "4. Test notice"
tv logcat -c 2>/dev/null
"$PY" "$D/notify_hook.py" <<J
{"hook_event_name": "Notification", "message": "Test from office-check: notices reach the TV", "cwd": "$PWD"}
J
echo "  sent. Watch the right side of the TV (up to 10 seconds)..."
sleep 10
tv logcat -d 2>/dev/null | grep SteroidOffice | tail -6 | sed 's/^/    /'
tv logcat -d 2>/dev/null | grep -q "showing .* notice" && ok "the TV showed the notice" || no "the TV didn't show it: send the lines above to Claude"

if [ "$1" = "approve" ]; then
  echo "5. Test approval (Reject or Approve it on the TV; nothing runs either way)"
  R=$("$PY" "$D/approve_hook.py" <<J
{"tool_name": "Bash", "tool_input": {"command": "echo office-check test"}, "cwd": "$PWD"}
J
)
  [ -n "$R" ] && ok "answer from the TV: $R" || no "no answer from the TV within 4 minutes (is this TV paired with this computer?)"
fi
