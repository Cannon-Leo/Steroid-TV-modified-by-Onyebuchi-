#!/bin/bash
# Buchi's setup, one step at a time. Run from the repo folder:  scripts/buchi.sh <step>
#   check    tools ready?                     build    build the launcher APK
#   plan     show what cleanup WOULD change (changes nothing)
#   install  install launcher, grant permissions, remove Projectivy, make it Home
#   clean    debloat (writes backup/restore.sh first)
#   tune     faster animations, ad id off, offline photo screensaver
#   photos <folder>   push your photos (and profile.jpg) to the stick
#   myphotos          the same, from the private folder named in personal/photos-folder
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
    # your name for the greeting (until you set one on the TV), also private: personal/name
    [ -f personal/name ] && tv push personal/name /sdcard/Android/data/tv.steroid.home/files/photos/name.txt >/dev/null && echo "sent your name"
    "$0" photos "$F" ;;
  office) shift; scripts/office-check.sh "$@" ;;  # Little Office end to end: buchi.sh office [approve]
  update)  # the whole fix in one go: build, install on the TV, refresh Little Office, then check the TV
    scripts/build.sh && "$0" install && little-office/install.sh && scripts/diag.sh ;;
  pip) shift; scripts/pip.sh "$@" ;;   # mini player stuck? buchi.sh pip full / pip close
  undo) backup/restore.sh ;;
  *) sed -n 2,10p "$0" ;;
esac
