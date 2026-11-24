package tv.steroid.home;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Little Office client: finds the Mac's status service (mDNS "_littleoffice._tcp") and reads a
 * glance of Claude Code / Codex activity. Shared by the home card, the glance panel and the
 * notification service.
 */
final class Office {
    static final long STALE_MS = 75_000; // no answer for this long: show "last seen", never "working"

    static class Session {
        String agent, project, state, text, computer;
        long at;      // seconds since epoch, that computer's clock
        long ageSec;  // how long ago, measured on that computer (clocks may differ)
    }

    static class Event {
        long id;
        String agent, project, kind, text, computer;
    }

    static List<Session> sessions = new ArrayList<>();
    static List<Event> events = new ArrayList<>();
    static List<String> pinned = new ArrayList<>();
    /** agent -> {installed 1/0, note, computer}: merged across every computer that answers. */
    static java.util.Map<String, String[]> agents = new java.util.LinkedHashMap<>();
    /** computer name -> last time it answered (ms). */
    static java.util.Map<String, Long> computers = new java.util.LinkedHashMap<>();
    static long lastOk;      // TV clock, ms
    static double macNow;    // kept for older callers
    static boolean everConnected;

    // ---------- Discovery: every computer running Little Office (Mac, Windows PC...) ----------

    /** service name -> {host, port} */
    static final java.util.Map<String, String[]> hosts = new java.util.LinkedHashMap<>();
    static NsdManager.DiscoveryListener discovery;

    static void discover(Context c) {
        SharedPreferences p = c.getSharedPreferences("office", Context.MODE_PRIVATE);
        synchronized (hosts) {
            if (hosts.isEmpty()) try {
                JSONObject saved = new JSONObject(p.getString("hosts", "{}"));
                for (java.util.Iterator<String> it = saved.keys(); it.hasNext(); ) {
                    String k = it.next();
                    JSONArray v = saved.getJSONArray(k);
                    hosts.put(k, new String[]{v.getString(0), v.getString(1)});
                }
                String old = p.getString("host", null); // from the one-computer version
                if (hosts.isEmpty() && old != null) hosts.put("Mac", new String[]{old, String.valueOf(p.getInt("port", 8765))});
            } catch (Exception ignored) {}
        }
        if (discovery != null) return;
        NsdManager nsd = (NsdManager) c.getSystemService(Context.NSD_SERVICE);
        discovery = new NsdManager.DiscoveryListener() {
            public void onStartDiscoveryFailed(String t, int e) { discovery = null; }
            public void onStopDiscoveryFailed(String t, int e) {}
            public void onDiscoveryStarted(String t) {}
            public void onDiscoveryStopped(String t) {}
            public void onServiceLost(NsdServiceInfo s) {}
            public void onServiceFound(NsdServiceInfo s) {
                // Android resolves one service at a time; retry a little later if it is busy
                resolve(nsd, s, p, 0);
            }
        };
        try { nsd.discoverServices("_littleoffice._tcp", NsdManager.PROTOCOL_DNS_SD, discovery); }
        catch (Exception e) { discovery = null; }
    }

