package tv.steroid.home;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.util.List;

/**
 * Notification centre: a glass panel from the bell at the top. Requests still waiting for your
 * approval come first (Approve or Reject right here, even if you missed the pop up), then every
 * Little Office notice, newest first. OK on a notice opens Little Office, holding OK clears it,
 * and Clear all empties the list. Back closes.
 */
final class NoticePanel extends FrameLayout {
    static final int PW = 460;
    final Home home;
    final LinearLayout list, waiting, earlier;
    final Handler h = new Handler(Looper.getMainLooper());
    View first;

    NoticePanel(Home hm) {
        super(hm);
        home = hm;
        setBackgroundColor(0x66000000);
        setClickable(true);
        FrameLayout panel = new FrameLayout(hm);
        Glass.apply(panel, 36, 0x99101014); // the same frosted glass as the Control centre
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
        TextView t = Ui.text(hm, Ui.serif, 30, 0xFFFFFFFF);
        t.setText("Notifications");
        t.setSingleLine(true);
        head.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
        TextView clear = pill("Clear all", false);
        clear.setOnClickListener(v -> { Notices.clear(home); fill(); });
        head.addView(clear, new LinearLayout.LayoutParams(-2, Ui.px(38)));
        list.addView(head);
        first = clear;

        waiting = new LinearLayout(hm);
        waiting.setOrientation(LinearLayout.VERTICAL);
        waiting.setClipChildren(false);
        list.addView(waiting, new LinearLayout.LayoutParams(-1, -2));
        earlier = new LinearLayout(hm);
        earlier.setOrientation(LinearLayout.VERTICAL);
        earlier.setClipChildren(false);
        list.addView(earlier, new LinearLayout.LayoutParams(-1, -2));
        fill();
        loadWaiting();
        Notices.readAll(hm);
    }

    TextView pill(String label, boolean primary) {
        TextView b = Ui.text(getContext(), Ui.sansMedium, 14, primary ? 0xFF18181C : 0xF2FFFFFF);
        b.setText(label);
        b.setGravity(Gravity.CENTER);
        b.setSingleLine(true);
        b.setFocusable(true);
        b.setPadding(Ui.px(18), 0, Ui.px(18), 0);
        GradientDrawable g = Ui.round(primary ? 0xFFF7F4E4 : 0x2EFFFFFF, 19);
        b.setBackground(g);
        if (!primary) b.setOnFocusChangeListener((v, f) -> g.setColor(f ? 0x47FFFFFF : 0x2EFFFFFF));
        Selector.shape(b, Ui.px(19), false);
        return b;
    }

    TextView section(String s) {
        TextView l = Ui.label(getContext(), s, 0x99FFFFFF);
        l.setPadding(Ui.px(4), Ui.px(22), 0, Ui.px(10));
        return l;
    }

    // ---------- waiting for approval (live from every paired computer) ----------

    void loadWaiting() {
        new Thread(() -> {
            List<Approvals.Request> l = Approvals.pending(home);
            h.post(() -> showWaiting(l));
        }).start();
    }

    void showWaiting(List<Approvals.Request> l) {
        waiting.removeAllViews();
        if (l.isEmpty()) return;
        waiting.addView(section("Waiting for your approval"));
        for (Approvals.Request q : l) waiting.addView(request(q));
    }

    View request(Approvals.Request q) {
        Context c = getContext();
        LinearLayout card = new LinearLayout(c);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(Ui.px(18), Ui.px(16), Ui.px(18), Ui.px(14));
        card.setBackground(Ui.round(0x2EFFFFFF, 24));
        card.setClipChildren(false);
        TextView who = Ui.text(c, Ui.sans, 13, 0xB3FFFFFF);
        who.setText("Claude Code · " + q.project + " · " + q.computer);
        who.setSingleLine(true);
        who.setEllipsize(TextUtils.TruncateAt.END);
        card.addView(who);
        TextView title = Ui.text(c, Ui.sansMedium, 17, 0xFFFFFFFF);
        title.setText(q.summary.isEmpty() ? "Use " + q.tool : q.summary);
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setPadding(0, Ui.px(6), 0, Ui.px(6));
        card.addView(title);
        if (!q.detail.isEmpty() && !q.summary.endsWith(q.detail)) {
            TextView d = Ui.text(c, Ui.mono, 12.5f, 0xB3FFFFFF);
            d.setText(q.detail);
            d.setMaxLines(3);
            d.setEllipsize(TextUtils.TruncateAt.END);
            d.setPadding(Ui.px(10), Ui.px(8), Ui.px(10), Ui.px(8));
            d.setBackground(Ui.round(0x1AFFFFFF, 10));
            card.addView(d);
        }
        LinearLayout btns = new LinearLayout(c);
        btns.setPadding(0, Ui.px(12), 0, 0);
        btns.setClipChildren(false);
        TextView no = pill("Reject", false), yes = pill("Approve", true);
        btns.addView(no, new LinearLayout.LayoutParams(0, Ui.px(40), 1));
        LinearLayout.LayoutParams yl = new LinearLayout.LayoutParams(0, Ui.px(40), 1);
        yl.leftMargin = Ui.px(10);
        btns.addView(yes, yl);
        card.addView(btns);
        View.OnClickListener act = v -> {
            boolean allow = v == yes;
            new Thread(() -> {
                boolean ok = Approvals.decide(home, q, allow);
                h.post(() -> {
                    Toast.makeText(c, ok ? (allow ? "Approved" : "Rejected") : "Couldn't reach " + q.computer + ". Answer it there.", Toast.LENGTH_SHORT).show();
                    loadWaiting();
                });
            }).start();
        };
        no.setOnClickListener(act);
        yes.setOnClickListener(act);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = Ui.px(12);
        card.setLayoutParams(lp);
        if (first != null && waiting.getChildCount() <= 1) h.post(no::requestFocus); // the first request is ready to answer
        return card;
    }

