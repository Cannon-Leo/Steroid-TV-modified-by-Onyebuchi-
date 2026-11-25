#!/bin/bash
# Installs the launcher, makes it the Home app, grants what its features need, then switches off
# the stock launchers (only after Steroid Home is confirmed as Home, so the TV is never left without one).
. "$(dirname "$0")/env.sh"
APK="$ROOT/launcher/out/SteroidHome.apk"
# Never install an old build: if any source, resource or config changed since the last build, build first.
newer=$([ -f "$APK" ] && find "$ROOT/launcher/src" "$ROOT/launcher/res" "$ROOT/launcher/AndroidManifest.xml" "$ROOT/config" -newer "$APK" -type f -print -quit 2>/dev/null || true)
if [ ! -f "$APK" ] || [ -n "$newer" ]; then
  echo "Building the latest version first${newer:+ (changed: ${newer#$ROOT/})}..."
  "$ROOT/scripts/build.sh" || { echo "BUILD FAILED: nothing was installed (see the error above)"; exit 1; }
fi
echo "Installing the build from $(date -r "$APK" '+%d %b %H:%M' 2>/dev/null || stat -f '%Sm' "$APK" 2>/dev/null || echo now)"
out=$(tv install -r --no-incremental "$APK" 2>&1) || out=$(tv install -r "$APK" 2>&1)
echo "$out" | tail -1
echo "$out" | grep -q Success || { echo "INSTALL FAILED: $out" | tail -3; exit 1; }
# check the TV really has this build (the Home button service only exists in new builds)
if tv shell dumpsys package tv.steroid.home | tr -d '\r' | grep -q "tv.steroid.home/.HomeKeys"; then echo "On the TV: the newest build"
else echo "WARNING: the TV still has an older build. Run: scripts/buchi.sh update"; fi
tv shell cmd package set-home-activity tv.steroid.home/.Home --user 0
resolve() { tv shell cmd package resolve-activity --brief -c android.intent.category.HOME -a android.intent.action.MAIN | tail -1 | tr -d '\r'; }
home=$(resolve)
LAUNCHERS=$("$PY" -c "import json;print(' '.join(json.load(open('$ROOT/config/debloat.json'))['launchers']))")
R="$ROOT/backup/restore.sh"; mkdir -p "$ROOT/backup"
[ -f "$R" ] || printf '#!/bin/bash\n# Undo Steroid TV changes. Run from the repo root.\n. "$(dirname "$0")/../scripts/env.sh"\n' > "$R"
if [ "$home" != "tv.steroid.home/.Home" ]; then
  # Android TV keeps its own launcher first. Check Steroid really opens, then switch the stock ones off.
  tv shell am start -W -n tv.steroid.home/.Home >/dev/null; sleep 4
  tv shell pidof tv.steroid.home >/dev/null || { echo "Steroid did not start — stock launcher left alone"; exit 1; }
  for p in $LAUNCHERS; do
    tv shell pm list packages | tr -d '\r' | grep -qx "package:$p" || continue
    tv shell pm disable-user --user 0 "$p" | tail -1; echo "tv shell pm enable $p" >> "$R"
  done
  home=$(resolve)
  if [ "$home" != "tv.steroid.home/.Home" ]; then
    for p in $LAUNCHERS; do tv shell pm enable "$p" >/dev/null 2>&1 || true; done
    echo "Home app still not switched (got $home) — stock launchers switched back on"; exit 1
  fi
fi
# Radio visualizer reads the radio's own audio output; Android gates that behind RECORD_AUDIO.
tv shell pm grant tv.steroid.home android.permission.RECORD_AUDIO || true
# Little Office's 'needs you' notice is a small overlay over whatever is playing.
tv shell appops set tv.steroid.home SYSTEM_ALERT_WINDOW allow || true
R="$ROOT/backup/restore.sh"; mkdir -p "$ROOT/backup"
[ -f "$R" ] || printf '#!/bin/bash\n# Undo Steroid TV changes. Run from the repo root.\n. "$(dirname "$0")/../scripts/env.sh"\n' > "$R"
for p in $("$PY" -c "import json;print(' '.join(json.load(open('$ROOT/config/debloat.json'))['launchers']))"); do
  tv shell pm list packages | tr -d '\r' | grep -qx "package:$p" || continue
  tv shell pm disable-user --user 0 "$p" | tail -1; echo "tv shell pm enable $p" >> "$R"
done
grep -q set-home-activity "$R" || echo 'tv shell cmd package set-home-activity com.google.android.tvlauncher/.MainActivity --user 0' >> "$R"
chmod +x "$R"
tv shell input keyevent KEYCODE_HOME
echo "INSTALLED — Steroid Home is the Home app"
