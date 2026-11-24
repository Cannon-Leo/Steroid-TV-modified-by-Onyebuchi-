#!/bin/bash
# Unstick a mini player (picture in picture) on the TV.
#   scripts/pip.sh          show which app is in a mini player
#   scripts/pip.sh full     bring it back to full screen
#   scripts/pip.sh close    close it (stops that app)
. "$(dirname "$0")/env.sh"
# the pinned (mini player) task: its package and task id, from the window manager
info=$(tv shell dumpsys activity activities | tr -d '\r' | awk '
  /mWindowingMode=pinned|windowingMode=pinned/ {pin=1}
  pin && /\* Task\{|TaskRecord\{|\* Task id=|Task id #/ { if (match($0, /#[0-9]+/)) id=substr($0, RSTART+1, RLENGTH-1) }
  pin && /(realActivity|mActivityComponent)=/ { if (match($0, /=[A-Za-z0-9_.]+\/[A-Za-z0-9_.$]+/)) { print id " " substr($0, RSTART+1, RLENGTH-1); exit } }')
if [ -z "$info" ]; then  # older layout: look at the window list instead
  pkg=$(tv shell dumpsys window windows | tr -d '\r' | grep -B30 -i "mode=pinned\|windowingMode=pinned" | grep -o "u0 [A-Za-z0-9_.]*/" | tail -1 | cut -d' ' -f2 | tr -d '/')
  [ -n "$pkg" ] && info="? $pkg/"
fi
if [ -z "$info" ]; then echo "No mini player on the TV right now."; exit 0; fi
task=${info%% *}; comp=${info#* }; pkg=${comp%%/*}
echo "Mini player: $pkg (task $task)"
case "$1" in
  full)
    # 1) relaunching the app's own task brings a mini player back to full screen
    tv shell monkey -p "$pkg" -c android.intent.category.LEANBACK_LAUNCHER 1 >/dev/null 2>&1 \
      || tv shell monkey -p "$pkg" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
    sleep 1.5
    # 2) still pinned? move that task to full screen directly
    if tv shell dumpsys activity activities | tr -d '\r' | grep -q "windowingMode=pinned\|mWindowingMode=pinned"; then
      [ "$task" != "?" ] && tv shell am task set-windowing-mode "$task" 1 >/dev/null 2>&1
      tv shell input keyevent KEYCODE_WINDOW  # the TV's own mini player menu (has Full screen)
    fi
    echo "Back to full screen. Press Back in the app to leave it normally." ;;
  close)
    tv shell am force-stop "$pkg" && echo "Closed $pkg." ;;
  *) echo "Run: scripts/pip.sh full   or   scripts/pip.sh close" ;;
esac
