package tv.steroid.home;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.text.format.DateFormat;
import java.util.Calendar;
import java.util.Locale;

/**
 * The screensaver clock, in HyperOS 4 lock screen styles. Shared by the screensaver (PhotoDream) and
 * the style picker in Launcher settings (ClockStyles), so what you pick is exactly what you get.
 *
 * Two layers, so the depth effect keeps working in every style: the big digits are drawn behind the
 * photo's subject (draw with front false), the small things (date, weather, AM/PM, seconds) in front.
 *
 * Steady: every digit sits in a fixed width cell, so a 1 changing to a 0 never moves the clock.
 * Contrast follows the photo (lum): the brighter the picture, the firmer the shadow.
 */
class ClockFace {
    static final String[] NAMES = {"Classic", "Tall", "Outline", "Glass", "Gradient", "Stacked", "Minimal"};
    static final String[] HINTS = {
        "Date and weather on top, a large centred time, AM/PM over the seconds",
        "Very tall narrow hours, faded minutes beside them, date at the bottom left",
        "Narrow hours in solid white, minutes as an outline",
        "Solid hours, see through minutes, like frosted glass",
        "Narrow digits filled with a cool sky gradient",
        "Hours over minutes on the left, date and weather beside them",
        "A small, light clock in the bottom left corner",
    };

