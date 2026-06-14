package ar.vger32app.localizer;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/*
 * Unit tests for WifiNetwork.frequencyToChannel().
 * Covers 2.4GHz channel 1–13, channel 14 (2484 MHz), 5GHz, and unknown frequencies.
 */
public class WifiNetworkTest {

    // --------------------------------------------------------
    // --- 2.4GHz — standard channels -------------------------

    @Test
    public void frequencyToChannel_2412MHz_returnsChannel1() {
        assertEquals(1, WifiNetwork.frequencyToChannel(2412));
    }

    @Test
    public void frequencyToChannel_2437MHz_returnsChannel6() {
        assertEquals(6, WifiNetwork.frequencyToChannel(2437));
    }

    @Test
    public void frequencyToChannel_2462MHz_returnsChannel11() {
        assertEquals(11, WifiNetwork.frequencyToChannel(2462));
    }

    @Test
    public void frequencyToChannel_2472MHz_returnsChannel13() {
        assertEquals(13, WifiNetwork.frequencyToChannel(2472));
    }

    // --------------------------------------------------------
    // --- 2.4GHz — channel 14 (Japan only, special case) -----

    @Test
    public void frequencyToChannel_2484MHz_returnsChannel14() {
        assertEquals(14, WifiNetwork.frequencyToChannel(2484));
    }

    // --------------------------------------------------------
    // --- 5GHz -----------------------------------------------

    @Test
    public void frequencyToChannel_5180MHz_returnsChannel36() {
        assertEquals(36, WifiNetwork.frequencyToChannel(5180));
    }

    @Test
    public void frequencyToChannel_5825MHz_returnsChannel165() {
        assertEquals(165, WifiNetwork.frequencyToChannel(5825));
    }

    // --------------------------------------------------------
    // --- Unknown --------------------------------------------

    @Test
    public void frequencyToChannel_unknown_returnsZero() {
        assertEquals(0, WifiNetwork.frequencyToChannel(0));
        assertEquals(0, WifiNetwork.frequencyToChannel(3000));
    }
}