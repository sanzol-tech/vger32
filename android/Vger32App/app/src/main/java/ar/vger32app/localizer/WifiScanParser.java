package ar.vger32app.localizer;

import java.util.ArrayList;
import java.util.List;

import ar.vger32app.ui.localizer.LocalizerWifiAdapter;

/*
 * Parses the pipe-separated response from the module /api/wifi-scan endpoint.
 *
 * Line format: SSID|MAC(12 hex)|channel|rssi
 *   e.g. "MyNet|A4C138F2B1D3|06|-61"
 */

public class WifiScanParser {

    private WifiScanParser() {
    }

    public static List<LocalizerWifiAdapter.WifiItem> parse(String text) {
        List<LocalizerWifiAdapter.WifiItem> items = new ArrayList<>();
        if (text == null || text.trim().isEmpty()) return items;

        for (String line : text.split("\n")) {
            line = line.trim();
            if (line.isEmpty()) continue;
            String[] parts = line.split("\\|");
            if (parts.length < 4) continue;
            try {
                items.add(new LocalizerWifiAdapter.WifiItem(
                        parts[0],
                        expandMac(parts[1].trim()),
                        Integer.parseInt(parts[3].trim()),
                        Integer.parseInt(parts[2].trim())));
            } catch (NumberFormatException ignored) {
            }
        }
        return items;
    }

    // --------------------------------------------------------
    // --- INTERNAL -------------------------------------------

    private static String expandMac(String raw) {
        if (raw.length() != 12) return raw;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 12; i += 2) {
            if (i > 0) sb.append(':');
            sb.append(raw, i, i + 2);
        }
        return sb.toString().toUpperCase();
    }
}