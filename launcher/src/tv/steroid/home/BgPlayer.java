package tv.steroid.home;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PixelFormat;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Keeps YouTube and YouTube Music playing after you leave them. Both TV apps stop their own player
 * when they go to the background, and nothing outside them can change that. So when you press Home
 * while one of them is playing, the launcher picks the same video up at the same second in its own
 * background player (YouTube's official embedded player, in an invisible window) and then carries on
 * with YouTube's mix of similar tracks. It shows up in the music widget like any player: play, pause,
 * next, previous, close. Open goes back to the app at the current track, and our player steps aside
 * the moment any other app starts playing.
 */
public class BgPlayer extends Service {
    static final String TAG = "steroid-bg";
    static final String YT = "com.google.android.youtube.tv", YTM = "com.google.android.youtube.tvmusic";
    static volatile BgPlayer running;

    // ---------- handoff: what YouTube was playing when it stopped ----------
    static String lastPkg, lastId, lastTitle, lastArtist;
    static long lastPos, stoppedAt, homeAt;
    /** Set when you close things on purpose (Close all, the widget's close): no pick up until then. */
    static volatile long suppressUntil;

    static boolean isYouTube(String pkg) { return YT.equals(pkg) || YTM.equals(pkg) || "com.google.android.youtube".equals(pkg)
        || "com.google.android.apps.youtube.music".equals(pkg); }

    static boolean enabled(Context c) { return Prefs.on(c, "ytHandoff", true); }

    /** From the session watcher: YouTube stopped playing (it was playing at position pos). */
    static synchronized void stopped(Context c, String pkg, String id, String title, String artist, long pos) {
        lastPkg = pkg; lastId = id; lastTitle = title; lastArtist = artist; lastPos = pos;
        stoppedAt = System.currentTimeMillis();
        maybe(c);
    }

    /** From the home screen: it just came to the front. */
    static synchronized void homeShown(Context c) { homeAt = System.currentTimeMillis(); maybe(c); }

    /** Pick up only when YouTube stopped because you went Home (not a pause you pressed earlier). */
    static void maybe(Context c) {
        if (!enabled(c) || stoppedAt == 0 || homeAt == 0 || running != null) return;
        if (System.currentTimeMillis() < suppressUntil) { stoppedAt = 0; return; }
        long d = stoppedAt - homeAt;
        if (d < -2500 || d > 4000) return;
        if (!Settings.canDrawOverlays(c)) return;
        String id = lastId, title = lastTitle, artist = lastArtist, pkg = lastPkg;
        long pos = lastPos;
        stoppedAt = 0;
        Intent i = new Intent(c, BgPlayer.class).putExtra("pkg", pkg).putExtra("id", id).putExtra("title", title)
            .putExtra("artist", artist).putExtra("pos", pos);
        try { c.startForegroundService(i); } catch (Exception ignored) {}
    }

    /** Another app started playing: step aside. */
    static void otherPlaying() { BgPlayer b = running; if (b != null) b.h.post(b::stopSelfNow); }

    // ---------- the player ----------

    final Handler h = new Handler(Looper.getMainLooper());
    WebView web;
    MediaSession session;
    AudioManager am;
    AudioFocusRequest focus;
    String pkg, vid, title = "", artist = "", artFor = "";
    Bitmap art;
    long pos, dur;
    int state = -1;
    boolean metaDirty = true;

    @Override public int onStartCommand(Intent in, int flags, int startId) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm.getNotificationChannel("bg") == null)
            nm.createNotificationChannel(new NotificationChannel("bg", "Background music", NotificationManager.IMPORTANCE_LOW));
        startForeground(3, new Notification.Builder(this, "bg").setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Playing in the background").build());
        if (in == null || running != null) return START_NOT_STICKY;
        running = this;
        pkg = in.getStringExtra("pkg");
        title = nz(in.getStringExtra("title"));
        artist = nz(in.getStringExtra("artist"));
        long start = in.getLongExtra("pos", 0);
        String id = in.getStringExtra("id");
        setupSession();
        publish(PlaybackState.STATE_BUFFERING);
        if (id != null && id.matches("[A-Za-z0-9_-]{11}")) begin(id, start);
        else new Thread(() -> { // the app didn't share the video id: find the track by its name
            String found = search(title + " " + artist);
            h.post(() -> { if (found != null) begin(found, found.equals(id) ? start : 0); else stopSelfNow(); });
        }).start();
        return START_NOT_STICKY;
    }

    static String nz(String s) { return s == null ? "" : s; }

    void begin(String id, long startMs) {
        vid = id;
        am = (AudioManager) getSystemService(AUDIO_SERVICE);
        focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setOnAudioFocusChangeListener(ch -> {
                if (ch == AudioManager.AUDIOFOCUS_LOSS) stopSelfNow();          // another player took over
                else if (ch == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) js("cmd('pause')");
                else if (ch == AudioManager.AUDIOFOCUS_GAIN) js("cmd('play')");
            }, h).build();
        am.requestAudioFocus(focus);
        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setDomStorageEnabled(true);
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient());
        web.addJavascriptInterface(new Bridge(), "Native");
        // an invisible 2 x 2 window keeps the player alive and playing while other apps are in front
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(2, 2,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE, PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        try { ((WindowManager) getSystemService(WINDOW_SERVICE)).addView(web, lp); }
        catch (Exception e) { stopSelfNow(); return; }
        web.loadDataWithBaseURL("https://steroid.home/#" + id + "@" + (startMs / 1000), asset("bgplayer.html"), "text/html", "utf-8", null);
    }

    void setupSession() {
        session = new MediaSession(this, TAG);
        session.setCallback(new MediaSession.Callback() {
            @Override public void onPlay() { am.requestAudioFocus(focus); js("cmd('play')"); }
            @Override public void onPause() { js("cmd('pause')"); }
            @Override public void onSkipToNext() { js("cmd('next')"); }
            @Override public void onSkipToPrevious() { js("cmd('prev')"); }
            @Override public void onSeekTo(long p) { js("cmd('seek'," + p + ")"); }
            @Override public void onStop() { stopSelfNow(); }
        }, h);
        session.setActive(true);
        sessionActivity();
    }

    /** "Open" in the widget: back to the app, at the track playing now. */
    void sessionActivity() {
        Intent i = vid == null ? getPackageManager().getLeanbackLaunchIntentForPackage(pkg == null ? YTM : pkg)
            : History.link(pkg == null ? YTM : pkg, vid);
        if (i == null) return;
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        session.setSessionActivity(PendingIntent.getActivity(this, 7, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
    }

    void publish(int st) {
        if (session == null) return;
        session.setPlaybackState(new PlaybackState.Builder()
            .setActions(PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_PLAY_PAUSE
                | PlaybackState.ACTION_SKIP_TO_NEXT | PlaybackState.ACTION_SKIP_TO_PREVIOUS | PlaybackState.ACTION_SEEK_TO | PlaybackState.ACTION_STOP)
            .setState(st, pos, st == PlaybackState.STATE_PLAYING ? 1f : 0f).build());
        if (metaDirty) {
            metaDirty = false;
            MediaMetadata.Builder m = new MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, title.isEmpty() ? "YouTube" : title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, artist + (artist.isEmpty() ? "" : " · ") + "kept playing")
                .putString(MediaMetadata.METADATA_KEY_MEDIA_ID, vid == null ? "" : vid)
                .putLong(MediaMetadata.METADATA_KEY_DURATION, dur);
            if (art != null) m.putBitmap(MediaMetadata.METADATA_KEY_ART, art);
            session.setMetadata(m.build());
        }
    }

    void loadArt(String id) {
        if (id == null || id.equals(artFor)) return;
        artFor = id;
        new Thread(() -> {
            try (InputStream in = new URL("https://i.ytimg.com/vi/" + id + "/hqdefault.jpg").openStream()) {
                Bitmap b = BitmapFactory.decodeStream(in);
                h.post(() -> { if (id.equals(artFor)) { art = b; metaDirty = true; publish(state == 1 ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED); } });
            } catch (Exception ignored) {}
        }).start();
    }

    class Bridge {
        @JavascriptInterface public void tick(int s, String id, String t, String a, long p, long d) {
            h.post(() -> {
                if (running != BgPlayer.this) return;
                if (id != null && id.matches("[A-Za-z0-9_-]{11}") && !id.equals(vid)) { vid = id; sessionActivity(); metaDirty = true; }
                if (t != null && !t.isEmpty() && !t.equals(title)) { title = t; artist = a == null ? "" : a; metaDirty = true; }
                if (d != dur) { dur = d; metaDirty = true; }
                loadArt(vid);
                pos = p;
                state = s;
                publish(s == 1 ? PlaybackState.STATE_PLAYING : s == 3 || s == -1 ? PlaybackState.STATE_BUFFERING : PlaybackState.STATE_PAUSED);
            });
        }
        @JavascriptInterface public void error(int code) { h.post(() -> stopSelfNow()); } // can't be played here: leave the Resume card
    }

    void js(String code) { if (web != null) web.evaluateJavascript(code, null); }

    void stopSelfNow() {
        if (running == this) running = null;
        h.removeCallbacksAndMessages(null);
        if (web != null) {
            try { ((WindowManager) getSystemService(WINDOW_SERVICE)).removeView(web); } catch (Exception ignored) {}
            web.loadUrl("about:blank");
            web.destroy();
            web = null;
        }
        if (am != null && focus != null) am.abandonAudioFocusRequest(focus);
        if (session != null) {
            session.setPlaybackState(new PlaybackState.Builder().setState(PlaybackState.STATE_STOPPED, pos, 0).build());
            session.setActive(false);
            session.release();
            session = null;
        }
        stopForeground(true);
        stopSelf();
    }

    @Override public void onDestroy() { if (running == this) stopSelfNow(); super.onDestroy(); }
    @Override public IBinder onBind(Intent i) { return null; }

    String asset(String name) {
        try (InputStream in = getAssets().open(name)) {
            byte[] b = new byte[in.available()];
            int n = in.read(b);
            return new String(b, 0, n, "UTF-8");
        } catch (Exception e) { return ""; }
    }

    /** First video for a search on YouTube (used when the app doesn't share the track's id). */
    static String search(String q) {
        if (q.trim().isEmpty()) return null;
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL("https://www.youtube.com/results?search_query=" + Uri.encode(q.trim())).openConnection();
            conn.setConnectTimeout(6000);
            conn.setReadTimeout(8000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/155.0.0.0 Safari/537.36");
            conn.setRequestProperty("Cookie", "CONSENT=YES+1");
            StringBuilder sb = new StringBuilder();
            try (InputStream in = conn.getInputStream()) {
                byte[] b = new byte[16384];
                int n;
                while ((n = in.read(b)) > 0 && sb.length() < 1_200_000) sb.append(new String(b, 0, n, "UTF-8"));
            }
            Matcher m = Pattern.compile("\"videoRenderer\":\\{\"videoId\":\"([A-Za-z0-9_-]{11})\"").matcher(sb);
            return m.find() ? m.group(1) : null;
        } catch (Exception e) {
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }
}