    final Context ctx;
    final Paint big = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG), side = new Paint(Paint.ANTI_ALIAS_FLAG),
        small = new Paint(Paint.ANTI_ALIAS_FLAG), fx = new Paint(Paint.ANTI_ALIAS_FLAG);
    final Typeface light, regular, bold, heavy;
    Drawable wIcon;
    int wIconFor = -1;

    int style;
    boolean showDate = true, showWeather = true, showAmpm = true, showSecs = true, showRange = true;

    ClockFace(Context c) {
        ctx = c;
        light = font(c, 300);
        regular = font(c, 500);
        bold = font(c, 700);
        heavy = font(c, 800);
        side.setTypeface(Ui.sansMedium);
        small.setTypeface(Ui.sansMedium);
        fx.setStrokeCap(Paint.Cap.ROUND);
        load();
    }

    static Typeface font(Context c, int w) {
        try { return new Typeface.Builder(c.getAssets(), "fonts/sans.ttf").setFontVariationSettings("'wght' " + w).build(); }
        catch (Exception e) { return Ui.sansMedium; }
    }

    /** The choices made in Launcher settings: style and which elements show. */
    void load() {
        SharedPreferences sp = Prefs.sp(ctx);
        style = Math.max(0, Math.min(NAMES.length - 1, sp.getInt("ss_style", 0)));
        showDate = sp.getBoolean("ss_date", true);
        showWeather = sp.getBoolean("ss_weather", true);
        showAmpm = sp.getBoolean("ss_ampm", true);
        showSecs = sp.getBoolean("ss_secs", true);
        showRange = sp.getBoolean("ss_range", true);
    }

    /** Shadows grow with the photo's brightness: soft on dark photos, firm on bright ones. */
    static void shade(Paint p, float radiusDp, float lum) {
        int alpha = Math.round(255 * Math.max(0.35f, Math.min(0.85f, 0.35f + 0.9f * (lum - 0.3f))));
        p.setShadowLayer(Ui.px(radiusDp * (1 + lum)), 0, Ui.px(2), alpha << 24);
    }

    // ---------- steady digits ----------

    /** The widest digit: every digit gets a cell this wide, so the clock never shifts as it ticks. */
    static float cell(Paint p) {
        float m = 0;
        for (char d = '0'; d <= '9'; d++) m = Math.max(m, p.measureText(String.valueOf(d)));
        return m;
    }

    static float width(String s, Paint p, float cell, float track) {
        float w = 0;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            w += Character.isDigit(ch) ? cell : p.measureText(String.valueOf(ch));
            if (i < s.length() - 1) w += track;
        }
        return w;
    }

    /** Draws s from x, each digit centred in its fixed cell. Returns where it ended. */
    static float digits(Canvas c, String s, float x, float y, Paint p, float cell, float track) {
        Paint.Align was = p.getTextAlign();
        p.setTextAlign(Paint.Align.CENTER);
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            float cw = Character.isDigit(ch) ? cell : p.measureText(String.valueOf(ch));
            c.drawText(String.valueOf(ch), x + cw / 2, y, p);
            x += cw + (i < s.length() - 1 ? track : 0);
        }
        p.setTextAlign(was);
        return x;
    }

    static void reset(Paint p) {
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        p.setTextScaleX(1f);
        p.setLetterSpacing(0f);
        p.setColor(0xFFFFFFFF);
        p.setTextAlign(Paint.Align.LEFT);
    }

    // ---------- the pieces of text ----------

    String ampm(Calendar k) { return k.get(Calendar.AM_PM) == Calendar.AM ? "AM" : "PM"; }
    String secs(Calendar k) { return String.format(Locale.US, "%02d", k.get(Calendar.SECOND)); }
    String weatherText() { return Weather.known() ? Weather.temp + "°" + (Weather.city.isEmpty() ? "" : "  " + Weather.city) : ""; }
    String rangeText() { return Weather.known() && !Weather.summary.isEmpty() ? Weather.summary + "  ·  H " + Weather.hi + "°  L " + Weather.lo + "°" : ""; }

    /** Date, then the weather icon and temperature, on one line from x (left) or centred on x. */
    float infoLine(Canvas c, String date, float x, float base, float sizeDp, float lum, boolean centre) {
        small.setTextSize(Ui.px(sizeDp));
        small.setTextAlign(Paint.Align.LEFT);
        small.setColor(0xF2FFFFFF);
        shade(small, 6, lum);
        String wt = showWeather ? weatherText() : "";
        String d = showDate ? date : "";
        float icon = Ui.px(sizeDp * 1.4f), igap = Ui.px(sizeDp * 0.55f), sep = d.isEmpty() || wt.isEmpty() ? 0 : Ui.px(sizeDp * 1.1f);
        float dw = d.isEmpty() ? 0 : small.measureText(d), ww = wt.isEmpty() ? 0 : sep + icon + igap + small.measureText(wt);
        if (dw + ww == 0) return x;
        float x0 = centre ? x - (dw + ww) / 2 : x;
        if (!d.isEmpty()) c.drawText(d, x0, base, small);
        if (!wt.isEmpty()) {
            float ix = x0 + dw + sep;
            weatherIcon(c, ix, base - icon + Ui.px(sizeDp * 0.24f), icon, lum);
            c.drawText(wt, ix + icon + igap, base, small);
        }
        return x0 + dw + ww;
    }

    /** AM/PM over the seconds, small, left aligned at x: top of the AM/PM at capTop, seconds on baseline. */
    void sideStack(Canvas c, Calendar k, float x, float capTop, float baseline, float size, float lum) {
        side.setTypeface(Ui.sansMedium);
        side.setTextSize(size);
        side.setTextAlign(Paint.Align.LEFT);
        side.setLetterSpacing(0.04f);
        shade(side, 8, lum);
        if (showAmpm) {
            side.setColor(0xF2FFFFFF);
            c.drawText(ampm(k), x, capTop - side.ascent() * 0.74f, side);
        }
        if (showSecs) {
            side.setColor(0xCCFFFFFF);
            digits(c, secs(k), x, baseline, side, cell(side), 0);
        }
        side.setLetterSpacing(0f);
    }

    // ---------- the styles ----------

    void draw(Canvas c, int w, int h, boolean front, float lum, int sx, int sy) {
        Calendar k = Calendar.getInstance();
        reset(big);
        switch (style) {
            case 1: tall(c, k, w, h, front, lum, sx, sy); break;
            case 2: case 3: case 4: narrow(c, k, w, h, front, lum, sx, sy); break;
            case 5: stacked(c, k, w, h, front, lum, sx, sy); break;
            case 6: minimal(c, k, w, h, front, lum, sx, sy); break;
            default: classic(c, k, w, h, front, lum, sx, sy);
        }
    }

    /** Classic: date and weather on top, a large centred h:mm, AM/PM over the seconds on its right. */
    void classic(Canvas c, Calendar k, int w, int h, boolean front, float lum, int sx, int sy) {
        float cx = w / 2f + sx, top = h * 0.085f + sy, base = top + Ui.px(20);
        String time = DateFormat.format("h:mm", k).toString();
        big.setTypeface(bold);
        big.setTextSize(h * 0.30f);
        float track = -big.getTextSize() * 0.02f, cl = cell(big);
        float tw = width(time, big, cl, track), gap = Ui.px(12);
        side.setTextSize(h * 0.30f * 0.19f);
        float sw = (showAmpm || showSecs) ? Math.max(side.measureText("PM"), cell(side) * 2) : 0;
        float x0 = cx - (tw + (sw > 0 ? gap + sw : 0)) / 2;
        Paint.FontMetrics fm = big.getFontMetrics();
        float clockBase = base + Ui.px(14) - fm.ascent * 0.86f;
        if (!front) { shade(big, 16, lum); digits(c, time, x0, clockBase, big, cl, track); return; }
        float capTop = clockBase - big.getTextSize() * 0.70f;
        if (sw > 0) sideStack(c, k, x0 + tw + gap, capTop, clockBase, side.getTextSize(), lum);
        if (showDate || showWeather) infoLine(c, DateFormat.format("EEEE, d MMMM", k).toString(), cx, base, 17, lum, true);
        if (showRange && !rangeText().isEmpty()) {
            small.setTextSize(Ui.px(15));
            small.setTextAlign(Paint.Align.CENTER);
            c.drawText(rangeText(), cx, clockBase + Ui.px(30), small);
            small.setTextAlign(Paint.Align.LEFT);
        }
    }

    /** Tall: very tall narrow hours, the minutes faded beside them, date at the bottom left. */
    void tall(Canvas c, Calendar k, int w, int h, boolean front, float lum, int sx, int sy) {
        String hh = DateFormat.format("hh", k).toString(), mm = DateFormat.format("mm", k).toString();
        big.setTypeface(bold);
        big.setTextSize(h * 0.86f);
        big.setTextScaleX(0.40f);
        float cl = cell(big), track = cl * 0.04f, cap = big.getTextSize() * 0.70f;
        float x0 = w * 0.06f + sx, baseline = h * 0.5f + cap / 2 + sy;
        float hw = width(hh, big, cl, track), gap = cl * 0.18f;
        if (!front) {
            shade(big, 18, lum);
            big.setColor(0xF2FFFFFF);
            digits(c, hh, x0, baseline, big, cl, track);
            big.setColor(0x66FFFFFF);
            digits(c, mm, x0 + hw + gap, baseline, big, cl, track);
            return;
        }
        float mEnd = x0 + hw + gap + width(mm, big, cl, track);
        sideStack(c, k, mEnd + Ui.px(14), baseline - cap, baseline, Ui.px(24), lum);
        float y = baseline + Ui.px(40);
        if (y > h - Ui.px(30)) y = h - Ui.px(30);
        if (showDate) {
            small.setTextSize(Ui.px(20));
            small.setColor(0xF2FFFFFF);
            shade(small, 6, lum);
            c.drawText(DateFormat.format("d/M", k).toString(), x0 + Ui.px(6), baseline - cap * 0.30f, small);
            c.drawText(DateFormat.format("EEEE", k).toString(), x0 + Ui.px(6), baseline - cap * 0.30f + Ui.px(26), small);
        }
        if (showWeather) {
            float wy = baseline - cap * 0.30f + (showDate ? Ui.px(58) : 0);
            boolean wasDate = showDate;
            showDate = false;
            infoLine(c, "", x0 + Ui.px(6), wy, 18, lum, false);
            showDate = wasDate;
        }
    }

    /** Outline, Glass, Gradient: narrow digits across the top, date and weather beneath. */
    void narrow(Canvas c, Calendar k, int w, int h, boolean front, float lum, int sx, int sy) {
        String hh = DateFormat.format("hh", k).toString(), mm = DateFormat.format("mm", k).toString();
        big.setTypeface(style == 3 ? regular : heavy);
        big.setTextSize(h * 0.50f);
        big.setTextScaleX(0.56f);
        float cl = cell(big), track = cl * 0.02f, cap = big.getTextSize() * 0.70f;
        float hw = width(hh, big, cl, track), mw = width(mm, big, cl, track), gap = cl * 0.22f;
        float x0 = w / 2f - (hw + gap + mw) / 2 + sx, capTop = h * 0.10f + sy, baseline = capTop + cap;
        if (!front) {
            if (style == 4) {   // gradient: cool sky, light at the top
                big.setShader(new LinearGradient(0, capTop, 0, baseline, 0xFFDDFBFF, 0xFF3FA2FF, Shader.TileMode.CLAMP));
                shade(big, 14, lum * 0.8f);
                digits(c, hh, x0, baseline, big, cl, track);
                big.setAlpha(210);
                digits(c, mm, x0 + hw + gap, baseline, big, cl, track);
                big.setShader(null);
                return;
            }
            shade(big, 16, lum);
            big.setColor(0xF5FFFFFF);
            digits(c, hh, x0, baseline, big, cl, track);
            if (style == 2) {   // outline minutes
                big.setStyle(Paint.Style.STROKE);
                big.setStrokeWidth(Ui.px(3));
                big.setColor(0xF2FFFFFF);
                big.clearShadowLayer();
            } else big.setColor(0x80FFFFFF);   // glass: see through
            digits(c, mm, x0 + hw + gap, baseline, big, cl, track);
            reset(big);
            return;
        }
        float infoBase = baseline + Ui.px(34);
        infoLine(c, DateFormat.format("EEE d MMM", k).toString(), x0 + Ui.px(4), infoBase, 18, lum, false);
        sideStack(c, k, x0 + hw + gap + mw + Ui.px(14), capTop, baseline, Ui.px(22), lum);
        if (showRange && !rangeText().isEmpty()) {
            small.setTextSize(Ui.px(14));
            small.setColor(0xCCFFFFFF);
            c.drawText(rangeText(), x0 + Ui.px(4), infoBase + Ui.px(24), small);
        }
    }

    /** Stacked: hours over minutes on the left, a column of date, weather and seconds beside them. */
    void stacked(Canvas c, Calendar k, int w, int h, boolean front, float lum, int sx, int sy) {
        String hh = DateFormat.format("hh", k).toString(), mm = DateFormat.format("mm", k).toString();
        big.setTypeface(bold);
        big.setTextSize(h * 0.40f);
        big.setTextScaleX(0.72f);
        float cl = cell(big), track = -cl * 0.02f, cap = big.getTextSize() * 0.70f, lead = cap * 0.12f;
        float x0 = w * 0.07f + sx, top = h * 0.10f + sy, b1 = top + cap, b2 = b1 + lead + cap;
        float bw = Math.max(width(hh, big, cl, track), width(mm, big, cl, track));
        if (!front) {
            shade(big, 16, lum);
            big.setColor(0xF5FFFFFF);
            digits(c, hh, x0, b1, big, cl, track);
            big.setColor(0xB3FFFFFF);
            digits(c, mm, x0, b2, big, cl, track);
            return;
        }
        float cx = x0 + bw + Ui.px(34), y = top + Ui.px(30);
        small.setColor(0xF2FFFFFF);
        shade(small, 6, lum);
        if (showDate) {
            small.setTypeface(Ui.sansMedium);
            small.setTextSize(Ui.px(30));
            c.drawText(DateFormat.format("EEEE", k).toString(), cx, y, small);
            small.setTextSize(Ui.px(19));
            small.setColor(0xCCFFFFFF);
            c.drawText(DateFormat.format("d MMMM", k).toString(), cx, y + Ui.px(30), small);
            y += Ui.px(30 + 44);
        }
        if (showWeather && !weatherText().isEmpty()) {
            boolean d = showDate;
            showDate = false;
            infoLine(c, "", cx, y, 19, lum, false);
            showDate = d;
            y += Ui.px(30);
            if (showRange && !rangeText().isEmpty()) {
                small.setTextSize(Ui.px(14));
                small.setColor(0xB3FFFFFF);
                c.drawText(rangeText(), cx, y, small);
            }
        }
        sideStack(c, k, cx, b2 - cap, b2, Ui.px(24), lum);
    }

    /** Minimal: a small light clock in the bottom left, the date and weather under it. */
    void minimal(Canvas c, Calendar k, int w, int h, boolean front, float lum, int sx, int sy) {
        String time = DateFormat.format("h:mm", k).toString();
        big.setTypeface(light);
        big.setTextSize(h * 0.17f);
        float cl = cell(big), track = 0, cap = big.getTextSize() * 0.70f;
        float x0 = Ui.px(64) + sx, info = h - Ui.px(56) + sy, baseline = info - Ui.px(34);
        if (!showDate && !showWeather) baseline = info;
        float tw = width(time, big, cl, track);
        if (!front) { shade(big, 12, lum); big.setColor(0xF5FFFFFF); digits(c, time, x0, baseline, big, cl, track); return; }
        sideStack(c, k, x0 + tw + Ui.px(10), baseline - cap, baseline, Ui.px(18), lum);
        float end = infoLine(c, DateFormat.format("EEEE, d MMMM", k).toString(), x0 + Ui.px(4), info, 17, lum, false);
        if (showRange && !rangeText().isEmpty()) {
            small.setTextSize(Ui.px(14));
            small.setColor(0xB3FFFFFF);
            c.drawText("·  " + rangeText(), end + Ui.px(16), info, small);
        }
    }

    // ---------- weather icon ----------

    /**
     * The weather icon, moving with the weather: the sun turns slowly, clouds drift, rain and snow
     * fall from the cloud, storms flash, fog sways and the moon glows.
     */
    void weatherIcon(Canvas c, float x, float y, float size, float lum) {
        int type = Weather.icon();
        if (wIconFor != type || wIcon == null) { wIcon = ctx.getDrawable(Icon.RES[type]); wIconFor = type; }
        if (wIcon == null) return;
        float t = SystemClock.uptimeMillis() / 1000f, cxI = x + size / 2, cyI = y + size / 2;
        int alpha = 242;
        c.save();
        switch (type) {
            case Icon.SUN: c.rotate((t * 18) % 360, cxI, cyI); break;
            case Icon.CLOUD: case Icon.CLOUD_SUN: case Icon.CLOUD_MOON:
                c.translate((float) Math.sin(t * 0.9) * size * 0.08f, 0); break;
            case Icon.CLOUD_FOG: c.translate((float) Math.sin(t * 0.6) * size * 0.12f, 0);
                alpha = Math.round(170 + 70 * (float) Math.sin(t * 1.3)); break;
            case Icon.MOON: alpha = Math.round(200 + 42 * (float) Math.sin(t * 1.5)); break;
            case Icon.CLOUD_LIGHTNING: {
                float ph = t % 3.2f;
                alpha = ph < 0.12f || (ph > 0.25f && ph < 0.33f) ? 255 : 190; break;
            }
            default: c.translate(0, (float) Math.sin(t * 1.6) * size * 0.03f);
        }
        wIcon.setTint(0xFFFFFFFF);
        wIcon.setAlpha(alpha);
        wIcon.setBounds((int) x, (int) y, (int) (x + size), (int) (y + size));
        wIcon.draw(c);
        c.restore();
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
            if (ph < 0.12f) { fx.setColor(0x40FFFFFF); c.drawCircle(cxI, cyI, size, fx); }
        }
    }
}
