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
    static final String[] NAMES = {"Classic", "Tall", "Outline", "Glass", "Gradient", "Stacked", "Minimal",
        "Ring", "Split", "Mono", "Serif", "Block", "Corner"};
    static final String[] HINTS = {
        "Date and weather on top, a large centred time, AM/PM over the seconds",
        "Very tall narrow hours, faded minutes, a tidy column with date, weather and seconds",
        "Narrow hours in solid white, minutes as an outline",
        "Solid hours, see through minutes, like frosted glass",
        "Narrow digits filled with a cool sky gradient",
        "Hours over minutes on the left, date and weather beside them",
        "A small, light clock in the bottom left corner",
        "The time inside a thin ring that fills with the seconds",
        "Hours on the far left, minutes on the far right, the date between them",
        "A typewriter clock with a blinking colon, top left",
        "An elegant italic serif time, centred, with a spaced out date",
        "Heavy, tightly packed digits low on the screen",
        "A compact clock top right, everything aligned to the right",
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
            case 7: ring(c, k, w, h, front, lum, sx, sy); break;
            case 8: split(c, k, w, h, front, lum, sx, sy); break;
            case 9: mono(c, k, w, h, front, lum, sx, sy); break;
            case 10: serif(c, k, w, h, front, lum, sx, sy); break;
            case 11: block(c, k, w, h, front, lum, sx, sy); break;
            case 12: corner(c, k, w, h, front, lum, sx, sy); break;
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

    /** Tall: very tall narrow hours, the minutes faded beside them, a tidy column of details on the right. */
    void tall(Canvas c, Calendar k, int w, int h, boolean front, float lum, int sx, int sy) {
        String hh = DateFormat.format("hh", k).toString(), mm = DateFormat.format("mm", k).toString();
        big.setTypeface(bold);
        big.setTextSize(h * 0.80f);
        big.setTextScaleX(0.42f);
        float cl = cell(big), track = cl * 0.02f, cap = big.getTextSize() * 0.70f;
        float hw = width(hh, big, cl, track), mw = width(mm, big, cl, track), gap = cl * 0.10f;
        float colW = Ui.px(200), x0 = (w - (hw + gap + mw + Ui.px(36) + colW)) / 2f + sx;   // digits and column, centred together
        float capTop = (h - cap) / 2f + sy, baseline = capTop + cap;
        if (!front) {
            shade(big, 18, lum);
            big.setColor(0xF5FFFFFF);
            digits(c, hh, x0, baseline, big, cl, track);
            big.setColor(0x6BFFFFFF);
            digits(c, mm, x0 + hw + gap, baseline, big, cl, track);
            return;
        }
        float cx = x0 + hw + gap + mw + Ui.px(36);
        small.setColor(0xF2FFFFFF);
        shade(small, 6, lum);
        if (showDate) {   // weekday and date at the top of the column, level with the top of the digits
            small.setTextSize(Ui.px(28));
            c.drawText(DateFormat.format("EEEE", k).toString(), cx, capTop + Ui.px(24), small);
            small.setTextSize(Ui.px(18));
            small.setColor(0xCCFFFFFF);
            c.drawText(DateFormat.format("d MMMM", k).toString(), cx, capTop + Ui.px(52), small);
        }
        if (showWeather && !weatherText().isEmpty()) {
            boolean d = showDate;
            showDate = false;
            infoLine(c, "", cx, capTop + Ui.px(showDate || d ? 92 : 24), 18, lum, false);
            showDate = d;
            if (showRange && !rangeText().isEmpty()) {
                small.setTextSize(Ui.px(13));
                small.setColor(0xB3FFFFFF);
                c.drawText(rangeText(), cx, capTop + Ui.px(d ? 118 : 50), small);
            }
        }
        sideStack(c, k, cx, baseline - Ui.px(56), baseline, Ui.px(26), lum);   // AM/PM and seconds on the baseline
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

    /** Ring: the time inside a thin ring that fills with the seconds, right of centre. */
    void ring(Canvas c, Calendar k, int w, int h, boolean front, float lum, int sx, int sy) {
        float r = h * 0.27f, cx = w * 0.68f + sx, cy = h * 0.44f + sy;
        String time = DateFormat.format("h:mm", k).toString();
        big.setTypeface(bold);
        big.setTextSize(r * 0.62f);
        float cl = cell(big), track = -big.getTextSize() * 0.02f, cap = big.getTextSize() * 0.70f;
        float tw = width(time, big, cl, track);
        if (!front) { shade(big, 14, lum); digits(c, time, cx - tw / 2, cy + cap / 2, big, cl, track); return; }
        fx.setStyle(Paint.Style.STROKE);
        fx.setStrokeWidth(Ui.px(4));
        fx.setColor(0x33FFFFFF);
        fx.setShadowLayer(Ui.px(6), 0, Ui.px(1), 0x66000000);
        c.drawCircle(cx, cy, r, fx);
        long ms = System.currentTimeMillis() % 60000;
        fx.setColor(0xF2FFFFFF);
        c.drawArc(cx - r, cy - r, cx + r, cy + r, -90, ms / 60000f * 360f, false, fx);
        fx.clearShadowLayer();
        fx.setStyle(Paint.Style.FILL);
        if (showAmpm) {
            side.setTextSize(Ui.px(18));
            side.setTextAlign(Paint.Align.CENTER);
            side.setColor(0xCCFFFFFF);
            shade(side, 6, lum);
            c.drawText(ampm(k), cx, cy + cap / 2 + Ui.px(34), side);
            side.setTextAlign(Paint.Align.LEFT);
        }
        infoLine(c, DateFormat.format("EEEE, d MMMM", k).toString(), cx, cy + r + Ui.px(46), 18, lum, true);
        if (showRange && !rangeText().isEmpty()) {
            small.setTextSize(Ui.px(14));
            small.setTextAlign(Paint.Align.CENTER);
            small.setColor(0xB3FFFFFF);
            c.drawText(rangeText(), cx, cy + r + Ui.px(72), small);
            small.setTextAlign(Paint.Align.LEFT);
        }
    }

    /** Split: hours on the far left, minutes on the far right, the details centred low between them. */
    void split(Canvas c, Calendar k, int w, int h, boolean front, float lum, int sx, int sy) {
        String hh = DateFormat.format("hh", k).toString(), mm = DateFormat.format("mm", k).toString();
        big.setTypeface(bold);
        big.setTextSize(h * 0.62f);
        big.setTextScaleX(0.52f);
        float cl = cell(big), track = cl * 0.02f, cap = big.getTextSize() * 0.70f;
        float mw = width(mm, big, cl, track), edge = w * 0.05f;
        float baseline = h * 0.12f + cap + sy;
        if (!front) {
            shade(big, 16, lum);
            big.setColor(0xF5FFFFFF);
            digits(c, hh, edge + sx, baseline, big, cl, track);
            big.setColor(0xCCFFFFFF);
            digits(c, mm, w - edge - mw + sx, baseline, big, cl, track);
            return;
        }
        float mid = w / 2f + sx, y = baseline + Ui.px(44);
        infoLine(c, DateFormat.format("EEEE, d MMMM", k).toString(), mid, y, 19, lum, true);
        if (showAmpm || showSecs) {
            side.setTextSize(Ui.px(20));
            side.setTextAlign(Paint.Align.CENTER);
            side.setColor(0xCCFFFFFF);
            shade(side, 6, lum);
            String t = (showAmpm ? ampm(k) : "") + (showAmpm && showSecs ? "   " : "") + (showSecs ? ":" + secs(k) : "");
            c.drawText(t, mid, baseline - cap / 2, side);
            side.setTextAlign(Paint.Align.LEFT);
        }
    }

    /** Mono: a typewriter clock with a blinking colon, top left, details in the same type. */
    void mono(Canvas c, Calendar k, int w, int h, boolean front, float lum, int sx, int sy) {
        big.setTypeface(Ui.mono);
        big.setTextSize(h * 0.20f);
        String hh = DateFormat.format("hh", k).toString(), mm = DateFormat.format("mm", k).toString();
        float cl = cell(big), cap = big.getTextSize() * 0.70f, x0 = w * 0.07f + sx, baseline = h * 0.12f + cap + sy;
        float colon = big.measureText(":");
        if (!front) {
            shade(big, 12, lum);
            big.setColor(0xF5FFFFFF);
            float x = digits(c, hh, x0, baseline, big, cl, 0);
            big.setAlpha(k.get(Calendar.SECOND) % 2 == 0 ? 245 : 70);   // the colon blinks with the seconds
            c.drawText(":", x, baseline, big);
            big.setAlpha(245);
            digits(c, mm, x + colon, baseline, big, cl, 0);
            return;
        }
        float end = x0 + cl * 4 + colon;
        sideStack(c, k, end + Ui.px(14), baseline - cap, baseline, Ui.px(22), lum);
        Typeface was = small.getTypeface();
        small.setTypeface(Ui.mono);
        infoLine(c, DateFormat.format("EEE dd.MM.yyyy", k).toString().toLowerCase(Locale.US), x0 + Ui.px(4), baseline + Ui.px(42), 17, lum, false);
        if (showRange && !rangeText().isEmpty()) {
            small.setTextSize(Ui.px(14));
            small.setColor(0xB3FFFFFF);
            c.drawText(rangeText().toLowerCase(Locale.US), x0 + Ui.px(4), baseline + Ui.px(68), small);
        }
        small.setTypeface(was);
    }

    /** Serif: an elegant italic serif time, centred, a spaced out date above it. */
    void serif(Canvas c, Calendar k, int w, int h, boolean front, float lum, int sx, int sy) {
        String time = DateFormat.format("h:mm", k).toString();
        big.setTypeface(Ui.serifItalic);
        big.setTextSize(h * 0.36f);
        float cl = cell(big), track = -big.getTextSize() * 0.01f, cap = big.getTextSize() * 0.68f;
        float tw = width(time, big, cl, track), cx = w / 2f + sx, top = h * 0.16f + sy, baseline = top + Ui.px(30) + cap;
        side.setTextSize(Ui.px(22));
        float sw = (showAmpm || showSecs) ? Math.max(side.measureText("PM"), cell(side) * 2) + Ui.px(14) : 0;
        float x0 = cx - (tw + sw) / 2;
        if (!front) { shade(big, 14, lum); big.setColor(0xF5FFFFFF); digits(c, time, x0, baseline, big, cl, track); return; }
        if (showDate) {
            small.setTextSize(Ui.px(15));
            small.setLetterSpacing(0.28f);
            small.setTextAlign(Paint.Align.CENTER);
            small.setColor(0xE6FFFFFF);
            shade(small, 6, lum);
            c.drawText(DateFormat.format("EEEE · d MMMM", k).toString().toUpperCase(Locale.US), cx, top, small);
            small.setLetterSpacing(0f);
            small.setTextAlign(Paint.Align.LEFT);
        }
        if (sw > 0) sideStack(c, k, x0 + tw + Ui.px(14), baseline - cap, baseline, Ui.px(22), lum);
        if (showWeather) {
            boolean d = showDate;
            showDate = false;
            infoLine(c, "", cx, baseline + Ui.px(46), 18, lum, true);
            showDate = d;
        }
        if (showRange && !rangeText().isEmpty()) {
            small.setTextSize(Ui.px(14));
            small.setTextAlign(Paint.Align.CENTER);
            small.setColor(0xB3FFFFFF);
            c.drawText(rangeText(), cx, baseline + Ui.px(74), small);
            small.setTextAlign(Paint.Align.LEFT);
        }
    }

    /** Block: heavy, tightly packed digits low on the screen, details small in the top left. */
    void block(Canvas c, Calendar k, int w, int h, boolean front, float lum, int sx, int sy) {
        String time = DateFormat.format("h:mm", k).toString();
        big.setTypeface(heavy);
        big.setTextSize(h * 0.50f);
        big.setTextScaleX(0.92f);
        float cl = cell(big), track = -cl * 0.06f, cap = big.getTextSize() * 0.70f;
        float x0 = w * 0.05f + sx, baseline = h * 0.93f + sy;
        if (!front) { shade(big, 18, lum); big.setColor(0xF5FFFFFF); digits(c, time, x0, baseline, big, cl, track); return; }
        float tw = width(time, big, cl, track);
        sideStack(c, k, x0 + tw + Ui.px(16), baseline - cap, baseline, Ui.px(26), lum);
        infoLine(c, DateFormat.format("EEEE, d MMMM", k).toString(), x0 + Ui.px(6), baseline - cap - Ui.px(24), 19, lum, false);
    }

    /** Corner: a compact clock top right, everything aligned to the right edge. */
    void corner(Canvas c, Calendar k, int w, int h, boolean front, float lum, int sx, int sy) {
        String time = DateFormat.format("h:mm", k).toString();
        big.setTypeface(bold);
        big.setTextSize(h * 0.19f);
        float cl = cell(big), track = -big.getTextSize() * 0.02f, cap = big.getTextSize() * 0.70f;
        float right = w * 0.94f + sx, tw = width(time, big, cl, track);
        side.setTextSize(Ui.px(18));
        float sw = (showAmpm || showSecs) ? Math.max(side.measureText("PM"), cell(side) * 2) + Ui.px(10) : 0;
        float x0 = right - tw - sw, baseline = h * 0.10f + cap + sy;
        if (!front) { shade(big, 12, lum); big.setColor(0xF5FFFFFF); digits(c, time, x0, baseline, big, cl, track); return; }
        if (sw > 0) sideStack(c, k, x0 + tw + Ui.px(10), baseline - cap, baseline, Ui.px(18), lum);
        // details right aligned: measure, then draw from the left of their width
        String date = DateFormat.format("EEEE, d MMMM", k).toString();
        float y = baseline + Ui.px(36);
        small.setTextSize(Ui.px(17));
        if (showDate) {
            small.setTextAlign(Paint.Align.RIGHT);
            small.setColor(0xF2FFFFFF);
            shade(small, 6, lum);
            c.drawText(date, right, y, small);
            small.setTextAlign(Paint.Align.LEFT);
            y += Ui.px(28);
        }
        if (showWeather && !weatherText().isEmpty()) {
            float icon = Ui.px(17 * 1.4f), igap = Ui.px(17 * 0.55f);
            small.setTextSize(Ui.px(17));
            float ww = icon + igap + small.measureText(weatherText());
            boolean d = showDate;
            showDate = false;
            infoLine(c, "", right - ww, y, 17, lum, false);
            showDate = d;
            y += Ui.px(24);
        }
        if (showRange && !rangeText().isEmpty()) {
            small.setTextSize(Ui.px(13));
            small.setTextAlign(Paint.Align.RIGHT);
            small.setColor(0xB3FFFFFF);
            c.drawText(rangeText(), right, y, small);
            small.setTextAlign(Paint.Align.LEFT);
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
