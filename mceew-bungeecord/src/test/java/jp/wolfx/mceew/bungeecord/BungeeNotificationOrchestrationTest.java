package jp.wolfx.mceew.bungeecord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import jp.wolfx.mceew.BungeeMessageProcessor;
import jp.wolfx.mceew.notification.NotificationSource;
import org.junit.jupiter.api.Test;

class BungeeNotificationOrchestrationTest {
    @Test
    void everyFreshRealtimeFixtureFlowsThroughCoreRouterAndBungeeDelivery() {
        RuntimeFixture fixture = runtimeFixture(
                BungeeNotificationTestSupport.config().build());
        BungeeNotificationTestSupport.FakePlayer player =
                fixture.platform.addPlayer("player", "lobby");
        fixture.runtime.start();

        for (String source : List.of(
                "jma_eew", "sc_eew", "fj_eew", "cwa_eew", "cenc_eew", "cq_eew")) {
            assertEquals(BungeeMessageProcessor.Outcome.FRESH_REALTIME,
                    fixture.runtime.processApplicationMessage(freshFixture(source)).outcome(),
                    source);
        }
        runAll(fixture);

        assertEquals(6, player.chats().size());
        assertEquals(6, player.titles().size());
        assertEquals(6, fixture.platform.consoleMessages().size());
        assertEquals(1, fixture.connector.connectionCount());
    }

    @Test
    void jmaAlertAndForecastUseDistinctProfilesAndSuppressionNodes() {
        RuntimeFixture fixture = runtimeFixture(
                BungeeNotificationTestSupport.config().build());
        BungeeNotificationTestSupport.FakePlayer player =
                fixture.platform.addPlayer("player", "lobby");
        fixture.runtime.start();
        JsonObject alert = JsonParser.parseString(freshFixture("jma_eew")).getAsJsonObject();
        JsonObject forecast = JsonParser.parseString(freshFixture("jma_eew")).getAsJsonObject();
        forecast.addProperty("Title", "緊急地震速報（予報）");

        fixture.runtime.processApplicationMessage(alert.toString());
        fixture.runtime.processApplicationMessage(forecast.toString());
        runAll(fixture);

        assertTrue(player.chats().get(0).contains("JMA_ALERT"));
        assertTrue(player.chats().get(1).contains("JMA_FORECAST"));
        assertTrue(player.permissionQueries().contains("mceew.suppress.jma.alert"));
        assertTrue(player.permissionQueries().contains("mceew.suppress.jma.forecast"));
    }

    @Test
    void staleAndDisabledRealtimeNeverReachNotificationDelivery() {
        BungeeConfigSnapshot disabledSichuan = BungeeNotificationTestSupport.config().build();
        disabledSichuan = new BungeeConfigSnapshot(
                1,
                true,
                new BungeeConfigSnapshot.SourceGates(true, false, true, true, true, true),
                disabledSichuan.timeFormat(),
                disabledSichuan.notificationDefaults(),
                disabledSichuan.notificationSources(),
                disabledSichuan.defaultTarget(),
                disabledSichuan.sourceTargets(),
                disabledSichuan.groups(),
                disabledSichuan.servers());
        RuntimeFixture fixture = runtimeFixture(disabledSichuan);
        BungeeNotificationTestSupport.FakePlayer player =
                fixture.platform.addPlayer("player", "lobby");

        assertEquals(BungeeMessageProcessor.Outcome.STALE_REALTIME,
                fixture.runtime.processApplicationMessage(fixture("jma_eew")).outcome());
        assertEquals(BungeeMessageProcessor.Outcome.DISABLED_REALTIME,
                fixture.runtime.processApplicationMessage("{\"type\":\"sc_eew\"}").outcome());
        runAll(fixture);

        assertTrue(player.chats().isEmpty());
        assertTrue(fixture.platform.consoleMessages().isEmpty());
    }

