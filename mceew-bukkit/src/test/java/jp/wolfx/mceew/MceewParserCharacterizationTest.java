package jp.wolfx.mceew;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MceewParserCharacterizationTest {
    @Test
    void canonicalRealtimePayloadsProduceCurrentGoldenNotifications() {
        assertRealtimeGolden("jma_eew",
                "§c緊急地震速報 (警報) | 第46報 最終報\n"
                        + " §e2024年01月01日 16時10分08秒 §f発生\n"
                        + " §f震央: §e能登半島沖 (北緯: §e37.6度 東経: §e137.2度)\n"
                        + " §fマグニチュード: §e7.4\n §f深さ: §e10km\n"
                        + " §f最大震度: §r§d7\n §f更新時間: §e%s",
                "§c緊急地震速報 (警報)", "§e能登半島沖で震度§d7§eの地震");
        assertRealtimeGolden("sc_eew",
                "§c四川地震预警 | 第1报\n §e2024年02月28日 21時23分30秒 §f发生\n"
                        + " §f震中: §e四川雅安市汉源县 (北纬: §e29.3度 东经: §e102.82度)\n"
                        + " §f震级: §e3.3\n §f深度: §e10km\n §f最大烈度: §r§a5\n"
                        + " §f更新时间: §e%s",
                "§c四川地震预警", "§e四川雅安市汉源县发生烈度§a5§e的地震");
        assertRealtimeGolden("fj_eew",
                "§c福建地震預警 | 第4報 最終報\n §e2024年02月29日 13時26分28秒 §f發生\n"
                        + " §f震央: §e台湾嘉义县 (北緯: §e23.47度 東經: §e120.26度)\n"
                        + " §f規模: §e4.4\n §f更新時間: §e%s",
                "§c福建地震預警", "§e台湾嘉义县发生震级4.4地震");
        assertRealtimeGolden("cwa_eew",
                "§c台灣地震預警 | 第2報\n §e2024年04月03日 07時58分10秒 §f發生\n"
                        + " §f震央: §e花蓮縣壽豐鄉 (北緯: §e23.89度 東經: §e121.56度)\n"
                        + " §f規模: §e6.8\n §f深度: §e20km\n §f最大震度: §r§c6弱\n"
                        + " §f更新時間: §e%s",
                "§c台灣地震預警", "§e花蓮縣壽豐鄉发生芮氏規模6.8地震");
        assertRealtimeGolden("cenc_eew",
                "§c中国地震台网地震预警 | 第1报\n §e2025年09月12日 05時50分58秒 §f发生\n"
                        + " §f震中: §e四川阿坝州红原县 (北纬: §e33.002度 东经: §e102.89度)\n"
                        + " §f震级: §e4.4\n §f深度: §e5km\n §f最大烈度: §r§26\n"
                        + " §f更新时间: §e%s",
                "§c中国地震台网地震预警", "§e四川阿坝州红原县发生震级4.4地震");
        assertRealtimeGolden("cq_eew",
                "§c重庆地震预警 | 第1报\n §e2026年08月07日 13時08分30秒 §f发生\n"
                        + " §f震中: §e四川宜宾市高县 (北纬: §e28.517度 东经: §e104.673度)\n"
                        + " §f震级: §e4.8\n §f深度: §e4km\n §f最大烈度: §r§e7\n"
                        + " §f更新时间: §e%s",
                "§c重庆地震预警", "§e四川宜宾市高县发生震级4.8地震");
    }

    @Test
    void jmaForecastProducesItsCurrentGoldenNotification() {
        JsonObject payload = freshObject("jma_eew");
        payload.addProperty("Title", "緊急地震速報（予報）");
        payload.addProperty("isFinal", false);
        assertPayloadGolden(payload.toString(),
                "§e緊急地震速報 (予報) | 第46報 \n"
                        + " §e2024年01月01日 16時10分08秒 §f発生\n"
                        + " §f震央: §e能登半島沖 (北緯: §e37.6度 東経: §e137.2度)\n"
                        + " §fマグニチュード: §e7.4\n §f深さ: §e10km\n"
                        + " §f最大震度: §r§d7\n §f更新時間: §e%s",
                "§e緊急地震速報 (予報)", "§e能登半島沖で震度§d7§eの地震");
    }

    @Test
    void compactTemplatesExposeEveryCurrentRealtimeParserValue() {
        YamlConfiguration config = MceewCharacterizationSupport.defaultConfiguration();
        String jma = "%flag%|%report_time%|%origin_time%|%num%|%lat%|%lon%|%region%|%mag%|%depth%|%shindo%|%type%";
        config.set("Message.Alert.broadcast", jma);
        config.set("Message.Forecast.broadcast", jma);
        String common = "%report_time%|%origin_time%|%num%|%lat%|%lon%|%region%|%mag%|%depth%|%shindo%";
        config.set("Message.Sichuan.broadcast", common);
        config.set("Message.Cwa.broadcast", common);
        config.set("Message.CencEEW.broadcast", common);
        config.set("Message.Chongqing.broadcast", common);
        config.set("Message.Fjea.broadcast",
                "%report_time%|%origin_time%|%num%|%lat%|%lon%|%region%|%mag%|%type%");

        assertCompact(config, "jma_eew",
                "警報|%s|2024年01月01日 16時10分08秒|46|37.6|137.2|能登半島沖|7.4|10km|§d7|最終報");
        assertCompact(config, "sc_eew",
                "%s|2024年02月28日 21時23分30秒|1|29.3|102.82|四川雅安市汉源县|3.3|10km|§a5");
        assertCompact(config, "fj_eew",
                "%s|2024年02月29日 13時26分28秒|4|23.47|120.26|台湾嘉义县|4.4|最終報");
        assertCompact(config, "cwa_eew",
                "%s|2024年04月03日 07時58分10秒|2|23.89|121.56|花蓮縣壽豐鄉|6.8|20km|§c6弱");
        assertCompact(config, "cenc_eew",
                "%s|2025年09月12日 05時50分58秒|1|33.002|102.89|四川阿坝州红原县|4.4|5km|§26");
        assertCompact(config, "cq_eew",
                "%s|2026年08月07日 13時08分30秒|1|28.517|104.673|四川宜宾市高县|4.8|4km|§e7");
    }

    @Test
    void jmaForecastAlertTrainingAssumptionFinalAndCancelTypesRemainDistinct() {
        assertJmaType("緊急地震速報（予報）", false, false, false, false, "予報||");
        assertJmaType("緊急地震速報（警報）", false, false, false, false, "警報||");
        assertJmaType("緊急地震速報（予報）", true, false, false, false, "予報|訓練|");
        assertJmaType("緊急地震速報（予報）", true, false, true, false, "予報|訓練 (最終報)|");
        assertJmaType("緊急地震速報（予報）", false, true, false, false, "予報|仮定震源|");
        assertJmaType("緊急地震速報（予報）", false, true, true, false, "予報|仮定震源 (最終報)|");
        assertJmaType("緊急地震速報（警報）", true, true, true, true, "警報|取消|");
    }

    @Test
    void currentDepthFallbacksAndJmaNullDepthBehaviorArePreserved() {
        for (String fixture : List.of("sc_eew", "cenc_eew", "cq_eew")) {
            JsonObject payload = freshObject(fixture);
            payload.add("Depth", null);
            MceewCharacterizationSupport.Harness harness = compactDepthHarness(fixture);
            harness.route(payload.toString());
            assertEquals(List.of("10km"), harness.console, fixture);
        }

        JsonObject jma = freshObject("jma_eew");
        jma.add("Depth", null);
        MceewCharacterizationSupport.Harness harness = compactDepthHarness("jma_eew");
        assertThrows(UnsupportedOperationException.class, () -> harness.route(jma.toString()));
    }

    @Test
    void changedEarthquakeListsProduceCurrentGoldenInformationMessages() {
        MceewCharacterizationSupport.Harness harness = MceewCharacterizationSupport.harness();
        JsonObject jma = MceewCharacterizationSupport.fixture("jma_eqlist");
        JsonObject cenc = MceewCharacterizationSupport.fixture("cenc_eqlist");
        harness.route(jma.toString());
        harness.route(cenc.toString());
        jma.addProperty("md5", "11111111111111111111111111111111");
        cenc.addProperty("md5", "22222222222222222222222222222222");
        harness.route(jma.toString());
        harness.route(cenc.toString());

        assertEquals("§e地震情報\n §e2024年01月01日 16時10分08秒 §f発生\n"
                        + " §f震央: §e能登半島沖 (北緯: §e37.6度 東経: §e137.2度)\n"
                        + " §fマグニチュード: §e7.4\n §f深さ: §e10km\n §f最大震度: §r§d7\n"
                        + " §f津波情報: §e津波警報等（大津波警報・津波警報あるいは津波注意報）を発表中",
                harness.console.get(0));
        assertEquals("§e中国地震台网 (正式测定)\n §e2026年08月09日 12時34分56秒 §f发生\n"
                        + " §f震中: §e四川测试地区 (北纬: §e30.1度 东经: §e120.2度)\n"
                        + " §f震级: §e5.2\n §f深度: §e10km\n §f最大烈度: §r§26",
                harness.console.get(1));
        assertEquals(harness.console, harness.player.chat);
        assertTrue(harness.player.titles.isEmpty());
        assertTrue(harness.player.sounds.isEmpty());
    }

    private static void assertRealtimeGolden(
            String fixture, String messagePattern, String title, String subtitle) {
        assertPayloadGolden(MceewCharacterizationSupport.freshPayload(fixture),
                messagePattern, title, subtitle);
    }

    private static void assertPayloadGolden(
            String payload, String messagePattern, String title, String subtitle) {
        MceewCharacterizationSupport.Harness harness = MceewCharacterizationSupport.harness();
        JsonObject json = JsonParser.parseString(payload).getAsJsonObject();
        String fixture = json.get("type").getAsString();
        String reportTime = json.has("AnnouncedTime")
                ? json.get("AnnouncedTime").getAsString()
                : json.get("ReportTime").getAsString();
        harness.route(payload);

        String expected = String.format(messagePattern, reportTime);
        assertEquals(List.of(expected), harness.console, fixture);
        assertEquals(List.of(expected), harness.player.chat, fixture);
        assertEquals(1, harness.player.titles.size(), fixture);
        MceewCharacterizationSupport.RecordedTitle actualTitle = harness.player.titles.get(0);
        assertEquals(title, actualTitle.title, fixture);
        assertEquals(subtitle, actualTitle.subtitle, fixture);
        assertEquals(10, actualTitle.fadeIn, fixture);
        assertEquals(70, actualTitle.stay, fixture);
        assertEquals(20, actualTitle.fadeOut, fixture);
        assertEquals(500, actualTitle.fadeIn * 50, fixture);
        assertEquals(3500, actualTitle.stay * 50, fixture);
        assertEquals(1000, actualTitle.fadeOut * 50, fixture);
        assertEquals(1, harness.player.sounds.size(), fixture);
        MceewCharacterizationSupport.RecordedSound sound = harness.player.sounds.get(0);
        assertEquals("block.note_block.pling", sound.key, fixture);
        assertEquals(1000.0F, sound.volume, fixture);
        assertEquals(1.0F, sound.pitch, fixture);
    }

    private static void assertCompact(YamlConfiguration config, String fixture, String pattern) {
        MceewCharacterizationSupport.Harness harness =
                MceewCharacterizationSupport.harness(config);
        String payload = MceewCharacterizationSupport.freshPayload(fixture);
        JsonObject json = JsonParser.parseString(payload).getAsJsonObject();
        String report = json.has("AnnouncedTime")
                ? json.get("AnnouncedTime").getAsString()
                : json.get("ReportTime").getAsString();
        harness.route(payload);
        assertEquals(List.of(String.format(pattern, report)), harness.console, fixture);
    }

    private static void assertJmaType(
            String title, boolean training, boolean assumption, boolean last, boolean cancel,
            String expected) {
        YamlConfiguration config = MceewCharacterizationSupport.defaultConfiguration();
        config.set("Message.Alert.broadcast", "%flag%|%type%|");
        config.set("Message.Forecast.broadcast", "%flag%|%type%|");
        MceewCharacterizationSupport.Harness harness =
                MceewCharacterizationSupport.harness(config);
        JsonObject payload = freshObject("jma_eew");
        payload.addProperty("Title", title);
        payload.addProperty("isTraining", training);
        payload.addProperty("isAssumption", assumption);
        payload.addProperty("isFinal", last);
        payload.addProperty("isCancel", cancel);
        harness.route(payload.toString());
        assertEquals(List.of(expected), harness.console);
    }

    private static MceewCharacterizationSupport.Harness compactDepthHarness(String fixture) {
        YamlConfiguration config = MceewCharacterizationSupport.defaultConfiguration();
        String path;
        switch (fixture) {
            case "sc_eew": path = "Message.Sichuan.broadcast"; break;
            case "cenc_eew": path = "Message.CencEEW.broadcast"; break;
            case "cq_eew": path = "Message.Chongqing.broadcast"; break;
            default: path = "Message.Alert.broadcast";
        }
        config.set(path, "%depth%");
        return MceewCharacterizationSupport.harness(config);
    }

    private static JsonObject freshObject(String fixture) {
        return JsonParser.parseString(
                MceewCharacterizationSupport.freshPayload(fixture)).getAsJsonObject();
    }
}
