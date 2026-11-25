package tv.steroid.home;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.text.format.DateFormat;
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
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Control centre in the style of Xiaomi HyperOS 4, on frosted glass:
 *   two wide tiles (Wi‑Fi, Bluetooth) that turn light when on,
 *   a now playing card beside two tall sliders (volume, background),
 *   a capsule of shortcuts, then rows of round toggles (light when on), and a Tour pill.
 * Round buttons carry no labels (as on HyperOS); the line under the date names whatever is selected.
 * Every icon is Lucide. Sizes are fixed on a grid, so no text is ever cut.
 */
final class QuickPanel extends FrameLayout {
    // HyperOS palette: light "on" surfaces with coloured glyphs; translucent glass when off
    static final int ON = 0xFFF7F4E4, ON_TEXT = 0xFF18181C, ON_SUB = 0xFF6A6A70, BLUE = 0xFF2F7BF6, RED = 0xFFF0483E,
        ORANGE = 0xFFF5A524, OFF = 0x2EFFFFFF, OFF_DEEP = 0x1FFFFFFF;
    static final int PW = 440, PAD = 24, G = 14;                 // panel width, padding, gap (dp)
    static final int W = PW - 2 * PAD, HALF = (W - G) / 2, SL = (HALF - G) / 2, DOT = 76; // grid (dp)

    final Home home;
    final FrameLayout panel;
    final LinearLayout list;
    final Handler h = new Handler(Looper.getMainLooper());
    final List<Runnable> refreshers = new ArrayList<>();
    final AudioManager am;
    TextView date, clock, caption;
    View first;

    QuickPanel(Home hm) {
        super(hm);
        home = hm;
        am = (AudioManager) hm.getSystemService(Context.AUDIO_SERVICE);
        setBackgroundColor(0x66000000); // soft dim: the glass does the rest
        setClickable(true);
        panel = new FrameLayout(hm);
        Glass.apply(panel, 36, 0x99101014); // Apple style frosted glass, dark enough for white text
        LayoutParams lp = new LayoutParams(Ui.px(PW), -1, Gravity.END);
        lp.setMargins(0, Ui.px(12), Ui.px(16), Ui.px(12));
        addView(panel, lp);
        Rails.Page scroll = new Rails.Page(hm, Ui.px(28)); // scrolls; the selected item always stays in view
        scroll.setClipToPadding(false);
        scroll.setPadding(0, Ui.px(22), 0, Ui.px(22));
        list = new LinearLayout(hm);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(Ui.px(PAD), 0, Ui.px(PAD), 0);
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
    NetworkCapabilities net() {
        ConnectivityManager cm = (ConnectivityManager) home.getSystemService(Context.CONNECTIVITY_SERVICE);
        return cm == null ? null : cm.getNetworkCapabilities(cm.getActiveNetwork());
    }
    boolean wifiConnected() { NetworkCapabilities n = net(); return n != null && n.hasTransport(NetworkCapabilities.TRANSPORT_WIFI); }
    boolean ethernet() { NetworkCapabilities n = net(); return n != null && n.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET); }
    static boolean btOn() { BluetoothAdapter b = BluetoothAdapter.getDefaultAdapter(); return b != null && b.isEnabled(); }
    boolean muted() { return am.isStreamMute(AudioManager.STREAM_MUSIC) || am.getStreamVolume(AudioManager.STREAM_MUSIC) == 0; }
    boolean dreamOn() { return Settings.Secure.getInt(home.getContentResolver(), "screensaver_enabled", 1) == 1; }
    boolean glass() { return Prefs.on(home, "glassPanels", true); }

    // ---------- layout ----------

