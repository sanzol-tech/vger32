package ar.vger32app.ui.mqtt;

/*
 * One entry in the MQTT monitor feed.
 *
 * Types:
 * MQTT    — inbound telemetry from a device (sensors/latest, etc.)
 * PONG    — device response to a ping, contains device identity
 * PUBLISH — outbound command sent by the user
 * HTTP    — HTTP call result
 * SYS     — internal app event (connected, disconnected, etc.)
 *
 * mid is set for device-originated events (MQTT, PONG) and null for
 * outbound or system events (PUBLISH, SYS). Used by the adapter to render
 * the module name on the first line and the payload on the second.
 */

public class MqttFeedEvent {

    private final MqttEventType type;
    private final String moduleId;        // module id — null for PUBLISH/SYS
    private final String text;
    private final long timestamp;

    public MqttFeedEvent(MqttEventType type, String moduleId, String text) {
        this.type = type;
        this.moduleId = moduleId;
        this.text = text;
        this.timestamp = System.currentTimeMillis();
    }

    public MqttEventType getType() {
        return type;
    }

    public String getModuleId() {
        return moduleId;
    }

    public String getText() {
        return text;
    }

    public long getTimestamp() {
        return timestamp;
    }

    // --------------------------------------------------------
    // --- FACTORY --------------------------------------------

    public static MqttFeedEvent mqtt(String mid, String text) {
        return new MqttFeedEvent(MqttEventType.MQTT, mid, text);
    }

    public static MqttFeedEvent pong(String mid, String text) {
        return new MqttFeedEvent(MqttEventType.PONG, mid, text);
    }

    public static MqttFeedEvent publish(String text) {
        return new MqttFeedEvent(MqttEventType.PUBLISH, null, text);
    }

    public static MqttFeedEvent http(String text) {
        return new MqttFeedEvent(MqttEventType.HTTP, null, text);
    }

    public static MqttFeedEvent sys(String text) {
        return new MqttFeedEvent(MqttEventType.SYS, null, text);
    }
}