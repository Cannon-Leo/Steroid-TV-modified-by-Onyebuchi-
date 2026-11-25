#!/bin/bash
# Records a ~30 second tour of Steroid TV for posts: home rails, control centre, Bluetooth,
# notifications, The World's Windows, Radio and the photo screensaver.
# Usage: scripts/demo-video.sh        -> personal/demo/steroid-demo.mp4 (16:9) and steroid-demo-square.mp4
# It only opens Steroid's own screens and moves focus; it never presses OK on another app.
# The result goes in personal/ (never uploaded to GitHub): it shows your home screen and photos.
. "$(dirname "$0")/env.sh"
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
echo "Recording 32 seconds. Hands off the remote."
tv shell screenrecord --time-limit 32 --bit-rate 16000000 /sdcard/steroid-demo.mp4 &
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
tv shell am start -n com.android.systemui/.Somnambulator >/dev/null 2>&1   # photo screensaver
wait $REC
k KEYCODE_HOME
tv pull /sdcard/steroid-demo.mp4 "$OUT/raw.mp4" >/dev/null && tv shell rm -f /sdcard/steroid-demo.mp4
command -v ffmpeg >/dev/null || { echo "Saved $OUT/raw.mp4 (install ffmpeg for the polished versions)"; exit 0; }
echo "Polishing..."
# 30 seconds, smooth 30 fps, gentle fade in and out, plays everywhere (H.264, faststart)
F="fps=30,fade=t=in:st=0:d=0.4,fade=t=out:st=29.4:d=0.6"
ffmpeg -y -loglevel error -ss 0.3 -t 30 -i "$OUT/raw.mp4" -vf "scale=1920:1080:flags=lanczos,$F" \
  -c:v libx264 -preset veryfast -crf 19 -pix_fmt yuv420p -movflags +faststart -an "$OUT/steroid-demo.mp4"
# square for feeds: the TV picture centred on a soft blurred copy of itself
ffmpeg -y -loglevel error -ss 0.3 -t 30 -i "$OUT/raw.mp4" -filter_complex \
  "[0:v]scale=1080:1080:force_original_aspect_ratio=increase,crop=1080:1080,boxblur=30:3,eq=brightness=-0.08[bg];[0:v]scale=1080:-2:flags=lanczos[fg];[bg][fg]overlay=(W-w)/2:(H-h)/2,$F" \
  -c:v libx264 -preset veryfast -crf 20 -pix_fmt yuv420p -movflags +faststart -an "$OUT/steroid-demo-square.mp4"
echo "DONE"
echo "  $OUT/steroid-demo.mp4         16:9, for X, LinkedIn, YouTube, Medium"
echo "  $OUT/steroid-demo-square.mp4  1:1, for feeds and Instagram"
open_cmd=$(command -v open || true); [ -n "$open_cmd" ] && "$open_cmd" "$OUT"
