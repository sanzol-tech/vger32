package ar.vger32app.module;

import java.util.Locale;

/*
 * Converts Module instances to and from the flat-file format used by ModulesStore.
 *
 * Current format (pipe-separated, one module per line):
 *   mid|ip|profileId|chip|board|version|lastSeenAt|lastDiscoverySource
 *
 * Legacy format (9 fields, had sts at index 6):
 *   mid|ip|pid|chip|brd|ver|sts|lastSeenAt|lastDiscoverySource
 * Both formats are read; only the current format is written.
 */

public class ModulesSerializer {

    private static final String SEP = "|";

    private ModulesSerializer() {
    }

    // --------------------------------------------------------
    // --- SERIALIZE ------------------------------------------

    public static String toLine(Module module) {
        return sanitize(module.getModuleId()) + SEP
                + sanitize(module.getIp()) + SEP
                + sanitize(module.getProfileId()) + SEP
                + sanitize(module.getChip()) + SEP
                + sanitize(module.getBoard()) + SEP
                + sanitize(module.getVersion()) + SEP
                + module.getLastSeenAt() + SEP
                + sourceName(module.getLastDiscoverySource());
    }

    // --------------------------------------------------------
    // --- DESERIALIZE ----------------------------------------

    public static Module fromLine(String line) {
        if (line == null || line.trim().isEmpty()) return null;
        String[] parts = line.split("\\|", -1);
        if (parts.length < 8) return null;

        String mid = parts[0].trim();
        if (mid.isEmpty()) return null;

        // Detect legacy format: try parsing index 6 as long.
        // Legacy has sts (string) at index 6; current has lastSeenAt (long).
        boolean legacy = false;
        long lastSeenAt = 0;
        try {
            lastSeenAt = Long.parseLong(parts[6].trim());
        } catch (NumberFormatException e) {
            legacy = true; // sts was at index 6
        }

        int seenAtIdx = legacy ? 7 : 6;
        int sourceIdx = legacy ? 8 : 7;

        if (legacy) {
            try {
                lastSeenAt = Long.parseLong(parts[seenAtIdx].trim());
            } catch (NumberFormatException ignored) {
            }
        }

        if (parts.length <= sourceIdx) return null;

        return new Module(
                mid,
                parts[1].trim(),    // ip
                parts[2].trim(),    // profileId (was pid)
                parts[3].trim(),    // chip
                parts[4].trim(),    // board (was brd)
                parts[5].trim(),    // version (was ver)
                lastSeenAt,
                sourceFrom(parts[sourceIdx].trim()));
    }

    // --------------------------------------------------------
    // --- HELPERS --------------------------------------------

    private static String sanitize(String value) {
        return value != null ? value : "";
    }

    private static String sourceName(DiscoverySource source) {
        return source != null ? source.name() : DiscoverySource.MQTT_PONG.name();
    }

    private static DiscoverySource sourceFrom(String name) {
        if (name == null || name.isEmpty()) return DiscoverySource.MQTT_PONG;
        int pipe = name.indexOf('|');
        String clean = (pipe >= 0 ? name.substring(0, pipe) : name).trim().toUpperCase(Locale.ROOT);
        try {
            return DiscoverySource.valueOf(clean);
        } catch (IllegalArgumentException ignored) {
            return DiscoverySource.MQTT_PONG;
        }
    }

}