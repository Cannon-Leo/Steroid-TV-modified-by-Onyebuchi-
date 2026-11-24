package tv.steroid.home;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;

/**
 * Control Centre, laid out like iOS and HyperOS: a grid of rounded modules on frosted glass.
 * Top: the connectivity module (round toggles that light up when on) beside the now playing module.
 * Then a tall volume slider beside square shortcuts, then a row of launcher buttons. Toggles show
 * their state in colour (white tiles mean on), and everything refreshes while the panel is open.
 */
final class QuickPanel extends FrameLayout {
    static final int BLUE = 0xFF0A84FF, ORANGE = 0xFFFF9F0A, PURPLE = 0xFF5E5CE6, MODULE = 0x2EFFFFFF;
    static final int C = 86, G = 12; // one grid cell and the gap between cells (dp)
    final Home home;
    final FrameLayout panel;
    final LinearLayout list;
    final Handler h = new Handler(Looper.getMainLooper());
    final List<Runnable> refreshers = new ArrayList<>();
    final AudioManager am;
    View first;

    QuickPanel(Home hm) {
        super(hm);
        home = hm;
        am = (AudioManager) hm.getSystemService(Context.AUDIO_SERVICE);
        setBackgroundColor(0x99000000); // dim the page so the panel reads clearly
        setClickable(true);
        panel = new FrameLayout(hm);
        Glass.apply(panel, 32, 0xCC0E0E12); // dark enough tint for white text over any photo
        LayoutParams lp = new LayoutParams(Ui.px(4 * C + 3 * G + 44), -1, Gravity.END);
        lp.setMargins(0, Ui.px(10), Ui.px(14), Ui.px(10));
        addView(panel, lp);
        // the content scrolls inside the panel; the focused item always stays in view
        Rails.Page scroll = new Rails.Page(hm, Ui.px(24));
        scroll.setClipToPadding(false);
        scroll.setPadding(0, Ui.px(22), 0, Ui.px(18));
        list = new LinearLayout(hm);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(Ui.px(22), 0, Ui.px(22), 0);
        list.setClipChildren(false);
        scroll.addView(list, new FrameLayout.LayoutParams(-1, -2));
        panel.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
        build();
        h.post(refresh);
    }

    final Runnable refresh = new Runnable() { public void run() {
        for (Runnable r : refreshers) r.run();
        h.postDelayed(this, 1000);
    }};

    // ---------- state ----------

