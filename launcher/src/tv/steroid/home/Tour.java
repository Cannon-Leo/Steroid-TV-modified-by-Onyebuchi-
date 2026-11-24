package tv.steroid.home;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * A guided tour over any screen: the page dims, a spotlight glides to each part being explained,
 * and a card says what it is and how to use it with the remote. Left and right (or Back and Next)
 * move between steps, Skip ends it, and some steps let you try the thing for real.
 */
final class Tour extends FrameLayout {
    static final class Step {
        final Supplier<View> target; final String kicker, title, body, tryLabel; final Runnable tryIt;
        Step(Supplier<View> target, String kicker, String title, String body) { this(target, kicker, title, body, null, null); }
        Step(Supplier<View> target, String kicker, String title, String body, String tryLabel, Runnable tryIt) {
            this.target = target; this.kicker = kicker; this.title = title; this.body = body; this.tryLabel = tryLabel; this.tryIt = tryIt;
        }
    }

    final Activity act;
    final List<Step> steps;
    final ViewGroup page;   // focus is held away from the page while the tour runs
    final Runnable done;
    int at;
    final RectF hole = new RectF(), from = new RectF(), to = new RectF();
    boolean hasHole;
    float holeAlpha;
    final Paint dim = new Paint(Paint.ANTI_ALIAS_FLAG), ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    final Path path = new Path();
    final LinearLayout card, dots;
    final TextView kicker, title, body;
    final TextView back, next, skip, tryBtn;
    ValueAnimator anim;
    boolean paused; // while the user tries something for real

    Tour(Activity a, ViewGroup page, List<Step> steps, Runnable done) {
        super(a);
        act = a; this.page = page; this.steps = steps; this.done = done;
        setWillNotDraw(false);
        setClickable(true);
        dim.setColor(0xC7000000);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(Ui.px(2));
        ring.setColor(0xE6FFFFFF);

        card = new LinearLayout(a);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(Ui.px(28), Ui.px(24), Ui.px(28), Ui.px(22));
        GradientDrawable bg = Ui.round(0xF2141418, 24);
        bg.setStroke(Ui.px(1), 0x33FFFFFF);
        card.setBackground(bg);
        card.setClipChildren(false);
        card.setClipToPadding(false);
        kicker = Ui.label(a, "", 0xFF8FB8FF);
        card.addView(kicker);
        title = Ui.text(a, Ui.serif, 30, 0xFFFFFFFF);
        title.setPadding(0, Ui.px(8), 0, Ui.px(6));
        card.addView(title);
        body = Ui.text(a, Ui.sans, 16, 0xD9FFFFFF);
        body.setLineSpacing(Ui.px(4), 1f);
        card.addView(body);

        dots = new LinearLayout(a);
        dots.setPadding(0, Ui.px(18), 0, Ui.px(16));
        card.addView(dots);

        LinearLayout btns = new LinearLayout(a);
        btns.setGravity(Gravity.CENTER_VERTICAL);
        btns.setClipChildren(false);
        skip = button("Skip tour", false);
        btns.addView(skip, wrap(0));
        btns.addView(new View(a), new LinearLayout.LayoutParams(0, 1, 1));
        tryBtn = button("", true);
        btns.addView(tryBtn, wrap(12));
        back = button("Back", false);
        btns.addView(back, wrap(12));
        next = button("Next", true);
        btns.addView(next, wrap(12));
        card.addView(btns);
        addView(card, new LayoutParams(Ui.px(620), -2));

        skip.setOnClickListener(v -> finish());
        back.setOnClickListener(v -> go(at - 1));
        next.setOnClickListener(v -> { if (at == steps.size() - 1) finish(); else go(at + 1); });
        tryBtn.setOnClickListener(v -> {
            Step s = steps.get(at);
            if (s.tryIt == null) return;
            paused = true;
            setVisibility(GONE);
            page.setDescendantFocusability(FOCUS_AFTER_DESCENDANTS);
            s.tryIt.run();
        });
    }

