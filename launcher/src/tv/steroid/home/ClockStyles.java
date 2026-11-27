package tv.steroid.home;

import android.app.Activity;
import android.graphics.Canvas;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Pick the screensaver clock, seeing the real thing: the screensaver's own photos, depth effect and
 * clock (ClockFace), full screen. Left and right change the style, OK uses it, Back leaves it as it was.
 * Opened from Launcher settings, Screensaver clock.
 */
public class ClockStyles extends Activity {
    Ambient a;
    ClockFace face;
    TextView name, hint;
    LinearLayout bar;
    final android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.init(this);
        Weather.restore(this);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        face = new ClockFace(this);
        a = new Ambient(this);
        a.dream = true;
        a.overlay = new Ambient.Overlay() {
            public void draw(Canvas c, int w, int hh) { face.draw(c, w, hh, false, a.lumNow, 0, 0); }
            public void drawFront(Canvas c, int w, int hh) { face.draw(c, w, hh, true, a.lumNow, 0, 0); }
        };
        FrameLayout root = new FrameLayout(this);
        root.addView(a, new FrameLayout.LayoutParams(-1, -1));

        // the style's name, bottom centre, in glass
        bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.VERTICAL);
        bar.setGravity(Gravity.CENTER_HORIZONTAL);
        bar.setPadding(Ui.px(26), Ui.px(12), Ui.px(26), Ui.px(12));
        Glass.apply(bar, 22, 0x8C101014);
        name = Ui.text(this, Ui.sansMedium, 17, 0xFFFFFFFF);
        name.setGravity(Gravity.CENTER);
        bar.addView(name);
        hint = Ui.text(this, Ui.sans, 12, 0xB3FFFFFF);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, Ui.px(4), 0, 0);
        bar.addView(hint);
        FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        bl.bottomMargin = Ui.px(26);
        root.addView(bar, bl);
        setContentView(root);
        label();
        new Thread(() -> { Weather.refresh(this); h.post(a::invalidate); }).start();
    }

    void label() {
        name.setText(ClockFace.NAMES[face.style] + "   " + (face.style + 1) + " of " + ClockFace.NAMES.length);
        hint.setText(ClockFace.HINTS[face.style] + "\n◀ ▶ change style   ·   OK use this one   ·   Back keep the old one");
        bar.setAlpha(1f);
        bar.animate().cancel();
        bar.animate().alpha(0.35f).setStartDelay(3500).setDuration(600).start();
        a.invalidate();
    }

    @Override protected void onResume() { super.onResume(); a.reload(); a.run(true); }
    @Override protected void onPause() { a.run(false); super.onPause(); }

    @Override public boolean onKeyDown(int k, KeyEvent e) {
        int n = ClockFace.NAMES.length;
        switch (k) {
            case KeyEvent.KEYCODE_DPAD_RIGHT: face.style = (face.style + 1) % n; label(); return true;
            case KeyEvent.KEYCODE_DPAD_LEFT: face.style = (face.style + n - 1) % n; label(); return true;
            case KeyEvent.KEYCODE_DPAD_UP: case KeyEvent.KEYCODE_DPAD_DOWN: label(); return true;
            case KeyEvent.KEYCODE_DPAD_CENTER: case KeyEvent.KEYCODE_ENTER:
                Prefs.put(this, "ss_style", face.style);
                android.widget.Toast.makeText(this, ClockFace.NAMES[face.style] + " clock is on", android.widget.Toast.LENGTH_SHORT).show();
                finish();
                return true;
        }
        return super.onKeyDown(k, e);
    }
}
