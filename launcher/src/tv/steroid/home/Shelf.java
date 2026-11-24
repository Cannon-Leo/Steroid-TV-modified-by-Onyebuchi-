package tv.steroid.home;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.GradientDrawable;
import android.media.tv.TvContract;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * One app's own row of videos: its Watch Next entries (continue watching, next episode) first, then the
 * programs on its home screen channels (recommended, recently played). Apps publish these to Android TV
 * and update them as you watch, so the row follows your activity. Refreshed whenever Home comes back.
 * Hidden when the app publishes nothing.
 */
class Shelf extends LinearLayout {
    static final class Prog { String pkg, title, sub, poster, intent; long when; }

    final Activity act;
    final String pkg;
    final TextView head;
    final LinearLayout row;
    final HorizontalScrollView scroller;
    final float size;
    final Handler h = new Handler(Looper.getMainLooper());
    static final ExecutorService io = Executors.newSingleThreadExecutor();
    static final LruCache<String, Bitmap> posters = new LruCache<String, Bitmap>(6 * 1024 * 1024) {
        @Override protected int sizeOf(String k, Bitmap b) { return b.getByteCount(); }
    };
    String signature = "";

    Shelf(Activity a, String pkg, String label, float size) {
        super(a);
        act = a;
        this.pkg = pkg;
        this.size = size;
        setOrientation(VERTICAL);
        setClipChildren(false);
        head = Ui.label(a, label + " · Recently played", 0xCCFFFFFF);
        head.setPadding(0, Ui.px(28), 0, Ui.px(4));
        addView(head);
        scroller = new Rails.Rail(a, Ui.px(Home.PAD));
        scroller.setPadding(Ui.px(Home.PAD), Ui.px(14), Ui.px(Home.PAD), Ui.px(14));
        row = new LinearLayout(a);
        row.setClipChildren(false);
        scroller.addView(row);
        LayoutParams slp = new LayoutParams(-1, -2);
        slp.leftMargin = -Ui.px(Home.PAD);
        slp.rightMargin = -Ui.px(Home.PAD);
        addView(scroller, slp);
        setVisibility(GONE);
    }

    void refresh() {
        io.execute(() -> {
            List<Prog> l = query();
            StringBuilder sig = new StringBuilder();
            for (Prog p : l) sig.append(p.title).append('|').append(p.poster).append(';');
            String s = sig.toString();
            h.post(() -> { if (!s.equals(signature)) { signature = s; render(l); } });
        });
    }

    /**
     * Android refuses filtered queries from launchers ("Selection not allowed"), so read every
     * program once (cached for a few seconds, shared by all rows) and pick this app's here.
     */
    List<Prog> query() {
        List<Prog> out = new ArrayList<>();
        List<Prog> src = new ArrayList<>(all(act.getContentResolver()));
        try {
            org.json.JSONArray hist = History.load(act);
            for (int i = 0; i < hist.length(); i++) {
                org.json.JSONObject o = hist.getJSONObject(i);
                Prog p = new Prog();
                p.pkg = o.optString("pkg"); p.title = o.optString("title"); p.sub = o.optString("sub");
                p.poster = o.optString("thumb"); p.intent = o.optString("intent"); p.when = o.optLong("at");
                if (p.poster.isEmpty()) p.poster = null;
                src.add(p);
            }
        } catch (Exception ignored) {}
        for (Prog p : src) {
            if (!pkg.equals(p.pkg)) continue;
            boolean dup = false;
            for (Prog q : out) if (q.title.equals(p.title)) { dup = true; break; }
            if (!dup) out.add(p);
            if (out.size() >= 16) break;
        }
        android.util.Log.i("SteroidShelf", pkg + ": " + out.size() + " programs visible");
        return out;
    }

    static List<Prog> cache = new ArrayList<>();
    static volatile boolean fresh;
    static long cachedAt;

    static synchronized List<Prog> all(ContentResolver cr) {
        if (System.currentTimeMillis() - cachedAt < 5000 && !fresh) return cache;
        fresh = false;
        List<Prog> watchNext = new ArrayList<>(), preview = new ArrayList<>();
        readAll(cr, TvContract.WatchNextPrograms.CONTENT_URI, true, watchNext);
        readAll(cr, TvContract.PreviewPrograms.CONTENT_URI, false, preview);
        java.util.Collections.sort(watchNext, (x, y) -> Long.compare(y.when, x.when)); // most recently watched first
        List<Prog> l = new ArrayList<>(watchNext);
        l.addAll(preview);
        cache = l;
        cachedAt = System.currentTimeMillis();
        android.util.Log.i("SteroidShelf", "read " + watchNext.size() + " watch next + " + preview.size() + " channel programs");
        return l;
    }

