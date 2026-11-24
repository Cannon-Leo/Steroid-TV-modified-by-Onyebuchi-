package tv.steroid.home;

import android.content.Context;
import android.graphics.*;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The home background. With photos: a looping slideshow (the World's Windows views plus your own
 * photos), crossfading with a slow drift, darkened so text stays readable. Without photos: the
 * original slow aurora with film grain. ~12fps, paused when hidden. At most two images in memory,
 * each decoded at about 1280px wide in 16 bit colour (~2 MB).
 */
class Ambient extends View {
    final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    final Paint shade = new Paint(), photoShade = new Paint(), sideShade = new Paint(), grain = new Paint(), dreamTop = new Paint();
    boolean dream; // the screensaver: photos at full brightness
    final Paint img = new Paint(Paint.FILTER_BITMAP_FLAG), fadePaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    int tint = 0xFFE8915A, shown = tint, second = 0xFF5B3CC4;
    float t;
    boolean running;

    // slideshow
    static final String OWN = "photos"; // getExternalFilesDir("photos"), filled from the Mac with adb push
    static final String SYNCED = "synced"; // your photos fetched from Little Office (see Photos)
    static final String FREE = "free";  // free to use photos the TV downloads by itself (see fetchFree)

    /**
     * Free to use landscape photos from Unsplash photographers (Unsplash licence: free for any use,
     * no permission or credit needed), served at TV size by Lorem Picsum. Downloaded once, one at a
     * time, on a background thread; ones that fail are simply skipped and tried again another day.
     */
    static final int[] FREE_IDS = {10, 11, 15, 16, 17, 18, 28, 29, 37, 41, 42, 43, 49, 54, 57, 58, 76, 84, 101, 110,
        116, 120, 122, 124, 128, 129, 142, 152, 167, 184, 188, 191, 200, 214};
    /**
     * Picked from Unsplash's "TV wallpaper" search: landscapes, sky, sea and wildlife with no words,
     * logos or watermarks in the picture. Unsplash+ (paid, watermarked) photos are left out.
     */
    static final String[] UNSPLASH = {
        "photo-1491466424936-e304919aada7", // northern lights
        "photo-1499678329028-101435549a4e", // Vernazza at sunset
        "photo-1484950763426-56b5bf172dbb", // off road car at dusk
        "photo-1482784160316-6eb046863ece", // Yosemite Tunnel View in snow
        "photo-1469474968028-56623f02e42e", // mountain in sun rays
        "photo-1451187580459-43490279c0fa", // outer space
        "photo-1507187632231-5beb21a654a2", // palms reflected in water
        "photo-1504006833117-8886a355efbf", // squirrel on black
        "photo-1500673922987-e212871fec22", // lights between trees
        "photo-1431440869543-efaf3388c585", // lightning storm
        "photo-1475598322381-f1b499717dda", // lava meeting the ocean
        "photo-1505699261378-c372af38134c", // Xinghai Bridge at sunset
        "photo-1507608869274-d3177c8bb4c7", // hot air balloons
        "photo-1508144753681-9986d4df99b3", // looking up a mountain
        "photo-1466853817435-05b43fe45b39", // mountain and water at night
        "photo-1430876988766-1be68caef0e4", // sleeping tiger
    };
    static volatile boolean fetching;

    /** World's Windows previews with no text, badges or logos on them (checked by eye); the rest stay in Windows only. */
    static final java.util.Set<String> CLEAN_VIEWS = new java.util.HashSet<>(java.util.Arrays.asList(
        "1RHOWVH4aHA", "B3k4zxss5DA", "FABZhqaFq6k", "RKOWCGVdsbM", "T108Hvq_0uM", "bMEOD4-qcbs", "hFd_jOr5X9E", "rbW4-x6vJmk"));

    /** Every free photo: {file name, address}. */
    static java.util.List<String[]> freeList() {
        java.util.List<String[]> l = new java.util.ArrayList<>();
        for (String id : UNSPLASH) l.add(new String[]{"s" + id.substring(6, 19) + ".jpg",
            "https://images.unsplash.com/" + id + "?fm=jpg&q=80&w=1920&h=1080&fit=crop&crop=entropy"});
        for (int id : FREE_IDS) l.add(new String[]{"u" + id + ".jpg", "https://picsum.photos/id/" + id + "/1920/1080.jpg"});
        return l;
    }

