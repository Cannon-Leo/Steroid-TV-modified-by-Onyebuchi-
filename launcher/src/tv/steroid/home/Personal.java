package tv.steroid.home;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import java.util.Arrays;
import java.util.List;

/**
 * Settings: grouped cards of rows, each with a Lucide icon, a title, a short hint and its control on the
 * right (switch, value or round buttons). One gliding focus ring, same as the home screen.
 * Opened from the profile picture.
 */
public class Personal extends Activity {
    LinearLayout col;
    EditText nameField;
    View arrangeFirst, arrangeCard;
    String refocus;
    ScrollView page;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.init(this);
        FrameLayout root = new FrameLayout(this);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xFF15131A, 0xFF0A0A0D}));
        page = new Rails.Page(this, Ui.px(60));
        col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setClipChildren(false);
        col.setPadding(Ui.px(96), Ui.px(52), Ui.px(96), Ui.px(80));
        page.addView(col);
        root.addView(page, new FrameLayout.LayoutParams(-1, -1));
        Selector.attach(this, root);
        setContentView(root);
        build();
    }

    void build() {
        col.removeAllViews();
        TextView kicker = Ui.label(this, "Settings", 0x99FFFFFF);
        col.addView(kicker);
        TextView title = Ui.text(this, Ui.serif, 44, 0xFFFFFFFF);
        title.setText("Make it yours");
        title.setPadding(0, Ui.px(8), 0, Ui.px(28));
        col.addView(title);

        // ---- Profile
        LinearLayout profile = group("Profile");
        LinearLayout nameRow = row(profile, Icon.USER, "Your name", "Shown in the greeting. Leave empty for no name.");
        EditText name = new EditText(this);
        nameField = name;
        name.setText(Prefs.name(this));
        name.setTypeface(Ui.sans);
        name.setTextSize(16);
        name.setTextColor(0xFFFFFFFF);
        name.setSingleLine(true);
        name.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        name.setImeOptions(EditorInfo.IME_ACTION_DONE);
        name.setPadding(Ui.px(18), 0, Ui.px(18), 0);
        name.setGravity(Gravity.CENTER_VERTICAL);
        name.setBackground(Ui.round(0x1FFFFFFF, 22));
        name.setOnEditorActionListener((v, a, e) -> { Prefs.name(this, name.getText().toString()); toast("Saved"); return false; });
        nameRow.addView(name, new LinearLayout.LayoutParams(Ui.px(260), Ui.px(44)));
        row(profile, Icon.IMAGE, "Profile photo", "Add profile.jpg to your photo folder, then run: scripts/buchi.sh photos <folder>");

        // ---- Background
        LinearLayout bgGroup = group("Background");
        switchRow(bgGroup, Icon.IMAGE, "Photo slideshow", "Your photos and the World's Windows views behind the home screen", "photos");
        switchRow(bgGroup, Icon.ZOOM, "Slow zoom", "Turn off for the lightest setting", "drift");
        LinearLayout ivRow = row(bgGroup, Icon.TIMER, "Change photo every", "How long each photo stays");
        TextView iv = pillButton(ivRow, Prefs.interval(this) + " s");
        iv.setOnClickListener(v -> {
            int[] steps = {15, 30, 60, 120, 300};
            int cur = Prefs.interval(this), next = steps[0];
            for (int i = 0; i < steps.length; i++) if (steps[i] == cur) next = steps[(i + 1) % steps.length];
            Prefs.put(this, "interval", next);
            iv.setText(next + " s");
        });

        // ---- Arrange home screen
        LinearLayout arrange = group("Home screen");
        arrangeCard = arrange;
        List<String> order = Prefs.order(this);
        View firstArrange = null;
        for (int i = 0; i < order.size(); i++) {
            final String k = order.get(i);
            final int at = i;
            String sname = Prefs.SECTION_NAMES[Arrays.asList(Prefs.SECTIONS).indexOf(k)];
            boolean shown = Prefs.shown(this, k);
            LinearLayout r = row(arrange, Icon.GRID, sname, shown ? "Size " + new String[]{"small", "medium", "large"}[Prefs.size(this, k)] : "Hidden");
            r.setAlpha(shown ? 1f : 0.55f);
            final String key = "arr_" + k;
            View first = null;
            if (!k.equals("system")) {
                first = roundButton(r, Icon.UP, key, 0, () -> { if (at > 0) { order.add(at - 1, order.remove(at)); Prefs.order(this, order); build(); } });
                roundButton(r, Icon.DOWN, key, 1, () -> { if (at < order.size() - 1) { order.add(at + 1, order.remove(at)); Prefs.order(this, order); build(); } });
                roundButton(r, Icon.SIZE, key, 2, () -> { Prefs.size(this, k, (Prefs.size(this, k) + 1) % 3); build(); });
            }
            View eye = roundButton(r, shown ? Icon.EYE : Icon.EYE_OFF, key, 3, () -> { Prefs.shown(this, k, !Prefs.shown(this, k)); build(); });
            if (first == null) first = eye;
            if (firstArrange == null) firstArrange = first;
        }
        arrangeFirst = firstArrange;

        // ---- Hidden apps
        List<String> hidden = Prefs.listOrEmpty(this, "hidden");
        if (!hidden.isEmpty()) {
            LinearLayout hg = group("Hidden apps");
            for (String p : hidden) {
                String label = p;
                try { label = getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(p, 0)).toString(); }
                catch (Exception ignored) {}
                LinearLayout r = row(hg, Icon.APP, label, "Hidden from All apps");
                TextView show = pillButton(r, "Show again");
                show.setOnClickListener(v -> { hidden.remove(p); Prefs.list(this, "hidden", hidden); build(); });
            }
        }

        boolean wantArrange = getIntent().getBooleanExtra("arrange", false);
        col.post(() -> {
            View back = refocus == null ? null : col.findViewWithTag(refocus);
            if (back != null) back.requestFocus();
            else if (wantArrange && arrangeFirst != null) {
                arrangeFirst.requestFocus();
                page.smoothScrollTo(0, arrangeCard.getTop() - Ui.px(60));
            } else name.requestFocus();
        });
    }

    // ---------- building blocks ----------

    /** A titled card holding rows. */
    LinearLayout group(String title) {
        TextView t = Ui.label(this, title, 0x99FFFFFF);
        t.setPadding(Ui.px(4), Ui.px(8), 0, Ui.px(12));
        col.addView(t);
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setClipChildren(false);
        card.setPadding(Ui.px(8), Ui.px(8), Ui.px(8), Ui.px(8));
        GradientDrawable bg = Ui.round(0x0FFFFFFF, 22);
        bg.setStroke(Ui.px(1), 0x14FFFFFF);
        card.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = Ui.px(30);
        col.addView(card, lp);
        return card;
    }

    /** Icon chip, title and hint on the left; controls get added to the right. */
    LinearLayout row(LinearLayout group, int icon, String title, String hint) {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setClipChildren(false);
        r.setPadding(Ui.px(14), Ui.px(12), Ui.px(14), Ui.px(12));
        Icon i = new Icon(this, icon);
        i.setPadding(Ui.px(9), Ui.px(9), Ui.px(9), Ui.px(9));
        GradientDrawable chip = new GradientDrawable();
        chip.setShape(GradientDrawable.OVAL);
        chip.setColor(0x1AFFFFFF);
        i.setBackground(chip);
        r.addView(i, new LinearLayout.LayoutParams(Ui.px(40), Ui.px(40)));
        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        text.setPadding(Ui.px(16), 0, Ui.px(16), 0);
        TextView t = Ui.text(this, Ui.sansMedium, 16, 0xF2FFFFFF);
        t.setText(title);
        text.addView(t);
        if (hint != null) {
            TextView h = Ui.text(this, Ui.sans, 12.5f, 0x8CFFFFFF);
            h.setText(hint);
            h.setPadding(0, Ui.px(4), 0, 0);
            text.addView(h);
        }
        r.addView(text, new LinearLayout.LayoutParams(0, -2, 1f));
        group.addView(r, new LinearLayout.LayoutParams(-1, -2));
        return r;
    }

    /** A whole row that toggles a setting; the switch on the right shows its state. */
    void switchRow(LinearLayout group, int icon, String title, String hint, String key) {
        LinearLayout r = row(group, icon, title, hint);
        r.setFocusable(true);
        r.setTag("sw_" + key);
        GradientDrawable bg = Ui.round(0x00FFFFFF, 16);
        r.setBackground(bg);
        Switch sw = new Switch(this, Prefs.sp(this).getBoolean(key, true));
        r.addView(sw, new LinearLayout.LayoutParams(Ui.px(48), Ui.px(28)));
        r.setOnFocusChangeListener((v, f) -> bg.setColor(f ? 0x14FFFFFF : 0x00FFFFFF));
        r.setOnClickListener(v -> {
            boolean now = !Prefs.sp(this).getBoolean(key, true);
            Prefs.put(this, key, now);
            sw.set(now);
        });
    }

    TextView pillButton(LinearLayout r, String text) {
        TextView b = Ui.text(this, Ui.sansMedium, 14, 0xF2FFFFFF);
        b.setText(text);
        b.setGravity(Gravity.CENTER);
        b.setFocusable(true);
        b.setPadding(Ui.px(18), 0, Ui.px(18), 0);
        GradientDrawable g = Ui.round(0x1FFFFFFF, 20);
        b.setBackground(g);
        b.setOnFocusChangeListener((v, f) -> g.setColor(f ? 0x40FFFFFF : 0x1FFFFFFF));
        r.addView(b, new LinearLayout.LayoutParams(-2, Ui.px(40)));
        return b;
    }

    View roundButton(LinearLayout r, int icon, String rowKey, int idx, Runnable run) {
        Icon b = new Icon(this, icon);
        b.setFocusable(true);
        b.setTag(rowKey + "#" + idx);
        b.setPadding(Ui.px(10), Ui.px(10), Ui.px(10), Ui.px(10));
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(0x1AFFFFFF);
        b.setBackground(g);
        b.setOnFocusChangeListener((v, f) -> g.setColor(f ? 0x40FFFFFF : 0x1AFFFFFF));
        b.setOnClickListener(v -> { refocus = (String) b.getTag(); run.run(); });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.px(40), Ui.px(40));
        lp.leftMargin = Ui.px(10);
        r.addView(b, lp);
        return b;
    }

    /** A small on/off switch drawn in one pass (no image assets). */
    static class Switch extends View {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        boolean on;
        float pos;
        Switch(android.content.Context c, boolean on) { super(c); this.on = on; pos = on ? 1 : 0; }
        void set(boolean v) { on = v; invalidate(); }
        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), r = h / 2;
            pos += ((on ? 1 : 0) - pos) * 0.35f;
            p.setColor(on ? 0xFF6FD49B : 0x40FFFFFF);
            c.drawRoundRect(0, 0, w, h, r, r, p);
            p.setColor(0xFFFFFFFF);
            float knob = r - Ui.px(3);
            c.drawCircle(r + (w - 2 * r) * pos, r, knob, p);
            if (Math.abs(pos - (on ? 1 : 0)) > 0.01f) postInvalidateOnAnimation();
        }
    }

    void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    @Override public boolean onKeyDown(int k, KeyEvent e) {
        if (k == KeyEvent.KEYCODE_BACK && nameField != null) Prefs.name(this, nameField.getText().toString());
        return super.onKeyDown(k, e);
    }

    @Override protected void onPause() {
        super.onPause();
        if (nameField != null) Prefs.name(this, nameField.getText().toString());
    }
}
