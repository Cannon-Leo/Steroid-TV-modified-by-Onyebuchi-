package tv.steroid.home;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.util.*;

/**
 * Recent apps, like on a phone: OK opens, hold OK (or the Close button) closes the app's background
 * process to free memory. "Close all" clears every recent app. Needs usage access, granted over adb:
 * appops set tv.steroid.home GET_USAGE_STATS allow
 */
public class Recents extends Activity {
    LinearLayout grid;
    List<Apps.App> recent = new ArrayList<>();

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.init(this);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xF20C0C10);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setClipChildren(false);
        col.setPadding(Ui.px(64), Ui.px(44), Ui.px(64), Ui.px(40));
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView t = Ui.text(this, Ui.serif, 40, 0xFFFFFFFF);
        t.setText("Recent apps");
        top.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        Button all = new Button(this);
        all.setAllCaps(false);
        all.setText("Close all");
        all.setTypeface(Ui.sansMedium);
        all.setTextColor(0xFFFFFFFF);
        GradientDrawable g = Ui.round(0x26FFFFFF, 22);
        all.setBackground(g);
        all.setPadding(Ui.px(22), 0, Ui.px(22), 0);
        all.setOnFocusChangeListener((v, f) -> g.setColor(f ? 0x40FFFFFF : 0x26FFFFFF));
        all.setOnClickListener(v -> {
            // everything: every app on the TV, not only those listed here
            List<String> pkgs = new ArrayList<>();
            for (Apps.App a : Apps.all(this)) pkgs.add(a.pkg);
            for (Apps.App a : recent) if (!pkgs.contains(a.pkg)) pkgs.add(a.pkg);
            Clear.all(this, pkgs);
            toast(recent.isEmpty() ? "Everything is closed" : "Closed " + recent.size() + (recent.size() == 1 ? " app" : " apps") + " and stopped all playback");
            load();
        });
        top.addView(all, new LinearLayout.LayoutParams(-2, Ui.px(44)));
        col.addView(top);
        TextView hint = Ui.text(this, Ui.sans, 13, 0x99FFFFFF);
        hint.setText("OK to open · hold OK for options (close, app info, uninstall) · Close all stops everything");
        hint.setPadding(0, Ui.px(8), 0, Ui.px(24));
        col.addView(hint);
        grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setClipChildren(false);
        ScrollView sv = new ScrollView(this);
        sv.setClipChildren(false);
        sv.addView(grid);
        col.addView(sv, new LinearLayout.LayoutParams(-1, -1));
        root.addView(col);
        Selector.attach(this, root);
        setContentView(root);
    }

    @Override protected void onResume() { super.onResume(); load(); }

    void load() {
        grid.removeAllViews();
        recent.clear();
        List<Apps.App> all = Apps.all(this);
        UsageStatsManager um = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
        long now = System.currentTimeMillis();
        List<UsageStats> stats = um == null ? null : um.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 86400000L * 2, now);
        if (stats == null || stats.isEmpty()) {
            TextView e = Ui.text(this, Ui.sans, 15, 0xCCFFFFFF);
            e.setText("Usage access is off. Run scripts/buchi.sh install on the Mac to allow it.");
            grid.addView(e);
            return;
        }
        Map<String, Long> last = new HashMap<>();
        for (UsageStats s : stats) if (s.getLastTimeUsed() > 0)
            last.put(s.getPackageName(), Math.max(s.getLastTimeUsed(), last.containsKey(s.getPackageName()) ? last.get(s.getPackageName()) : 0));
        for (Apps.App a : all) {
            if (a.pkg.equals(getPackageName())) continue;
            Long t = last.get(a.pkg);
            if (t != null && t > now - 86400000L && !Clear.closedSince(this, a.pkg, t)) recent.add(a); // closed apps stay gone
        }
        Collections.sort(recent, (x, y) -> Long.compare(last.get(y.pkg), last.get(x.pkg)));
        if (recent.size() > 18) recent = new ArrayList<>(recent.subList(0, 18));
        float w = (Ui.dpWidth(this) - 128 - 5 * 18) / 6f;
        LinearLayout row = null;
        for (int i = 0; i < recent.size(); i++) {
            if (i % 6 == 0) {
                row = new LinearLayout(this);
                row.setClipChildren(false);
                row.setPadding(0, 0, 0, Ui.px(18));
                grid.addView(row);
            }
            Apps.App a = recent.get(i);
            View c = Apps.card(this, a, null, v -> {
                Apps.menu(this, a, new String[]{"Close app"}, new Runnable[]{() -> { Clear.one(this, a.pkg); toast("Closed " + a.label); load(); }}, this::load);
                return true;
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.px(w), Ui.px(w * 0.58f));
            if (i % 6 != 5) lp.rightMargin = Ui.px(18);
            row.addView(c, lp);
        }
        if (recent.isEmpty()) {
            TextView e = Ui.text(this, Ui.sans, 15, 0xCCFFFFFF);
            e.setText("Nothing open recently.");
            grid.addView(e);
        } else ((LinearLayout) grid.getChildAt(0)).getChildAt(0).requestFocus();
    }

    void kill(String pkg) {
        try { ((ActivityManager) getSystemService(ACTIVITY_SERVICE)).killBackgroundProcesses(pkg); } catch (Exception ignored) {}
    }

    void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
}
