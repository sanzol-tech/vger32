package ar.vger32app.module;

/*
 * Represents a known vger32 device.
 *
 * Firmware fields  — populated from ModuleDiscovered at discovery time.
 * App metadata     — managed by ModulesStore (when and how last seen).
 */

public class Module {

    private static final long OFFLINE_TIMEOUT_MS = 90_000;

    // --------------------------------------------------------
    // --- FROM FIRMWARE --------------------------------------

    private final String moduleId;
    private String ip;
    private String profileId;
    private String chip;
    private String board;
    private String version;

    // --------------------------------------------------------
    // --- APP METADATA ---------------------------------------

    private long lastSeenAt;
    private DiscoverySource lastDiscoverySource;

    // --------------------------------------------------------
    // --- CONSTRUCTORS ---------------------------------------

    /* Created when a module is first discovered. */
    public Module(ModuleDiscovered discovered, DiscoverySource source) {
        this.moduleId = discovered.moduleId;
        this.ip = discovered.ip;
        this.profileId = discovered.profileId;
        this.chip = discovered.chip;
        this.board = discovered.board;
        this.version = discovered.version;
        this.lastSeenAt = discovered.discoveredAt;
        this.lastDiscoverySource = source;
    }

    /* Used by ModulesSerializer when restoring from disk. */
    public Module(String moduleId, String ip, String profileId, String chip, String board,
                  String version, long lastSeenAt, DiscoverySource lastDiscoverySource) {
        this.moduleId = moduleId;
        this.ip = ip;
        this.profileId = profileId;
        this.chip = chip;
        this.board = board;
        this.version = version;
        this.lastSeenAt = lastSeenAt;
        this.lastDiscoverySource = lastDiscoverySource;
    }

    // --------------------------------------------------------
    // --- UPDATE ---------------------------------------------

    public void update(ModuleDiscovered discovered, DiscoverySource source) {
        this.ip = discovered.ip;
        this.profileId = discovered.profileId;
        this.chip = discovered.chip;
        this.board = discovered.board;
        this.version = discovered.version;
        this.lastSeenAt = discovered.discoveredAt;
        this.lastDiscoverySource = source;
    }

    public void touch(DiscoverySource source) {
        this.lastSeenAt = System.currentTimeMillis();
        this.lastDiscoverySource = source;
    }

    // --------------------------------------------------------
    // --- QUERIES --------------------------------------------

    public boolean isOnline() {
        return System.currentTimeMillis() - lastSeenAt < OFFLINE_TIMEOUT_MS;
    }

    public boolean isExpired(long maxAgeMs) {
        return System.currentTimeMillis() - lastSeenAt > maxAgeMs;
    }

    // --------------------------------------------------------
    // --- GETTERS --------------------------------------------

    public String getModuleId() {
        return moduleId;
    }

    public String getIp() {
        return ip;
    }

    public String getProfileId() {
        return profileId;
    }

    public String getChip() {
        return chip;
    }

    public String getBoard() {
        return board;
    }

    public String getVersion() {
        return version;
    }

    public long getLastSeenAt() {
        return lastSeenAt;
    }

    public DiscoverySource getLastDiscoverySource() {
        return lastDiscoverySource;
    }

    public String getSummary() {
        StringBuilder sb = new StringBuilder(moduleId);
        if (chip != null && !chip.isEmpty()) sb.append(" · ").append(chip);
        if (board != null && !board.isEmpty()) sb.append(" · ").append(board);
        if (profileId != null && !profileId.isEmpty()) sb.append(" · ").append(profileId);
        if (ip != null && !ip.isEmpty()) sb.append(" · ").append(ip);
        return sb.toString();
    }
}