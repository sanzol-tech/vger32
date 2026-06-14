package ar.vger32app.config;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.preference.PreferenceManager;
import ar.vger32app.config.safe.SecurePreferencesManager;
import ar.vger32app.logger.LogManager;
import ar.vger32app.scrambler.Scrambler;
import java.util.LinkedHashMap;
import java.util.Map;

/*
 * Parses and applies a VG1 configuration QR.
 * Format:  Base64( Scrambler.encode( "VG1" \x1F key:value \x1F key:value ... ) )
 * Empty value → clears the preference.  Absent key → not touched.
 */
public final class QrConfigParser {

    public  static final char   SEP     = '\u001F';
    public  static final String VERSION = "VG1";
    private static final String QR_KEY  = "eU8iA4oF6mN1rB7c";

    private QrConfigParser() {}

    // --------------------------------------------------------
    // --- PRIVATE --------------------------------------------

    private static String deobfuscate(String b64) {
        byte[] decoded = android.util.Base64.decode(b64, android.util.Base64.NO_WRAP);
        byte[] plain   = Scrambler.decode(decoded, QR_KEY);
        if (plain == null || plain.length == 0) throw new IllegalArgumentException("empty payload");
        return new String(plain, java.nio.charset.StandardCharsets.UTF_8);
    }

    // --------------------------------------------------------
    // --- PUBLIC API -----------------------------------------

    /** Returns null if raw is not a valid VG1 payload. */
    public static Map<String, String> parse(String raw) {
        if (raw == null) return null;
        try { raw = deobfuscate(raw); } catch (Exception e) { return null; }
        String[] parts = raw.split(String.valueOf(SEP), -1);
        if (parts.length < 2 || !VERSION.equals(parts[0])) return null;
        Map<String, String> out = new LinkedHashMap<>();
        for (int i = 1; i < parts.length; i++) {
            String[] kv = parts[i].split(":", 2);
            if (kv.length == 2) out.put(kv[0], kv[1]);
        }
        return out.isEmpty() ? null : out;
    }

    public static void apply(Context ctx, Map<String, String> changes) {
        SharedPreferences.Editor ed  = PreferenceManager.getDefaultSharedPreferences(ctx).edit();
        SecurePreferencesManager spm = SecurePreferencesManager.getInstance(ctx);
        try {
            for (Map.Entry<String, String> e : changes.entrySet()) {
                String k = e.getKey(), v = e.getValue();
                switch (k) {
                    case "mqtt_broker_host":    if (v.isEmpty()) ed.remove(k); else ed.putString(k, v);  break;
                    case "mqtt_broker_port":    if (v.isEmpty()) ed.remove(k); else ed.putString(k, v);  break;
                    case "unlock_code_enabled": ed.putBoolean(k, "true".equals(v));                       break;
                    case "api_key":             spm.saveDefaultApiKey(v.isEmpty() ? null : v);            break;
                    case "scrambler_key":       spm.saveDefaultScramblerKey(v.isEmpty() ? null : v);      break;
                }
            }
            ed.apply();
        } catch (Exception e) {
            LogManager.APP_LOGGER.error("QrConfigParser", "apply failed: " + e.getMessage());
            throw e;
        }
    }

    public static String summary(Map<String, String> changes) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : changes.entrySet()) {
            boolean sensitive = e.getKey().equals("api_key") || e.getKey().equals("scrambler_key");
            String  display   = e.getValue().isEmpty() ? "(clear)" : sensitive ? "***" : e.getValue();
            sb.append("  • ").append(e.getKey()).append(": ").append(display).append("\n");
        }
        return sb.toString().trim();
    }
}