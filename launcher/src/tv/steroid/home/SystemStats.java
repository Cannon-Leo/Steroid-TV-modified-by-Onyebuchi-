package tv.steroid.home;

import android.app.ActivityManager;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.net.TrafficStats;
import android.os.Environment;
import android.os.Handler;
import android.os.StatFs;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

/**
 * Live system glance: RAM, storage, CPU clock and network speed, each with its icon.
 * Updates every 3 seconds, only while the home screen is showing. Android does not let apps read
 * CPU load, so CPU shows the current clock speed instead (it rises when the stick is busy).
 */
class SystemStats extends LinearLayout {
    final Handler h = new Handler();
    final TextView ram, disk, cpu, temp, net;
    long lastRx = -1, lastTx, lastAt;

    /** Same height as the music cards beside it, so the row lines up. */
    @Override protected void onMeasure(int w, int h) {
        super.onMeasure(w, MeasureSpec.makeMeasureSpec(Ui.px(NowPlaying.H), MeasureSpec.EXACTLY));
    }

    SystemStats(Context c) {
        super(c);
        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setPadding(Ui.px(14), Ui.px(10), Ui.px(16), Ui.px(10));
        Glass.apply(this, 18, 0x99101014); // frosted glass
        ram = row(Icon.RAM, "RAM");
        disk = row(Icon.STORAGE, "Storage");
        cpu = row(Icon.CPU, "CPU");
        temp = null; // Android blocks apps from reading the temperature sensor on this stick, so no row
        net = row(Icon.NET, "Network");
    }

    TextView row(int icon, String name) {
        LinearLayout r = new LinearLayout(getContext());
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, Ui.px(3), 0, Ui.px(3));
        Icon i = new Icon(getContext(), icon);
        i.color(0xCCFFFFFF);
        r.addView(i, new LayoutParams(Ui.px(15), Ui.px(15)));
        TextView n = Ui.text(getContext(), Ui.mono, 10.5f, 0x99FFFFFF);
        n.setLetterSpacing(0.08f);
        n.setText(name.toUpperCase());
        n.setPadding(Ui.px(9), 0, 0, 0);
        r.addView(n, new LayoutParams(Ui.px(84), -2));
        TextView v = Ui.text(getContext(), Ui.mono, 11, 0xF2FFFFFF);
        v.setSingleLine(true);
        r.addView(v, new LayoutParams(Ui.px(150), -2));
        addView(r);
        return v;
    }

    final Runnable tick = new Runnable() { public void run() { update(); h.postDelayed(this, 3000); } };
    void start() { h.removeCallbacks(tick); tick.run(); }
    void stop() { h.removeCallbacks(tick); }

    void update() {
        try {
            ActivityManager am = (ActivityManager) getContext().getSystemService(Context.ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(mi);
            ram.setText(gb(mi.totalMem - mi.availMem) + " / " + gb(mi.totalMem) + " GB");
        } catch (Exception e) { ram.setText("n/a"); }
        try {
            StatFs fs = new StatFs(Environment.getDataDirectory().getPath());
            long total = fs.getTotalBytes(), free = fs.getAvailableBytes();
            disk.setText(gb(total - free) + " / " + gb(total) + " GB");
        } catch (Exception e) { disk.setText("n/a"); }
        long maxKhz = 0;
        int cores = Runtime.getRuntime().availableProcessors();
        for (int i = 0; i < cores; i++) maxKhz = Math.max(maxKhz, readLong("/sys/devices/system/cpu/cpu" + i + "/cpufreq/scaling_cur_freq"));
        cpu.setText(maxKhz > 0 ? String.format("%.1f GHz · %d cores", maxKhz / 1e6, cores) : cores + " cores");
        long rx = TrafficStats.getTotalRxBytes(), tx = TrafficStats.getTotalTxBytes(), now = System.currentTimeMillis();
        if (lastRx >= 0 && now > lastAt) {
            double s = (now - lastAt) / 1000.0;
            net.setText("↓ " + rate((rx - lastRx) / s) + "  ↑ " + rate((tx - lastTx) / s));
        } else net.setText("measuring…");
        lastRx = rx; lastTx = tx; lastAt = now;
    }

    static String gb(long b) { return String.format("%.1f", b / 1073741824.0); }

    static String rate(double bps) {
        return bps >= 1048576 ? String.format("%.1f MB/s", bps / 1048576) : String.format("%.0f KB/s", bps / 1024);
    }

    static long readLong(String path) {
        File f = new File(path);
        if (!f.canRead()) return 0;
        try (BufferedReader r = new BufferedReader(new FileReader(f))) {
            return Long.parseLong(r.readLine().trim());
        } catch (Exception e) { return 0; }
    }
}