    @Test
    void eqlistsNotifyOnlyChangedAndCacheIsNewBeforeQueuedDelivery() {
        RuntimeFixture fixture = runtimeFixture(
                BungeeNotificationTestSupport.config().build());
        BungeeNotificationTestSupport.FakePlayer player =
                fixture.platform.addPlayer("player", "lobby");

        assertEqlistTransition(fixture, "jma_eqlist", 'c', "更新震央");
        assertEqlistTransition(fixture, "cenc_eqlist", 'd', "更新地区");
        assertTrue(player.chats().isEmpty(), "delivery remains queued on the Bungee scheduler");
        assertTrue(fixture.runtime.latestJmaEarthquakeInformation().contains("更新震央"));
        assertTrue(fixture.runtime.latestCencEarthquakeInformation().contains("更新地区"));

        runAll(fixture);

        assertEquals(2, player.chats().size());
        assertTrue(player.chats().get(0).contains("更新震央"));
        assertTrue(player.chats().get(1).contains("更新地区"));
        assertEquals(2, fixture.platform.consoleMessages().size());
    }

    @Test
    void disabledEqlistBroadcastStillUpdatesCacheWithoutConsoleOrPlayerDelivery() {
        RuntimeFixture fixture = runtimeFixture(BungeeNotificationTestSupport.config()
                .sourceChannels(NotificationSource.JMA_EARTHQUAKE_LIST, false, null)
                .sourceChannels(NotificationSource.CENC_EARTHQUAKE_LIST, false, null)
                .build());
        BungeeNotificationTestSupport.FakePlayer player =
                fixture.platform.addPlayer("player", "lobby");

        assertEqlistTransition(fixture, "jma_eqlist", 'e', "更新震央");
        assertEqlistTransition(fixture, "cenc_eqlist", 'f', "更新地区");
        runAll(fixture);

        assertTrue(player.chats().isEmpty());
        assertTrue(fixture.platform.consoleMessages().isEmpty());
        assertTrue(fixture.runtime.latestJmaEarthquakeInformation().contains("更新震央"));
        assertTrue(fixture.runtime.latestCencEarthquakeInformation().contains("更新地区"));
    }

    @Test
    void eqlistSuppressionAffectsPlayersOnlyAndNeverCacheOrConsole() {
        RuntimeFixture fixture = runtimeFixture(
                BungeeNotificationTestSupport.config().build());
        BungeeNotificationTestSupport.FakePlayer player =
                fixture.platform.addPlayer("player", "lobby");
        player.permission("mceew.suppress.jma.eqlist", true);
        player.permission("mceew.suppress.cenc.eqlist", true);

        assertEqlistTransition(fixture, "jma_eqlist", 'e', "更新震央");
        assertEqlistTransition(fixture, "cenc_eqlist", 'f', "更新地区");
        runAll(fixture);

        assertTrue(player.chats().isEmpty());
        assertEquals(2, fixture.platform.consoleMessages().size());
        assertTrue(fixture.runtime.latestJmaEarthquakeInformation().contains("更新震央"));
        assertTrue(fixture.runtime.latestCencEarthquakeInformation().contains("更新地区"));
    }

    @Test
    void allSevenTestSourcesUseRealPolicyPipelineAndSeparateWarning() {
        RuntimeFixture fixture = runtimeFixture(
                BungeeNotificationTestSupport.config().build());
        BungeeNotificationTestSupport.FakePlayer player =
                fixture.platform.addPlayer("player", "lobby");
        fixture.runtime.start();

        for (String source : List.of(
                "forecast", "alert", "sc", "fj", "cwa", "cenc", "cq")) {
            assertTrue(fixture.runtime.dispatchTest(source), source);
        }
        runAll(fixture);

        assertEquals(14, player.chats().size(), "seven earthquakes plus seven warnings");
        assertEquals(7, player.titles().size());
        assertEquals(14, fixture.platform.consoleMessages().size());
        assertEquals(7, player.chats().stream()
                .filter(message -> message.contains("Earthquake Early Warning test"))
                .count());
        assertEquals(1, fixture.connector.connectionCount());
        assertFalse(fixture.runtime.messageProcessor().hasJmaCacheValue());
        assertFalse(fixture.runtime.messageProcessor().hasCencCacheValue());
    }

