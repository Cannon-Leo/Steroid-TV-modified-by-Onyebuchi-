package tv.steroid.home;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.SystemClock;
import android.view.View;
import android.widget.FrameLayout;
import java.util.WeakHashMap;

/**
 * The one focus ring for a whole screen. It lives on its own layer above everything, so no row,
 * scroll edge or screen edge can clip it, and it glides from item to item, taking the exact shape of
 * what is focused: a circle for round buttons, a pill for pills, the card's own corners for cards,
 * always with the same small gap outside the item. Costs one rounded stroke; it only redraws while moving.
 */
class Selector extends View {
    static final WeakHashMap<View, float[]> SHAPES = new WeakHashMap<>(); // {radiusPx (or -1 = circle), flush 0/1}

    /** Declare an item's shape. flush: the item's bounds already include the gap (Ui.frame style). */
    static void shape(View v, float radiusPx, boolean flush) { SHAPES.put(v, new float[]{radiusPx, flush ? 1 : 0, -1}); }
    /** Same, with a wider gap between the item and the ring (for large cards). */
    static void shape(View v, float radiusPx, float gapPx) { SHAPES.put(v, new float[]{radiusPx, 0, gapPx}); }
    static void circle(View v, boolean flush) { shape(v, -1, flush); }

    /** The ring traces this child instead of the focused view (for cards where only the picture zooms). */
    static final WeakHashMap<View, View> VISUAL = new WeakHashMap<>();
    static void visual(View focusable, View shown, float radiusPx) { VISUAL.put(focusable, shown); shape(shown, radiusPx, false); }
    /** Same, with the ring sitting gapPx outside the shown view (1px: the ring hugs the zoomed picture). */
    static void visual(View focusable, View shown, float radiusPx, float gapPx) { VISUAL.put(focusable, shown); shape(shown, radiusPx, gapPx); }

    static Selector attach(Activity a, FrameLayout root) {
        Selector s = new Selector(a);
        root.addView(s, new FrameLayout.LayoutParams(-1, -1));
        root.getViewTreeObserver().addOnGlobalFocusChangeListener((o, n) -> s.kick());
        root.getViewTreeObserver().addOnScrollChangedListener(s::kick);
        // layout changes (a card swapped, a row rebuilt) move items without a focus change: follow them
        root.getViewTreeObserver().addOnGlobalLayoutListener(s::relayout);
        return s;
    }

    static float gap() { return Ui.px(4); } // a method: Ui.dp is set per screen
    final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    final RectF cur = new RectF(), want = new RectF();
    final int[] loc = new int[2], mine = new int[2];
    float curR, wantR, alpha;
    boolean placed;
    long busyUntil;

    Selector(Activity a) {
        super(a);
        setFocusable(false);
        setClickable(false);
        setWillNotDraw(false);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(Ui.px(2.5f));
        ring.setColor(0xFFFFFFFF);
    }

    /** After a layout pass: only wake up if the focused item actually moved (cheap check). */
    final RectF probe = new RectF();
    void relayout() {
        View f = getRootView().findFocus();
        if (f == null || !placed) return;
        RectF old = new RectF(want);
        float oldR = wantR;
        if (target(f) && (Math.abs(want.left - cur.left) + Math.abs(want.top - cur.top) + Math.abs(want.right - cur.right)
            + Math.abs(want.bottom - cur.bottom) > 0.6f)) kick();
        else { want.set(old); wantR = oldR; }
    }

    void kick() { busyUntil = SystemClock.uptimeMillis() + 720; postInvalidateOnAnimation(); }

    // light sweep: a soft band of light crosses the item once when it gets focus (Apple style shine)
    final Paint sweep = new Paint(Paint.ANTI_ALIAS_FLAG);
    final android.graphics.Path clip = new android.graphics.Path();
    View last;
    long sweepAt;
    static final long SWEEP_MS = 560;

