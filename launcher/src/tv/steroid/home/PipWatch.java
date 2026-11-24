package tv.steroid.home;

import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import java.util.HashMap;
import java.util.Map;

/**
 * Finds an app sitting in a mini player (picture in picture) while the home screen is up, and brings
 * it back. An app in a mini player is paused but never stopped (Android's own usage events say so),
 * so the newest app whose last event is "paused" with no "stopped" after it is the one floating on
 * screen. Works for any app that uses picture in picture, not one in particular.
 */
final class PipWatch {
    static final int RESUMED = 1, PAUSED = 2, STOPPED = 23;
    static final String[] IGNORE = {"com.android.systemui", "android", "com.google.android.tvlauncher",
        "com.google.android.katniss", "com.google.android.apps.tv.launcherx"};

    /** The package in a mini player right now, or null. Call off the main thread if you can (cheap anyway). */
    static String pinned(Context c) {
        try {
            UsageStatsManager um = (UsageStatsManager) c.getSystemService(Context.USAGE_STATS_SERVICE);
            long now = System.currentTimeMillis();
            UsageEvents ev = um.queryEvents(now - 6 * 3600_000L, now);
            Map<String, int[]> last = new HashMap<>();   // pkg -> {type}
            Map<String, Long> at = new HashMap<>();
            UsageEvents.Event e = new UsageEvents.Event();
            while (ev.hasNextEvent()) {
                ev.getNextEvent(e);
                int t = e.getEventType();
                if (t != RESUMED && t != PAUSED && t != STOPPED) continue;
                last.put(e.getPackageName(), new int[]{t});
                at.put(e.getPackageName(), e.getTimeStamp());
            }
            String best = null;
            long bestAt = 0;
            for (Map.Entry<String, int[]> x : last.entrySet()) {
                String p = x.getKey();
                if (x.getValue()[0] != PAUSED || p.equals(c.getPackageName()) || ignored(p)) continue;
                long t = at.get(p);
                if (now - t < 1500) continue;            // still on its way to stopped
                if (t > bestAt) { best = p; bestAt = t; }
            }
            return best;
        } catch (Exception ex) {
            return null;
        }
    }

    static boolean ignored(String p) { for (String s : IGNORE) if (s.equals(p)) return true; return false; }

    /** Back to full screen: opening the app's own task again lifts it out of the mini player. */
    static boolean full(Context c, String pkg) {
        PackageManager pm = c.getPackageManager();
        Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
        if (i == null) i = pm.getLaunchIntentForPackage(pkg);
        try {
            MediaController m = session(c, pkg);
            if (m != null && m.getSessionActivity() != null) { m.getSessionActivity().send(); return true; }
        } catch (Exception ignored) {}
        if (i == null) return false;
        try {
            c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED));
            return true;
        } catch (Exception e) { return false; }
    }

    /** Close: stop its playback (most apps close their mini player when playback stops). */
    static void close(Context c, String pkg) {
        MediaController m = session(c, pkg);
        if (m != null) try { m.getTransportControls().pause(); m.getTransportControls().stop(); } catch (Exception ignored) {}
    }

    static MediaController session(Context c, String pkg) {
        try {
            MediaSessionManager msm = (MediaSessionManager) c.getSystemService(Context.MEDIA_SESSION_SERVICE);
            for (MediaController m : msm.getActiveSessions(new ComponentName(c, NotifListener.class)))
                if (m.getPackageName().equals(pkg)) return m;
        } catch (Exception ignored) {}
        return null;
    }
}
