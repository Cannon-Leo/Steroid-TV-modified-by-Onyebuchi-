package tv.steroid.home;

import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.service.dreams.DreamService;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.Calendar;

/**
 * Screensaver "Steroid photos": the home screen's slideshow (your photos woven through the free
 * photos and views) at full brightness and colour, with a small clock in the bottom corner that
 * shifts a little every minute so it never marks the screen. Fully offline, unlike Google's Backdrop.
 * Choose it in Settings, Device Preferences, Screen saver, or from the Mac:
 *   settings put secure screensaver_components tv.steroid.home/.PhotoDream
 */
public class PhotoDream extends DreamService {
    Ambient a;
    TextView time, date;
    LinearLayout clock;
    final Handler h = new Handler(Looper.getMainLooper());

    @Override public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setFullscreen(true);
        setInteractive(false);
        Ui.init(this);
        FrameLayout root = new FrameLayout(this);
        a = new Ambient(this);
        a.dream = true;
        root.addView(a, new FrameLayout.LayoutParams(-1, -1));
        clock = new LinearLayout(this);
        clock.setOrientation(LinearLayout.VERTICAL);
        clock.setGravity(Gravity.END);
        time = Ui.text(this, Ui.sans, 44, 0xF2FFFFFF);
        time.setTypeface(Ui.sans, Typeface.NORMAL);
        time.setShadowLayer(Ui.px(12), 0, Ui.px(2), 0x99000000);
        clock.addView(time);
        date = Ui.text(this, Ui.sansMedium, 15, 0xD9FFFFFF);
        date.setShadowLayer(Ui.px(8), 0, Ui.px(1), 0x99000000);
        clock.addView(date);
        root.addView(clock, new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.END));
        setContentView(root);
    }

    final Runnable tick = new Runnable() { public void run() {
        Calendar c = Calendar.getInstance();
        time.setText(DateFormat.format(DateFormat.is24HourFormat(PhotoDream.this) ? "HH:mm" : "h:mm", c));
        date.setText(DateFormat.format("EEEE, d MMMM", c));
        // drift a few pixels each minute (no burn in on the screen)
        int m = c.get(Calendar.MINUTE);
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) clock.getLayoutParams();
        lp.setMargins(0, 0, Ui.px(48 + (m % 5) * 6), Ui.px(40 + (m % 3) * 6));
        clock.setLayoutParams(lp);
        h.postDelayed(this, 60000 - c.get(Calendar.SECOND) * 1000);
    }};

    @Override public void onDreamingStarted() { super.onDreamingStarted(); a.reload(); a.run(true); h.post(tick); }
    @Override public void onDreamingStopped() { h.removeCallbacks(tick); a.run(false); super.onDreamingStopped(); }
}
