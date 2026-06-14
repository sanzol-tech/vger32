package ar.vger32app.localizer;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/*
 * Unit tests for FingerprintFormat.serialize() and parse().
 *
 * Wire format:
 *   Header: >NAME(6 chars padded)TSMIN(8 digits)\n
 *   Data:   MAC(12 hex) + CH(2 digits) + RSSI_ABS(3 digits)\n
 *
 * BASE_TS_MS is chosen divisible by 60 000 so the round-trip through
 * minutes is lossless.
 */
public class FingerprintFormatTest {

    private static final long BASE_TS_MS = 1_704_960_000_000L; // 28 416 000 minutes exactly

    private Waypoint waypoint(String name, long tsMs, List<WifiNetwork> networks) {
        return new Waypoint("test-id", name, tsMs, networks, "test");
    }

    private WifiNetwork net(String mac, int ch, int rssi) {
        return new WifiNetwork(mac, ch, rssi);
    }

    // --------------------------------------------------------
    // --- SERIALIZE ------------------------------------------

    @Test
    public void serialize_emptyList_returnsEmptyString() {
        assertEquals("", FingerprintFormat.serialize(Collections.emptyList()));
    }

    @Test
    public void serialize_singleWaypoint_correctHeaderFormat() {
        List<WifiNetwork> nets = Collections.singletonList(net("AA:BB:CC:DD:EE:FF", 6, -61));
        String result = FingerprintFormat.serialize(
                Collections.singletonList(waypoint("LAB1", BASE_TS_MS, nets)));

        // ">LAB1  28416000" — name padded to 6 chars, ts in minutes zero-padded to 8 digits
        assertEquals(">LAB1  28416000", result.split("\n")[0]);
    }

    @Test
    public void serialize_singleWaypoint_correctDataLine() {
        List<WifiNetwork> nets = Collections.singletonList(net("AA:BB:CC:DD:EE:FF", 6, -61));
        String result = FingerprintFormat.serialize(
                Collections.singletonList(waypoint("LAB1", BASE_TS_MS, nets)));

        // MAC(12 no separators) + CH(2 digits) + RSSI_ABS(3 digits)
        assertEquals("AABBCCDDEEFF06061", result.split("\n")[1]);
    }

    @Test
    public void serialize_shortName_paddedToSixChars() {
        List<WifiNetwork> nets = Collections.singletonList(net("AA:BB:CC:DD:EE:FF", 1, -50));
        String result = FingerprintFormat.serialize(
                Collections.singletonList(waypoint("A", BASE_TS_MS, nets)));

        assertTrue(result.startsWith(">A     "));
    }

    @Test
    public void serialize_multipleWaypoints_allHeadersPresent() {
        List<Waypoint> wps = new ArrayList<>();
        wps.add(waypoint("WP1", BASE_TS_MS, Collections.singletonList(net("AA:BB:CC:DD:EE:FF", 1, -50))));
        wps.add(waypoint("WP2", BASE_TS_MS, Collections.singletonList(net("11:22:33:44:55:66", 6, -70))));

        String result = FingerprintFormat.serialize(wps);
        assertTrue(result.contains(">WP1"));
        assertTrue(result.contains(">WP2"));
    }

    // --------------------------------------------------------
    // --- PARSE ----------------------------------------------

    @Test
    public void parse_null_returnsEmptyList() {
        assertTrue(FingerprintFormat.parse(null).isEmpty());
    }

    @Test
    public void parse_empty_returnsEmptyList() {
        assertTrue(FingerprintFormat.parse("").isEmpty());
    }

    @Test
    public void parse_whitespaceOnly_returnsEmptyList() {
        assertTrue(FingerprintFormat.parse("   \n  ").isEmpty());
    }

    @Test
    public void parse_validSingleWaypoint_returnsOneEntry() {
        String raw = ">LAB1  28416000\nAABBCCDDEEFF06061\n";
        assertEquals(1, FingerprintFormat.parse(raw).size());
    }

    @Test
    public void parse_validSingleWaypoint_correctName() {
        String raw = ">LAB1  28416000\nAABBCCDDEEFF06061\n";
        assertEquals("LAB1", FingerprintFormat.parse(raw).get(0).getName());
    }