    @Test
    void testCommandPipelineHonorsSuppressionAndTargetNoneButWarningRemainsFeedback() {
        RuntimeFixture suppressed = runtimeFixture(
                BungeeNotificationTestSupport.config().build());
        BungeeNotificationTestSupport.FakePlayer player =
                suppressed.platform.addPlayer("player", "lobby");
        player.permission("mceew.suppress.jma.forecast", true);
        suppressed.runtime.start();

        assertTrue(suppressed.runtime.dispatchTest("forecast"));
        runAll(suppressed);
        assertEquals(1, player.chats().size());
        assertTrue(player.chats().get(0).contains("Earthquake Early Warning test"));
        assertTrue(player.titles().isEmpty());

        RuntimeFixture none = runtimeFixture(BungeeNotificationTestSupport.config()
                .defaultTarget(BungeeConfigSnapshot.TargetMode.NONE, Set.of(), Set.of())
                .build());
        BungeeNotificationTestSupport.FakePlayer untargeted =
                none.platform.addPlayer("untargeted", "lobby");
        none.runtime.start();
        assertTrue(none.runtime.dispatchTest("forecast"));
        runAll(none);
        assertEquals(1, untargeted.chats().size());
        assertTrue(untargeted.titles().isEmpty());
    }

    @Test
    void enabledReloadKeepsRuntimeManagerProcessorCacheAndConnectionButSwapsPolicy() {
        RuntimeFixture fixture = runtimeFixture(
                BungeeNotificationTestSupport.config().build(), true);
        BungeeNotificationTestSupport.FakePlayer player =
                fixture.platform.addPlayer("player", "lobby");
        fixture.runtime.start();
        fixture.runtime.processApplicationMessage(fixture("jma_eqlist"));
        Object manager = fixture.runtime.webSocketManagerIdentity();
        Object processor = fixture.runtime.messageProcessor();
        Object oldPolicy = fixture.runtime.notificationOrchestratorIdentity();
        int connections = fixture.connector.connectionCount();

        BungeeConfigSnapshot none = BungeeNotificationTestSupport.config()
                .defaultTarget(BungeeConfigSnapshot.TargetMode.NONE, Set.of(), Set.of())
                .build();
        BungeeMceewRuntime.PreparedConfiguration prepared =
                fixture.runtime.prepareConfiguration(none);
        fixture.runtime.commitConfiguration(prepared);
        Object newPolicy = fixture.runtime.notificationOrchestratorIdentity();
        fixture.runtime.dispatchTest("forecast");
        runAll(fixture);

        assertSame(manager, fixture.runtime.webSocketManagerIdentity());
        assertSame(processor, fixture.runtime.messageProcessor());
        assertNotSame(oldPolicy, newPolicy);
        assertTrue(fixture.runtime.latestJmaEarthquakeInformation().contains("能登半島沖"));
        assertEquals(connections, fixture.connector.connectionCount());
        assertEquals(1, player.chats().size(), "target-none leaves only test warning");
        assertTrue(player.titles().isEmpty());
    }

    @Test
    void runtimeCloseCancelsQueuedDeliveryAndDeliveryFailuresNeverReconnect() {
        RuntimeFixture closed = runtimeFixture(
                BungeeNotificationTestSupport.config().build());
        BungeeNotificationTestSupport.FakePlayer stale =
                closed.platform.addPlayer("stale", "lobby");
        closed.runtime.start();
        closed.runtime.processApplicationMessage(freshFixture("sc_eew"));
        closed.runtime.close();
        runAll(closed);
        assertTrue(stale.chats().isEmpty());
        assertTrue(closed.platform.consoleMessages().isEmpty());

        RuntimeFixture failing = runtimeFixture(
                BungeeNotificationTestSupport.config().build());
        BungeeNotificationTestSupport.FakePlayer broken =
                failing.platform.addPlayer("broken", "lobby");
        broken.failChat(true);
        broken.failTitle(true);
        failing.runtime.start();
        Object manager = failing.runtime.webSocketManagerIdentity();
        failing.runtime.processApplicationMessage(freshFixture("cenc_eew"));
        runAll(failing);
        assertSame(manager, failing.runtime.webSocketManagerIdentity());
        assertEquals(1, failing.connector.connectionCount());
        assertTrue(failing.runtime.isActive());
    }

