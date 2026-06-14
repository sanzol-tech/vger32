package ar.vger32app.network.http;

import java.io.IOException;

/*
 * Android-side wrapper of the firmware HTTP API. Defines the full
 * endpoint surface; not all endpoints have Android callers today.
 */

@SuppressWarnings("unused")
public class Vger32Api {

    private final Vger32ApiClient client;

    public Vger32Api(Vger32ApiClient client) {
        this.client = client;
    }

    // --------------------------------------------------------
    // --- SYSTEM ---------------------------------------------

    public String getSystemIdentity() throws IOException {
        return client.get("/api/system-identity");
    }

    public String getSystemMetrics() throws IOException {
        return client.get("/api/system-metrics");
    }

    public String getBootHistory() throws IOException {
        return client.get("/api/boot-history");
    }

    public String getLogs() throws IOException {
        return client.get("/api/logs");
    }

    public String clearLogs() throws IOException {
        return client.delete("/api/logs");
    }

    // --------------------------------------------------------
    // --- CONFIGURATION --------------------------------------

    public String getPreferences() throws IOException {
        return client.get("/api/preferences");
    }

    public String savePreferences(String payload) throws IOException {
        return client.post("/api/preferences", payload);
    }

    public String getKnownNetworks() throws IOException {
        return client.get("/api/known-networks");
    }

    public String saveKnownNetworks(String payload) throws IOException {
        return client.post("/api/known-networks", payload);
    }

    public String getCapabilities() throws IOException {
        return client.get("/api/capabilities");
    }

    public String saveCapabilities(String payload) throws IOException {
        return client.post("/api/capabilities", payload);
    }

    // --------------------------------------------------------
    // --- SENSORS --------------------------------------------

    public String getSensors() throws IOException {
        return client.get("/api/sensors");
    }

    public String getSensorHistory(String hardwareCode, String metricCode) throws IOException {
        return client.get("/api/sensor-history?h=" + hardwareCode + "&m=" + metricCode);
    }

    // --------------------------------------------------------
    // --- WIFI / LOCATION ------------------------------------

    public String getWifiScan() throws IOException {
        return client.get("/api/wifi-scan");
    }

    public String getWifiFingerprints() throws IOException {
        return client.get("/api/wifi-fingerprints");
    }

    public String saveWifiFingerprint(String payload) throws IOException {
        return client.post("/api/wifi-fingerprints", payload);
    }

    public String replaceWifiFingerprints(String payload) throws IOException {
        return client.put("/api/wifi-fingerprints", payload);
    }

    public String getLocation() throws IOException {
        return client.get("/api/location");
    }

    // --------------------------------------------------------
    // --- TIME -----------------------------------------------

    public String getTime() throws IOException {
        return client.get("/api/time");
    }

    public String setTime(long unixTimestamp) throws IOException {
        return client.post("/api/time", "ts=" + unixTimestamp + "\n");
    }

    // --------------------------------------------------------
    // --- CONTROL --------------------------------------------

    public String reboot() throws IOException {
        return client.post("/api/reboot", "");
    }

    public String forceAp() throws IOException {
        return client.post("/api/force-ap", "");
    }

}