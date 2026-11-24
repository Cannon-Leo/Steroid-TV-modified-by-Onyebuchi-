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
 * Quick settings in the spirit of a phone's control centre: a scrolling glass panel with round
 * toggles that light up when on (Wi‑Fi, Bluetooth, Mute, Screensaver), a tall volume slider,
 * shortcut buttons, then grouped launcher and system options. States refresh while it is open.
 */
final class QuickPanel extends FrameLayout {
    static final int BLUE = 0xFF3B82F6, ORANGE = 0xFFFF9F0A, PURPLE = 0xFF8E6BFF;
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
        LayoutParams lp = new LayoutParams(Ui.px(560), -1, Gravity.END);
        lp.setMargins(0, Ui.px(20), Ui.px(20), Ui.px(20));
        addView(panel, lp);
        // the content scrolls inside the panel; the focused item always stays in view
        Rails.Page scroll = new Rails.Page(hm, Ui.px(40));
        scroll.setClipToPadding(false);
        scroll.setPadding(0, Ui.px(30), 0, Ui.px(30));
        list = new LinearLayout(hm);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(Ui.px(28), 0, Ui.px(28), 0);
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

    void build() {
        Context c = getContext();
        LinearLayout head = new LinearLayout(c);
        head.setGravity(Gravity.BOTTOM);
        TextView t = Ui.text(c, Ui.serif, 34, 0xFFFFFFFF);
        t.setText("Control centre");
        head.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
        TextView hint = Ui.label(c, "Back to close", 0x99FFFFFF);
        hint.setPadding(0, 0, 0, Ui.px(8));
        head.addView(hint);
        list.addView(head);

        // toggles block + volume slider, side by side
        LinearLayout top = new LinearLayout(c);
        top.setClipChildren(false);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-1, Ui.px(240));
        tlp.topMargin = Ui.px(20);
        list.addView(top, tlp);

        LinearLayout block = group(c);
        block.setPadding(Ui.px(12), Ui.px(16), Ui.px(12), Ui.px(12));
        top.addView(block, new LinearLayout.LayoutParams(0, -1, 1));
        LinearLayout r1 = new LinearLayout(c), r2 = new LinearLayout(c);
        block.addView(r1, new LinearLayout.LayoutParams(-1, 0, 1));
        block.addView(r2, new LinearLayout.LayoutParams(-1, 0, 1));

