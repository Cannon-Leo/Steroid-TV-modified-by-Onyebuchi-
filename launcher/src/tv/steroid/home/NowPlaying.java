package tv.steroid.home;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.List;

/**
 * Music widget for any app that plays media: YouTube, YouTube Music, Spotify, Prime Video, Netflix...
 * Artwork fills the card under a heavy overlay; previous, play/pause, next and close; a progress bar.
 * Buttons send the same media keys a remote would (apps react to those fastest) and the icons change at
 * once, then follow what the app reports. The artwork opens the app. Close pauses the app and puts the
 * widget to sleep until something new plays. Fixed size, so it never moves the home screen.
 */
class NowPlaying extends FrameLayout {
    final Handler h = new Handler();
    MediaSessionManager msm;
    ComponentName listener;
    MediaController ctl;
    final MediaController.Callback cb = new MediaController.Callback() {
        @Override public void onPlaybackStateChanged(PlaybackState s) { h.post(NowPlaying.this::show); }
        @Override public void onMetadataChanged(MediaMetadata m) { h.post(NowPlaying.this::show); }
        @Override public void onSessionDestroyed() { h.post(NowPlaying.this::pick); }
    };
    final MediaSessionManager.OnActiveSessionsChangedListener changed = l -> h.post(this::pick);
    final ImageView art, backdrop;
    final TextView app, title, artist;
    final Icon play;
    final LinearLayout box;
    final Progress bar;
    Bitmap shownArt;
    boolean listening, active, shownPlaying;
    long optimisticUntil;      // after a press, trust our icon for a moment instead of a stale report
    String dismissedKey;       // closed by you: stay asleep until something else plays
    Runnable onChange;
    static final int W = 440, H = 112; // fixed size (dp): artwork never decides how big the widget is

