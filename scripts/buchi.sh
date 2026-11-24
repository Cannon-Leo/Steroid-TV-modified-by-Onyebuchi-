#!/bin/bash
# Buchi's setup, one step at a time. Run from the repo folder:  scripts/buchi.sh <step>
#   all      EVERYTHING in one go: depth tool, build, install, Little Office, your photos, screensaver
#   check    tools ready?                     build    build the launcher APK
#   plan     show what cleanup WOULD change (changes nothing)
#   install  install launcher, grant permissions, remove Projectivy, make it Home
#   clean    debloat (writes backup/restore.sh first)
#   tune     faster animations, ad id off, offline photo screensaver
#   photos <folder>   push your photos (and profile.jpg) to the stick
#   myphotos          the same, from the private folder named in personal/photos-folder
#   screensaver       use the Steroid photos screensaver (your photos, full colour, clock)
#   office [approve]  check Little Office end to end and send a test notice (and approval) to the TV
#   update   build + install + Little Office + diagnostics, in one go
#   pip [full|close]   mini player stuck on screen? bring it back to full screen or close it
#   undo     put everything back
. "$(dirname "$0")/env.sh"
cd "$ROOT"
# apps that go: other launchers and the old shortcut wrappers (All apps replaces them)
REMOVE="com.spocky.projengmenu news.androidtv.tvapprepo de.eye_interactive.atvl.a9aef56bdbfcd373a3602684b254c8aaa de.eye_interactive.atvl.chrome"
keepfile() {  # keep every app you can open, except the ones above and anything on the cleanup lists
  mkdir -p backup
  drop="$REMOVE $("$PY" -c "import json;d=json.load(open('config/debloat.json'));print(' '.join(d['launchers']+[p for g in d['ads_tracking'].values() for p in g]))")"
  scripts/inventory.sh | cut -d'|' -f1 | while read p; do
    case " $drop " in *" $p "*) ;; *) echo "$p";; esac
  done > backup/keep.txt
}
case "$1" in
  check) scripts/check-env.sh ;;
  build) scripts/build.sh ;;
  plan)
    keepfile
    echo "== Apps kept (all apps you open) =="; cat backup/keep.txt
    echo; echo "== Removed for your user =="; for p in $REMOVE; do tv shell pm list packages | tr -d '\r' | grep -qx "package:$p" && echo "$p"; done
    echo; echo "== Ad / tracking / Xiaomi services switched off (if present) =="
    installed=$(tv shell pm list packages | sed 's/package://' | tr -d '\r')
    "$PY" -c "import json;d=json.load(open('config/debloat.json'));print('\n'.join(p for g in d['ads_tracking'].values() for p in g))" \
      | while read p; do echo "$installed" | grep -qx "$p" && echo "$p"; done
    echo; echo "== Stock launchers disabled after Steroid is Home =="
    "$PY" -c "import json;print('\n'.join(json.load(open('config/debloat.json'))['launchers']))" \
      | while read p; do echo "$installed" | grep -qx "$p" && echo "$p"; done
    echo; echo "Nothing was changed." ;;
  install)
    mkdir -p backup; R=backup/restore.sh
    [ -f "$R" ] || printf '#!/bin/bash\n# Undo. Run from the repo root.\n. "$(dirname "$0")/../scripts/env.sh"\n' > "$R"
    for p in $REMOVE; do
      tv shell pm list packages | tr -d '\r' | grep -qx "package:$p" || continue
      echo "removing $p: $(tv shell pm uninstall -k --user 0 $p | tr -d '\r')"
      echo "tv shell cmd package install-existing $p" >> "$R"
    done
    chmod +x "$R"
    scripts/install.sh || exit 1
    tv shell pm grant tv.steroid.home com.android.providers.tv.permission.READ_EPG_DATA 2>/dev/null && echo "Up next: allowed" || echo "Up next: permission refused (row stays hidden)"
    tv shell cmd notification allow_listener tv.steroid.home/tv.steroid.home.NotifListener && echo "Music widget: allowed"
    tv shell appops set tv.steroid.home GET_USAGE_STATS allow && echo "Recent apps: allowed"
    tv shell am force-stop tv.steroid.home; tv shell input keyevent KEYCODE_HOME
    echo "DONE: Steroid is Home" ;;
  clean) keepfile; scripts/debloat.sh backup/keep.txt ;;
  tune)
    R=backup/restore.sh
    old=$(tv shell settings get secure screensaver_components | tr -d '\r')
    tv shell settings put secure screensaver_components tv.steroid.home/.PhotoDream
    echo "tv shell settings put secure screensaver_components '$old'" >> "$R"
    tv shell settings put secure limit_ad_tracking 1
    echo "Screensaver: your photos (offline). Ad id: off." ;;
  photos)
    [ -d "$2" ] || { echo "usage: scripts/buchi.sh photos <folder>"; exit 1; }
    D=/sdcard/Android/data/tv.steroid.home/files/photos
    tv shell mkdir -p $D
    for f in "$2"/*; do
      case "$(echo "$f" | tr "A-Z" "a-z")" in *.jpg|*.jpeg|*.png|*.webp) tv push "$f" "$D/" >/dev/null && echo "sent $(basename "$f")";; esac
    done
    # restart the launcher so the avatar, name and slideshow pick the new files up at once
    tv shell am force-stop tv.steroid.home; sleep 1; tv shell input keyevent KEYCODE_HOME
    echo "Photos are in the slideshow (a profile.jpg or .png is your avatar)." ;;
  myphotos)  # your own photos and profile picture, from the folder named in personal/photos-folder (never committed)
    F="$(cat personal/photos-folder 2>/dev/null)"
    [ -d "$F" ] || { echo "Put your photo folder's path in personal/photos-folder first."; exit 1; }
    # a private copy Little Office can serve to paired TVs (macOS keeps background services out of Downloads);
    # photos named in personal/skip-photos (ones with words on them, say) are left out
    L="$HOME/.little-office/photos"; mkdir -p "$L"
    SKIP="$(cat personal/skip-photos 2>/dev/null)"
    # bring the copy up to date without wiping it: new or changed photos come in, removed ones go
    # (with their depth masks); unchanged photos keep their masks
    n=0; seen=" "
    for f in "$F"/*; do
      b="$(basename "$f")"
      case "$(echo "$b" | tr "A-Z" "a-z")" in *.jpg|*.jpeg|*.png|*.webp) ;; *) continue;; esac
      echo "$SKIP" | grep -qxF "$b" && { echo "skipped $b"; continue; }
      [ -f "$L/$b" ] && [ ! "$f" -nt "$L/$b" ] || cp -p "$f" "$L/" || continue
      n=$((n+1)); seen="$seen$b|"
    done
    if [ "$n" = 0 ]; then
      echo "Couldn't read your photo folder. If it isn't empty, macOS is blocking Terminal from it:"
      echo "System Settings > Privacy & Security > Files and Folders > Terminal > turn on Downloads Folder."
      echo "Keeping the photos already prepared for the TV ($(ls "$L" | grep -vic '\.mask\.') files)."
    else
      for g in "$L"/*; do
        b="$(basename "$g")"; base="${b%.mask.png}"
        case "$seen" in *"|$base|"*|*" $base|"*) ;; *) rm -f "$g" && echo "removed $b";; esac
      done
    fi
    # depth effect masks for the screensaver (needs rembg once: pip3 install "rembg[cpu]")
    if "$PY" -c "import rembg" 2>/dev/null; then "$PY" little-office/depth.py "$L"
    else echo "Depth effect: skipped (run once: pip3 install \"rembg[cpu]\", then myphotos again)"; fi
    "$PY" - "$L" "$(cat personal/name 2>/dev/null)" <<'P'
import json, os, sys
p = os.path.expanduser("~/.little-office/config.json")
try: c = json.load(open(p))
except Exception: c = {"pinned": [], "names": {}}
c["photos_folder"], c["name"] = sys.argv[1], sys.argv[2].strip()
json.dump(c, open(p, "w"), indent=2)
P
    echo "$n photos ready for the TV (it fetches them from Little Office within a minute, and every 10 minutes)"
    # also copy them straight over adb, for a TV that isn't paired yet
    [ -f personal/name ] && tv push personal/name /sdcard/Android/data/tv.steroid.home/files/photos/name.txt >/dev/null 2>&1
    tv shell rm -f "/sdcard/Android/data/tv.steroid.home/files/photos/*.jpg" "/sdcard/Android/data/tv.steroid.home/files/photos/*.png" >/dev/null 2>&1
    "$0" photos "$L" ;;
  office) shift; scripts/office-check.sh "$@" ;;  # Little Office end to end: buchi.sh office [approve]
  screensaver)  # use the Steroid photos screensaver (your photos, full colour, small clock) instead of Backdrop
    tv shell settings put secure screensaver_components tv.steroid.home/.PhotoDream && echo "Screensaver: Steroid photos" ;;  # Little Office end to end: buchi.sh office [approve]
  all)  # everything in one go: depth tool, build and install, Little Office, your photos, screensaver
    echo "== 1/5  Cut out tool for the depth effect"
    "$PY" -c "import rembg" 2>/dev/null || "$PY" -m pip install --user -q "rembg[cpu]" 2>/dev/null \
      || "$PY" -m pip install --user --break-system-packages -q "rembg[cpu]" || echo "   rembg didn't install: photos will show without depth"
    echo "== 2/5  Build, install on the TV, Little Office, diagnostics"
    "$0" update || { echo "Stopped: the build or install failed (see above)."; exit 1; }
    echo "== 3/5  Your photos, avatar, name and depth masks"
    "$0" myphotos
    echo "== 4/5  Screensaver"
    "$0" screensaver
    echo "== 5/5  Tidy up"
    rm -f "$HOME/.little-office/photos/masks.tgz"
    echo "All done. Press Home on the TV; your photos arrive within a minute." ;;
  update)  # the whole fix in one go: build, install on the TV, refresh Little Office, then check the TV
    scripts/build.sh && "$0" install && little-office/install.sh && scripts/diag.sh ;;
  pip) shift; scripts/pip.sh "$@" ;;   # mini player stuck? buchi.sh pip full / pip close
  undo) backup/restore.sh ;;
  *) sed -n 2,10p "$0" ;;
esac
