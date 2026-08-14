package jp.wolfx.mceew;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import jp.wolfx.mceew.BungeeMessageProcessor.Outcome;
import jp.wolfx.mceew.BungeeMessageProcessor.ProcessingPolicy;
import jp.wolfx.mceew.BungeeMessageProcessor.ProcessingResult;
import jp.wolfx.mceew.message.WolfxMessageRouter;
import org.junit.jupiter.api.Test;

class BungeeMessageProcessorTest {
    @Test
    void everyEnabledRealtimeSourceUsesTheCoreRouterAndProducesFreshEvent() {
        BungeeMessageProcessor processor = processor(allEnabled());
        Map<String, WolfxMessageRouter.MessageType> fixtures = new LinkedHashMap<>();
        fixtures.put("jma_eew", WolfxMessageRouter.MessageType.JMA_EEW);
        fixtures.put("sc_eew", WolfxMessageRouter.MessageType.SICHUAN_EEW);
        fixtures.put("fj_eew", WolfxMessageRouter.MessageType.FUJIAN_EEW);
        fixtures.put("cwa_eew", WolfxMessageRouter.MessageType.CWA_EEW);
        fixtures.put("cenc_eew", WolfxMessageRouter.MessageType.CENC_EEW);
        fixtures.put("cq_eew", WolfxMessageRouter.MessageType.CHONGQING_EEW);

        for (Map.Entry<String, WolfxMessageRouter.MessageType> fixture : fixtures.entrySet()) {
            ProcessingResult result = processor.process(fixture(fixture.getKey()));
            assertEquals(fixture.getValue(), result.messageType(), fixture.getKey());
            assertEquals(Outcome.FRESH_REALTIME, result.outcome(), fixture.getKey());
            assertNotNull(result.realtimeEvent(), fixture.getKey());
        }
    }

    @Test
    void everyRealtimeGateSkipsParsingBeforeMalformedPayloadFieldsAreRead() {
        String[] wireTypes = {"jma_eew", "sc_eew", "fj_eew", "cwa_eew", "cenc_eew", "cq_eew"};
        for (int disabled = 0; disabled < wireTypes.length; disabled++) {
            boolean[] flags = allEnabled();
            flags[disabled] = false;
            BungeeMessageProcessor processor = processor(flags);

            ProcessingResult result = processor.process(
                    "{\"type\":\"" + wireTypes[disabled] + "\"}");

            assertEquals(Outcome.DISABLED_REALTIME, result.outcome(), wireTypes[disabled]);
            assertNull(result.realtimeEvent(), wireTypes[disabled]);
        }
    }

    @Test
    void enabledMalformedRealtimeAndMalformedJsonPreserveTheApplicationBoundary() {
        BungeeMessageProcessor processor = processor(allEnabled());

        assertThrows(RuntimeException.class,
                () -> processor.process("{\"type\":\"jma_eew\"}"));
        assertThrows(RuntimeException.class, () -> processor.process("not-json"));
    }

    @Test
    void heartbeatAndUnknownMessagesAreIgnored() {
        BungeeMessageProcessor processor = processor(allEnabled());

        assertEquals(Outcome.IGNORED, processor.process(fixture("heartbeat")).outcome());
        assertEquals(Outcome.IGNORED, processor.process(fixture("unknown")).outcome());
    }

    @Test
    void jmaCachePreservesFirstUnchangedAndChangedTransitions() {
        BungeeMessageProcessor processor = processor(allDisabled());
        String initial = fixture("jma_eqlist");
        JsonObject changed = JsonParser.parseString(initial).getAsJsonObject();
        changed.addProperty("md5", "cccccccccccccccccccccccccccccccc");
        changed.getAsJsonObject("No1").addProperty("location", "更新された地域");

        assertEquals(Outcome.CACHE_FIRST_VALUE, processor.process(initial).outcome());
        assertTrue(processor.hasJmaCacheValue());
        assertEquals(Outcome.CACHE_UNCHANGED, processor.process(initial).outcome());
        ProcessingResult result = processor.process(changed.toString());
        assertEquals(Outcome.CACHE_CHANGED, result.outcome());
        assertEquals("更新された地域", processor.latestJmaEarthquakeList()
                .orElseThrow().render("%region%"));
    }