    @Override protected void onMeasure(int w, int h) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(Ui.px(W), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(Ui.px(H), MeasureSpec.EXACTLY));
    }

    NowPlaying(Context c) {
        super(c);
        setClipToOutline(true);
        GradientDrawable bg = Ui.round(0xFF141418, 18);
        bg.setStroke(Ui.px(1), 0x26FFFFFF);
        setBackground(bg);
        backdrop = new ImageView(c);
        backdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);
        addView(backdrop, new LayoutParams(-1, -1));
        View veil = new View(c);
        veil.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{0xE0101014, 0xC7101014}));
        addView(veil, new LayoutParams(-1, -1));
        box = new LinearLayout(c);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setPadding(Ui.px(12), Ui.px(10), Ui.px(10), Ui.px(14));
        addView(box, new LayoutParams(-1, -1));

        // artwork: OK opens the app that is playing
        art = new ImageView(c);
        art.setScaleType(ImageView.ScaleType.CENTER_CROP);
        art.setClipToOutline(true);
        art.setBackground(Ui.round(0x33FFFFFF, 10));
        art.setFocusable(true);
        art.setContentDescription("Open the app");
        art.setOnFocusChangeListener((v, f) -> Ui.lift(v, f, 1.05f));
        art.setOnClickListener(v -> openApp());
        box.addView(art, new LinearLayout.LayoutParams(Ui.px(80), Ui.px(80)));

        LinearLayout t = new LinearLayout(c);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setPadding(Ui.px(14), 0, Ui.px(8), 0);
        app = Ui.label(c, "", 0xB3FFFFFF);
        app.setTextSize(11f);
        title = Ui.text(c, Ui.sansMedium, 14, 0xFFFFFFFF);
        artist = Ui.text(c, Ui.sans, 12, 0xCCFFFFFF);
        for (TextView v : new TextView[]{title, artist}) {
            v.setSingleLine(true);
            v.setEllipsize(android.text.TextUtils.TruncateAt.END);
            v.setPadding(0, Ui.px(4), 0, 0);
            v.setShadowLayer(Ui.px(4), 0, Ui.px(1), 0xCC000000);
        }
        t.addView(app);
        t.addView(title);
        t.addView(artist);
        box.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        box.addView(button(Icon.PREV, () -> key(KeyEvent.KEYCODE_MEDIA_PREVIOUS)));
        play = button(Icon.PAUSE, this::toggle);
        box.addView(play);
        box.addView(button(Icon.NEXT, () -> key(KeyEvent.KEYCODE_MEDIA_NEXT)));
        box.addView(button(Icon.CLOSE, this::close));

        bar = new Progress(c);
        LayoutParams bl = new LayoutParams(-1, Ui.px(3), Gravity.BOTTOM);
        bl.setMargins(Ui.px(14), 0, Ui.px(14), Ui.px(8));
        addView(bar, bl);
    }

    Icon button(int type, Runnable r) {
        Icon b = new Icon(getContext(), type);
        b.setFocusable(true);
        b.setPadding(Ui.px(10), Ui.px(10), Ui.px(10), Ui.px(10));
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(0x00FFFFFF);
        b.setBackground(g);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.px(42), Ui.px(42));
        lp.leftMargin = Ui.px(4);
        b.setLayoutParams(lp);
        b.setOnFocusChangeListener((v, f) -> g.setColor(f ? 0x40FFFFFF : 0x00FFFFFF));
        Selector.circle(b, false);
        b.setOnClickListener(v -> r.run());
        return b;
    }

    // ---------- controls ----------

    /** Send a media key straight to the app, like a remote's play/next buttons. */
    boolean key(int code) {
        if (ctl == null) return false;
        long t = SystemClock.uptimeMillis();
        boolean ok = ctl.dispatchMediaButtonEvent(new KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0));
        ctl.dispatchMediaButtonEvent(new KeyEvent(t, t, KeyEvent.ACTION_UP, code, 0));
        if (!ok) { // the app ignores media keys: use its transport controls instead
            MediaController.TransportControls tc = ctl.getTransportControls();
            switch (code) {
                case KeyEvent.KEYCODE_MEDIA_NEXT: tc.skipToNext(); break;
                case KeyEvent.KEYCODE_MEDIA_PREVIOUS: tc.skipToPrevious(); break;
                case KeyEvent.KEYCODE_MEDIA_PLAY: tc.play(); break;
                case KeyEvent.KEYCODE_MEDIA_PAUSE: tc.pause(); break;
                case KeyEvent.KEYCODE_MEDIA_STOP: tc.stop(); break;
            }
        }
        return true;
    }

    void toggle() {
        if (ctl == null) return;
        boolean wasPlaying = shownPlaying;
        key(wasPlaying ? KeyEvent.KEYCODE_MEDIA_PAUSE : KeyEvent.KEYCODE_MEDIA_PLAY);
        // change the icon now; the app's own report follows
        shownPlaying = !wasPlaying;
        optimisticUntil = SystemClock.uptimeMillis() + 2500;
        play.set(shownPlaying ? Icon.PAUSE : Icon.PLAY);
        bar.playing = shownPlaying;
        if (!wasPlaying) {
            MediaController m = ctl;
            // video apps (YouTube) refuse to play while hidden: then reopen the app right where it was
            h.postDelayed(() -> {
                PlaybackState now = m.getPlaybackState();
                if (now != null && now.getState() == PlaybackState.STATE_PLAYING) return;
                openApp();
            }, 1800);
        }
    }

    void openApp() {
        if (ctl == null) return;
        try { if (ctl.getSessionActivity() != null) { ctl.getSessionActivity().send(); return; } } catch (Exception ignored) {}
        PackageManager pm = getContext().getPackageManager();
        Intent li = pm.getLeanbackLaunchIntentForPackage(ctl.getPackageName());
        if (li == null) li = pm.getLaunchIntentForPackage(ctl.getPackageName());
        if (li != null) getContext().startActivity(li.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    /** Close: pause the app, put the widget to sleep, and free the app's memory if Android allows. */
    void close() {
        if (ctl == null) return;
        MediaController m = ctl;
        String pkg = m.getPackageName();
        key(KeyEvent.KEYCODE_MEDIA_PAUSE);
        try { m.getTransportControls().stop(); } catch (Exception ignored) {}
        dismissedKey = sessionKey(m);
        setActive(false);
        if (pkg.equals(getContext().getPackageName())) return; // our own player just stopped; nothing to free
        h.postDelayed(() -> {
            try { ((ActivityManager) getContext().getSystemService(Context.ACTIVITY_SERVICE)).killBackgroundProcesses(pkg); }
            catch (Exception ignored) {}
        }, 1500);
    }

    static String sessionKey(MediaController m) { return m.getPackageName() + "|" + m.getSessionToken().hashCode(); }

    // ---------- following the apps ----------

    /** Start following media sessions. Safe to call when the permission was never granted. */
    void start() {
        if (listening) { pick(); return; }
        try {
            msm = (MediaSessionManager) getContext().getSystemService(Context.MEDIA_SESSION_SERVICE);
            listener = new ComponentName(getContext(), NotifListener.class);
            msm.addOnActiveSessionsChangedListener(changed, listener);
            listening = true;
            pick();
        } catch (SecurityException e) { setActive(false); }
        h.removeCallbacks(tick);
        h.post(tick);
    }

    void stop() {
        h.removeCallbacks(tick);
        if (!listening) return;
        try { msm.removeOnActiveSessionsChangedListener(changed); } catch (Exception ignored) {}
        if (ctl != null) ctl.unregisterCallback(cb);
        listening = false;
    }

    void pick() {
        List<MediaController> list;
        try { list = msm.getActiveSessions(listener); } catch (Exception e) { setActive(false); return; }
        MediaController best = null;
        for (MediaController m : list) {
            // our radio has its own chip; our background YouTube player shows here like any app
            if (m.getPackageName().equals(getContext().getPackageName()) && !BgPlayer.TAG.equals(m.getTag())) continue;
            PlaybackState s = m.getPlaybackState();
            if (s == null) continue;
            boolean playing = s.getState() == PlaybackState.STATE_PLAYING;
            if (playing && sessionKey(m).equals(dismissedKey)) dismissedKey = null; // you pressed play again
            if (best == null || playing) best = m;
            if (playing) break;
        }
        if (ctl != null && (best == null || !best.getSessionToken().equals(ctl.getSessionToken()))) ctl.unregisterCallback(cb);
        if (best != null && (ctl == null || !best.getSessionToken().equals(ctl.getSessionToken()))) best.registerCallback(cb, h);
        ctl = best;
        show();
    }

    void show() {
        PlaybackState s = ctl == null ? null : ctl.getPlaybackState();
        int st = s == null ? PlaybackState.STATE_NONE : s.getState();
        if (ctl != null && st == PlaybackState.STATE_PLAYING && sessionKey(ctl).equals(dismissedKey)) dismissedKey = null;
        boolean alive = ctl != null && st != PlaybackState.STATE_NONE && st != PlaybackState.STATE_ERROR
            && !sessionKey(ctl).equals(dismissedKey);
        setActive(alive);
        if (!alive) return;
        boolean playing = st == PlaybackState.STATE_PLAYING || st == PlaybackState.STATE_BUFFERING;
        if (SystemClock.uptimeMillis() > optimisticUntil) shownPlaying = playing;
        play.set(shownPlaying ? Icon.PAUSE : Icon.PLAY);
        PackageManager pm = getContext().getPackageManager();
        try { app.setText(pm.getApplicationLabel(pm.getApplicationInfo(ctl.getPackageName(), 0)).toString().toUpperCase()); }
        catch (Exception e) { app.setText("NOW PLAYING"); }
        MediaMetadata m = ctl.getMetadata();
        String ti = m == null ? null : m.getString(MediaMetadata.METADATA_KEY_TITLE);
        String ar = m == null ? null : m.getString(MediaMetadata.METADATA_KEY_ARTIST);
        if (ar == null && m != null) ar = m.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE);
        title.setText(ti == null ? "Playing" : ti);
        artist.setText(ar == null ? "" : ar);
        Bitmap b = m == null ? null : m.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
        if (b == null && m != null) b = m.getBitmap(MediaMetadata.METADATA_KEY_ART);
        if (b == null && m != null) b = m.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);
        if (b != shownArt) {
            shownArt = b;
            Bitmap small = null;
            if (b != null) {
                float k = Math.min(1f, 240f / Math.max(1, Math.max(b.getWidth(), b.getHeight())));
                small = k < 1f ? Bitmap.createScaledBitmap(b, Math.round(b.getWidth() * k), Math.round(b.getHeight() * k), true) : b;
            }
            art.setImageBitmap(small);
            backdrop.setImageBitmap(small);
        }
        bar.duration = m == null ? 0 : m.getLong(MediaMetadata.METADATA_KEY_DURATION);
        bar.state = s;
        bar.playing = shownPlaying;
        bar.invalidate();
    }

    void setActive(boolean a) {
        if (a == active) return;
        active = a;
        if (!a && findFocus() != null) { View n = focusSearch(FOCUS_DOWN); if (n != null) n.requestFocus(); }
        if (onChange != null) onChange.run();
    }

    /** Twice a second while showing: keeps the progress bar moving and catches apps that report late. */
    final Runnable tick = new Runnable() { public void run() {
        if (active && isShown()) {
            if (SystemClock.uptimeMillis() > optimisticUntil && ctl != null) {
                PlaybackState s = ctl.getPlaybackState();
                boolean p = s != null && (s.getState() == PlaybackState.STATE_PLAYING || s.getState() == PlaybackState.STATE_BUFFERING);
                if (p != shownPlaying) show();
            }
            bar.invalidate();
        }
        h.postDelayed(this, 500);
    }};

    /** Thin progress line: where the song or video is, from what the app reports. */
    static class Progress extends View {
        final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG), fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        long duration;
        PlaybackState state;
        boolean playing;
        Progress(Context c) { super(c); track.setColor(0x33FFFFFF); fill.setColor(0xF2FFFFFF); }
        @Override protected void onDraw(Canvas c) {
            if (duration <= 0 || state == null) return;
            long pos = state.getPosition();
            if (playing && state.getState() == PlaybackState.STATE_PLAYING)
                pos += (long) ((SystemClock.elapsedRealtime() - state.getLastPositionUpdateTime()) * state.getPlaybackSpeed());
            float f = Math.max(0, Math.min(1, pos / (float) duration));
            float w = getWidth(), hh = getHeight(), r = hh / 2;
            c.drawRoundRect(0, 0, w, hh, r, r, track);
            c.drawRoundRect(0, 0, Math.max(hh, w * f), hh, r, r, fill);
        }
    }
}
