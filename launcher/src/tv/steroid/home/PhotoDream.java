package tv.steroid.home;

import android.graphics.Canvas;
import android.os.Handler;
import android.os.Looper;
import android.service.dreams.DreamService;
import java.util.Calendar;

/**
 * Screensaver "Steroid photos", in the style of a HyperOS lock screen: your photos woven through the
 * free photos and views at full colour, the date and weather (animated icon) at the top, a large clock
 * with AM/PM and running seconds, and the depth effect on photos with a clear subject.
 * Contrast follows each photo: the brighter the picture behind the clock, the deeper the shade and the
 * stronger the text's shadow, so the time stays readable on skies, snow and white walls.
 * The clock has seven styles (Launcher settings, Screensaver clock), drawn by ClockFace.
 * Choose it in Settings, Device Preferences, Screen saver, or: scripts/buchi.sh screensaver
 */
public class PhotoDream extends DreamService {
    Ambient a;
    ClockFace face;
    final Handler h = new Handler(Looper.getMainLooper());
    int shiftX, shiftY;

    @Override public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setFullscreen(true);
        setInteractive(false);
        Ui.init(this);
        Weather.restore(this);
        face = new ClockFace(this);
        a = new Ambient(this);
        a.dream = true;
        a.overlay = new Ambient.Overlay() {
            public void draw(Canvas c, int w, int hh) { face.draw(c, w, hh, false, a.lumNow, shiftX, shiftY); }      // behind the subject
            public void drawFront(Canvas c, int w, int hh) { face.draw(c, w, hh, true, a.lumNow, shiftX, shiftY); }  // in front of it
        };
        setContentView(a);
    }

    final Runnable tick = new Runnable() { public void run() {
        Calendar k = Calendar.getInstance();
        // against burn in: a few pixels, once an hour (moving every minute looked like the clock jumping)
        int hr = k.get(Calendar.HOUR_OF_DAY);
        shiftX = Ui.px((hr % 5 - 2) * 3);
        shiftY = Ui.px((hr % 3 - 1) * 3);
        new Thread(() -> { Weather.refresh(PhotoDream.this); h.post(a::invalidate); }).start();
        h.postDelayed(this, 60000 - k.get(Calendar.SECOND) * 1000);
    }};

    @Override public void onDreamingStarted() { super.onDreamingStarted(); face.load(); a.reload(); a.run(true); h.post(tick); }
    @Override public void onDreamingStopped() { h.removeCallbacks(tick); a.run(false); super.onDreamingStopped(); }
}