    private static RuntimeFixture runtimeFixture(BungeeConfigSnapshot config) {
        return runtimeFixture(config, false);
    }

    private static RuntimeFixture runtimeFixture(
            BungeeConfigSnapshot config,
            boolean reloadablePolicy
    ) {
        BungeeNotificationTestSupport.FakePlatform platform =
                new BungeeNotificationTestSupport.FakePlatform();
        BungeeDelaySchedulerTest.FakeBackend backend =
                new BungeeDelaySchedulerTest.FakeBackend();
        BungeeDelayScheduler scheduler = new BungeeDelayScheduler(backend);
        TestWebSocketSupport.RecordingConnector connector =
                new TestWebSocketSupport.RecordingConnector(true);
        java.util.logging.Logger logger = BungeeNotificationTestSupport.logger("orchestration");
        BungeeMceewRuntime.NotificationOrchestratorFactory factory = current ->
                new BungeeNotificationOrchestrator(
                        current,
                        new BungeeNotificationDispatcher(
                                platform, scheduler, current, logger));
        BungeeMceewRuntime runtime = reloadablePolicy
                ? new BungeeMceewRuntime(config, scheduler, connector, logger, factory)
                : new BungeeMceewRuntime(
                        config, scheduler, connector, logger, factory.create(config));
        return new RuntimeFixture(platform, backend, connector, runtime);
    }

    private static void assertEqlistTransition(
            RuntimeFixture fixture,
            String name,
            char changedMd5Character,
            String region
    ) {
        String initial = fixture(name);
        JsonObject changed = JsonParser.parseString(initial).getAsJsonObject();
        changed.addProperty("md5", String.valueOf(changedMd5Character).repeat(32));
        changed.getAsJsonObject("No1").addProperty("location", region);

        assertEquals(BungeeMessageProcessor.Outcome.CACHE_FIRST_VALUE,
                fixture.runtime.processApplicationMessage(initial).outcome());
        assertEquals(BungeeMessageProcessor.Outcome.CACHE_UNCHANGED,
                fixture.runtime.processApplicationMessage(initial).outcome());
        assertEquals(BungeeMessageProcessor.Outcome.CACHE_CHANGED,
                fixture.runtime.processApplicationMessage(changed.toString()).outcome());
    }

    private static void runAll(RuntimeFixture fixture) {
        BungeeNotificationTestSupport.runAll(fixture.backend);
    }

    private static String freshFixture(String name) {
        JsonObject payload = JsonParser.parseString(fixture(name)).getAsJsonObject();
        if ("jma_eew".equals(name)) {
            payload.addProperty("AnnouncedTime", "not-a-timestamp");
        } else {
            payload.addProperty("ReportTime", "not-a-timestamp");
        }
        return payload.toString();
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

    private static final class RuntimeFixture {
        private final BungeeNotificationTestSupport.FakePlatform platform;
        private final BungeeDelaySchedulerTest.FakeBackend backend;
        private final TestWebSocketSupport.RecordingConnector connector;
        private final BungeeMceewRuntime runtime;

        private RuntimeFixture(
                BungeeNotificationTestSupport.FakePlatform platform,
                BungeeDelaySchedulerTest.FakeBackend backend,
                TestWebSocketSupport.RecordingConnector connector,
                BungeeMceewRuntime runtime
        ) {
            this.platform = platform;
            this.backend = backend;
            this.connector = connector;
            this.runtime = runtime;
        }
    }
}
