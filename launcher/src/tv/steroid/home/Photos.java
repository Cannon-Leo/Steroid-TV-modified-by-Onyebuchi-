package tv.steroid.home;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.util.Log;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Your photos and profile picture, straight from your computer over the home network (Little Office
 * serves the folder you chose, to paired TVs only, with signed requests). The TV writes them into its
 * own folder itself, so nothing depends on copying files over adb. Also: phone photos are turned
 * upright, and the "shot on" stamp some phones print in the bottom corner is trimmed off.
 */
final class Photos {
    static final String TAG = "SteroidPhotos";
    static volatile Runnable changed; // the home screen refreshes avatar and slideshow
    static volatile boolean syncing;

    /** Sync from every paired computer. Off the main thread. */
    static void sync(Context c) {
        if (syncing) return;
        syncing = true;
        try {
            // a folder the TV creates itself: always writable and readable (a folder made over adb may not be)
            File dir = c.getExternalFilesDir(Ambient.SYNCED);
            if (dir == null) return;
            boolean any = false, touched = false;
            Set<String> keep = new HashSet<>();
            for (String comp : new java.util.ArrayList<>(Office.addr.keySet())) {
                if (!Approvals.paired(c, comp)) continue;
                Approvals.Reply r = Approvals.signed(c, comp, "GET", "/photos", null);
                if (r.code != 200 || r.body == null) { Log.i(TAG, comp + ": no photo list (" + r.code + ")"); continue; }
                any = true;
                String nm = r.body.optString("name").trim(); // your name, until you set one on the TV
                if (nm.matches("[\\p{L} .'’\\-]{1,40}")) Prefs.sp(c).edit().putString("remoteName", nm).apply();
                JSONArray a = r.body.optJSONArray("photos");
                for (int i = 0; a != null && i < a.length() && i < 300; i++) {
                    JSONObject o = a.optJSONObject(i);
                    String name = o == null ? "" : o.optString("name");
                    long size = o == null ? 0 : o.optLong("size");
                    if (!name.matches("[\\w .()\\-]{1,80}\\.(?i)(jpg|jpeg|png|webp)") || size <= 0 || size > 25_000_000) continue;
                    String local = "lo_" + name.replaceAll("[^\\w.\\-]", "_");
                    keep.add(local);
                    File out = new File(dir, local);
                    if (out.exists() && out.length() == size) continue;
                    if (download(c, comp, i, out, size)) { touched = true; Log.i(TAG, "got " + name); }
                }
            }
            if (any) { // photos removed from the folder go from the TV too
                File[] fs = dir.listFiles();
                if (fs != null) for (File f : fs) if (f.getName().startsWith("lo_") && !keep.contains(f.getName())) { f.delete(); touched = true; }
            }
            Runnable r = changed;
            if (touched && r != null) History.main.post(r);
        } catch (Exception e) {
            Log.w(TAG, "sync failed", e);
        } finally { syncing = false; }
    }

    static boolean download(Context c, String comp, int index, File out, long size) {
        String[] a = Office.addr.get(comp);
        if (a == null) return false;
        String path = "/photo/" + index, tok = Approvals.token(c, comp);
        File tmp = new File(out.getPath() + ".part");
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL("http://" + a[0] + ":" + a[1] + path).openConnection();
            conn.setConnectTimeout(4000);
            conn.setReadTimeout(20000);
            String ts = String.valueOf(System.currentTimeMillis() / 1000), nonce = Approvals.random(8);
            conn.setRequestProperty("X-LO-Device", Approvals.device(c));
            conn.setRequestProperty("X-LO-Time", ts);
            conn.setRequestProperty("X-LO-Nonce", nonce);
            conn.setRequestProperty("X-LO-Sig", Approvals.hex(Approvals.hmac(Approvals.unhex(tok), "GET\n" + path + "\n" + ts + "\n" + nonce + "\n")));
            if (conn.getResponseCode() != 200) return false;
            long n = 0;
            try (InputStream in = conn.getInputStream(); FileOutputStream fo = new FileOutputStream(tmp)) {
                byte[] b = new byte[65536];
                for (int k; (k = in.read(b)) > 0; ) { fo.write(b, 0, k); n += k; if (n > size) throw new java.io.IOException("too long"); }
            }
            if (n != size) { tmp.delete(); return false; }
            return tmp.renameTo(out);
        } catch (Exception e) {
            tmp.delete();
            return false;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /** Upright (EXIF), and without the phone's "shot on" stamp at the bottom. */
    static Bitmap upright(String path, Bitmap b) {
        try {
            ExifInterface x = new ExifInterface(path);
            int o = x.getAttributeInt(ExifInterface.TAG_ORIENTATION, 1);
            String make = String.valueOf(x.getAttribute(ExifInterface.TAG_MAKE)).toLowerCase();
            int deg = o == 6 ? 90 : o == 3 ? 180 : o == 8 ? 270 : 0;
            if (deg != 0) {
                Matrix m = new Matrix();
                m.postRotate(deg);
                b = Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), m, true);
            }
            // Xiaomi, Redmi and POCO phones print a "shot on" line in the bottom left corner
            if (make.contains("xiaomi") || make.contains("redmi") || make.contains("poco")) {
                int h = Math.round(b.getHeight() * 0.92f);
                b = Bitmap.createBitmap(b, 0, 0, b.getWidth(), h);
            }
        } catch (Exception ignored) {}
        return b;
    }
}
