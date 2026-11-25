package tv.steroid.home;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;

/**
 * Bluetooth: a glass panel (same look as Notifications) with every paired device, whether it is
 * connected and its battery when the device reports one. OK on a device connects it, or opens its
 * page in the TV's settings. Pair a new device and Bluetooth settings open the TV's own screens,
 * trying every known way in, because the plain Android Bluetooth screen doesn't exist on this TV.
 * There is no off switch on purpose: the remote talks Bluetooth, so turning it off would leave you
 * with no way to turn it back on.
 */
final class BluetoothPanel extends FrameLayout {
    static final int PW = 460;
    static final String TVS = "com.android.tv.settings";
    final Home home;
    final LinearLayout list, devices;
    final TextView state;
    final Handler h = new Handler(Looper.getMainLooper());
    View first;
    String shown = "";

    BluetoothPanel(Home hm) {
        super(hm);
        home = hm;
        setBackgroundColor(0x66000000);
        setClickable(true);
        FrameLayout panel = new FrameLayout(hm);
        Glass.apply(panel, 36, 0x99101014);
        LayoutParams lp = new LayoutParams(Ui.px(PW), -1, Gravity.END);
        lp.setMargins(0, Ui.px(12), Ui.px(16), Ui.px(12));
        addView(panel, lp);
        Rails.Page scroll = new Rails.Page(hm, Ui.px(28));
        scroll.setClipToPadding(false);
        scroll.setPadding(0, Ui.px(24), 0, Ui.px(24));
        list = new LinearLayout(hm);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(Ui.px(24), 0, Ui.px(24), 0);
        list.setClipChildren(false);
        scroll.addView(list, new FrameLayout.LayoutParams(-1, -2));
        panel.addView(scroll, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout head = new LinearLayout(hm);
        head.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout disc = new FrameLayout(hm);
        GradientDrawable db = new GradientDrawable();
        db.setShape(GradientDrawable.OVAL);
        db.setColor(0xFF3B82F6);
        disc.setBackground(db);
        disc.addView(new Icon(hm, Icon.BLUETOOTH), new FrameLayout.LayoutParams(Ui.px(22), Ui.px(22), Gravity.CENTER));
        head.addView(disc, new LinearLayout.LayoutParams(Ui.px(42), Ui.px(42)));
        LinearLayout ht = new LinearLayout(hm);
        ht.setOrientation(LinearLayout.VERTICAL);
        ht.setPadding(Ui.px(14), 0, 0, 0);
        TextView t = Ui.text(hm, Ui.serif, 30, 0xFFFFFFFF);
        t.setText("Bluetooth");
        t.setSingleLine(true);
        ht.addView(t);
        state = Ui.text(hm, Ui.sans, 13, 0xB3FFFFFF);
        state.setSingleLine(true);
        ht.addView(state);
        head.addView(ht, new LinearLayout.LayoutParams(0, -2, 1));
        list.addView(head);

        TextView note = Ui.text(hm, Ui.sans, 13, 0x99FFFFFF);
        note.setText("Always on: your remote connects over Bluetooth.");
        note.setPadding(Ui.px(4), Ui.px(14), 0, 0);
        list.addView(note);

        LinearLayout acts = new LinearLayout(hm);
        acts.setPadding(0, Ui.px(16), 0, 0);
        acts.setClipChildren(false);
        TextView pair = pill("Pair a new device", true), set = pill("Bluetooth settings", false);
        acts.addView(pair, new LinearLayout.LayoutParams(0, Ui.px(40), 1));
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(0, Ui.px(40), 1);
        sl.leftMargin = Ui.px(10);
        acts.addView(set, sl);
        list.addView(acts);
        pair.setOnClickListener(v -> launch("Pairing", pairIntents()));
        set.setOnClickListener(v -> launch("Bluetooth settings", settingsIntents()));
        first = pair;

        devices = new LinearLayout(hm);
        devices.setOrientation(LinearLayout.VERTICAL);
        devices.setClipChildren(false);
        list.addView(devices, new LinearLayout.LayoutParams(-1, -2));
        fill();
        h.postDelayed(tick, 3000);
    }

    final Runnable tick = new Runnable() { public void run() { fill(); h.postDelayed(this, 3000); } };

    // ---------- the TV's own Bluetooth screens: every known way in, first one that opens wins ----------

    static Intent comp(String cls) { return new Intent().setComponent(new ComponentName(TVS, TVS + cls)); }

    static Intent[] pairIntents() {
        return new Intent[] {
            new Intent("com.google.android.intent.action.CONNECT_INPUT"),
            comp(".accessories.AddAccessoryActivity"),
            new Intent("android.settings.BLUETOOTH_PAIRING_SETTINGS"),
            new Intent(Settings.ACTION_BLUETOOTH_SETTINGS),
        };
    }

    static Intent[] settingsIntents() {
        return new Intent[] {
            new Intent(Settings.ACTION_BLUETOOTH_SETTINGS),
            comp(".accessories.AccessoriesActivity"),
            new Intent("com.android.tv.settings.ACCESSORIES"),
            comp(".accessories.AddAccessoryActivity"),
            new Intent("com.google.android.intent.action.CONNECT_INPUT"),
            new Intent(Settings.ACTION_SETTINGS),   // last resort: Settings, then Remotes & Accessories
        };
    }

    static Intent[] deviceIntents(BluetoothDevice d) {
        Intent x = comp(".accessories.BluetoothAccessoryActivity")
            .putExtra("accessory_address", d.getAddress()).putExtra("accessory_name", name(d))
            .putExtra("accessory_icon_res", 0);
        Intent[] rest = settingsIntents();
        Intent[] all = new Intent[rest.length + 1];
        all[0] = x;
        System.arraycopy(rest, 0, all, 1, rest.length);
        return all;
    }

    void launch(String what, Intent[] chain) {
        for (Intent i : chain) {
            try { home.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); close(); return; }
            catch (Exception ignored) {}   // not on this TV, or not open to other apps: try the next
        }
        Toast.makeText(home, what + " isn't available on this TV", Toast.LENGTH_SHORT).show();
    }

