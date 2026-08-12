package jp.wolfx.mceew.format;

import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EarthquakeTimeFormatterTest {
    private static final ZoneId TOKYO = ZoneId.of("Asia/Tokyo");

    @Test
    void formatsTokyoAndShanghaiTimestampsWithTheExistingPatterns() {
        assertEquals("2024年01月01日 16時10分08秒", EarthquakeTimeFormatter.format(
                "yyyy/MM/dd HH:mm:ss", "yyyy年MM月dd日 HH時mm分ss秒",
                "Asia/Tokyo", "2024/01/01 16:10:08"));
        assertEquals("2026年08月07日 13時08分30秒", EarthquakeTimeFormatter.format(
                "yyyy-MM-dd HH:mm:ss", "yyyy年MM月dd日 HH時mm分ss秒",
                "Asia/Shanghai", "2026-08-07 13:08:30"));
    }

    @Test
    void formattingFailureStillPropagates() {
        assertThrows(DateTimeParseException.class, () -> EarthquakeTimeFormatter.format(
                "yyyy/MM/dd HH:mm:ss", "yyyy年MM月dd日 HH時mm分ss秒",
                "Asia/Tokyo", "invalid"));
    }

    @Test
    void freshnessUsesAbsoluteTruncatedMinutesAndTheInclusiveBoundary() {
        ZonedDateTime now = ZonedDateTime.of(2026, 8, 12, 14, 30, 0, 0, TOKYO);

        assertTrue(fresh(now, now));
        assertTrue(fresh(now.minusMinutes(9).minusSeconds(59), now));
        assertTrue(fresh(now.minusMinutes(10), now));
        assertTrue(fresh(now.minusMinutes(10).minusSeconds(1), now));
        assertFalse(fresh(now.minusMinutes(11), now));
        assertTrue(fresh(now.plusMinutes(9).plusSeconds(59), now));
        assertTrue(fresh(now.plusMinutes(10).plusSeconds(1), now));
        assertFalse(fresh(now.plusMinutes(11), now));
    }

    @Test
    void invalidTimestampPreservesTheCurrentFreshnessBehavior() {
        ZonedDateTime now = ZonedDateTime.of(2026, 8, 12, 14, 30, 0, 0, TOKYO);
        assertTrue(EarthquakeTimeFormatter.isFresh(
                "not-a-time", "yyyy/MM/dd HH:mm:ss", TOKYO, now));
    }

    private static boolean fresh(ZonedDateTime report, ZonedDateTime now) {
        return EarthquakeTimeFormatter.isFresh(
                report.format(java.time.format.DateTimeFormatter.ofPattern(
                        "yyyy/MM/dd HH:mm:ss")),
                "yyyy/MM/dd HH:mm:ss", TOKYO, now);
    }
}