    static void resolve(NsdManager nsd, NsdServiceInfo s, SharedPreferences p, int tries) {
        try {
            nsd.resolveService(s, new NsdManager.ResolveListener() {
                public void onResolveFailed(NsdServiceInfo x, int e) {
                    if (e == NsdManager.FAILURE_ALREADY_ACTIVE && tries < 5)
                        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> resolve(nsd, s, p, tries + 1), 1500);
                }
                public void onServiceResolved(NsdServiceInfo x) {
                    synchronized (hosts) {
                        hosts.put(x.getServiceName(), new String[]{x.getHost().getHostAddress(), String.valueOf(x.getPort())});
                        JSONObject o = new JSONObject();
                        try { for (java.util.Map.Entry<String, String[]> e : hosts.entrySet()) o.put(e.getKey(), new JSONArray().put(e.getValue()[0]).put(e.getValue()[1])); }
                        catch (Exception ignored) {}
                        p.edit().putString("hosts", o.toString()).apply();
                    }
                }
            });
        } catch (Exception ignored) {}
    }

    // ---------- Polling (call off the main thread): ask every computer, merge the answers ----------

    static synchronized boolean poll(Context c) {
        discover(c);
        List<String[]> targets = new ArrayList<>();
        synchronized (hosts) { for (java.util.Map.Entry<String, String[]> e : hosts.entrySet()) targets.add(new String[]{e.getKey(), e.getValue()[0], e.getValue()[1]}); }
        if (targets.isEmpty()) return false;
        List<Session> ss = new ArrayList<>();
        List<Event> es = new ArrayList<>();
        List<String> pins = new ArrayList<>();
        java.util.Map<String, String[]> ag = new java.util.LinkedHashMap<>();
        boolean any = false;
        for (String[] t : targets) {
            JSONObject o = fetch(t[1], t[2]);
            if (o == null) continue;
            any = true;
            String comp = o.optString("computer", t[0].replace("Little Office", "").trim());
            if (comp.isEmpty()) comp = "Computer";
            computers.put(comp, System.currentTimeMillis());
            double now = o.optDouble("generated_at");
            macNow = now;
            try {
                JSONArray a = o.optJSONArray("sessions");
                for (int i = 0; a != null && i < a.length(); i++) {
                    JSONObject s = a.getJSONObject(i);
                    Session x = new Session();
                    x.agent = s.optString("agent"); x.project = s.optString("project");
                    x.state = s.optString("state"); x.text = s.optString("text"); x.at = (long) s.optDouble("at");
                    x.ageSec = (long) Math.max(0, now - x.at);
                    x.computer = comp;
                    ss.add(x);
                }
                JSONArray e = o.optJSONArray("events");
                for (int i = 0; e != null && i < e.length(); i++) {
                    JSONObject s = e.getJSONObject(i);
                    Event x = new Event();
                    x.id = s.optLong("id"); x.agent = s.optString("agent"); x.project = s.optString("project");
                    x.kind = s.optString("kind"); x.text = s.optString("text"); x.computer = comp;
                    es.add(x);
                }
                JSONArray pa = o.optJSONArray("pinned");
                for (int i = 0; pa != null && i < pa.length(); i++) if (!pins.contains(pa.getString(i))) pins.add(pa.getString(i));
                JSONArray aa = o.optJSONArray("agents");
                for (int i = 0; aa != null && i < aa.length(); i++) {
                    JSONObject x = aa.getJSONObject(i);
                    String name = x.optString("name");
                    boolean inst = x.optBoolean("installed");
                    String[] prev = ag.get(name);
                    // an agent that exists on any computer counts; keep the note from where it exists
                    if (prev == null || ("0".equals(prev[0]) && inst))
                        ag.put(name, new String[]{inst ? "1" : "0", x.optString("note"), comp});
                }
            } catch (Exception ignored) {}
        }
        if (!any) return false;
        java.util.Collections.sort(ss, (x, y) -> Long.compare(x.ageSec, y.ageSec));
        agents = ag;
        sessions = ss; events = es; pinned = pins;
        lastOk = System.currentTimeMillis();
        everConnected = true;
        c.getSharedPreferences("office", Context.MODE_PRIVATE).edit().putLong("lastOk", lastOk).apply();
        return true;
    }

    static JSONObject fetch(String host, String port) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL("http://" + host + ":" + port + "/status").openConnection();
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(5000);
            InputStream in = conn.getInputStream();
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] b = new byte[16384];
            for (int n; (n = in.read(b)) > 0; ) buf.write(b, 0, n);
            return new JSONObject(buf.toString("UTF-8"));
        } catch (Exception ex) {
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /** Names of the computers that answered in the last few minutes. */
    static List<String> liveComputers() {
        List<String> l = new ArrayList<>();
        for (java.util.Map.Entry<String, Long> e : computers.entrySet())
            if (System.currentTimeMillis() - e.getValue() < STALE_MS) l.add(e.getKey());
        return l;
    }

    // ---------- Wording ----------

    static boolean connected() { return System.currentTimeMillis() - lastOk < STALE_MS; }

    static String lastSeen(Context c) {
        long t = lastOk > 0 ? lastOk : c.getSharedPreferences("office", Context.MODE_PRIVATE).getLong("lastOk", 0);
        if (t == 0) return "Waiting for the computer";
        long min = (System.currentTimeMillis() - t) / 60000;
        return min < 1 ? "Last seen just now" : min < 60 ? "Last seen " + min + (min == 1 ? " minute ago" : " minutes ago")
            : "Last seen " + (min / 60) + (min / 60 == 1 ? " hour ago" : " hours ago");
    }

    /** Agents in display order: the ones on this Mac first. */
    static List<String> order() {
        List<String> a = new ArrayList<>(), b = new ArrayList<>();
        for (String n : new String[]{"Gemini", "Claude", "Codex"}) {
            String[] i = agents.get(n);
            (i == null || "1".equals(i[0]) ? a : b).add(n);
        }
        a.addAll(b);
        return a;
    }

    static boolean installed(String agent) { String[] i = agents.get(agent); return i == null || "1".equals(i[0]); }

    /** "Codex · 2 working · 1 needs you", "Gemini · Last: … · 13 h ago" or "Codex · Not on this computer". */
    static String line(String agent) {
        int working = 0, needs = 0;
        for (Session s : sessions) if (s.agent.equals(agent)) {
            if (s.state.equals("working")) working++;
            if (s.state.equals("needs_you")) needs++;
        }
        StringBuilder b = new StringBuilder(agent);
        String[] info = agents.get(agent);
        if (working == 0 && needs == 0) {
            if (info != null && !info[1].isEmpty()) return b.append(" · ").append(info[1]).toString();
            return b.append(" · All quiet").toString();
        }
        if (working > 0) b.append(" · ").append(working).append(" working");
        if (needs > 0) b.append(" · ").append(needs).append(" needs you");
        return b.toString();
    }

    /** The most interesting thing happening, e.g. "Forma — rendering video." */
    static String highlight() {
        Session best = null;
        for (Session s : sessions) if (rank(s) > rank(best)) best = s;
        if (best == null || rank(best) < 2) return null;
        return best.project + " — " + best.text;
    }

    static int rank(Session s) {
        if (s == null) return -1;
        switch (s.state) { case "needs_you": return 3; case "working": return 2; case "finished": return 1; default: return 0; }
    }

    static String lower(String t) {
        if (t == null || t.isEmpty()) return "";
        return t.length() > 1 && Character.isUpperCase(t.charAt(0)) && !Character.isUpperCase(t.charAt(1))
            ? Character.toLowerCase(t.charAt(0)) + t.substring(1) : t;
    }

    static String ago(Session s) {
        long min = s.ageSec / 60;
        return min < 1 ? "just now" : min < 60 ? min + " min ago" : (min / 60) + " h ago";
    }

    static String stateLabel(Session s) {
        switch (s.state) {
            case "working": return "Working";
            case "needs_you": return "Needs you";
            case "finished": return "Finished · " + ago(s);
            default: return "Quiet · " + ago(s);
        }
    }

    static int stateColor(String state) {
        switch (state) {
            case "working": return 0xFF6FD49B;
            case "needs_you": return 0xFFFFB454;
            case "finished": return 0xFF8FB8FF;
            default: return 0x80FFFFFF;
        }
    }
}
