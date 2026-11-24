package tv.steroid.home;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.service.dreams.DreamService;
import android.text.format.DateFormat;
import java.util.Calendar;

/**
 * Screensaver "Steroid photos", in the style of a HyperOS lock screen: your photos woven through the
 * free photos and views at full colour, a large clock with the date and weather at the top, and the
 * depth effect: on photos with a clear subject (masks made on the computer, little-office/depth.py)
 * the subject stands in front of the clock. The clock shifts a few pixels each minute (no burn in).
 * Choose it in Settings, Device Preferences, Screen saver, or: scripts/buchi.sh screensaver
 */
public class PhotoDream extends DreamService {
    Ambient a;
    final Handler h = new Handler(Looper.getMainLooper());
    final Paint big = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG), small = new Paint(Paint.ANTI_ALIAS_FLAG);
    int shiftX, shiftY;

    @Override public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setFullscreen(true);
        setInteractive(false);
        Ui.init(this);
        Weather.restore(this);
        Typeface bold = new Typeface.Builder(getAssets(), "fonts/sans.ttf").setFontVariationSettings("'wght' 700").build();
        big.setTypeface(bold);
        big.setColor(0xF5FFFFFF);
        big.setTextAlign(Paint.Align.CENTER);
        big.setLetterSpacing(-0.03f);
        big.setShadowLayer(Ui.px(18), 0, Ui.px(3), 0x59000000);
        small.setTypeface(Ui.sansMedium);
        small.setColor(0xF2FFFFFF);
        small.setShadowLayer(Ui.px(8), 0, Ui.px(1), 0x80000000);
        a = new Ambient(this);
        a.dream = true;
        a.overlay = new Ambient.Overlay() {
            public void draw(Canvas c, int w, int h) { drawClock(c, w, h, false); }       // behind the subject
            public void drawFront(Canvas c, int w, int h) { drawClock(c, w, h, true); }   // in front of it
        };
        setContentView(a);
    }

    /** HyperOS lock screen: date and weather on one line, the big clock under it, all centred at the top. */
    void drawClock(Canvas c, int w, int hgt, boolean front) {
        Calendar k = Calendar.getInstance();
        String time = DateFormat.format(DateFormat.is24HourFormat(this) ? "HH:mm" : "h:mm", k).toString();
        float cx = w / 2f + shiftX, top = hgt * 0.085f + shiftY;
        // date · weather
        small.setTextSize(Ui.px(17));
        String date = DateFormat.format("EEEE, d MMMM", k).toString();
        String wtext = Weather.known() ? Weather.temp + "°" + (Weather.city.isEmpty() ? "" : "  " + Weather.city) : "";
        float iconSize = Ui.px(22), gap = Ui.px(10), sep = Ui.px(18);
        float dw = small.measureText(date), ww = wtext.isEmpty() ? 0 : sep + iconSize + gap + small.measureText(wtext);
        float x = cx - (dw + ww) / 2, base = top + Ui.px(20);
        small.setTextAlign(Paint.Align.LEFT);
        if (front) c.drawText(date, x, base, small);
        if (front && !wtext.isEmpty()) {
            float ix = x + dw + sep;
            Drawable d = getDrawable(Icon.RES[Weather.icon()]);
            if (d != null) {
                d.setTint(0xF2FFFFFF);
                d.setBounds((int) ix, (int) (base - iconSize + Ui.px(3)), (int) (ix + iconSize), (int) (base + Ui.px(3)));
                d.draw(c);
            }
            c.drawText(wtext, ix + iconSize + gap, base, small);
        }
        // the big clock
        big.setTextSize(hgt * 0.30f);
        Paint.FontMetrics fm = big.getFontMetrics();
        float clockBase = base + Ui.px(14) - fm.ascent * 0.86f;
        if (!front) c.drawText(time, cx, clockBase, big);
        // today's range under it
        if (front && Weather.known() && !Weather.summary.isEmpty()) {
            small.setTextSize(Ui.px(15));
            small.setTextAlign(Paint.Align.CENTER);
            c.drawText(Weather.summary + "  ·  H " + Weather.hi + "°  L " + Weather.lo + "°", cx, clockBase + Ui.px(30), small);
        }
    }

    final Runnable tick = new Runnable() { public void run() {
        Calendar k = Calendar.getInstance();
        int m = k.get(Calendar.MINUTE);
        shiftX = Ui.px((m % 5 - 2) * 4); // a few pixels each minute: no burn in
        shiftY = Ui.px((m % 3 - 1) * 4);
        a.invalidate();
        new Thread(() -> { Weather.refresh(PhotoDream.this); h.post(a::invalidate); }).start();
        h.postDelayed(this, 60000 - k.get(Calendar.SECOND) * 1000);
    }};

    @Override public void onDreamingStarted() { super.onDreamingStarted(); a.reload(); a.run(true); h.post(tick); }
    @Override public void onDreamingStopped() { h.removeCallbacks(tick); a.run(false); super.onDreamingStopped(); }
}
