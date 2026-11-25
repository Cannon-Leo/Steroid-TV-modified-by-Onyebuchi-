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
    static final long HOLD = 450;   // ms: longer than a normal press, shorter than impatience
    final Handler h = new Handler(Looper.getMainLooper());
    boolean held;

    final Runnable hold = () -> {
        held = true;
        // never any heavy work here: the system waits on this service for every Home press
        try { Recents.captureHome(); } catch (Throwable ignored) {}   // async, returns at once
        try {
            startActivity(new Intent(this, Recents.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION));
        } catch (Exception e) { held = false; }   // couldn't open: the release still goes Home
    };

    @Override protected boolean onKeyEvent(KeyEvent e) {
        if (e.getKeyCode() != KeyEvent.KEYCODE_HOME) return false;   // never touch any other key
        if (e.getAction() == KeyEvent.ACTION_DOWN) {
            if (e.getRepeatCount() == 0) { held = false; h.removeCallbacks(hold); h.postDelayed(hold, HOLD); }
            return true;
        }
        if (e.getAction() == KeyEvent.ACTION_UP) {
            h.removeCallbacks(hold);
            if (!held) performGlobalAction(GLOBAL_ACTION_HOME);   // a normal press: Home, as always
            held = false;
            return true;
        }
        return false;
    }

    /**
     * Keeps hold Home switched on for good. Android turns an accessibility service off whenever its app
     * is force-stopped (updates, "close all" tools, some Xiaomi clean-ups). The launcher calls this every
     * time it comes to the front and switches the service straight back on, keeping any other services.
     * Needs WRITE_SECURE_SETTINGS, which only adb can grant (scripts/buchi.sh install does it once).
     */
    static void ensure(android.content.Context c) {
        if (c.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") != android.content.pm.PackageManager.PERMISSION_GRANTED) return;
        try {
            android.content.ContentResolver r = c.getContentResolver();
            String me = c.getPackageName() + "/" + HomeKeys.class.getName();
            String have = android.provider.Settings.Secure.getString(r, android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (have != null && (":" + have + ":").contains(":" + me + ":")
                && android.provider.Settings.Secure.getInt(r, android.provider.Settings.Secure.ACCESSIBILITY_ENABLED, 0) == 1) return;
            String now = have == null || have.isEmpty() || have.equals("null") ? me
                : (":" + have + ":").contains(":" + me + ":") ? have : have + ":" + me;
            android.provider.Settings.Secure.putString(r, android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, now);
            android.provider.Settings.Secure.putInt(r, android.provider.Settings.Secure.ACCESSIBILITY_ENABLED, 1);
            android.util.Log.i("SteroidHome", "hold Home switched back on");
        } catch (Exception e) { android.util.Log.w("SteroidHome", "couldn't switch hold Home on: " + e); }
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {}
    @Override public void onInterrupt() { h.removeCallbacks(hold); }
}
