package tv.steroid.home;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.PlaybackState;
import android.net.Uri;
import java.io.File;
import java.io.FileOutputStream;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * "Recently played on this TV". Android hides every app's own Continue Watching data from launchers
 * that are not part of the system, so Steroid keeps its own history instead: whenever YouTube,
 * YouTube Music, Spotify, Netflix, Prime Video or any player announces what it is playing (the same
 * signal the music widget uses), the title, artwork and a link back are saved here. Stored on the TV
 * only, capped at 80 items, with small thumbnails (about 20 KB each).
 */
final class History {
    static final int MAX = 80;
    /** Told (on the main thread) whenever something new is saved, so the rows update while you watch. */
    static volatile Runnable listener;
    static final android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());

    static File dir(Context c) { File d = new File(c.getFilesDir(), "history"); d.mkdirs(); return d; }
    static File index(Context c) { return new File(dir(c), "index.json"); }

    static synchronized JSONArray load(Context c) {
        try (java.io.FileInputStream in = new java.io.FileInputStream(index(c))) {
            return new JSONArray(new String(Shelf.readAll(in), "UTF-8"));
        } catch (Exception e) { return new JSONArray(); }
    }

    /** Called from the background listener when a session plays something. */
    static synchronized void record(Context c, MediaController m) {
        try {
            PlaybackState st = m.getPlaybackState();
            if (st == null || st.getState() != PlaybackState.STATE_PLAYING) return;
            MediaMetadata md = m.getMetadata();
            if (md == null) return;
            String pkg = m.getPackageName();
            if (pkg.equals(c.getPackageName())) return;
            String title = md.getString(MediaMetadata.METADATA_KEY_TITLE);
            if (title == null || title.trim().isEmpty()) title = md.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE);
            if (title == null || title.trim().isEmpty()) return;
            String sub = md.getString(MediaMetadata.METADATA_KEY_ARTIST);
            if (sub == null) sub = md.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE);
            String id = md.getString(MediaMetadata.METADATA_KEY_MEDIA_ID);

            JSONArray old = load(c), out = new JSONArray();
            String key = pkg + "|" + title;
            String thumb = "h" + Integer.toHexString(key.hashCode()) + ".jpg";
            Bitmap art = md.getBitmap(MediaMetadata.METADATA_KEY_ART);
            if (art == null) art = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
            if (art == null) art = md.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);
            File tf = new File(dir(c), thumb);
            if (art != null) {
                float s = Math.min(1f, 360f / Math.max(1, art.getWidth()));
                Bitmap small = s < 1f ? Bitmap.createScaledBitmap(art, Math.round(art.getWidth() * s), Math.round(art.getHeight() * s), true) : art;
                try (FileOutputStream fo = new FileOutputStream(tf)) { small.compress(Bitmap.CompressFormat.JPEG, 82, fo); }
            }
            JSONObject e = new JSONObject();
            e.put("pkg", pkg).put("title", title).put("sub", sub == null ? "" : sub).put("id", id == null ? "" : id)
                .put("at", System.currentTimeMillis()).put("thumb", tf.exists() ? Uri.fromFile(tf).toString() : "")
                .put("intent", link(pkg, id).toUri(Intent.URI_INTENT_SCHEME));
            out.put(e);
            for (int i = 0; i < old.length() && out.length() < MAX; i++) {
                JSONObject o = old.getJSONObject(i);
                if (key.equals(o.optString("pkg") + "|" + o.optString("title"))) continue; // newest copy wins
                out.put(o);
            }
            // drop thumbnails nobody points to any more
            java.util.Set<String> keep = new java.util.HashSet<>();
            for (int i = 0; i < out.length(); i++) keep.add(Uri.parse(out.getJSONObject(i).optString("thumb")).getLastPathSegment());
            File[] fs = dir(c).listFiles();
            if (fs != null) for (File f : fs) if (f.getName().startsWith("h") && !keep.contains(f.getName())) f.delete();
            try (FileOutputStream fo = new FileOutputStream(index(c))) { fo.write(out.toString().getBytes("UTF-8")); }
            Runnable l = listener;
            if (l != null) main.post(l);
        } catch (Throwable ignored) {}
    }

    /** Hold OK, "Remove": one item (title), or an app's "Opened" card (title null: hides it until you use it again). */
    static synchronized void remove(Context c, String pkg, String title) {
        try {
            if (title == null) {
                c.getSharedPreferences("history", 0).edit().putLong("hide_" + pkg, System.currentTimeMillis()).apply();
                return;
            }
            JSONArray old = load(c), out = new JSONArray();
            for (int i = 0; i < old.length(); i++) {
                JSONObject o = old.getJSONObject(i);
                if (pkg.equals(o.optString("pkg")) && title.equals(o.optString("title"))) continue;
                out.put(o);
            }
            try (FileOutputStream fo = new FileOutputStream(index(c))) { fo.write(out.toString().getBytes("UTF-8")); }
        } catch (Throwable ignored) {}
    }

    /** Hold OK, "Clear Recently played": everything goes, and apps opened before now stay hidden. */
    static synchronized void clear(Context c) {
        try {
            File[] fs = dir(c).listFiles();
            if (fs != null) for (File f : fs) f.delete();
            c.getSharedPreferences("history", 0).edit().putLong("cleared", System.currentTimeMillis()).apply();
        } catch (Throwable ignored) {}
    }

    /** An app's "Opened" card stays hidden if it was removed or cleared after you last used it. */
    static boolean hidden(Context c, String pkg, long lastUsed) {
        android.content.SharedPreferences sp = c.getSharedPreferences("history", 0);
        return lastUsed <= Math.max(sp.getLong("cleared", 0), sp.getLong("hide_" + pkg, 0));
    }

    /** The best link back: the exact video or track when the app shares its id, otherwise the app itself. */
    static Intent link(String pkg, String id) {
        if (id != null && !id.isEmpty()) {
            if (pkg.equals("com.google.android.youtube.tv") && id.matches("[A-Za-z0-9_-]{11}"))
                return new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=" + id)).setPackage(pkg);
            if (pkg.equals("com.google.android.youtube.tvmusic") && id.matches("[A-Za-z0-9_-]{11}"))
                return new Intent(Intent.ACTION_VIEW, Uri.parse("https://music.youtube.com/watch?v=" + id)).setPackage(pkg);
            if (id.startsWith("spotify:"))
                return new Intent(Intent.ACTION_VIEW, Uri.parse(id)).setPackage(pkg);
        }
        return new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER).setPackage(pkg);
    }
}