    @Test
    public void parse_validSingleWaypoint_correctTimestamp() {
        String raw = ">LAB1  28416000\nAABBCCDDEEFF06061\n";
        // 28 416 000 minutes × 60 × 1000 = BASE_TS_MS
        assertEquals(BASE_TS_MS, FingerprintFormat.parse(raw).get(0).getTimestamp());
    }

    @Test
    public void parse_validSingleWaypoint_correctNetworkData() {
        String raw = ">LAB1  28416000\nAABBCCDDEEFF06061\n";
        WifiNetwork net = FingerprintFormat.parse(raw).get(0).getNetworks().get(0);
        assertEquals("AA:BB:CC:DD:EE:FF", net.getMac());
        assertEquals(6, net.getChannel());
        assertEquals(-61, net.getRssi());
    }

    @Test
    public void parse_multipleWaypoints_returnsAll() {
        // Data lines: MAC(12) + CH(2) + RSSI_ABS(3) = 17 chars exactly
        String raw = ">WP1   28416000\nAABBCCDDEEFF06061\n"
                + ">WP2   28416000\n11223344556601050\n";
        assertEquals(2, FingerprintFormat.parse(raw).size());
    }

    @Test
    public void parse_waypointWithNoDataLines_skipped() {
        // EMPTY has no networks → dropped; NEXT has one → kept
        String raw = ">EMPTY 28416000\n>NEXT  28416000\nAABBCCDDEEFF06061\n";
        List<Waypoint> result = FingerprintFormat.parse(raw);
        assertEquals(1, result.size());
        assertEquals("NEXT", result.get(0).getName());
    }

    @Test
    public void parse_headerTooShort_sectionSkipped() {
        // parseHeader requires at least 1 + NAME_MAX_LEN(6) + 8 = 15 chars.
        // A shorter header returns null immediately without calling the logger
        // (which needs Android context), so this path is safe in pure JUnit.
        String raw = ">SHORT\nAABBCCDDEEFF06061\n";
        assertTrue(FingerprintFormat.parse(raw).isEmpty());
    }

    @Test
    public void parse_malformedDataLine_lineSkipped_validLineKept() {
        // "NOTVALID" is not 17 chars → skipped; the second line is valid
        String raw = ">LAB1  28416000\nNOTVALID\nAABBCCDDEEFF06061\n";
        List<Waypoint> result = FingerprintFormat.parse(raw);
        assertEquals(1, result.size());
        assertEquals(1, result.get(0).networkCount());
    }

    // --------------------------------------------------------
    // --- ROUND-TRIP -----------------------------------------

    @Test
    public void roundtrip_preservesNameAndNetworkCount() {
        List<WifiNetwork> nets = new ArrayList<>();
        nets.add(net("AA:BB:CC:DD:EE:FF", 6, -61));
        nets.add(net("11:22:33:44:55:66", 11, -75));

        Waypoint original = waypoint("ROOM1", BASE_TS_MS, nets);
        String serialized = FingerprintFormat.serialize(Collections.singletonList(original));
        Waypoint restored = FingerprintFormat.parse(serialized).get(0);

        assertEquals("ROOM1", restored.getName());
        assertEquals(2, restored.networkCount());
    }

    @Test
    public void roundtrip_preservesTimestampToMinutePrecision() {
        Waypoint original = waypoint("TS", BASE_TS_MS,
                Collections.singletonList(net("AA:BB:CC:DD:EE:FF", 1, -50)));
        String serialized = FingerprintFormat.serialize(Collections.singletonList(original));
        Waypoint restored = FingerprintFormat.parse(serialized).get(0);

        assertEquals(BASE_TS_MS, restored.getTimestamp());
    }

    @Test
    public void roundtrip_preservesNetworkData() {
        WifiNetwork original = net("DE:AD:BE:EF:CA:FE", 13, -88);
        Waypoint wp = waypoint("NET", BASE_TS_MS, Collections.singletonList(original));
        String serialized = FingerprintFormat.serialize(Collections.singletonList(wp));
        WifiNetwork restored = FingerprintFormat.parse(serialized).get(0).getNetworks().get(0);

        assertEquals("DE:AD:BE:EF:CA:FE", restored.getMac());
        assertEquals(13, restored.getChannel());
        assertEquals(-88, restored.getRssi());
    }
}