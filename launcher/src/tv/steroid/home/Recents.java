package tv.steroid.home;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Recent apps, HyperOS style: an overlay over whatever is on screen, the apps you used lately as a
 * stack of cards, the newest in front. Left and right cycle, Up flicks the card away (closes that app),
 * OK opens, hold OK for app options, Down reaches Close all. Memory in use sits top right and shows
 * what closing freed. Opened by holding Home on the remote (HomeKeys) and from the top icon.
 * Needs usage access, granted over adb: appops set tv.steroid.home GET_USAGE_STATS allow
 */
public class Recents extends Activity {
    static final long DAY = 86400000L;
    static final float W = 400, H = 225;   // the front card, dp (16:9)

    static final class Item { Apps.App app; long used; LinearLayout view; View ring, shade; TextView name, when; ImageView icon; }

    /** A soft, blurred picture of the home screen, when Recent apps opens over it. Android 11 can't blur
     *  another app's window, so over other apps the glass is a denser frosted tint instead. */
    static android.graphics.Bitmap backdrop;
    static long backdropAt;

    /** Every way in (top icon, control centre, holding Home) comes through here. */
    static void open(Context c) {
        Home hm = Home.live;
        if (hm != null && hm.resumed) {
            try {
                View d = hm.getWindow().getDecorView();
                int w = Math.max(1, d.getWidth() / 10), hh = Math.max(1, d.getHeight() / 10);
                android.graphics.Bitmap b = android.graphics.Bitmap.createBitmap(w, hh, android.graphics.Bitmap.Config.ARGB_8888);
                android.graphics.Canvas cv = new android.graphics.Canvas(b);
                cv.scale(w / (float) d.getWidth(), hh / (float) d.getHeight());
                d.draw(cv);
                // shrink again and scale back up: a wide, smooth blur for almost nothing
                android.graphics.Bitmap tiny = android.graphics.Bitmap.createScaledBitmap(b, Math.max(1, w / 3), Math.max(1, hh / 3), true);
                backdrop = android.graphics.Bitmap.createScaledBitmap(tiny, w * 2, hh * 2, true);
                backdropAt = System.currentTimeMillis();
            } catch (Throwable e) { backdrop = null; }
        } else backdrop = null;
        c.startActivity(new Intent(c, Recents.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION));
    }

