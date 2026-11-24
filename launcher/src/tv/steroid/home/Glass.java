package tv.steroid.home;

import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.view.View;

/**
 * Frosted glass, the light way. Android 11 has no live blur, so the background photo is blurred ONCE
 * when it changes (a 240 x 135 copy, about 130 KB), and each glass panel paints the matching patch of
 * that copy, stretched (which smooths it further), under a dark tint, a bright hairline edge and a soft
 * top highlight. No per frame blur; panels only repaint when the photo changes or the page scrolls.
 */
final class Glass extends Drawable {
    static Bitmap frost;              // blurred copy of what the background shows, screen proportioned
    static final java.util.Set<View> owners = java.util.Collections.newSetFromMap(new java.util.WeakHashMap<View, Boolean>());
    static final int FW = 240, FH = 135;

    final View owner;
    final float radius;
    final boolean oval;
    final Paint img = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    final Paint tint = new Paint(Paint.ANTI_ALIAS_FLAG), edge = new Paint(Paint.ANTI_ALIAS_FLAG), shine = new Paint(Paint.ANTI_ALIAS_FLAG);
    final Matrix m = new Matrix();
    final int[] loc = new int[2];
    final RectF r = new RectF();
    int tintColor;
    boolean useFrost;
    BitmapShader shader;
    Bitmap shaderFor;

    /** Put glass behind a view. radiusDp < 0 means a circle. */
    static Glass apply(View v, float radiusDp, int tintColor) {
        Glass g = new Glass(v, radiusDp, tintColor);
        g.useFrost = v.getContext() instanceof Home; // only the home screen shows the photo behind
        v.setBackground(g);
        owners.add(v);
        return g;
    }

    Glass(View owner, float radiusDp, int tintColor) {
        this.owner = owner;
        this.oval = radiusDp < 0;
        this.radius = Ui.px(Math.max(0, radiusDp));
        this.tintColor = tintColor;
        tint.setColor(tintColor);
        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeWidth(Ui.px(1));
        edge.setColor(0x38FFFFFF);
    }

    void tint(int c) { tintColor = c; tint.setColor(c); invalidateSelf(); }

    /** Called by the background when its photo changes: blur once, then repaint the panels. */
    static void update(Bitmap shown, int screenW, int screenH) { frost = make(shown, screenW, screenH); refresh(); }

    /** The blur work; safe to run off the main thread. */
    static Bitmap make(Bitmap shown, int screenW, int screenH) {
        if (shown == null || screenW <= 0 || screenH <= 0) return null;
        try {
            // centre crop to the screen's proportions, shrink hard, then box blur the tiny copy a few times
            float s = Math.max((float) screenW / shown.getWidth(), (float) screenH / shown.getHeight());
            Bitmap small = Bitmap.createBitmap(FW, FH, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(small);
            float k = (float) FW / screenW;
            c.translate(FW / 2f, FH / 2f);
            c.scale(s * k, s * k);
            c.translate(-shown.getWidth() / 2f, -shown.getHeight() / 2f);
            c.drawBitmap(shown, 0, 0, new Paint(Paint.FILTER_BITMAP_FLAG));
            blur(small, 3);
            blur(small, 3);
            return small;
        } catch (Throwable t) { return null; }
    }

    static void refresh() { for (View v : owners) if (v.isAttachedToWindow()) v.invalidate(); }

    /** Simple separable box blur on a tiny bitmap (runs once per photo, a few milliseconds). */
    static void blur(Bitmap b, int rad) {
        int w = b.getWidth(), h = b.getHeight();
        int[] p = new int[w * h], q = new int[w * h];
        b.getPixels(p, 0, w, 0, 0, w, h);
        for (int pass = 0; pass < 2; pass++) {
            int[] src = pass == 0 ? p : q, dst = pass == 0 ? q : p;
            int len = pass == 0 ? w : h, lines = pass == 0 ? h : w;
            for (int line = 0; line < lines; line++) {
                for (int i = 0; i < len; i++) {
                    int rr = 0, gg = 0, bb = 0, n = 0;
                    for (int d = -rad; d <= rad; d++) {
                        int j = Math.max(0, Math.min(len - 1, i + d));
                        int c = pass == 0 ? src[line * w + j] : src[j * w + line];
                        rr += (c >> 16) & 255; gg += (c >> 8) & 255; bb += c & 255; n++;
                    }
                    int idx = pass == 0 ? line * w + i : i * w + line;
                    dst[idx] = 0xFF000000 | ((rr / n) << 16) | ((gg / n) << 8) | (bb / n);
                }
            }
        }
        b.setPixels(p, 0, w, 0, 0, w, h);
    }

    @Override public void draw(Canvas c) {
        Rect b = getBounds();
        r.set(b);
        float rad = oval ? Math.min(r.width(), r.height()) / 2 : radius;
        Bitmap f = useFrost ? frost : null;
        if (f != null) {
            if (shader == null || shaderFor != f) {
                shader = new BitmapShader(f, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                shaderFor = f;
            }
            // map the frost copy onto the screen, then shift by where this panel sits on screen
            owner.getLocationOnScreen(loc);
            View root = owner.getRootView();
            float k = (float) root.getWidth() / FW;
            m.setScale(k, (float) root.getHeight() / FH);
            m.postTranslate(-loc[0], -loc[1]);
            shader.setLocalMatrix(m);
            img.setShader(shader);
            c.drawRoundRect(r, rad, rad, img);
        }
        c.drawRoundRect(r, rad, rad, tint);
        // soft highlight along the top, like light catching the glass
        shine.setShader(new LinearGradient(0, r.top, 0, r.top + r.height() * 0.6f, 0x1FFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        c.drawRoundRect(r, rad, rad, shine);
        float h = edge.getStrokeWidth() / 2;
        c.drawRoundRect(r.left + h, r.top + h, r.right - h, r.bottom - h, Math.max(0, rad - h), Math.max(0, rad - h), edge);
    }

    @Override public void getOutline(Outline o) {
        Rect b = getBounds();
        if (oval) o.setOval(b); else o.setRoundRect(b, radius);
    }

    @Override public void setAlpha(int a) {}
    @Override public void setColorFilter(ColorFilter cf) {}
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }

    /** So the shared focus ring can read this shape like any other background. */
    float cornerRadiusPx() { return radius; }
    boolean isOval() { return oval; }
}
