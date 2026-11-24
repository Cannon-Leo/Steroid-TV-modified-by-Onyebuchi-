package tv.steroid.home;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Live TV: official 24 hour streams that channels run on their own YouTube channels, played in the
 * same clean player as The World's Windows. Each channel's current live video is looked up when you
 * tune to it (stream ids change when a channel restarts its stream), cached for a few hours.
 * Up and down change channel, OK opens the guide, Back closes it or leaves.
 */
public class LiveTV extends Activity {
    // name, YouTube channel path (channel id or @handle), line
    static final String[][] CHANNELS = {
        {"Channels TV", "channel/UCEXGDNclvmg6RW0vipJYsTQ", "Lagos · Nigerian news"},
        {"Arise News", "@ARISENEWS", "Lagos and Abuja · Africa and the world"},
        {"Al Jazeera English", "channel/UCNye-wNBqNL5ZzHSJj3l8Bg", "Doha · world news"},
        {"DW News", "channel/UCknLrEdhRCp1aegoMqRaCZg", "Berlin · world news"},
        {"France 24 English", "channel/UCQfwfsi5VrQ8yKZ-UWmAEFg", "Paris · world news"},
        {"Sky News", "channel/UCoMdktPbSTixAyNGwb-UYkQ", "London · UK and world news"},
        {"Bloomberg", "channel/UCIALMKvObZNtJ6AmdCLP7Lg", "New York · markets and business"},
        {"Euronews", "@euronews", "Lyon · European news"},
        {"NASA", "channel/UCLA_DiR1FfKNvjuUpBHmylQ", "Space station views and launches"},
    };
    static final long CACHE_MS = 3 * 3600_000L;