    @Override protected void onDraw(Canvas c) {
        View f = getRootView().findFocus();
        boolean show = f != null && f != this && f.isShown() && target(f);
        float aWant = show ? 1f : 0f;
        alpha += (aWant - alpha) * 0.35f;
        if (show && (!placed || alpha < 0.05f)) { cur.set(want); curR = wantR; placed = true; }
        else if (show) {
            float k = 0.32f; // glide: close a third of the gap each frame
            cur.left += (want.left - cur.left) * k;
            cur.top += (want.top - cur.top) * k;
            cur.right += (want.right - cur.right) * k;
            cur.bottom += (want.bottom - cur.bottom) * k;
            curR += (wantR - curR) * k;
        }
        if (show && f != last) { last = f; sweepAt = SystemClock.uptimeMillis() + 90; } // starts as the ring lands
        long st = SystemClock.uptimeMillis() - sweepAt;
        if (show && st >= 0 && st < SWEEP_MS) {
            float t = st / (float) SWEEP_MS;
            float gap = gap(), w = cur.width(), x = cur.left - w * 0.4f + (w * 1.8f) * t;
            clip.reset();
            float r0 = Math.max(0, Math.min(curR, Math.min(cur.width(), cur.height()) / 2) - gap);
            clip.addRoundRect(cur.left + gap, cur.top + gap, cur.right - gap, cur.bottom - gap, r0, r0, android.graphics.Path.Direction.CW);
            sweep.setShader(new android.graphics.LinearGradient(x - w * 0.25f, cur.top, x + w * 0.25f, cur.bottom,
                new int[]{0x00FFFFFF, 0x30FFFFFF, 0x00FFFFFF}, null, android.graphics.Shader.TileMode.CLAMP));
            c.save();
            c.clipPath(clip);
            c.drawRect(cur, sweep);
            c.restore();
        }
        if (alpha > 0.02f) {
            ring.setAlpha((int) (255 * alpha));
            float half = ring.getStrokeWidth() / 2;
            float r = Math.min(curR, Math.min(cur.width(), cur.height()) / 2);
            c.drawRoundRect(cur.left + half, cur.top + half, cur.right - half, cur.bottom - half,
                Math.max(0, r - half), Math.max(0, r - half), ring);
        }
        boolean moving = Math.abs(cur.left - want.left) + Math.abs(cur.top - want.top)
            + Math.abs(cur.right - want.right) + Math.abs(cur.bottom - want.bottom) > 0.6f
            || Math.abs(alpha - aWant) > 0.02f;
        if (moving || SystemClock.uptimeMillis() < busyUntil) postInvalidateOnAnimation();
    }

    /** Where the ring should be for this item, in this layer's coordinates. */
    boolean target(View focused) {
        View v = VISUAL.get(focused);
        View f = v != null ? v : focused;
        f.getLocationInWindow(loc);
        getLocationInWindow(mine);
        float sx = f.getScaleX(), sy = f.getScaleY();
        float l = loc[0] - mine[0], t = loc[1] - mine[1];
        want.set(l, t, l + f.getWidth() * sx, t + f.getHeight() * sy);
        if (want.width() < 2 || want.height() < 2) return false;
        float[] s = SHAPES.get(f);
        float r, gapOverride = -1;
        boolean flush;
        if (s != null) { r = s[0]; flush = s[1] > 0; if (s.length > 2) gapOverride = s[2]; }
        else {
            flush = false;
            Drawable bg = f.getBackground();
            r = Ui.px(12);
            if (bg instanceof Glass) r = ((Glass) bg).isOval() ? -1 : ((Glass) bg).cornerRadiusPx();
            else if (bg instanceof GradientDrawable) {
                GradientDrawable g = (GradientDrawable) bg;
                if (g.getShape() == GradientDrawable.OVAL) r = -1;
                else { float cr = g.getCornerRadius(); r = cr > 0 ? cr : Ui.px(6); }
            }
        }
        if (!flush) {
            // one gap everywhere, so the ring hugs every card, button and chip the same way;
            // a smaller gap is allowed (pictures that zoom up to the ring), never a larger one
            float gap = gapOverride > 0 ? Math.min(gapOverride, gap()) : gap();
            want.inset(-gap, -gap);
            if (r >= 0) r += gap;
        }
        wantR = r < 0 ? Math.min(want.width(), want.height()) / 2 : r * sx;
        return true;
    }

    /** Remove a view from the shape table (not needed normally; views are weakly held). */
    static void forget(View v) { SHAPES.remove(v); }
}
