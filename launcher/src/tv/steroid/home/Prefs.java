package tv.steroid.home;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Settings the user changes on the TV itself, so nothing needs a rebuild. */
final class Prefs {
    static SharedPreferences sp(Context c) { return c.getSharedPreferences("personal", Context.MODE_PRIVATE); }

    static String name(Context c) { return sp(c).getString("name", "Onyebuchi"); }
    static void name(Context c, String n) { sp(c).edit().putString("name", n.trim()).apply(); }

    static boolean photos(Context c) { return sp(c).getBoolean("photos", true); }
    static boolean drift(Context c) { return sp(c).getBoolean("drift", true); }
    static int interval(Context c) { return sp(c).getInt("interval", 30); }
    static void put(Context c, String k, boolean v) { sp(c).edit().putBoolean(k, v).apply(); }
    static void put(Context c, String k, int v) { sp(c).edit().putInt(k, v).apply(); }

    /** Ordered package lists: "mine" (My apps row), "watch" (Watch row, starts from apps.json), "hidden". */
    static List<String> list(Context c, String key) {
        String s = sp(c).getString(key, null);
        if (s == null) return null;
        List<String> out = new ArrayList<>();
        if (!s.isEmpty()) out.addAll(Arrays.asList(s.split(",")));
        return out;
    }

    static List<String> listOrEmpty(Context c, String key) {
        List<String> l = list(c, key);
        return l == null ? new ArrayList<>() : l;
    }

    static void list(Context c, String key, List<String> l) {
        sp(c).edit().putString(key, android.text.TextUtils.join(",", l)).apply();
    }

    // ---------- Home layout: section order, size (0 small, 1 medium, 2 large) and visibility ----------
    static final String[] SECTIONS = {"watch", "videos", "worlds", "mine", "lookout", "all", "system"};
    static final String[] SECTION_NAMES = {"Watch (streaming apps)", "Video rows (continue and for you)", "Retro TV and Radio",
        "My apps", "Look out (Windows, Little Office)", "All apps", "System widget"};

    static List<String> order(Context c) {
        List<String> o = listOrEmpty(c, "order");
        for (String k : SECTIONS) if (!o.contains(k)) o.add(k);
        o.retainAll(Arrays.asList(SECTIONS));
        return o;
    }
    static void order(Context c, List<String> o) { list(c, "order", o); bump(c); }
    static int size(Context c, String k) { return sp(c).getInt("size_" + k, 1); }
    static void size(Context c, String k, int v) { sp(c).edit().putInt("size_" + k, v).apply(); bump(c); }
    static float scale(Context c, String k) { return new float[]{0.85f, 1f, 1.2f}[size(c, k)]; }
    static boolean shown(Context c, String k) { return !sp(c).getBoolean("hide_" + k, false); }
    static void shown(Context c, String k, boolean v) { sp(c).edit().putBoolean("hide_" + k, !v).apply(); bump(c); }
    static int layoutVersion(Context c) { return sp(c).getInt("layoutVersion", 0); }
    static void bump(Context c) { sp(c).edit().putInt("layoutVersion", layoutVersion(c) + 1).apply(); }
}
