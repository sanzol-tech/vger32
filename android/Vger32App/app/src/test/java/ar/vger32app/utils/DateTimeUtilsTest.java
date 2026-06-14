package ar.vger32app.utils;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/*
 * Each test uses a value well inside its range to avoid flakiness at boundaries.
 * Thresholds: <60 s → "Xs", <3600 s → "Xm", <86400 s → "Xh", else → "Xd".
 */
public class DateTimeUtilsTest {

    // --------------------------------------------------------
    // --- SECONDS --------------------------------------------

    @Test
    public void formatElapsed_0seconds_returns0s() {
        assertEquals("0s", DateTimeUtils.formatElapsed(System.currentTimeMillis()));
    }

    @Test
    public void formatElapsed_30seconds_returns30s() {
        assertEquals("30s", DateTimeUtils.formatElapsed(System.currentTimeMillis() - 30_000));
    }

    @Test
    public void formatElapsed_59seconds_returns59s() {
        assertEquals("59s", DateTimeUtils.formatElapsed(System.currentTimeMillis() - 59_000));
    }

    // --------------------------------------------------------
    // --- MINUTES --------------------------------------------

    @Test
    public void formatElapsed_1minute_returns1m() {
        assertEquals("1m", DateTimeUtils.formatElapsed(System.currentTimeMillis() - 60_000));
    }

    @Test
    public void formatElapsed_90seconds_returns1m() {
        assertEquals("1m", DateTimeUtils.formatElapsed(System.currentTimeMillis() - 90_000));
    }

    @Test
    public void formatElapsed_30minutes_returns30m() {
        assertEquals("30m", DateTimeUtils.formatElapsed(System.currentTimeMillis() - 30 * 60_000L));
    }

    // --------------------------------------------------------
    // --- HOURS ----------------------------------------------

    @Test
    public void formatElapsed_1hour_returns1h() {
        assertEquals("1h", DateTimeUtils.formatElapsed(System.currentTimeMillis() - 3_600_000));
    }

    @Test
    public void formatElapsed_12hours_returns12h() {
        assertEquals("12h", DateTimeUtils.formatElapsed(System.currentTimeMillis() - 12 * 3_600_000L));
    }

    // --------------------------------------------------------
    // --- DAYS -----------------------------------------------

    @Test
    public void formatElapsed_1day_returns1d() {
        assertEquals("1d", DateTimeUtils.formatElapsed(System.currentTimeMillis() - 86_400_000L));
    }

    @Test
    public void formatElapsed_3days_returns3d() {
        assertEquals("3d", DateTimeUtils.formatElapsed(System.currentTimeMillis() - 3 * 86_400_000L));
    }
}
