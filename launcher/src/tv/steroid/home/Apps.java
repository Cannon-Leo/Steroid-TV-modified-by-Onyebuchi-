package tv.steroid.home;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.LruCache;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Every app a person can open: TV apps (leanback entry) and phone apps (normal launcher entry),
 * one entry per package. TV apps show their banner; phone apps show their icon on a tile tinted
 * with the icon's own colour, so sideloaded APKs never appear blank.
 */
final class Apps {
    static final class App {
        String pkg, label;
        ComponentName comp;
        boolean tv;
        Intent launch() {
            return new Intent(Intent.ACTION_MAIN).setComponent(comp)
                .addCategory(tv ? Intent.CATEGORY_LEANBACK_LAUNCHER : Intent.CATEGORY_LAUNCHER)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        }
    }

    /** Never shown as apps: system plumbing, other launchers and ourselves. */
    static final Set<String> SKIP = new HashSet<>(Arrays.asList(
        "tv.steroid.home", "com.android.tv.settings", "com.android.settings", "com.google.android.tvlauncher",
        "com.google.android.apps.tv.launcherx", "com.google.android.leanbacklauncher", "com.mitv.tvhome.atv",
        "com.spocky.projengmenu", "news.androidtv.tvapprepo", "com.google.android.tungsten.setupwraith",
        "com.android.vending.billing", "com.google.android.katniss"));

    static final ExecutorService io = Executors.newSingleThreadExecutor();
    static final Handler main = new Handler(Looper.getMainLooper());
    static final LruCache<String, Object[]> art = new LruCache<>(80); // pkg -> {Drawable, Integer tint, Boolean banner}

    static List<App> all(Context c) {
        PackageManager pm = c.getPackageManager();
        Map<String, App> by = new LinkedHashMap<>();
        add(pm, by, Intent.CATEGORY_LEANBACK_LAUNCHER, true);
        add(pm, by, Intent.CATEGORY_LAUNCHER, false);
        List<App> out = new ArrayList<>(by.values());
        Collections.sort(out, (a, b) -> a.label.compareToIgnoreCase(b.label));
        return out;
    }

    static void add(PackageManager pm, Map<String, App> by, String cat, boolean tv) {
        Intent q = new Intent(Intent.ACTION_MAIN).addCategory(cat);
        for (ResolveInfo ri : pm.queryIntentActivities(q, 0)) {
            ActivityInfo ai = ri.activityInfo;
            if (ai == null || SKIP.contains(ai.packageName) || by.containsKey(ai.packageName)) continue;
            if (!ai.enabled || !ai.applicationInfo.enabled) continue;
            App a = new App();
            a.pkg = ai.packageName;
            a.comp = new ComponentName(ai.packageName, ai.name);
            CharSequence l = ri.loadLabel(pm);
            a.label = l == null ? a.pkg : l.toString();
            a.tv = tv;
            by.put(a.pkg, a);
        }
    }

    static App find(List<App> all, String pkg) {
        for (App a : all) if (a.pkg.equals(pkg)) return a;
        return null;
    }

