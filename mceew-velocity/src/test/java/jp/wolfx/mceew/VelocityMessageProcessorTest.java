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
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import jp.wolfx.mceew.VelocityMessageProcessor.Outcome;
import jp.wolfx.mceew.VelocityMessageProcessor.ProcessingResult;
import jp.wolfx.mceew.message.WolfxMessageRouter;
import org.junit.jupiter.api.Test;

class VelocityMessageProcessorTest {
    @Test
    void everyEnabledRealtimeSourceUsesTheCoreRouterAndProducesFreshEvent() {
        VelocityMessageProcessor processor = processor(allEnabled(), (time, pattern, zone) -> true);
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
    void eachDisabledRealtimeSourceSkipsParsingEvenWhenItsFieldsAreMalformed() {
        String[] wireTypes = {"jma_eew", "sc_eew", "fj_eew", "cwa_eew", "cenc_eew", "cq_eew"};
        for (int disabled = 0; disabled < wireTypes.length; disabled++) {
            boolean[] flags = allEnabled();
            flags[disabled] = false;
            VelocityMessageProcessor processor = processor(flags, (time, pattern, zone) -> {
                throw new AssertionError("Freshness must not run for a disabled source");
            });

            ProcessingResult result = processor.process(
                    "{\"type\":\"" + wireTypes[disabled] + "\"}");

            assertEquals(Outcome.DISABLED_REALTIME, result.outcome(), wireTypes[disabled]);
            assertNull(result.realtimeEvent(), wireTypes[disabled]);
        }
    }

    @Test
    void enabledMalformedRealtimePayloadPreservesApplicationExceptionBoundary() {
        VelocityMessageProcessor processor = processor(allEnabled(), (time, pattern, zone) -> true);

        assertThrows(RuntimeException.class,
                () -> processor.process("{\"type\":\"jma_eew\"}"));
    }

    @Test
    void freshnessUsesCurrentSourcePatternsAndZonesAndStopsStaleEvents() {
        Map<String, String> observed = new LinkedHashMap<>();
        VelocityMessageProcessor processor = processor(allEnabled(), (time, pattern, zone) -> {
            observed.put(pattern, zone.getId());
            return false;
        });

        ProcessingResult jma = processor.process(fixture("jma_eew"));
        ProcessingResult sichuan = processor.process(fixture("sc_eew"));

        assertEquals(Outcome.STALE_REALTIME, jma.outcome());
        assertEquals(Outcome.STALE_REALTIME, sichuan.outcome());
        assertNull(jma.realtimeEvent());
        assertNull(sichuan.realtimeEvent());
        assertEquals("Asia/Tokyo", observed.get("yyyy/MM/dd HH:mm:ss"));
        assertEquals("Asia/Shanghai", observed.get("yyyy-MM-dd HH:mm:ss"));
    }

    @Test
    void productionFreshnessHelperPreservesInvalidTimestampAsFresh() {
        JsonObject payload = JsonParser.parseString(fixture("jma_eew")).getAsJsonObject();
        payload.addProperty("AnnouncedTime", "not-a-timestamp");
        VelocityMessageProcessor processor = new VelocityMessageProcessor(
                true, true, true, true, true, true);

        ProcessingResult result = processor.process(payload.toString());

        assertEquals(Outcome.FRESH_REALTIME, result.outcome());
        assertNotNull(result.realtimeEvent());
    }

    @Test
    void heartbeatAndUnknownMessagesAreIgnored() {
        VelocityMessageProcessor processor = processor(allEnabled(), (time, pattern, zone) -> true);

        assertEquals(Outcome.IGNORED, processor.process(fixture("heartbeat")).outcome());
        assertEquals(Outcome.IGNORED, processor.process(fixture("unknown")).outcome());
    }

    @Test
    void jmaCachePreservesFirstUnchangedAndChangedTransitions() {
        VelocityMessageProcessor processor = processor(allDisabled(), (time, pattern, zone) -> true);
        String initial = fixture("jma_eqlist");
        JsonObject changed = JsonParser.parseString(initial).getAsJsonObject();
        changed.addProperty("md5", "cccccccccccccccccccccccccccccccc");

        assertEquals(Outcome.CACHE_FIRST_VALUE, processor.process(initial).outcome());
        assertTrue(processor.hasJmaCacheValue());
        assertEquals(Outcome.CACHE_UNCHANGED, processor.process(initial).outcome());
        assertEquals(Outcome.CACHE_CHANGED, processor.process(changed.toString()).outcome());
    }

    @Test
    void cencCachePreservesFirstUnchangedAndChangedTransitions() {
        VelocityMessageProcessor processor = processor(allDisabled(), (time, pattern, zone) -> true);
        String initial = fixture("cenc_eqlist");
        JsonObject changed = JsonParser.parseString(initial).getAsJsonObject();
        changed.addProperty("md5", "dddddddddddddddddddddddddddddddd");

        assertEquals(Outcome.CACHE_FIRST_VALUE, processor.process(initial).outcome());
        assertTrue(processor.hasCencCacheValue());
        assertEquals(Outcome.CACHE_UNCHANGED, processor.process(initial).outcome());
        assertEquals(Outcome.CACHE_CHANGED, processor.process(changed.toString()).outcome());
    }

    @Test
    void disabledRealtimeSourcesDoNotDisableEitherEarthquakeListCache() {
        VelocityMessageProcessor processor = processor(allDisabled(), (time, pattern, zone) -> true);

        assertEquals(Outcome.CACHE_FIRST_VALUE,
                processor.process(fixture("jma_eqlist")).outcome());
        assertEquals(Outcome.CACHE_FIRST_VALUE,
                processor.process(fixture("cenc_eqlist")).outcome());
        assertTrue(processor.hasJmaCacheValue());
        assertTrue(processor.hasCencCacheValue());
    }

    private static VelocityMessageProcessor processor(
            boolean[] flags,
            VelocityMessageProcessor.FreshnessEvaluator freshness
    ) {
        return new VelocityMessageProcessor(
                flags[0], flags[1], flags[2], flags[3], flags[4], flags[5], freshness);
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
