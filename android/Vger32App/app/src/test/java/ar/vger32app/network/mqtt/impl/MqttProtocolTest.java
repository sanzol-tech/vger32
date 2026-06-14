package ar.vger32app.network.mqtt.impl;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.*;

/*
 * Unit tests for the MQTT 3.1.1 frame codec.
 * MqttProtocol is a pure byte-level codec with no external dependencies —
 * all methods are deterministic and fully testable in isolation.
 *
 * Coverage:
 *   - buildConnect / buildSubscribe / buildPublish / buildPingReq / buildDisconnect
 *   - parseConnAck / parsePublish / parseType
 *   - VLE encoding boundary cases (127↔128, 16383↔16384)
 */
public class MqttProtocolTest {

    // --------------------------------------------------------
    // --- BUILD — PINGREQ / DISCONNECT (fixed frames) --------

    @Test
    public void buildPingReq_isExactlyTwoBytes_0xC0_0x00() {
        byte[] frame = MqttProtocol.buildPingReq();

        assertEquals(2, frame.length);
        assertEquals((byte) 0xC0, frame[0]);
        assertEquals((byte) 0x00, frame[1]);
    }

    @Test
    public void buildDisconnect_isExactlyTwoBytes_0xE0_0x00() {
        byte[] frame = MqttProtocol.buildDisconnect();

        assertEquals(2, frame.length);
        assertEquals((byte) 0xE0, frame[0]);
        assertEquals((byte) 0x00, frame[1]);
    }

    // --------------------------------------------------------
    // --- BUILD — CONNECT byte layout ------------------------

    @Test
    public void buildConnect_headerByte_is0x10() throws IOException {
        byte[] frame = MqttProtocol.buildConnect("id", 30);

        assertEquals((byte) 0x10, frame[0]);
    }

    @Test
    public void buildConnect_protocolName_isMQTT() throws IOException {
        byte[] frame = MqttProtocol.buildConnect("id", 30);

        // Payload starts at frame[2]: length-prefixed "MQTT"
        // frame[2..3] = 0x00 0x04 (length = 4)
        // frame[4..7] = 'M' 'Q' 'T' 'T'
        assertEquals((byte) 0x00, frame[2]);
        assertEquals((byte) 0x04, frame[3]);
        assertEquals((byte) 'M',  frame[4]);
        assertEquals((byte) 'Q',  frame[5]);
        assertEquals((byte) 'T',  frame[6]);
        assertEquals((byte) 'T',  frame[7]);
    }

    @Test
    public void buildConnect_protocolLevelAndFlags_correct() throws IOException {
        byte[] frame = MqttProtocol.buildConnect("id", 30);

        // frame[8] = protocol level 0x04 (MQTT 3.1.1)
        // frame[9] = connect flags 0x02 (CleanSession=1)
        assertEquals((byte) 0x04, frame[8]);
        assertEquals((byte) 0x02, frame[9]);
    }

    @Test
    public void buildConnect_keepaliveMsbLsb_correct() throws IOException {
        // keepalive = 60 = 0x003C
        byte[] frame = MqttProtocol.buildConnect("id", 60);

        assertEquals((byte) 0x00, frame[10]);
        assertEquals((byte) 0x3C, frame[11]);
    }

    @Test
    public void buildConnect_clientId_encodedWithLengthPrefix() throws IOException {
        byte[] frame = MqttProtocol.buildConnect("vger", 30);

        // clientId "vger" (4 chars) starts at frame[12]
        // frame[12..13] = 0x00 0x04 (length = 4)
        // frame[14..17] = 'v' 'g' 'e' 'r'
        assertEquals((byte) 0x00, frame[12]);
        assertEquals((byte) 0x04, frame[13]);
        assertEquals((byte) 'v',  frame[14]);
        assertEquals((byte) 'g',  frame[15]);
        assertEquals((byte) 'e',  frame[16]);
        assertEquals((byte) 'r',  frame[17]);
    }

    // --------------------------------------------------------
    // --- BUILD — SUBSCRIBE byte layout ----------------------

    @Test
    public void buildSubscribe_headerByte_is0x82() throws IOException {
        byte[] frame = MqttProtocol.buildSubscribe(1, "t");

        assertEquals((byte) 0x82, frame[0]);
    }

    @Test
    public void buildSubscribe_packetIdMsbLsb_correct() throws IOException {
        // packetId = 256 = 0x0100
        byte[] frame = MqttProtocol.buildSubscribe(256, "t");

        // Payload starts at frame[2]: [0x01, 0x00, topic...]
        assertEquals((byte) 0x01, frame[2]);
        assertEquals((byte) 0x00, frame[3]);
    }

