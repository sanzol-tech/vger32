package ar.vger32app.localizer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import ar.vger32app.logger.LogManager;

/*
 * Serializes and parses the firmware WiFi fingerprint wire format.
 *
 * Header line: >NAME(padded to NAME_MAX_LEN chars)TSMIN(8 digits)
 *   e.g. ">LAB1  00028394"
 * Data line:   MAC(12 hex) + CH(2 digits) + RSSI_ABS(3 digits)
 *   e.g. "A4C138F2B1D306061"
 */

public class FingerprintFormat {

    private static final String LOG_TAG = "FingerprintFormat";

    private FingerprintFormat() {
    }

    // --------------------------------------------------------
    // --- SERIALIZE ------------------------------------------

    public static String serialize(List<Waypoint> waypoints) {
        StringBuilder sb = new StringBuilder();
        for (Waypoint wp : waypoints) {
            appendSection(sb, wp);
        }
        return sb.toString();
    }

    private static void appendSection(StringBuilder sb, Waypoint wp) {
        long tsMinutes = wp.getTimestamp() / 1000L / 60L;
        sb.append(String.format(Locale.ROOT,
                ">%-" + Waypoint.NAME_MAX_LEN + "s%08d\n", wp.getName(), tsMinutes));
        for (WifiNetwork network : wp.getNetworks()) {
            sb.append(network.toFirmwareLine()).append('\n');
        }
    }

    // --------------------------------------------------------
    // --- PARSE ----------------------------------------------

    public static List<Waypoint> parse(String raw) {
        List<Waypoint> result = new ArrayList<>();
        if (raw == null || raw.trim().isEmpty()) return result;

        String currentName = null;
        long currentTs = 0;
        List<WifiNetwork> currentNetworks = new ArrayList<>();

        for (String line : raw.split("\n")) {
            line = line.trim();
            if (line.isEmpty()) continue;

            if (line.startsWith(">")) {
                if (currentName != null && !currentNetworks.isEmpty()) {
                    result.add(buildWaypoint(currentName, currentTs, currentNetworks));
                }
                ParsedHeader header = parseHeader(line);
                if (header == null) {
                    currentName = null;
                    continue;
                }
                currentName = header.name;
                currentTs = header.tsMinutes * 60L * 1000L;
                currentNetworks = new ArrayList<>();

            } else if (currentName != null && line.length() == 17) {
                WifiNetwork net = parseDataLine(line);
                if (net != null) currentNetworks.add(net);
            }
        }

        if (currentName != null && !currentNetworks.isEmpty()) {
            result.add(buildWaypoint(currentName, currentTs, currentNetworks));
        }
        return result;
    }

    // --------------------------------------------------------
    // --- INTERNAL -------------------------------------------

    private static ParsedHeader parseHeader(String line) {
        if (line.length() < 1 + Waypoint.NAME_MAX_LEN + 8) return null;
        String name = line.substring(1, 1 + Waypoint.NAME_MAX_LEN).trim().toUpperCase();
        if (!Waypoint.isValidName(name)) {
            LogManager.APP_LOGGER.debug(LOG_TAG, "skipping invalid name '" + name + "'");
            return null;
        }
        long tsMinutes;
        try {
            tsMinutes = Long.parseLong(
                    line.substring(1 + Waypoint.NAME_MAX_LEN,
                            1 + Waypoint.NAME_MAX_LEN + 8).trim());
        } catch (NumberFormatException e) {
            tsMinutes = System.currentTimeMillis() / 1000L / 60L;
        }
        return new ParsedHeader(name, tsMinutes);
    }

    private static WifiNetwork parseDataLine(String line) {
        try {
            StringBuilder mac = new StringBuilder();
            for (int i = 0; i < 12; i += 2) {
                if (i > 0) mac.append(':');
                mac.append(line, i, i + 2);
            }
            int channel = Integer.parseInt(line.substring(12, 14));
            int rssiAbs = Integer.parseInt(line.substring(14, 17));
            return new WifiNetwork(mac.toString().toUpperCase(), channel, -rssiAbs);
        } catch (NumberFormatException e) {
            LogManager.APP_LOGGER.debug(LOG_TAG, "skipping malformed data line '" + line + "'");
            return null;
        }
    }

    private static Waypoint buildWaypoint(String name, long ts, List<WifiNetwork> networks) {
        return new Waypoint(UUID.randomUUID().toString(), name, ts,
                new ArrayList<>(networks), "");
    }

    private static class ParsedHeader {
        final String name;
        final long tsMinutes;

        ParsedHeader(String name, long tsMinutes) {
            this.name = name;
            this.tsMinutes = tsMinutes;
        }
    }
}