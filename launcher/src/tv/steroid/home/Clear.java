package tv.steroid.home;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * "Close all" means everything: every player is stopped (other apps' sessions, our background
 * YouTube player, the radio), every app's background process is freed (twice, since some restart a
 * service on the first try), the Resume card is cleared, the music widget goes to sleep, and the
 * Recent apps list starts empty. Only apps opened afterwards appear again.
 */
final class Clear {
    /** Media sessions closed by Close all: the widget ignores them until they play again. */
    static final Set<String> dismissed = new HashSet<>();

    static void all(Context c, Collection<String> pkgs) {
        long now = System.currentTimeMillis();
        BgPlayer.suppressUntil = now + 15000; // the players we stop now must not be picked up again
        Prefs.sp(c).edit().putLong("recentsClearedAt", now).putLong("resumeClearedAt", now).apply();
        // stop every player that's still holding a session
        try {
            MediaSessionManager msm = (MediaSessionManager) c.getSystemService(Context.MEDIA_SESSION_SERVICE);
            List<MediaController> list = msm.getActiveSessions(new ComponentName(c, NotifListener.class));
            for (MediaController m : list) {
                dismissed.add(NowPlaying.sessionKey(m));
                if (m.getPackageName().equals(c.getPackageName())) continue; // ours are stopped below
                try { m.getTransportControls().pause(); m.getTransportControls().stop(); } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
        BgPlayer b = BgPlayer.running;
        if (b != null) b.h.post(b::stopSelfNow);
        if (RadioService.station >= 0) RadioService.stop(c);
        // free the apps' memory; again a moment later for those that restart a service
        Set<String> todo = new HashSet<>(pkgs);
        if (BgPlayer.lastPkg != null) todo.add(BgPlayer.lastPkg);
        Runnable kill = () -> {
            ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
            for (String p : todo) if (!p.equals(c.getPackageName())) try { am.killBackgroundProcesses(p); } catch (Exception ignored) {}
        };
        kill.run();
        Handler h = new Handler(Looper.getMainLooper());
        h.postDelayed(kill, 1200);
        h.postDelayed(kill, 4000);
        BgPlayer.stoppedAt = 0; // nothing to pick up when you come home
    }

    /** One app closed from Recent apps (hold OK). */
    static void one(Context c, String pkg) {
        BgPlayer.suppressUntil = System.currentTimeMillis() + 15000;
        Prefs.sp(c).edit().putLong("closed_" + pkg, System.currentTimeMillis()).apply();
        try {
            MediaSessionManager msm = (MediaSessionManager) c.getSystemService(Context.MEDIA_SESSION_SERVICE);
            for (MediaController m : msm.getActiveSessions(new ComponentName(c, NotifListener.class)))
                if (m.getPackageName().equals(pkg)) { dismissed.add(NowPlaying.sessionKey(m)); try { m.getTransportControls().stop(); } catch (Exception ignored) {} }
        } catch (Exception ignored) {}
        if (pkg.equals(BgPlayer.lastPkg)) { BgPlayer b = BgPlayer.running; if (b != null) b.h.post(b::stopSelfNow); }
        ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
        am.killBackgroundProcesses(pkg);
        new Handler(Looper.getMainLooper()).postDelayed(() -> am.killBackgroundProcesses(pkg), 1500);
    }

    /** Was this app last used before you closed it (or closed everything)? */
    static boolean closedSince(Context c, String pkg, long lastUsed) {
        long all = Prefs.sp(c).getLong("recentsClearedAt", 0), one = Prefs.sp(c).getLong("closed_" + pkg, 0);
        return lastUsed <= Math.max(all, one) + 1500;
    }

    static boolean playing(MediaController m) {
        PlaybackState s = m.getPlaybackState();
        return s != null && s.getState() == PlaybackState.STATE_PLAYING;
    }
}
