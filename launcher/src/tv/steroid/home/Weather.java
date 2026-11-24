package tv.steroid.home;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import org.json.JSONObject;

/**
 * Weather for the screensaver, from Open-Meteo (free, no account or key). The place is the city set in
 * Launcher settings, or else found from the TV's internet address. Fetched at most every 30 minutes and
 * kept, so the screensaver still shows the last reading when the internet is down.
 */
final class Weather {
    static volatile String city = "", summary = "";
    static volatile int temp = Integer.MIN_VALUE, hi, lo, code = -1;
    static volatile boolean day = true;
    static volatile long at;

    static SharedPreferences sp(Context c) { return c.getSharedPreferences("weather", Context.MODE_PRIVATE); }

    static void restore(Context c) {
        if (at != 0) return;
        SharedPreferences p = sp(c);
        at = p.getLong("at", 0);
        if (at == 0) return;
        city = p.getString("city", ""); temp = p.getInt("temp", Integer.MIN_VALUE); hi = p.getInt("hi", 0); lo = p.getInt("lo", 0);
        code = p.getInt("code", -1); day = p.getBoolean("day", true); summary = describe(code);
    }

    /** Off the main thread. */
    static void refresh(Context c) {
        restore(c);
        if (System.currentTimeMillis() - at < 30 * 60_000L && temp != Integer.MIN_VALUE) return;
        try {
            double lat, lon;
            String place = Prefs.sp(c).getString("weatherCity", "").trim();
            if (!place.isEmpty()) {
                JSONObject g = get("https://geocoding-api.open-meteo.com/v1/search?count=1&name=" + Uri.encode(place));
                JSONObject r = g.getJSONArray("results").getJSONObject(0);
                lat = r.getDouble("latitude"); lon = r.getDouble("longitude"); city = r.optString("name", place);
            } else {
                JSONObject ip = get("https://ipwho.is/");
                lat = ip.getDouble("latitude"); lon = ip.getDouble("longitude"); city = ip.optString("city", "");
            }
            JSONObject f = get("https://api.open-meteo.com/v1/forecast?latitude=" + lat + "&longitude=" + lon
                + "&current=temperature_2m,weather_code,is_day&daily=temperature_2m_max,temperature_2m_min&forecast_days=1&timezone=auto");
            JSONObject cur = f.getJSONObject("current"), d = f.getJSONObject("daily");
            temp = (int) Math.round(cur.getDouble("temperature_2m"));
            code = cur.getInt("weather_code");
            day = cur.optInt("is_day", 1) == 1;
            hi = (int) Math.round(d.getJSONArray("temperature_2m_max").getDouble(0));
            lo = (int) Math.round(d.getJSONArray("temperature_2m_min").getDouble(0));
            summary = describe(code);
            at = System.currentTimeMillis();
            sp(c).edit().putLong("at", at).putString("city", city).putInt("temp", temp).putInt("hi", hi).putInt("lo", lo)
                .putInt("code", code).putBoolean("day", day).apply();
        } catch (Exception e) {
            android.util.Log.w("SteroidWeather", "weather unavailable: " + e);
        }
    }

    static JSONObject get(String u) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(u).openConnection();
        conn.setConnectTimeout(6000);
        conn.setReadTimeout(8000);
        try (InputStream in = conn.getInputStream()) {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            for (int n; (n = in.read(buf)) > 0 && b.size() < 200_000; ) b.write(buf, 0, n);
            return new JSONObject(b.toString("UTF-8"));
        } finally { conn.disconnect(); }
    }

    static boolean known() { return temp != Integer.MIN_VALUE; }

    static int icon() {
        int c = code;
        if (c == 0) return day ? Icon.SUN : Icon.MOON;
        if (c == 1 || c == 2) return day ? Icon.CLOUD_SUN : Icon.CLOUD_MOON;
        if (c == 3) return Icon.CLOUD;
        if (c == 45 || c == 48) return Icon.CLOUD_FOG;
        if (c >= 95) return Icon.CLOUD_LIGHTNING;
        if ((c >= 71 && c <= 77) || c == 85 || c == 86) return Icon.SNOWFLAKE;
        return Icon.CLOUD_RAIN;
    }

    static String describe(int c) {
        if (c == 0) return "Clear";
        if (c == 1) return "Mostly clear";
        if (c == 2) return "Partly cloudy";
        if (c == 3) return "Cloudy";
        if (c == 45 || c == 48) return "Fog";
        if (c >= 51 && c <= 57) return "Drizzle";
        if (c >= 61 && c <= 67) return "Rain";
        if (c >= 71 && c <= 77) return "Snow";
        if (c >= 80 && c <= 82) return "Showers";
        if (c == 85 || c == 86) return "Snow showers";
        if (c >= 95) return "Thunderstorm";
        return "";
    }
}
