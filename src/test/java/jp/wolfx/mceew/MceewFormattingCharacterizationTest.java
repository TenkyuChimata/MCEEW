package jp.wolfx.mceew;

import com.google.gson.JsonObject;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MceewFormattingCharacterizationTest {
    private static final ZoneId TOKYO = ZoneId.of("Asia/Tokyo");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");

    @Test
    void freshnessUsesAbsoluteTruncatedMinutesAndInclusiveTenMinuteBoundary() {
        MceewCharacterizationSupport.Harness harness = MceewCharacterizationSupport.harness();
        ZonedDateTime now = ZonedDateTime.of(2026, 8, 12, 14, 30, 0, 0, TOKYO);

        assertTrue(fresh(harness, now, now));
        assertTrue(fresh(harness, now.minusMinutes(9).minusSeconds(59), now));
        assertTrue(fresh(harness, now.minusMinutes(10), now));
        assertTrue(fresh(harness, now.minusMinutes(10).minusSeconds(1), now),
                "Duration.toMinutes truncates 10m01s to 10");
        assertFalse(fresh(harness, now.minusMinutes(11), now));
        assertTrue(fresh(harness, now.plusMinutes(9).plusSeconds(59), now));
        assertTrue(fresh(harness, now.plusMinutes(10).plusSeconds(1), now),
                "future timestamps use the same absolute/truncated-minute rule");
        assertFalse(fresh(harness, now.plusMinutes(11), now));
    }

    @Test
    void invalidTimestampPreservesCurrentFreshnessBehavior() {
        MceewCharacterizationSupport.Harness harness = MceewCharacterizationSupport.harness();
        ZonedDateTime now = ZonedDateTime.of(2026, 8, 12, 14, 30, 0, 0, TOKYO);

        assertTrue((Boolean) MceewCharacterizationSupport.invoke(
                harness.plugin, "isFresh",
                new Class<?>[]{String.class, String.class, ZoneId.class, ZonedDateTime.class},
                "not-a-time", "yyyy/MM/dd HH:mm:ss", TOKYO, now));
    }

    @Test
    void staleRealtimePayloadIsIgnoredAndInvalidTimestampIsCurrentlyAccepted() {
        MceewCharacterizationSupport.Harness harness = MceewCharacterizationSupport.harness();
        JsonObject payload = MceewCharacterizationSupport.fixture("jma_eew");
        payload.addProperty("AnnouncedTime", "2000/01/01 00:00:00");
        harness.route(payload.toString());
        assertTrue(harness.console.isEmpty());

        payload.addProperty("AnnouncedTime", "invalid");
        harness.route(payload.toString());
        assertEquals(1, harness.console.size());
    }

    @Test
    void stalePayloadFromEveryRealtimeSourceIsIgnored() {
        Map<String, String> timeFieldByFixture = Map.of(
                "jma_eew", "AnnouncedTime",
                "sc_eew", "ReportTime",
                "fj_eew", "ReportTime",
                "cwa_eew", "ReportTime",
                "cenc_eew", "ReportTime",
                "cq_eew", "ReportTime");
        for (Map.Entry<String, String> entry : timeFieldByFixture.entrySet()) {
            MceewCharacterizationSupport.Harness harness =
                    MceewCharacterizationSupport.harness();
            JsonObject payload = MceewCharacterizationSupport.fixture(entry.getKey());
            payload.addProperty(entry.getValue(), entry.getKey().equals("jma_eew")
                    ? "2000/01/01 00:00:00" : "2000-01-01 00:00:00");
            harness.route(payload.toString());
            assertTrue(harness.console.isEmpty(), entry.getKey());
            assertTrue(harness.player.chat.isEmpty(), entry.getKey());
            assertTrue(harness.player.titles.isEmpty(), entry.getKey());
            assertTrue(harness.player.sounds.isEmpty(), entry.getKey());
        }
    }

    @Test
    void shindoLegacyColorsPreserveBoundsAliasesAndFallback() {
        MceewCharacterizationSupport.Harness harness = MceewCharacterizationSupport.harness();
        assertEquals("§f0", shindo(harness, "0"));
        assertEquals("§71", shindo(harness, "1"));
        assertEquals("§93", shindo(harness, "3"));
        assertEquals("§e5弱", shindo(harness, "5弱"));
        assertEquals("§e5-", shindo(harness, "5-"));
        assertEquals("§65強", shindo(harness, "5強"));
        assertEquals("§c6弱", shindo(harness, "6弱"));
        assertEquals("§46+", shindo(harness, "6+"));
        assertEquals("§d7", shindo(harness, "7"));
        assertEquals("§funexpected", shindo(harness, "unexpected"));
    }

    @Test
    void chinaIntensityLegacyColorsRoundAndClampCurrentInput() {
        MceewCharacterizationSupport.Harness harness = MceewCharacterizationSupport.harness();
        assertEquals("§f-2", intensity(harness, "-2"));
        assertEquals("§f0", intensity(harness, "0"));
        assertEquals("§33", intensity(harness, "3"));
        assertEquals("§a5", intensity(harness, "5"));
        assertEquals("§26.1", intensity(harness, "6.1"));
        assertEquals("§e6.6", intensity(harness, "6.6"));
        assertEquals("§512", intensity(harness, "12"));
        assertEquals("§599", intensity(harness, "99"));
        assertThrows(NumberFormatException.class, () -> intensity(harness, "unexpected"));
    }

    @Test
    void runtimeConfigurationUnconditionallyReplacesEveryAmpersand() {
        YamlConfiguration config = MceewCharacterizationSupport.defaultConfiguration();
        config.set("Message.Alert.broadcast", "&cTest|A&B|&&");
        MceewCharacterizationSupport.Harness harness =
                MceewCharacterizationSupport.harness(config);
        harness.routeFresh("jma_eew");

        assertEquals(List.of("§cTest|A§B|§§"), harness.console);
    }

    @Test
    void dollarInPlaceholderReplacementPreservesCurrentReplaceAllFailure() {
        YamlConfiguration config = MceewCharacterizationSupport.defaultConfiguration();
        config.set("Message.Alert.broadcast", "%region%");
        config.set("Action.title", false);
        config.set("Action.alert", false);
        MceewCharacterizationSupport.Harness harness =
                MceewCharacterizationSupport.harness(config);
        JsonObject payload = freshJma();
        payload.addProperty("Hypocenter", "$1");

        assertThrows(IndexOutOfBoundsException.class, () -> harness.route(payload.toString()));
    }

    @Test
    void backslashInPlaceholderReplacementIsCurrentlyConsumedByReplaceAll() {
        YamlConfiguration config = MceewCharacterizationSupport.defaultConfiguration();
        config.set("Message.Alert.broadcast", "%region%");
        config.set("Message.Alert.title", "fixed");
        config.set("Message.Alert.subtitle", "fixed");
        MceewCharacterizationSupport.Harness harness =
                MceewCharacterizationSupport.harness(config);
        JsonObject payload = freshJma();
        payload.addProperty("Hypocenter", "A\\B");
        harness.route(payload.toString());

        assertEquals(List.of("AB"), harness.console);
        assertEquals(List.of("AB"), harness.player.chat);
    }

    @Test
    void earthquakeListTemplatesCoverInfoAndCencFlagPlaceholdersExactly() {
        YamlConfiguration config = MceewCharacterizationSupport.defaultConfiguration();
        config.set("Message.Jma.broadcast",
                "%origin_time%|%lat%|%lon%|%region%|%mag%|%depth%|%shindo%|%info%");
        config.set("Message.Cenc.broadcast",
                "%flag%|%origin_time%|%lat%|%lon%|%region%|%mag%|%depth%|%shindo%");
        MceewCharacterizationSupport.Harness harness =
                MceewCharacterizationSupport.harness(config);
        JsonObject jma = MceewCharacterizationSupport.fixture("jma_eqlist");
        JsonObject cenc = MceewCharacterizationSupport.fixture("cenc_eqlist");
        harness.route(jma.toString());
        harness.route(cenc.toString());
        jma.addProperty("md5", "11111111111111111111111111111111");
        cenc.addProperty("md5", "22222222222222222222222222222222");
        harness.route(jma.toString());
        harness.route(cenc.toString());

        assertEquals("2024年01月01日 16時10分08秒|37.6|137.2|能登半島沖|7.4|10km|§d7|"
                        + "津波警報等（大津波警報・津波警報あるいは津波注意報）を発表中",
                harness.console.get(0));
        assertEquals("正式测定|2026年08月09日 12時34分56秒|30.1|120.2|四川测试地区|5.2|10km|§26",
                harness.console.get(1));
    }

    private static boolean fresh(
            MceewCharacterizationSupport.Harness harness,
            ZonedDateTime report, ZonedDateTime now) {
        return (Boolean) MceewCharacterizationSupport.invoke(
                harness.plugin, "isFresh",
                new Class<?>[]{String.class, String.class, ZoneId.class, ZonedDateTime.class},
                report.format(TIME), "yyyy/MM/dd HH:mm:ss", TOKYO, now);
    }

    private static String shindo(MceewCharacterizationSupport.Harness harness, String value) {
        return (String) MceewCharacterizationSupport.invoke(
                harness.plugin, "getShindoColor", new Class<?>[]{String.class}, value);
    }

    private static String intensity(MceewCharacterizationSupport.Harness harness, String value) {
        return (String) MceewCharacterizationSupport.invoke(
                harness.plugin, "getIntensityColor", new Class<?>[]{String.class}, value);
    }

    private static JsonObject freshJma() {
        return com.google.gson.JsonParser.parseString(
                MceewCharacterizationSupport.freshPayload("jma_eew")).getAsJsonObject();
    }
}
