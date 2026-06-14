package ar.vger32app.network.mqtt.impl;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/*
 * Frame construction and parsing for MQTT 3.1.1 at the byte level.
 * No external libraries — minimal implementation over a raw TCP socket.
 * QoS 0 only (fire and forget). No SSL. No retained messages.
 *
 * Implemented packets:
 *   CONNECT    — session open with the broker
 *   CONNACK    — broker response parsing for CONNECT
 *   SUBSCRIBE  — topic subscription (QoS 0)
 *   SUBACK     — subscription confirmation parsing
 *   PUBLISH    — message publish (QoS 0, no ACK)
 *   PINGREQ    — keepalive sent to the broker
 *   PINGRESP   — keepalive response from the broker
 *   DISCONNECT — clean session close
 *
 * All build*() methods return the complete frame as byte[].
 * The parse*() methods operate on the first header byte to identify
 * the incoming packet type.
 *
 * Remaining length format: MQTT uses "Variable Length Encoding" (VLE),
 * this implementation supports up to 4 bytes (max ~256 MB, more than enough).
 *
 * parsePublish uses ISO_8859_1 (not UTF-8) for the message body.
 * ISO_8859_1 maps bytes 0x00-0xFF to chars losslessly — required when the
 * payload may contain scrambled binary data. MqttManager.prepareInbound()
 * also uses ISO_8859_1 to convert back to bytes before decoding.
 * For plain-text payloads (ASCII subset of ISO_8859_1), behaviour is identical.
 *
 * Referencias:
 *   MQTT 3.1.1 spec — http://docs.oasis-open.org/mqtt/mqtt/v3.1.1/os/mqtt-v3.1.1-os.html
 */

public final class MqttProtocol {

    private MqttProtocol() {
    }

    // --------------------------------------------------------
    // --- PACKET TYPE CONSTANTS (high nibble of header byte) ---

    public static final int TYPE_CONNECT = 0x10;
    public static final int TYPE_CONNACK = 0x20;
    public static final int TYPE_PUBLISH = 0x30;
    public static final int TYPE_SUBSCRIBE = 0x82;
    public static final int TYPE_SUBACK = 0x90;
    public static final int TYPE_PINGREQ = 0xC0;
    public static final int TYPE_PINGRESP = 0xD0;
    public static final int TYPE_DISCONNECT = 0xE0;

    // ==========================================
    // CONNACK return codes (MQTT 3.1.1 §3.2.2.3)
    // Protocol constants — kept for documentation completeness.
    // Only CONNACK_ACCEPTED has callers today.
    // ==========================================
    public static final int CONNACK_ACCEPTED = 0x00;
    public static final int CONNACK_REFUSED_PROTOCOL = 0x01;
    public static final int CONNACK_REFUSED_ID = 0x02;
    public static final int CONNACK_REFUSED_SERVER = 0x03;
    public static final int CONNACK_REFUSED_AUTH = 0x04;
    public static final int CONNACK_REFUSED_NOT_AUTH = 0x05;

    // --------------------------------------------------------
    // --- BUILD — CONNECT ------------------------------------

    public static byte[] buildConnect(String clientId, int keepalive) throws IOException {
        ByteArrayOutputStream payload = new ByteArrayOutputStream();

        // Protocol name: "MQTT"
        writeString(payload, "MQTT");

        // Protocol level: 4 (MQTT 3.1.1)
        payload.write(0x04);

        // Connect flags: CleanSession=1, no username/password/will
        payload.write(0x02);

        // KeepAlive MSB + LSB
        payload.write((keepalive >> 8) & 0xFF);
        payload.write(keepalive & 0xFF);

        // Client ID
        writeString(payload, clientId);

        return buildFrame(TYPE_CONNECT, payload.toByteArray());
    }

    // --------------------------------------------------------
    // --- BUILD — SUBSCRIBE ----------------------------------