    // ---------- devices ----------

    static String name(BluetoothDevice d) {
        String n = null;
        try { n = d.getAlias(); } catch (Throwable ignored) {}
        if (TextUtils.isEmpty(n)) n = d.getName();
        return TextUtils.isEmpty(n) ? d.getAddress() : n;
    }

    static boolean connected(BluetoothDevice d) {
        try { return (Boolean) BluetoothDevice.class.getMethod("isConnected").invoke(d); }
        catch (Throwable e) { return false; }
    }

    static int battery(BluetoothDevice d) {
        try { return (Integer) BluetoothDevice.class.getMethod("getBatteryLevel").invoke(d); }
        catch (Throwable e) { return -1; }
    }

    static String kind(BluetoothDevice d) {
        BluetoothClass c = d.getBluetoothClass();
        if (c == null) return "Device";
        int dev = c.getDeviceClass();
        switch (c.getMajorDeviceClass()) {
            case BluetoothClass.Device.Major.PERIPHERAL:
                if ((dev & 0x3C) == 0x0C) return "Remote";
                if ((dev & 0x3C) == 0x08) return "Game controller";
                if ((dev & 0xC0) == 0x40) return "Keyboard";
                if ((dev & 0xC0) == 0x80) return "Mouse";
                return "Remote or keyboard";
            case BluetoothClass.Device.Major.AUDIO_VIDEO:
                if (dev == BluetoothClass.Device.AUDIO_VIDEO_LOUDSPEAKER) return "Speaker";
                return "Headphones or speaker";
            case BluetoothClass.Device.Major.PHONE: return "Phone";
            case BluetoothClass.Device.Major.COMPUTER: return "Computer";
            default: return "Device";
        }
    }

