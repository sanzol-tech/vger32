package ar.vger32app.module;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/*
 * Unit tests for ModuleDiscovered.fromPayload().
 *
 * The payload is the key=value format sent by the firmware via
 * UDP broadcast, HTTP /api/system-identity, and MQTT pong.
 */
public class ModuleDiscoveredTest {

    private static final String FULL_PAYLOAD =
            "mid=ESP32_ABC\nip=192.168.1.10\npid=sensor_v2\nchip=ESP32\nbrd=devkit\nver=1.0.0\n";

    // --------------------------------------------------------
    // --- NULL / EMPTY GUARDS --------------------------------

    @Test
    public void fromPayload_null_returnsNull() {
        assertNull(ModuleDiscovered.fromPayload(null));
    }

    @Test
    public void fromPayload_empty_returnsNull() {
        assertNull(ModuleDiscovered.fromPayload(""));
    }

    @Test
    public void fromPayload_whitespaceOnly_returnsNull() {
        assertNull(ModuleDiscovered.fromPayload("   \n  "));
    }

    @Test
    public void fromPayload_missingMid_returnsNull() {
        assertNull(ModuleDiscovered.fromPayload("ip=192.168.1.10\npid=sensor_v2\n"));
    }

    // --------------------------------------------------------
    // --- VALID PAYLOAD — ALL FIELDS -------------------------

    @Test
    public void fromPayload_fullPayload_returnsNotNull() {
        assertNotNull(ModuleDiscovered.fromPayload(FULL_PAYLOAD));
    }

    @Test
    public void fromPayload_fullPayload_correctModuleId() {
        assertEquals("ESP32_ABC", ModuleDiscovered.fromPayload(FULL_PAYLOAD).moduleId);
    }

    @Test
    public void fromPayload_fullPayload_correctIp() {
        assertEquals("192.168.1.10", ModuleDiscovered.fromPayload(FULL_PAYLOAD).ip);
    }

    @Test
    public void fromPayload_fullPayload_correctProfileId() {
        assertEquals("sensor_v2", ModuleDiscovered.fromPayload(FULL_PAYLOAD).profileId);
    }

    @Test
    public void fromPayload_fullPayload_correctChip() {
        assertEquals("ESP32", ModuleDiscovered.fromPayload(FULL_PAYLOAD).chip);
    }

    @Test
    public void fromPayload_fullPayload_correctBoard() {
        assertEquals("devkit", ModuleDiscovered.fromPayload(FULL_PAYLOAD).board);
    }

    @Test
    public void fromPayload_fullPayload_correctVersion() {
        assertEquals("1.0.0", ModuleDiscovered.fromPayload(FULL_PAYLOAD).version);
    }

    @Test
    public void fromPayload_discoveredAt_isApproximatelyNow() {
        long before = System.currentTimeMillis();
        ModuleDiscovered d = ModuleDiscovered.fromPayload(FULL_PAYLOAD);
        long after = System.currentTimeMillis();
        assertTrue(d.discoveredAt >= before && d.discoveredAt <= after);
    }

    // --------------------------------------------------------
    // --- PARTIAL PAYLOADS -----------------------------------

    @Test
    public void fromPayload_onlyMid_returnsModuleWithEmptyOptionalFields() {
        ModuleDiscovered d = ModuleDiscovered.fromPayload("mid=SOLO\n");
        assertNotNull(d);
        assertEquals("SOLO", d.moduleId);
        assertEquals("", d.ip);
        assertEquals("", d.profileId);
        assertEquals("", d.chip);
        assertEquals("", d.board);
        assertEquals("", d.version);
    }

    @Test
    public void fromPayload_unknownKeys_ignored() {
        ModuleDiscovered d = ModuleDiscovered.fromPayload("mid=TEST\nunknown=value\nextra=stuff\n");
        assertNotNull(d);
        assertEquals("TEST", d.moduleId);
    }

    @Test
    public void fromPayload_valueContainingEquals_parsedCorrectly() {
        // split("=", 2) preserves "=" inside the value
        ModuleDiscovered d = ModuleDiscovered.fromPayload("mid=TEST\npid=fw=1.0\n");
        assertNotNull(d);
        assertEquals("fw=1.0", d.profileId);
    }

    @Test
    public void fromPayload_whitespaceAroundKeyAndValue_trimmed() {
        ModuleDiscovered d = ModuleDiscovered.fromPayload("  mid  =  ESP32_X  \n  ip  =  10.0.0.1  \n");
        assertNotNull(d);
        assertEquals("ESP32_X", d.moduleId);
        assertEquals("10.0.0.1", d.ip);
    }
}
