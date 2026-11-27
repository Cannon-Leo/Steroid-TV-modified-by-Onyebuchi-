package tv.steroid.home;

import android.app.Activity;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * "Recently played on this TV": one row for every app, below Spotify. What played (from History, the
 * same signal as the music widget: title, artwork, a link back) mixed with apps you opened that share
 * nothing (Android's record of when each app was last used: "Opened 2 h ago"). Newest first, last 20.
 * OK opens it (the exact item where the app allows, otherwise the app). Hold OK: remove it, or clear all.
 * Stored on the TV only.
 */
class HistoryRow extends Shelf {
    static final long DAYS = 3L * 86400000L;

    volatile java.util.Map<String, Apps.App> apps = new java.util.HashMap<>();   // filled once per refresh, off the main thread

    HistoryRow(Activity a, float size) {
        super(a, "*", "", size);
        head.setText("Recently played on this TV");
    }

    @Override List<Prog> query() {
        List<Prog> out = new ArrayList<>();
        List<Apps.App> installed = Apps.all(act);   // one look at the installed apps per refresh
        java.util.Map<String, Apps.App> byPkg = new java.util.HashMap<>();
        for (Apps.App a : installed) byPkg.put(a.pkg, a);
        apps = byPkg;
        Set<String> withMedia = new HashSet<>();
        try {
            org.json.JSONArray hist = History.load(act);
            for (int i = 0; i < hist.length(); i++) {
                org.json.JSONObject o = hist.getJSONObject(i);
                Prog p = new Prog();
                p.pkg = o.optString("pkg"); p.title = o.optString("title"); p.sub = o.optString("sub");
                p.poster = o.optString("thumb"); p.intent = o.optString("intent"); p.when = o.optLong("at");
                if (p.poster.isEmpty()) p.poster = null;
                if (p.pkg.isEmpty() || p.title.isEmpty()) continue;
                out.add(p);
                withMedia.add(p.pkg);
            }
        } catch (Exception ignored) {}
        // apps that share nothing about what they play: when you last opened them
        try {
            UsageStatsManager um = (UsageStatsManager) act.getSystemService(Context.USAGE_STATS_SERVICE);
            long now = System.currentTimeMillis();
            List<UsageStats> stats = um == null ? null : um.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - DAYS, now);
            if (stats != null) {
                java.util.Map<String, Long> last = new java.util.HashMap<>();
                for (UsageStats s : stats) if (s.getLastTimeUsed() > now - DAYS)
                    last.merge(s.getPackageName(), s.getLastTimeUsed(), Math::max);
                for (Apps.App a : installed) {
                    Long t = last.get(a.pkg);
                    if (t == null || withMedia.contains(a.pkg) || a.pkg.equals(act.getPackageName())) continue;
                    if (History.hidden(act, a.pkg, t)) continue;   // removed or cleared since
                    Prog p = new Prog();
                    p.pkg = a.pkg; p.title = a.label; p.sub = "Opened " + ago(t); p.when = t;
                    Intent li = act.getPackageManager().getLeanbackLaunchIntentForPackage(a.pkg);
                    if (li == null) li = act.getPackageManager().getLaunchIntentForPackage(a.pkg);
                    if (li == null) continue;
                    p.intent = li.toUri(Intent.URI_INTENT_SCHEME);
                    p.poster = null;
                    out.add(p);
                }
            }
        } catch (Exception ignored) {}
        Collections.sort(out, (x, y) -> Long.compare(y.when, x.when));
        while (out.size() > 20) out.remove(out.size() - 1);
        android.util.Log.i("SteroidShelf", "recently played on this TV: " + out.size());
        return out;
    }

    static String ago(long t) {
        long m = (System.currentTimeMillis() - t) / 60000;
        if (m < 1) return "just now";
        if (m < 60) return m + " min ago";
        if (m < 1440) return (m / 60) + " h ago";
        return (m / 1440) + " d ago";
    }

    @Override View card(Prog p) {
        View card = super.card(p);
        FrameLayout in = (FrameLayout) ((FrameLayout) card).getChildAt(0);
        // which app it came from: a small icon top left; app only cards get the app's own artwork
        Apps.App a = apps.get(p.pkg);   // no package scan per card
        if (a != null) {
            Runnable apply = () -> {
                Object[] o = Apps.art.get(a.pkg);
                if (o == null) return;
                if (p.poster == null) {
                    ImageView art = new ImageView(act);
                    art.setScaleType((Boolean) o[2] ? ImageView.ScaleType.CENTER_CROP : ImageView.ScaleType.FIT_CENTER);
                    art.setImageDrawable((Drawable) o[0]);
                    if (!(Boolean) o[2]) {
                        int c = (Integer) o[1];
                        in.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                            new int[]{Ambient.blend(c, 0xFFFFFFFF, 0.10f), Ambient.blend(c, 0xFF000000, 0.45f)}));
                        art.setPadding(Ui.px(40), Ui.px(18), Ui.px(40), Ui.px(34));
                    }
                    in.addView(art, 0, new FrameLayout.LayoutParams(-1, -1));
                }
            };
            if (Apps.art.get(a.pkg) != null) apply.run();
            else Apps.io.execute(() -> { Apps.load(act, a); Apps.main.post(apply); });
            TextView chip = Ui.text(act, Ui.sansMedium, 10, 0xFFFFFFFF);
            chip.setText(a.label);
            chip.setSingleLine(true);
            chip.setPadding(Ui.px(7), Ui.px(2), Ui.px(7), Ui.px(2));
            chip.setBackground(Ui.round(0x99000000, 6));
            FrameLayout.LayoutParams cl = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START);
            cl.setMargins(Ui.px(8), Ui.px(8), 0, 0);
            in.addView(chip, cl);
        }
        // hold OK: remove this one, or clear the whole row
        card.setOnLongClickListener(v -> {
            Apps.App app = a != null ? a : new Apps.App();
            if (a == null) { app.pkg = p.pkg; app.label = p.title; }
            Apps.menu(act, app, new String[]{"Remove from Recently played", "Clear Recently played"},
                new Runnable[]{() -> { History.remove(act, p.pkg, p.sub != null && p.sub.startsWith("Opened ") ? null : p.title); refresh(); },
                    () -> { History.clear(act); refresh(); }}, this::refresh);
            return true;
        });
        return card;
    }
}
