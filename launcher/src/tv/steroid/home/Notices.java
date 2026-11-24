package tv.steroid.home;

import android.content.Context;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Every Little Office notice the TV received (needs you, finished), kept on the TV so you can read the
 * ones you missed in the notification centre, open them, or clear them. Newest first, at most 100.
 */
final class Notices {
    static final class Item { long id, at; String agent, project, kind, text, computer; boolean read; }
    static final List<Item> items = new ArrayList<>();
    static boolean loaded;
    static volatile Runnable changed; // the bell's badge listens

    static File file(Context c) { return new File(c.getFilesDir(), "notices.json"); }

    static synchronized void load(Context c) {
        if (loaded) return;
        loaded = true;
        try {
            JSONArray a = new JSONArray(new String(java.nio.file.Files.readAllBytes(file(c).toPath()), "UTF-8"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Item x = new Item();
                x.id = o.optLong("id"); x.at = o.optLong("at"); x.agent = o.optString("agent"); x.project = o.optString("project");
                x.kind = o.optString("kind"); x.text = o.optString("text"); x.computer = o.optString("computer"); x.read = o.optBoolean("read");
                items.add(x);
            }
        } catch (Exception ignored) {}
    }

    static synchronized void save(Context c) {
        try {
            JSONArray a = new JSONArray();
            for (Item x : items) a.put(new JSONObject().put("id", x.id).put("at", x.at).put("agent", x.agent).put("project", x.project)
                .put("kind", x.kind).put("text", x.text).put("computer", x.computer).put("read", x.read));
            try (FileOutputStream fo = new FileOutputStream(file(c))) { fo.write(a.toString().getBytes("UTF-8")); }
        } catch (Exception ignored) {}
        Runnable l = changed;
        if (l != null) History.main.post(l);
    }

    static synchronized void add(Context c, Office.Event e) {
        load(c);
        for (Item x : items) if (x.id == e.id && x.project.equals(e.project)) return;
        Item x = new Item();
        x.id = e.id; x.at = System.currentTimeMillis(); x.agent = e.agent; x.project = e.project; x.kind = e.kind;
        x.text = Approvals.clean(e.text, 300); x.computer = e.computer == null ? "" : e.computer;
        items.add(0, x);
        while (items.size() > 100) items.remove(items.size() - 1);
        save(c);
    }

    static synchronized int unread(Context c) { load(c); int n = 0; for (Item x : items) if (!x.read) n++; return n; }
    static synchronized void readAll(Context c) { load(c); for (Item x : items) x.read = true; save(c); }
    static synchronized void remove(Context c, Item x) { items.remove(x); save(c); }
    static synchronized void clear(Context c) { items.clear(); save(c); }
    static synchronized List<Item> list(Context c) { load(c); return new ArrayList<>(items); }
}