    void fill() {
        BluetoothAdapter a = BluetoothAdapter.getDefaultAdapter();
        List<BluetoothDevice> ds = new ArrayList<>();
        boolean on = a != null && a.isEnabled();
        try { if (on) ds.addAll(a.getBondedDevices()); } catch (Throwable ignored) {}
        // connected first, then by name
        ds.sort((x, y) -> connected(x) != connected(y) ? (connected(x) ? -1 : 1) : name(x).compareToIgnoreCase(name(y)));
        int n = 0;
        StringBuilder sig = new StringBuilder(on ? "on" : "off");
        for (BluetoothDevice d : ds) { boolean c = connected(d); if (c) n++; sig.append(d.getAddress()).append(c).append(battery(d)); }
        state.setText(a == null ? "Not available" : !on ? "Off" : n == 0 ? "On" : "On · " + n + " connected");
        if (sig.toString().equals(shown)) return;   // nothing changed: keep focus where it is
        shown = sig.toString();
        View had = findFocus();
        String hadTag = had != null && had.getTag() instanceof String ? (String) had.getTag() : null;
        devices.removeAllViews();
        if (a != null && !on) {   // only ever turned ON from here
            devices.addView(section("Bluetooth is off"));
            TextView turn = pill("Turn on", true);
            turn.setOnClickListener(v -> { try { a.enable(); } catch (Throwable e) { launch("Bluetooth settings", settingsIntents()); } });
            devices.addView(turn, new LinearLayout.LayoutParams(-1, Ui.px(40)));
            return;
        }
        devices.addView(section(ds.isEmpty() ? "Paired devices" : "Paired devices · OK to connect or manage"));
        if (ds.isEmpty()) {
            TextView e = Ui.text(getContext(), Ui.serifItalic, 17, 0x99FFFFFF);
            e.setText("No paired devices yet. Choose Pair a new device.");
            e.setPadding(Ui.px(4), 0, 0, 0);
            devices.addView(e);
            return;
        }
        for (BluetoothDevice d : ds) {
            View r = row(d);
            devices.addView(r);
            if (d.getAddress().equals(hadTag)) h.post(r::requestFocus);
        }
    }

    TextView section(String s) {
        TextView l = Ui.label(getContext(), s, 0x99FFFFFF);
        l.setPadding(Ui.px(4), Ui.px(22), 0, Ui.px(10));
        return l;
    }

    TextView pill(String label, boolean primary) {
        TextView b = Ui.text(getContext(), Ui.sansMedium, 14, primary ? 0xFF18181C : 0xF2FFFFFF);
        b.setText(label);
        b.setGravity(Gravity.CENTER);
        b.setSingleLine(true);
        b.setFocusable(true);
        b.setPadding(Ui.px(14), 0, Ui.px(14), 0);
        GradientDrawable g = Ui.round(primary ? 0xFFF7F4E4 : 0x2EFFFFFF, 19);
        b.setBackground(g);
        if (!primary) b.setOnFocusChangeListener((v, f) -> g.setColor(f ? 0x47FFFFFF : 0x2EFFFFFF));
        Selector.shape(b, Ui.px(19), false);
        return b;
    }

