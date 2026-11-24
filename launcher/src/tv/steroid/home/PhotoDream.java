package tv.steroid.home;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.service.dreams.DreamService;
import android.text.format.DateFormat;
import java.util.Calendar;

/**
 * Screensaver "Steroid photos", in the style of a HyperOS lock screen: your photos woven through the
 * free photos and views at full colour, the date and weather (animated icon) at the top, a large clock
 * with AM/PM and running seconds, and the depth effect on photos with a clear subject.
 * Contrast follows each photo: the brighter the picture behind the clock, the deeper the shade and the
 * stronger the text's shadow, so the time stays readable on skies, snow and white walls.
 * Choose it in Settings, Device Preferences, Screen saver, or: scripts/buchi.sh screensaver
 */
public class PhotoDream extends DreamService {
    Ambient a;
    final Handler h = new Handler(Looper.getMainLooper());
    final Paint big = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG), side = new Paint(Paint.ANTI_ALIAS_FLAG),
        small = new Paint(Paint.ANTI_ALIAS_FLAG), fx = new Paint(Paint.ANTI_ALIAS_FLAG);
    int shiftX, shiftY;
    Drawable wIcon;
    int wIconFor = -1;

    @Override public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setFullscreen(true);
        setInteractive(false);
        Ui.init(this);
        Weather.restore(this);
        Typeface bold = new Typeface.Builder(getAssets(), "fonts/sans.ttf").setFontVariationSettings("'wght' 700").build();
        big.setTypeface(bold);
        big.setColor(0xFFFFFFFF);
        big.setTextAlign(Paint.Align.LEFT);
        big.setLetterSpacing(-0.03f);
        side.setTypeface(Ui.sansMedium);
        side.setColor(0xF2FFFFFF);
        side.setTextAlign(Paint.Align.LEFT);
        small.setTypeface(Ui.sansMedium);
        small.setColor(0xF2FFFFFF);
        fx.setStrokeCap(Paint.Cap.ROUND);
        a = new Ambient(this);
        a.dream = true;
        a.overlay = new Ambient.Overlay() {
            public void draw(Canvas c, int w, int hh) { drawClock(c, w, hh, false); }      // behind the subject
            public void drawFront(Canvas c, int w, int hh) { drawClock(c, w, hh, true); }  // in front of it
        };
        setContentView(a);
    }

    /** Shadows grow with the photo's brightness: soft on dark photos, firm on bright ones. */
    void shade(Paint p, float radiusDp, float lum) {
        int alpha = Math.round(255 * Math.max(0.35f, Math.min(0.85f, 0.35f + 0.9f * (lum - 0.3f))));
        p.setShadowLayer(Ui.px(radiusDp * (1 + lum)), 0, Ui.px(2), alpha << 24);
    }

    void drawClock(Canvas c, int w, int hgt, boolean front) {
        Calendar k = Calendar.getInstance();
        float lum = a.lumNow;
        float cx = w / 2f + shiftX, top = hgt * 0.085f + shiftY;
        float base = top + Ui.px(20);

        // the big time, 12 hour, with AM/PM above the seconds on its right (laid out as one centred group)
        String time = DateFormat.format("h:mm", k).toString();
        big.setTextSize(hgt * 0.30f);
        side.setTextSize(hgt * 0.30f * 0.20f);
        float tw = big.measureText(time), gap = Ui.px(14);
        String ampm = k.get(Calendar.AM_PM) == Calendar.AM ? "AM" : "PM";
        String secs = String.format(java.util.Locale.US, "%02d", k.get(Calendar.SECOND));
        float sw = Math.max(side.measureText(ampm), side.measureText("00"));
        float x0 = cx - (tw + gap + sw) / 2;
        Paint.FontMetrics fm = big.getFontMetrics();
        float clockBase = base + Ui.px(14) - fm.ascent * 0.86f;
        if (!front) {
            shade(big, 16, lum);
            c.drawText(time, x0, clockBase, big);
            return;
        }
        // AM/PM and seconds stay in front, beside the digits
        shade(side, 8, lum);
        float digitTop = clockBase + fm.ascent * 0.72f; // cap height of the digits
        c.drawText(ampm, x0 + tw + gap, digitTop - side.ascent() * 0.9f, side);
        side.setColor(0xCCFFFFFF);
        c.drawText(secs, x0 + tw + gap, clockBase, side);
        side.setColor(0xF2FFFFFF);

        // date · weather (animated icon) above the time
        small.setTextSize(Ui.px(17));
        shade(small, 6, lum);
        small.setTextAlign(Paint.Align.LEFT);
        String date = DateFormat.format("EEEE, d MMMM", k).toString();
        String wtext = Weather.known() ? Weather.temp + "°" + (Weather.city.isEmpty() ? "" : "  " + Weather.city) : "";
        float iconSize = Ui.px(24), igap = Ui.px(10), sep = Ui.px(20);
        float dw = small.measureText(date), ww = wtext.isEmpty() ? 0 : sep + iconSize + igap + small.measureText(wtext);
        float x = cx - (dw + ww) / 2;
        c.drawText(date, x, base, small);
        if (!wtext.isEmpty()) {
            float ix = x + dw + sep;
            weatherIcon(c, ix, base - iconSize + Ui.px(4), iconSize, lum);
            c.drawText(wtext, ix + iconSize + igap, base, small);
        }
        // today's range under the time
        if (Weather.known() && !Weather.summary.isEmpty()) {
            small.setTextSize(Ui.px(15));
            small.setTextAlign(Paint.Align.CENTER);
            c.drawText(Weather.summary + "  ·  H " + Weather.hi + "°  L " + Weather.lo + "°", cx, clockBase + Ui.px(30), small);
        }
    }

    /**
     * The weather icon, moving with the weather: the sun turns slowly, clouds drift, rain and snow
     * fall from the cloud, storms flash, fog sways and the moon glows.
     */
    void weatherIcon(Canvas c, float x, float y, float size, float lum) {
        int type = Weather.icon();
        if (wIconFor != type || wIcon == null) { wIcon = getDrawable(Icon.RES[type]); wIconFor = type; }
        if (wIcon == null) return;
        float t = SystemClock.uptimeMillis() / 1000f, cxI = x + size / 2, cyI = y + size / 2;
        int alpha = 242;
        c.save();
        switch (type) {
            case Icon.SUN: c.rotate((t * 18) % 360, cxI, cyI); break;                                   // slow turn
            case Icon.CLOUD: case Icon.CLOUD_SUN: case Icon.CLOUD_MOON:
                c.translate((float) Math.sin(t * 0.9) * size * 0.08f, 0); break;                        // drift
            case Icon.CLOUD_FOG: c.translate((float) Math.sin(t * 0.6) * size * 0.12f, 0);
                alpha = Math.round(170 + 70 * (float) Math.sin(t * 1.3)); break;                       // sway, thin out
            case Icon.MOON: alpha = Math.round(200 + 42 * (float) Math.sin(t * 1.5)); break;           // glow
            case Icon.CLOUD_LIGHTNING: {                                                                // flash
                float ph = t % 3.2f;
                alpha = ph < 0.12f || (ph > 0.25f && ph < 0.33f) ? 255 : 190; break;
            }
            default: c.translate(0, (float) Math.sin(t * 1.6) * size * 0.03f);                         // rain, snow: bob
        }
        wIcon.setTint(0xFFFFFFFF);
        wIcon.setAlpha(alpha);
        wIcon.setBounds((int) x, (int) y, (int) (x + size), (int) (y + size));
        wIcon.draw(c);
        c.restore();
        // falling drops or flakes below the cloud
        if (type == Icon.CLOUD_RAIN || type == Icon.SNOWFLAKE) {
            boolean snow = type == Icon.SNOWFLAKE;
            fx.setColor(snow ? 0xE6FFFFFF : 0xCCBFD9FF);
            fx.setStrokeWidth(Ui.px(snow ? 2.2f : 1.6f));
            for (int i = 0; i < 3; i++) {
                float ph = (t * (snow ? 0.5f : 1.4f) + i / 3f) % 1f;
                float dx = x + size * (0.25f + 0.25f * i), dy = y + size * (0.85f + 0.55f * ph);
                fx.setAlpha(Math.round(220 * (1 - ph)));
                if (snow) c.drawCircle(dx + (float) Math.sin(t * 2 + i) * Ui.px(2), dy, Ui.px(1.6f), fx);
                else c.drawLine(dx, dy, dx - Ui.px(1.5f), dy + Ui.px(5), fx);
            }
        }
        if (type == Icon.CLOUD_LIGHTNING) {
            float ph = t % 3.2f;
            if (ph < 0.12f) { fx.setColor(0x40FFFFFF); c.drawCircle(cxI, cyI, size, fx); } // a brief glow
        }
    }

    final Runnable tick = new Runnable() { public void run() {
        Calendar k = Calendar.getInstance();
        int m = k.get(Calendar.MINUTE);
        shiftX = Ui.px((m % 5 - 2) * 4); // a few pixels each minute: no burn in
        shiftY = Ui.px((m % 3 - 1) * 4);
        new Thread(() -> { Weather.refresh(PhotoDream.this); h.post(a::invalidate); }).start();
        h.postDelayed(this, 60000 - k.get(Calendar.SECOND) * 1000);
    }};

    @Override public void onDreamingStarted() { super.onDreamingStarted(); a.reload(); a.run(true); h.post(tick); }
    @Override public void onDreamingStopped() { h.removeCallbacks(tick); a.run(false); super.onDreamingStopped(); }
}
