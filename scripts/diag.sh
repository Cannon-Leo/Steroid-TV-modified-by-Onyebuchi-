#!/bin/bash
# Captures TV screenshots and facts for Claude. Changes nothing on the TV except pressing keys to move focus.
. "$(dirname "$0")/env.sh"
D="$ROOT/diag"; mkdir -p "$D"
shot() { tv exec-out screencap -p > "$D/$1.png"; echo "shot $1"; }
key() { tv shell input keyevent "$@"; sleep 1.2; }
key KEYCODE_HOME; sleep 2; shot 1-home-top
key KEYCODE_DPAD_UP; shot 2-top-icons
key KEYCODE_DPAD_UP; shot 3-top-icons-b
key KEYCODE_HOME; key KEYCODE_DPAD_DOWN; shot 4-down1
key KEYCODE_DPAD_DOWN; shot 5-down2
key KEYCODE_DPAD_DOWN; shot 6-down3
key KEYCODE_DPAD_DOWN; shot 7-down4
key KEYCODE_DPAD_DOWN KEYCODE_DPAD_DOWN; shot 8-down6
key KEYCODE_DPAD_DOWN KEYCODE_DPAD_DOWN; shot 9-down8
key KEYCODE_DPAD_LEFT KEYCODE_DPAD_LEFT KEYCODE_DPAD_LEFT KEYCODE_DPAD_LEFT KEYCODE_DPAD_LEFT; shot 10-left-edge
key KEYCODE_DPAD_RIGHT KEYCODE_DPAD_RIGHT KEYCODE_DPAD_RIGHT KEYCODE_DPAD_RIGHT KEYCODE_DPAD_RIGHT KEYCODE_DPAD_RIGHT; shot 11-right-edge
key KEYCODE_HOME
{
  echo "== display"; tv shell wm size; tv shell wm density
  echo "== steroid permissions"; tv shell dumpsys package tv.steroid.home | grep -iE "EPG|USAGE|granted=" | head -20
  echo "== appops"; tv shell appops get tv.steroid.home 2>&1 | head
  echo "== who publishes video rows (watch next)"; tv shell content query --uri content://android.media.tv/watch_next_program --projection package_name:title 2>&1 | head -15
  echo "== preview programs"; tv shell content query --uri content://android.media.tv/preview_program --projection package_name:title 2>&1 | head -15
  echo "== channels"; tv shell content query --uri content://android.media.tv/channel --projection package_name:display_name:browsable 2>&1 | head -20
  echo "== steroid errors"; tv logcat -d -t 400 | grep -iE "SteroidShelf|FATAL|AndroidRuntime: (FATAL|Caused)" | tail -20
} > "$D/facts.txt" 2>&1
echo "DONE — diagnostics saved"
