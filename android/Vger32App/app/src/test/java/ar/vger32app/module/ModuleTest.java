package ar.vger32app.module;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/*
 * Unit tests for Module.isExpired() and isOnline().
 * Uses the disk-restore constructor to inject a specific lastSeenAt.
 */
public class ModuleTest {

    private static final long ONE_MINUTE_MS = 60_000;
    private static final long TWO_MINUTES_MS = 120_000;

    private Module moduleSeenAgo(long msAgo) {
        return new Module(
                "TEST_MODULE",
                "192.168.1.1",
                "pid",
                "ESP32",
                "board",
                "1.0.0",
                System.currentTimeMillis() - msAgo,
                DiscoverySource.LAN_SCAN
        );
    }

    // --------------------------------------------------------
    // --- isExpired ------------------------------------------

    @Test
    public void isExpired_recentModule_returnsFalse() {
        Module m = moduleSeenAgo(ONE_MINUTE_MS);
        assertFalse(m.isExpired(TWO_MINUTES_MS));
    }

    @Test
    public void isExpired_oldModule_returnsTrue() {
        Module m = moduleSeenAgo(TWO_MINUTES_MS + 1000);
        assertTrue(m.isExpired(TWO_MINUTES_MS));
    }

    // --------------------------------------------------------
    // --- isOnline — OFFLINE_TIMEOUT_MS = 90 seconds ---------

    @Test
    public void isOnline_seenJustNow_returnsTrue() {
        Module m = moduleSeenAgo(0);
        assertTrue(m.isOnline());
    }

    @Test
    public void isOnline_seenOver90SecondsAgo_returnsFalse() {
        Module m = moduleSeenAgo(91_000);
        assertFalse(m.isOnline());
    }
}