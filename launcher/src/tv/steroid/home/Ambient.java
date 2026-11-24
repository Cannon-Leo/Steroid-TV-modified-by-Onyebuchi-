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
    final Paint shade = new Paint(), photoShade = new Paint(), sideShade = new Paint(), grain = new Paint();
    final Paint img = new Paint(Paint.FILTER_BITMAP_FLAG), fadePaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    int tint = 0xFFE8915A, shown = tint, second = 0xFF5B3CC4;
    float t;
    boolean running;

    // slideshow
    static final String OWN = "photos"; // getExternalFilesDir("photos"), filled from the Mac with adb push
    final List<String> sources = new ArrayList<>();
    final ExecutorService io = Executors.newSingleThreadExecutor();
    final Handler h = new Handler(Looper.getMainLooper());
    Bitmap cur, prev;
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
            if (photos && cur != null) h.postDelayed(advance, Math.max(1000, Prefs.interval(getContext()) * 1000L
                - (System.currentTimeMillis() - curSince)));
            invalidate();
        }
    }

    /** Read the photo list again (from onResume): picks up newly pushed photos and settings. */
    void reload() {
        boolean want = Prefs.photos(getContext());
        List<String> l = new ArrayList<>();
        if (want) {
            File dir = getContext().getExternalFilesDir(OWN);
            File[] fs = dir == null ? null : dir.listFiles();
            if (fs != null) for (File f : fs) {
                String n = f.getName().toLowerCase();
                if (n.startsWith("profile.")) continue;
                if (n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") || n.endsWith(".webp")) l.add(f.getAbsolutePath());
            }
            try {
                String[] a = getContext().getAssets().list("windows");
                if (a != null) for (String s : a) if (s.endsWith(".jpg")) l.add("asset:windows/" + s);
            } catch (Exception ignored) {}
        }
        List<String> sorted = new ArrayList<>(sources);
        Collections.sort(sorted);
        Collections.sort(l);
        if (l.equals(sorted) && want == wanted) return;
        wanted = want;
        // your own photos come first in each loop so new ones show up straight away
        List<String> own = new ArrayList<>(), rest = new ArrayList<>();
        for (String s : l) (s.startsWith("asset:") ? rest : own).add(s);
        Collections.shuffle(own);
        Collections.shuffle(rest);
        sources.clear(); sources.addAll(own); sources.addAll(rest);
        photos = want && !sources.isEmpty();
        if (!photos) { cur = null; prev = null; Glass.frost = null; Glass.refresh(); invalidate(); return; }
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
            Bitmap fr = Glass.make(b, getWidth() > 0 ? getWidth() : 1920, getHeight() > 0 ? getHeight() : 1080);
            h.post(() -> {
                if (b != null) {
                    Glass.frost = fr;
                    Glass.refresh();
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
            return open(src, o);
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
            if (fade >= 1f && prev != null) prev = null; // free the old image
            c.drawRect(0, 0, w, hgt, photoShade);
            c.drawRect(0, 0, w, hgt, sideShade);
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