    static void fetchFree(Context c) {
        if (fetching) return;
        File dir = c.getExternalFilesDir(FREE);
        if (dir == null) return;
        long lastTry = Prefs.sp(c).getLong("freeTried", 0);
        java.util.List<String[]> list = freeList();
        int have = 0;
        for (String[] f : list) if (new File(dir, f[0]).exists()) have++;
        if (have == list.size() || System.currentTimeMillis() - lastTry < 6 * 3600_000L && have > 0 && Prefs.sp(c).getBoolean("freeV2", false)) return;
        Prefs.sp(c).edit().putBoolean("freeV2", true).apply(); // new photos added: fetch them now, once
        Prefs.sp(c).edit().putLong("freeTried", System.currentTimeMillis()).apply();
        fetching = true;
        new Thread(() -> {
            try {
                for (String[] item : list) {
                    File out = new File(dir, item[0]), tmp = new File(dir, item[0] + ".part");
                    if (out.exists()) continue;
                    java.net.HttpURLConnection conn = null;
                    try {
                        conn = (java.net.HttpURLConnection) new java.net.URL(item[1]).openConnection();
                        conn.setConnectTimeout(8000);
                        conn.setReadTimeout(15000);
                        conn.setInstanceFollowRedirects(true);
                        if (conn.getResponseCode() != 200) continue;
                        try (java.io.InputStream in = conn.getInputStream(); java.io.FileOutputStream fo = new java.io.FileOutputStream(tmp)) {
                            byte[] b = new byte[32768];
                            long total = 0;
                            for (int n; (n = in.read(b)) > 0; ) { fo.write(b, 0, n); total += n; if (total > 4_000_000) throw new java.io.IOException("too big"); }
                        }
                        if (tmp.length() > 20_000) tmp.renameTo(out); else tmp.delete();
                    } catch (Exception e) {
                        tmp.delete();
                    } finally {
                        if (conn != null) conn.disconnect();
                    }
                }
            } finally { fetching = false; }
        }).start();
    }
    final List<String> sources = new ArrayList<>();
    final ExecutorService io = Executors.newSingleThreadExecutor();
    final Handler h = new Handler(Looper.getMainLooper());
    Bitmap cur, prev, curFg, prevFg;

    /** Drawn between the photo and its subject in the screensaver, so the subject can stand in front (the clock). */
    interface Overlay { void draw(Canvas c, int w, int h); void drawFront(Canvas c, int w, int h); }
    Overlay overlay;

    /**
     * Depth effect: the photo's subject alone (cut out with the mask made on the computer, see
     * little-office/depth.py), drawn again on top of the clock. Photos without a mask return null.
     */
    Bitmap foreground(String src, Bitmap photo) {
        if (src.startsWith("asset:") || !Prefs.on(getContext(), "depth", true)) return null;
        File mf = new File(src + ".mask.png");
        if (!mf.exists() || !mf.canRead()) return null;
        try {
            Bitmap mask = BitmapFactory.decodeFile(mf.getPath());
            if (mask == null) return null;
            int w = photo.getWidth(), hh = photo.getHeight();
            // the photo may have lost its bottom strip (camera stamp): keep the mask's matching top part
            int mh = Math.min(mask.getHeight(), Math.round(mask.getWidth() * (hh / (float) w)));
            Bitmap fg = Bitmap.createBitmap(w, hh, Bitmap.Config.ARGB_8888);
            Canvas k = new Canvas(fg);
            k.drawBitmap(photo, 0, 0, null);
            Paint mp = new Paint(Paint.FILTER_BITMAP_FLAG);
            mp.setColorFilter(new ColorMatrixColorFilter(new float[]{ // grey level becomes the alpha
                0, 0, 0, 0, 0,  0, 0, 0, 0, 0,  0, 0, 0, 0, 0,  1, 0, 0, 0, 0}));
            mp.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
            k.drawBitmap(mask, new Rect(0, 0, mask.getWidth(), mh), new Rect(0, 0, w, hh), mp);
            mask.recycle();
            return fg;
        } catch (Throwable e) { return null; }
    }
    long curSince, fadeStart;
    int index = -1;
    boolean photos, wanted;

