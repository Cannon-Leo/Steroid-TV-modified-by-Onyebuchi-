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

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {}
    @Override public void onInterrupt() { h.removeCallbacks(hold); }
}
