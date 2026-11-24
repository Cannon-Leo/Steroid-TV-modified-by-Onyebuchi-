package tv.steroid.home;

import android.content.Context;
import android.graphics.Color;
import org.json.JSONArray;
import org.json.JSONObject;

/** Radio stations from config/stations.json (imported from CLI AMP, or the default list). */
final class Stations {
    static String[][] ALL = new String[0][];
    static int[] HUE = new int[0];
    static final int[] PALETTE = {0xFFE8915A, 0xFF8FB8A8, 0xFF5F7FB8, 0xFF8A6CE0, 0xFFFF4FA3, 0xFF39D0FF, 0xFF9BE15D, 0xFFFFB347};

    static synchronized void reload(Context c) { ALL = new String[0][]; load(c); }

    /** Your own stations, added on the TV ({title,url,genre} JSON array in the "personal" prefs). */
    static JSONArray mine(Context c) {
        try { return new JSONArray(Prefs.sp(c).getString("myStations", "[]")); } catch (Exception e) { return new JSONArray(); }
    }

    static void add(Context c, String title, String url) {
        JSONArray a = mine(c);
        try { a.put(new JSONObject().put("title", title).put("url", url).put("genre", "Added by you")); } catch (Exception ignored) {}
        Prefs.sp(c).edit().putString("myStations", a.toString()).apply();
        reload(c);
    }

    static synchronized void load(Context c) {
        if (ALL.length > 0) return;
        Config.load(c);
        JSONArray a = new JSONArray();
        JSONArray base = Config.stations.optJSONArray("stations");
        for (int i = 0; base != null && i < base.length(); i++) a.put(base.opt(i));
        JSONArray own = mine(c);
        for (int i = 0; i < own.length(); i++) a.put(own.opt(i));
        int n = a.length();
        String[][] all = new String[n][];
        int[] hue = new int[n];
        for (int i = 0; i < n; i++) {
            JSONObject s = a.optJSONObject(i);
            all[i] = new String[]{s.optString("title"), s.optString("url"), s.optString("genre", "")};
            try { hue[i] = Color.parseColor(s.optString("color")); }
            catch (Exception e) { hue[i] = PALETTE[i % PALETTE.length]; }
        }
        ALL = all;
        HUE = hue;
    }

    static String name(int i) { return ALL[i][0]; }
    static String url(int i) { return ALL[i][1]; }
    static String genre(int i) { return ALL[i][2]; }
    static int count() { return ALL.length; }
}
