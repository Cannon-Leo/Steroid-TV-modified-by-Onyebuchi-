#!/bin/bash
# Records a ~30 second tour of Steroid TV for posts: home rails, control centre, Bluetooth,
# notifications, The World's Windows, Radio and the photo screensaver.
# Usage: scripts/demo-video.sh [720]  -> personal/demo/steroid-demo.mp4 (16:9) and steroid-demo-square.mp4
# It only opens Steroid's own screens and moves focus; it never presses OK on another app.
# The result goes in personal/ (never uploaded to GitHub): it shows your home screen and photos.
. "$(dirname "$0")/env.sh"
set +e   # one screen failing to open must never stop the recording halfway
cd "$ROOT"
scripts/connect.sh >/dev/null || { echo "Can't reach the TV: scripts/buchi.sh connect"; exit 1; }
OUT="$ROOT/personal/demo"; mkdir -p "$OUT"
H="tv.steroid.home"
k() { tv shell input keyevent "$@" >/dev/null; }
panel() { tv shell am start -n "$H/.Home" --es open "$1" >/dev/null 2>&1; }
act() { tv shell am start -n "$H/.$1" >/dev/null 2>&1; }

echo "Getting ready..."
k KEYCODE_HOME; sleep 2
tv shell rm -f /sdcard/steroid-demo.mp4
echo "Recording the tour (about a minute). Hands off the remote."
SIZE=""; [ "$1" = 720 ] && SIZE="--size 1280x720"
tv shell screenrecord --time-limit 90 $SIZE --bit-rate 12000000 /sdcard/steroid-demo.mp4 &
REC=$!
sleep 2.5                                   # home, top
for i in 1 2 3; do k KEYCODE_DPAD_DOWN; sleep 1.3; done   # rails scroll by
k KEYCODE_HOME; sleep 1
panel quick; sleep 3.2                       # control centre
k KEYCODE_BACK; sleep 0.6
panel bluetooth; sleep 3                     # Bluetooth panel
k KEYCODE_BACK; sleep 0.6
panel notices; sleep 2.6                     # notification centre
k KEYCODE_BACK; sleep 0.6
act Windows; sleep 2.6                      # The World's Windows
k KEYCODE_DPAD_DOWN; sleep 0.9; k KEYCODE_DPAD_RIGHT; sleep 0.9; k KEYCODE_DPAD_RIGHT; sleep 1
k KEYCODE_BACK; sleep 0.6
act Radio; sleep 3                          # Radio
k KEYCODE_HOME; sleep 0.8
# photo screensaver: Android's own "start screensaver" screen, or ask the dream service directly
tv shell am start -n com.android.systemui/.Somnambulator >/dev/null 2>&1 || tv shell service call dreams 1 >/dev/null 2>&1
sleep 4
tv shell pkill -INT screenrecord >/dev/null 2>&1   # tour done: stop recording (adb is slower than the plan, so it runs long)
wait $REC
k KEYCODE_HOME
echo "Saving the recording..."
tv pull /sdcard/steroid-demo.mp4 "$OUT/raw.mp4"
if [ ! -s "$OUT/raw.mp4" ]; then
  echo "The TV didn't make a recording. Trying a lighter one (720p)..."
  k KEYCODE_HOME; sleep 1
  tv shell screenrecord --time-limit 5 --size 1280x720 --bit-rate 6000000 /sdcard/steroid-demo.mp4
  tv pull /sdcard/steroid-demo.mp4 "$OUT/raw.mp4"
  [ -s "$OUT/raw.mp4" ] && echo "720p works on this TV: run  scripts/buchi.sh demo 720  to record the tour at 720p." \
    || echo "This TV won't record its screen right now. Restart the TV and try again."
  exit 1
fi
tv shell rm -f /sdcard/steroid-demo.mp4
command -v ffmpeg >/dev/null || { echo "Saved $OUT/raw.mp4 (install ffmpeg for the polished versions)"; [ "$(uname)" = Darwin ] && /usr/bin/open "$OUT"; exit 0; }
echo "Polishing..."
# squeezed to 30 seconds whatever the recording's length, smooth 30 fps, gentle fades, plays everywhere
DUR=$(ffprobe -v error -show_entries format=duration -of csv=p=0 "$OUT/raw.mp4")
SP=$(awk -v d="$DUR" 'BEGIN{s=(d-0.3)/30; if(s<1)s=1; printf "%.3f", s}')
F="setpts=(PTS-STARTPTS)/$SP,fps=30,fade=t=in:st=0:d=0.4,fade=t=out:st=29.4:d=0.6"
ffmpeg -y -loglevel error -ss 0.3 -i "$OUT/raw.mp4" -t 30 -vf "scale=1920:1080:flags=lanczos,$F" \
  -c:v libx264 -preset veryfast -crf 19 -pix_fmt yuv420p -movflags +faststart -an "$OUT/steroid-demo.mp4"
# square for feeds: the TV picture centred on a soft blurred copy of itself
ffmpeg -y -loglevel error -ss 0.3 -i "$OUT/raw.mp4" -t 30 -filter_complex \
  "[0:v]split[x][y];[x]scale=1080:1080:force_original_aspect_ratio=increase,crop=1080:1080,boxblur=30:3,eq=brightness=-0.08[bg];[y]scale=1080:-2:flags=lanczos[fg];[bg][fg]overlay=(W-w)/2:(H-h)/2,$F" \
  -c:v libx264 -preset veryfast -crf 20 -pix_fmt yuv420p -movflags +faststart -an "$OUT/steroid-demo-square.mp4"
[ -s "$OUT/steroid-demo.mp4" ] || { echo "Polishing failed; the plain recording is $OUT/raw.mp4"; /usr/bin/open "$OUT" 2>/dev/null; exit 1; }
echo "DONE"
echo "  $OUT/steroid-demo.mp4         16:9, for X, LinkedIn, YouTube, Medium"
echo "  $OUT/steroid-demo-square.mp4  1:1, for feeds and Instagram"
[ "$(uname)" = Darwin ] && /usr/bin/open "$OUT"   # show the folder in Finder
