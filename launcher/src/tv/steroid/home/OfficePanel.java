package tv.steroid.home;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Little Office, full screen. Top: which computers are answering (Mac, Windows PC...). Then a large
 * spotlight on the agent that matters most right now (needs you, else working, else the latest),
 * with its icon, status and what it is doing. Then one card per agent (Claude, Codex, Gemini), the
 * busy ones lit up. At the bottom: what needs you, and recent activity side by side. OK on the spotlight,
 * an agent or a row opens a side sheet: what's waiting (approve or reject there) and its notices.
 */
public class OfficePanel extends Activity {
    final Handler h = new Handler();
    Ambient ambient;
    LinearLayout body;
    LinearLayout computersRow;
    ObjectAnimator pulse;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.init(this);
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        FrameLayout root = new FrameLayout(this);
        ambient = new Ambient(this);
        ambient.tint(0xFF6FD49B);
        root.addView(ambient);
        ScrollView scroll = new Rails.Page(this, Ui.px(56));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setClipChildren(false);
        col.setPadding(Ui.px(56), Ui.px(36), Ui.px(56), Ui.px(56));

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.BOTTOM);
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.addView(Ui.label(this, "Little Office", 0x99FFFFFF));
        TextView t = Ui.text(this, Ui.serif, 44, 0xFFFFFFFF);
        t.setText("At the desk");
        t.setPadding(0, Ui.px(6), 0, 0);
        titles.addView(t);
        head.addView(titles, new LinearLayout.LayoutParams(0, -2, 1f));
        computersRow = new LinearLayout(this);
        computersRow.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        head.addView(computersRow);
        // replay the guided tour any time
        FrameLayout help = new FrameLayout(this);
        help.setFocusable(true);
        help.setContentDescription("Tour");
        GradientDrawable hb = new GradientDrawable();
        hb.setShape(GradientDrawable.OVAL);
        hb.setColor(0x26FFFFFF);
        help.setBackground(hb);
        help.addView(new Icon(this, Icon.COMPASS), new FrameLayout.LayoutParams(Ui.px(20), Ui.px(20), Gravity.CENTER));
        Selector.circle(help, false);
        help.setOnClickListener(v -> startTour());
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(Ui.px(40), Ui.px(40));
        hl.leftMargin = Ui.px(12);
        head.addView(help, hl);
        col.addView(head);

        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setClipChildren(false);
        col.addView(body);
        scroll.addView(col);
        root.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
        selector = Selector.attach(this, root);
        setContentView(root);
        render();
        rootView = root;
        page = scroll;
        if (!Prefs.on(this, "officeTourDone", false)) h.postDelayed(this::startTour, 1200); // first visit only
    }

    // ---------- Guided tour ----------
    FrameLayout rootView;
    ScrollView page;
    Selector selector;
    Tour tour;

    void startTour() {
        if (tour != null || isFinishing()) return;
        java.util.List<Tour.Step> st = new java.util.ArrayList<>();
        st.add(new Tour.Step(null, "Little Office", "Your agents, at a glance",
            "Little Office shows what Claude Code, Codex and Gemini (Antigravity) are doing on your computers. It is read only: only project names, a state and one short sentence come to the TV, and only over your home network."));
        st.add(new Tour.Step(() -> computersRow, "Computers", "Which computers are answering",
            "Every Mac or Windows PC running Little Office appears here with a green dot. With two or more, these become a switcher: pick one to see only its agents, or All computers."));
        st.add(new Tour.Step(() -> body.findViewWithTag("spot"), "Spotlight", "What matters right now",
            "The agent that needs you comes first, then whatever is working, then the latest finish. Amber means it needs you, green means it's working."));
        st.add(new Tour.Step(() -> body.findViewWithTag("t_agents"), "Agents", "One card per agent",
            "How many sessions are working or waiting, or the last thing it did. OK opens its notices and anything waiting for you. A faded card means that agent isn't installed on these computers."));
        st.add(new Tour.Step(() -> body.findViewWithTag("t_lists"), "Lists", "Needs you, and recent activity",
            "Left: sessions waiting on you. Right: the last few hours, newest first. OK on any of them opens its request (approve or reject right there) and its notices."));
        st.add(new Tour.Step(() -> body.findViewWithTag("t_approve"), "Approvals", "Answer Claude Code from the sofa",
            "Pair once with the code shown on your computer. When Claude Code asks permission, a card appears over whatever you're watching: left and right choose, OK sends, Back leaves it for the computer. Reject is picked first, and risky commands always stay at the keyboard."));
        st.add(new Tour.Step(null, "Notices", "It finds you",
            "When an agent needs you or finishes, a small notice slides in at the top right. OK opens this screen; any other key dismisses it. The compass at the top replays this tour."));
        tour = Tour.start(this, rootView, selector, page, st, () -> {
            tour = null;
            Prefs.put(this, "officeTourDone", true);
            h.postDelayed(() -> { View s = body.findViewWithTag("spot"); if (s != null) s.requestFocus(); }, 250);
        });
    }

    @Override public void onBackPressed() {
        if (tour != null) tour.finish();
        else if (sheet != null) closeSheet();
        else super.onBackPressed();
    }

    // ---------- Agent sheet: what an agent (or one project) is asking and has said ----------
    FrameLayout sheet;
    LinearLayout sheetList;
    TextView sheetSub;
    View sheetFrom;
    String sAgent, sProject, sComputer;
    List<Approvals.Request> waiting = new ArrayList<>();

    static String agentName(String a) { return a.equals("Claude") ? "Claude Code" : a.equals("Gemini") ? "Gemini (Antigravity)" : a; }

    /** OK on an agent card, the spotlight, or a row: a panel slides in from the right with that agent's
     *  (or that project's) waiting approvals first, Approve or Reject right there, then its notices. */
    void openSheet(String agent, String project, String computer, View from) {
        if (sheet != null || tour != null) return;
        sAgent = agent; sProject = project; sComputer = computer; sheetFrom = from;
        page.setDescendantFocusability(android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        FrameLayout dim = new FrameLayout(this);
        dim.setBackgroundColor(0x8C000000);
        dim.setClickable(true);
        LinearLayout p = new LinearLayout(this);
        p.setOrientation(LinearLayout.VERTICAL);
        p.setPadding(Ui.px(30), Ui.px(30), Ui.px(30), Ui.px(20));
        Glass.apply(p, 0, 0xF0101014);
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        Icon ic = new Icon(this, iconFor(agent));
        ic.setPadding(Ui.px(9), Ui.px(9), Ui.px(9), Ui.px(9));
        GradientDrawable ig = new GradientDrawable();
        ig.setShape(GradientDrawable.OVAL);
        ig.setColor(Ui.withAlpha(accentFor(agent), 0xE6));
        ic.setBackground(ig);
        ic.color(0xFFFFFFFF);
        top.addView(ic, new LinearLayout.LayoutParams(Ui.px(44), Ui.px(44)));
        LinearLayout tt = new LinearLayout(this);
        tt.setOrientation(LinearLayout.VERTICAL);
        tt.setPadding(Ui.px(14), 0, 0, 0);
        TextView name = Ui.text(this, Ui.serif, 26, 0xFFFFFFFF);
        name.setText(project == null ? agentName(agent) : project);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        tt.addView(name);
        sheetSub = Ui.text(this, Ui.sans, 12.5f, 0xB3FFFFFF);
        tt.addView(sheetSub);
        top.addView(tt, new LinearLayout.LayoutParams(0, -2, 1f));
        p.addView(top);
        sheetList = new LinearLayout(this);
        sheetList.setOrientation(LinearLayout.VERTICAL);
        sheetList.setClipChildren(false);
        sheetList.setPadding(Ui.px(4), Ui.px(18), Ui.px(4), Ui.px(18));
        ScrollView sv = new Rails.Page(this, Ui.px(40));
        sv.setClipChildren(false);
        sv.addView(sheetList);
        p.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        TextView hint = Ui.text(this, Ui.sans, 12, 0x8CFFFFFF);
        hint.setText("OK on a notice opens it in full  ·  Back closes");
        p.addView(hint);
        dim.addView(p, new FrameLayout.LayoutParams(Ui.px(560), -1, Gravity.END));
        sheet = dim;
        rootView.addView(dim, new FrameLayout.LayoutParams(-1, -1));
        p.setTranslationX(Ui.px(560));
        p.animate().translationX(0).setDuration(240).setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
        dim.setAlpha(0f);
        dim.animate().alpha(1f).setDuration(200).start();
        fillSheet();
        loadWaiting();
    }

    void closeSheet() {
        if (sheet == null) return;
        View s = sheet;
        sheet = null;
        s.animate().alpha(0f).setDuration(160).withEndAction(() -> rootView.removeView(s)).start();
        page.setDescendantFocusability(android.view.ViewGroup.FOCUS_AFTER_DESCENDANTS);
        View back = sheetFrom != null && sheetFrom.isAttachedToWindow() ? sheetFrom : body.findViewWithTag("spot");
        if (back != null) back.requestFocus();
        render();
    }

    boolean matches(String agent, String project, String computer) {
        if (!sAgent.equals(agent)) return false;
        if (sProject != null && !sProject.equals(project)) return false;
        return sComputer == null || computer == null || computer.isEmpty() || sComputer.equals(computer);
    }

    /** Approvals come from Claude Code: fetched off the main thread, signed and checked (Approvals.pending). */
    void loadWaiting() {
        if (!sAgent.equals("Claude")) return;
        new Thread(() -> {
            List<Approvals.Request> all = Approvals.pending(this), mine = new ArrayList<>();
            for (Approvals.Request q : all) if (matches("Claude", q.project, q.computer)) mine.add(q);
            h.post(() -> { if (sheet != null) { waiting = mine; fillSheet(); } });
        }).start();
    }

    void refreshSheet() { loadWaiting(); fillSheet(); }

    void fillSheet() {
        if (sheet == null) return;
        View had = getCurrentFocus();
        Object focusTag = had == null ? null : had.getTag();
        sheetList.removeAllViews();
        List<Notices.Item> ns = new ArrayList<>();
        for (Notices.Item x : Notices.list(this)) if (matches(x.agent, x.project, x.computer)) ns.add(x);
        sheetSub.setText((waiting.isEmpty() ? "" : waiting.size() + " waiting for you  ·  ")
            + (ns.isEmpty() ? "No notices" : ns.size() + (ns.size() == 1 ? " notice" : " notices"))
            + (sProject != null ? "  ·  " + agentName(sAgent) : ""));
        if (!waiting.isEmpty()) {
            sheetList.addView(Ui.label(this, "Waiting for you", 0xFFFFB454));
            for (Approvals.Request q : waiting) sheetList.addView(approvalCard(q));
        }
        if (!ns.isEmpty()) {
            TextView l = Ui.label(this, "Notices", 0x99FFFFFF);
            if (!waiting.isEmpty()) l.setPadding(0, Ui.px(18), 0, 0);
            sheetList.addView(l);
            for (Notices.Item x : ns) sheetList.addView(noticeRow(x));
        }
        if (waiting.isEmpty() && ns.isEmpty()) {
            TextView e = Ui.text(this, Ui.serifItalic, 20, 0xCCFFFFFF);
            e.setText("Nothing from " + (sProject != null ? sProject : agentName(sAgent)) + " yet.");
            e.setPadding(0, Ui.px(30), 0, Ui.px(6));
            sheetList.addView(e);
            TextView e2 = Ui.text(this, Ui.sans, 13, 0x99FFFFFF);
            e2.setText(sAgent.equals("Claude") ? "Requests to approve and notices show up here as they arrive."
                : "Notices show up here when it finishes or needs you. Approving from the TV is for Claude Code.");
            sheetList.addView(e2);
            View close = sheetButton("Close", 0x26FFFFFF, 0xFFFFFFFF);
            close.setTag("sh_close");
            close.setOnClickListener(v -> closeSheet());
            LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(Ui.px(140), Ui.px(44));
            cl.topMargin = Ui.px(22);
            sheetList.addView(close, cl);
        }
        View back = focusTag == null ? null : sheetList.findViewWithTag(focusTag);
        if (back == null) back = firstFocusable(sheetList);
        if (back != null) back.requestFocus();
    }

    static View firstFocusable(android.view.ViewGroup g) {
        for (int i = 0; i < g.getChildCount(); i++) {
            View c = g.getChildAt(i);
            if (c.isFocusable() && c.getVisibility() == View.VISIBLE) return c;
            if (c instanceof android.view.ViewGroup) { View f = firstFocusable((android.view.ViewGroup) c); if (f != null) return f; }
        }
        return null;
    }

    TextView sheetButton(String label, int bg, int fg) {
        TextView b = Ui.text(this, Ui.sansMedium, 15, fg);
        b.setText(label);
        b.setGravity(Gravity.CENTER);
        b.setFocusable(true);
        b.setBackground(Ui.round(bg, 14));
        Selector.shape(b, Ui.px(14), Ui.px(3));
        return b;
    }

    /** One request: what it wants, then Reject and Approve (Reject first: a stray OK never approves). */
    View approvalCard(Approvals.Request q) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(Ui.px(18), Ui.px(16), Ui.px(18), Ui.px(16));
        GradientDrawable bg = Ui.round(0x26FFB454, 18);
        bg.setStroke(Ui.px(1), 0x66FFB454);
        c.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = Ui.px(10);
        c.setLayoutParams(lp);
        c.addView(Ui.label(this, q.project + "  ·  " + q.computer, 0xCCFFFFFF));
        TextView t = Ui.text(this, Ui.serif, 19, 0xFFFFFFFF);
        t.setText(q.summary.isEmpty() ? "Use " + q.tool : q.summary);
        t.setPadding(0, Ui.px(8), 0, Ui.px(6));
        c.addView(t);
        if (!q.detail.isEmpty() && !q.summary.endsWith(q.detail)) {
            TextView d = Ui.text(this, Ui.mono, 12.5f, 0xCCFFFFFF);
            d.setText(q.detail);
            d.setMaxLines(4);
            d.setEllipsize(android.text.TextUtils.TruncateAt.END);
            d.setPadding(Ui.px(12), Ui.px(8), Ui.px(12), Ui.px(8));
            d.setBackground(Ui.round(0x1AFFFFFF, 10));
            c.addView(d);
        }
        LinearLayout btns = new LinearLayout(this);
        btns.setPadding(0, Ui.px(14), 0, 0);
        TextView no = sheetButton("Reject", 0x33FF6B5A, 0xFFFFFFFF), yes = sheetButton("Approve", 0x336FD49B, 0xFFFFFFFF);
        no.setTag("no_" + q.id);
        yes.setTag("yes_" + q.id);
        no.setOnFocusChangeListener((v, f) -> { ((GradientDrawable) no.getBackground()).setColor(f ? 0xFFFF6B5A : 0x33FF6B5A); no.setTextColor(f ? 0xFF140A08 : 0xFFFFFFFF); });
        yes.setOnFocusChangeListener((v, f) -> { ((GradientDrawable) yes.getBackground()).setColor(f ? 0xFF6FD49B : 0x336FD49B); yes.setTextColor(f ? 0xFF08140C : 0xFFFFFFFF); });
        TextView state = Ui.text(this, Ui.sans, 12.5f, 0xB3FFFFFF);
        View.OnClickListener send = v -> {
            boolean allow = v == yes;
            no.setEnabled(false); yes.setEnabled(false);
            state.setText(allow ? "Approving…" : "Rejecting…");
            new Thread(() -> {
                boolean ok = Approvals.decide(this, q, allow);
                h.post(() -> {
                    if (sheet == null) return;
                    if (ok) { waiting.remove(q); state.setText(allow ? "Approved" : "Rejected"); h.postDelayed(this::fillSheet, 700); }
                    else { state.setText("Couldn't reach " + q.computer + ". Answer it there."); no.setEnabled(true); yes.setEnabled(true); }
                });
            }).start();
        };
        no.setOnClickListener(send);
        yes.setOnClickListener(send);
        btns.addView(no, new LinearLayout.LayoutParams(0, Ui.px(46), 1));
        LinearLayout.LayoutParams yl = new LinearLayout.LayoutParams(0, Ui.px(46), 1);
        yl.leftMargin = Ui.px(12);
        btns.addView(yes, yl);
        c.addView(btns);
        state.setPadding(0, Ui.px(8), 0, 0);
        c.addView(state);
        return c;
    }

    /** One notice: what happened, where, when. OK opens it in full (and marks it read). */
    View noticeRow(Notices.Item x) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.VERTICAL);
        r.setFocusable(true);
        r.setTag("n_" + x.id + x.project);
        r.setPadding(Ui.px(16), Ui.px(13), Ui.px(16), Ui.px(13));
        GradientDrawable bg = Ui.round(x.read ? 0x14FFFFFF : 0x24FFFFFF, 16);
        r.setBackground(bg);
        Selector.shape(r, Ui.px(16), Ui.px(3));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = Ui.px(10);
        r.setLayoutParams(lp);
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        boolean needs = "needs_you".equals(x.kind) || "needs".equals(x.kind);
        View dot = new View(this);
        dot.setBackground(Ui.round(x.read ? 0x40FFFFFF : needs ? 0xFFFFB454 : 0xFF6FD49B, 4));
        top.addView(dot, new LinearLayout.LayoutParams(Ui.px(8), Ui.px(8)));
        TextView k = Ui.text(this, Ui.sansMedium, 13.5f, 0xFFFFFFFF);
        k.setText((needs ? "Needs you" : "Finished") + "  ·  " + x.project);
        k.setSingleLine(true);
        k.setEllipsize(android.text.TextUtils.TruncateAt.END);
        k.setPadding(Ui.px(10), 0, Ui.px(8), 0);
        top.addView(k, new LinearLayout.LayoutParams(0, -2, 1f));
        long m = (System.currentTimeMillis() - x.at) / 60000;
        top.addView(Ui.text(this, Ui.sans, 11.5f, 0x8CFFFFFF));
        ((TextView) top.getChildAt(2)).setText(m < 1 ? "now" : m < 60 ? m + " min" : m < 1440 ? (m / 60) + " h" : (m / 1440) + " d");
        r.addView(top);
        TextView t = Ui.text(this, Ui.sans, 13, 0xCCFFFFFF);
        t.setText(x.text.isEmpty() ? (x.computer.isEmpty() ? "" : x.computer) : x.text + (x.computer.isEmpty() ? "" : "  ·  " + x.computer));
        t.setMaxLines(2);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        t.setPadding(Ui.px(18), Ui.px(5), 0, 0);
        r.addView(t);
        r.setOnClickListener(v -> {
            boolean open = t.getMaxLines() > 2;
            t.setMaxLines(open ? 2 : 40);
            t.setEllipsize(open ? android.text.TextUtils.TruncateAt.END : null);
            if (!x.read) { x.read = true; Notices.save(this); bg.setColor(0x14FFFFFF); dot.setBackground(Ui.round(0x40FFFFFF, 4)); }
        });
        return r;
    }

    static int iconFor(String agent) {
        switch (agent) { case "Claude": return Icon.CLAUDE; case "Codex": return Icon.CODEX; default: return Icon.GEMINI; }
    }

    static int accentFor(String agent) {
        switch (agent) { case "Claude": return 0xFFE8915A; case "Codex": return 0xFF8FB8FF; default: return 0xFF7FD4C4; }
    }

    void render() {
        View had = getCurrentFocus();
        String focusKey = had == null ? null : (String) had.getTag();
        body.removeAllViews();
        if (pulse != null) { pulse.cancel(); pulse = null; }
        boolean live = Office.connected();

        // computers answering
        computersRow.removeAllViews();
        List<String> comps = Office.liveComputers();
        if (Office.device != null && !comps.contains(Office.device)) Office.device = null; // that computer went away
        if (comps.isEmpty()) computersRow.addView(chip(Icon.LAPTOP, live ? "Connected" : Office.lastSeen(this), 0x80FFFFFF));
        boolean pick = comps.size() > 1; // two or more computers: the chips switch between them
        if (pick) computersRow.addView(deviceChip(Icon.GRID, "All computers", null));
        for (String c : comps) {
            boolean pc = !c.toLowerCase().contains("mac");
            computersRow.addView(pick ? deviceChip(pc ? Icon.MONITOR : Icon.LAPTOP, c, c) : chip(pc ? Icon.MONITOR : Icon.LAPTOP, c, 0xFF6FD49B));
        }

        // spotlight: needs you, else working, else most recent
        Office.Session best = null;
        for (Office.Session s : Office.shown()) if (best == null || Office.rank(s) > Office.rank(best)) best = s;
        body.addView(spotlight(best, live));

        // one card per agent
        LinearLayout cards = new LinearLayout(this);
        cards.setClipChildren(false);
        List<String> ord = Office.order();
        for (int i = 0; i < ord.size(); i++) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.px(132), 1f);
            if (i > 0) lp.leftMargin = Ui.px(18);
            cards.addView(agentCard(ord.get(i), live), lp);
        }
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-1, -2);
        clp.topMargin = Ui.px(22);
        cards.setTag("t_agents");
        body.addView(cards, clp);

        // needs you | recent activity
        LinearLayout lists = new LinearLayout(this);
        lists.setClipChildren(false);
        LinearLayout left = column("Needs you"), right = column("Recent activity");
        int n1 = 0, n2 = 0;
        for (Office.Session s : Office.shown()) {
            if (live && s.state.equals("needs_you")) { left.addView(row(s, true)); n1++; }
            else if (n2 < 6) { right.addView(row(s, false)); n2++; }
        }
        if (n1 == 0) left.addView(quiet("Nothing is waiting on you."));
        if (n2 == 0) right.addView(quiet(live ? "No recent activity in the last 6 hours." : "Waiting for a computer to answer."));
        lists.addView(left, new LinearLayout.LayoutParams(0, -2, 1f));
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(0, -2, 1.4f);
        rlp.leftMargin = Ui.px(24);
        lists.addView(right, rlp);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(-1, -2);
        llp.topMargin = Ui.px(26);
        lists.setTag("t_lists");
        body.addView(lists, llp);

        // approvals from the remote (Claude Code): pair this TV with each computer
        LinearLayout ap = column("Approve from the TV · Claude Code");
        if (comps.isEmpty()) ap.addView(quiet("Pair once a computer answers."));
        for (String comp : comps) ap.addView(pairRow(comp));
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(-1, -2);
        alp.topMargin = Ui.px(26);
        ap.setTag("t_approve");
        body.addView(ap, alp);

        if (sheet != null) { refreshSheet(); return; }   // the sheet keeps focus; it refreshes itself
        View back = focusKey == null ? null : body.findViewWithTag(focusKey);
        if (back != null) back.requestFocus();
        else { View s = body.findViewWithTag("spot"); if (s != null) s.requestFocus(); }
    }

    /** A computer you can pick: OK shows only that computer's agents and sessions. */
    View deviceChip(int icon, String text, String device) {
        boolean on = device == null ? Office.device == null : device.equals(Office.device);
        View c = chip(icon, text, on ? 0xFF6FD49B : 0x4DFFFFFF);
        c.setFocusable(true);
        c.setTag("dev_" + text);
        c.setAlpha(on ? 1f : 0.72f);
        Selector.shape(c, Ui.px(18), Ui.px(3));
        c.setOnFocusChangeListener((v, f) -> v.setAlpha(f || on ? 1f : 0.72f));
        c.setOnClickListener(v -> { Office.device = device; render(); });
        return c;
    }

    View chip(int icon, String text, int dot) {
        LinearLayout c = new LinearLayout(this);
        c.setGravity(Gravity.CENTER_VERTICAL);
        c.setPadding(Ui.px(12), Ui.px(8), Ui.px(14), Ui.px(8));
        Glass.apply(c, 18, 0x66101014);
        Icon i = new Icon(this, icon);
        c.addView(i, new LinearLayout.LayoutParams(Ui.px(16), Ui.px(16)));
        TextView t = Ui.text(this, Ui.sans, 12.5f, 0xE6FFFFFF);
        t.setText(text);
        t.setPadding(Ui.px(8), 0, Ui.px(8), 0);
        c.addView(t);
        View d = new View(this);
        d.setBackground(Ui.round(dot, 4));
        c.addView(d, new LinearLayout.LayoutParams(Ui.px(7), Ui.px(7)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.leftMargin = Ui.px(10);
        c.setLayoutParams(lp);
        return c;
    }

    /** The big card: the agent that matters most right now. */
    View spotlight(Office.Session s, boolean live) {
        String state = s == null || !live ? "idle" : s.state;
        String agent = s == null ? Office.order().get(0) : s.agent;
        int accent = accentFor(agent), sc = Office.stateColor(state);
        LinearLayout card = new LinearLayout(this);
        card.setTag("spot");
        card.setFocusable(true);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(Ui.px(28), Ui.px(26), Ui.px(28), Ui.px(26));
        Glass g = Glass.apply(card, 24, Ui.withAlpha(Ambient.blend(sc, 0xFF0E0E12, 0.82f), 0xB3));
        Selector.shape(card, Ui.px(24), Ui.px(9)); // big card: no zoom, a roomier ring that clears its edges
        card.setOnClickListener(v -> openSheet(agent, s == null ? null : s.project, s == null ? null : s.computer, v));

        FrameLayout badge = new FrameLayout(this);
        badge.setClipChildren(false);
        View ring = new View(this); // soft breathing halo while the agent is working or waiting
        GradientDrawable rg = new GradientDrawable();
        rg.setShape(GradientDrawable.OVAL);
        rg.setColor(Ui.withAlpha(sc, 0x40));
        ring.setBackground(rg);
        badge.addView(ring, new FrameLayout.LayoutParams(Ui.px(96), Ui.px(96), Gravity.CENTER));
        Icon icon = new Icon(this, iconFor(agent));
        icon.setPadding(Ui.px(18), Ui.px(18), Ui.px(18), Ui.px(18));
        GradientDrawable ig = new GradientDrawable();
        ig.setShape(GradientDrawable.OVAL);
        ig.setColor(Ui.withAlpha(accent, 0xCC));
        icon.setBackground(ig);
        icon.color(0xFFFFFFFF);
        badge.addView(icon, new FrameLayout.LayoutParams(Ui.px(76), Ui.px(76), Gravity.CENTER));
        card.addView(badge, new LinearLayout.LayoutParams(Ui.px(96), Ui.px(96)));
        if (state.equals("working") || state.equals("needs_you")) {
            pulse = ObjectAnimator.ofPropertyValuesHolder(ring,
                android.animation.PropertyValuesHolder.ofFloat("scaleX", 0.86f, 1.06f),
                android.animation.PropertyValuesHolder.ofFloat("scaleY", 0.86f, 1.06f),
                android.animation.PropertyValuesHolder.ofFloat("alpha", 1f, 0.35f));
            pulse.setDuration(1400);
            pulse.setRepeatCount(ValueAnimator.INFINITE);
            pulse.setRepeatMode(ValueAnimator.REVERSE);
            pulse.start();
        }

        LinearLayout txt = new LinearLayout(this);
        txt.setOrientation(LinearLayout.VERTICAL);
        txt.setPadding(Ui.px(26), 0, 0, 0);
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView who = Ui.text(this, Ui.sansMedium, 15, 0xF2FFFFFF);
        who.setText(agent + (s != null && s.computer != null ? "  ·  " + s.computer : ""));
        top.addView(who);
        TextView pill = Ui.text(this, Ui.sansMedium, 12, 0xFF101014);
        pill.setText(s == null ? "Quiet" : live ? Office.stateLabel(s) : "Last known: " + Office.stateLabel(s));
        pill.setPadding(Ui.px(12), Ui.px(4), Ui.px(12), Ui.px(4));
        pill.setBackground(Ui.round(sc | 0xFF000000, 12));
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(-2, -2);
        plp.leftMargin = Ui.px(14);
        top.addView(pill, plp);
        txt.addView(top);
        TextView project = Ui.text(this, Ui.serif, 34, 0xFFFFFFFF);
        project.setText(s == null ? "All quiet at the desk" : s.project);
        project.setSingleLine(true);
        project.setEllipsize(android.text.TextUtils.TruncateAt.END);
        project.setPadding(0, Ui.px(10), 0, Ui.px(6));
        txt.addView(project);
        TextView what = Ui.text(this, Ui.sans, 15, 0xCCFFFFFF);
        what.setText(s == null ? "Start a task in Claude Code, Codex or Antigravity and it shows up here." : s.text);
        what.setMaxLines(2);
        what.setEllipsize(android.text.TextUtils.TruncateAt.END);
        txt.addView(what);
        card.addView(txt, new LinearLayout.LayoutParams(0, -2, 1f));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = Ui.px(24);
        card.setLayoutParams(lp);
        return card;
    }

    /** One agent: icon, name, status; lit up while busy. */
    View agentCard(String agent, boolean live) {
        int working = 0, needs = 0;
        for (Office.Session s : Office.shown()) if (s.agent.equals(agent)) {
            if (s.state.equals("working")) working++;
            if (s.state.equals("needs_you")) needs++;
        }
        boolean busy = live && (working > 0 || needs > 0);
        boolean here = Office.installed(agent);
        String state = !live ? "idle" : needs > 0 ? "needs_you" : working > 0 ? "working" : "idle";
        int sc = Office.stateColor(state);
        LinearLayout c = new LinearLayout(this);
        c.setTag("agent_" + agent);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setFocusable(true);
        c.setPadding(Ui.px(20), Ui.px(18), Ui.px(20), Ui.px(16));
        Glass.apply(c, 20, busy ? Ui.withAlpha(Ambient.blend(sc, 0xFF0E0E12, 0.75f), 0xCC) : 0x8C101014);
        c.setAlpha(here ? 1f : 0.5f);
        c.setOnFocusChangeListener((v, f) -> Ui.lift(v, f, 1.03f));
        c.setOnClickListener(v -> openSheet(agent, null, null, v));   // its notices and anything waiting
        Selector.shape(c, Ui.px(20), Ui.px(8));
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        Icon i = new Icon(this, iconFor(agent));
        i.setPadding(Ui.px(8), Ui.px(8), Ui.px(8), Ui.px(8));
        GradientDrawable ig = new GradientDrawable();
        ig.setShape(GradientDrawable.OVAL);
        ig.setColor(Ui.withAlpha(accentFor(agent), busy ? 0xE6 : 0x59));
        i.setBackground(ig);
        i.color(0xFFFFFFFF);
        top.addView(i, new LinearLayout.LayoutParams(Ui.px(38), Ui.px(38)));
        TextView n = Ui.text(this, Ui.sansMedium, 17, 0xFFFFFFFF);
        n.setText(agent);
        n.setPadding(Ui.px(12), 0, 0, 0);
        top.addView(n, new LinearLayout.LayoutParams(0, -2, 1f));
        View dot = new View(this);
        dot.setBackground(Ui.round(sc | 0xFF000000, 5));
        top.addView(dot, new LinearLayout.LayoutParams(Ui.px(10), Ui.px(10)));
        c.addView(top);
        TextView st = Ui.text(this, Ui.sansMedium, 13, busy ? (sc | 0xFF000000) : 0xB3FFFFFF);
        String count = needs > 0 ? needs + " needs you" + (working > 0 ? " · " + working + " working" : "")
            : working > 0 ? working + " working" : here ? "Quiet" : "Not on these computers";
        st.setText(count);
        st.setPadding(0, Ui.px(12), 0, Ui.px(4));
        c.addView(st);
        String[] info = Office.info(agent);
        TextView note = Ui.text(this, Ui.sans, 12, 0x99FFFFFF);
        note.setText(busy ? "" : info == null ? "" : info[1] + (info.length > 2 && here ? " · " + info[2] : ""));
        note.setMaxLines(2);
        note.setEllipsize(android.text.TextUtils.TruncateAt.END);
        c.addView(note);
        return c;
    }

    /** "Pair this TV with MacBook" or "Paired · 83 days left · OK to forget". */
    View pairRow(String comp) {
        boolean on = Approvals.paired(this, comp);
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setFocusable(true);
        r.setTag("pair_" + comp);
        r.setPadding(Ui.px(16), Ui.px(12), Ui.px(16), Ui.px(12));
        Glass.apply(r, 16, 0x8C101014);
        Selector.shape(r, Ui.px(16), Ui.px(3));
        Icon i = new Icon(this, on ? Icon.CLAUDE : Icon.PLUS);
        if (on) i.color(0xFFE8915A);
        r.addView(i, new LinearLayout.LayoutParams(Ui.px(22), Ui.px(22)));
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setPadding(Ui.px(14), 0, 0, 0);
        TextView a = Ui.text(this, Ui.sansMedium, 16, 0xFFFFFFFF);
        a.setText(on ? "Paired with " + comp : "Pair this TV with " + comp);
        t.addView(a);
        TextView b = Ui.text(this, Ui.sans, 13, 0xA6FFFFFF);
        long left = Approvals.daysLeft(this, comp);
        b.setText(on ? (left <= 7 ? "Expires in " + left + (left == 1 ? " day" : " days") + " · OK to pair again"
            : left + " days left · requests from Claude Code show here · OK to unpair")
            : "Approve or reject Claude Code permission requests with the remote");
        b.setSingleLine(true);
        b.setEllipsize(android.text.TextUtils.TruncateAt.END);
        t.addView(b);
        r.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
        if (on && left <= 7) b.setTextColor(0xFFFFB454);
        r.setOnClickListener(v -> { if (on && left > 7) confirmForget(comp); else pickDays(comp); });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = Ui.px(10);
        r.setLayoutParams(lp);
        return r;
    }

    void confirmForget(String comp) {
        new android.app.AlertDialog.Builder(this).setTitle("Unpair " + comp + "?")
            .setMessage("This TV stops receiving Claude Code requests from " + comp + ". You can pair again any time.")
            .setPositiveButton("Unpair", (d, w) -> new Thread(() -> { Approvals.forget(this, comp); runOnUiThread(this::render); }).start())
            .setNegativeButton("Keep", null).show();
    }

    void pickDays(String comp) {
        new android.app.AlertDialog.Builder(this).setTitle("Pair with " + comp)
            .setMessage("An 8 digit code will appear on " + comp + "'s screen; type it here. The pairing lasts 90 days, then this TV asks you to pair again.")
            .setPositiveButton("Show the code", (d, w) -> startPair(comp, 90))
            .setNegativeButton("Cancel", null).show();
    }

    void startPair(String comp, int days) {
        new Thread(() -> {
            String err = Approvals.pairStart(this, comp, days);
            runOnUiThread(() -> { if (err != null) toast(err); else askCode(comp, null); });
        }).start();
    }

    void askCode(String comp, String problem) {
        android.widget.EditText e = new android.widget.EditText(this);
        e.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        e.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(8)});
        e.setHint("8 digit code");
        e.setTextSize(24);
        e.setPadding(Ui.px(20), Ui.px(12), Ui.px(20), Ui.px(12));
        new android.app.AlertDialog.Builder(this).setTitle("Code from " + comp)
            .setMessage(problem != null ? problem : "Look at " + comp + "'s screen. The code works for 3 minutes.")
            .setView(e)
            .setPositiveButton("Pair", (d, w) -> {
                String code = e.getText().toString().trim();
                new Thread(() -> {
                    String err = Approvals.pairFinish(this, comp, code);
                    runOnUiThread(() -> {
                        if (err == null) { toast("Paired. Claude Code requests from " + comp + " will show on this TV."); render(); }
                        else if (err.contains("tries left")) askCode(comp, err);
                        else toast(err);
                    });
                }).start();
            })
            .setNegativeButton("Cancel", null).show();
        e.requestFocus();
    }

    void toast(String s) { android.widget.Toast.makeText(this, s, android.widget.Toast.LENGTH_LONG).show(); }

    LinearLayout column(String title) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setClipChildren(false);
        TextView t = Ui.label(this, title, 0x99FFFFFF);
        t.setPadding(0, 0, 0, Ui.px(10));
        c.addView(t);
        return c;
    }

    TextView quiet(String s) {
        TextView t = Ui.text(this, Ui.serifItalic, 16, 0x8CFFFFFF);
        t.setText(s);
        t.setPadding(Ui.px(4), Ui.px(6), 0, 0);
        return t;
    }

    /** A compact line: agent icon, project, state, one sentence. Focusable so the D-pad can scroll. */
    View row(Office.Session s, boolean urgent) {
        boolean live = Office.connected();
        String state = live ? s.state : "idle";
        LinearLayout r = new LinearLayout(this);
        r.setTag("row_" + s.agent + s.project + s.computer);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setFocusable(true);
        r.setPadding(Ui.px(14), Ui.px(12), Ui.px(16), Ui.px(12));
        Glass.apply(r, 16, urgent ? 0x66332410 : 0x59101014);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = Ui.px(10);
        r.setLayoutParams(lp);
        Selector.shape(r, Ui.px(16), Ui.px(3));
        r.setOnClickListener(v -> openSheet(s.agent, s.project, s.computer, v));   // that project's request or notices
        Icon i = new Icon(this, iconFor(s.agent));
        i.setPadding(Ui.px(7), Ui.px(7), Ui.px(7), Ui.px(7));
        GradientDrawable ig = new GradientDrawable();
        ig.setShape(GradientDrawable.OVAL);
        ig.setColor(Ui.withAlpha(accentFor(s.agent), 0x80));
        i.setBackground(ig);
        r.addView(i, new LinearLayout.LayoutParams(Ui.px(32), Ui.px(32)));
        LinearLayout txt = new LinearLayout(this);
        txt.setOrientation(LinearLayout.VERTICAL);
        txt.setPadding(Ui.px(12), 0, 0, 0);
        LinearLayout top = new LinearLayout(this);
        TextView name = Ui.text(this, Ui.sansMedium, 14, 0xFFFFFFFF);
        name.setText(s.project);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        top.addView(name, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView st = Ui.label(this, Office.stateLabel(s), Office.stateColor(state));
        st.setPadding(Ui.px(10), 0, 0, 0);
        top.addView(st);
        txt.addView(top);
        TextView line = Ui.text(this, Ui.sans, 12, 0xA6FFFFFF);
        line.setText((s.computer == null ? "" : s.computer + " · ") + (s.text.isEmpty() ? s.agent : s.text));
        line.setSingleLine(true);
        line.setEllipsize(android.text.TextUtils.TruncateAt.END);
        line.setPadding(0, Ui.px(3), 0, 0);
        txt.addView(line);
        r.addView(txt, new LinearLayout.LayoutParams(0, -2, 1f));
        return r;
    }

    final Runnable refresh = new Runnable() { public void run() {
        new Thread(() -> { Office.poll(OfficePanel.this); h.post(() -> { if (!isFinishing()) render(); }); }).start();
        h.postDelayed(this, 15000);
    }};

    @Override protected void onResume() { super.onResume(); h.post(refresh); }
    @Override public void onWindowFocusChanged(boolean f) { super.onWindowFocusChanged(f); ambient.run(f); }
    @Override protected void onPause() {
        super.onPause();
        h.removeCallbacks(refresh);
        ambient.run(false);
        if (pulse != null) pulse.pause();
    }
}
