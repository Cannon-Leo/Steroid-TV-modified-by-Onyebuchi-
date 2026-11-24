package tv.steroid.home;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.TextView;

/** Shared type, sizing and small view helpers. */
final class Ui {
    static Typeface serif, serifItalic, sans, sansMedium, mono;
    static float dp;

    static void init(Context c) {
        dp = c.getResources().getDisplayMetrics().density;
        Config.load(c);
        Stations.load(c);
        Places.load(c);
        if (serif != null) return;
        AssetManager a = c.getAssets();
        serif = Typeface.createFromAsset(a, "fonts/serif.ttf");
        serifItalic = Typeface.createFromAsset(a, "fonts/serif-italic.ttf");
        sans = new Typeface.Builder(a, "fonts/sans.ttf").setFontVariationSettings("'wght' 400").build();
        sansMedium = new Typeface.Builder(a, "fonts/sans.ttf").setFontVariationSettings("'wght' 600").build();
        mono = Typeface.createFromAsset(a, "fonts/mono.ttf");
    }

    static int px(float v) { return Math.round(v * dp); }

    static TextView text(Context c, Typeface tf, float sp, int color) {
        TextView t = new TextView(c);
        t.setTypeface(tf);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        return t;
    }

    static TextView label(Context c, String s, int color) {
        TextView t = text(c, mono, 11.5f, color); // 10 foot legibility: never below 11.5
        t.setLetterSpacing(0.18f);
        t.setSingleLine(true);                     // long labels end in … instead of wrapping into the row
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        t.setShadowLayer(px(6), 0, px(1), 0xB3000000);
        t.setText(s.toUpperCase());
        return t;
    }

    static GradientDrawable round(int color, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(px(radiusDp));
        return g;
    }

    static void lift(View v, boolean f, float scale) {
        v.animate().scaleX(f ? scale : 1f).scaleY(f ? scale : 1f)
            .translationZ(0).setDuration(180).start();
    }

    /**
     * Focus frame: the focus ring sits OUTSIDE the content (a gap, then a 2.5dp white ring), and the
     * whole frame zooms evenly, so app art is never covered or squeezed. Returns the focusable outer view;
     * put the card's content in the returned frame's child (frame.getChildAt(0)).
     */
    static android.widget.FrameLayout frame(Context c, float radiusDp, float scale, Runnable onFocus) {
        android.widget.FrameLayout outer = new android.widget.FrameLayout(c);
        outer.setFocusable(true);
        outer.setClipChildren(false);
        int gap = px(4);
        outer.setPadding(gap, gap, gap, gap);
        GradientDrawable ring = new GradientDrawable();
        ring.setCornerRadius(px(radiusDp + 4));
        ring.setColor(0x00000000);
        outer.setBackground(ring);
        android.widget.FrameLayout inner = new android.widget.FrameLayout(c);
        inner.setClipToOutline(true);
        inner.setBackground(round(0x26FFFFFF, radiusDp));
        outer.addView(inner, new android.widget.FrameLayout.LayoutParams(-1, -1));
        Selector.shape(outer, px(radiusDp + 4), true); // the shared focus ring traces this exact shape
        outer.setOnFocusChangeListener((v, f) -> {
            lift(v, f, scale);
            if (f && onFocus != null) onFocus.run();
        });
        return outer;
    }

    static android.widget.FrameLayout inner(View frame) { return (android.widget.FrameLayout) ((android.view.ViewGroup) frame).getChildAt(0); }

    static float dpWidth(Context c) { return c.getResources().getDisplayMetrics().widthPixels / dp; }

    static int withAlpha(int color, int alpha) { return (color & 0x00FFFFFF) | (alpha << 24); }
}