    // ---------- every notice ----------

    void fill() {
        earlier.removeAllViews();
        List<Notices.Item> all = Notices.list(home);
        earlier.addView(section(all.isEmpty() ? "Notices" : "Notices · hold OK on one to clear it"));
        if (all.isEmpty()) {
            TextView e = Ui.text(getContext(), Ui.serifItalic, 17, 0x99FFFFFF);
            e.setText("Nothing yet. When an agent needs you or finishes, it shows here.");
            e.setPadding(Ui.px(4), 0, 0, 0);
            earlier.addView(e);
            return;
        }
        for (Notices.Item x : all) earlier.addView(row(x));
    }

    View row(Notices.Item x) {
        Context c = getContext();
        LinearLayout r = new LinearLayout(c);
        r.setFocusable(true);
        r.setPadding(Ui.px(14), Ui.px(12), Ui.px(14), Ui.px(12));
        GradientDrawable bg = Ui.round(x.read ? 0x1FFFFFFF : 0x33FFFFFF, 22);
        r.setBackground(bg);
        Selector.shape(r, Ui.px(22), false);
        r.setOnFocusChangeListener((v, f) -> bg.setColor(f ? 0x47FFFFFF : x.read ? 0x1FFFFFFF : 0x33FFFFFF));
        FrameLayout ic = new FrameLayout(c);
        GradientDrawable ib = new GradientDrawable();
        ib.setShape(GradientDrawable.OVAL);
        ib.setColor(Ui.withAlpha(OfficePanel.accentFor(x.agent), 0xE6));
        ic.setBackground(ib);
        ic.addView(new Icon(c, OfficePanel.iconFor(x.agent)), new FrameLayout.LayoutParams(Ui.px(20), Ui.px(20), Gravity.CENTER));
        r.addView(ic, new LinearLayout.LayoutParams(Ui.px(40), Ui.px(40)));
        LinearLayout tx = new LinearLayout(c);
        tx.setOrientation(LinearLayout.VERTICAL);
        tx.setPadding(Ui.px(14), 0, 0, 0);
        LinearLayout top = new LinearLayout(c);
        TextView p = Ui.text(c, Ui.sansMedium, 15, 0xFFFFFFFF);
        p.setText(x.project + (x.kind.equals("needs_you") ? " needs you" : " finished"));
        p.setSingleLine(true);
        p.setEllipsize(TextUtils.TruncateAt.END);
        top.addView(p, new LinearLayout.LayoutParams(0, -2, 1));
        TextView when = Ui.text(c, Ui.sans, 12, 0x99FFFFFF);
        when.setText(DateUtils.getRelativeTimeSpanString(x.at, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS,
            DateUtils.FORMAT_ABBREV_RELATIVE));
        when.setPadding(Ui.px(8), 0, 0, 0);
        top.addView(when);
        tx.addView(top);
        TextView body = Ui.text(c, Ui.sans, 13.5f, 0xCCFFFFFF);
        body.setText(x.text + (x.computer.isEmpty() ? "" : " · " + x.computer));
        body.setMaxLines(2);
        body.setEllipsize(TextUtils.TruncateAt.END);
        body.setPadding(0, Ui.px(3), 0, 0);
        tx.addView(body);
        r.addView(tx, new LinearLayout.LayoutParams(0, -2, 1));
        if (x.kind.equals("needs_you")) {
            View dot = new View(c);
            dot.setBackground(Ui.round(Office.stateColor("needs_you"), 5));
            LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(Ui.px(9), Ui.px(9));
            dl.leftMargin = Ui.px(8);
            r.addView(dot, dl);
        }
        r.setOnClickListener(v -> { close(); home.startActivity(new Intent(home, OfficePanel.class)); });
        r.setOnLongClickListener(v -> { Notices.remove(home, x); fill(); return true; });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = Ui.px(10);
        r.setLayoutParams(lp);
        return r;
    }

    // ---------- open and close ----------

    static NoticePanel open(Home h, FrameLayout root, ViewGroup page) {
        NoticePanel q = new NoticePanel(h);
        page.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        root.addView(q, root.indexOfChild(h.selector), new LayoutParams(-1, -1));
        q.setAlpha(0f);
        q.animate().alpha(1f).setDuration(200).start();
        q.post(() -> { if (q.first != null) q.first.requestFocus(); h.selector.kick(); });
        h.notices = q;
        h.quickPage = page;
        return q;
    }

    void close() {
        if (home.notices != this) return;
        home.notices = null;
        h.removeCallbacksAndMessages(null);
        home.quickPage.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        animate().alpha(0f).setDuration(140).withEndAction(() -> {
            ViewGroup p = (ViewGroup) getParent();
            if (p != null) p.removeView(this);
            home.selector.kick();
        }).start();
        if (home.tour != null) home.tour.resume(); // back to the tour if it sent you here
        else if (home.bellView != null) home.bellView.requestFocus();
        home.updateBell();
    }
}
