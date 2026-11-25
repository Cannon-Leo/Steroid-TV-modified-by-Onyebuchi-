package tv.steroid.home;

import android.content.Context;
import android.content.SharedPreferences;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * TV side of Little Office approvals (see little-office/approvals.py). Pairs this TV with a
 * computer using the code shown on that computer's screen, keeps one device token per computer
 * (60 or 90 days), and signs every request with it. Call the network methods off the main thread.
 */
final class Approvals {
    static class Request { String id, computer, project, tool, summary, detail; long at; }

    static final SecureRandom RNG = new SecureRandom();
    static final java.util.regex.Pattern ID = java.util.regex.Pattern.compile("[0-9a-f]{16}"),
        TOKEN = java.util.regex.Pattern.compile("[0-9a-f]{64}");

    /** Text from the network: cut to size and stripped of control characters before it is shown. */
    static String clean(String s, int max) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length() && b.length() < max; i++) { char ch = s.charAt(i); if (ch >= 32 || ch == '\n') b.append(ch); }
        return b.toString();
    }

    static SharedPreferences sp(Context c) { return c.getSharedPreferences("approvals", Context.MODE_PRIVATE); }

    static String hex(byte[] b) { StringBuilder s = new StringBuilder(); for (byte x : b) s.append(String.format("%02x", x)); return s.toString(); }
    static byte[] unhex(String h) { byte[] b = new byte[h.length() / 2]; for (int i = 0; i < b.length; i++) b[i] = (byte) Integer.parseInt(h.substring(2 * i, 2 * i + 2), 16); return b; }
    static byte[] concat(byte[] a, byte[] b) { byte[] o = new byte[a.length + b.length]; System.arraycopy(a, 0, o, 0, a.length); System.arraycopy(b, 0, o, a.length, b.length); return o; }
    static String random(int n) { byte[] b = new byte[n]; RNG.nextBytes(b); return hex(b); }

    // Diffie Hellman, RFC 3526 group 14 (same as little-office/approvals.py)
    static final java.math.BigInteger DH_P = new java.math.BigInteger("FFFFFFFFFFFFFFFFC90FDAA22168C234C4C6628B80DC1CD129024E088A67CC74020BBEA63B139B22514A08798E3404DDEF9519B3CD3A431B302B0A6DF25F14374FE1356D6D51C245E485B576625E7EC6F44C42E9A637ED6B0BFF5CB6F406B7EDEE386BFB5A899FA5AE9F24117C4B1FE649286651ECE45B3DC2007CB8A163BF0598DA48361C55D39A69163FA8FD24CF5F83655D23DCA3AD961C62F356208552BB9ED529077096966D670C354E4ABC9804F1746C08CA18217C32905E462E36CE3BE39E772C180E86039B2783A2EC07A28FB5C55DF06F4C52C9DE2BCBF6955817183995497CEA956AE515D2261898FA051015728E5A8AACAA68FFFFFFFFFFFFFFFF", 16), DH_G = java.math.BigInteger.valueOf(2);
    static final java.util.regex.Pattern PUB = java.util.regex.Pattern.compile("[0-9a-f]{512}");

    /** A number as exactly 256 big endian bytes (matches Python's to_bytes(256, "big")). */
    static byte[] fixed(java.math.BigInteger x) {
        byte[] b = x.toByteArray(), out = new byte[256];
        int n = Math.min(b.length, 256);
        System.arraycopy(b, b.length - n, out, 256 - n, n);
        return out;
    }

    static byte[] sha256(byte[] a, byte[] b) {
        try { java.security.MessageDigest d = java.security.MessageDigest.getInstance("SHA-256"); d.update(a); d.update(b); return d.digest(); }
        catch (Exception e) { throw new RuntimeException(e); }
    }

    static byte[] hmacBytes(byte[] key, byte[] msg) {
        try {
            Mac m = Mac.getInstance("HmacSHA256");
            m.init(new SecretKeySpec(key, "HmacSHA256"));
            return m.doFinal(msg);
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    static byte[] hmac(byte[] key, String msg) {
        try {
            Mac m = Mac.getInstance("HmacSHA256");
            m.init(new SecretKeySpec(key, "HmacSHA256"));
            return m.doFinal(msg.getBytes("UTF-8"));
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    static String device(Context c) {
        String d = sp(c).getString("device", "");
        if (d.isEmpty()) { d = random(16); sp(c).edit().putString("device", d).apply(); }
        return d;
    }

    static String token(Context c, String computer) {
        long exp = sp(c).getLong("exp_" + computer, 0);
        return System.currentTimeMillis() / 1000 < exp ? sp(c).getString("tok_" + computer, "") : "";
    }
    static boolean paired(Context c, String computer) { return !token(c, computer).isEmpty(); }
    static long daysLeft(Context c, String computer) { return Math.max(0, (sp(c).getLong("exp_" + computer, 0) - System.currentTimeMillis() / 1000) / 86400); }
    static boolean anyPaired(Context c) { for (String k : Office.addr.keySet()) if (paired(c, k)) return true; return false; }

    // ---------- HTTP ----------

    static final class Reply { int code; JSONObject body; String raw = "", nonce = "", sig = ""; }

    static Reply http(String computer, String method, String path, String body, String token, String device) {
        String[] a = Office.addr.get(computer);
        Reply r = new Reply();
        if (a == null) { r.code = -1; return r; }
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL("http://" + a[0] + ":" + a[1] + path).openConnection();
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(6000);
            conn.setRequestMethod(method);
            conn.setRequestProperty("Content-Type", "application/json");
            if (token != null && !token.isEmpty()) {
                String ts = String.valueOf(System.currentTimeMillis() / 1000), nonce = random(8);
                r.nonce = nonce;
                String sig = hex(hmac(unhex(token), method + "\n" + path + "\n" + ts + "\n" + nonce + "\n" + (body == null ? "" : body)));
                conn.setRequestProperty("X-LO-Device", device);
                conn.setRequestProperty("X-LO-Time", ts);
                conn.setRequestProperty("X-LO-Nonce", nonce);
                conn.setRequestProperty("X-LO-Sig", sig);
            }
            if (body != null) {
                conn.setDoOutput(true);
                byte[] b = body.getBytes("UTF-8");
                conn.setFixedLengthStreamingMode(b.length);
                try (OutputStream o = conn.getOutputStream()) { o.write(b); }
            }
            r.code = conn.getResponseCode();
            InputStream in = r.code < 400 ? conn.getInputStream() : conn.getErrorStream();
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            if (in != null) { byte[] b = new byte[8192]; for (int n; (n = in.read(b)) > 0; ) buf.write(b, 0, n); }
            r.raw = buf.toString("UTF-8");
            String sg = conn.getHeaderField("X-LO-Reply");
            r.sig = sg == null ? "" : sg;
            r.body = buf.size() > 0 ? new JSONObject(r.raw) : new JSONObject();
        } catch (Exception e) {
            r.code = -1;
        } finally {
            if (conn != null) conn.disconnect();
        }
        return r;
    }

    static Reply signed(Context c, String computer, String method, String path, String body) {
        return http(computer, method, path, body, token(c, computer), device(c));
    }

    // ---------- pairing ----------

    /** Step 1: ask the computer to show a code. Returns null on success, else a message. */
    static String pairStart(Context c, String computer, int days) {
        String nonce = random(16);
        // a fresh Diffie Hellman secret for this pairing: the code alone never protects the token
        java.math.BigInteger mine = new java.math.BigInteger(256, RNG).setBit(255);
        String pub = String.format("%512s", DH_G.modPow(mine, DH_P).toString(16)).replace(' ', '0');
        try {
            JSONObject b = new JSONObject().put("device", device(c)).put("nonce", nonce)
                .put("name", Prefs.name(c).isEmpty() ? "TV" : Prefs.name(c) + "'s TV").put("days", days).put("pub", pub);
            Reply r = http(computer, "POST", "/pair/start", b.toString(), null, null);
            if (r.code == 200) {
                String theirs = r.body.optString("pub");
                if (!PUB.matcher(theirs).matches()) return "This computer needs the newest Little Office (run scripts/buchi.sh update, or the Windows installer).";
                java.math.BigInteger tb = new java.math.BigInteger(theirs, 16);
                if (tb.compareTo(java.math.BigInteger.ONE) <= 0 || tb.compareTo(DH_P.subtract(java.math.BigInteger.ONE)) >= 0) return "The computer sent something unexpected. Pairing cancelled.";
                sp(c).edit().putString("nonce_" + computer, nonce).putString("dh_" + computer, hex(fixed(tb.modPow(mine, DH_P)))).apply();
                return null;
            }
            return r.code == -1 ? "Couldn't reach " + computer + "." : "This computer needs the newest Little Office (run little-office/approvals.sh on).";
        } catch (Exception e) { return "Couldn't start pairing."; }
    }

    /** Step 2: prove the code without sending it, receive the device token. Null on success. */
    static String pairFinish(Context c, String computer, String code) {
        String dev = device(c), nonce = sp(c).getString("nonce_" + computer, ""), dh = sp(c).getString("dh_" + computer, "");
        if (dh.length() != 512) return "Start pairing again.";
        try {
            byte[] key = sha256(unhex(dh), code.getBytes("UTF-8"));
            String proof = hex(hmac(key, "pair|" + dev + "|" + nonce));
            Reply r = http(computer, "POST", "/pair/finish", new JSONObject().put("device", dev).put("proof", proof).toString(), null, null);
            if (r.code == 200) {
                String tok = r.body.optString("token");
                long exp = r.body.optLong("expires");
                long now = System.currentTimeMillis() / 1000;
                if (!TOKEN.matcher(tok).matches() || exp < now || exp > now + 91L * 86400) return "The computer sent something unexpected. Pairing cancelled.";
                byte[] wrapped = unhex(tok), k = hmac(key, "wrap|" + dev + "|" + nonce), t = new byte[32];
                for (int i = 0; i < 32; i++) t[i] = (byte) (wrapped[i] ^ k[i]);
                sp(c).edit().putString("tok_" + computer, hex(t)).putLong("exp_" + computer, r.body.getLong("expires"))
                    .remove("nonce_" + computer).remove("dh_" + computer).apply();
                return null;
            }
            if (r.code == 401) return "That code isn't right. " + r.body.optInt("left") + " tries left.";
            if (r.code == 403) { sp(c).edit().remove("dh_" + computer).apply(); return "Too many wrong codes. Start again."; }
            if (r.code == 410) return "The code expired. Start again.";
            return "Couldn't reach " + computer + ".";
        } catch (Exception e) { return "Pairing failed."; }
    }

    static void forget(Context c, String computer) {
        if (paired(c, computer)) signed(c, computer, "POST", "/forget", "{}");
        sp(c).edit().remove("tok_" + computer).remove("exp_" + computer).apply();
    }

    // ---------- requests ----------

    static List<Request> pending(Context c) {
        List<Request> out = new ArrayList<>();
        for (String comp : new ArrayList<>(Office.addr.keySet())) {
            if (!paired(c, comp)) continue;
            Reply n = http(comp, "GET", "/approvals/count", null, null, null);
            if (n.code != 200 || n.body.optInt("n") == 0) continue;
            Reply r = signed(c, comp, "GET", "/approvals", null);
            if (r.code == 401) { sp(c).edit().remove("tok_" + comp).remove("exp_" + comp).apply(); continue; } // revoked on the computer
            // the list must be signed for this TV and this request, or it is not shown (nothing on the
            // network can change what you are approving)
            if (r.code != 200) continue;
            String want = hex(hmacBytes(unhex(token(c, comp)), concat(("reply\n/approvals\n" + r.nonce + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8), r.raw.getBytes(java.nio.charset.StandardCharsets.UTF_8))));
            if (!java.security.MessageDigest.isEqual(want.getBytes(), r.sig.getBytes())) {
                android.util.Log.w("SteroidOffice", "unsigned or altered approval list from " + comp + ", ignored");
                continue;
            }
            JSONArray a = r.body == null ? null : r.body.optJSONArray("requests");
            for (int i = 0; a != null && i < a.length() && i < 20; i++) {
                JSONObject o = a.optJSONObject(i);
                Request q = new Request();
                if (o == null || !ID.matcher(o.optString("id")).matches()) continue; // anything malformed is dropped
                q.id = o.optString("id"); q.computer = comp; q.project = clean(o.optString("project"), 60);
                q.tool = clean(o.optString("tool"), 80); q.summary = clean(o.optString("summary"), 300); q.detail = clean(o.optString("detail"), 400);
                q.at = (long) o.optDouble("at");
                out.add(q);
            }
        }
        return out;
    }

    static boolean decide(Context c, Request q, boolean allow) {
        try {
            Reply r = signed(c, q.computer, "POST", "/approvals/" + q.id, new JSONObject().put("decision", allow ? "allow" : "deny").toString());
            return r.code == 200;
        } catch (Exception e) { return false; }
    }
}