    public static byte[] buildSubscribe(int packetId, String topic) throws IOException {
        ByteArrayOutputStream payload = new ByteArrayOutputStream();

        // Packet identifier
        payload.write((packetId >> 8) & 0xFF);
        payload.write(packetId & 0xFF);

        // Topic + QoS 0
        writeString(payload, topic);
        payload.write(0x00); // QoS 0

        return buildFrame(TYPE_SUBSCRIBE, payload.toByteArray());
    }

    // --------------------------------------------------------
    // --- BUILD — PUBLISH (QoS 0) ----------------------------

    public static byte[] buildPublish(String topic, String message) throws IOException {
        ByteArrayOutputStream payload = new ByteArrayOutputStream();

        // Topic name
        writeString(payload, topic);

        // Message payload encoded as ISO_8859_1 to preserve binary bytes.
        // MqttManager.prepareOutbound() uses the same charset when building
        // the String from scrambled bytes, so the round-trip is lossless.
        byte[] msgBytes = message != null ? message.getBytes(StandardCharsets.ISO_8859_1) : new byte[0];

        payload.write(msgBytes);

        return buildFrame(TYPE_PUBLISH, payload.toByteArray());
    }

    // --------------------------------------------------------
    // --- BUILD — PINGREQ ------------------------------------

    public static byte[] buildPingReq() {
        return new byte[]{(byte) TYPE_PINGREQ, 0x00};
    }

    // --------------------------------------------------------
    // --- BUILD — DISCONNECT ---------------------------------

    public static byte[] buildDisconnect() {
        return new byte[]{(byte) TYPE_DISCONNECT, 0x00};
    }

    // --------------------------------------------------------
    // --- PARSE — incoming frame type identification ---------

    public static int parseType(int headerByte) {
        return headerByte & 0xF0;
    }

    public static int parseConnAck(byte[] frame) {
        if (frame.length < 4) return -1;
        return frame[3] & 0xFF;
    }

    public static String[] parsePublish(byte[] frame) {
        try {
            int index = 1; // skip header byte

            // Read remaining length (VLE)
            int remaining = readVle(frame, index);
            index += vleSize(remaining);

            // Topic length
            int topicLength = ((frame[index] & 0xFF) << 8) | (frame[index + 1] & 0xFF);
            index += 2;

            // Topic is always UTF-8 per MQTT spec
            String topic = new String(frame, index, topicLength, StandardCharsets.UTF_8);
            index += topicLength;

            // QoS 0 → no packet identifier

            // Message: ISO_8859_1 so binary scrambled bytes survive the String round-trip.
            String message = new String(frame, index, frame.length - index,
                    StandardCharsets.ISO_8859_1);
            return new String[]{topic, message};
        } catch (Exception e) {
            return null;
        }
    }

    // --------------------------------------------------------
    // --- PRIVATE HELPERS ------------------------------------

    private static void writeString(ByteArrayOutputStream out, String s) throws IOException {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        out.write((bytes.length >> 8) & 0xFF);
        out.write(bytes.length & 0xFF);
        out.write(bytes);
    }

    private static byte[] buildFrame(int type, byte[] payload) throws IOException {
        ByteArrayOutputStream frame = new ByteArrayOutputStream();
        frame.write(type);
        writeVle(frame, payload.length);
        frame.write(payload);
        return frame.toByteArray();
    }

    private static void writeVle(ByteArrayOutputStream out, int length) {
        do {
            int digit = length % 128;
            length /= 128;
            if (length > 0) digit |= 0x80;
            out.write(digit);
        } while (length > 0);
    }

    private static int readVle(byte[] frame, int offset) {
        int value = 0, multiplier = 1;
        for (int i = 0; i < 4; i++) {
            int vleByte = frame[offset + i] & 0xFF;
            value += (vleByte & 0x7F) * multiplier;
            multiplier *= 128;
            if ((vleByte & 0x80) == 0) break;
        }
        return value;
    }

    private static int vleSize(int length) {
        if (length < 128) return 1;
        if (length < 16384) return 2;
        if (length < 2097152) return 3;
        return 4;
    }
}