    final List<Item> items = new ArrayList<>();
    final Handler h = new Handler(Looper.getMainLooper());
    FrameLayout root, stack, closeAll;
    LinearLayout emptyBox, bottom;
    TextView count, ramFree, ramOf, hint;
    View ramFill;
    GradientDrawable ramFillBg, closeBg;
    int cur;
    long availBefore = -1;
    boolean leaving;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.init(this);
        overridePendingTransition(0, 0);
        root = new FrameLayout(this);
        root.setClipChildren(false);
        // the app behind stays faintly visible: dark glass, deeper at the bottom where the controls are
        boolean blur = backdrop != null && System.currentTimeMillis() - backdropAt < 4000;
        GradientDrawable tint = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, blur
            ? new int[]{0xA60A0B10, 0xB8070809, 0xD9040406}      // over the blurred home screen
            : new int[]{0xE60A0B10, 0xEE07080B, 0xF5040406});    // over another app: denser frosted glass
        if (blur) {
            android.graphics.drawable.BitmapDrawable bd = new android.graphics.drawable.BitmapDrawable(getResources(), backdrop);
            bd.setFilterBitmap(true);
            root.setBackground(new android.graphics.drawable.LayerDrawable(new Drawable[]{bd, tint}));
        } else root.setBackground(tint);
        backdrop = null;

        // ---- top: title and count left, memory right ----
        FrameLayout top = new FrameLayout(this);
        top.setPadding(Ui.px(56), Ui.px(40), Ui.px(56), 0);
        LinearLayout tl = new LinearLayout(this);
        tl.setOrientation(LinearLayout.VERTICAL);
        TextView title = Ui.text(this, Ui.serif, 34, 0xFFFFFFFF);
        title.setText("Recent apps");
        tl.addView(title);
        count = Ui.text(this, Ui.sans, 13, 0xB3FFFFFF);
        count.setPadding(0, Ui.px(2), 0, 0);
        tl.addView(count);
        top.addView(tl, new FrameLayout.LayoutParams(-2, -2, Gravity.START | Gravity.TOP));
        top.addView(ramPill(), new FrameLayout.LayoutParams(-2, -2, Gravity.END | Gravity.TOP));
        root.addView(top, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP));

        // ---- the stack ----
        stack = new Stack(this);
        stack.setClipChildren(false);
        FrameLayout.LayoutParams sl = new FrameLayout.LayoutParams(-1, Ui.px(H + 60), Gravity.CENTER);
        sl.topMargin = -Ui.px(30);   // clear of Close all and its hint below
        root.addView(stack, sl);

        emptyBox = new LinearLayout(this);
        emptyBox.setOrientation(LinearLayout.VERTICAL);
        emptyBox.setGravity(Gravity.CENTER_HORIZONTAL);
        Icon ei = new Icon(this, Icon.RECENTS);
        ei.color(0x80FFFFFF);
        emptyBox.addView(ei, new LinearLayout.LayoutParams(Ui.px(40), Ui.px(40)));
        TextView et = Ui.text(this, Ui.serifItalic, 24, 0xE6FFFFFF);
        et.setText("No recent apps");
        et.setPadding(0, Ui.px(14), 0, Ui.px(4));
        emptyBox.addView(et);
        TextView es = Ui.text(this, Ui.sans, 13, 0xB3FFFFFF);
        es.setText("Apps you open show up here.");
        emptyBox.addView(es);
        emptyBox.setVisibility(View.GONE);
        root.addView(emptyBox, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));

        // ---- bottom: Close all in the middle, the keys on the left ----
        bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setGravity(Gravity.CENTER_HORIZONTAL);
        closeAll = new FrameLayout(this);
        closeAll.setFocusable(true);
        closeAll.setContentDescription("Close all");
        closeBg = new GradientDrawable();
        closeBg.setShape(GradientDrawable.OVAL);
        closeBg.setColor(0x26FFFFFF);
        closeAll.setBackground(closeBg);
        closeAll.addView(new Icon(this, Icon.CLOSE), new FrameLayout.LayoutParams(Ui.px(26), Ui.px(26), Gravity.CENTER));
        closeAll.setOnFocusChangeListener((v, f) -> {
            closeBg.setColor(f ? 0xF2FFFFFF : 0x26FFFFFF);
            ((Icon) closeAll.getChildAt(0)).color(f ? 0xFF111114 : 0xFFFFFFFF);
            closeAll.animate().scaleX(f ? 1.08f : 1f).scaleY(f ? 1.08f : 1f).setDuration(160).start();
            stack.invalidate();
            paint(true);
        });
        closeAll.setOnClickListener(v -> closeEverything());
        closeAll.setOnKeyListener((v, k, e) -> {
            if (e.getAction() == KeyEvent.ACTION_DOWN && k == KeyEvent.KEYCODE_DPAD_UP && !items.isEmpty()) { stack.requestFocus(); return true; }
            return k == KeyEvent.KEYCODE_DPAD_LEFT || k == KeyEvent.KEYCODE_DPAD_RIGHT || k == KeyEvent.KEYCODE_DPAD_DOWN;
        });
        bottom.addView(closeAll, new LinearLayout.LayoutParams(Ui.px(60), Ui.px(60)));
        TextView cl = Ui.text(this, Ui.sansMedium, 12, 0xCCFFFFFF);
        cl.setText("Close all");
        cl.setPadding(0, Ui.px(8), 0, 0);
        bottom.addView(cl);
        // the keys, centred under Close all (never over it)
        hint = Ui.text(this, Ui.sans, 12, 0x99FFFFFF);
        hint.setText("Left and right to browse  ·  Up to close an app  ·  OK to open  ·  hold OK for options");
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, Ui.px(14), 0, 0);
        bottom.addView(hint);
        FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        bl.bottomMargin = Ui.px(22);
        root.addView(bottom, bl);

        setContentView(root);
        root.setAlpha(0f);
        root.animate().alpha(1f).setDuration(180).start();
        load();
        h.post(ramTick);
    }

    @Override protected void onNewIntent(Intent i) { super.onNewIntent(i); load(); }   // held Home again: refresh
    @Override protected void onDestroy() { h.removeCallbacksAndMessages(null); super.onDestroy(); }

    @Override public void onBackPressed() { leave(); }

    void leave() {
        if (leaving) return;
        leaving = true;
        root.animate().alpha(0f).setDuration(140).withEndAction(() -> { super.finish(); overridePendingTransition(0, 0); }).start();
    }

    // ---------- memory ----------

    View ramPill() {
        LinearLayout p = new LinearLayout(this);
        p.setGravity(Gravity.CENTER_VERTICAL);
        p.setPadding(Ui.px(14), Ui.px(10), Ui.px(16), Ui.px(10));
        Glass.apply(p, 22, 0x59101014);   // the same frosted glass as the control centre
        FrameLayout disc = new FrameLayout(this);
        disc.setBackground(Ui.round(0x26FFFFFF, 17));
        disc.addView(new Icon(this, Icon.RAM), new FrameLayout.LayoutParams(Ui.px(18), Ui.px(18), Gravity.CENTER));
        p.addView(disc, new LinearLayout.LayoutParams(Ui.px(34), Ui.px(34)));
        LinearLayout tx = new LinearLayout(this);
        tx.setOrientation(LinearLayout.VERTICAL);
        tx.setPadding(Ui.px(12), 0, Ui.px(14), 0);
        ramFree = Ui.text(this, Ui.sansMedium, 14, 0xFFFFFFFF);
        ramFree.setSingleLine(true);
        tx.addView(ramFree);
        ramOf = Ui.text(this, Ui.sans, 11, 0xB3FFFFFF);
        ramOf.setSingleLine(true);
        tx.addView(ramOf);
        p.addView(tx);
        FrameLayout track = new FrameLayout(this);
        track.setBackground(Ui.round(0x33FFFFFF, 3));
        ramFill = new View(this);
        ramFillBg = Ui.round(0xFF34D399, 3);
        ramFill.setBackground(ramFillBg);
        track.addView(ramFill, new FrameLayout.LayoutParams(0, -1, Gravity.START));
        p.addView(track, new LinearLayout.LayoutParams(Ui.px(84), Ui.px(6)));
        return p;
    }

    static String gb(long bytes) {
        double g = bytes / 1073741824.0;
        return g >= 1 ? String.format(java.util.Locale.US, "%.1f GB", g) : (bytes / 1048576) + " MB";
    }

    final Runnable ramTick = new Runnable() { public void run() { ram(); h.postDelayed(this, 1500); } };

    void ram() {
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        try { ((ActivityManager) getSystemService(ACTIVITY_SERVICE)).getMemoryInfo(mi); } catch (Exception e) { return; }
        if (mi.totalMem <= 0) return;
        float used = 1f - (float) mi.availMem / mi.totalMem;
        ramFree.setText(gb(mi.availMem) + " free");
        if (availBefore >= 0 && mi.availMem - availBefore > 8L * 1048576) {
            ramOf.setText("Freed " + gb(mi.availMem - availBefore));
            ramOf.setTextColor(0xFF6EE7B7);
        } else if (availBefore < 0) {
            ramOf.setText("of " + gb(mi.totalMem) + "  ·  " + Math.round(used * 100) + "% in use");
            ramOf.setTextColor(0xB3FFFFFF);
        }
        ramFillBg.setColor(used < 0.7f ? 0xFF34D399 : used < 0.85f ? 0xFFFBBF24 : 0xFFF87171);
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) ramFill.getLayoutParams();
        int w = Math.round(Ui.px(84) * Math.min(1f, Math.max(0.04f, used)));
        if (lp.width != w) { lp.width = w; ramFill.setLayoutParams(lp); }
    }

    /** Remember the free memory now; the pill shows what closing freed for a few seconds. */
    void measureFreed() {
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        try { ((ActivityManager) getSystemService(ACTIVITY_SERVICE)).getMemoryInfo(mi); availBefore = mi.availMem; } catch (Exception ignored) {}
        h.postDelayed(() -> { availBefore = -1; ram(); }, 6000);
    }

    // ---------- the apps ----------

    void load() {
        stack.removeAllViews();
        items.clear();
        List<Apps.App> all = Apps.all(this);
        UsageStatsManager um = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
        long now = System.currentTimeMillis();
        List<UsageStats> stats = null;
        try { stats = um == null ? null : um.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - DAY * 2, now); } catch (Exception ignored) {}
        if (stats == null || stats.isEmpty()) {
            showEmpty("Usage access is off", "Run scripts/buchi.sh install on the Mac to allow it.");
            return;
        }
        Map<String, Long> last = new HashMap<>();
        for (UsageStats s : stats) if (s.getLastTimeUsed() > 0) {
            Long had = last.get(s.getPackageName());
            last.put(s.getPackageName(), Math.max(s.getLastTimeUsed(), had == null ? 0 : had));
        }
        for (Apps.App a : all) {
            if (a.pkg.equals(getPackageName())) continue;
            Long t = last.get(a.pkg);
            if (t == null || t <= now - DAY || Clear.closedSince(this, a.pkg, t)) continue;   // closed apps stay gone
            Item it = new Item();
            it.app = a;
            it.used = t;
            items.add(it);
        }
        Collections.sort(items, (x, y) -> Long.compare(y.used, x.used));
        while (items.size() > 12) items.remove(items.size() - 1);
        for (Item it : items) build(it);
        cur = 0;
        if (items.isEmpty()) { showEmpty(null, null); return; }
        emptyBox.setVisibility(View.GONE);
        bottom.setVisibility(View.VISIBLE);
        layoutStack(false);
        stack.requestFocus();
        counts();
    }

    void counts() { count.setText(items.size() + (items.size() == 1 ? " app open" : " apps open")); }

    void showEmpty(String t, String s) {
        if (t != null) { ((TextView) emptyBox.getChildAt(1)).setText(t); ((TextView) emptyBox.getChildAt(2)).setText(s); }
        emptyBox.setVisibility(View.VISIBLE);
        emptyBox.setAlpha(0f);
        emptyBox.animate().alpha(1f).setDuration(220).start();
        bottom.setVisibility(View.INVISIBLE);
        count.setText("Nothing open");
    }

    static String ago(long t) {
        long m = (System.currentTimeMillis() - t) / 60000;
        if (m < 1) return "Just now";
        if (m < 60) return m + " min ago";
        return (m / 60) + " h ago";
    }

    void build(Item it) {
        Apps.App a = it.app;
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setClipChildren(false);
        // label above the card: icon, name, when
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(Ui.px(4), 0, 0, Ui.px(10));
        it.icon = new ImageView(this);
        it.icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        head.addView(it.icon, new LinearLayout.LayoutParams(Ui.px(24), Ui.px(24)));
        it.name = Ui.text(this, Ui.sansMedium, 15, 0xFFFFFFFF);
        it.name.setText(a.label);
        it.name.setSingleLine(true);
        it.name.setEllipsize(TextUtils.TruncateAt.END);
        it.name.setPadding(Ui.px(10), 0, Ui.px(8), 0);
        head.addView(it.name, new LinearLayout.LayoutParams(0, -2, 1));
        it.when = Ui.text(this, Ui.sans, 12, 0x99FFFFFF);
        it.when.setText(ago(it.used));
        head.addView(it.when);
        v.addView(head, new LinearLayout.LayoutParams(-1, Ui.px(34)));

        // the card: the app's banner, or its icon on a tile of its own colour
        FrameLayout art = new FrameLayout(this);
        GradientDrawable abg = Ui.round(0xFF22222A, 22);
        art.setBackground(abg);
        art.setClipToOutline(true);
        ImageView banner = new ImageView(this);
        banner.setScaleType(ImageView.ScaleType.CENTER_CROP);
        art.addView(banner, new FrameLayout.LayoutParams(-1, -1));
        ImageView big = new ImageView(this);
        big.setScaleType(ImageView.ScaleType.FIT_CENTER);
        art.addView(big, new FrameLayout.LayoutParams(Ui.px(84), Ui.px(84), Gravity.CENTER));
        it.shade = new View(this);
        it.shade.setBackgroundColor(0xFF000000);
        it.shade.setAlpha(0f);
        art.addView(it.shade, new FrameLayout.LayoutParams(-1, -1));
        it.ring = new View(this);
        GradientDrawable rd = new GradientDrawable();
        rd.setCornerRadius(Ui.px(22));
        rd.setStroke(Ui.px(3), 0xFFFFFFFF);
        it.ring.setBackground(rd);
        it.ring.setAlpha(0f);
        art.addView(it.ring, new FrameLayout.LayoutParams(-1, -1));
        v.addView(art, new LinearLayout.LayoutParams(Ui.px(W), Ui.px(H)));
        it.view = v;
        stack.addView(v, new FrameLayout.LayoutParams(Ui.px(W), Ui.px(H + 34), Gravity.CENTER));
        v.setPivotX(Ui.px(W) / 2f);
        v.setPivotY(Ui.px(34 + H / 2));
        v.setOutlineProvider(null);
        art.setElevation(Ui.px(10));

        Runnable apply = () -> {
            Object[] o = Apps.art.get(a.pkg);
            if (o == null) return;
            if ((Boolean) o[2]) { banner.setImageDrawable((Drawable) o[0]); big.setVisibility(View.GONE); }
            else {
                big.setImageDrawable((Drawable) o[0]);
                int c = (Integer) o[1];
                GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                    new int[]{Ambient.blend(c, 0xFFFFFFFF, 0.10f), Ambient.blend(c, 0xFF000000, 0.45f)});
                g.setCornerRadius(Ui.px(22));
                art.setBackground(g);
            }
        };
        if (Apps.art.get(a.pkg) != null) apply.run();
        else Apps.io.execute(() -> { Apps.load(this, a); Apps.main.post(apply); });
        Apps.io.execute(() -> {
            Drawable ic = null;
            try { ic = getPackageManager().getApplicationIcon(a.pkg); } catch (Exception ignored) {}
            final Drawable f = ic;
            if (f != null) Apps.main.post(() -> it.icon.setImageDrawable(f));
        });
    }

    /** Where each card sits: the current one in front, the rest fanned out behind it, smaller and dimmer. */
    void layoutStack(boolean animate) {
        int n = items.size();
        float w = Ui.px(W);
        for (int i = 0; i < n; i++) {
            Item it = items.get(i);
            int d = i - cur, ad = Math.abs(d);
            float x = d == 0 ? 0 : Math.signum(d) * (w * 0.60f + w * 0.30f * (ad - 1));
            float s = Math.max(0.6f, 1f - 0.13f * ad);
            float a = ad <= 2 ? 1f : ad == 3 ? 0.55f : 0f;
            it.view.setTranslationZ(Ui.px(40 - ad * 8));
            if (animate) it.view.animate().translationX(x).scaleX(s).scaleY(s).alpha(a)
                .setInterpolator(new DecelerateInterpolator(1.6f)).setDuration(260).start();
            else { it.view.setTranslationX(x); it.view.setScaleX(s); it.view.setScaleY(s); it.view.setAlpha(a); }
        }
        paint(animate);
    }

    void paint(boolean animate) {
        boolean focused = stack.hasFocus() || stack.isFocused();
        for (int i = 0; i < items.size(); i++) {
            Item it = items.get(i);
            int ad = Math.abs(i - cur);
            float shade = ad == 0 ? 0f : Math.min(0.62f, 0.30f + 0.12f * ad);
            float ring = ad == 0 && focused ? 1f : 0f;
            float label = ad == 0 ? 1f : ad == 1 ? 0.55f : 0.25f;
            if (animate) {
                it.shade.animate().alpha(shade).setDuration(220).start();
                it.ring.animate().alpha(ring).setDuration(160).start();
                ((View) it.name.getParent()).animate().alpha(label).setDuration(220).start();
            } else { it.shade.setAlpha(shade); it.ring.setAlpha(ring); ((View) it.name.getParent()).setAlpha(label); }
        }
    }

    void move(int by) {
        int to = Math.max(0, Math.min(items.size() - 1, cur + by));
        if (to == cur) {   // at the end: a small nudge says so
            View v = items.get(cur).view;
            v.animate().translationX(by * Ui.px(14)).setDuration(90).withEndAction(() -> layoutStack(true)).start();
            return;
        }
        cur = to;
        layoutStack(true);
    }

    void openCurrent() {
        if (items.isEmpty()) return;
        Item it = items.get(cur);
        it.view.animate().scaleX(1.06f).scaleY(1.06f).setDuration(120).start();
        Apps.open(this, it.app);
        h.postDelayed(() -> { super.finish(); overridePendingTransition(0, 0); }, 150);
    }

    /** Up on a card: it flies up and away, and the app is closed. */
    void closeCurrent() {
        if (items.isEmpty()) return;
        final Item it = items.get(cur);
        measureFreed();
        Clear.one(this, it.app.pkg);
        it.view.animate().translationY(-Ui.px(H * 1.25f)).alpha(0f).setDuration(240)
            .setInterpolator(new DecelerateInterpolator()).withEndAction(() -> {
                stack.removeView(it.view);
                items.remove(it);
                if (items.isEmpty()) { showEmpty(null, null); h.postDelayed(this::leave, 900); return; }
                cur = Math.min(cur, items.size() - 1);
                counts();
                layoutStack(true);
                stack.requestFocus();
            }).start();
    }

    /** Close all: every card flies up, everything is stopped and freed, then back to what you were doing. */
    void closeEverything() {
        List<String> pkgs = new ArrayList<>();
        for (Apps.App a : Apps.all(this)) pkgs.add(a.pkg);
        for (Item it : items) if (!pkgs.contains(it.app.pkg)) pkgs.add(it.app.pkg);
        measureFreed();
        Clear.all(this, pkgs);
        int i = 0;
        for (Item it : items) it.view.animate().translationY(-Ui.px(H * 1.25f)).alpha(0f)
            .setStartDelay(Math.abs(i++ - cur) * 35L).setDuration(260).start();
        h.postDelayed(() -> {
            stack.removeAllViews();
            items.clear();
            showEmpty("All clear", "Every app is closed and playback has stopped.");
        }, 420);
        h.postDelayed(this::leave, 1700);
    }

    void options() {
        if (items.isEmpty()) return;
        Apps.App a = items.get(cur).app;
        Apps.menu(this, a, new String[]{"Close app"}, new Runnable[]{this::closeCurrent}, this::load);
    }

    /** The stack takes the remote's keys itself: one focus stop, the front card is the selection. */
    final class Stack extends FrameLayout {
        Stack(Context c) {
            super(c);
            setFocusable(true);
            setFocusableInTouchMode(true);
            setOnFocusChangeListener((v, f) -> paint(true));
        }
        @Override public boolean onKeyDown(int k, KeyEvent e) {
            switch (k) {
                case KeyEvent.KEYCODE_DPAD_LEFT: move(-1); return true;
                case KeyEvent.KEYCODE_DPAD_RIGHT: move(1); return true;
                case KeyEvent.KEYCODE_DPAD_UP: if (e.getRepeatCount() == 0) closeCurrent(); return true;
                case KeyEvent.KEYCODE_DPAD_DOWN: closeAll.requestFocus(); return true;
                case KeyEvent.KEYCODE_DPAD_CENTER: case KeyEvent.KEYCODE_ENTER:
                    e.startTracking(); return true;
            }
            return super.onKeyDown(k, e);
        }
        @Override public boolean onKeyLongPress(int k, KeyEvent e) {
            if (k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER) { options(); return true; }
            return super.onKeyLongPress(k, e);
        }
        @Override public boolean onKeyUp(int k, KeyEvent e) {
            if ((k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER)) {
                if (e.isTracking() && !e.isCanceled()) openCurrent();
                return true;
            }
            return super.onKeyUp(k, e);
        }
    }
}
