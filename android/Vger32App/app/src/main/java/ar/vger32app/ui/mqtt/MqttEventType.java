package ar.vger32app.ui.mqtt;

import androidx.annotation.ColorRes;

import ar.vger32app.R;

/*
 * Type of entry in the MQTT monitor feed.
 * Each value carries its badge label and badge color.
 */

public enum MqttEventType {

    MQTT("mqtt", R.color.BLUE),
    PONG("pong", R.color.BLUE),
    PUBLISH("pub", R.color.color500),
    HTTP("http", R.color.YELLOW),
    SYS("sys", R.color.color400);

    public final String badge;
    @ColorRes
    public final int colorRes;

    MqttEventType(String badge, @ColorRes int colorRes) {
        this.badge = badge;
        this.colorRes = colorRes;
    }
}