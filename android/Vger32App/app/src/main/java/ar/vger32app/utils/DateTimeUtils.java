package ar.vger32app.utils;

import android.os.SystemClock;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

/*
 * Shared date/time formatting utilities.
 */

public final class DateTimeUtils {

    private DateTimeUtils() {
    }

    public static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("HH:mm:ss");

    // Universal time unit symbols — not internationalized by design.
    // Single-char abbreviations are universally understood.
    public static String formatElapsed(long lastSeenMs) {
        long elapsed = System.currentTimeMillis() - lastSeenMs;
        if (elapsed < 60_000) return (elapsed / 1000) + "s";
        if (elapsed < 3_600_000) return (elapsed / 60_000) + "m";
        if (elapsed < 86_400_000) return (elapsed / 3_600_000) + "h";
        return (elapsed / 86_400_000) + "d";
    }

    public static String formatUptimeMicros(long uptimeMicros) {
        long bootMs = System.currentTimeMillis() - SystemClock.elapsedRealtime();
        long wallMs = bootMs + TimeUnit.MICROSECONDS.toMillis(uptimeMicros);
        return Instant.ofEpochMilli(wallMs)
                .atZone(ZoneId.systemDefault())
                .toLocalTime()
                .format(TIME_FORMATTER);
    }
}