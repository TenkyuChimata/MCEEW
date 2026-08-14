package jp.wolfx.mceew.bungeecord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import jp.wolfx.mceew.BungeeMessageProcessor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BungeeMceewRuntimeTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void startCreatesOneManagerConnectionAndPreservesBootstrapPacing() throws Exception {
        RuntimeHarness harness = runtime("bootstrap", true, true, true);
        Object manager = harness.runtime.webSocketManagerIdentity();
        Object processor = harness.runtime.messageProcessor();

        harness.runtime.start();
        harness.runtime.start();

        assertTrue(harness.runtime.isActive());
        assertEquals(1, harness.connector.connectionCount());
        assertSame(manager, harness.runtime.webSocketManagerIdentity());
        assertSame(processor, harness.runtime.messageProcessor());
        assertEquals(List.of("query_jmaeqlist"),
                harness.connector.attempt(0).socket().textMessages());
        assertEquals(1, harness.backend.tasks.size());
        harness.backend.run(0);
        assertEquals(List.of("query_jmaeqlist", "query_cenceqlist"),
                harness.connector.attempt(0).socket().textMessages());
    }

    @Test
    void websocketMessagesAndFragmentsReachTheSingleCache() throws Exception {
        RuntimeHarness harness = runtime("messages", true, true, true);
        harness.runtime.start();
        String jma = fixture("jma_eqlist");

        harness.connector.attempt(0).fragment(jma, jma.length() / 2);
        harness.connector.attempt(0).message(fixture("cenc_eqlist"));

        assertTrue(harness.runtime.messageProcessor().hasJmaCacheValue());
        assertTrue(harness.runtime.messageProcessor().hasCencCacheValue());
        assertEquals(1, harness.connector.connectionCount());
        assertTrue(harness.connector.attempt(0).socket().requestCalls() >= 4);
    }

    @Test
    void malformedApplicationMessagesAreIsolatedWithoutReconnectOrCacheCorruption()
            throws Exception {
        RuntimeHarness harness = runtime("malformed", true, true, true);
        harness.runtime.start();
        harness.connector.attempt(0).message(fixture("jma_eqlist"));
        String before = harness.runtime.latestJmaEarthquakeInformation();

        harness.connector.attempt(0).message("not-json");
        harness.connector.attempt(0).message("{\"type\":\"jma_eew\"}");

        assertEquals(before, harness.runtime.latestJmaEarthquakeInformation());
        assertTrue(harness.runtime.isActive());
        assertEquals(1, harness.connector.connectionCount());
    }

    @Test
    void preparedConfigChangesGatesWithoutReplacingRuntimeManagerProcessorOrCache()
            throws Exception {
        RuntimeHarness harness = runtime("policy", true, true, true);
        harness.runtime.start();
        harness.runtime.processApplicationMessage(fixture("jma_eqlist"));
        Object manager = harness.runtime.webSocketManagerIdentity();
        Object processor = harness.runtime.messageProcessor();
        int connections = harness.connector.connectionCount();

        BungeeConfigSnapshot replacement = snapshot(
                temporaryDirectory.resolve("policy-reload"), true, false, true);
        BungeeMceewRuntime.PreparedConfiguration prepared =
                harness.runtime.prepareConfiguration(replacement);
        harness.runtime.commitConfiguration(prepared);

        assertEquals(BungeeMessageProcessor.Outcome.DISABLED_REALTIME,
                harness.runtime.processApplicationMessage("{\"type\":\"sc_eew\"}").outcome());
        assertEquals(BungeeMessageProcessor.Outcome.CACHE_UNCHANGED,
                harness.runtime.processApplicationMessage(fixture("jma_eqlist")).outcome());
        assertSame(manager, harness.runtime.webSocketManagerIdentity());
        assertSame(processor, harness.runtime.messageProcessor());
        assertEquals(connections, harness.connector.connectionCount());
    }

    @Test
    void infoReadsOnlyLocalCacheAndUsesConfiguredPresentation() throws Exception {
        RuntimeHarness harness = runtime("info", true, true, true);

        assertEquals("[MCEEW] Earthquake information is not available yet.",
                harness.runtime.latestJmaEarthquakeInformation());
        assertEquals("[MCEEW] Earthquake information is not available yet.",
                harness.runtime.latestCencEarthquakeInformation());
        assertEquals(0, harness.connector.connectionCount(),
                "reading an empty cache does not start or query the network");

        harness.runtime.processApplicationMessage(fixture("jma_eqlist"));
        harness.runtime.processApplicationMessage(fixture("cenc_eqlist"));

        assertTrue(harness.runtime.latestJmaEarthquakeInformation().contains("能登半島沖"));
        assertTrue(harness.runtime.latestJmaEarthquakeInformation().startsWith("§e地震情報"));
        assertTrue(harness.runtime.latestCencEarthquakeInformation().contains("中国地震台网"));
        assertEquals(0, harness.connector.connectionCount(),
                "cached info commands do not trigger WebSocket work");
    }

    @Test
    void reconnectRemainsInsideSameRuntimeManagerAndCache() throws Exception {
        RuntimeHarness harness = runtime("reconnect", true, true, true);
        harness.runtime.start();
        harness.runtime.processApplicationMessage(fixture("jma_eqlist"));
        Object manager = harness.runtime.webSocketManagerIdentity();
        Object processor = harness.runtime.messageProcessor();

        harness.connector.attempt(0).closeFromPeer(WebSocket.NORMAL_CLOSURE, "peer restart");
        assertTrue(harness.backend.tasks.size() >= 2);
        harness.backend.run(harness.backend.tasks.size() - 1);

        assertEquals(2, harness.connector.connectionCount());
        assertSame(manager, harness.runtime.webSocketManagerIdentity());
        assertSame(processor, harness.runtime.messageProcessor());
        assertTrue(harness.runtime.messageProcessor().hasJmaCacheValue());
    }

    @Test
    void closeIsIdempotentCancelsReconnectAndRejectsStaleOpen() throws Exception {
        RuntimeHarness harness = runtime("close", false, true, true);
        harness.runtime.start();

        harness.runtime.close();
        harness.runtime.close();
        harness.connector.attempt(0).open();
        runAll(harness.backend);

        assertFalse(harness.runtime.isActive());
        assertTrue(harness.connector.attempt(0).socket().aborted());
        assertTrue(harness.connector.attempt(0).socket().textMessages().isEmpty());
        assertEquals(1, harness.connector.connectionCount());
    }

    @Test
    void eqlistCacheTransitionsRemainIndependentFromRealtimeJmaAndCencGates()
            throws Exception {
        RuntimeHarness harness = runtime("eqlist-gates", true, false, false);

        assertEquals(BungeeMessageProcessor.Outcome.DISABLED_REALTIME,
                harness.runtime.processApplicationMessage("{\"type\":\"jma_eew\"}").outcome());
        assertEquals(BungeeMessageProcessor.Outcome.DISABLED_REALTIME,
                harness.runtime.processApplicationMessage("{\"type\":\"cenc_eew\"}").outcome());
        assertEquals(BungeeMessageProcessor.Outcome.CACHE_FIRST_VALUE,
                harness.runtime.processApplicationMessage(fixture("jma_eqlist")).outcome());
        assertEquals(BungeeMessageProcessor.Outcome.CACHE_FIRST_VALUE,
                harness.runtime.processApplicationMessage(fixture("cenc_eqlist")).outcome());
    }

    private RuntimeHarness runtime(
            String name,
            boolean autoOpen,
            boolean jma,
            boolean cenc
    ) throws Exception {
        BungeeDelaySchedulerTest.FakeBackend backend =
                new BungeeDelaySchedulerTest.FakeBackend();
        BungeeDelayScheduler scheduler = new BungeeDelayScheduler(backend);
        TestWebSocketSupport.RecordingConnector connector =
                new TestWebSocketSupport.RecordingConnector(autoOpen);
        BungeeMceewRuntime runtime = new BungeeMceewRuntime(
                snapshot(temporaryDirectory.resolve(name), true, jma, cenc),
                scheduler,
                connector,
                logger(name));
        return new RuntimeHarness(runtime, connector, backend);
    }

    private BungeeConfigSnapshot snapshot(
            Path directory,
            boolean enabled,
            boolean jma,
            boolean cenc
    ) throws Exception {
        Files.createDirectories(directory);
        Files.writeString(directory.resolve("config.yml"),
                "platform_config_version: 1\n"
                        + "global:\n"
                        + "  enabled: " + enabled + "\n"
                        + "  sources:\n"
                        + "    enable_jp: " + jma + "\n"
                        + "    enable_sc: " + jma + "\n"
                        + "    enable_fj: true\n"
                        + "    enable_cwa: true\n"
                        + "    enable_cenceew: " + cenc + "\n"
                        + "    enable_cq: true\n"
                        + "targets:\n  default:\n    mode: all\n  sources: {}\n"
                        + "groups: {}\nservers: {}\n");
        return new BungeeConfigLoader(directory, getClass().getClassLoader()).loadSnapshot();
    }

    private static void runAll(BungeeDelaySchedulerTest.FakeBackend backend) {
        for (int index = 0; index < backend.tasks.size(); index++) {
            backend.run(index);
        }
    }

    private static Logger logger(String name) {
        Logger logger = Logger.getLogger("BungeeMceewRuntimeTest." + name);
        logger.setUseParentHandlers(false);
        return logger;
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

    private static final class RuntimeHarness {
        private final BungeeMceewRuntime runtime;
        private final TestWebSocketSupport.RecordingConnector connector;
        private final BungeeDelaySchedulerTest.FakeBackend backend;

        private RuntimeHarness(
                BungeeMceewRuntime runtime,
                TestWebSocketSupport.RecordingConnector connector,
                BungeeDelaySchedulerTest.FakeBackend backend
        ) {
            this.runtime = runtime;
            this.connector = connector;
            this.backend = backend;
        }
    }
}
