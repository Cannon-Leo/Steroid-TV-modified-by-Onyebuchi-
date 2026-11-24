package tv.steroid.home;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.BitmapFactory;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.view.*;
import android.widget.*;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/** Home: time and mood up top, streaming apps, then two "worlds" — Retro TV and Radio. */
public class Home extends Activity {
    /** {package, label, accent} for the Watch row, from config/apps.json. */
    static String[][] APPS = new String[0][];

    static void loadApps() {
        org.json.JSONArray a = Config.apps.optJSONArray("apps");
        int n = a == null ? 0 : a.length();
        String[][] out = new String[n][];
        for (int i = 0; i < n; i++) {
            org.json.JSONObject o = a.optJSONObject(i);
            out[i] = new String[]{o.optString("package"), o.optString("label"), o.optString("accent", "#8FB8D8")};
        }
        APPS = out;
    }

    final Handler h = new Handler();
    ScrollView scroll;
    View firstApp;
    Ambient ambient;
    TextView clock, meridiem, date, greeting, focusTitle, seconds;
    View timeRow, whoRow;
    TextView retroTitle, retroBlurb, radioState, radioName, chip;
    RetroPanel retroPanel;
    LinearLayout watchRow, mineRow, allBox;
    TextView allHead;

    NowPlaying nowPlaying;
    ImageView avatar;
    TextView avatarLetter;
    List<Apps.App> installed = new ArrayList<>();
    boolean appsDirty;
    final android.content.BroadcastReceiver pkgs = new android.content.BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { appsDirty = true; h.removeCallbacks(rebuild); h.postDelayed(rebuild, 600); }
    };
    final Runnable rebuild = this::rebuildApps;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.init(this);
        loadApps();
        layoutVersion = Prefs.layoutVersion(this);
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);

        FrameLayout root = new FrameLayout(this);
        rootView = root;
        root.setClipChildren(false);
        ambient = new Ambient(this);
        root.addView(ambient);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setClipChildren(false);
        col.setClipToPadding(false);
        col.setPadding(Ui.px(PAD), Ui.px(36), Ui.px(PAD), Ui.px(110));
        // The page scrolls: focus moving down the deck brings each row into view.
        scroll = new Rails.Page(this, Ui.px(72));
        scroll.getViewTreeObserver().addOnScrollChangedListener(Glass::refresh); // glass shows what is behind it
        scroll.addView(col, new FrameLayout.LayoutParams(-1, -2));
        root.addView(scroll, new FrameLayout.LayoutParams(-1, -1));

        col.addView(pipBar(), new LinearLayout.LayoutParams(-1, -2)); // shows only while an app sits in a mini player
        col.addView(header());

        // Sections, in the order and size chosen in "Arrange home screen"
        for (String k : Prefs.order(this)) {
            if (!Prefs.shown(this, k)) continue;
            View sec = section(k);
            if (sec == null) continue;
            sectionViews.put(k, sec);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.topMargin = Ui.px(GAP);
            col.addView(sec, lp);
        }
        // always last: a way back into the guided tour for anyone using the TV
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-1, Ui.px(112));
        tlp.topMargin = Ui.px(GAP);
        col.addView(tourCard(), tlp);

        // notices and approvals run even when the Look out row is hidden
        if (Config.feature("littleOffice")) OfficeService.start(this);
        Photos.changed = () -> { avatarFor = ""; loadAvatar(); ambient.reload(); }; // photos synced from the computer
        selector = Selector.attach(this, root); // one gliding focus ring above everything
        History.listener = () -> { if (resumed) { for (Shelf sh : shelves) sh.refresh(); fillIdle(); } }; // rows update as you watch
        setContentView(root);
        rebuildApps();
        if (!Prefs.on(this, "tourDone", false)) h.postDelayed(this::startTour, 1500); // first time only
        if (firstApp != null) firstApp.requestFocus();

        android.content.IntentFilter f = new android.content.IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_CHANGED);
        f.addAction(Intent.ACTION_PACKAGE_REPLACED);
        f.addDataScheme("package");
        registerReceiver(pkgs, f);

        // Video rows read what apps publish to Android TV; Android asks once on screen (like Projectivy did).
        if (checkSelfPermission(EPG) != PackageManager.PERMISSION_GRANTED) requestPermissions(new String[]{EPG}, 7);
    }

    static final int PAD = 56, GAP = 40;
    static final String EPG = "com.android.providers.tv.permission.READ_EPG_DATA";
    int layoutVersion;
    LinearLayout shelvesBox;
    final List<Shelf> shelves = new ArrayList<>();
    SystemStats stats;
    Selector selector;
    boolean resumed;
    android.animation.ObjectAnimator breathe;

    @Override public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        if (code == 7 && res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED) for (Shelf s : shelves) s.refresh();
    }

    /** Builds one home section. Sizes S / M / L scale its cards. */
    View section(String k) {
        float sc = Prefs.scale(this, k);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setClipChildren(false);
        switch (k) {
            case "watch": {
                LinearLayout head = new LinearLayout(this);
                head.setGravity(Gravity.BOTTOM);
                head.addView(Ui.label(this, "Watch", 0xCCFFFFFF));
                focusTitle = Ui.text(this, Ui.serifItalic, 17, 0xF2FFFFFF);
                focusTitle.setShadowLayer(Ui.px(6), 0, Ui.px(1), 0xB3000000);
                focusTitle.setPadding(Ui.px(12), 0, 0, 0);
                head.addView(focusTitle);
                box.addView(head);
                watchRow = scrollRow(box);
                watchScale = sc;
                return box;
            }
            case "videos":
                shelvesBox = box;
                videoScale = sc;
                return box;
            case "worlds": {
                boolean hasRetro = Config.feature("retroTv"), hasRadio = Config.feature("radio") && Stations.count() > 0;
                if (!hasRetro && !hasRadio) return null;
                LinearLayout worlds = new LinearLayout(this);
                worlds.setClipChildren(false);
                boolean hasLive = Config.feature("liveTv");
                if (hasRetro) worlds.addView(retro(), new LinearLayout.LayoutParams(0, -1, hasLive ? 1.6f : 1.75f));
                if (hasLive) {
                    LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(0, -1, 1f);
                    if (hasRetro) llp.leftMargin = Ui.px(18);
                    worlds.addView(liveCard(), llp);
                }
                if (hasRadio) {
                    LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(0, -1, 1f);
                    if (hasRetro || hasLive) rlp.leftMargin = Ui.px(18);
                    worlds.addView(radio(), rlp);
                }
                box.addView(worlds, new LinearLayout.LayoutParams(-1, Ui.px(172 * sc)));
                return box;
            }
            case "mine":
                box.addView(Ui.label(this, "My apps", 0xCCFFFFFF));
                mineRow = scrollRow(box);
                mineScale = sc;
                return box;
            case "lookout": {
                boolean hasWindows = Config.feature("worldsWindows") && Places.count() > 0, hasOffice = Config.feature("littleOffice");
                if (!hasWindows && !hasOffice) return null;
                TextView more = Ui.label(this, "Look out", 0xCCFFFFFF);
                more.setPadding(0, 0, 0, Ui.px(14));
                box.addView(more);
                LinearLayout deck = new LinearLayout(this);
                deck.setClipChildren(false);
                if (hasWindows) deck.addView(window(), new LinearLayout.LayoutParams(0, -1, 1.9f));
                if (hasOffice) {
                    LinearLayout.LayoutParams olp = new LinearLayout.LayoutParams(0, -1, 1f);
                    if (hasWindows) olp.leftMargin = Ui.px(18);
                    deck.addView(office(), olp);
                    OfficeService.start(this);
                }
                box.addView(deck, new LinearLayout.LayoutParams(-1, Ui.px(190 * sc)));
                return box;
            }
            case "all":
                allHead = Ui.label(this, "All apps", 0xCCFFFFFF);
                allHead.setPadding(0, 0, 0, Ui.px(10));
                box.addView(allHead);
                allBox = new LinearLayout(this);
                allBox.setOrientation(LinearLayout.VERTICAL);
                allBox.setClipChildren(false);
                box.addView(allBox);
                allCols = new int[]{7, 6, 5}[Prefs.size(this, k)];
                return box;
        }
        return null;
    }

    float watchScale = 1, mineScale = 1, videoScale = 1;
    int allCols = 6;

    @Override protected void onDestroy() { try { unregisterReceiver(pkgs); } catch (Exception ignored) {} super.onDestroy(); }

    // ---------- Header: time, greeting, system ----------

    View header() {
        LinearLayout top = new LinearLayout(this);
        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        date = Ui.label(this, "", 0xB3FFFFFF);
        left.addView(date);
        LinearLayout time = new LinearLayout(this);
        time.setGravity(Gravity.BOTTOM);
        clock = Ui.text(this, Ui.serif, 84, 0xFFFFFFFF);
        clock.setLetterSpacing(-0.02f);
        clock.setShadowLayer(Ui.px(14), 0, Ui.px(2), 0x99000000);
        time.addView(clock);
        seconds = Ui.text(this, Ui.serif, 30, 0xB3FFFFFF); // small seconds beside the big time
        seconds.setPadding(Ui.px(2), 0, 0, Ui.px(13));
        seconds.setShadowLayer(Ui.px(8), 0, Ui.px(1), 0x99000000);
        // fixed size, so the text changing every second never re-lays out the page
        time.addView(seconds, new LinearLayout.LayoutParams(Ui.px(52), Ui.px(48)));
        seconds.setGravity(Gravity.BOTTOM | Gravity.START);
        meridiem = Ui.text(this, Ui.serifItalic, 26, 0xB3FFFFFF);
        meridiem.setPadding(Ui.px(8), 0, 0, Ui.px(14));
        time.addView(meridiem);
        timeRow = time;
        left.addView(time);
        LinearLayout who = new LinearLayout(this);
        who.setGravity(Gravity.CENTER_VERTICAL);
        who.setClipChildren(false);
        who.setPadding(0, Ui.px(6), 0, 0);
        who.addView(avatar(), new LinearLayout.LayoutParams(Ui.px(52), Ui.px(52)));
        greeting = Ui.text(this, Ui.serifItalic, 22, 0xF2FFFFFF);
        greeting.setPadding(Ui.px(12), 0, 0, 0);
        greeting.setShadowLayer(Ui.px(8), 0, Ui.px(1), 0xB3000000);
        who.addView(greeting);
        left.addView(who);
        top.addView(left, new LinearLayout.LayoutParams(0, -2, 1f));

        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setGravity(Gravity.END);
        LinearLayout sys = new LinearLayout(this);
        tourIcons = sys;
        sys.setGravity(Gravity.CENTER_VERTICAL);
        sys.addView(iconButton(Icon.ASSIST, "Assistant", new Intent(Intent.ACTION_ASSIST),
            new Intent("android.intent.action.VOICE_ASSIST"), new Intent(Intent.ACTION_SEARCH_LONG_PRESS),
            new Intent("android.search.action.GLOBAL_SEARCH")));
        sys.addView(iconButton(Icon.RECENTS, "Recent apps", new Intent(this, Recents.class)));
        TvInputManager tim = (TvInputManager) getSystemService(Context.TV_INPUT_SERVICE);
        if (tim != null) for (TvInputInfo in : tim.getTvInputList()) {
            if (in.getType() != TvInputInfo.TYPE_HDMI || in.isHidden(this)) continue;
            CharSequence l = in.loadLabel(this);
            sys.addView(iconButton(Icon.INPUT, l == null ? "HDMI" : l.toString(),
                new Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(in.getId()))));
        }
        sys.addView(iconButton(Icon.WIFI, "Wi-Fi", new Intent(Settings.ACTION_WIFI_SETTINGS)));
        sys.addView(iconButton(Icon.BLUETOOTH, "Bluetooth", new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)));
        sys.addView(iconButton(Icon.SOUND, "Sound", new Intent(Settings.ACTION_SOUND_SETTINGS)));
        // notification centre: every Little Office notice, and approvals you can still answer
        View bell = iconButton(Icon.BELL, "Notifications", new Intent(this, OfficePanel.class));
        bell.setOnClickListener(v -> NoticePanel.open(this, rootView, scroll));
        bellDot = new View(this);
        bellDot.setBackground(Ui.round(0xFFFF6B5A, 5));
        FrameLayout.LayoutParams bdl = new FrameLayout.LayoutParams(Ui.px(10), Ui.px(10), Gravity.TOP | Gravity.END);
        bdl.setMargins(0, Ui.px(10), Ui.px(10), 0);
        ((FrameLayout) bell).addView(bellDot, bdl);
        bellView = bell;
        sys.addView(bell);
        Notices.changed = this::updateBell;
        updateBell();
        View gear = iconButton(Icon.GEAR, "Quick settings", new Intent(Settings.ACTION_SETTINGS));
        gear.setOnClickListener(v -> { quickFrom = v; QuickPanel.open(this, rootView, scroll); }); // quick panel first
        sys.addView(gear);
        right.addView(sys, new LinearLayout.LayoutParams(-2, -2));
        // widgets under the icons: music (only while something plays) and the system glance
        // Widgets under the icons, fixed sizes so nothing on the page moves:
        //  playing:  [swap] [music player]   (swap flips to the system card and back)
        //  idle:     [Play music] 16dp [system card]
        LinearLayout widgets = new LinearLayout(this);
        tourWidgets = widgets;
        widgets.setGravity(Gravity.TOP | Gravity.END);
        widgets.setClipChildren(false);
        swap = iconButton(Icon.SWAP, "Switch between music and system", (Intent[]) null);
        swap.setOnClickListener(v -> { showStats = !showStats; updateWidgets(); });
        widgets.addView(swap);
        musicIdle = idleMusicCard();
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(Ui.px(200), Ui.px(NowPlaying.H));
        widgets.addView(musicIdle, ilp);
        nowPlaying = new NowPlaying(this);
        LinearLayout.LayoutParams nlp2 = new LinearLayout.LayoutParams(-2, -2);
        nlp2.leftMargin = Ui.px(16);
        widgets.addView(nowPlaying, nlp2);
        if (Prefs.shown(this, "system")) {
            stats = new SystemStats(this);
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-2, -2);
            slp.leftMargin = Ui.px(16);
            widgets.addView(stats, slp);
        }
        nowPlaying.onChange = () -> { if (!nowPlaying.active) showStats = false; updateWidgets(); };
        updateWidgets();
        LinearLayout.LayoutParams wlp2 = new LinearLayout.LayoutParams(-2, -2);
        wlp2.topMargin = Ui.px(20);
        wlp2.gravity = Gravity.END;
        right.addView(widgets, wlp2);

        chip = Ui.text(this, Ui.mono, 11, 0xFFFFFFFF);
        chip.setLetterSpacing(0.08f);
        chip.setFocusable(true);
        chip.setPadding(Ui.px(14), Ui.px(8), Ui.px(14), Ui.px(8));
        GradientDrawable cbg = Ui.round(0x33FF6B5A, 18);
        chip.setBackground(cbg);
        chip.setOnFocusChangeListener((v, f) -> { cbg.setColor(f ? 0xFFFF6B5A : 0x33FF6B5A); Ui.lift(v, f, 1.05f); });
        chip.setOnClickListener(v -> startActivity(new Intent(this, Radio.class).putExtra("nowPlaying", true)));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-2, -2);
        clp.topMargin = Ui.px(10);
        left.addView(chip, clp);

        top.addView(right);
        return top;
    }

    // ---------- The World's Windows (deck card: the last view, framed as a window) ----------

    FrameLayout windowCard;
    ImageView windowArt;
    TextView windowName, windowWhere;

    View window() {
        FrameLayout w = new FrameLayout(this);
        w.setFocusable(true);
        w.setClipToOutline(true);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xFF1C2A3E, 0xFF0C0E14});
        bg.setCornerRadius(Ui.px(20));
        w.setBackground(bg);

        int artLeft = Ui.px(250);
        windowArt = new ImageView(this);
        windowArt.setScaleType(ImageView.ScaleType.CENTER_CROP);
        FrameLayout.LayoutParams al = new FrameLayout.LayoutParams(-1, -1);
        al.leftMargin = artLeft;
        w.addView(windowArt, al);
        View fade = new View(this); // the view dissolves into the card on its left edge
        fade.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{0xFF15202F, 0x0015202F}));
        FrameLayout.LayoutParams fl = new FrameLayout.LayoutParams(Ui.px(180), -1);
        fl.leftMargin = artLeft;
        w.addView(fade, fl);
        // window mullions over the view, so it reads as a window rather than a thumbnail
        View mv = new View(this);
        mv.setBackgroundColor(0x2EFFFFFF);
        FrameLayout.LayoutParams mvl = new FrameLayout.LayoutParams(Ui.px(2), -1, Gravity.END);
        mvl.rightMargin = Ui.px(170);
        w.addView(mv, mvl);
        View mh = new View(this);
        mh.setBackgroundColor(0x2EFFFFFF);
        FrameLayout.LayoutParams mhl = new FrameLayout.LayoutParams(-1, Ui.px(2), Gravity.TOP);
        mhl.topMargin = Ui.px(64);
        mhl.leftMargin = artLeft + Ui.px(120);
        w.addView(mh, mhl);

        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setPadding(Ui.px(26), Ui.px(22), 0, Ui.px(20));
        t.addView(Ui.label(this, "The World's Windows · " + Places.count() + " views", 0xB3FFFFFF));
        TextView title = Ui.text(this, Ui.serif, 34, 0xFFFFFFFF);
        title.setText("Somewhere else");
        title.setPadding(0, Ui.px(10), 0, Ui.px(6));
        t.addView(title);
        TextView sub = Ui.text(this, Ui.sans, 12.5f, 0xB3FFFFFF);
        sub.setText("Train windows, rooms with a view, still nature, the sea and the sky.");
        sub.setLineSpacing(Ui.px(3), 1f);
        t.addView(sub);
        t.addView(new View(this), new LinearLayout.LayoutParams(1, 0, 1f));
        windowName = Ui.text(this, Ui.serifItalic, 17, 0xFFFFFFFF);
        windowName.setSingleLine(true);
        windowName.setEllipsize(android.text.TextUtils.TruncateAt.END);
        t.addView(windowName);
        windowWhere = Ui.label(this, "", 0x99FFFFFF);
        windowWhere.setTextSize(11f);
        windowWhere.setSingleLine(true);
        windowWhere.setPadding(0, Ui.px(4), 0, 0);
        t.addView(windowWhere);
        w.addView(t, new FrameLayout.LayoutParams(artLeft + Ui.px(40), -1));

        View ring = new View(this);
        GradientDrawable rd = new GradientDrawable();
        rd.setCornerRadius(Ui.px(20));
        rd.setStroke(Ui.px(1), 0x1FFFFFFF);
        ring.setBackground(rd);
        w.addView(ring, new FrameLayout.LayoutParams(-1, -1));
        w.setOnFocusChangeListener((v, f) -> {
            Ui.lift(v, f, 1.03f);
            // the focus ring alone marks focus: no second border
            if (f) ambient.tint(0xFF5FA8C8);
        });
        w.setOnClickListener(v -> startActivity(new Intent(this, Windows.class)));
        windowCard = w;
        return w;
    }

    // ---------- Little Office ----------

    TextView officeCodex, officeClaude, officeGemini, officeNow;
    View officeDot;

    View office() {
        LinearLayout p = new LinearLayout(this);
        p.setOrientation(LinearLayout.VERTICAL);
        p.setFocusable(true);
        p.setPadding(Ui.px(22), Ui.px(22), Ui.px(20), Ui.px(20));
        Glass.apply(p, 20, 0x8C0E1A14); // frosted glass with a hint of green
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        officeDot = new View(this);
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(Ui.px(8), Ui.px(8));
        dl.rightMargin = Ui.px(10);
        head.addView(officeDot, dl);
        head.addView(Ui.label(this, "Little Office", 0xB3FFFFFF));
        p.addView(head);
        officeCodex = Ui.text(this, Ui.sans, 14, 0xFFFFFFFF);
        officeCodex.setPadding(0, Ui.px(12), 0, Ui.px(6));
        p.addView(officeCodex);
        officeClaude = Ui.text(this, Ui.sans, 14, 0xFFFFFFFF);
        officeClaude.setPadding(0, 0, 0, Ui.px(6));
        p.addView(officeClaude);
        officeGemini = Ui.text(this, Ui.sans, 14, 0xFFFFFFFF);
        p.addView(officeGemini);
        p.addView(new View(this), new LinearLayout.LayoutParams(1, 0, 1f));
        officeNow = Ui.text(this, Ui.serifItalic, 15, 0xD9FFFFFF);
        officeNow.setMaxLines(1);
        officeNow.setEllipsize(android.text.TextUtils.TruncateAt.END);
        p.addView(officeNow);
        p.setOnFocusChangeListener((x, f) -> {
            Ui.lift(x, f, 1.04f);
            if (f) ambient.tint(0xFF24A36B);
        });
        p.setOnClickListener(x -> startActivity(new Intent(this, OfficePanel.class)));
        return p;
    }

    void refreshOffice() {
        if (officeCodex == null) return; // card switched off in features.json
        boolean live = Office.connected();
        if (live) {
            List<String> ord = Office.order();
            TextView[] lines = {officeCodex, officeClaude, officeGemini};
            for (int i = 0; i < 3; i++) {
                lines[i].setText(Office.line(ord.get(i)));
                lines[i].setAlpha(Office.installed(ord.get(i)) ? 1f : 0.45f);
                lines[i].setSingleLine(true);
                lines[i].setEllipsize(android.text.TextUtils.TruncateAt.END);
            }
            String hi = Office.highlight();
            officeNow.setText(hi == null ? "Nothing running right now." : hi);
        } else {
            officeCodex.setText(Office.everConnected || getSharedPreferences("office", MODE_PRIVATE).getLong("lastOk", 0) > 0
                ? "Computer offline" : "Looking for the computer…");
            officeClaude.setText(Office.lastSeen(this));
            officeGemini.setText("");
            officeCodex.setAlpha(1f); officeClaude.setAlpha(1f);
            officeNow.setText("Status will return when it reconnects.");
        }
        boolean needs = false, working = false;
        if (live) for (Office.Session s : Office.sessions) { needs |= s.state.equals("needs_you"); working |= s.state.equals("working"); }
        officeDot.setBackground(Ui.round(!live ? 0x66FFFFFF : needs ? 0xFFFFB454 : working ? 0xFF6FD49B : 0xFF8FB8FF, 4));
    }

    final Runnable officeTick = new Runnable() { public void run() {
        new Thread(() -> { Office.poll(Home.this); h.post(Home.this::refreshOffice); }).start();
        h.postDelayed(this, 15000);
    }};

    void refreshWindow() {
        if (windowName == null) return;
        int i = Places.indexOf(getSharedPreferences("windows", MODE_PRIVATE).getString("last", Places.id(13)));
        if (i < 0) i = 0;
        windowName.setText("Last time: " + Places.name(i));
        windowWhere.setText((Places.where(i) + " · " + Windows.localTime(i)).toUpperCase());
        try (java.io.InputStream in = getAssets().open("windows/" + Places.id(i) + ".jpg")) {
            android.graphics.BitmapFactory.Options o = new android.graphics.BitmapFactory.Options();
            windowArt.setImageBitmap(android.graphics.BitmapFactory.decodeStream(in, null, o));
        } catch (Exception e) { windowArt.setImageDrawable(null); }
    }

    TextView pill(String label, Intent i) {
        TextView t = Ui.text(this, Ui.mono, 11, 0xCCFFFFFF);
        t.setLetterSpacing(0.06f);
        t.setText(label);
        t.setFocusable(true);
        t.setPadding(Ui.px(13), Ui.px(7), Ui.px(13), Ui.px(7));
        GradientDrawable bg = Ui.round(0x14FFFFFF, 16);
        t.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.leftMargin = Ui.px(6);
        t.setLayoutParams(lp);
        t.setOnFocusChangeListener((v, f) -> {
            bg.setColor(f ? 0xFFFFFFFF : 0x14FFFFFF);
            t.setTextColor(f ? 0xFF111114 : 0xCCFFFFFF);
        });
        t.setOnClickListener(v -> start(i));
        return t;
    }

    // ---------- App rows: Watch, My apps, All apps (all rebuilt when apps change) ----------

    LinearLayout scrollRow(LinearLayout col) {
        // Full bleed: the row spans the whole screen, rests at the page margin, and scrolls the focused
        // card into view with a margin, so cards never get chopped by the side of the screen while resting.
        Rails.Rail hs = new Rails.Rail(this, Ui.px(PAD));
        hs.setPadding(Ui.px(PAD), Ui.px(14), Ui.px(PAD), Ui.px(14));
        LinearLayout row = new LinearLayout(this);
        row.setClipChildren(false);
        hs.addView(row);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.leftMargin = -Ui.px(PAD);
        lp.rightMargin = -Ui.px(PAD);
        col.addView(hs, lp);
        return row;
    }

    List<String> watchList() {
        List<String> w = Prefs.list(this, "watch");
        if (w == null) { // first run: start from config/apps.json
            w = new ArrayList<>();
            for (String[] a : APPS) w.add(a[0]);
            Prefs.list(this, "watch", w);
        }
        return w;
    }

    void rebuildApps() {
        appsDirty = false;
        View focused = getCurrentFocus();
        boolean refocus = focused == null || isIn(focused, watchRow) || isIn(focused, mineRow) || isIn(focused, allBox);
        installed = Apps.all(this);
        firstApp = null;
        List<String> watch = watchList();

        // Watch: streaming apps, then a + card to add more
        if (watchRow != null) {
            watchRow.removeAllViews();
            for (int i = 0; i < watch.size(); i++) {
                Apps.App a = Apps.find(installed, watch.get(i));
                if (a == null) continue;
                final int at = i;
                View c = Apps.card(this, a, () -> focusTitle(a.label),
                    v -> { Apps.menu(this, a, new String[]{"Move left", "Move right", "Remove from Watch"},
                        new Runnable[]{() -> move("watch", at, -1), () -> move("watch", at, 1), () -> remove("watch", a.pkg)},
                        this::rebuildApps); return true; });
                add(watchRow, c, 168 * watchScale, 94 * watchScale);
                if (firstApp == null) firstApp = c;
            }
            View plus = plusCard("watch");
            add(watchRow, plus, 94 * watchScale, 94 * watchScale);
            if (firstApp == null) firstApp = plus;
        }

        // Video rows: one per Watch app that publishes continue watching / recommendations
        if (shelvesBox != null) {
            List<String> have = new ArrayList<>();
            for (Shelf sh : shelves) have.add(sh.pkg);
            List<String> want = new ArrayList<>();
            for (String p : watch) if (Apps.find(installed, p) != null) want.add(p);
            if (!have.equals(want)) {
                shelvesBox.removeAllViews();
                shelves.clear();
                for (String p : want) {
                    Shelf sh = new Shelf(this, p, Apps.find(installed, p).label, videoScale);
                    shelves.add(sh);
                    shelvesBox.addView(sh);
                }
            }
            for (Shelf sh : shelves) sh.refresh();
        }

        // My apps: your own row, with a + card to add more
        if (mineRow != null) {
            mineRow.removeAllViews();
            List<String> mine = Prefs.listOrEmpty(this, "mine");
            for (int i = 0; i < mine.size(); i++) {
                Apps.App a = Apps.find(installed, mine.get(i));
                if (a == null) continue;
                final int at = i;
                add(mineRow, Apps.card(this, a, () -> ambient.tint(0xFF8FB8D8),
                    v -> { Apps.menu(this, a, new String[]{"Move left", "Move right", "Remove from My apps"},
                        new Runnable[]{() -> move("mine", at, -1), () -> move("mine", at, 1), () -> remove("mine", a.pkg)},
                        this::rebuildApps); return true; }), 150 * mineScale, 84 * mineScale);
            }
            add(mineRow, plusCard("mine"), 84 * mineScale, 84 * mineScale);
        }

        // All apps: every app you can open, TV or phone, in a grid that always fits the screen
        if (allBox != null) {
            allBox.removeAllViews();
            List<String> hidden = Prefs.listOrEmpty(this, "hidden");
            int cols = allCols;
            float gap = 16, cw = (Ui.dpWidth(this) - 2 * PAD - (cols - 1) * gap) / cols, ch = cw * 0.56f;
            LinearLayout row = null;
            int n = 0;
            for (Apps.App a : installed) {
                if (hidden.contains(a.pkg)) continue;
                if (n % cols == 0) {
                    row = new LinearLayout(this);
                    row.setClipChildren(false);
                    row.setPadding(0, Ui.px(6), 0, Ui.px(10));
                    allBox.addView(row);
                }
                View c = Apps.card(this, a, () -> ambient.tint(0xFF8FB8D8),
                    v -> { Apps.menu(this, a, new String[0], new Runnable[0], this::rebuildApps); return true; });
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.px(cw), Ui.px(ch));
                if (n % cols != cols - 1) lp.rightMargin = Ui.px(gap);
                row.addView(c, lp);
                n++;
            }
            allHead.setText(("All apps · " + n).toUpperCase());
        }
        if (refocus && firstApp != null) firstApp.requestFocus();
    }

    static boolean isIn(View v, View parent) {
        for (View x = v; x != null; x = x.getParent() instanceof View ? (View) x.getParent() : null) if (x == parent) return true;
        return false;
    }

    void add(LinearLayout row, View card, float wDp, float hDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.px(wDp), Ui.px(hDp));
        lp.rightMargin = Ui.px(16);
        row.addView(card, lp);
    }

    void move(String key, int i, int d) {
        List<String> l = Prefs.listOrEmpty(this, key);
        int j = i + d;
        if (i < 0 || i >= l.size() || j < 0 || j >= l.size()) return;
        String t = l.get(i); l.set(i, l.get(j)); l.set(j, t);
        Prefs.list(this, key, l);
        rebuildApps();
    }

    void remove(String key, String pkg) {
        List<String> l = Prefs.listOrEmpty(this, key);
        l.remove(pkg);
        Prefs.list(this, key, l);
        rebuildApps();
    }

    /** The + card: pick any installed app to add to this row. */
    View plusCard(String key) {
        FrameLayout c = Ui.frame(this, 14, 1.07f, () -> focusTitle(key.equals("mine") ? "Add to My apps" : "Add a streaming app"));
        FrameLayout in = Ui.inner(c);
        Glass.apply(in, 14, 0x40101014); // frosted glass
        Icon plus = new Icon(this, Icon.PLUS);
        plus.setPadding(Ui.px(4), Ui.px(4), Ui.px(4), Ui.px(4));
        in.addView(plus, new FrameLayout.LayoutParams(Ui.px(34), Ui.px(34), Gravity.CENTER));
        c.setOnClickListener(v -> {
            List<String> have = Prefs.listOrEmpty(this, key);
            if (key.equals("watch")) have = watchList();
            final List<String> list = have;
            List<Apps.App> choices = new ArrayList<>();
            for (Apps.App a : installed) if (!list.contains(a.pkg)) choices.add(a);
            Apps.pick(this, key.equals("mine") ? "Add to My apps" : "Add to Watch", choices,
                a -> { list.add(a.pkg); Prefs.list(this, key, list); rebuildApps(); });
        });
        return c;
    }

    void focusTitle(String t) { if (focusTitle != null) focusTitle.setText(t); }

    // ---------- Music and system widgets ----------

    View swap, musicIdle;
    boolean showStats;
    QuickPanel quick;
    NoticePanel notices;
    long lastPhotoSync;
    View bellView, bellDot;
    Tour tour;
    View tourIcons, tourWidgets, tourAvatar;
    final java.util.Map<String, View> sectionViews = new java.util.HashMap<>();
    ViewGroup quickPage;
    View quickFrom;
    FrameLayout rootView;

    void updateWidgets() {
        boolean active = nowPlaying.active;
        if (!active) fillIdle();
        swap.setVisibility(active && stats != null ? View.VISIBLE : View.GONE);
        musicIdle.setVisibility(active ? View.GONE : View.VISIBLE);
        nowPlaying.setVisibility(active && !showStats ? View.VISIBLE : View.GONE);
        if (stats != null) stats.setVisibility(!active || showStats ? View.VISIBLE : View.GONE);
        if (stats != null) ((LinearLayout.LayoutParams) stats.getLayoutParams()).leftMargin = Ui.px(16);
    }

    /** Shown while nothing plays: OK lists every app that can play music or video, with its icon. */
    /** Idle music card: "Resume <last thing played>" when there is one (YouTube and YouTube Music stop
     *  their player when you leave them, so this is the way back), otherwise "Play music". */
    TextView idleTop, idleTitle, idleSub;
    Intent idleGo;

    View idleMusicCard() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setGravity(Gravity.CENTER_VERTICAL);
        c.setFocusable(true);
        c.setPadding(Ui.px(18), Ui.px(12), Ui.px(18), Ui.px(12));
        Glass.apply(c, 18, 0x99101014);
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(new Icon(this, Icon.MUSIC), new LinearLayout.LayoutParams(Ui.px(20), Ui.px(20)));
        idleTop = Ui.label(this, "", 0xB3FFFFFF);
        idleTop.setPadding(Ui.px(8), 0, 0, 0);
        top.addView(idleTop, new LinearLayout.LayoutParams(0, -2, 1));
        c.addView(top);
        idleTitle = Ui.text(this, Ui.sansMedium, 15, 0xFFFFFFFF);
        idleTitle.setSingleLine(true);
        idleTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        idleTitle.setPadding(0, Ui.px(8), 0, Ui.px(2));
        c.addView(idleTitle);
        idleSub = Ui.text(this, Ui.sans, 12.5f, 0xA6FFFFFF);
        idleSub.setSingleLine(true);
        idleSub.setEllipsize(android.text.TextUtils.TruncateAt.END);
        c.addView(idleSub);
        Selector.shape(c, Ui.px(18), Ui.px(3)); // ring hugs the card closely, no zoom
        c.setOnClickListener(v -> {
            if (idleGo != null) try { startActivity(idleGo); return; } catch (Exception ignored) {}
            pickMusic();
        });
        c.setOnLongClickListener(v -> { pickMusic(); return true; });
        fillIdle();
        return c;
    }

    void pickMusic() {
        List<Apps.App> media = new ArrayList<>();
        for (Apps.App a : installed) if (MEDIA_APPS.contains(a.pkg) || watchList().contains(a.pkg)) media.add(a);
        Apps.pick(this, "Play with", media, Apps::open0);
    }

    void fillIdle() {
        if (idleTitle == null) return;
        idleGo = null;
        try {
            org.json.JSONArray hist = History.load(this);
            for (int k = 0; k < hist.length(); k++) {
                org.json.JSONObject e = hist.getJSONObject(k);
                if (System.currentTimeMillis() - e.optLong("at") > 3 * 86400000L) break; // only recent listening
                if (e.optLong("at") <= Prefs.sp(this).getLong("resumeClearedAt", 0)) break;  // cleared with Close all
                String pkg = e.optString("pkg");
                if (!MEDIA_APPS.contains(pkg)) continue;
                idleGo = Intent.parseUri(e.optString("intent"), Intent.URI_INTENT_SCHEME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                idleTop.setText("Resume");
                idleTitle.setText(e.optString("title"));
                String app = pkg;
                for (Apps.App a : installed) if (a.pkg.equals(pkg)) { app = a.label; break; }
                String sub = e.optString("sub");
                idleSub.setText(sub.isEmpty() ? app : sub + " · " + app);
                return;
            }
        } catch (Exception ignored) {}
        idleTop.setText("Music");
        idleTitle.setText("Play music");
        idleSub.setText("Choose an app");
    }

    /** Apps that play music or video (the Watch row counts too). */
    static final java.util.Set<String> MEDIA_APPS = new java.util.HashSet<>(java.util.Arrays.asList(
        "com.spotify.tv.android", "com.spotify.music", "com.google.android.youtube.tvmusic", "com.google.android.apps.youtube.music",
        "com.google.android.youtube.tv", "com.google.android.youtube", "com.amazon.amazonvideo.livingroom", "com.netflix.ninja",
        "com.amazon.mp3", "com.apple.android.music", "com.deezer.android.tv", "com.aspiro.tidal", "com.soundcloud.android",
        "com.audiomack", "com.mmm.trebelmusic", "com.boomplay.music", "tv.pluto.android", "com.plexapp.android",
        "org.videolan.vlc", "com.mxtech.videoplayer.ad", "is.xyz.mpv"));

    // ---------- Profile picture and top icons ----------

    View avatar() {
        // outer: focus ring sits outside the photo with a small gap; inner: the round photo itself
        FrameLayout f = new FrameLayout(this);
        tourAvatar = f;
        f.setFocusable(true);
        f.setClipChildren(false);
        f.setPadding(Ui.px(4), Ui.px(4), Ui.px(4), Ui.px(4));
        GradientDrawable ring = new GradientDrawable();
        ring.setShape(GradientDrawable.OVAL);
        ring.setColor(0x00000000);
        f.setBackground(ring);
        FrameLayout in = new FrameLayout(this);
        in.setClipToOutline(true);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(0x40FFFFFF);
        in.setBackground(bg);
        avatarLetter = Ui.text(this, Ui.serif, 20, 0xFFFFFFFF);
        avatarLetter.setGravity(Gravity.CENTER);
        in.addView(avatarLetter, new FrameLayout.LayoutParams(-1, -1));
        avatar = new ImageView(this);
        avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
        in.addView(avatar, new FrameLayout.LayoutParams(-1, -1));
        f.addView(in, new FrameLayout.LayoutParams(-1, -1));
        Selector.circle(f, true);
        f.setOnFocusChangeListener((v, fo) -> Ui.lift(v, fo, 1.06f));
        f.setOnClickListener(v -> startActivity(new Intent(this, Personal.class)));
        return f;
    }

    void loadAvatar() {
        String n = Prefs.name(this);
        avatarLetter.setText(n.isEmpty() ? "" : n.substring(0, 1).toUpperCase());
        java.io.File pic = null;
        // from Little Office first (written by the TV itself), then anything pushed with adb
        for (String d : new String[]{Ambient.SYNCED, Ambient.OWN}) {
            java.io.File dir = getExternalFilesDir(d);
            java.io.File[] all = dir == null ? null : dir.listFiles();
            if (all != null) for (java.io.File f : all) { // profile.jpg, Profile.PNG, lo_profile.webp...
                String fn = f.getName().toLowerCase();
                if (f.canRead() && (fn.startsWith("profile.") || fn.startsWith("lo_profile."))
                    && (fn.endsWith(".jpg") || fn.endsWith(".jpeg") || fn.endsWith(".png") || fn.endsWith(".webp"))) { pic = f; break; }
            }
            if (pic != null) break;
        }
        if (pic == null) { avatar.setImageDrawable(null); avatarFor = ""; return; }
        if ((pic.getPath() + pic.lastModified()).equals(avatarFor)) { avatarLetter.setText(""); return; } // already showing
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(pic.getPath(), o);
        o.inSampleSize = 1;
        while (o.outWidth / (o.inSampleSize * 2) >= 160) o.inSampleSize *= 2;
        o.inJustDecodeBounds = false;
        android.graphics.Bitmap bm = BitmapFactory.decodeFile(pic.getPath(), o);
        if (bm == null) { android.util.Log.w("SteroidHome", "avatar: can't read " + pic); avatar.setImageDrawable(null); return; }
        try { // phone photos are often stored sideways with a rotation tag
            int rot = new android.media.ExifInterface(pic.getPath()).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1);
            int deg = rot == 6 ? 90 : rot == 3 ? 180 : rot == 8 ? 270 : 0;
            if (deg != 0) {
                android.graphics.Matrix m = new android.graphics.Matrix();
                m.postRotate(deg);
                bm = android.graphics.Bitmap.createBitmap(bm, 0, 0, bm.getWidth(), bm.getHeight(), m, true);
            }
        } catch (Exception ignored) {}
        avatar.setImageBitmap(bm);
        avatarLetter.setText("");
        avatarFor = pic.getPath() + pic.lastModified();
    }
    String avatarFor = "";

    /** A round icon button for the top bar; tries each intent in turn (devices differ). */
    View iconButton(int type, String label, Intent... intents) {
        // outer: focus ring outside the button; inner: the round button with its Lucide icon
        FrameLayout f = new FrameLayout(this);
        f.setFocusable(true);
        f.setClipChildren(false);
        f.setContentDescription(label);
        f.setPadding(Ui.px(4), Ui.px(4), Ui.px(4), Ui.px(4));
        GradientDrawable ring = new GradientDrawable();
        ring.setShape(GradientDrawable.OVAL);
        ring.setColor(0x00000000);
        f.setBackground(ring);
        Icon b = new Icon(this, type);
        b.setPadding(Ui.px(11), Ui.px(11), Ui.px(11), Ui.px(11));
        GradientDrawable g = new GradientDrawable(); // nothing at rest: the icon stands alone
        g.setShape(GradientDrawable.OVAL);
        g.setColor(0x00000000);
        b.setBackground(g);
        f.addView(b, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.px(54), Ui.px(54));
        lp.leftMargin = Ui.px(6);
        f.setLayoutParams(lp);
        f.setOnFocusChangeListener((v, fo) -> {
            g.setColor(fo ? 0x8C000000 : 0x00000000); // dark disc only while selected, for contrast
            Ui.lift(v, fo, 1.06f);
        });
        Selector.circle(f, true);
        f.setOnClickListener(v -> {
            for (Intent i : intents) {
                try { startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return; } catch (Exception ignored) {}
            }
            Toast.makeText(this, label + " isn't available", Toast.LENGTH_SHORT).show();
        });
        return f;
    }

    // ---------- Retro TV ----------

    View retro() {
        retroPanel = new RetroPanel(this);
        retroPanel.setClipChildren(false);
        retroPanel.setClipToPadding(false);
        retroPanel.setPadding(Ui.px(22), Ui.px(18), Ui.px(22), Ui.px(16));
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setClipChildren(false);
        // the text column stops before the little TV on the right, so nothing runs under it
        int crt = Ui.px(RetroPanel.TV_W + 16);
        TextView lab = Ui.label(this, "Retro TV", 0xB3FFFFFF);
        lab.setPadding(0, 0, crt, 0);
        v.addView(lab);
        retroTitle = Ui.text(this, Ui.serif, 28, 0xFFFFFFFF);
        retroTitle.setSingleLine(true);
        retroTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        retroTitle.setIncludeFontPadding(false);
        retroTitle.setPadding(0, Ui.px(10), crt, Ui.px(6));
        v.addView(retroTitle);
        retroBlurb = Ui.text(this, Ui.sans, 13, 0xBFFFFFFF);
        retroBlurb.setMaxLines(2);
        retroBlurb.setLineSpacing(Ui.px(3), 1f);
        retroBlurb.setEllipsize(android.text.TextUtils.TruncateAt.END);
        retroBlurb.setPadding(0, 0, crt, 0);
        v.addView(retroBlurb);
        v.addView(new View(this), new LinearLayout.LayoutParams(1, 0, 1f)); // the dial sits on the bottom edge
        LinearLayout.LayoutParams gapLp = new LinearLayout.LayoutParams(1, Ui.px(12));
        v.addView(new View(this), gapLp);

        // decade dial: six equal chips that always fit the card's width
        LinearLayout dial = new LinearLayout(this);
        dial.setClipChildren(false);
        for (int i = 0; i < Retro.DECADES.length; i++) {
            View d = decade(i);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.px(34), 1f);
            if (i > 0) lp.leftMargin = Ui.px(8);
            dial.addView(d, lp);
        }
        v.addView(dial, new LinearLayout.LayoutParams(-1, Ui.px(34)));
        retroPanel.addView(v, new FrameLayout.LayoutParams(-1, -1));
        setDecade(0);
        return retroPanel;
    }

    View decade(int i) {
        String[] d = Retro.DECADES[i];
        int accent = Color.parseColor(d[3]);
        TextView t = Ui.text(this, Ui.serifItalic, 17, 0xD9FFFFFF);
        t.setText("’" + d[0]);
        t.setGravity(Gravity.CENTER);
        t.setIncludeFontPadding(false);
        t.setSingleLine(true);
        t.setFocusable(true);
        GradientDrawable bg = Ui.round(0x1AFFFFFF, 12);
        t.setBackground(bg);
        Selector.shape(t, Ui.px(12), Ui.px(3));
        t.setOnFocusChangeListener((v, f) -> {
            bg.setColor(f ? accent : 0x1AFFFFFF);
            t.setTextColor(f ? 0xFF121014 : 0xD9FFFFFF);
            if (f) setDecade(i);
        });
        t.setOnClickListener(v -> startActivity(new Intent(this, Retro.class).putExtra("decade", i)));
        return t;
    }

    void setDecade(int i) {
        String[] d = Retro.DECADES[i];
        int accent = Color.parseColor(d[3]);
        retroTitle.setText("The " + d[1]);
        retroBlurb.setText(d[4]);
        retroPanel.setDecade(i, accent);
        ambient.tint(accent);
    }

    /** Panel background: tinted glass with a little CRT set on the right showing the decade. */
    class RetroPanel extends FrameLayout {
        final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), scan = new Paint(), glass = new Paint(Paint.ANTI_ALIAS_FLAG),
            body = new Paint(Paint.ANTI_ALIAS_FLAG), stroke = new Paint(Paint.ANTI_ALIAS_FLAG), digits = new Paint(Paint.ANTI_ALIAS_FLAG);
        static final int TV_W = 72;
        int decade, accent = 0xFFD8B47A;

        RetroPanel(Context c) {
            super(c);
            setWillNotDraw(false);
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeWidth(Ui.px(1));
            stroke.setColor(0x1FFFFFFF);
            scan.setColor(0x22000000);
            digits.setTypeface(Ui.serifItalic);
            digits.setTextAlign(Paint.Align.CENTER);
        }

        void setDecade(int d, int a) { decade = d; accent = a; invalidate(); }

        @Override protected void onDraw(Canvas c) {
            int w = getWidth(), h = getHeight();
            float r = Ui.px(20);
            fill.setShader(new LinearGradient(0, 0, w, h, Ui.withAlpha(accent, 0x40), 0x1A0E0C12, Shader.TileMode.CLAMP));
            c.drawRoundRect(0, 0, w, h, r, r, fill);
            c.drawRoundRect(0.5f, 0.5f, w - 0.5f, h - 0.5f, r, r, stroke);

            // CRT set
            float tw = Ui.px(TV_W), th = Ui.px(TV_W * 0.72f), tx = w - tw - Ui.px(22), ty = Ui.px(18);
            body.setColor(decade <= 2 ? 0xFF4A3426 : decade <= 4 ? 0xFF2A2A30 : 0xFF141418);
            c.drawRoundRect(tx, ty, tx + tw, ty + th, Ui.px(decade <= 2 ? 16 : 8), Ui.px(decade <= 2 ? 16 : 8), body);
            float inset = Ui.px(decade >= 5 ? 5 : 10);
            float sx = tx + inset, sy = ty + inset, sw = tw - inset * (decade >= 5 ? 2 : 2.8f), sh = th - inset * 2;
            float sr = Ui.px(decade <= 3 ? 14 : 4);
            int screen = decade == 0 ? 0xFFBFC4C0 : accent;
            glass.setShader(new RadialGradient(sx + sw / 2, sy + sh / 2, sw * 0.75f,
                Ambient.blend(screen, 0xFFFFFFFF, 0.35f), Ambient.blend(screen, 0xFF000000, 0.55f), Shader.TileMode.CLAMP));
            c.drawRoundRect(sx, sy, sx + sw, sy + sh, sr, sr, glass);
            c.save();
            c.clipRect(sx, sy, sx + sw, sy + sh);
            for (float y = sy; y < sy + sh; y += Ui.px(3)) c.drawRect(sx, y, sx + sw, y + Ui.px(1), scan);
            c.restore();
            digits.setTextSize(Ui.px(22));
            digits.setColor(0xE6FFFFFF);
            digits.setShadowLayer(Ui.px(10), 0, 0, accent);
            c.drawText("’" + Retro.DECADES[decade][0], sx + sw / 2, sy + sh / 2 + Ui.px(9), digits);
            if (decade < 5) { // knobs
                body.setColor(0x66FFFFFF);
                c.drawCircle(tx + tw - inset * 0.9f, ty + th * 0.35f, Ui.px(2.5f), body);
                c.drawCircle(tx + tw - inset * 0.9f, ty + th * 0.6f, Ui.px(2.5f), body);
            }
            c.drawRect(tx + tw * 0.3f, ty + th, tx + tw * 0.7f, ty + th + Ui.px(5), body);
            super.onDraw(c);
        }
    }

    // ---------- Radio ----------

    // ---------- First time tour ----------

    void startTour() {
        if (tour != null || isFinishing()) return;
        if (quick != null) quick.close();
        String n = Prefs.name(this);
        java.util.List<Tour.Step> st = new java.util.ArrayList<>();
        boolean unnamed = Prefs.defaultName(this) || n.isEmpty();
        st.add(new Tour.Step(null, "Welcome", unnamed ? "Welcome to your new home screen" : "Welcome, " + n,
            "A quick look at what's here and how to use it with the remote. Next and Back move through the tour; Skip ends it any time."));
        if (unnamed) st.add(new Tour.Step(() -> greeting, "Make it yours", "Hello, User? Let's fix that",
            "The greeting says \"User\" until you add your name. Open Launcher settings and type your name at the top; your photo can go on the round avatar beside it.",
            "Add my name", () -> startActivity(new Intent(this, Personal.class))));
        st.add(new Tour.Step(() -> timeRow, "Header", "Time, date and a greeting",
            "Choose what shows here (seconds, year, greeting) in Quick settings, then Launcher settings."));
        st.add(new Tour.Step(() -> bellView, "Notifications", "Everything you missed",
            "The bell keeps every Little Office notice, with a dot when there's something new. Requests waiting for approval sit at the top: approve or reject them right there. Hold OK on a notice to clear it, or Clear all.",
            "Open notifications", () -> NoticePanel.open(this, rootView, scroll)));
        st.add(new Tour.Step(() -> tourIcons, "Top icons", "Voice, inputs and the Control centre",
            "The mic asks Google Assistant. Then Recent apps, your HDMI inputs, Wi‑Fi, Bluetooth, Sound and the bell. The gear opens the Control centre.",
            "Open Quick settings", () -> { quickFrom = null; QuickPanel.open(this, rootView, scroll); }));
        st.add(new Tour.Step(() -> tourWidgets, "Widgets", "Music and system at a glance",
            "While something plays, the player shows here: OK plays or pauses, left and right reach previous, next and close. Press Home while YouTube or YouTube Music plays and it keeps playing here. The swap button flips to RAM, storage, CPU and network."));
        addSection(st, "watch", "Watch", "Your streaming apps",
            "Hold OK on any app for options: move it, hide it, open its info or uninstall. The + tile adds apps to this row.");
        addSection(st, "videos", "Recently played", "Pick up where you left off",
            "Each app gets its own row of what you played lately, newest first. OK opens it right where it was.");
        addSection(st, "worlds", "Channels", "Retro TV, Live TV and Radio",
            "Retro TV tunes decades from the '60s to the '10s. Live TV plays news channels' own live streams. Radio plays internet stations, and you can add your own.");
        addSection(st, "lookout", "Look out", "The World's Windows and Little Office",
            "Windows are calm live views from around the world. Little Office shows what Claude Code, Codex and Gemini are doing on your computers, and can approve Claude Code requests from the remote.");
        addSection(st, "all", "All apps", "Every app on the TV",
            "Phone apps show with their own icons too. Hold OK on an app for the same options as above.");
        st.add(new Tour.Step(() -> tourAvatar, "You", "Your profile and settings",
            unnamed ? "Your avatar opens Launcher settings. Change \"User\" to your name there, then background photos, rows, hidden apps and header options."
                : "Your photo opens Launcher settings: your name, background photos, arrange or hide rows, hidden apps and header options.",
            unnamed ? "Add my name" : null, unnamed ? () -> startActivity(new Intent(this, Personal.class)) : null));
        st.add(new Tour.Step(() -> tourCardView, "Tour", "Come back any time",
            "This card at the end of the home screen replays the tour, for you or anyone else using the TV. It's also in the Control centre."));
        st.add(new Tour.Step(null, "Done", "That's everything",
            "Back never leaves the home screen. Home on the remote always brings you here."));
        tour = Tour.start(this, rootView, selector, scroll, st, () -> { tour = null; Prefs.put(this, "tourDone", true); });
    }

    // ---------- Mini player rescue ----------
    LinearLayout pipBar;
    TextView pipText;
    View pipFull;
    String pipPkg;

    /** A bar at the top when some app is floating in a mini player: Full screen or Close, for any app. */
    View pipBar() {
        pipBar = new LinearLayout(this);
        pipBar.setGravity(Gravity.CENTER_VERTICAL);
        pipBar.setPadding(Ui.px(18), Ui.px(10), Ui.px(10), Ui.px(10));
        pipBar.setClipChildren(false);
        GradientDrawable bg = Ui.round(0xE6141418, 22);
        bg.setStroke(Ui.px(1), 0x40FFFFFF);
        pipBar.setBackground(bg);
        pipBar.addView(new Icon(this, Icon.MONITOR), new LinearLayout.LayoutParams(Ui.px(22), Ui.px(22)));
        pipText = Ui.text(this, Ui.sansMedium, 16, 0xFFFFFFFF);
        pipText.setSingleLine(true);
        pipText.setEllipsize(android.text.TextUtils.TruncateAt.END);
        pipText.setPadding(Ui.px(12), 0, Ui.px(12), 0);
        pipBar.addView(pipText, new LinearLayout.LayoutParams(0, -2, 1));
        pipFull = pipButton("Full screen", true, () -> {
            String p = pipPkg;
            if (p != null && !PipWatch.full(this, p)) Toast.makeText(this, "Couldn't reopen it. Run scripts/buchi.sh pip full on the Mac.", Toast.LENGTH_LONG).show();
        });
        pipBar.addView(pipFull, new LinearLayout.LayoutParams(-2, Ui.px(42)));
        View close = pipButton("Close", false, () -> {
            String p = pipPkg;
            if (p == null) return;
            PipWatch.close(this, p);
            h.postDelayed(() -> { // still floating? open it full screen, where Back closes it normally
                new Thread(() -> {
                    String still = PipWatch.pinned(this);
                    h.post(() -> {
                        if (p.equals(still)) { PipWatch.full(this, p); Toast.makeText(this, "Press Back in the app to close it", Toast.LENGTH_LONG).show(); }
                        else checkPip();
                    });
                }).start();
            }, 1500);
        });
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(-2, Ui.px(42));
        cl.leftMargin = Ui.px(10);
        pipBar.addView(close, cl);
        pipBar.setVisibility(View.GONE);
        return pipBar;
    }

    TextView pipButton(String label, boolean primary, Runnable run) {
        TextView b = Ui.text(this, Ui.sansMedium, 15, primary ? 0xFF0E0E12 : 0xFFFFFFFF);
        b.setText(label);
        b.setGravity(Gravity.CENTER);
        b.setFocusable(true);
        b.setPadding(Ui.px(20), 0, Ui.px(20), 0);
        GradientDrawable g = Ui.round(primary ? 0xFFFFFFFF : 0x26FFFFFF, 21);
        b.setBackground(g);
        b.setOnFocusChangeListener((v, f) -> { if (!primary) g.setColor(f ? 0x40FFFFFF : 0x26FFFFFF); });
        b.setOnClickListener(v -> run.run());
        return b;
    }

    final Runnable pipPoll = new Runnable() { public void run() { checkPip(); h.postDelayed(this, 3000); } };

    void checkPip() {
        new Thread(() -> {
            String p = PipWatch.pinned(this);
            h.post(() -> {
                boolean was = pipPkg != null;
                pipPkg = p;
                if (p == null) { pipBar.setVisibility(View.GONE); return; }
                String label = p;
                for (Apps.App a : installed) if (a.pkg.equals(p)) { label = a.label; break; }
                pipText.setText(label + " is in a mini player · press Home again to bring it back");
                pipBar.setVisibility(View.VISIBLE);
                if (!was && tour == null && quick == null) { scroll.smoothScrollTo(0, 0); pipFull.requestFocus(); } // one press of OK brings it back
            });
        }).start();
    }

    /** End of the home screen: replay the tour. */
    View tourCard() {
        LinearLayout c = new LinearLayout(this);
        c.setGravity(Gravity.CENTER_VERTICAL);
        c.setFocusable(true);
        c.setPadding(Ui.px(26), Ui.px(18), Ui.px(26), Ui.px(18));
        Glass.apply(c, 22, 0x8C101014);
        Selector.shape(c, Ui.px(22), false);
        FrameLayout ic = new FrameLayout(this);
        GradientDrawable ib = new GradientDrawable();
        ib.setShape(GradientDrawable.OVAL);
        ib.setColor(0x338FB8FF);
        ic.setBackground(ib);
        Icon i = new Icon(this, Icon.COMPASS);
        i.color(0xFFBFD6FF);
        ic.addView(i, new FrameLayout.LayoutParams(Ui.px(26), Ui.px(26), Gravity.CENTER));
        c.addView(ic, new LinearLayout.LayoutParams(Ui.px(56), Ui.px(56)));
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setPadding(Ui.px(20), 0, Ui.px(12), 0);
        t.addView(Ui.label(this, "Guided tour", 0xB3FFFFFF));
        TextView a = Ui.text(this, Ui.serif, 26, 0xFFFFFFFF);
        a.setText("New here? Take the tour");
        a.setSingleLine(true);
        a.setPadding(0, Ui.px(6), 0, Ui.px(2));
        t.addView(a);
        TextView b = Ui.text(this, Ui.sans, 13, 0xB3FFFFFF);
        b.setText("Two minutes on what's here and how to use it with the remote. Skip any time.");
        b.setSingleLine(true);
        b.setEllipsize(android.text.TextUtils.TruncateAt.END);
        t.addView(b);
        c.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
        TextView go = Ui.text(this, Ui.sansMedium, 15, 0xFF0E0E12);
        go.setText("Start");
        go.setGravity(Gravity.CENTER);
        go.setPadding(Ui.px(22), 0, Ui.px(22), 0);
        go.setBackground(Ui.round(0xFFFFFFFF, 20));
        c.addView(go, new LinearLayout.LayoutParams(-2, Ui.px(40)));
        c.setOnFocusChangeListener((v, f) -> Ui.lift(v, f, 1.02f));
        c.setOnClickListener(v -> startTour());
        tourCardView = c;
        return c;
    }
    View tourCardView;

    void addSection(java.util.List<Tour.Step> st, String key, String kicker, String title, String body) {
        if (!sectionViews.containsKey(key)) return;
        st.add(new Tour.Step(() -> sectionViews.get(key), kicker, title, body));
    }

    /** Live TV card: news channels' own 24 hour streams. */
    View liveCard() {
        FrameLayout p = new FrameLayout(this);
        p.setFocusable(true);
        p.setPadding(Ui.px(22), Ui.px(18), Ui.px(22), Ui.px(16));
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0x40E8463C, 0x1A0E1218});
        bg.setCornerRadius(Ui.px(20));
        bg.setStroke(Ui.px(1), 0x1FFFFFFF);
        p.setBackground(bg);
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        v.addView(Ui.label(this, "● Live TV · " + LiveTV.CHANNELS.length + " channels", 0xE6FF8A80));
        TextView t = Ui.text(this, Ui.serif, 34, 0xFFFFFFFF);
        t.setText("On air");
        t.setPadding(0, Ui.px(8), 0, Ui.px(4));
        t.setMaxLines(1);
        v.addView(t);
        TextView sub = Ui.text(this, Ui.sans, 12.5f, 0xB3FFFFFF);
        sub.setText("Channels TV, Arise, Al Jazeera, DW and more");
        sub.setMaxLines(2);
        sub.setEllipsize(android.text.TextUtils.TruncateAt.END);
        v.addView(sub);
        p.addView(v, new FrameLayout.LayoutParams(-1, -1));
        p.setOnFocusChangeListener((x, f) -> {
            Ui.lift(x, f, 1.04f);
            // the focus ring alone marks focus: no second border
            if (f) ambient.tint(0xFFE8463C);
        });
        p.setOnClickListener(x -> startActivity(new Intent(this, LiveTV.class)));
        return p;
    }

    View radio() {
        FrameLayout p = new FrameLayout(this);
        p.setFocusable(true);
        p.setPadding(Ui.px(22), Ui.px(18), Ui.px(22), Ui.px(16));
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0x40FF6B5A, 0x1A120E18});
        bg.setCornerRadius(Ui.px(20));
        bg.setStroke(Ui.px(1), 0x1FFFFFFF);
        p.setBackground(bg);
        View arcs = new View(this) {
            final Paint a = new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override protected void onDraw(Canvas c) {
                a.setStyle(Paint.Style.STROKE);
                a.setStrokeWidth(Ui.px(1.5f));
                float cx = getWidth() - Ui.px(8), cy = getHeight() - Ui.px(8);
                for (int i = 1; i <= 5; i++) {
                    a.setColor(Ui.withAlpha(0xFFFFB199, 0x50 - i * 8));
                    float rr = Ui.px(22 * i);
                    c.drawArc(cx - rr, cy - rr, cx + rr, cy + rr, 180, 90, false, a);
                }
            }
        };
        p.addView(arcs, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        v.addView(Ui.label(this, "Radio · " + Stations.count() + " stations", 0xB3FFFFFF));
        radioName = Ui.text(this, Ui.serif, 34, 0xFFFFFFFF);
        radioName.setPadding(0, Ui.px(8), 0, Ui.px(4));
        radioName.setMaxLines(1);
        radioName.setEllipsize(android.text.TextUtils.TruncateAt.END);
        v.addView(radioName);
        radioState = Ui.text(this, Ui.sans, 12, 0xB3FFFFFF);
        radioState.setMaxLines(2);
        v.addView(radioState);
        p.addView(v, new FrameLayout.LayoutParams(-1, -1));
        p.setOnFocusChangeListener((x, f) -> {
            Ui.lift(x, f, 1.04f);
            // the focus ring alone marks focus: no second border
            if (f) ambient.tint(RadioService.station >= 0 ? Stations.HUE[RadioService.station] : 0xFFFF6B5A);
        });
        p.setOnClickListener(x -> startActivity(new Intent(this, Radio.class)
            .putExtra("nowPlaying", RadioService.station >= 0)));
        return p;
    }

    void refreshRadio() {
        if (radioName == null) { chip.setVisibility(View.GONE); return; }
        int s = RadioService.station;
        boolean on = s >= 0;
        radioName.setText(on ? Stations.name(s).split(" — ")[0] : "Tune in");
        radioState.setText(on ? (RadioService.track.isEmpty() ? "On air now" : RadioService.track)
            : "Your CLI AMP stations, on the big screen.");
        chip.setVisibility(on ? View.VISIBLE : View.GONE);
        if (on) chip.setText("● ON AIR  " + Stations.name(s).split(" — ")[0].toUpperCase());
    }

    // ---------- Plumbing ----------

    void start(Intent i) {
        try { startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
        catch (Exception e) { Toast.makeText(this, "Unavailable", Toast.LENGTH_SHORT).show(); }
    }

    final Runnable tick = new Runnable() { public void run() {
        h.removeCallbacks(this); // never two clocks running
        Calendar c = Calendar.getInstance();
        boolean h24 = DateFormat.is24HourFormat(Home.this);
        clock.setText(DateFormat.format(h24 ? "HH:mm" : "h:mm", c));
        boolean sec = Prefs.showSeconds(Home.this);
        seconds.setVisibility(sec ? View.VISIBLE : View.GONE);
        if (sec) seconds.setText(String.format(java.util.Locale.US, ":%02d", c.get(Calendar.SECOND)));
        meridiem.setText(h24 ? "" : (c.get(Calendar.AM_PM) == Calendar.AM ? "am" : "pm"));
        date.setText(DateFormat.format(Prefs.showYear(Home.this) ? "EEEE · d MMMM yyyy" : "EEEE · d MMMM", c).toString().toUpperCase());
        date.setVisibility(Prefs.showDate(Home.this) ? View.VISIBLE : View.INVISIBLE);
        timeRow.setVisibility(Prefs.showClock(Home.this) ? View.VISIBLE : View.GONE);
        greeting.setVisibility(Prefs.showGreeting(Home.this) ? View.VISIBLE : View.INVISIBLE);
        int hr = c.get(Calendar.HOUR_OF_DAY);
        String n = Prefs.name(Home.this);
        String g = hr < 5 ? "Still up" : hr < 12 ? "Good morning" : hr < 17 ? "Good afternoon" : hr < 21 ? "Good evening" : "Good night";
        greeting.setText(n.isEmpty() ? g + (hr < 5 ? "?" : ".") : g + ", " + n + (hr < 5 ? "?" : "."));
        // with seconds: tick on each second (one small text change); without: once a minute
        if (sec) h.postDelayed(this, 1000 - c.get(Calendar.MILLISECOND));
        else h.postDelayed(this, 60000 - c.get(Calendar.SECOND) * 1000);
    }};

    @Override protected void onResume() {
        BgPlayer.homeShown(this); // YouTube stopped because you came home? keep it playing
        super.onResume();
        if (Prefs.layoutVersion(this) != layoutVersion) { recreate(); return; } // arrangement changed
        resumed = true;
        tick.run();
        ambient.reload();
        loadAvatar();
        nowPlaying.start();
        fillIdle(); // Close all in Recent apps also clears the Resume card
        updateBell();
        if (System.currentTimeMillis() - lastPhotoSync > 30_000) { // your photos and avatar from Little Office
            lastPhotoSync = System.currentTimeMillis();
            new Thread(() -> { if (Office.addr.isEmpty()) Office.poll(this); Photos.sync(this); }).start();
        }
        h.removeCallbacks(pipPoll);
        h.postDelayed(pipPoll, 800); // an app left floating in a mini player?
        if (stats != null) stats.start();
        if (appsDirty) rebuildApps();
        else for (Shelf sh : shelves) sh.refresh(); // video rows follow what you just watched
        // the greeting breathes very gently: one property animation on the render thread, no redraw cost
        if (breathe == null) {
            breathe = android.animation.ObjectAnimator.ofPropertyValuesHolder(greeting,
                android.animation.PropertyValuesHolder.ofFloat(View.ALPHA, 1f, 0.72f),
                android.animation.PropertyValuesHolder.ofFloat(View.TRANSLATION_X, 0f, Ui.px(3)));
            breathe.setDuration(3600);
            breathe.setRepeatCount(android.animation.ValueAnimator.INFINITE);
            breathe.setRepeatMode(android.animation.ValueAnimator.REVERSE);
            breathe.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
        }
        if (breathe.isPaused()) breathe.resume(); else if (!breathe.isStarted()) breathe.start();
        RadioService.listener = this::refreshRadio;
        refreshRadio();
        refreshWindow();
        refreshOffice();
        h.removeCallbacks(officeTick);
        if (officeCodex != null) h.post(officeTick);
    }

    @Override public void onWindowFocusChanged(boolean f) {
        super.onWindowFocusChanged(f);
        ambient.run(f);
        if (f) { loadAvatar(); tick.run(); } // a photo or name pushed while the home screen was up shows at once
    }

    @Override protected void onPause() {
        h.removeCallbacks(pipPoll);
        super.onPause();
        resumed = false;
        h.removeCallbacks(officeTick);
        h.removeCallbacks(tick);
        ambient.run(false);
        nowPlaying.stop();
        if (stats != null) stats.stop();
        if (breathe != null) breathe.pause();
    }

    @Override public void onBackPressed() {
        if (notices != null) notices.close();
        else if (quick != null) quick.close(); /* otherwise home stays home */
    }

    /** A dot on the bell while there are notices you haven't seen. */
    void updateBell() { if (bellDot != null) bellDot.setVisibility(Notices.unread(this) > 0 ? View.VISIBLE : View.GONE); }

    /** Pressing Home on the remote always lands at the top, on the first app. */
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Home pressed while you're already on the home screen and an app floats in a mini player:
        // push it back up into its app, full screen. (Coming home from another app never does this.)
        if (hasWindowFocus() && pipPkg != null && pipBar != null && pipBar.getVisibility() == View.VISIBLE) {
            PipWatch.full(this, pipPkg);
            return;
        }
        if (scroll != null) scroll.scrollTo(0, 0);
        if (firstApp != null) firstApp.requestFocus();
    }
}