    View row(BluetoothDevice d) {
        Context c = getContext();
        boolean on = connected(d);
        int bat = battery(d);
        LinearLayout r = new LinearLayout(c);
        r.setTag(d.getAddress());
        r.setFocusable(true);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(Ui.px(14), Ui.px(12), Ui.px(14), Ui.px(12));
        GradientDrawable bg = Ui.round(on ? 0x33FFFFFF : 0x1FFFFFFF, 22);
        r.setBackground(bg);
        Selector.shape(r, Ui.px(22), false);
        r.setOnFocusChangeListener((v, f) -> bg.setColor(f ? 0x47FFFFFF : on ? 0x33FFFFFF : 0x1FFFFFFF));
        FrameLayout ic = new FrameLayout(c);
        GradientDrawable ib = new GradientDrawable();
        ib.setShape(GradientDrawable.OVAL);
        ib.setColor(on ? 0xE63B82F6 : 0x33FFFFFF);
        ic.setBackground(ib);
        ic.addView(new Icon(c, Icon.BLUETOOTH), new FrameLayout.LayoutParams(Ui.px(20), Ui.px(20), Gravity.CENTER));
        r.addView(ic, new LinearLayout.LayoutParams(Ui.px(40), Ui.px(40)));
        LinearLayout tx = new LinearLayout(c);
        tx.setOrientation(LinearLayout.VERTICAL);
        tx.setPadding(Ui.px(14), 0, 0, 0);
        TextView nm = Ui.text(c, Ui.sansMedium, 15, 0xFFFFFFFF);
        nm.setText(name(d));
        nm.setSingleLine(true);
        nm.setEllipsize(TextUtils.TruncateAt.END);
        tx.addView(nm);
        TextView sub = Ui.text(c, Ui.sans, 13, 0xB3FFFFFF);
        sub.setText(kind(d) + " · " + (on ? "Connected" : "Not connected"));
        sub.setSingleLine(true);
        sub.setPadding(0, Ui.px(2), 0, 0);
        tx.addView(sub);
        r.addView(tx, new LinearLayout.LayoutParams(0, -2, 1));
        if (bat >= 0) {
            TextView b = Ui.text(c, Ui.sansMedium, 13, bat <= 15 ? 0xFFFF8A7A : 0xCCFFFFFF);
            b.setText(bat + "%");
            b.setPadding(Ui.px(8), 0, 0, 0);
            r.addView(b);
        }
        if (on) {
            View dot = new View(c);
            dot.setBackground(Ui.round(0xFF34D399, 5));
            LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(Ui.px(9), Ui.px(9));
            dl.leftMargin = Ui.px(10);
            r.addView(dot, dl);
        }
        r.setOnClickListener(v -> { if (on || !connect(d)) launch(name(d), deviceIntents(d)); });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = Ui.px(10);
        r.setLayoutParams(lp);
        return r;
    }

    /** Asks every Bluetooth profile the device might use to connect it. False if the TV won't let us. */
    boolean connect(BluetoothDevice d) {
        BluetoothAdapter a = BluetoothAdapter.getDefaultAdapter();
        if (a == null) return false;
        final boolean[] tried = {false};
        for (int profile : new int[] {BluetoothProfile.A2DP, BluetoothProfile.HEADSET, 4 /* input devices */}) {
            try {
                a.getProfileProxy(home, new BluetoothProfile.ServiceListener() {
                    public void onServiceConnected(int p, BluetoothProfile proxy) {
                        try {
                            Object ok = proxy.getClass().getMethod("connect", BluetoothDevice.class).invoke(proxy, d);
                            if (Boolean.TRUE.equals(ok)) h.post(() -> Toast.makeText(home, "Connecting " + name(d) + "…", Toast.LENGTH_SHORT).show());
                        } catch (Throwable ignored) {}
                        try { a.closeProfileProxy(p, proxy); } catch (Throwable ignored) {}
                    }
                    public void onServiceDisconnected(int p) {}
                }, profile);
                tried[0] = true;
            } catch (Throwable ignored) {}
        }
        if (!tried[0]) return false;
        // if nothing connected within a few seconds, open its page instead
        h.postDelayed(() -> { if (home.bluetooth == this && !connected(d)) launch(name(d), deviceIntents(d)); }, 6000);
        return true;
    }

    // ---------- open and close ----------

    static BluetoothPanel open(Home h, FrameLayout root, ViewGroup page) {
        if (h.quick != null) h.quick.close();
        BluetoothPanel q = new BluetoothPanel(h);
        page.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        root.addView(q, root.indexOfChild(h.selector), new LayoutParams(-1, -1));
        q.setAlpha(0f);
        q.animate().alpha(1f).setDuration(200).start();
        q.post(() -> { if (q.first != null) q.first.requestFocus(); h.selector.kick(); });
        h.bluetooth = q;
        h.quickPage = page;
        return q;
    }

    void close() {
        if (home.bluetooth != this) return;
        home.bluetooth = null;
        h.removeCallbacksAndMessages(null);
        home.quickPage.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        animate().alpha(0f).setDuration(140).withEndAction(() -> {
            ViewGroup p = (ViewGroup) getParent();
            if (p != null) p.removeView(this);
            home.selector.kick();
        }).start();
        if (home.tour != null) home.tour.resume();
        else if (home.btView != null) home.btView.requestFocus();
    }
}
