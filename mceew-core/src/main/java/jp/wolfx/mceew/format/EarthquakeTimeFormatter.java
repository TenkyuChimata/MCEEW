package jp.wolfx.mceew.format;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Preserves the time conversion and freshness rules used by MCEEW events.
 */
public final class EarthquakeTimeFormatter {
    private EarthquakeTimeFormatter() {
    }

    public static String format(
            String inputPattern,
            String outputPattern,
            String timezone,
            String input
    ) {
        DateTimeFormatter inputFormatter = DateTimeFormatter.ofPattern(inputPattern);
        ZonedDateTime parsed = ZonedDateTime.parse(
                input, inputFormatter.withZone(ZoneId.of(timezone)));
        return parsed.format(DateTimeFormatter.ofPattern(outputPattern));
    }

    public static boolean isFresh(String reportTime, String pattern, ZoneId zone) {
        return isFresh(reportTime, pattern, zone, ZonedDateTime.now(zone));
    }

    public static boolean isFresh(
            String reportTime,
            String pattern,
            ZoneId zone,
            ZonedDateTime now
    ) {
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
            LocalDateTime parsed = LocalDateTime.parse(reportTime, formatter);
            ZonedDateTime report = parsed.atZone(zone);
            long difference = Math.abs(Duration.between(report, now).toMinutes());
            return difference <= 10;
        } catch (Exception error) {
            return true;
        }
    }
}