    @Test
    public void buildSubscribe_topicLengthPrefixAndQos_correct() throws IOException {
        // topic "cmd" (3 chars), packetId=1
        byte[] frame = MqttProtocol.buildSubscribe(1, "cmd");

        // frame[2..3] = packetId 0x00 0x01
        // frame[4..5] = topic length 0x00 0x03
        // frame[6..8] = 'c' 'm' 'd'
        // frame[9]    = QoS 0x00
        assertEquals((byte) 0x00, frame[4]);
        assertEquals((byte) 0x03, frame[5]);
        assertEquals((byte) 'c',  frame[6]);
        assertEquals((byte) 'm',  frame[7]);
        assertEquals((byte) 'd',  frame[8]);
        assertEquals((byte) 0x00, frame[9]); // QoS 0
    }

    // --------------------------------------------------------
    // --- BUILD — PUBLISH byte layout ------------------------

    @Test
    public void buildPublish_headerByte_is0x30() throws IOException {
        byte[] frame = MqttProtocol.buildPublish("t", "msg");

        assertEquals((byte) 0x30, frame[0]);
    }

    @Test
    public void buildPublish_topicLengthPrefixUtf8_correct() throws IOException {
        // topic "sensor/temp" = 11 UTF-8 bytes
        byte[] frame = MqttProtocol.buildPublish("sensor/temp", "x");

        // Payload starts at frame[2]: topic length then topic bytes
        assertEquals((byte) 0x00, frame[2]);
        assertEquals((byte) 0x0B, frame[3]); // 11
        assertEquals((byte) 's',  frame[4]);
        assertEquals((byte) 't',  frame[4 + 7]);  // 'temp' starts after "sensor/" (7 chars)
    }

    @Test
    public void buildPublish_payloadEncodedAsIso8859_correct() throws IOException {
        // Byte 0xFF is valid in ISO_8859_1 but invalid in UTF-8
        String message = new String(new byte[]{(byte) 0xFF}, StandardCharsets.ISO_8859_1);
        byte[] frame = MqttProtocol.buildPublish("t", message);

        // Last byte of frame should be 0xFF
        assertEquals((byte) 0xFF, frame[frame.length - 1]);
    }

    // --------------------------------------------------------
    // --- VLE ENCODING BOUNDARY CASES ------------------------

    @Test
    public void buildPublish_127byteRemainingLength_singleVleByte() throws IOException {
        // topic "t" (1 char): topic_len(2) + topic(1) = 3 bytes fixed overhead
        // message of 124 chars → payload = 127 bytes → VLE = [0x7F]
        String msg = repeat('A', 124);
        byte[] frame = MqttProtocol.buildPublish("t", msg);

        assertEquals((byte) 0x7F, frame[1]); // single VLE byte
        // Total frame = 1 (header) + 1 (VLE) + 127 (payload) = 129 bytes
        assertEquals(129, frame.length);
    }

    @Test
    public void buildPublish_128byteRemainingLength_twoVleBytes() throws IOException {
        // message of 125 chars → payload = 128 bytes → VLE = [0x80, 0x01]
        String msg = repeat('A', 125);
        byte[] frame = MqttProtocol.buildPublish("t", msg);

        assertEquals((byte) 0x80, frame[1]); // VLE first byte (continuation bit set)
        assertEquals((byte) 0x01, frame[2]); // VLE second byte
        // Total frame = 1 (header) + 2 (VLE) + 128 (payload) = 131 bytes
        assertEquals(131, frame.length);
    }

    @Test
    public void buildPublish_16383byteRemainingLength_twoVleBytes() throws IOException {
        // payload = 16383 → VLE = [0xFF, 0x7F] (2 bytes)
        String msg = repeat('A', 16380); // 3 + 16380 = 16383
        byte[] frame = MqttProtocol.buildPublish("t", msg);

        assertEquals((byte) 0xFF, frame[1]);
        assertEquals((byte) 0x7F, frame[2]);
    }

    @Test
    public void buildPublish_16384byteRemainingLength_threeVleBytes() throws IOException {
        // payload = 16384 → VLE = [0x80, 0x80, 0x01] (3 bytes)
        String msg = repeat('A', 16381); // 3 + 16381 = 16384
        byte[] frame = MqttProtocol.buildPublish("t", msg);

        assertEquals((byte) 0x80, frame[1]);
        assertEquals((byte) 0x80, frame[2]);
        assertEquals((byte) 0x01, frame[3]);
    }

    // --------------------------------------------------------
    // --- PARSE — parseType ----------------------------------

