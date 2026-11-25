package tv.steroid.home;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/**
 * Hold Home on the remote for Recent apps, like HyperOS. Android lets a launcher see the Home button
 * only through an accessibility service, so this one listens to key presses and acts on Home alone:
 * a short press still goes Home (passed on as the system's own Home action), a hold opens Recents.
 * Every other key goes straight through untouched. Switched on by scripts/buchi.sh install.
 */
public class HomeKeys extends AccessibilityService {
    static final long HOLD = 450;   // ms from the first press: a hold
    static final long GAP = 260;    // ms of silence after a release: the press is over
    final Handler h = new Handler(Looper.getMainLooper());
    // Xiaomi's remote doesn't keep the O button "down" while you hold it: it sends press, release,
    // press, release... for as long as it's held (seen with getevent: KEY_HOMEPAGE DOWN/UP pairs).
    // So a hold is either a press still down after HOLD, or presses that keep coming for HOLD.
    boolean busy, down, fired;
    int presses;

    final Runnable decide = () -> {
        if (!busy || fired) return;
        if (down || presses >= 2) {   // still held, or the remote is repeating it: a hold
            fired = true;
            try { Recents.captureHome(); } catch (Throwable ignored) {}   // async, returns at once
            try {
                startActivity(new Intent(this, Recents.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION));
                android.util.Log.i("SteroidHome", "hold Home: Recent apps");
            } catch (Exception e) { fired = false; }
        }
    };

    final Runnable over = () -> {   // the button has been quiet for GAP after a release
        h.removeCallbacks(decide);
        if (busy && !fired) performGlobalAction(GLOBAL_ACTION_HOME);   // a normal press: Home, as always
        busy = false; down = false; fired = false; presses = 0;
    };

    @Override protected boolean onKeyEvent(KeyEvent e) {
        if (e.getKeyCode() != KeyEvent.KEYCODE_HOME) return false;   // never touch any other key
        // nothing slow here: Android waits on this for every Home press
        if (e.getAction() == KeyEvent.ACTION_DOWN) {
            h.removeCallbacks(over);
            if (e.getRepeatCount() == 0) {
                if (!busy) { busy = true; fired = false; presses = 0; h.postDelayed(decide, HOLD); }
                presses++;
            }
            down = true;
            return true;
        }
        if (e.getAction() == KeyEvent.ACTION_UP) {
            down = false;
            if (!busy) { performGlobalAction(GLOBAL_ACTION_HOME); return true; }   // stray release
            h.removeCallbacks(over);
            h.postDelayed(over, GAP);
            return true;
        }
        return false;
    }

    /**
     * Keeps hold Home switched on for good. Android turns an accessibility service off whenever its app
     * is force-stopped, and never restarts one it marked "crashed". The launcher calls this every time it
     * comes to the front and starts the service again if it isn't really running, keeping any others.
     * Needs WRITE_SECURE_SETTINGS, which only adb can grant (scripts/buchi.sh install does it once).
     */
    static long lastFix = -60000;

    static void ensure(android.content.Context c) {
        if (c.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") != android.content.pm.PackageManager.PERMISSION_GRANTED) return;
        try {
            final android.content.ContentResolver r = c.getContentResolver();
            final String me = c.getPackageName() + "/" + HomeKeys.class.getName();
            String have = android.provider.Settings.Secure.getString(r, android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            boolean listed = have != null && (":" + have + ":").contains(":" + me + ":");
            boolean on = android.provider.Settings.Secure.getInt(r, android.provider.Settings.Secure.ACCESSIBILITY_ENABLED, 0) == 1;
            if (listed && on && running(c)) return;   // switched on and actually running: nothing to do
            long now = android.os.SystemClock.elapsedRealtime();
            if (now - lastFix < 15000) return;          // just restarted it: give Android time to start it
            lastFix = now;
            // Not running: never switched on, switched off, or marked "crashed" (Android then never starts
            // it again by itself). Taking it off the list and putting it back makes Android start it fresh.
            final String others = without(have, me);
            android.provider.Settings.Secure.putString(r, android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, others);
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                try {
                    android.provider.Settings.Secure.putString(r, android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                        others.isEmpty() ? me : others + ":" + me);
                    android.provider.Settings.Secure.putInt(r, android.provider.Settings.Secure.ACCESSIBILITY_ENABLED, 1);
                    android.util.Log.i("SteroidHome", "hold Home (re)started");
                } catch (Exception e) { android.util.Log.w("SteroidHome", "couldn't start hold Home: " + e); }
            }, 600);
        } catch (Exception e) { android.util.Log.w("SteroidHome", "couldn't switch hold Home on: " + e); }
    }

    /** Is the service really running (bound), not just listed? Crashed services are listed but not running. */
    static boolean running(android.content.Context c) {
        try {
            android.view.accessibility.AccessibilityManager am =
                (android.view.accessibility.AccessibilityManager) c.getSystemService(android.content.Context.ACCESSIBILITY_SERVICE);
            for (android.accessibilityservice.AccessibilityServiceInfo i :
                    am.getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK))
                if (i.getResolveInfo() != null && i.getResolveInfo().serviceInfo != null
                    && HomeKeys.class.getName().equals(i.getResolveInfo().serviceInfo.name)) return true;
        } catch (Exception ignored) {}
        return false;
    }

    static String without(String list, String me) {
        if (list == null || list.equals("null")) return "";
        StringBuilder b = new StringBuilder();
        for (String p : list.split(":")) if (!p.isEmpty() && !p.equals(me)) b.append(b.length() == 0 ? "" : ":").append(p);
        return b.toString();
    }

    @Override protected void onServiceConnected() { android.util.Log.i("SteroidHome", "hold Home service running"); }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {}
    @Override public void onInterrupt() { h.removeCallbacks(decide); h.removeCallbacks(over); busy = false; }
}