    LinearLayout.LayoutParams wrap(int leftDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, Ui.px(46));
        lp.leftMargin = Ui.px(leftDp);
        return lp;
    }

    TextView button(String label, boolean primary) {
        TextView b = Ui.text(act, Ui.sansMedium, 16, primary ? 0xFF0E0E12 : 0xFFFFFFFF);
        b.setText(label);
        b.setGravity(Gravity.CENTER);
        b.setFocusable(true);
        b.setPadding(Ui.px(22), 0, Ui.px(22), 0);
        GradientDrawable g = Ui.round(primary ? 0xFFFFFFFF : 0x1FFFFFFF, 23);
        b.setBackground(g);
        b.setOnFocusChangeListener((v, f) -> g.setColor(primary ? (f ? 0xFFFFFFFF : 0xE6FFFFFF) : (f ? 0x40FFFFFF : 0x1FFFFFFF)));
        Selector.shape(b, Ui.px(23), Ui.px(3));
        return b;
    }

    // ---------- show / move ----------

    static Tour start(Activity a, FrameLayout root, View selector, ViewGroup page, List<Step> steps, Runnable done) {
        Tour t = new Tour(a, page, steps, done);
        page.setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);
        int idx = selector == null ? root.getChildCount() : root.indexOfChild(selector);
        root.addView(t, Math.max(0, idx), new LayoutParams(-1, -1)); // under the focus ring, so it rings the buttons
        t.setAlpha(0f);
        t.animate().alpha(1f).setDuration(260).start();
        t.post(() -> t.go(0));
        return t;
    }

    /** After a "try it" step: the user comes back and the tour carries on. */
    void resume() {
        if (!paused) return;
        paused = false;
        setVisibility(VISIBLE);
        page.setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);
        post(() -> go(Math.min(at + 1, steps.size() - 1)));
    }

    void go(int i) {
        if (i < 0 || i >= steps.size()) return;
        at = i;
        Step s = steps.get(i);
        kicker.setText((i + 1) + " of " + steps.size() + (s.kicker.isEmpty() ? "" : " · " + s.kicker));
        title.setText(s.title);
        body.setText(s.body);
        back.setVisibility(i == 0 ? GONE : VISIBLE);
        next.setText(i == steps.size() - 1 ? "Done" : "Next");
        skip.setVisibility(i == steps.size() - 1 ? INVISIBLE : VISIBLE);
        tryBtn.setVisibility(s.tryIt == null ? GONE : VISIBLE);
        tryBtn.setText(s.tryLabel == null ? "Try it" : s.tryLabel);
        dots.removeAllViews();
        for (int k = 0; k < steps.size(); k++) {
            View d = new View(act);
            d.setBackground(Ui.round(k == i ? 0xFFFFFFFF : 0x40FFFFFF, 4));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.px(k == i ? 22 : 8), Ui.px(8));
            lp.rightMargin = Ui.px(6);
            dots.addView(d, lp);
        }
        View target = s.target == null ? null : s.target.get();
        if (target != null && target.isShown()) {
            ScrollView sv = scrollerOf(target);
            if (sv != null) {
                android.graphics.Rect r = new android.graphics.Rect(0, 0, target.getWidth(), target.getHeight());
                ((ViewGroup) sv.getChildAt(0)).offsetDescendantRectToMyCoords(target, r);
                int y = Math.max(0, r.top - Ui.px(140));
                sv.smoothScrollTo(0, y);
            }
            postDelayed(() -> spot(target), sv == null ? 0 : 380);
        } else spot(null);
        (s.tryIt != null ? tryBtn : next).requestFocus();
    }

    static ScrollView scrollerOf(View v) {
        for (ViewParent p = v.getParent(); p != null; p = p.getParent()) if (p instanceof ScrollView) return (ScrollView) p;
        return null;
    }

    void spot(View target) {
        from.set(hole);
        boolean had = hasHole;
        if (target != null) {
            int[] me = new int[2], it = new int[2];
            getLocationInWindow(me);
            target.getLocationInWindow(it);
            float pad = Ui.px(10);
            to.set(it[0] - me[0] - pad, it[1] - me[1] - pad, it[0] - me[0] + target.getWidth() * target.getScaleX() + pad,
                it[1] - me[1] + target.getHeight() * target.getScaleY() + pad);
            to.intersect(Ui.px(8), Ui.px(8), getWidth() - Ui.px(8), getHeight() - Ui.px(8));
            if (!had) from.set(to.centerX(), to.centerY(), to.centerX(), to.centerY());
        } else to.set(getWidth() / 2f, getHeight() / 2f, getWidth() / 2f, getHeight() / 2f);
        hasHole = true;
        final float a0 = holeAlpha, a1 = target == null ? 0f : 1f;
        if (anim != null) anim.cancel();
        anim = ValueAnimator.ofFloat(0, 1);
        anim.setDuration(420);
        anim.setInterpolator(new DecelerateInterpolator(2));
        anim.addUpdateListener(v -> {
            float f = (float) v.getAnimatedValue();
            hole.set(from.left + (to.left - from.left) * f, from.top + (to.top - from.top) * f,
                from.right + (to.right - from.right) * f, from.bottom + (to.bottom - from.bottom) * f);
            holeAlpha = a0 + (a1 - a0) * f;
            invalidate();
        });
        anim.start();
        placeCard(target == null ? null : to);
    }

    /** Card goes where there is room: under the spotlight, above it, or in the middle. */
    void placeCard(RectF t) {
        card.measure(MeasureSpec.makeMeasureSpec(Ui.px(620), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(getHeight(), MeasureSpec.AT_MOST));
        int ch = card.getMeasuredHeight(), cw = Ui.px(620), m = Ui.px(28);
        float x, y;
        if (t == null) { x = (getWidth() - cw) / 2f; y = (getHeight() - ch) / 2f; }
        else {
            x = Math.max(m, Math.min(getWidth() - cw - m, t.centerX() - cw / 2f));
            if (getHeight() - t.bottom > ch + m * 2) y = t.bottom + m;
            else if (t.top > ch + m * 2) y = t.top - ch - m;
            else { y = getHeight() - ch - m; x = t.centerX() > getWidth() / 2f ? m : getWidth() - cw - m; }
        }
        card.animate().translationX(x).translationY(y).setDuration(card.getTranslationY() == 0 ? 0 : 380)
            .setInterpolator(new DecelerateInterpolator(2)).start();
    }

    @Override protected void onDraw(Canvas c) {
        path.reset();
        path.setFillType(Path.FillType.EVEN_ODD);
        path.addRect(0, 0, getWidth(), getHeight(), Path.Direction.CW);
        if (hasHole && holeAlpha > 0.01f) {
            float r = Ui.px(22);
            path.addRoundRect(hole, r, r, Path.Direction.CW);
        }
        c.drawPath(path, dim);
        if (hasHole && holeAlpha > 0.01f) {
            ring.setAlpha((int) (230 * holeAlpha));
            c.drawRoundRect(hole, Ui.px(22), Ui.px(22), ring);
        }
    }

    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        if (e.getAction() == KeyEvent.ACTION_DOWN && e.getKeyCode() == KeyEvent.KEYCODE_BACK) { finish(); return true; }
        return super.dispatchKeyEvent(e);
    }

    void finish() {
        page.setDescendantFocusability(FOCUS_AFTER_DESCENDANTS);
        animate().alpha(0f).setDuration(200).withEndAction(() -> {
            ViewGroup p = (ViewGroup) getParent();
            if (p != null) p.removeView(this);
        }).start();
        if (done != null) done.run();
    }
}
