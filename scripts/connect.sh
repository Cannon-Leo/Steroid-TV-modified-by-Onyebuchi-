#!/bin/bash
# Usage: scripts/connect.sh [tv-ip[:port]]
# Connects to the TV over Wi-Fi debugging and records it so later scripts target it.
# With no address it tries, in order: the last TV that worked, the TV's network name (.local),
# then looks for it on your Wi-Fi. It restarts adb and retries, so a sleeping TV or a Mac that has
# just woken up still connects. Safe to run any time.
. "$(dirname "$0")/env.sh"
set +e
LAST="$ROOT/.tv-serial"; NAME="$ROOT/.tv-name"

try() {  # try one address a few times; prints the adb answer, returns 0 when connected
  local t="$1" out state
  [[ "$t" == *:* ]] || t="$t:5555"
  for i in 1 2 3; do
    out=$("$ADB" connect "$t" 2>&1 | tail -1)
    state=$("$ADB" -s "$t" get-state 2>/dev/null)
    if [ "$state" = "device" ]; then echo "$t" > "$LAST"; T="$t"; return 0; fi
    case "$out" in
      *refused*) WHY=refused; return 1 ;;                      # TV awake, debugging off
      *unauthorized*|*authenticate*) WHY=unauthorized ;;       # waiting for "Allow debugging?"
      *) WHY=unreachable ;;
    esac
    [ $i -eq 1 ] && "$ADB" kill-server >/dev/null 2>&1        # a stale adb server is the usual cause
    sleep 3
  done
  return 1
}

find_on_wifi() {  # every device on this Wi-Fi with port 5555 open
  local me net
  me=$(ipconfig getifaddr en0 2>/dev/null || ipconfig getifaddr en1 2>/dev/null || hostname -I 2>/dev/null | awk '{print $1}')
  [ -n "$me" ] || return
  net=${me%.*}
  local o="-w 1"; [ "$(uname)" = Darwin ] && o="-G 1 -w 1"
  for h in $(seq 1 254); do (nc -z $o "$net.$h" 5555 >/dev/null 2>&1 && echo "$net.$h") & done; wait
}

T=""; WHY=""
if [ -n "$1" ]; then
  try "$1"
else
  echo "Looking for the TV..."
  { [ -s "$LAST" ] && try "$(cat "$LAST")"; } ||
  { [ -s "$NAME" ] && try "$(cat "$NAME")"; } ||
  { for ip in $(find_on_wifi); do try "$ip" && break; done; [ -n "$T" ]; }
fi

if [ -z "$T" ]; then
  case "$WHY" in
    refused) echo "NOT CONNECTED: the TV answered but debugging is off. On the TV: Settings, Device Preferences, Developer options, turn Network debugging on. Then run this again." ;;
    unauthorized) echo "NOT CONNECTED: accept 'Allow debugging?' on the TV (tick 'Always allow'), then run this again." ;;
    *) echo "NOT CONNECTED: can't reach the TV. Wake it with the remote, check the Mac is on the same Wi-Fi, then run this again (or give the address: scripts/connect.sh 192.168.1.51)." ;;
  esac
  exit 1
fi
# remember the TV's network name too, so a new address from the router is found next time
host=$("$ADB" -s "$T" shell getprop net.hostname 2>/dev/null | tr -d '\r')
[ -n "$host" ] && echo "$host.local" > "$NAME"
"$ADB" -s "$T" shell 'echo "model: $(getprop ro.product.manufacturer) $(getprop ro.product.model)"; echo "android: $(getprop ro.build.version.release) (sdk $(getprop ro.build.version.sdk))"; echo "abi: $(getprop ro.product.cpu.abilist)"; echo "screen: $(wm size | tail -1)"'
echo "CONNECTED to $T"
