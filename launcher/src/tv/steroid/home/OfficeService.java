package tv.steroid.home;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Polls Little Office in the background and shows one small, temporary notice when an agent needs
 * you or finishes. Events are grouped (a short window, then a cool-down) so an evening isn't
 * interrupted repeatedly. OK on the notice opens the glance; any other key dismisses it.
 */
public class OfficeService extends Service {
    static final long POLL_MS = 20_000, GROUP_MS = 30_000, COOLDOWN_MS = 5 * 60_000, SHOW_MS = 9_000;

    final Handler h = new Handler(Looper.getMainLooper());
    SharedPreferences prefs;
    final List<Office.Event> pending = new ArrayList<>();
    long pendingSince, lastToast;
    View toast;

    static void start(Context c) {
        try { c.startForegroundService(new Intent(c, OfficeService.class)); } catch (Exception ignored) {}
    }

    @Override public int onStartCommand(Intent i, int flags, int id) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm.getNotificationChannel("office") == null)
            nm.createNotificationChannel(new NotificationChannel("office", "Little Office", NotificationManager.IMPORTANCE_MIN));
        startForeground(2, new Notification.Builder(this, "office").setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentTitle("Little Office").build());
        prefs = getSharedPreferences("office", MODE_PRIVATE);
        h.removeCallbacks(tick);
        h.post(tick);
        ah.removeCallbacks(approvalTick);
        ah.postDelayed(approvalTick, 3000);
        return START_STICKY;
    }

    final Runnable tick = new Runnable() { public void run() {
        new Thread(() -> {
            boolean ok = Office.poll(OfficeService.this);
            h.post(() -> { if (ok) collect(); maybeShow(); });
        }).start();
        h.postDelayed(this, POLL_MS);
    }};

    void collect() {
        long seen = prefs.getLong("seenEvent", -1);
        if (seen < 0) { // first contact: everything so far is history, not news
            long max = 0;
            for (Office.Event e : Office.events) max = Math.max(max, e.id);
            prefs.edit().putLong("seenEvent", max).apply();
            return;
        }
        long max = seen;
        for (Office.Event e : Office.events) {
            if (e.id > seen) {
                if (pending.isEmpty()) pendingSince = System.currentTimeMillis();
                pending.add(e);
            }
            max = Math.max(max, e.id);
        }
        if (max != seen) prefs.edit().putLong("seenEvent", max).apply();
    }

    void maybeShow() {
        long now = System.currentTimeMillis();
        if (pending.isEmpty() || toast != null) return;
        if (now - pendingSince < GROUP_MS || now - lastToast < COOLDOWN_MS) return;
        if (!Settings.canDrawOverlays(this)) { pending.clear(); return; }
        show(new ArrayList<>(pending));
        pending.clear();
        lastToast = now;
    }

    void show(List<Office.Event> evs) {
        boolean anyNeeds = false;
        LinkedHashSet<String> projects = new LinkedHashSet<>();
        for (Office.Event e : evs) { anyNeeds |= e.kind.equals("needs_you"); projects.add(e.project); }
        String headline;
        if (evs.size() == 1) {
            Office.Event e = evs.get(0);
            headline = e.project + (e.kind.equals("needs_you") ? " needs you" : " finished");
        } else {
            List<String> p = new ArrayList<>(projects);
            headline = evs.size() + " updates · " + p.get(0) + (p.size() > 1 ? ", " + p.get(1) : "") + (p.size() > 2 ? " +" + (p.size() - 2) : "");
        }

        Ui.init(this);
        FrameLayout frame = new FrameLayout(this) {
            @Override public boolean dispatchKeyEvent(KeyEvent e) {
                if (e.getAction() != KeyEvent.ACTION_UP) return true;
                int k = e.getKeyCode();
                if (k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER || k == KeyEvent.KEYCODE_BUTTON_A)
                    startActivity(new Intent(OfficeService.this, OfficePanel.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                hide();
                return true;
            }
        };
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(Ui.px(18), Ui.px(12), Ui.px(22), Ui.px(13));
        GradientDrawable bg = Ui.round(0xE6101014, 16);
        bg.setStroke(Ui.px(1), anyNeeds ? 0x99FFB454 : 0x4DFFFFFF);
        card.setBackground(bg);
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        View dot = new View(this);
        dot.setBackground(Ui.round(Office.stateColor(anyNeeds ? "needs_you" : "finished"), 4));
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(Ui.px(8), Ui.px(8));
        dl.rightMargin = Ui.px(10);
        top.addView(dot, dl);
        top.addView(Ui.label(this, "Little Office", 0x99FFFFFF));
        card.addView(top);
        TextView title = Ui.text(this, Ui.serif, 19, 0xFFFFFFFF);
        title.setText(headline);
        title.setPadding(0, Ui.px(4), 0, Ui.px(2));
        card.addView(title);
        TextView sub = Ui.text(this, Ui.sans, 11.5f, 0x99FFFFFF);
        sub.setText("OK to see why · any other key to dismiss");
        card.addView(sub);
        frame.addView(card);

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(-2, -2,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.END;
        lp.x = Ui.px(36);
        lp.y = Ui.px(30);
        try {
            ((WindowManager) getSystemService(WINDOW_SERVICE)).addView(frame, lp);
            toast = frame;
            frame.setAlpha(0f);
            frame.animate().alpha(1f).setDuration(300).start();
            h.postDelayed(this::hide, SHOW_MS);
        } catch (Exception ignored) { toast = null; }
    }

    void hide() {
        if (toast == null) return;
        View v = toast;
        toast = null;
        h.removeCallbacksAndMessages(null);
        h.postDelayed(tick, POLL_MS);
        try { ((WindowManager) getSystemService(WINDOW_SERVICE)).removeView(v); } catch (Exception ignored) {}
    }

    // ---------- Approvals: answer Claude Code permission requests from the remote ----------

    final Handler ah = new Handler(Looper.getMainLooper()); // its own handler: hide() above clears h
    final java.util.Set<String> handled = new java.util.HashSet<>();
    Approvals.Request showing;
    View prompt;
    volatile boolean checking;

    final Runnable approvalTick = new Runnable() { public void run() {
        boolean paired = Approvals.anyPaired(OfficeService.this);
        if (paired && !checking) {
            checking = true;
            new Thread(() -> {
                List<Approvals.Request> l = Approvals.pending(OfficeService.this);
                ah.post(() -> { checking = false; onRequests(l); });
            }).start();
        }
        ah.postDelayed(this, paired ? 4000 : 20000);
    }};

    void onRequests(List<Approvals.Request> l) {
        if (showing != null) {
            boolean still = false;
            for (Approvals.Request q : l) still |= q.id.equals(showing.id);
            if (!still) closePrompt(); // answered at the computer, or timed out there
            else return;
        }
        for (Approvals.Request q : l) if (!handled.contains(q.id)) { showPrompt(q, l.size()); return; }
    }

    void showPrompt(Approvals.Request q, int waiting) {
        if (!Settings.canDrawOverlays(this)) return;
        Ui.init(this);
        showing = q;
        handled.add(q.id);
        final boolean[] approve = {false}; // Reject is selected first: a stray OK never approves
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(Ui.px(28), Ui.px(22), Ui.px(28), Ui.px(22));
        GradientDrawable bg = Ui.round(0xF2121216, 22);
        bg.setStroke(Ui.px(1), 0x80E8915A);
        card.setBackground(bg);
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        Icon ic = new Icon(this, Icon.CLAUDE);
        ic.color(0xFFE8915A);
        top.addView(ic, new LinearLayout.LayoutParams(Ui.px(20), Ui.px(20)));
        TextView who = Ui.label(this, "Claude Code asks · " + q.project + " · " + q.computer, 0xCCFFFFFF);
        who.setPadding(Ui.px(10), 0, 0, 0);
        top.addView(who, new LinearLayout.LayoutParams(0, -2, 1));
        if (waiting > 1) top.addView(Ui.label(this, waiting + " waiting", 0xFFFFB454));
        card.addView(top);
        TextView title = Ui.text(this, Ui.serif, 24, 0xFFFFFFFF);
        title.setText(q.summary.isEmpty() ? "Use " + q.tool : q.summary);
        title.setMaxLines(2);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        title.setPadding(0, Ui.px(12), 0, Ui.px(6));
        card.addView(title);
        if (!q.detail.isEmpty() && !q.summary.endsWith(q.detail)) {
            TextView d = Ui.text(this, Ui.mono, 13, 0xB3FFFFFF);
            d.setText(q.detail);
            d.setMaxLines(3);
            d.setEllipsize(android.text.TextUtils.TruncateAt.END);
            d.setPadding(Ui.px(12), Ui.px(8), Ui.px(12), Ui.px(8));
            d.setBackground(Ui.round(0x14FFFFFF, 10));
            card.addView(d);
        }
        LinearLayout btns = new LinearLayout(this);
        btns.setPadding(0, Ui.px(16), 0, 0);
        TextView no = button("Reject"), yes = button("Approve");
        btns.addView(no, new LinearLayout.LayoutParams(0, Ui.px(48), 1));
        LinearLayout.LayoutParams yl = new LinearLayout.LayoutParams(0, Ui.px(48), 1);
        yl.leftMargin = Ui.px(12);
        btns.addView(yes, yl);
        card.addView(btns);
        TextView hint = Ui.text(this, Ui.sans, 12.5f, 0x8CFFFFFF);
        hint.setText("Left and right to choose, OK to send · Back leaves it for the computer");
        hint.setPadding(0, Ui.px(10), 0, 0);
        card.addView(hint);
        Runnable paint = () -> {
            ((GradientDrawable) no.getBackground()).setColor(approve[0] ? 0x1AFFFFFF : 0xFFFF6B5A);
            ((GradientDrawable) yes.getBackground()).setColor(approve[0] ? 0xFF6FD49B : 0x1AFFFFFF);
            no.setTextColor(approve[0] ? 0xCCFFFFFF : 0xFF140A08);
            yes.setTextColor(approve[0] ? 0xFF08140C : 0xCCFFFFFF);
        };
        paint.run();
        FrameLayout frame = new FrameLayout(this) {
            @Override public boolean dispatchKeyEvent(KeyEvent e) {
                int k = e.getKeyCode();
                if (e.getAction() != KeyEvent.ACTION_DOWN) return true;
                if (k == KeyEvent.KEYCODE_DPAD_LEFT || k == KeyEvent.KEYCODE_DPAD_RIGHT) { approve[0] = k == KeyEvent.KEYCODE_DPAD_RIGHT; paint.run(); }
                else if (k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER || k == KeyEvent.KEYCODE_BUTTON_A) {
                    boolean a = approve[0];
                    hint.setText(a ? "Approving…" : "Rejecting…");
                    new Thread(() -> {
                        boolean ok = Approvals.decide(OfficeService.this, q, a);
                        ah.post(() -> {
                            if (!ok) { hint.setText("Couldn't reach " + q.computer + ". Answer it there."); ah.postDelayed(OfficeService.this::closePrompt, 3000); }
                            else closePrompt();
                        });
                    }).start();
                } else if (k == KeyEvent.KEYCODE_BACK) closePrompt();
                return true;
            }
        };
        FrameLayout.LayoutParams cl = new FrameLayout.LayoutParams(Ui.px(560), -2);
        frame.addView(card, cl);
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(-2, -2,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        lp.y = Ui.px(40);
        try {
            ((WindowManager) getSystemService(WINDOW_SERVICE)).addView(frame, lp);
            prompt = frame;
            frame.setAlpha(0f);
            frame.animate().alpha(1f).setDuration(250).start();
        } catch (Exception e) { prompt = null; showing = null; }
    }

    TextView button(String label) {
        TextView b = Ui.text(this, Ui.sansMedium, 16, 0xFFFFFFFF);
        b.setText(label);
        b.setGravity(Gravity.CENTER);
        b.setBackground(Ui.round(0x1AFFFFFF, 14));
        return b;
    }

    void closePrompt() {
        showing = null;
        if (prompt == null) return;
        View v = prompt;
        prompt = null;
        try { ((WindowManager) getSystemService(WINDOW_SERVICE)).removeView(v); } catch (Exception ignored) {}
    }

    @Override public void onDestroy() { h.removeCallbacksAndMessages(null); ah.removeCallbacksAndMessages(null); hide(); closePrompt(); super.onDestroy(); }
    @Override public IBinder onBind(Intent i) { return null; }
}