    static void readAll(ContentResolver cr, Uri uri, boolean watchNext, List<Prog> out) {
        String[] cols = watchNext
            ? new String[]{"package_name", "title", "episode_title", "poster_art_uri", "intent_uri", "last_engagement_time_utc_millis"}
            : new String[]{"package_name", "title", "episode_title", "poster_art_uri", "intent_uri"};
        try (Cursor c = cr.query(uri, cols, null, null, null)) {
            while (c != null && c.moveToNext()) {
                Prog p = new Prog();
                p.pkg = c.getString(0); p.title = c.getString(1); p.sub = c.getString(2);
                p.poster = c.getString(3); p.intent = c.getString(4);
                if (watchNext) p.when = c.getLong(5);
                if (p.title != null && p.intent != null && p.pkg != null) out.add(p);
            }
        } catch (Exception e) { android.util.Log.i("SteroidShelf", uri.getLastPathSegment() + " refused: " + e); }
    }

    static boolean probed;

    /** One time diagnostic: how many rows Android lets this launcher see at all (read with adb logcat). */
    static void probe(ContentResolver cr) {
        Uri[] uris = {TvContract.Channels.CONTENT_URI, TvContract.PreviewPrograms.CONTENT_URI, TvContract.WatchNextPrograms.CONTENT_URI};
        for (Uri u : uris) {
            try (Cursor c = cr.query(u, new String[]{"package_name"}, null, null, null)) {
                java.util.Map<String, Integer> by = new java.util.TreeMap<>();
                int n = 0;
                while (c != null && c.moveToNext()) { n++; by.merge(String.valueOf(c.getString(0)), 1, Integer::sum); }
                android.util.Log.i("SteroidShelf", "probe " + u.getLastPathSegment() + ": " + n + " rows " + by);
            } catch (Exception e) {
                android.util.Log.i("SteroidShelf", "probe " + u.getLastPathSegment() + " refused: " + e);
            }
        }
    }


    void render(List<Prog> l) {
        boolean hadFocus = row.findFocus() != null;
        row.removeAllViews();
        if (l.isEmpty()) { setVisibility(GONE); return; }
        for (Prog p : l) row.addView(card(p));
        setVisibility(VISIBLE);
        if (hadFocus) row.getChildAt(0).requestFocus();
    }

    View card(Prog p) {
        FrameLayout card = Ui.frame(act, 12, 1.06f, null);
        FrameLayout in = Ui.inner(card);
        ImageView iv = new ImageView(act);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        in.addView(iv, new FrameLayout.LayoutParams(-1, -1));
        View fade = new View(act);
        fade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0x00000000, 0xE6000000}));
        in.addView(fade, new FrameLayout.LayoutParams(-1, Ui.px(70 * size), Gravity.BOTTOM));
        TextView t = Ui.text(act, Ui.sansMedium, 12, 0xFFFFFFFF);
        t.setText(p.sub != null && !p.sub.isEmpty() ? p.title + " · " + p.sub : p.title);
        t.setMaxLines(2);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        FrameLayout.LayoutParams tl = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        tl.setMargins(Ui.px(10), 0, Ui.px(10), Ui.px(8));
        in.addView(t, tl);
        card.setOnClickListener(v -> {
            try { act.startActivity(Intent.parseUri(p.intent, Intent.URI_INTENT_SCHEME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
            catch (Exception e) {
                // the exact item could not be opened: open the app instead (it resumes where you left off)
                Intent li = act.getPackageManager().getLeanbackLaunchIntentForPackage(p.pkg);
                if (li == null) li = act.getPackageManager().getLaunchIntentForPackage(p.pkg);
                if (li != null) act.startActivity(li.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                else Toast.makeText(act, "Couldn't open that", Toast.LENGTH_SHORT).show();
            }
        });
        LayoutParams lp = new LayoutParams(Ui.px(204 * size), Ui.px(122 * size));
        lp.rightMargin = Ui.px(10);
        card.setLayoutParams(lp);
        if (p.poster != null) {
            Bitmap cached = posters.get(p.poster);
            if (cached != null) iv.setImageBitmap(cached);
            else io.execute(() -> { Bitmap b = poster(p.poster); if (b != null) h.post(() -> iv.setImageBitmap(b)); });
        }
        return card;
    }

    /** Small, low memory poster: about 400px wide, 16 bit colour. */
    Bitmap poster(String uri) {
        try {
            byte[] data;
            if (uri.startsWith("http")) {
                HttpURLConnection c = (HttpURLConnection) new URL(uri).openConnection();
                c.setConnectTimeout(5000); c.setReadTimeout(8000);
                try (InputStream in = c.getInputStream()) { data = readAll(in); }
            } else {
                try (InputStream in = act.getContentResolver().openInputStream(Uri.parse(uri))) { data = readAll(in); }
            }
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(data, 0, data.length, o);
            o.inSampleSize = 1;
            while (o.outWidth / (o.inSampleSize * 2) >= 400) o.inSampleSize *= 2;
            o.inJustDecodeBounds = false;
            o.inPreferredConfig = Bitmap.Config.RGB_565;
            Bitmap b = BitmapFactory.decodeByteArray(data, 0, data.length, o);
            if (b != null) posters.put(uri, b);
            return b;
        } catch (Exception e) { return null; }
    }

    static byte[] readAll(InputStream in) throws java.io.IOException {
        java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
        return bo.toByteArray();
    }
}