    LinearLayout.LayoutParams box(int wDp, int hDp, int leftDp, int topDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.px(wDp), Ui.px(hDp));
        lp.leftMargin = Ui.px(leftDp);
        lp.topMargin = Ui.px(topDp);
        return lp;
    }

    LinearLayout row(int topDp) {
        LinearLayout r = new LinearLayout(getContext());
        r.setClipChildren(false);
        r.setClipToPadding(false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = Ui.px(topDp);
        list.addView(r, lp);
        return r;
    }

    void build() {
        Context c = getContext();

        // header: date left, time right; under it, the name and state of what's selected
        LinearLayout head = row(0);
        head.setGravity(Gravity.BOTTOM);
        date = Ui.text(c, Ui.sansMedium, 17, 0xF2FFFFFF);
        date.setSingleLine(true);
        head.addView(date, new LinearLayout.LayoutParams(0, -2, 1));
        clock = Ui.text(c, Ui.sansMedium, 17, 0xF2FFFFFF);
        head.addView(clock);
        caption = Ui.text(c, Ui.sans, 14, 0xB3FFFFFF);
        caption.setSingleLine(true);
        caption.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(-1, Ui.px(22));
        cl.topMargin = Ui.px(4);
        list.addView(caption, cl);
        refreshers.add(() -> {
            java.util.Calendar k = java.util.Calendar.getInstance();
            date.setText(DateFormat.format("EEE, MMM d", k));
            clock.setText(DateFormat.format(DateFormat.is24HourFormat(home) ? "HH:mm" : "h:mm", k));
        });

        // 1. wide tiles
        LinearLayout r1 = row(12);
        first = wide(r1, 0, Icon.WIFI, Icon.WIFI_OFF, "Wi‑Fi",
            () -> wifiOn() || ethernet(), () -> ethernet() ? "Ethernet" : !wifiOn() ? "Off" : wifiConnected() ? "Connected" : "Not connected",
            () -> open(new Intent(Settings.ACTION_WIFI_SETTINGS)), () -> open(new Intent(Settings.ACTION_WIFI_SETTINGS)));
        // Bluetooth stays on (the remote uses it): OK opens the Bluetooth panel with every device
        wide(r1, G, Icon.BLUETOOTH, Icon.BT_OFF, "Bluetooth",
            QuickPanel::btOn, () -> !btOn() ? "Off" : btConnected() == 0 ? "On" : "On · " + btConnected() + " connected",
            this::openBluetooth, () -> { close(); BluetoothPanel.openAccessories(home); });  // hold: Remotes & Accessories

        // 2. now playing | volume | background
        LinearLayout r2 = row(G);
        r2.addView(media(c), box(HALF, HALF, 0, 0));
        r2.addView(slider(c, Icon.SOUND, BLUE, "Volume",
            () -> muted() ? 0f : am.getStreamVolume(AudioManager.STREAM_MUSIC) / (float) Math.max(1, am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)),
            up -> am.adjustStreamVolume(AudioManager.STREAM_MUSIC, up ? AudioManager.ADJUST_RAISE : AudioManager.ADJUST_LOWER, 0),
            () -> am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, 0),
            () -> muted() ? "Muted" : am.getStreamVolume(AudioManager.STREAM_MUSIC) + " of " + am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)),
            box(SL, HALF, G, 0));
        r2.addView(slider(c, Icon.SUN_DIM, ORANGE, "Background",
            () -> Prefs.sp(home).getInt("bgDim", 100) / 100f,
            up -> { int v = Math.max(0, Math.min(100, Prefs.sp(home).getInt("bgDim", 100) + (up ? 10 : -10)));
                    Prefs.sp(home).edit().putInt("bgDim", v).apply(); home.ambient.invalidate(); },
            null, () -> "Shade behind the home screen " + Prefs.sp(home).getInt("bgDim", 100) + "%"),
            box(SL, HALF, G, 0));

        // 3. capsule of shortcuts
        LinearLayout cap = row(G);
        cap.setGravity(Gravity.CENTER_VERTICAL);
        cap.setPadding(Ui.px(14), 0, Ui.px(14), 0);
        cap.setBackground(Ui.round(OFF, 32));
        ((LinearLayout.LayoutParams) cap.getLayoutParams()).height = Ui.px(92);
        capsule(cap, Icon.RECENTS, "Recent apps", () -> open(new Intent(c, Recents.class)));
        capsule(cap, Icon.INPUT, "Inputs", () -> open(new Intent("android.settings.TV_INPUT_SETTINGS")));
        capsule(cap, Icon.MONITOR, "Display", () -> open(new Intent(Settings.ACTION_DISPLAY_SETTINGS)));
        capsule(cap, Icon.ELLIPSIS, "All settings", () -> open(new Intent(Settings.ACTION_SETTINGS)));

        // 4. round toggles and buttons
        LinearLayout r4 = row(G + 4);
        dot(r4, () -> Icon.MOON, "Screensaver", this::dreamOn, BLUE, () -> dreamOn() ? "On · OK starts it now" : "Off",
            () -> open(new Intent(Intent.ACTION_MAIN).setClassName("com.android.systemui", "com.android.systemui.Somnambulator")),
            () -> open(new Intent(Settings.ACTION_DREAM_SETTINGS)));
        dot(r4, () -> muted() ? Icon.BELL_OFF : Icon.SOUND, "Mute", this::muted, RED, () -> muted() ? "On" : "Off",
            () -> am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, 0),
            () -> open(new Intent(Settings.ACTION_SOUND_SETTINGS)));
        dot(r4, () -> Icon.CONTRAST, "Glass panels", this::glass, BLUE, () -> glass() ? "Frosted glass" : "Solid panels",
            () -> { Prefs.put(home, "glassPanels", !glass()); h.postDelayed(home::recreate, 250); }, null);
        dot(r4, () -> Icon.CAST, "Mini player", null, BLUE, () -> "Bring a floating mini player back to full screen",
            () -> new Thread(() -> {
                String p = PipWatch.pinned(c);
                h.post(() -> { if (p == null) Toast.makeText(c, "No mini player on screen", Toast.LENGTH_SHORT).show(); else { close(); PipWatch.full(c, p); } });
            }).start(), null);
        LinearLayout r5 = row(G + 4);
        dot(r5, () -> Icon.USER, "Launcher settings", null, BLUE, () -> "Name, photos, rows, header", () -> open(new Intent(c, Personal.class)), null);
        dot(r5, () -> Icon.APP, "Apps", null, BLUE, () -> "Installed apps", () -> open(new Intent(Settings.ACTION_APPLICATION_SETTINGS)), null);
        dot(r5, () -> Icon.TIMER, "Date and time", null, BLUE, () -> "Clock, time zone", () -> open(new Intent(Settings.ACTION_DATE_SETTINGS)), null);
        dot(r5, () -> Icon.SHIELD, "Privacy", null, BLUE, () -> "Permissions and accounts", () -> open(new Intent(Settings.ACTION_PRIVACY_SETTINGS)), null);

        // 5. Tour pill, centred (where HyperOS puts Edit)
        LinearLayout r6 = row(G + 8);
        r6.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView tour = Ui.text(c, Ui.sansMedium, 15, 0xF2FFFFFF);
        tour.setText("Tour");
        tour.setGravity(Gravity.CENTER);
        tour.setFocusable(true);
        tour.setPadding(Ui.px(26), 0, Ui.px(26), 0);
        GradientDrawable tb = Ui.round(OFF, 20);
        tour.setBackground(tb);
        Selector.shape(tour, Ui.px(20), false);
        tour.setOnFocusChangeListener((v, f) -> { tb.setColor(f ? 0x47FFFFFF : OFF); if (f) caption.setText("Tour · a guided look at the home screen"); });
        tour.setOnClickListener(v -> { close(); home.h.postDelayed(home::startTour, 300); });
        r6.addView(tour, new LinearLayout.LayoutParams(-2, Ui.px(40)));
    }

    /** Caption under the date: what the selected control is and its state. */
    void describe(View v, String name, Supplier<String> state) {
        View.OnFocusChangeListener prev = v.getOnFocusChangeListener();
        v.setOnFocusChangeListener((x, f) -> {
            if (prev != null) prev.onFocusChange(x, f);
            if (f) caption.setText(name + (state == null ? "" : " · " + state.get()));
        });
        refreshers.add(() -> { if (v.isFocused()) caption.setText(name + (state == null ? "" : " · " + state.get())); });
    }

    /** Wide tile: icon, name and state; light with a blue glyph when on. OK acts, holding OK opens settings. */
    View wide(LinearLayout row, int leftDp, int iconOn, int iconOff, String name, BooleanSupplier isOn,
              Supplier<String> state, Runnable ok, Runnable longOk) {
        Context c = getContext();
        LinearLayout t = new LinearLayout(c);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setFocusable(true);
        t.setPadding(Ui.px(20), 0, Ui.px(14), 0);
        GradientDrawable bg = Ui.round(OFF, 30);
        t.setBackground(bg);
        Icon i = new Icon(c, iconOn);
        t.addView(i, new LinearLayout.LayoutParams(Ui.px(28), Ui.px(28)));
        LinearLayout tx = new LinearLayout(c);
        tx.setOrientation(LinearLayout.VERTICAL);
        tx.setPadding(Ui.px(14), 0, 0, 0);
        TextView n = Ui.text(c, Ui.sansMedium, 17, 0xFFFFFFFF);
        n.setText(name);
        n.setSingleLine(true);
        n.setEllipsize(TextUtils.TruncateAt.END);
        tx.addView(n);
        TextView s = Ui.text(c, Ui.sans, 13, 0xB3FFFFFF);
        s.setSingleLine(true);
        s.setEllipsize(TextUtils.TruncateAt.END);
        s.setPadding(0, Ui.px(2), 0, 0);
        tx.addView(s);
        t.addView(tx, new LinearLayout.LayoutParams(0, -2, 1));
        Selector.shape(t, Ui.px(30), false);
        Runnable paint = () -> {
            boolean on = isOn.getAsBoolean();
            bg.setColor(on ? ON : OFF);
            i.setImageResource(Icon.RES[on ? iconOn : iconOff]);
            i.color(on ? BLUE : 0xFFFFFFFF);
            n.setTextColor(on ? ON_TEXT : 0xFFFFFFFF);
            s.setTextColor(on ? ON_SUB : 0xB3FFFFFF);
            s.setText(state.get());
        };
        paint.run();
        refreshers.add(paint);
        t.setOnClickListener(v -> { ok.run(); h.postDelayed(paint, 300); h.postDelayed(paint, 1500); });
        t.setOnLongClickListener(v -> { longOk.run(); return true; });
        describe(t, name, () -> state.get() + " · hold OK for settings");
        row.addView(t, box(HALF, 76, leftDp, 0));
        return t;
    }

    /** Round button: light when on (its colour on the glyph), glass when off. */
    void dot(LinearLayout row, java.util.function.IntSupplier icon, String name, BooleanSupplier isOn, int onColor,
             Supplier<String> state, Runnable ok, Runnable longOk) {
        Context c = getContext();
        FrameLayout cell = new FrameLayout(c);
        cell.setClipChildren(false);
        FrameLayout b = new FrameLayout(c);
        b.setFocusable(true);
        b.setContentDescription(name);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        b.setBackground(bg);
        Icon i = new Icon(c, icon.getAsInt());
        b.addView(i, new FrameLayout.LayoutParams(Ui.px(28), Ui.px(28), Gravity.CENTER));
        Selector.circle(b, false);
        cell.addView(b, new FrameLayout.LayoutParams(Ui.px(DOT), Ui.px(DOT), Gravity.CENTER));
        Runnable paint = () -> {
            boolean on = isOn != null && isOn.getAsBoolean();
            bg.setColor(on ? ON : OFF);
            i.setImageResource(Icon.RES[icon.getAsInt()]);
            i.color(on ? onColor : 0xFFFFFFFF);
        };
        paint.run();
        refreshers.add(paint);
        b.setOnClickListener(v -> { ok.run(); h.postDelayed(paint, 200); h.postDelayed(paint, 1200); });
        if (longOk != null) b.setOnLongClickListener(v -> { longOk.run(); return true; });
        describe(b, name, () -> state.get() + (longOk != null ? " · hold OK for settings" : ""));
        row.addView(cell, new LinearLayout.LayoutParams(0, Ui.px(DOT), 1));
    }

    /** A smaller round button inside the shortcut capsule. */
    void capsule(LinearLayout cap, int icon, String name, Runnable run) {
        Context c = getContext();
        FrameLayout cell = new FrameLayout(c);
        cell.setClipChildren(false);
        FrameLayout b = new FrameLayout(c);
        b.setFocusable(true);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(OFF_DEEP);
        b.setBackground(bg);
        b.addView(new Icon(c, icon), new FrameLayout.LayoutParams(Ui.px(26), Ui.px(26), Gravity.CENTER));
        Selector.circle(b, false);
        b.setOnFocusChangeListener((v, f) -> bg.setColor(f ? 0x40FFFFFF : OFF_DEEP));
        b.setOnClickListener(v -> run.run());
        describe(b, name, null);
        cell.addView(b, new FrameLayout.LayoutParams(Ui.px(64), Ui.px(64), Gravity.CENTER));
        cap.addView(cell, new LinearLayout.LayoutParams(0, Ui.px(64), 1));
    }

    /** Tall slider: light fill rises from the bottom, glyph at the foot. Up and down change it, OK runs the extra action. */
    View slider(Context c, int icon, int glyph, String name, Supplier<Float> level, java.util.function.Consumer<Boolean> step,
                Runnable ok, Supplier<String> state) {
        FrameLayout f = new FrameLayout(c);
        f.setFocusable(true);
        View fill = new View(c) {
            final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            final Path clip = new Path();
            @Override protected void onDraw(Canvas k) {
                float w = getWidth(), hh = getHeight(), r = Ui.px(30);
                float v = Math.max(0, Math.min(1, level.get()));
                clip.reset();
                clip.addRoundRect(0, 0, w, hh, r, r, Path.Direction.CW);
                k.save();
                k.clipPath(clip);
                p.setColor(OFF);
                k.drawRect(0, 0, w, hh, p);
                p.setColor(ON);
                k.drawRect(0, hh * (1 - v), w, hh, p);
                k.restore();
            }
        };
        f.addView(fill, new FrameLayout.LayoutParams(-1, -1));
        Icon i = new Icon(c, icon);
        FrameLayout.LayoutParams il = new FrameLayout.LayoutParams(Ui.px(30), Ui.px(30), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        il.bottomMargin = Ui.px(24);
        f.addView(i, il);
        Selector.shape(f, Ui.px(30), false);
        Runnable paint = () -> {
            float v = level.get();
            boolean under = v > 0.16f; // glyph sits on the light fill
            i.color(under ? glyph : 0xFFFFFFFF);
            if (icon == Icon.SOUND) i.setImageResource(Icon.RES[muted() ? Icon.MUTE : Icon.SOUND]);
            fill.invalidate();
        };
        paint.run();
        refreshers.add(paint);
        f.setOnKeyListener((v, code, e) -> {
            if (e.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (code != KeyEvent.KEYCODE_DPAD_UP && code != KeyEvent.KEYCODE_DPAD_DOWN) return false;
            step.accept(code == KeyEvent.KEYCODE_DPAD_UP);
            paint.run();
            caption.setText(name + " · " + state.get());
            return true;
        });
        if (ok != null) f.setOnClickListener(v -> { ok.run(); h.postDelayed(paint, 150); });
        describe(f, name, () -> state.get() + " · up and down to change");
        return f;
    }

    /** Now playing card: title, then previous, play or pause, next. While something plays, the album
     *  art fills the card, softened and under a dark scrim, so the text and controls keep their contrast. */
    View media(Context c) {
        FrameLayout card = new FrameLayout(c);
        card.setBackground(Ui.round(OFF, 30));
        card.setClipToOutline(true);   // the art follows the card's rounded corners
        android.widget.ImageView cover = new android.widget.ImageView(c);
        cover.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
        cover.setAlpha(0f);
        card.addView(cover, new FrameLayout.LayoutParams(-1, -1));
        // scrim: at least 62% black everywhere (white text stays above 4.5:1 on any picture), darker at the controls
        View scrim = new View(c);
        scrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0x9E000000, 0xA6000000, 0xC2000000}));
        scrim.setAlpha(0f);
        card.addView(scrim, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout m = new LinearLayout(c);
        m.setOrientation(LinearLayout.VERTICAL);
        m.setPadding(Ui.px(18), Ui.px(16), Ui.px(18), Ui.px(14));
        m.setClipChildren(false);
        card.addView(m, new FrameLayout.LayoutParams(-1, -1));
        final android.graphics.Bitmap[] lastArt = {null};
        LinearLayout top = new LinearLayout(c);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView app = Ui.text(c, Ui.sans, 13, 0x99FFFFFF);
        app.setSingleLine(true);
        app.setEllipsize(TextUtils.TruncateAt.END);
        top.addView(app, new LinearLayout.LayoutParams(0, -2, 1));
        Icon cast = new Icon(c, Icon.CAST);
        cast.color(0x99FFFFFF);
        top.addView(cast, new LinearLayout.LayoutParams(Ui.px(22), Ui.px(22)));
        m.addView(top);
        m.addView(new View(c), new LinearLayout.LayoutParams(1, 0, 1));
        TextView title = Ui.text(c, Ui.sansMedium, 16, 0xFFFFFFFF);
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        m.addView(title);
        TextView sub = Ui.text(c, Ui.sans, 13, 0x99FFFFFF);
        sub.setSingleLine(true);
        sub.setEllipsize(TextUtils.TruncateAt.END);
        m.addView(sub);
        m.addView(new View(c), new LinearLayout.LayoutParams(1, 0, 1));
        LinearLayout ctl = new LinearLayout(c);
        ctl.setGravity(Gravity.CENTER_VERTICAL);
        ctl.setClipChildren(false);
        Icon pi = new Icon(c, Icon.PLAY);
        View prev = small(c, new Icon(c, Icon.PREV), "Previous", () -> np(KeyEvent.KEYCODE_MEDIA_PREVIOUS));
        View play = small(c, pi, "Play or pause", () -> { NowPlaying n = home.nowPlaying; if (n != null && n.active) n.toggle(); else { close(); home.pickMusic(); } });
        View next = small(c, new Icon(c, Icon.NEXT), "Next", () -> np(KeyEvent.KEYCODE_MEDIA_NEXT));
        ctl.addView(prev, new LinearLayout.LayoutParams(Ui.px(44), Ui.px(44)));
        ctl.addView(new View(c), new LinearLayout.LayoutParams(0, 1, 1));
        ctl.addView(play, new LinearLayout.LayoutParams(Ui.px(48), Ui.px(48)));
        ctl.addView(new View(c), new LinearLayout.LayoutParams(0, 1, 1));
        ctl.addView(next, new LinearLayout.LayoutParams(Ui.px(44), Ui.px(44)));
        m.addView(ctl, new LinearLayout.LayoutParams(-1, -2));
        Runnable paint = () -> {
            NowPlaying n = home.nowPlaying;
            boolean on = n != null && n.active && n.ctl != null;
            prev.setAlpha(on ? 1f : 0.35f);
            next.setAlpha(on ? 1f : 0.35f);
            pi.setImageResource(Icon.RES[on && n.shownPlaying ? Icon.PAUSE : Icon.PLAY]);
            android.graphics.Bitmap a = on ? n.shownArt : null;
            if (a != lastArt[0]) {
                lastArt[0] = a;
                if (a != null) cover.setImageBitmap(soft(a));
                float to = a != null ? 1f : 0f;
                cover.animate().alpha(to * 0.9f).setDuration(300).start();
                scrim.animate().alpha(to).setDuration(300).start();
                // small grey text brightens over a picture so it still reads at 4.5:1 or better
                int second = a != null ? 0xD9FFFFFF : 0x99FFFFFF;
                app.setTextColor(second);
                sub.setTextColor(second);
                cast.color(second);
            }
            if (!on) { app.setText("Music"); title.setText("Nothing playing"); sub.setText("Play to choose an app"); return; }
            android.media.MediaMetadata md = n.ctl.getMetadata();
            String t = md == null ? null : md.getString(android.media.MediaMetadata.METADATA_KEY_TITLE);
            String ar = md == null ? null : md.getString(android.media.MediaMetadata.METADATA_KEY_ARTIST);
            String label;
            try { label = home.getPackageManager().getApplicationLabel(home.getPackageManager().getApplicationInfo(n.ctl.getPackageName(), 0)).toString(); }
            catch (Exception e) { label = "Now playing"; }
            if (n.ctl.getPackageName().equals(home.getPackageName())) label = "YouTube";
            app.setText(label);
            title.setText(t == null ? "Playing" : t);
            sub.setText(ar == null ? "" : ar);
        };
        paint.run();
        refreshers.add(paint);
        return card;
    }

    /** The album art, softened: shrunk to a few pixels and scaled back up, a blur that costs nothing. */
    static android.graphics.Bitmap soft(android.graphics.Bitmap b) {
        try {
            int w = Math.max(1, b.getWidth()), hgt = Math.max(1, b.getHeight());
            float k = 20f / Math.max(w, hgt);
            android.graphics.Bitmap tiny = android.graphics.Bitmap.createScaledBitmap(b, Math.max(1, Math.round(w * k)), Math.max(1, Math.round(hgt * k)), true);
            return android.graphics.Bitmap.createScaledBitmap(tiny, tiny.getWidth() * 6, tiny.getHeight() * 6, true);
        } catch (Throwable e) { return b; }
    }

    View small(Context c, Icon i, String name, Runnable run) {
        FrameLayout b = new FrameLayout(c);
        b.setFocusable(true);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(0x00FFFFFF);
        b.setBackground(bg);
        b.addView(i, new FrameLayout.LayoutParams(Ui.px(24), Ui.px(24), Gravity.CENTER));
        Selector.circle(b, false);
        b.setOnFocusChangeListener((v, f) -> bg.setColor(f ? 0x33FFFFFF : 0x00FFFFFF));
        b.setOnClickListener(v -> run.run());
        describe(b, name, null);
        return b;
    }

    void np(int key) { NowPlaying n = home.nowPlaying; if (n != null && n.active) n.key(key); }

    void openBluetooth() { BluetoothPanel.open(home, home.rootView, home.quickPage != null ? home.quickPage : home.scroll); }

    static int btConnected() {
        BluetoothAdapter b = BluetoothAdapter.getDefaultAdapter();
        int n = 0;
        try { if (b != null && b.isEnabled()) for (android.bluetooth.BluetoothDevice d : b.getBondedDevices()) if (BluetoothPanel.connected(d)) n++; }
        catch (Throwable ignored) {}
        return n;
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