    boolean wifiOn() {
        WifiManager w = (WifiManager) home.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        return w != null && w.isWifiEnabled();
    }
    boolean wifiConnected() {
        ConnectivityManager cm = (ConnectivityManager) home.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkCapabilities nc = cm == null ? null : cm.getNetworkCapabilities(cm.getActiveNetwork());
        return nc != null && nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
    }
    boolean ethernet() {
        ConnectivityManager cm = (ConnectivityManager) home.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkCapabilities nc = cm == null ? null : cm.getNetworkCapabilities(cm.getActiveNetwork());
        return nc != null && nc.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET);
    }
    static boolean btOn() { BluetoothAdapter b = BluetoothAdapter.getDefaultAdapter(); return b != null && b.isEnabled(); }
    boolean muted() { return am.isStreamMute(AudioManager.STREAM_MUSIC) || am.getStreamVolume(AudioManager.STREAM_MUSIC) == 0; }
    boolean dreamOn() { return Settings.Secure.getInt(home.getContentResolver(), "screensaver_enabled", 1) == 1; }

    // ---------- layout ----------

    int cell(int n) { return Ui.px(n * C + (n - 1) * G); }

    LinearLayout.LayoutParams at(int w, int h, boolean gapLeft) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w, h);
        if (gapLeft) lp.leftMargin = Ui.px(G);
        return lp;
    }

    LinearLayout row(boolean gapTop) {
        LinearLayout r = new LinearLayout(getContext());
        r.setClipChildren(false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        if (gapTop) lp.topMargin = Ui.px(G);
        list.addView(r, lp);
        return r;
    }

    void build() {
        Context c = getContext();
        // row 1: connectivity | now playing
        LinearLayout r1 = row(false);
        LinearLayout conn = module(c, 26);
        conn.setPadding(Ui.px(10), Ui.px(12), Ui.px(10), Ui.px(8));
        r1.addView(conn, at(cell(2), cell(2), false));
        LinearLayout a = new LinearLayout(c), b = new LinearLayout(c);
        a.setClipChildren(false);
        b.setClipChildren(false);
        conn.addView(a, new LinearLayout.LayoutParams(-1, 0, 1));
        conn.addView(b, new LinearLayout.LayoutParams(-1, 0, 1));
        first = toggle(a, "Wi‑Fi", BLUE, this::wifiOn, () -> wifiOn() ? Icon.WIFI : Icon.WIFI_OFF,
            () -> ethernet() ? "Ethernet" : !wifiOn() ? "Off" : wifiConnected() ? "Connected" : "On",
            () -> open(new Intent(Settings.ACTION_WIFI_SETTINGS)), () -> open(new Intent(Settings.ACTION_WIFI_SETTINGS)));
        toggle(a, "Bluetooth", BLUE, QuickPanel::btOn, () -> btOn() ? Icon.BLUETOOTH : Icon.BT_OFF, () -> btOn() ? "On" : "Off",
            this::flipBluetooth, () -> open(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)));
        toggle(b, "Mute", ORANGE, this::muted, () -> muted() ? Icon.MUTE : Icon.SOUND, () -> muted() ? "On" : "Off",
            () -> am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, 0),
            () -> open(new Intent(Settings.ACTION_SOUND_SETTINGS)));
        toggle(b, "Screensaver", PURPLE, this::dreamOn, () -> Icon.MOON, () -> dreamOn() ? "Start" : "Off",
            () -> open(new Intent(Intent.ACTION_MAIN).setClassName("com.android.systemui", "com.android.systemui.Somnambulator")),
            () -> open(new Intent(Settings.ACTION_DREAM_SETTINGS)));
        r1.addView(media(c), at(cell(2), cell(2), true));

        // row 2: tall volume | 3 x 2 shortcuts
        LinearLayout r2 = row(true);
        r2.addView(volume(c), at(cell(1), cell(2), false));
        LinearLayout grid = new LinearLayout(c);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setClipChildren(false);
        r2.addView(grid, at(cell(3), cell(2), true));
        LinearLayout g1 = new LinearLayout(c), g2 = new LinearLayout(c);
        g1.setClipChildren(false);
        g2.setClipChildren(false);
        grid.addView(g1, new LinearLayout.LayoutParams(-1, cell(1)));
        LinearLayout.LayoutParams g2l = new LinearLayout.LayoutParams(-1, cell(1));
        g2l.topMargin = Ui.px(G);
        grid.addView(g2, g2l);
        square(g1, false, Icon.MONITOR, "Display", null, () -> open(new Intent(Settings.ACTION_DISPLAY_SETTINGS)));
        square(g1, true, Icon.RECENTS, "Recents", null, () -> open(new Intent(c, Recents.class)));
        square(g1, true, Icon.INPUT, "Inputs", null, () -> open(new Intent("android.settings.TV_INPUT_SETTINGS")));
        square(g2, false, Icon.APP, "Apps", null, () -> open(new Intent(Settings.ACTION_APPLICATION_SETTINGS)));
        square(g2, true, Icon.TIMER, "Date & time", null, () -> open(new Intent(Settings.ACTION_DATE_SETTINGS)));
        square(g2, true, Icon.GEAR, "Settings", null, () -> open(new Intent(Settings.ACTION_SETTINGS)));

        // row 3: launcher
        LinearLayout r3 = row(true);
        square(r3, false, Icon.USER, "Launcher", null, () -> open(new Intent(c, Personal.class)));
        square(r3, true, Icon.GEMINI, "Glass", () -> Prefs.on(c, "glassPanels", true), () -> {
            Prefs.put(c, "glassPanels", !Prefs.on(c, "glassPanels", true));
            h.postDelayed(home::recreate, 250); // every panel repaints glass or solid
        });
        square(r3, true, Icon.COMPASS, "Tour", null, () -> { close(); home.h.postDelayed(home::startTour, 300); });
        square(r3, true, Icon.SHIELD, "Privacy", null, () -> open(new Intent(Settings.ACTION_PRIVACY_SETTINGS)));
        // a mini player stuck on screen: back to full screen, for any app
        LinearLayout r4 = new LinearLayout(c);
        r4.setClipChildren(false);
        LinearLayout.LayoutParams r4l = new LinearLayout.LayoutParams(-1, -2);
        r4l.topMargin = Ui.px(G);
        list.addView(r4, r4l);
        square(r4, false, Icon.MONITOR, "Mini player", null, () -> new Thread(() -> {
            String p = PipWatch.pinned(c);
            h.post(() -> {
                if (p == null) Toast.makeText(c, "No mini player on screen", Toast.LENGTH_SHORT).show();
                else { close(); PipWatch.full(c, p); }
            });
        }).start());

        TextView tip = Ui.text(c, Ui.sans, 12, 0x8CFFFFFF);
        tip.setText("OK switches · hold OK for settings · on the volume, up and down");
        tip.setGravity(Gravity.CENTER);
        tip.setPadding(0, Ui.px(14), 0, 0);
        list.addView(tip, new LinearLayout.LayoutParams(cell(4), -2));
    }

    LinearLayout module(Context c, int radius) {
        LinearLayout g = new LinearLayout(c);
        g.setOrientation(LinearLayout.VERTICAL);
        g.setClipChildren(false);
        g.setBackground(Ui.round(MODULE, radius));
        return g;
    }

    /** A round toggle inside the connectivity module: lit in its colour when on. */
    View toggle(LinearLayout row, String name, int onColor, java.util.function.BooleanSupplier isOn,
                java.util.function.IntSupplier icon, java.util.function.Supplier<String> state, Runnable ok, Runnable longOk) {
        Context c = getContext();
        LinearLayout cell = new LinearLayout(c);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER);
        cell.setClipChildren(false);
        FrameLayout btn = new FrameLayout(c);
        btn.setFocusable(true);
        btn.setContentDescription(name);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        btn.setBackground(bg);
        Icon i = new Icon(c, icon.getAsInt());
        btn.addView(i, new FrameLayout.LayoutParams(Ui.px(24), Ui.px(24), Gravity.CENTER));
        Selector.circle(btn, false);
        cell.addView(btn, new LinearLayout.LayoutParams(Ui.px(52), Ui.px(52)));
        TextView n = Ui.text(c, Ui.sansMedium, 12, 0xFFFFFFFF);
        n.setText(name);
        n.setGravity(Gravity.CENTER);
        n.setSingleLine(true);
        n.setPadding(0, Ui.px(6), 0, 0);
        cell.addView(n, new LinearLayout.LayoutParams(-1, -2));
        TextView s = Ui.text(c, Ui.sans, 11, 0x99FFFFFF);
        s.setGravity(Gravity.CENTER);
        s.setSingleLine(true);
        cell.addView(s, new LinearLayout.LayoutParams(-1, -2));
        row.addView(cell, new LinearLayout.LayoutParams(0, -1, 1));
        Runnable paint = () -> {
            boolean on = isOn.getAsBoolean();
            bg.setColor(on ? onColor : 0x40FFFFFF);
            i.setImageResource(Icon.RES[icon.getAsInt()]);
            i.color(0xFFFFFFFF);
            s.setText(state.get());
        };
        paint.run();
        refreshers.add(paint);
        btn.setOnClickListener(v -> { ok.run(); h.postDelayed(paint, 250); h.postDelayed(paint, 1200); });
        btn.setOnLongClickListener(v -> { longOk.run(); return true; });
        return btn;
    }

    /** Square button (1 x 1). With isOn it is a toggle: white with a dark icon when on, like iOS. */
    void square(LinearLayout row, boolean gapLeft, int icon, String name, java.util.function.BooleanSupplier isOn, Runnable run) {
        Context c = getContext();
        LinearLayout b = new LinearLayout(c);
        b.setOrientation(LinearLayout.VERTICAL);
        b.setGravity(Gravity.CENTER);
        b.setFocusable(true);
        b.setContentDescription(name);
        GradientDrawable bg = Ui.round(MODULE, 20);
        b.setBackground(bg);
        Icon i = new Icon(c, icon);
        b.addView(i, new LinearLayout.LayoutParams(Ui.px(26), Ui.px(26)));
        TextView n = Ui.text(c, Ui.sans, 12, 0xD9FFFFFF);
        n.setText(name);
        n.setGravity(Gravity.CENTER);
        n.setSingleLine(true);
        n.setEllipsize(android.text.TextUtils.TruncateAt.END);
        n.setPadding(Ui.px(4), Ui.px(8), Ui.px(4), 0);
        b.addView(n, new LinearLayout.LayoutParams(-1, -2));
        Selector.shape(b, Ui.px(20), false);
        Runnable paint = () -> {
            boolean on = isOn != null && isOn.getAsBoolean();
            bg.setColor(on ? 0xF2FFFFFF : MODULE);
            i.color(on ? 0xFF0E0E12 : 0xFFFFFFFF);
            n.setTextColor(on ? 0xFF0E0E12 : 0xD9FFFFFF);
        };
        paint.run();
        if (isOn != null) refreshers.add(paint);
        b.setOnClickListener(v -> { run.run(); h.postDelayed(paint, 150); });
        row.addView(b, at(cell(1), cell(1), gapLeft));
    }

    /** Now playing module: what plays in the music widget, with previous, play or pause, next. */
    View media(Context c) {
        FrameLayout m = new FrameLayout(c);
        m.setBackground(Ui.round(MODULE, 26));
        m.setClipChildren(false);
        LinearLayout v = new LinearLayout(c);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setPadding(Ui.px(16), Ui.px(14), Ui.px(16), Ui.px(12));
        v.setClipChildren(false);
        m.addView(v, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout top = new LinearLayout(c);
        top.setGravity(Gravity.CENTER_VERTICAL);
        android.widget.ImageView art = new android.widget.ImageView(c);
        art.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
        art.setClipToOutline(true);
        art.setBackground(Ui.round(0x33FFFFFF, 10));
        top.addView(art, new LinearLayout.LayoutParams(Ui.px(46), Ui.px(46)));
        TextView app = Ui.label(c, "", 0x99FFFFFF);
        app.setPadding(Ui.px(10), 0, 0, 0);
        top.addView(app, new LinearLayout.LayoutParams(0, -2, 1));
        v.addView(top);
        TextView title = Ui.text(c, Ui.sansMedium, 15, 0xFFFFFFFF);
        title.setMaxLines(2);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        title.setPadding(0, Ui.px(10), 0, 0);
        v.addView(title);
        TextView sub = Ui.text(c, Ui.sans, 12, 0x99FFFFFF);
        sub.setSingleLine(true);
        sub.setEllipsize(android.text.TextUtils.TruncateAt.END);
        v.addView(sub);
        v.addView(new View(c), new LinearLayout.LayoutParams(1, 0, 1));
        LinearLayout ctl = new LinearLayout(c);
        ctl.setGravity(Gravity.CENTER);
        ctl.setClipChildren(false);
        v.addView(ctl, new LinearLayout.LayoutParams(-1, -2));
        View prev = round(c, Icon.PREV, () -> np(android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS));
        Icon playIcon = new Icon(c, Icon.PLAY);
        View play = round(c, playIcon, () -> { NowPlaying n = home.nowPlaying; if (n != null && n.active) n.toggle(); else home.pickMusic(); });
        View next = round(c, Icon.NEXT, () -> np(android.view.KeyEvent.KEYCODE_MEDIA_NEXT));
        ctl.addView(prev, at(Ui.px(40), Ui.px(40), false));
        ctl.addView(play, at(Ui.px(44), Ui.px(44), true));
        ctl.addView(next, at(Ui.px(40), Ui.px(40), true));
        Runnable paint = () -> {
            NowPlaying n = home.nowPlaying;
            boolean on = n != null && n.active && n.ctl != null;
            prev.setVisibility(on ? VISIBLE : INVISIBLE);
            next.setVisibility(on ? VISIBLE : INVISIBLE);
            playIcon.setImageResource(Icon.RES[on && n.shownPlaying ? Icon.PAUSE : on ? Icon.PLAY : Icon.MUSIC]);
            if (!on) {
                app.setText("Music");
                title.setText("Not playing");
                sub.setText("OK to choose an app");
                art.setImageResource(Icon.RES[Icon.MUSIC]);
                art.setPadding(Ui.px(11), Ui.px(11), Ui.px(11), Ui.px(11));
                return;
            }
            android.media.MediaMetadata md = n.ctl.getMetadata();
            String t = md == null ? null : md.getString(android.media.MediaMetadata.METADATA_KEY_TITLE);
            String ar = md == null ? null : md.getString(android.media.MediaMetadata.METADATA_KEY_ARTIST);
            android.graphics.Bitmap bm = md == null ? null : md.getBitmap(android.media.MediaMetadata.METADATA_KEY_ART);
            if (bm == null && md != null) bm = md.getBitmap(android.media.MediaMetadata.METADATA_KEY_ALBUM_ART);
            try { app.setText(home.getPackageManager().getApplicationLabel(home.getPackageManager().getApplicationInfo(n.ctl.getPackageName(), 0))); }
            catch (Exception e) { app.setText("Now playing"); }
            if (n.ctl.getPackageName().equals(home.getPackageName())) app.setText("YouTube");
            title.setText(t == null ? "Playing" : t);
            sub.setText(ar == null ? "" : ar);
            if (bm != null) { art.setPadding(0, 0, 0, 0); art.setImageBitmap(bm); }
            else { art.setImageResource(Icon.RES[Icon.MUSIC]); art.setPadding(Ui.px(11), Ui.px(11), Ui.px(11), Ui.px(11)); }
        };
        paint.run();
        refreshers.add(paint);
        return m;
    }

    void np(int key) { NowPlaying n = home.nowPlaying; if (n != null && n.active) n.key(key); }

    View round(Context c, int icon, Runnable run) { return round(c, new Icon(c, icon), run); }

    View round(Context c, Icon i, Runnable run) {
        FrameLayout b = new FrameLayout(c);
        b.setFocusable(true);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(0x33FFFFFF);
        b.setBackground(bg);
        b.addView(i, new FrameLayout.LayoutParams(Ui.px(20), Ui.px(20), Gravity.CENTER));
        Selector.circle(b, false);
        b.setOnClickListener(v -> { run.run(); h.postDelayed(() -> { for (Runnable r : refreshers) r.run(); }, 400); });
        return b;
    }

    void flipBluetooth() {
        BluetoothAdapter b = BluetoothAdapter.getDefaultAdapter();
        try {
            if (b == null) throw new IllegalStateException();
            boolean ok = b.isEnabled() ? b.disable() : b.enable();
            if (!ok) throw new IllegalStateException();
        } catch (Exception e) { open(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)); }
    }

    /** Tall slider like iOS: select it, then up and down set the volume; OK mutes. */
    View volume(Context c) {
        FrameLayout f = new FrameLayout(c);
        f.setFocusable(true);
        f.setContentDescription("Volume");
        f.setBackground(Ui.round(0x00000000, 24));
        View slider = new View(c) {
            final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            final android.graphics.Path clip = new android.graphics.Path();
            @Override protected void onDraw(Canvas k) {
                float w = getWidth(), hh = getHeight(), r = Ui.px(24);
                int max = Math.max(1, am.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
                float v = muted() ? 0 : am.getStreamVolume(AudioManager.STREAM_MUSIC) / (float) max;
                p.setColor(MODULE);
                k.drawRoundRect(0, 0, w, hh, r, r, p);
                k.save();
                clip.reset();
                clip.addRoundRect(0, 0, w, hh, r, r, android.graphics.Path.Direction.CW);
                k.clipPath(clip);
                p.setColor(0xF2FFFFFF);
                k.drawRect(0, hh * (1 - v), w, hh, p);
                k.restore();
            }
        };
        f.addView(slider, new FrameLayout.LayoutParams(-1, -1));
        Icon i = new Icon(c, Icon.SOUND);
        FrameLayout.LayoutParams il = new FrameLayout.LayoutParams(Ui.px(24), Ui.px(24), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        il.bottomMargin = Ui.px(16);
        f.addView(i, il);
        TextView val = Ui.text(c, Ui.mono, 14, 0xFFFFFFFF);
        val.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams vl = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP);
        vl.topMargin = Ui.px(14);
        f.addView(val, vl);
        Selector.shape(f, Ui.px(24), false);
        Runnable paint = () -> {
            int vol = am.getStreamVolume(AudioManager.STREAM_MUSIC), max = Math.max(1, am.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
            boolean m = muted();
            val.setText(m ? "Off" : String.valueOf(vol));
            val.setTextColor(!m && vol / (float) max > 0.86f ? 0xFF0E0E12 : 0xFFFFFFFF);
            i.setImageResource(Icon.RES[m ? Icon.MUTE : Icon.SOUND]);
            i.color(!m && vol > 0 ? 0xFF0E0E12 : 0xFFFFFFFF);
            slider.invalidate();
        };
        paint.run();
        refreshers.add(paint);
        f.setOnKeyListener((v, code, e) -> {
            if (e.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (code != KeyEvent.KEYCODE_DPAD_UP && code != KeyEvent.KEYCODE_DPAD_DOWN) return false;
            am.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                code == KeyEvent.KEYCODE_DPAD_UP ? AudioManager.ADJUST_RAISE : AudioManager.ADJUST_LOWER, 0);
            paint.run();
            return true;
        });
        f.setOnClickListener(v -> { am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, 0); h.postDelayed(paint, 150); });
        return f;
    }

    void open(Intent i) {
        try { home.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); close(); }
        catch (Exception e) { Toast.makeText(getContext(), "Not available on this TV", Toast.LENGTH_SHORT).show(); }
    }

    // ---------- open and close ----------

    static QuickPanel open(Home h, FrameLayout root, ViewGroup page) {
        QuickPanel q = new QuickPanel(h);
        page.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS); // focus stays inside the panel
        root.addView(q, root.indexOfChild(h.selector), new LayoutParams(-1, -1)); // under the ring
        q.setAlpha(0f);
        q.animate().alpha(1f).setDuration(200).start(); // fade only: the ring never chases a moving panel
        q.post(() -> { if (q.first != null) q.first.requestFocus(); h.selector.kick(); });
        h.quick = q;
        h.quickPage = page;
        return q;
    }

    void close() {
        if (home.quick != this) return;
        home.quick = null;
        h.removeCallbacksAndMessages(null);
        home.quickPage.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        animate().alpha(0f).setDuration(140).withEndAction(() -> {
            ViewGroup p = (ViewGroup) getParent();
            if (p != null) p.removeView(this);
            home.selector.kick();
        }).start();
        if (home.tour != null) home.tour.resume(); // back to the tour if it sent you here
        else if (home.quickFrom != null) home.quickFrom.requestFocus();
    }
}