    @Test
    void cencCachePreservesFirstUnchangedAndChangedTransitions() {
        BungeeMessageProcessor processor = processor(allDisabled());
        String initial = fixture("cenc_eqlist");
        JsonObject changed = JsonParser.parseString(initial).getAsJsonObject();
        changed.addProperty("md5", "dddddddddddddddddddddddddddddddd");
        changed.getAsJsonObject("No1").addProperty("location", "更新地区");

        assertEquals(Outcome.CACHE_FIRST_VALUE, processor.process(initial).outcome());
        assertTrue(processor.hasCencCacheValue());
        assertEquals(Outcome.CACHE_UNCHANGED, processor.process(initial).outcome());
        ProcessingResult result = processor.process(changed.toString());
        assertEquals(Outcome.CACHE_CHANGED, result.outcome());
        assertEquals("更新地区", processor.latestCencEarthquakeList()
                .orElseThrow().render("%region%"));
    }

    @Test
    void disabledRealtimeGatesRemainIndependentFromBothEarthquakeListCaches() {
        BungeeMessageProcessor processor = processor(allDisabled());

        assertEquals(Outcome.CACHE_FIRST_VALUE,
                processor.process(fixture("jma_eqlist")).outcome());
        assertEquals(Outcome.CACHE_FIRST_VALUE,
                processor.process(fixture("cenc_eqlist")).outcome());
        assertTrue(processor.hasJmaCacheValue());
        assertTrue(processor.hasCencCacheValue());
    }

    @Test
    void immutablePolicyArgumentChangesGatesWithoutReplacingTheProcessorOrCache() {
        BungeeMessageProcessor processor = processor(allEnabled());
        ProcessingPolicy disabledSichuan = policy(true, false, true, true, true, true);
        ProcessingPolicy enabledSichuan = policy(true, true, true, true, true, true);

        assertEquals(Outcome.DISABLED_REALTIME,
                processor.process("{\"type\":\"sc_eew\"}", disabledSichuan).outcome());
        assertEquals(Outcome.FRESH_REALTIME,
                processor.process(fixture("sc_eew"), enabledSichuan).outcome());
        assertEquals(Outcome.CACHE_FIRST_VALUE,
                processor.process(fixture("jma_eqlist"), disabledSichuan).outcome());
        assertEquals(Outcome.CACHE_UNCHANGED,
                processor.process(fixture("jma_eqlist"), enabledSichuan).outcome());
    }

    @Test
    void earthquakeListPresentationUsesConfiguredTimeFormatAndCoreFormatting() {
        BungeeMessageProcessor processor = new BungeeMessageProcessor(
                false, false, false, false, false, false,
                "yyyy年MM月dd日 HH時mm分ss秒", (time, pattern, zone) -> true);

        ProcessingResult jma = processor.process(fixture("jma_eqlist"));
        ProcessingResult cenc = processor.process(fixture("cenc_eqlist"));

        assertEquals("2024年01月01日 16時10分08秒 能登半島沖 §d7",
                jma.earthquakeList().render("%origin_time% %region% %shindo%"));
        assertNotNull(cenc.earthquakeList());
    }

    private static BungeeMessageProcessor processor(boolean[] flags) {
        return new BungeeMessageProcessor(
                flags[0], flags[1], flags[2], flags[3], flags[4], flags[5],
                "yyyy/MM/dd HH:mm:ss", (time, pattern, zone) -> true);
    }

    private static ProcessingPolicy policy(
            boolean jma,
            boolean sichuan,
            boolean fujian,
            boolean cwa,
            boolean cenc,
            boolean chongqing
    ) {
        return new ProcessingPolicy(
                jma, sichuan, fujian, cwa, cenc, chongqing, "yyyy/MM/dd HH:mm:ss");
    }

    private static boolean[] allEnabled() {
        return new boolean[]{true, true, true, true, true, true};
    }

    private static boolean[] allDisabled() {
        return new boolean[]{false, false, false, false, false, false};
    }

    private static String fixture(String name) {
        Path root = Path.of(requiredSystemProperty("mceew.reactor.root"));
        Path fixture = root.resolve(
                "mceew-bukkit/src/test/resources/websocket/current-schema/" + name + ".json");
        try {
            return Files.readString(fixture, StandardCharsets.UTF_8);
        } catch (IOException error) {
            throw new IllegalStateException("Unable to read fixture: " + fixture, error);
        }
    }

    private static String requiredSystemProperty(String name) {
        String value = System.getProperty(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Required Maven test property is missing: " + name);
        }
        return value;
    }
}
