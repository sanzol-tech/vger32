package ar.vger32app.localizer;

import java.util.Locale;

/*
 * Represents one WiFi network within a waypoint fingerprint.
 * Stored in Android format (colon-separated MAC, negative RSSI).
 * Firmware export conversion is handled by WaypointStore.
 *
 * Firmware format: MAC(12 no separators) + CH(2 digits) + RSSI(3 digits absolute)
 * Example:         "A4C138F2B1D3" + "06" + "061"  →  "A4C138F2B1D306061"
 *
 * Frequency → channel (2.4GHz):
 *   channel = (frequency_MHz - 2412) / 5 + 1   for 2412–2472 MHz
 *   channel = 14                                 for 2484 MHz
 */

public class WifiNetwork {

    private final String mac;      // colon-separated, e.g. "A4:C1:38:F2:B1:D3"
    private final int channel;  // WiFi channel 1–14
    private final int rssi;     // negative RSSI, e.g. -61

    public WifiNetwork(String mac, int channel, int rssi) {
        this.mac = mac;
        this.channel = channel;
        this.rssi = rssi;
    }

    // --------------------------------------------------------
    // --- FIRMWARE EXPORT ------------------------------------

    public String macStripped() {
        return mac.replace(":", "").toUpperCase();
    }

    public String channelFormatted() {
        return String.format(Locale.ROOT, "%02d", channel);
    }

    public String rssiFormatted() {
        return String.format(Locale.ROOT, "%03d", Math.abs(rssi));
    }

    public String toFirmwareLine() {
        return macStripped() + channelFormatted() + rssiFormatted();
    }

    // --------------------------------------------------------
    // --- GETTERS --------------------------------------------

    public String getMac() {
        return mac;
    }

    public int getChannel() {
        return channel;
    }

    public int getRssi() {
        return rssi;
    }

    // --------------------------------------------------------
    // --- FACTORY --------------------------------------------

    public static int frequencyToChannel(int frequencyMhz) {
        if (frequencyMhz == 2484) return 14;
        if (frequencyMhz >= 2412 && frequencyMhz <= 2472) {
            return (frequencyMhz - 2412) / 5 + 1;
        }
        // 5GHz — firmware uses 2.4GHz only, but we store the channel anyway
        if (frequencyMhz >= 5170 && frequencyMhz <= 5825) {
            return (frequencyMhz - 5170) / 5 + 34;
        }
        return 0;
    }
}

