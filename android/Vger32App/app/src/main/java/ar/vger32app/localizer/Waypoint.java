package ar.vger32app.localizer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/*
 * Represents a saved WiFi fingerprint waypoint.
 * Contains the name (1-6 chars A-Z0-9), capture timestamp, source label,
 * and the list of WiFi networks visible at that moment.
 *
 * Serialization and firmware export live in WaypointStore.
 *
 * Firmware constraints:
 *   - name: 1–NAME_MAX_LEN chars, [A-Z0-9] only
 *   - networks: max AppConfig.FINGERPRINT_MAX_NETWORKS (20)
 */

public class Waypoint {

    public static final int NAME_MAX_LEN = 6;

    private final String id;
    private final String name;
    private final long timestamp;
    private final String source;
    private final List<WifiNetwork> networks;

    public Waypoint(String id, String name, long timestamp, List<WifiNetwork> networks, String source) {
        this.id = id;
        this.name = name.toUpperCase();
        this.timestamp = timestamp;
        this.source = source != null ? source : "";
        this.networks = new ArrayList<>(networks);
    }

    public static boolean isValidName(String name) {
        if (name == null || name.isEmpty() || name.length() > NAME_MAX_LEN) return false;
        return name.matches("[A-Z0-9]+");
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getSource() {
        return source;
    }

    public List<WifiNetwork> getNetworks() {
        return Collections.unmodifiableList(networks);
    }

    public int networkCount() {
        return networks.size();
    }
}