    @Test
    public void parseType_publishWithVariousFlags_allReturnTypePublish() {
        // PUBLISH header byte varies in low nibble (DUP, QoS, RETAIN flags)
        // & 0xF0 must always yield TYPE_PUBLISH (0x30)
        assertEquals(MqttProtocol.TYPE_PUBLISH, MqttProtocol.parseType(0x30)); // no flags
        assertEquals(MqttProtocol.TYPE_PUBLISH, MqttProtocol.parseType(0x32)); // QoS 1
        assertEquals(MqttProtocol.TYPE_PUBLISH, MqttProtocol.parseType(0x3D)); // all flags
    }

    @Test
    public void parseType_pingrespByte_returnsTypePingresp() {
        assertEquals(MqttProtocol.TYPE_PINGRESP, MqttProtocol.parseType(0xD0));
    }

    @Test
    public void parseType_subackByte_returnsTypeSuback() {
        assertEquals(MqttProtocol.TYPE_SUBACK, MqttProtocol.parseType(0x90));
    }

    // --------------------------------------------------------
    // --- PARSE — parseConnAck -------------------------------

    @Test
    public void parseConnAck_accepted_returns0x00() {
        byte[] frame = {0x20, 0x02, 0x00, 0x00};
        assertEquals(MqttProtocol.CONNACK_ACCEPTED, MqttProtocol.parseConnAck(frame));
    }

    @Test
    public void parseConnAck_allSixReturnCodes_correct() {
        int[] codes = {
                MqttProtocol.CONNACK_ACCEPTED,
                MqttProtocol.CONNACK_REFUSED_PROTOCOL,
                MqttProtocol.CONNACK_REFUSED_ID,
                MqttProtocol.CONNACK_REFUSED_SERVER,
                MqttProtocol.CONNACK_REFUSED_AUTH,
                MqttProtocol.CONNACK_REFUSED_NOT_AUTH
        };

        for (int code : codes) {
            byte[] frame = {0x20, 0x02, 0x00, (byte) code};
            assertEquals(code, MqttProtocol.parseConnAck(frame));
        }
    }

    @Test
    public void parseConnAck_frameShorterThan4Bytes_returnsNegativeOne() {
        assertEquals(-1, MqttProtocol.parseConnAck(new byte[]{0x20, 0x02, 0x00}));
        assertEquals(-1, MqttProtocol.parseConnAck(new byte[]{}));
    }

    // --------------------------------------------------------
    // --- PARSE — parsePublish -------------------------------

    @Test
    public void parsePublish_roundtrip_preservesTopicAndMessage() throws IOException {
        byte[] frame = MqttProtocol.buildPublish("sensor/temp", "hello");
        String[] result = MqttProtocol.parsePublish(frame);

        assertNotNull(result);
        assertEquals(2, result.length);
        assertEquals("sensor/temp", result[0]);
        assertEquals("hello", result[1]);
    }

    @Test
    public void parsePublish_emptyMessage_returnsEmptyString() throws IOException {
        byte[] frame = MqttProtocol.buildPublish("topic", "");
        String[] result = MqttProtocol.parsePublish(frame);

        assertNotNull(result);
        assertEquals("topic", result[0]);
        assertEquals("", result[1]);
    }

    @Test
    public void parsePublish_binaryPayload_roundtripsViaIso8859() throws IOException {
        // Build a message with non-ASCII bytes using ISO_8859_1
        byte[] rawBytes = new byte[]{0x00, (byte) 0x80, (byte) 0xFF};
        String message = new String(rawBytes, StandardCharsets.ISO_8859_1);

        byte[] frame = MqttProtocol.buildPublish("t", message);
        String[] result = MqttProtocol.parsePublish(frame);

        assertNotNull(result);
        byte[] recovered = result[1].getBytes(StandardCharsets.ISO_8859_1);
        assertArrayEquals(rawBytes, recovered);
    }

    @Test
    public void parsePublish_malformedFrame_returnsNull() {
        // Truncated frame — accessing topic length bytes will throw IOOBE
        assertNull(MqttProtocol.parsePublish(new byte[]{0x30, 0x05, 0x00}));
        assertNull(MqttProtocol.parsePublish(new byte[]{0x30}));
        assertNull(MqttProtocol.parsePublish(new byte[]{}));
    }

    // --------------------------------------------------------
    // --- HELPERS --------------------------------------------

    private static String repeat(char c, int count) {
        StringBuilder sb = new StringBuilder(count);
        for (int i = 0; i < count; i++) sb.append(c);
        return sb.toString();
    }
}