        first = toggle(r1, "Wi‑Fi", BLUE,
            () -> wifiOn(), () -> wifiOn() ? Icon.WIFI : Icon.WIFI_OFF,
            () -> ethernet() ? "Ethernet" : !wifiOn() ? "Off" : wifiConnected() ? "Connected" : "On",
            () -> open(new Intent(Settings.ACTION_WIFI_SETTINGS)), () -> open(new Intent(Settings.ACTION_WIFI_SETTINGS)));
        toggle(r1, "Bluetooth", BLUE,
            QuickPanel::btOn, () -> btOn() ? Icon.BLUETOOTH : Icon.BT_OFF, () -> btOn() ? "On" : "Off",
            this::flipBluetooth, () -> open(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)));
        toggle(r2, "Mute", ORANGE,
            this::muted, () -> muted() ? Icon.MUTE : Icon.SOUND, () -> muted() ? "Muted" : "Sound on",
            () -> am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, 0),
            () -> open(new Intent(Settings.ACTION_SOUND_SETTINGS)));
        toggle(r2, "Screensaver", PURPLE,
            this::dreamOn, () -> Icon.MOON, () -> dreamOn() ? "Start now" : "Off",
            () -> open(new Intent(Intent.ACTION_MAIN).setClassName("com.android.systemui", "com.android.systemui.Somnambulator")),
            () -> open(new Intent(Settings.ACTION_DREAM_SETTINGS)));

        LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(Ui.px(112), -1);
        vlp.leftMargin = Ui.px(14);
        top.addView(volume(c), vlp);

        TextView tip = Ui.text(c, Ui.sans, 13, 0x99FFFFFF);
        tip.setText("OK switches · hold OK for its settings · on the slider, up and down change the volume");
        tip.setPadding(Ui.px(4), Ui.px(10), 0, 0);
        list.addView(tip);

        // shortcuts
        LinearLayout sc = new LinearLayout(c);
        sc.setClipChildren(false);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, -2);
        slp.topMargin = Ui.px(20);
        list.addView(sc, slp);
        shortcut(sc, Icon.MONITOR, "Display", () -> open(new Intent(Settings.ACTION_DISPLAY_SETTINGS)));
        shortcut(sc, Icon.RECENTS, "Recents", () -> open(new Intent(c, Recents.class)));
        shortcut(sc, Icon.APP, "Apps", () -> open(new Intent(Settings.ACTION_APPLICATION_SETTINGS)));
        shortcut(sc, Icon.TIMER, "Date & time", () -> open(new Intent(Settings.ACTION_DATE_SETTINGS)));

        // launcher
        LinearLayout g1 = group(c);
        LinearLayout.LayoutParams g1l = new LinearLayout.LayoutParams(-1, -2);
        g1l.topMargin = Ui.px(22);
        list.addView(Ui.label(c, "Launcher", 0x99FFFFFF), g1l);
        LinearLayout.LayoutParams gl = new LinearLayout.LayoutParams(-1, -2);
        gl.topMargin = Ui.px(10);
        list.addView(g1, gl);
        row(g1, Icon.USER, "Launcher settings", "Name, photos, rows, header, hidden apps",
            () -> open(new Intent(c, Personal.class)));
        switchRow(g1, Icon.GEMINI, "Glass panels", "Frosted glass, or solid dark panels", "glassPanels");
        row(g1, Icon.COMPASS, "Show the tour again", "A quick guided look at the home screen", () -> {
            close();
            home.h.postDelayed(home::startTour, 300);
        });

        // system
        LinearLayout g2 = group(c);
        LinearLayout.LayoutParams g2l = new LinearLayout.LayoutParams(-1, -2);
        g2l.topMargin = Ui.px(22);
        list.addView(Ui.label(c, "System", 0x99FFFFFF), g2l);
        list.addView(g2, new LinearLayout.LayoutParams(gl));
        row(g2, Icon.GEAR, "All system settings", "The TV's own settings", () -> open(new Intent(Settings.ACTION_SETTINGS)));
        row(g2, Icon.INPUT, "Inputs", "HDMI and other sources", () -> open(new Intent("android.settings.TV_INPUT_SETTINGS")));
        row(g2, Icon.SHIELD, "Privacy", "Permissions and accounts", () -> open(new Intent(Settings.ACTION_PRIVACY_SETTINGS)));
    }

    LinearLayout group(Context c) {
        LinearLayout g = new LinearLayout(c);
        g.setOrientation(LinearLayout.VERTICAL);
        g.setClipChildren(false);
        g.setBackground(Ui.round(0x14FFFFFF, 26));
        return g;
    }

    /** A round toggle: lit in its colour when on. OK runs the action, holding OK opens its settings. */
    View toggle(LinearLayout row, String name, int onColor, java.util.function.BooleanSupplier isOn,
                java.util.function.IntSupplier icon, java.util.function.Supplier<String> state, Runnable ok, Runnable longOk) {
        Context c = getContext();
        LinearLayout cell = new LinearLayout(c);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER_HORIZONTAL);
        cell.setClipChildren(false);
        FrameLayout btn = new FrameLayout(c);
        btn.setFocusable(true);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        btn.setBackground(bg);
        Icon i = new Icon(c, icon.getAsInt());
        btn.addView(i, new FrameLayout.LayoutParams(Ui.px(28), Ui.px(28), Gravity.CENTER));
        Selector.circle(btn, false);
        cell.addView(btn, new LinearLayout.LayoutParams(Ui.px(62), Ui.px(62)));
        TextView n = Ui.text(c, Ui.sansMedium, 14, 0xFFFFFFFF);
        n.setText(name);
        n.setGravity(Gravity.CENTER);
        n.setSingleLine(true);
        n.setPadding(0, Ui.px(8), 0, 0);
        cell.addView(n, new LinearLayout.LayoutParams(-1, -2));
        TextView s = Ui.text(c, Ui.sans, 12, 0xA6FFFFFF);
        s.setGravity(Gravity.CENTER);
        s.setSingleLine(true);
        cell.addView(s, new LinearLayout.LayoutParams(-1, -2));
        row.addView(cell, new LinearLayout.LayoutParams(0, -1, 1));
        Runnable paint = () -> {
            boolean on = isOn.getAsBoolean();
            bg.setColor(on ? onColor : 0x33FFFFFF);
            i.setImageResource(Icon.RES[icon.getAsInt()]);
            i.color(0xFFFFFFFF);
            s.setText(state.get());
            s.setTextColor(on ? 0xE6FFFFFF : 0x99FFFFFF);
        };
        paint.run();
        refreshers.add(paint);
        btn.setOnClickListener(v -> { ok.run(); h.postDelayed(paint, 250); h.postDelayed(paint, 1200); });
        btn.setOnLongClickListener(v -> { longOk.run(); return true; });
        return btn;
    }

    void flipBluetooth() {
        BluetoothAdapter b = BluetoothAdapter.getDefaultAdapter();
        try {
            if (b == null) throw new IllegalStateException();
            boolean ok = b.isEnabled() ? b.disable() : b.enable();
            if (!ok) throw new IllegalStateException();
            Toast.makeText(getContext(), b.isEnabled() ? "Turning Bluetooth off" : "Turning Bluetooth on", Toast.LENGTH_SHORT).show();
        } catch (Exception e) { open(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)); }
    }

    /** Tall slider like a phone's control centre: focus it, then up and down set the volume. */
    View volume(Context c) {
        FrameLayout f = new FrameLayout(c);
        f.setFocusable(true);
        f.setBackground(Ui.round(0x00000000, 30));
        View slider = new View(c) {
            final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override protected void onDraw(Canvas k) {
                float w = getWidth(), hh = getHeight(), r = Ui.px(30);
                int max = Math.max(1, am.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
                float v = muted() ? 0 : am.getStreamVolume(AudioManager.STREAM_MUSIC) / (float) max;
                p.setColor(0x2EFFFFFF);
                k.drawRoundRect(0, 0, w, hh, r, r, p);
                k.save();
                android.graphics.Path clip = new android.graphics.Path();
                clip.addRoundRect(0, 0, w, hh, r, r, android.graphics.Path.Direction.CW);
                k.clipPath(clip);
                p.setColor(0xF2FFFFFF);
                k.drawRect(0, hh * (1 - v), w, hh, p);
                k.restore();
            }
        };
        f.addView(slider, new FrameLayout.LayoutParams(-1, -1));
        Icon i = new Icon(c, Icon.SOUND);
        FrameLayout.LayoutParams il = new FrameLayout.LayoutParams(Ui.px(26), Ui.px(26), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        il.bottomMargin = Ui.px(18);
        f.addView(i, il);
        TextView val = Ui.text(c, Ui.mono, 15, 0xFFFFFFFF);
        val.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams vl = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP);
        vl.topMargin = Ui.px(16);
        f.addView(val, vl);
        Selector.shape(f, Ui.px(30), false);
        Runnable paint = () -> {
            int vol = am.getStreamVolume(AudioManager.STREAM_MUSIC);
            boolean m = muted();
            val.setText(m ? "Off" : String.valueOf(vol));
            int max = Math.max(1, am.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
            boolean dark = !m && vol / (float) max > 0.82f; // text over the white fill
            val.setTextColor(dark ? 0xFF0E0E12 : 0xFFFFFFFF);
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

    void shortcut(LinearLayout row, int icon, String name, Runnable run) {
        Context c = getContext();
        LinearLayout cell = new LinearLayout(c);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER_HORIZONTAL);
        cell.setClipChildren(false);
        FrameLayout b = new FrameLayout(c);
        b.setFocusable(true);
        GradientDrawable bg = Ui.round(0x1FFFFFFF, 20);
        b.setBackground(bg);
        b.addView(new Icon(c, icon), new FrameLayout.LayoutParams(Ui.px(26), Ui.px(26), Gravity.CENTER));
        b.setOnFocusChangeListener((v, f) -> bg.setColor(f ? 0x40FFFFFF : 0x1FFFFFFF));
        Selector.shape(b, Ui.px(20), false);
        b.setOnClickListener(v -> run.run());
        cell.addView(b, new LinearLayout.LayoutParams(Ui.px(64), Ui.px(64)));
        TextView n = Ui.text(c, Ui.sans, 13, 0xD9FFFFFF);
        n.setText(name);
        n.setGravity(Gravity.CENTER);
        n.setSingleLine(true);
        n.setPadding(0, Ui.px(8), 0, 0);
        cell.addView(n, new LinearLayout.LayoutParams(-1, -2));
        row.addView(cell, new LinearLayout.LayoutParams(0, -2, 1));
    }

    LinearLayout line(LinearLayout group, int icon, String title, String sub) {
        Context c = getContext();
        LinearLayout r = new LinearLayout(c);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setFocusable(true);
        r.setPadding(Ui.px(18), Ui.px(12), Ui.px(18), Ui.px(12));
        GradientDrawable bg = Ui.round(0x00FFFFFF, 20);
        r.setBackground(bg);
        r.setOnFocusChangeListener((v, f) -> bg.setColor(f ? 0x1FFFFFFF : 0x00FFFFFF));
        Selector.shape(r, Ui.px(20), false);
        FrameLayout ib = new FrameLayout(c);
        ib.setBackground(Ui.round(0x26FFFFFF, 10));
        ib.addView(new Icon(c, icon), new FrameLayout.LayoutParams(Ui.px(20), Ui.px(20), Gravity.CENTER));
        r.addView(ib, new LinearLayout.LayoutParams(Ui.px(36), Ui.px(36)));
        LinearLayout tx = new LinearLayout(c);
        tx.setOrientation(LinearLayout.VERTICAL);
        tx.setPadding(Ui.px(14), 0, Ui.px(8), 0);
        TextView l = Ui.text(c, Ui.sansMedium, 17, 0xFFFFFFFF);
        l.setText(title);
        l.setSingleLine(true);
        tx.addView(l);
        TextView s = Ui.text(c, Ui.sans, 13, 0x99FFFFFF);
        s.setText(sub);
        s.setSingleLine(true);
        s.setEllipsize(android.text.TextUtils.TruncateAt.END);
        tx.addView(s);
        r.addView(tx, new LinearLayout.LayoutParams(0, -2, 1));
        group.addView(r, new LinearLayout.LayoutParams(-1, -2));
        return r;
    }

    void row(LinearLayout g, int icon, String title, String sub, Runnable run) {
        line(g, icon, title, sub).setOnClickListener(v -> run.run());
    }

    void switchRow(LinearLayout g, int icon, String title, String sub, String key) {
        LinearLayout r = line(g, icon, title, sub);
        Personal.Switch sw = new Personal.Switch(getContext(), Prefs.on(getContext(), key, true));
        r.addView(sw, new LinearLayout.LayoutParams(Ui.px(48), Ui.px(28)));
        r.setOnClickListener(v -> {
            boolean now = !Prefs.on(getContext(), key, true);
            Prefs.put(getContext(), key, now);
            sw.set(now);
            h.postDelayed(home::recreate, 250); // every panel repaints glass or solid
        });
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
