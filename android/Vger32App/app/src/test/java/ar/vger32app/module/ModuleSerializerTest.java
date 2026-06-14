package ar.vger32app.module;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ModuleSerializerTest {

    private static final long TEST_TIMESTAMP = 1704987654321L;

    // --------------------------------------------------------
    // --- TEST DATA BUILDERS ---------------------------------

    private Module createTestModule() {
        return new Module(
                "ESP32_ABC123",
                "192.168.1.100",
                "firmware_v2",
                "ESP32",
                "dev-board",
                "1.0.0",
                TEST_TIMESTAMP,
                DiscoverySource.LAN_SCAN
        );
    }

    private Module createTestModuleWithNulls() {
        return new Module(
                "ESP32_NULL_TEST",
                null,
                null,
                null,
                null,
                null,
                TEST_TIMESTAMP,
                DiscoverySource.MQTT_PONG
        );
    }

    private Module createModuleWithSource(DiscoverySource source) {
        return new Module(
                "test_mid",
                "10.0.0.1",
                "test_pid",
                "ESP32",
                "test_board",
                "2.0",
                TEST_TIMESTAMP,
                source
        );
    }

    // --------------------------------------------------------
    // --- ROUNDTRIP TESTS ------------------------------------

    @Test
    public void toLine_fromLine_roundtrip_preservesAllFields() {
        Module original = createTestModule();
        String line = ModulesSerializer.toLine(original);
        Module restored = ModulesSerializer.fromLine(line);

        assertNotNull(restored);
        assertEquals(original.getModuleId(), restored.getModuleId());
        assertEquals(original.getIp(), restored.getIp());
        assertEquals(original.getProfileId(), restored.getProfileId());
        assertEquals(original.getChip(), restored.getChip());
        assertEquals(original.getBoard(), restored.getBoard());
        assertEquals(original.getVersion(), restored.getVersion());
        assertEquals(original.getLastSeenAt(), restored.getLastSeenAt());
        assertEquals(original.getLastDiscoverySource(), restored.getLastDiscoverySource());
    }

    @Test
    public void toLine_fromLine_roundtrip_withNullFields_preservesAsEmpty() {
        Module original = createTestModuleWithNulls();
        String line = ModulesSerializer.toLine(original);
        Module restored = ModulesSerializer.fromLine(line);

        assertNotNull(restored);
        assertEquals(original.getModuleId(), restored.getModuleId());
        assertEquals("", restored.getIp());
        assertEquals("", restored.getProfileId());
        assertEquals("", restored.getChip());
        assertEquals("", restored.getBoard());
        assertEquals("", restored.getVersion());
        assertEquals(original.getLastSeenAt(), restored.getLastSeenAt());
        assertEquals(original.getLastDiscoverySource(), restored.getLastDiscoverySource());
    }

    // --------------------------------------------------------
    // --- SERIALIZE TESTS ------------------------------------

    @Test
    public void toLine_returnsCorrectFormat() {
        Module module = createTestModule();
        String line = ModulesSerializer.toLine(module);

        // Expected: mid|ip|profileId|chip|board|version|lastSeenAt|source
        String expected = "ESP32_ABC123|192.168.1.100|firmware_v2|ESP32|dev-board|1.0.0|"
                + TEST_TIMESTAMP + "|LAN_SCAN";

        assertEquals(expected, line);
    }

    @Test
    public void toLine_withNullFields_replacesWithEmptyString() {
        Module module = createTestModuleWithNulls();
        String line = ModulesSerializer.toLine(module);

        assertTrue(line.startsWith("ESP32_NULL_TEST|"));
        // Six consecutive pipes: ip, profileId, chip, board, version all empty
        assertTrue(line.contains("||||||"));
    }

    @Test
    public void toLine_withNullDiscoverySource_usesDefaultSource() {
        Module module = new Module(
                "test_mid", "1.1.1.1", "pid", "chip", "board", "ver",
                TEST_TIMESTAMP, null
        );

        String line = ModulesSerializer.toLine(module);

        assertTrue(line.endsWith("|MQTT_PONG"));
    }

    // --------------------------------------------------------
    // --- DESERIALIZE TESTS ----------------------------------

    @Test
    public void fromLine_validLine_returnsModule() {
        String line = "ESP32_XYZ|10.0.0.50|v3|ESP32-S3|board|2.0|1234567890|LAN_SCAN";
        Module module = ModulesSerializer.fromLine(line);

        assertNotNull(module);
        assertEquals("ESP32_XYZ", module.getModuleId());
        assertEquals("10.0.0.50", module.getIp());
        assertEquals("v3", module.getProfileId());
        assertEquals("ESP32-S3", module.getChip());
        assertEquals("board", module.getBoard());
        assertEquals("2.0", module.getVersion());
        assertEquals(1234567890L, module.getLastSeenAt());
        assertEquals(DiscoverySource.LAN_SCAN, module.getLastDiscoverySource());
    }

    @Test
    public void fromLine_withEmptyFields_returnsModuleWithEmptyStrings() {
        // mid|ip|profileId|chip|board|version|lastSeenAt|source — 8 fields
        String line = "EMPTY_TEST||||||1234567890|MQTT_PONG";
        Module module = ModulesSerializer.fromLine(line);

        assertNotNull(module);
        assertEquals("EMPTY_TEST", module.getModuleId());
        assertEquals("", module.getIp());
        assertEquals("", module.getProfileId());
        assertEquals("", module.getChip());
        assertEquals("", module.getBoard());
        assertEquals("", module.getVersion());
    }

    @Test
    public void fromLine_withInvalidEnum_usesDefault() {
        String line = "TEST_MID|1.1.1.1|pid|chip|board|ver|123456|UNKNOWN_SOURCE";
        Module module = ModulesSerializer.fromLine(line);

        assertNotNull(module);
        assertEquals(DiscoverySource.MQTT_PONG, module.getLastDiscoverySource());
    }

    @Test
    public void fromLine_caseInsensitiveEnum_works() {
        String line = "TEST_MID|1.1.1.1|pid|chip|board|ver|123456|lan_scan";
        Module module = ModulesSerializer.fromLine(line);

        assertNotNull(module);
        assertEquals(DiscoverySource.LAN_SCAN, module.getLastDiscoverySource());
    }

    // --------------------------------------------------------
    // --- ALL DISCOVERY SOURCES ------------------------------

    @Test
    public void fromLine_allDiscoverySources_roundtripCorrectly() {
        DiscoverySource[] sources = {
                DiscoverySource.MQTT_PONG,
                DiscoverySource.MDNS,
                DiscoverySource.UDP_DISCOVERY,
                DiscoverySource.LAN_SCAN,
                DiscoverySource.MANUAL_IP
        };

        for (DiscoverySource source : sources) {
            Module original = createModuleWithSource(source);
            String line = ModulesSerializer.toLine(original);
            Module restored = ModulesSerializer.fromLine(line);

            assertEquals(source, restored.getLastDiscoverySource());
        }
    }

    // --------------------------------------------------------
    // --- EDGE CASES & ERROR HANDLING ------------------------

    @Test
    public void fromLine_nullLine_returnsNull() {
        assertNull(ModulesSerializer.fromLine(null));
    }

    @Test
    public void fromLine_emptyLine_returnsNull() {
        assertNull(ModulesSerializer.fromLine(""));
    }

    @Test
    public void fromLine_whitespaceOnly_returnsNull() {
        assertNull(ModulesSerializer.fromLine("   "));
    }

    @Test
    public void fromLine_missingMid_returnsNull() {
        String line = "|1.1.1.1|pid|chip|board|ver|123456|LAN_SCAN";
        assertNull(ModulesSerializer.fromLine(line));
    }

    @Test
    public void fromLine_insufficientFields_returnsNull() {
        // Only 7 fields, need 8
        String line = "MID|IP|PID|CHIP|BOARD|VER|123456";
        assertNull(ModulesSerializer.fromLine(line));
    }

    @Test
    public void fromLine_nonNumericAtTimestampField_returnsNull() {
        // Non-numeric at index 6 triggers legacy detection (needs 9 fields); with only 8 → null.
        String line = "TEST_MID|1.1.1.1|pid|chip|board|ver|not_a_number|LAN_SCAN";
        assertNull(ModulesSerializer.fromLine(line));
    }

    @Test
    public void fromLine_extraFields_ignoresExtraContent() {
        String line = "MID|IP|PID|CHIP|BOARD|VER|123456|LAN_SCAN|extra|junk";
        Module module = ModulesSerializer.fromLine(line);

        assertNotNull(module);
        assertEquals("MID", module.getModuleId());
        assertEquals(DiscoverySource.LAN_SCAN, module.getLastDiscoverySource());
    }

    @Test
    public void fromLine_malformedLine_returnsNull() {
        assertNull(ModulesSerializer.fromLine("this is not a valid module line at all"));
    }

    // --------------------------------------------------------
    // --- REAL-WORLD EXAMPLES --------------------------------

    @Test
    public void fromLine_legacyFormat_parsesCorrectly() {
        // Legacy format had sts at index 6: mid|ip|pid|chip|brd|ver|sts|lastSeenAt|source
        // ModulesSerializer still supports this via legacy detection.
        String line = "ESP32_5C7A|192.168.1.42|sensor_gateway|ESP32|v1.2|3.0|AP|1705123456789|LAN_SCAN";
        Module module = ModulesSerializer.fromLine(line);

        assertNotNull(module);
        assertEquals("ESP32_5C7A", module.getModuleId());
        assertEquals("192.168.1.42", module.getIp());
        assertEquals(1705123456789L, module.getLastSeenAt());
        assertEquals(DiscoverySource.LAN_SCAN, module.getLastDiscoverySource());
    }

    @Test
    public void fromLine_moduleWithSpecialCharactersInProfileId_works() {
        String line = "TEST|1.1.1.1|firmware_v2.1-beta|ESP32|board|1.0|123456|LAN_SCAN";
        Module module = ModulesSerializer.fromLine(line);

        assertNotNull(module);
        assertEquals("firmware_v2.1-beta", module.getProfileId());
    }

    // --------------------------------------------------------
    // --- REGRESSION -----------------------------------------

    @Test
    public void fromLine_extraFieldsWithLowercaseEnum_parsesCorrectly() {
        String line = "MID|IP|PID|CHIP|BOARD|VER|123456|lan_scan|extra|junk";
        Module module = ModulesSerializer.fromLine(line);

        assertNotNull(module);
        assertEquals(DiscoverySource.LAN_SCAN, module.getLastDiscoverySource());
    }

    @Test
    public void fromLine_emptyDiscoveryField_usesFallback() {
        String line = "MID|IP|PID|CHIP|BOARD|VER|123456|";
        Module module = ModulesSerializer.fromLine(line);

        assertNotNull(module);
        assertEquals(DiscoverySource.MQTT_PONG, module.getLastDiscoverySource());
    }

    @Test
    public void fromLine_mixedCaseDiscoverySource_parsesCorrectly() {
        String line = "MID|IP|PID|CHIP|BOARD|VER|123456|Udp_Discovery";
        Module module = ModulesSerializer.fromLine(line);

        assertNotNull(module);
        assertEquals(DiscoverySource.UDP_DISCOVERY, module.getLastDiscoverySource());
    }

    @Test
    public void fromLine_lowercaseManualIp_parsesCorrectly() {
        // MANUAL_IP contains 'I' — sensitive to Turkish locale; toUpperCase(Locale.ROOT) required
        String line = "MID|IP|PID|CHIP|BOARD|VER|123456|manual_ip";
        Module module = ModulesSerializer.fromLine(line);

        assertNotNull(module);
        assertEquals(DiscoverySource.MANUAL_IP, module.getLastDiscoverySource());
    }
}