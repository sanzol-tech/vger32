package ar.vger32app.config;

/*
 * Global constants shared across multiple classes.
 * If a value is only used in one class, it belongs there — not here.
 */

public final class AppConfig {

    private AppConfig() {
        //
    }

    // --- MQTT FEED ----------------------------------

    public static final int MQTT_FEED_MAX_EVENTS = 200;


    // --- FINGERPRINTS ----------------------------------

    public static final String WIFI_FINGERPRINTS_FILENAME = "wifi_fingerprints.dat";
    public static final String WIFI_FINGERPRINTS_MIME = "text/plain";

}