    Ambient(Context c) {
        super(c);
        Bitmap noise = Bitmap.createBitmap(160, 160, Bitmap.Config.ARGB_8888);
        Random r = new Random(7);
        for (int y = 0; y < 160; y++) for (int x = 0; x < 160; x++) {
            int v = r.nextInt(256);
            noise.setPixel(x, y, Color.argb(14, v, v, v));
        }
        grain.setShader(new BitmapShader(noise, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT));
    }

    void tint(int c) { tint = c; }

    void run(boolean r) {
        running = r;
        h.removeCallbacks(advance);
        if (r) {
            if (photos && cur == null) h.post(advance);            // nothing shown yet: start now
            else if (photos) h.postDelayed(advance, Math.max(1000, Prefs.interval(getContext()) * 1000L
                - (System.currentTimeMillis() - curSince)));
            invalidate();
        }
    }

    /** Read the photo list again (from onResume): picks up newly pushed photos and settings. */
    void reload() {
        boolean want = Prefs.photos(getContext());
        List<String> l = new ArrayList<>();
        if (want) {
            List<File> fs = new ArrayList<>();
            for (String d : new String[]{SYNCED, OWN}) {
                File dir = getContext().getExternalFilesDir(d);
                File[] in = dir == null ? null : dir.listFiles();
                if (in != null) for (File f : in) if (f.canRead()) fs.add(f); // skip files the TV isn't allowed to read
            }
            for (File f : fs) {
                String n = f.getName().toLowerCase();
                if (n.startsWith("profile.") || n.startsWith("lo_profile.") || n.endsWith(".part") || n.contains(".mask.")) continue; // the avatar and depth masks, not backgrounds
                if (n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") || n.endsWith(".webp")) l.add(f.getAbsolutePath());
            }
            // free landscape photos (Unsplash licence, via Lorem Picsum), fetched once in the background
            if (Prefs.on(getContext(), "freePhotos", true)) {
                File free = getContext().getExternalFilesDir(FREE);
                File[] ff = free == null ? null : free.listFiles();
                if (ff != null) for (File f : ff) if (f.getName().endsWith(".jpg")) l.add(f.getAbsolutePath());
                fetchFree(getContext());
            }
            try {
                String[] a = getContext().getAssets().list("windows");
                // only the views with nothing written on them: most YouTube previews carry "4K", titles or slogans
                if (a != null) for (String s : a) if (s.endsWith(".jpg") && CLEAN_VIEWS.contains(s.replace(".jpg", ""))) l.add("asset:windows/" + s);
            } catch (Exception ignored) {}
        }
        List<String> sorted = new ArrayList<>(sources);
        Collections.sort(sorted);
        Collections.sort(l);
        if (l.equals(sorted) && want == wanted) return;
        boolean wasWanted = wanted;
        wanted = want;
        // your photos woven through the rest: one of yours, then one free photo or view, and so on,
        // so the slideshow is always a mix with yours in every stretch of it
        List<String> own = new ArrayList<>(), rest = new ArrayList<>();
        for (String s : l) (s.startsWith("asset:") || s.contains("/" + FREE + "/") ? rest : own).add(s);
        Collections.shuffle(own);
        Collections.shuffle(rest);
        List<String> mix = new ArrayList<>();
        int a = 0, b = 0;
        while (a < own.size() || b < rest.size()) {
            if (a < own.size()) mix.add(own.get(a++));
            if (b < rest.size()) mix.add(rest.get(b++));
        }
        String showing = index >= 0 && index < sources.size() ? sources.get(index) : null;
        sources.clear(); sources.addAll(mix);
        photos = want && !sources.isEmpty();
        if (!photos) { cur = null; prev = null; Glass.frost = null; Glass.refresh(); invalidate(); return; }
        android.util.Log.i("SteroidAmbient", sources.size() + " photos in the slideshow (" + own.size() + " yours)");
        // new photos arriving (downloads, your folder) never restart the slideshow: it carries on from here
        if (cur != null && wasWanted && showing != null) {
            index = Math.max(-1, sources.indexOf(showing));
            if (running) { h.removeCallbacks(advance); h.postDelayed(advance, Math.max(1000, Prefs.interval(getContext()) * 1000L - (System.currentTimeMillis() - curSince))); }
            return;
        }
        index = -1;
        advance.run();
    }

    final Runnable advance = new Runnable() { public void run() {
        h.removeCallbacks(this);
        if (!photos || sources.isEmpty()) return;
        index = (index + 1) % sources.size();
        final String src = sources.get(index);
        io.execute(() -> {
            Bitmap b = decode(src);
            Bitmap fr = dream ? null : Glass.make(b, getWidth() > 0 ? getWidth() : 1920, getHeight() > 0 ? getHeight() : 1080);
            Bitmap fg = dream && b != null ? foreground(src, b) : null;
            h.post(() -> {
                if (b == null) { // can't read this one: straight on to the next
                    android.util.Log.w("SteroidAmbient", "skipped unreadable " + src);
                    if (sources.size() > 1) h.postDelayed(advance, 300);
                    return;
                }
                if (b != null) {
                    if (!dream) { Glass.frost = fr; Glass.refresh(); }
                    prevFg = curFg; curFg = fg;
                    prev = cur; cur = b;
                    fadeStart = curSince = System.currentTimeMillis();
                    tint(average(b));
                    invalidate();
                }
                if (running) h.postDelayed(advance, Prefs.interval(getContext()) * 1000L);
            });
        });
    }};

    Bitmap decode(String src) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            open(src, o);
            o.inSampleSize = 1;
            while (o.outWidth / (o.inSampleSize * 2) >= 1280) o.inSampleSize *= 2;
            o.inJustDecodeBounds = false;
            o.inPreferredConfig = Bitmap.Config.RGB_565;
            Bitmap b = open(src, o);
            if (b == null || src.startsWith("asset:")) return b;
            return Photos.upright(src, b);
        } catch (Throwable e) { return null; }
    }

    Bitmap open(String src, BitmapFactory.Options o) throws java.io.IOException {
        if (src.startsWith("asset:")) try (InputStream in = getContext().getAssets().open(src.substring(6))) {
            return BitmapFactory.decodeStream(in, null, o);
        }
        return BitmapFactory.decodeFile(src, o);
    }

    static int average(Bitmap b) {
        Bitmap s = Bitmap.createScaledBitmap(b, 8, 8, true);
        long r = 0, g = 0, bl = 0;
        for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) {
            int px = s.getPixel(x, y);
            r += Color.red(px); g += Color.green(px); bl += Color.blue(px);
        }
        if (s != b) s.recycle();
        return Color.rgb((int) (r / 64), (int) (g / 64), (int) (bl / 64));
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        shade.setShader(new LinearGradient(0, 0, 0, h,
            new int[]{0x22000000, 0x00000000, 0x99000000, 0xF2050508},
            new float[]{0f, 0.3f, 0.62f, 1f}, Shader.TileMode.CLAMP));
        // photos need more darkening behind the clock (top) and the rows (bottom)
        // screensaver: full colour, only a light shade at the top behind the clock and weather
        dreamTop.setShader(new LinearGradient(0, 0, 0, h * 0.42f, 0x73000000, 0x00000000, Shader.TileMode.CLAMP));
        photoShade.setShader(new LinearGradient(0, 0, 0, h,
            new int[]{0xCC000000, 0x99000000, 0xD9000000, 0xF7050508},
            new float[]{0f, 0.35f, 0.6f, 1f}, Shader.TileMode.CLAMP));
        // extra scrim on the left, behind the clock, greeting and row labels
        sideShade.setShader(new LinearGradient(0, 0, w, 0,
            new int[]{0x99000000, 0x4D000000, 0x00000000}, new float[]{0f, 0.45f, 0.8f}, Shader.TileMode.CLAMP));
    }

    @Override protected void onDraw(Canvas c) {
        int w = getWidth(), hgt = getHeight();
        c.drawColor(0xFF08080C);
        if (photos && cur != null) {
            long now = System.currentTimeMillis();
            float fade = Math.min(1f, (now - fadeStart) / 1500f);
            if (prev != null && fade < 1f) photo(c, prev, now - fadeStart + Prefs.interval(getContext()) * 1000L, 255, w, hgt);
            photo(c, cur, now - curSince, (int) (255 * fade), w, hgt);
            if (dream) { // screensaver: clock and weather, then each photo's subject in front of them
                c.drawRect(0, 0, w, hgt * 0.42f, dreamTop);
                if (overlay != null) overlay.draw(c, w, hgt);
                if (prevFg != null && fade < 1f) photo(c, prevFg, now - fadeStart + Prefs.interval(getContext()) * 1000L, (int) (255 * (1 - fade)), w, hgt);
                if (curFg != null) photo(c, curFg, now - curSince, (int) (255 * fade), w, hgt);
                if (overlay != null) overlay.drawFront(c, w, hgt); // date and weather always stay readable
            }
            if (fade >= 1f && prev != null) { prev = null; prevFg = null; } // free the old image
            // "Background" slider in the Control centre: how dark the photo is behind the text (45 to 100%)
            if (!dream) {
                photoShade.setAlpha(Math.round(255 * (0.45f + 0.55f * Prefs.sp(getContext()).getInt("bgDim", 100) / 100f)));
                c.drawRect(0, 0, w, hgt, photoShade);
                c.drawRect(0, 0, w, hgt, sideShade);
            }
            boolean moving = fade < 1f || Prefs.drift(getContext());
            if (running && moving) postInvalidateDelayed(83);
            return;
        }
        shown = blend(shown, tint, 0.07f);
        blob(c, w * (0.30f + 0.07f * (float) Math.sin(t * 0.7)), hgt * (0.30f + 0.08f * (float) Math.cos(t * 0.5)), w * 0.62f, shown, 0x78);
        blob(c, w * (0.86f + 0.05f * (float) Math.cos(t * 0.4)), hgt * (0.22f + 0.07f * (float) Math.sin(t * 0.6)), w * 0.5f, second, 0x52);
        blob(c, w * (0.60f + 0.10f * (float) Math.sin(t * 0.3)), hgt * 0.95f, w * 0.55f, blend(shown, 0xFF000000, 0.4f), 0x40);
        c.drawRect(0, 0, w, hgt, shade);
        c.drawRect(0, 0, w, hgt, grain);
        t += 0.014f;
        if (running) postInvalidateDelayed(83);
    }

    /** Centre crop with a slow zoom (about 6% over one interval) when drift is on. */
    void photo(Canvas c, Bitmap b, long age, int alpha, int w, int hgt) {
        float base = Math.max((float) w / b.getWidth(), (float) hgt / b.getHeight());
        float k = Prefs.drift(getContext()) ? 1f + 0.06f * Math.min(1f, age / (Prefs.interval(getContext()) * 1000f + 1500f)) : 1f;
        float s = base * k;
        float dw = b.getWidth() * s, dh = b.getHeight() * s;
        Paint pt = alpha >= 255 ? img : fadePaint;
        pt.setAlpha(Math.max(0, Math.min(255, alpha)));
        c.save();
        c.translate((w - dw) / 2, (hgt - dh) / 2);
        c.scale(s, s);
        c.drawBitmap(b, 0, 0, pt);
        c.restore();
    }

    void blob(Canvas c, float x, float y, float r, int color, int alpha) {
        p.setShader(new RadialGradient(x, y, r, Ui.withAlpha(color, alpha), color & 0x00FFFFFF, Shader.TileMode.CLAMP));
        c.drawCircle(x, y, r, p);
    }

    static int blend(int a, int b, float f) {
        return Color.rgb((int) (Color.red(a) + (Color.red(b) - Color.red(a)) * f),
            (int) (Color.green(a) + (Color.green(b) - Color.green(a)) * f),
            (int) (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * f));
    }
}
