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
 * busy ones lit up. At the bottom: what needs you, and recent activity side by side. Read only.
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
        col.addView(head);

        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setClipChildren(false);
        col.addView(body);
        scroll.addView(col);
        root.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
        Selector.attach(this, root);
        setContentView(root);
        render();
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
        if (comps.isEmpty()) computersRow.addView(chip(Icon.LAPTOP, live ? "Connected" : Office.lastSeen(this), 0x80FFFFFF));
        for (String c : comps) {
            boolean pc = !c.toLowerCase().contains("mac");
            computersRow.addView(chip(pc ? Icon.MONITOR : Icon.LAPTOP, c, 0xFF6FD49B));
        }

        // spotlight: needs you, else working, else most recent
        Office.Session best = null;
        for (Office.Session s : Office.sessions) if (best == null || Office.rank(s) > Office.rank(best)) best = s;
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
        body.addView(cards, clp);

        // needs you | recent activity
        LinearLayout lists = new LinearLayout(this);
        lists.setClipChildren(false);
        LinearLayout left = column("Needs you"), right = column("Recent activity");
        int n1 = 0, n2 = 0;
        for (Office.Session s : Office.sessions) {
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
        body.addView(lists, llp);

        View back = focusKey == null ? null : body.findViewWithTag(focusKey);
        if (back != null) back.requestFocus();
        else { View s = body.findViewWithTag("spot"); if (s != null) s.requestFocus(); }
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
        for (Office.Session s : Office.sessions) if (s.agent.equals(agent)) {
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
        String[] info = Office.agents.get(agent);
        TextView note = Ui.text(this, Ui.sans, 12, 0x99FFFFFF);
        note.setText(busy ? "" : info == null ? "" : info[1] + (info.length > 2 && here ? " · " + info[2] : ""));
        note.setMaxLines(2);
        note.setEllipsize(android.text.TextUtils.TruncateAt.END);
        c.addView(note);
        return c;
    }

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