    /** A card for one app. Art loads off the main thread and is cached. Focus ring sits outside the art. */
    static FrameLayout card(Activity ctx, App a, Runnable onFocus, View.OnLongClickListener longPress) {
        FrameLayout card = Ui.frame(ctx, 14, 1.07f, onFocus);
        FrameLayout in = Ui.inner(card);
        GradientDrawable bg = (GradientDrawable) in.getBackground();
        // TV apps: the banner fills the card. Phone apps: a tinted tile with the icon (see apply below)
        ImageView banner = new ImageView(ctx);
        banner.setScaleType(ImageView.ScaleType.CENTER_CROP);
        in.addView(banner, new FrameLayout.LayoutParams(-1, -1));
        View dim = new View(ctx);
        dim.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0x73000000, 0xBF000000}));
        dim.setVisibility(View.GONE);
        in.addView(dim, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout tile = new LinearLayout(ctx);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER);
        ImageView icon = new ImageView(ctx);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        tile.addView(icon, new LinearLayout.LayoutParams(Ui.px(40), Ui.px(40)));
        TextView name = Ui.text(ctx, Ui.sansMedium, 12.5f, 0xFFFFFFFF);
        name.setShadowLayer(Ui.px(4), 0, Ui.px(1), 0xCC000000);
        name.setText(a.label);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        name.setGravity(Gravity.CENTER);
        name.setPadding(Ui.px(8), Ui.px(6), Ui.px(8), 0);
        tile.addView(name, new LinearLayout.LayoutParams(-1, -2));
        in.addView(tile, new FrameLayout.LayoutParams(-1, -1));

        Runnable apply = () -> {
            Object[] o = art.get(a.pkg);
            if (o == null) return;
            if ((Boolean) o[2]) { banner.setImageDrawable((Drawable) o[0]); tile.setVisibility(View.GONE); }
            else {
                // phone app: a clean tile in the icon's own colour, the crisp icon and its name
                icon.setImageDrawable((Drawable) o[0]);
                int c = (Integer) o[1];
                in.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                    new int[]{Ambient.blend(c, 0xFFFFFFFF, 0.08f), Ambient.blend(c, 0xFF000000, 0.35f)}));
                ((GradientDrawable) in.getBackground()).setCornerRadius(Ui.px(14));
            }
        };
        if (art.get(a.pkg) != null) apply.run();
        else io.execute(() -> { load(ctx, a); main.post(apply); });
        card.setOnClickListener(v -> open(ctx, a));
        if (longPress != null) card.setOnLongClickListener(longPress);
        return card;
    }

    /** For pickers: open the chosen app (the picker's context is the activity). */
    static Activity last;
    static void open0(App a) { if (last != null) open(last, a); }

    static void open(Activity ctx, App a) {
        try { ctx.startActivity(a.launch()); }
        catch (Exception e) {
            Intent li = ctx.getPackageManager().getLaunchIntentForPackage(a.pkg);
            if (li != null) ctx.startActivity(li.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }
    }

    /** A list of apps with their icons, for "+ add" and similar pickers. */
    interface Picked { void on(App a); }
    static void pick(Activity ctx, String title, List<App> choices, Picked done) {
        last = ctx;
        io.execute(() -> {
            for (App a : choices) if (art.get(a.pkg) == null) load(ctx, a);
            main.post(() -> {
                android.widget.ArrayAdapter<App> ad = new android.widget.ArrayAdapter<App>(ctx, 0, choices) {
                    @Override public View getView(int i, View cv, android.view.ViewGroup parent) {
                        LinearLayout row = cv instanceof LinearLayout ? (LinearLayout) cv : new LinearLayout(ctx);
                        row.removeAllViews();
                        row.setGravity(Gravity.CENTER_VERTICAL);
                        row.setPadding(Ui.px(20), Ui.px(10), Ui.px(20), Ui.px(10));
                        App a = getItem(i);
                        ImageView iv = new ImageView(ctx);
                        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                        Object[] o = art.get(a.pkg);
                        if (o != null) iv.setImageDrawable((Drawable) o[0]);
                        row.addView(iv, new LinearLayout.LayoutParams(Ui.px((o != null && (Boolean) o[2]) ? 64 : 36), Ui.px(36)));
                        TextView t = Ui.text(ctx, Ui.sans, 16, 0xFFFFFFFF);
                        t.setText(a.label);
                        t.setPadding(Ui.px(16), 0, 0, 0);
                        row.addView(t);
                        return row;
                    }
                };
                new AlertDialog.Builder(ctx, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setTitle(title)
                    .setAdapter(ad, (d, i) -> done.on(choices.get(i)))
                    .show();
            });
        });
    }

    static void load(Context c, App a) {
        PackageManager pm = c.getPackageManager();
        Drawable d = null;
        boolean isBanner = false;
        try {
            ActivityInfo ai = pm.getActivityInfo(a.comp, 0);
            d = ai.loadBanner(pm);
            if (d == null) d = pm.getApplicationBanner(a.pkg);
            isBanner = d != null;
            if (d == null) d = ai.loadIcon(pm);
        } catch (Exception ignored) {}
        if (d == null) d = pm.getDefaultActivityIcon();
        int tint = isBanner ? 0xFF333340 : average(d);
        art.put(a.pkg, new Object[]{d, tint, isBanner});
    }

    /** The icon's main colour, darkened a little so white text stays readable on it. */
    static int average(Drawable d) {
        try {
            Bitmap b = Bitmap.createBitmap(12, 12, Bitmap.Config.ARGB_8888);
            Canvas cv = new Canvas(b);
            d.setBounds(0, 0, 12, 12);
            d.draw(cv);
            long r = 0, g = 0, bl = 0, n = 0;
            for (int y = 0; y < 12; y++) for (int x = 0; x < 12; x++) {
                int p = b.getPixel(x, y);
                if (Color.alpha(p) < 128) continue;
                r += Color.red(p); g += Color.green(p); bl += Color.blue(p); n++;
            }
            b.recycle();
            if (n == 0) return 0xFF3A3A48;
            return Ambient.blend(Color.rgb((int) (r / n), (int) (g / n), (int) (bl / n)), 0xFF000000, 0.35f);
        } catch (Exception e) { return 0xFF3A3A48; }
    }

    /** Long press menu shared by every row. Extra actions (move, remove) come first. */
    static void menu(Activity ctx, App a, String[] extra, Runnable[] extraDo, Runnable changed) {
        List<String> labels = new ArrayList<>(Arrays.asList(extra));
        List<Runnable> acts = new ArrayList<>(Arrays.asList(extraDo));
        List<Integer> icons = new ArrayList<>();
        for (String l : extra) icons.add(l.startsWith("Move left") ? Icon.PREV : l.startsWith("Move right") ? Icon.NEXT
            : l.startsWith("Close") ? Icon.CLOSE : Icon.CLOSE);
        List<String> watch = Prefs.listOrEmpty(ctx, "watch"), mine = Prefs.listOrEmpty(ctx, "mine");
        if (!watch.contains(a.pkg)) { labels.add("Pin to Watch"); icons.add(Icon.PLUS); acts.add(() -> { watch.add(a.pkg); Prefs.list(ctx, "watch", watch); changed.run(); }); }
        if (!mine.contains(a.pkg)) { labels.add("Add to My apps"); icons.add(Icon.PLUS); acts.add(() -> { mine.add(a.pkg); Prefs.list(ctx, "mine", mine); changed.run(); }); }
        labels.add("Hide from All apps"); icons.add(Icon.EYE_OFF);
        acts.add(() -> { List<String> h = Prefs.listOrEmpty(ctx, "hidden"); h.add(a.pkg); Prefs.list(ctx, "hidden", h); changed.run(); });
        labels.add("App info"); icons.add(Icon.GEAR);
        acts.add(() -> ctx.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + a.pkg))));
        boolean system = false;
        try { system = (ctx.getPackageManager().getApplicationInfo(a.pkg, 0).flags & android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0; }
        catch (Exception ignored) {}
        if (!system) { // built in apps can't be removed: App info has Disable for those
            labels.add("Uninstall"); icons.add(Icon.CLOSE);
            acts.add(() -> ctx.startActivity(new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + a.pkg))));
        }
        popup(ctx, ctx.getCurrentFocus(), a, labels, icons, acts);
    }

    /**
     * The small pop up beside an app, like the stock launcher's: the app's name on top, then its
     * options. Up and down move, OK chooses, Back closes. It floats right of the app (or left, near the edge).
     */
    static void popup(Activity ctx, View anchor, App a, List<String> labels, List<Integer> icons, List<Runnable> acts) {
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Ui.px(8), Ui.px(12), Ui.px(8), Ui.px(8));
        GradientDrawable bg = Ui.round(0xF2181820, 20);
        bg.setStroke(Ui.px(1), 0x2EFFFFFF);
        box.setBackground(bg);
        box.setElevation(Ui.px(16));
        TextView title = Ui.text(ctx, Ui.sansMedium, 15, 0xB3FFFFFF);
        title.setText(a.label);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        title.setPadding(Ui.px(14), 0, Ui.px(14), Ui.px(8));
        box.addView(title);
        android.widget.PopupWindow pw = new android.widget.PopupWindow(box, Ui.px(270), -2, true);
        pw.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0));
        pw.setAnimationStyle(android.R.style.Animation_Dialog);
        View first = null;
        for (int i = 0; i < labels.size(); i++) {
            final Runnable act = acts.get(i);
            boolean danger = labels.get(i).equals("Uninstall");
            LinearLayout r = new LinearLayout(ctx);
            r.setGravity(Gravity.CENTER_VERTICAL);
            r.setFocusable(true);
            r.setPadding(Ui.px(14), 0, Ui.px(14), 0);
            GradientDrawable rb = Ui.round(0x00FFFFFF, 14);
            r.setBackground(rb);
            Icon ic = new Icon(ctx, icons.get(i));
            ic.color(danger ? 0xFFFF6B5A : 0xE6FFFFFF);
            r.addView(ic, new LinearLayout.LayoutParams(Ui.px(18), Ui.px(18)));
            TextView t = Ui.text(ctx, Ui.sans, 16, danger ? 0xFFFF8A7E : 0xFFFFFFFF);
            t.setText(labels.get(i));
            t.setSingleLine(true);
            t.setPadding(Ui.px(14), 0, 0, 0);
            r.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
            r.setOnFocusChangeListener((v, f) -> {           // the popup is its own window: it draws its own focus
                rb.setColor(f ? (danger ? 0x33FF6B5A : 0x29FFFFFF) : 0x00FFFFFF);
                rb.setStroke(f ? Ui.px(2) : 0, 0xFFFFFFFF);
            });
            r.setOnClickListener(v -> { pw.dismiss(); act.run(); });
            box.addView(r, new LinearLayout.LayoutParams(-1, Ui.px(46)));
            if (first == null) first = r;
        }
        if (anchor == null) { pw.showAtLocation(ctx.getWindow().getDecorView(), Gravity.CENTER, 0, 0); }
        else {
            int[] at = new int[2];
            anchor.getLocationOnScreen(at);
            int sw = ctx.getResources().getDisplayMetrics().widthPixels, sh = ctx.getResources().getDisplayMetrics().heightPixels;
            box.measure(View.MeasureSpec.makeMeasureSpec(Ui.px(270), View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED);
            int w = Ui.px(270), hgt = box.getMeasuredHeight();
            int x = at[0] + (int) (anchor.getWidth() * anchor.getScaleX()) + Ui.px(14);
            if (x + w > sw - Ui.px(16)) x = at[0] - w - Ui.px(14);           // no room on the right: open on the left
            int y = Math.max(Ui.px(16), Math.min(sh - hgt - Ui.px(16), at[1]));
            pw.showAtLocation(ctx.getWindow().getDecorView(), Gravity.TOP | Gravity.START, Math.max(Ui.px(16), x), y);
        }
        View f0 = first;
        if (f0 != null) f0.post(f0::requestFocus);
    }
}
