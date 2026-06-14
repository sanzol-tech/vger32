package ar.vger32app.module;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/*
 * Immutable snapshot of a module as announced at discovery time.
 * Produced by each discoverer (mDNS, UDP, LAN, MQTT pong) and consumed
 * by ModulesStore.register() — the only place that knows the wire format.
 */

public final class ModuleDiscovered {

    public final String moduleId;
    public final String ip;
    public final String profileId;
    public final String chip;
    public final String board;
    public final String version;
    public final long discoveredAt;

    private ModuleDiscovered(String moduleId, String ip, String profileId,
                             String chip, String board, String version) {
        this.moduleId = moduleId;
        this.ip = ip;
        this.profileId = profileId;
        this.chip = chip;
        this.board = board;
        this.version = version;
        this.discoveredAt = System.currentTimeMillis();
    }

    // --------------------------------------------------------
    // --- FACTORIES ------------------------------------------

    /*
     * Parses the key=value identity payload sent by the firmware via
     * UDP broadcast, HTTP /api/system-identity, and MQTT pong.
     * Returns null if moduleId is missing — caller should discard.
     */
    public static ModuleDiscovered fromPayload(String raw) {
        if (raw == null || raw.trim().isEmpty()) return null;

        String moduleId = "", ip = "", profileId = "", chip = "", board = "", version = "";

        for (String line : raw.split("\n")) {
            String[] parts = line.split("=", 2);
            if (parts.length < 2) continue;
            switch (parts[0].trim()) {
                case "mid":
                    moduleId = parts[1].trim();
                    break;
                case "ip":
                    ip = parts[1].trim();
                    break;
                case "pid":
                    profileId = parts[1].trim();
                    break;
                case "chip":
                    chip = parts[1].trim();
                    break;
                case "brd":
                    board = parts[1].trim();
                    break;
                case "ver":
                    version = parts[1].trim();
                    break;
            }
        }

        return moduleId.isEmpty() ? null
                : new ModuleDiscovered(moduleId, ip, profileId, chip, board, version);
    }

    /*
     * Builds from mDNS TXT records resolved by NsdManager.
     * Returns null if moduleId is missing.
     */
    public static ModuleDiscovered fromTxtRecords(Map<String, byte[]> attrs,
                                                  String resolvedIp) {
        if (attrs == null || attrs.isEmpty()) return null;

        String moduleId = decode(attrs, "mid");
        String ip = attrs.containsKey("ip") ? decode(attrs, "ip") : resolvedIp;
        String profileId = decode(attrs, "pid");
        String chip = decode(attrs, "chip");
        String board = decode(attrs, "brd");
        String version = decode(attrs, "ver");

        return moduleId.isEmpty() ? null
                : new ModuleDiscovered(moduleId, ip, profileId, chip, board, version);
    }

    private static String decode(Map<String, byte[]> attrs, String key) {
        byte[] val = attrs.get(key);
        return val != null ? new String(val, StandardCharsets.UTF_8).trim() : "";
    }
}