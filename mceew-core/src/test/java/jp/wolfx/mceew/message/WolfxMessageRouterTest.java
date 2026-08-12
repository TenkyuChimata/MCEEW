package jp.wolfx.mceew.message;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WolfxMessageRouterTest {
    private final WolfxMessageRouter router = new WolfxMessageRouter();

    @Test
    void routesEveryCurrentWireTypeWithoutApplyingPlatformPolicy() {
        Map<String, WolfxMessageRouter.MessageType> types = Map.ofEntries(
                Map.entry("jma_eew", WolfxMessageRouter.MessageType.JMA_EEW),
                Map.entry("jma_eqlist", WolfxMessageRouter.MessageType.JMA_EARTHQUAKE_LIST),
                Map.entry("sc_eew", WolfxMessageRouter.MessageType.SICHUAN_EEW),
                Map.entry("fj_eew", WolfxMessageRouter.MessageType.FUJIAN_EEW),
                Map.entry("cwa_eew", WolfxMessageRouter.MessageType.CWA_EEW),
                Map.entry("cenc_eew", WolfxMessageRouter.MessageType.CENC_EEW),
                Map.entry("cq_eew", WolfxMessageRouter.MessageType.CHONGQING_EEW),
                Map.entry("cenc_eqlist", WolfxMessageRouter.MessageType.CENC_EARTHQUAKE_LIST),
                Map.entry("heartbeat", WolfxMessageRouter.MessageType.HEARTBEAT),
                Map.entry("future_source", WolfxMessageRouter.MessageType.UNKNOWN));

        for (Map.Entry<String, WolfxMessageRouter.MessageType> entry : types.entrySet()) {
            WolfxMessageRouter.RoutedMessage routed = router.route(
                    "{\"type\":\"" + entry.getKey() + "\"}");
            assertEquals(entry.getValue(), routed.getType(), entry.getKey());
            assertEquals(entry.getKey(), routed.getWireType(), entry.getKey());
            assertEquals(entry.getKey(), routed.getPayload().get("type").getAsString());
        }
    }

    @Test
    void malformedJsonAndMissingTypePreserveTheCurrentApplicationExceptions() {
        assertThrows(JsonSyntaxException.class, () -> router.route("{not-json"));
        assertThrows(NullPointerException.class, () -> router.route("{}"));
    }

    @Test
    void missingAndWrongParserFieldsPreserveCurrentGsonConversionBehavior() {
        JsonObject missing = fixture("cenc_eew");
        missing.remove("ReportNum");
        assertThrows(NullPointerException.class,
                () -> router.parseRealtime(router.route(missing.toString())));

        JsonObject primitiveTypes = fixture("cenc_eew");
        primitiveTypes.addProperty("ReportNum", 1);
        primitiveTypes.addProperty("MaxIntensity", 6.1F);
        RegionalEewEvent event = (RegionalEewEvent) router.parseRealtime(
                router.route(primitiveTypes.toString()));
        assertEquals("1", event.getReportNumber());
        assertEquals("6", event.getMaximumIntensity());

        JsonObject objectValue = fixture("cenc_eew");
        objectValue.add("MaxIntensity", new JsonObject());
        assertThrows(UnsupportedOperationException.class,
                () -> router.parseRealtime(router.route(objectValue.toString())));
    }

    @Test
    void nonRealtimeMessagesCannotBeParsedAsRealtimeEvents() {
        for (String type : new String[]{"heartbeat", "jma_eqlist", "cenc_eqlist", "future_source"}) {
            WolfxMessageRouter.RoutedMessage routed = router.route(
                    "{\"type\":\"" + type + "\"}");
            assertThrows(IllegalArgumentException.class,
                    () -> router.parseRealtime(routed), type);
        }
    }

    @Test
    void parsesCanonicalJmaEventWithoutFormattingItsSemanticValues() {
        JmaEewEvent event = (JmaEewEvent) router.parseRealtime(routeFixture("jma_eew"));

        assertEquals("警報", event.getFlag());
        assertEquals("2026/08/12 13:00:00", event.getReportTime());
        assertEquals("2024/01/01 16:10:08", event.getOriginTime());
        assertEquals("46", event.getReportNumber());
        assertEquals("37.6", event.getLatitude());
        assertEquals("137.2", event.getLongitude());
        assertEquals("能登半島沖", event.getRegion());
        assertEquals("7.4", event.getMagnitude());
        assertEquals("10", event.getDepth());
        assertEquals("7", event.getMaximumIntensity());
        assertFalse(event.isTraining());
        assertFalse(event.isAssumption());
        assertTrue(event.isFinalReport());
        assertFalse(event.isCancelled());
    }

    @Test
    void preservesAllCurrentJmaStatusInputsIndependently() {
        assertJmaStatus("緊急地震速報（予報）", false, false, false, false,
                "予報", false, false, false, false);
        assertJmaStatus("緊急地震速報（警報）", false, false, true, false,
                "警報", false, false, true, false);
        assertJmaStatus("緊急地震速報（予報）", true, false, false, false,
                "予報", true, false, false, false);
        assertJmaStatus("緊急地震速報（予報）", false, true, false, false,
                "予報", false, true, false, false);
        assertJmaStatus("緊急地震速報（警報）", true, true, true, true,
                "警報", true, true, true, true);
    }

    @Test
    void jmaNullDepthPreservesTheCurrentFailure() {
        JsonObject payload = fixture("jma_eew");
        payload.add("Depth", null);
        WolfxMessageRouter.RoutedMessage routed = router.route(payload.toString());

        assertThrows(UnsupportedOperationException.class,
                () -> router.parseRealtime(routed));
    }

    @Test
    void parsesSichuanFieldsAndPreservesNullDepthAndRoundingBehavior() {
        RegionalEewEvent event = regional("sc_eew");
        assertRegional(event, RegionalEewEvent.Source.SICHUAN,
                "2026-08-12 13:00:00", "2024-02-28 21:23:30", "1",
                "29.3", "102.82", "四川雅安市汉源县", "3.3", "10", "5");

        JsonObject payload = fixture("sc_eew");
        payload.add("Depth", null);
        payload.addProperty("MaxIntensity", "5.5");
        event = (RegionalEewEvent) router.parseRealtime(router.route(payload.toString()));
        assertEquals("10", event.getDepth());
        assertEquals("6", event.getMaximumIntensity());
    }

    @Test
    void parsesFujianFieldsAndPreservesFinalReportInput() {
        FujianEewEvent event = (FujianEewEvent) router.parseRealtime(routeFixture("fj_eew"));
        assertEquals("2026-08-12 13:00:00", event.getReportTime());
        assertEquals("2024-02-29 13:26:28", event.getOriginTime());
        assertEquals("4", event.getReportNumber());
        assertEquals("23.47", event.getLatitude());
        assertEquals("120.26", event.getLongitude());
        assertEquals("台湾嘉义县", event.getRegion());
        assertEquals("4.4", event.getMagnitude());
        assertTrue(event.isFinalReport());

        JsonObject payload = fixture("fj_eew");
        payload.addProperty("isFinal", false);
        event = (FujianEewEvent) router.parseRealtime(router.route(payload.toString()));
        assertFalse(event.isFinalReport());
    }

    @Test
    void parsesCwaFieldsWithoutNormalizingItsShindo() {
        RegionalEewEvent event = regional("cwa_eew");
        assertRegional(event, RegionalEewEvent.Source.CWA,
                "2026-08-12 13:00:00", "2024-04-03 07:58:10", "2",
                "23.89", "121.56", "花蓮縣壽豐鄉", "6.8", "20", "6弱");
    }

    @Test
    void parsesCencFieldsAndPreservesMagnitudeSpellingNullDepthAndRounding() {
        RegionalEewEvent event = regional("cenc_eew");
        assertRegional(event, RegionalEewEvent.Source.CENC,
                "2026-08-12 13:00:00", "2025-09-12 05:50:58", "1",
                "33.002", "102.89", "四川阿坝州红原县", "4.4", "5", "6");

        JsonObject payload = fixture("cenc_eew");
        payload.add("Depth", null);
        payload.addProperty("MaxIntensity", "6.6");
        event = (RegionalEewEvent) router.parseRealtime(router.route(payload.toString()));
        assertEquals("10", event.getDepth());
        assertEquals("7", event.getMaximumIntensity());
    }

    @Test
    void parsesEveryChongqingFieldAndPreservesNullDepthAndRounding() {
        RegionalEewEvent event = regional("cq_eew");
        assertRegional(event, RegionalEewEvent.Source.CHONGQING,
                "2026-08-12 13:00:00", "2026-08-07 13:08:30", "1",
                "28.517", "104.673", "四川宜宾市高县", "4.8", "4", "7");

        JsonObject payload = fixture("cq_eew");
        payload.add("Depth", null);
        payload.addProperty("MaxIntensity", "4.4");
        event = (RegionalEewEvent) router.parseRealtime(router.route(payload.toString()));
        assertEquals("10", event.getDepth());
        assertEquals("4", event.getMaximumIntensity());
    }

    private void assertJmaStatus(
            String title,
            boolean training,
            boolean assumption,
            boolean finalReport,
            boolean cancelled,
            String expectedFlag,
            boolean expectedTraining,
            boolean expectedAssumption,
            boolean expectedFinal,
            boolean expectedCancelled
    ) {
        JsonObject payload = fixture("jma_eew");
        payload.addProperty("Title", title);
        payload.addProperty("isTraining", training);
        payload.addProperty("isAssumption", assumption);
        payload.addProperty("isFinal", finalReport);
        payload.addProperty("isCancel", cancelled);

        JmaEewEvent event = (JmaEewEvent) router.parseRealtime(router.route(payload.toString()));
        assertEquals(expectedFlag, event.getFlag());
        assertEquals(expectedTraining, event.isTraining());
        assertEquals(expectedAssumption, event.isAssumption());
        assertEquals(expectedFinal, event.isFinalReport());
        assertEquals(expectedCancelled, event.isCancelled());
    }

    private RegionalEewEvent regional(String fixture) {
        return (RegionalEewEvent) router.parseRealtime(routeFixture(fixture));
    }

    private WolfxMessageRouter.RoutedMessage routeFixture(String name) {
        return router.route(fixture(name).toString());
    }

    private static void assertRegional(
            RegionalEewEvent event,
            RegionalEewEvent.Source source,
            String reportTime,
            String originTime,
            String reportNumber,
            String latitude,
            String longitude,
            String region,
            String magnitude,
            String depth,
            String maximumIntensity
    ) {
        assertEquals(source, event.getSource());
        assertEquals(reportTime, event.getReportTime());
        assertEquals(originTime, event.getOriginTime());
        assertEquals(reportNumber, event.getReportNumber());
        assertEquals(latitude, event.getLatitude());
        assertEquals(longitude, event.getLongitude());
        assertEquals(region, event.getRegion());
        assertEquals(magnitude, event.getMagnitude());
        assertEquals(depth, event.getDepth());
        assertEquals(maximumIntensity, event.getMaximumIntensity());
    }

    private static JsonObject fixture(String name) {
        String path = "websocket/current-schema/" + name + ".json";
        try (InputStream input = WolfxMessageRouterTest.class
                .getClassLoader().getResourceAsStream(path)) {
            if (input == null) {
                throw new IllegalStateException("Fixture not found: " + path);
            }
            return JsonParser.parseString(
                    new String(input.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException error) {
            throw new IllegalStateException("Unable to read fixture " + path, error);
        }
    }
}