    final Handler h = new Handler(Looper.getMainLooper());
    SharedPreferences prefs;
    FrameLayout root;
    WebView web;
    LinearLayout card, guide;
    TextView cardName, cardLine, status;
    int ch;
    boolean loaded;
    int tuneSeq;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.init(this);
        prefs = getSharedPreferences("livetv", MODE_PRIVATE);
        ch = Math.max(0, Math.min(CHANNELS.length - 1, prefs.getInt("ch", 0)));
        root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);

        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setDomStorageEnabled(true);
        web.setBackgroundColor(0xFF000000);
        web.setFocusable(false);
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient());
        web.addJavascriptInterface(new Bridge(), "Native");
        root.addView(web, new FrameLayout.LayoutParams(-1, -1));

        // channel card, bottom left, shown while tuning and for a few seconds after
        card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(Ui.px(24), Ui.px(18), Ui.px(28), Ui.px(18));
        card.setBackground(Ui.round(0xD9101014, 20));
        TextView live = Ui.label(this, "● Live TV", 0xFFFF5A4F);
        card.addView(live);
        cardName = Ui.text(this, Ui.serif, 32, 0xFFFFFFFF);
        cardName.setPadding(0, Ui.px(6), 0, Ui.px(2));
        card.addView(cardName);
        cardLine = Ui.text(this, Ui.sans, 15, 0xCCFFFFFF);
        card.addView(cardLine);
        status = Ui.text(this, Ui.sans, 14, 0x99FFFFFF);
        status.setPadding(0, Ui.px(8), 0, 0);
        card.addView(status);
        FrameLayout.LayoutParams cl = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.START);
        cl.setMargins(Ui.px(48), 0, 0, Ui.px(44));
        root.addView(card, cl);

        buildGuide();
        Selector.attach(this, root);
        setContentView(root);
        tune();
    }

    void buildGuide() {
        guide = new LinearLayout(this);
        guide.setOrientation(LinearLayout.VERTICAL);
        guide.setPadding(Ui.px(28), Ui.px(36), Ui.px(28), Ui.px(28));
        guide.setBackground(Ui.round(0xE6101014, 0));
        guide.setClipChildren(false);
        guide.setClipToPadding(false);
        TextView t = Ui.text(this, Ui.serif, 30, 0xFFFFFFFF);
        t.setText("Live TV");
        guide.addView(t);
        TextView sub = Ui.text(this, Ui.sans, 14, 0x99FFFFFF);
        sub.setText("Official streams from each channel's own YouTube");
        sub.setPadding(0, Ui.px(4), 0, Ui.px(18));
        guide.addView(sub);
        for (int i = 0; i < CHANNELS.length; i++) {
            final int k = i;
            LinearLayout r = new LinearLayout(this);
            r.setOrientation(LinearLayout.VERTICAL);
            r.setFocusable(true);
            r.setTag("ch" + i);
            r.setPadding(Ui.px(16), Ui.px(10), Ui.px(16), Ui.px(10));
            GradientDrawable bg = Ui.round(0x00FFFFFF, 14);
            r.setBackground(bg);
            TextView n = Ui.text(this, Ui.sansMedium, 18, 0xFFFFFFFF);
            n.setText(CHANNELS[i][0]);
            r.addView(n);
            TextView l = Ui.text(this, Ui.sans, 13, 0x99FFFFFF);
            l.setText(CHANNELS[i][2]);
            l.setSingleLine(true);
            l.setEllipsize(android.text.TextUtils.TruncateAt.END);
            r.addView(l);
            r.setOnFocusChangeListener((v, f) -> bg.setColor(f ? 0x26FFFFFF : 0x00FFFFFF));
            Selector.shape(r, Ui.px(14), Ui.px(3));
            r.setOnClickListener(v -> { ch = k; showGuide(false); tune(); });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.bottomMargin = Ui.px(4);
            guide.addView(r, lp);
        }
        guide.setVisibility(View.GONE);
        root.addView(guide, new FrameLayout.LayoutParams(Ui.px(400), -1, Gravity.START));
    }

    void showGuide(boolean on) {
        guide.setVisibility(on ? View.VISIBLE : View.GONE);
        if (on) { View r = guide.findViewWithTag("ch" + ch); if (r != null) r.requestFocus(); }
    }

    final Runnable hideCard = () -> card.animate().alpha(0f).setDuration(400).start();

    void showCard(String line) {
        cardName.setText(CHANNELS[ch][0]);
        cardLine.setText(CHANNELS[ch][2]);
        status.setText(line);
        status.setVisibility(line.isEmpty() ? View.GONE : View.VISIBLE);
        card.animate().cancel();
        card.setAlpha(1f);
        h.removeCallbacks(hideCard);
    }

    void tune() {
        prefs.edit().putInt("ch", ch).apply();
        final int seq = ++tuneSeq, c = ch;
        showCard("Tuning in…");
        if (loaded) web.evaluateJavascript("player&&player.stopVideo&&player.stopVideo()", null);
        String cached = prefs.getString("id_" + c, "");
        if (!cached.isEmpty() && System.currentTimeMillis() - prefs.getLong("at_" + c, 0) < CACHE_MS) { play(cached); return; }
        new Thread(() -> {
            String id = resolve(CHANNELS[c][1]);
            h.post(() -> {
                if (seq != tuneSeq) return; // changed channel meanwhile
                if (id == null) { showCard("Not live right now. Try another channel."); return; }
                prefs.edit().putString("id_" + c, id).putLong("at_" + c, System.currentTimeMillis()).apply();
                play(id);
            });
        }).start();
    }

    void play(String id) {
        if (!loaded) {
            loaded = true;
            web.loadDataWithBaseURL("https://steroid.home/#" + id + "@0@0", asset("window.html"), "text/html", "utf-8", null);
        } else web.evaluateJavascript("load('" + id + "',0);setMuted(false)", null);
    }

    /** The channel's current live video id, read from its /live page; null when it isn't live. */
    static String resolve(String path) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL("https://www.youtube.com/" + path + "/live").openConnection();
            conn.setConnectTimeout(6000);
            conn.setReadTimeout(8000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/155.0.0.0 Safari/537.36");
            conn.setRequestProperty("Accept-Language", "en");
            conn.setRequestProperty("Cookie", "CONSENT=YES+1");
            StringBuilder sb = new StringBuilder();
            try (InputStream in = conn.getInputStream()) {
                byte[] b = new byte[16384];
                int n;
                while ((n = in.read(b)) > 0 && sb.length() < 1_500_000) sb.append(new String(b, 0, n, "UTF-8"));
            }
            String html = sb.toString();
            Matcher m = Pattern.compile("<link rel=\"canonical\" href=\"https://www\\.youtube\\.com/watch\\?v=([A-Za-z0-9_-]{11})\"").matcher(html);
            if (!m.find()) return null; // the /live page fell back to the channel page: nothing on air
            if (!html.contains("\"isLiveNow\":true") && !html.contains("\"isLive\":true")) return null; // an upcoming or past stream
            return m.group(1);
        } catch (Exception e) {
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    String asset(String name) {
        try (InputStream in = getAssets().open(name)) {
            byte[] b = new byte[in.available()];
            int n = in.read(b);
            return new String(b, 0, n, "UTF-8");
        } catch (Exception e) { return ""; }
    }

    class Bridge {
        @JavascriptInterface public void position(String id, int seconds) { }
        @JavascriptInterface public void state(int s, int live) {
            h.post(() -> { if (s == 1) { status.setVisibility(View.GONE); h.removeCallbacks(hideCard); h.postDelayed(hideCard, 4000); } });
        }
        @JavascriptInterface public void error(int code) {
            h.post(() -> {
                prefs.edit().remove("id_" + ch).apply(); // the stream id went stale: look it up again next time
                showCard(code == 101 || code == 150 ? "This channel doesn't allow playing outside YouTube." : "The stream stopped. Press OK to pick a channel.");
            });
        }
    }

    @Override public boolean onKeyDown(int code, KeyEvent e) {
        if (guide.getVisibility() == View.VISIBLE) return super.onKeyDown(code, e);
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_UP: case KeyEvent.KEYCODE_CHANNEL_UP:
                ch = (ch + CHANNELS.length - 1) % CHANNELS.length; tune(); return true;
            case KeyEvent.KEYCODE_DPAD_DOWN: case KeyEvent.KEYCODE_CHANNEL_DOWN:
                ch = (ch + 1) % CHANNELS.length; tune(); return true;
            case KeyEvent.KEYCODE_DPAD_CENTER: case KeyEvent.KEYCODE_ENTER: case KeyEvent.KEYCODE_DPAD_LEFT:
                showGuide(true); return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                showCard(""); h.postDelayed(hideCard, 4000); return true;
        }
        return super.onKeyDown(code, e);
    }

    @Override public void onBackPressed() {
        if (guide.getVisibility() == View.VISIBLE) showGuide(false); else finish();
    }

    @Override protected void onPause() {
        super.onPause();
        web.evaluateJavascript("player&&player.pauseVideo&&player.pauseVideo()", null);
    }

    @Override protected void onResume() {
        super.onResume();
        if (loaded) web.evaluateJavascript("player&&player.playVideo&&player.playVideo()", null);
    }

    @Override protected void onDestroy() {
        h.removeCallbacksAndMessages(null);
        web.loadUrl("about:blank");
        web.destroy();
        super.onDestroy();
    }
}
