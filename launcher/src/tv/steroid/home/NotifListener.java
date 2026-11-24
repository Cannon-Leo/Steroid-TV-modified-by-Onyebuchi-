package tv.steroid.home;

import android.content.ComponentName;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.HandlerThread;
import android.service.notification.NotificationListenerService;
import java.util.ArrayList;
import java.util.List;

/**
 * Lets Steroid see which app is playing media (Android requires an enabled notification listener for
 * that; granted once over adb: cmd notification allow_listener tv.steroid.home/tv.steroid.home.NotifListener).
 * It reads no notifications. While connected it notes what plays, for the "Recently played" rows
 * (see History). Runs on its own small background thread; does nothing while nothing plays.
 */
public class NotifListener extends NotificationListenerService {
    HandlerThread thread;
    Handler h;
    MediaSessionManager msm;
    final List<MediaController> watched = new ArrayList<>();
    final MediaSessionManager.OnActiveSessionsChangedListener changed = this::watch;

    @Override public void onListenerConnected() {
        thread = new HandlerThread("steroid-history");
        thread.start();
        h = new Handler(thread.getLooper());
        try {
            msm = (MediaSessionManager) getSystemService(MEDIA_SESSION_SERVICE);
            ComponentName me = new ComponentName(this, NotifListener.class);
            msm.addOnActiveSessionsChangedListener(changed, me, h);
            watch(msm.getActiveSessions(me));
        } catch (Exception ignored) {}
    }

    void watch(List<MediaController> list) {
        for (MediaController m : watched) m.unregisterCallback(cb(m));
        watched.clear();
        if (list == null) return;
        for (MediaController m : list) {
            m.registerCallback(cb(m), h);
            watched.add(m);
            History.record(this, m);
        }
    }

    final java.util.Map<String, MediaController.Callback> cbs = new java.util.HashMap<>();

    MediaController.Callback cb(MediaController m) {
        String k = m.getSessionToken().toString();
        MediaController.Callback c = cbs.get(k);
        if (c == null) {
            c = new MediaController.Callback() {
                int lastState = -1;
                String lastTitle = "";
                // only write when playback starts or the title changes (not on every progress tick)
                @Override public void onPlaybackStateChanged(PlaybackState s) {
                    int st = s == null ? -1 : s.getState();
                    if (st == PlaybackState.STATE_PLAYING && lastState != PlaybackState.STATE_PLAYING) History.record(NotifListener.this, m);
                    lastState = st;
                }
                @Override public void onMetadataChanged(MediaMetadata md) {
                    String t = md == null ? "" : String.valueOf(md.getString(MediaMetadata.METADATA_KEY_TITLE));
                    if (t.equals(lastTitle)) return;
                    lastTitle = t;
                    h.postDelayed(() -> History.record(NotifListener.this, m), 4000); // once the new title is playing
                }
            };
            cbs.put(k, c);
        }
        return c;
    }

    @Override public void onListenerDisconnected() {
        try { msm.removeOnActiveSessionsChangedListener(changed); } catch (Exception ignored) {}
        for (MediaController m : watched) m.unregisterCallback(cb(m));
        watched.clear();
        cbs.clear();
        if (thread != null) thread.quitSafely();
    }
}
