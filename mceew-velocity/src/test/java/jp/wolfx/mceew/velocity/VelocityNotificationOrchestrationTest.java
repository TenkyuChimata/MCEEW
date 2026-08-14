package jp.wolfx.mceew.velocity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import jp.wolfx.mceew.VelocityMessageProcessor;
import jp.wolfx.mceew.notification.NotificationSource;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VelocityNotificationOrchestrationTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void everyFreshRealtimeFixtureFlowsThroughCoreFactoryAndVelocityDelivery() throws Exception {
        RuntimeFixture fixture = runtimeFixture(true);
        Set<String> permissions = Set.of(
                "mceew.notify.all",
                NotificationSource.JMA_ALERT.getPermissionNode(),
                NotificationSource.JMA_FORECAST.getPermissionNode(),
                NotificationSource.SICHUAN_EEW.getPermissionNode(),
                NotificationSource.FUJIAN_EEW.getPermissionNode(),
                NotificationSource.CWA_EEW.getPermissionNode(),
                NotificationSource.CENC_EEW.getPermissionNode(),
                NotificationSource.CHONGQING_EEW.getPermissionNode());
        NotificationTestSupport.RecordingPlayer player = fixture.environment.addPlayer(
                "player", "lobby", permissions);
        fixture.runtime.start();

        for (String source : new String[]{
                "jma_eew", "sc_eew", "fj_eew", "cwa_eew", "cenc_eew", "cq_eew"}) {
            fixture.connector.attempt(0).message(freshFixture(source));
        }
        fixture.environment.scheduler().runAll();

        assertEquals(6, player.messages().size());
        assertEquals(6, player.titles().size());
        assertEquals(6, player.sounds().size());
        assertEquals(6, fixture.environment.consoleMessages().size());
    }

    @Test
    void jmaAlertAndForecastSelectDistinctCoreProfilesAndPermissions() throws Exception {
        RuntimeFixture fixture = runtimeFixture(true);
        NotificationTestSupport.RecordingPlayer player = fixture.environment.addPlayer(
                "player", "lobby", Set.of(
                        "mceew.notify.all",
                        NotificationSource.JMA_ALERT.getPermissionNode(),
                        NotificationSource.JMA_FORECAST.getPermissionNode()));
        fixture.runtime.start();
        JsonObject alert = JsonParser.parseString(freshFixture("jma_eew")).getAsJsonObject();
        JsonObject forecast = JsonParser.parseString(freshFixture("jma_eew")).getAsJsonObject();
        forecast.addProperty("Title", "緊急地震速報（予報）");

        fixture.connector.attempt(0).message(alert.toString());
        fixture.connector.attempt(0).message(forecast.toString());
        fixture.environment.scheduler().runAll();

        String alertText = LegacyComponentSerializer.legacySection().serialize(player.messages().get(0));
        String forecastText = LegacyComponentSerializer.legacySection().serialize(player.messages().get(1));
        assertTrue(alertText.startsWith("§c緊急地震速報 (警報)"));
        assertTrue(forecastText.startsWith("§e緊急地震速報 (予報)"));
        assertTrue(player.permissionQueries().contains(NotificationSource.JMA_ALERT.getPermissionNode()));
        assertTrue(player.permissionQueries().contains(NotificationSource.JMA_FORECAST.getPermissionNode()));
    }

    @Test
    void staleAndDisabledRealtimeEventsDoNotScheduleDelivery() throws Exception {
        RuntimeFixture fixture = runtimeFixture(
                new boolean[]{true, false, false, false, false, false});
        NotificationTestSupport.RecordingPlayer player = fixture.environment.addPlayer(
                "player", "lobby", Set.of("mceew.notify.all",
                        NotificationSource.JMA_ALERT.getPermissionNode()));
        fixture.runtime.start();
        int bootstrapTasks = fixture.environment.scheduler().tasks().size();

        fixture.connector.attempt(0).message(fixture("jma_eew"));
        fixture.connector.attempt(0).message("{\"type\":\"sc_eew\"}");

        assertEquals(bootstrapTasks, fixture.environment.scheduler().tasks().size());
        fixture.environment.scheduler().runAll();
        assertTrue(player.messages().isEmpty());
        assertTrue(fixture.environment.consoleMessages().isEmpty());
    }

    @Test
    void eqlistNotifiesOnlyChangedAfterCacheUpdateAndExposesReadOnlyPresentation()
            throws Exception {
        RuntimeFixture fixture = runtimeFixture(false);
        NotificationTestSupport.RecordingPlayer player = fixture.environment.addPlayer(
                "player", "lobby", Set.of(
                        "mceew.notify.all",
                        NotificationSource.JMA_EARTHQUAKE_LIST.getPermissionNode(),
                        NotificationSource.CENC_EARTHQUAKE_LIST.getPermissionNode()));
        fixture.runtime.start();

        sendFirstUnchangedChanged(fixture, "jma_eqlist", 'c');
        sendFirstUnchangedChanged(fixture, "cenc_eqlist", 'd');
        fixture.environment.scheduler().runAll();

        assertEquals(2, player.messages().size());
        assertEquals(2, fixture.environment.consoleMessages().size());
        VelocityMessageProcessor processor = fixture.runtime.messageProcessor();
        assertTrue(processor.latestJmaEarthquakeList().isPresent());
        assertTrue(processor.latestCencEarthquakeList().isPresent());
        String jma = processor.latestJmaEarthquakeList().orElseThrow()
                .render("%region% %shindo%");
        assertEquals("能登半島沖 §d7", jma);
    }

    @Test
    void runtimeShutdownPreventsAlreadyScheduledNotificationDelivery() throws Exception {
        RuntimeFixture fixture = runtimeFixture(true);
        NotificationTestSupport.RecordingPlayer player = fixture.environment.addPlayer(
                "player", "lobby", Set.of(
                        "mceew.notify.all", NotificationSource.SICHUAN_EEW.getPermissionNode()));
        fixture.runtime.start();
        fixture.connector.attempt(0).message(freshFixture("sc_eew"));

        fixture.runtime.close();
        fixture.environment.scheduler().runAll();

        assertTrue(player.messages().isEmpty());
        assertTrue(fixture.environment.consoleMessages().isEmpty());
    }

    @Test
    void disabledEqlistBroadcastsStillPublishChangedCachesBeforeDecision() throws Exception {
        RuntimeFixture fixture = runtimeFixture(false, eqlistConfig(false, false));
        NotificationTestSupport.RecordingPlayer player = fixture.environment.addPlayer(
                "player", "lobby", Set.of(
                        "mceew.notify.all",
                        NotificationSource.JMA_EARTHQUAKE_LIST.getPermissionNode(),
                        NotificationSource.CENC_EARTHQUAKE_LIST.getPermissionNode()));
        fixture.runtime.start();

        fixture.connector.attempt(0).message(fixture("jma_eqlist"));
        fixture.connector.attempt(0).message(fixture("jma_eqlist"));
        fixture.connector.attempt(0).message(
                changedEqlist("jma_eqlist", 'e', "更新震央"));
        fixture.connector.attempt(0).message(fixture("cenc_eqlist"));
        fixture.connector.attempt(0).message(fixture("cenc_eqlist"));
        fixture.connector.attempt(0).message(
                changedEqlist("cenc_eqlist", 'f', "更新地区"));
        fixture.environment.scheduler().runAll();

        assertTrue(player.messages().isEmpty());
        assertTrue(fixture.environment.consoleMessages().isEmpty());
        assertEquals("更新震央", fixture.runtime.messageProcessor()
                .latestJmaEarthquakeList().orElseThrow().render("%region%"));
        assertEquals("更新地区", fixture.runtime.messageProcessor()
                .latestCencEarthquakeList().orElseThrow().render("%region%"));
        assertEquals(1, fixture.connector.connectionCount());
    }

    @Test
    void jmaAndCencEqlistBroadcastSwitchesAreIndependent() throws Exception {
        assertIndependentEqlistSwitches(false, true, "中国地震台网");
        assertIndependentEqlistSwitches(true, false, "地震情報");
    }

    @Test
    void realtimeGatesAndEqlistBroadcastSwitchesRemainIndependent() throws Exception {
        RuntimeFixture gatedRealtime = runtimeFixture(
                new boolean[]{false, true, true, true, false, true},
                eqlistConfig(true, true));
        NotificationTestSupport.RecordingPlayer listPlayer = gatedRealtime.environment.addPlayer(
                "list-player", "lobby", Set.of(
                        "mceew.notify.all",
                        NotificationSource.JMA_EARTHQUAKE_LIST.getPermissionNode(),
                        NotificationSource.CENC_EARTHQUAKE_LIST.getPermissionNode()));
        gatedRealtime.runtime.start();

        assertEquals(VelocityMessageProcessor.Outcome.DISABLED_REALTIME,
                gatedRealtime.runtime.processApplicationMessage(
                        "{\"type\":\"jma_eew\"}").outcome());
        assertEquals(VelocityMessageProcessor.Outcome.DISABLED_REALTIME,
                gatedRealtime.runtime.processApplicationMessage(
                        "{\"type\":\"cenc_eew\"}").outcome());
        sendFirstUnchangedChanged(gatedRealtime, "jma_eqlist", 'e');
        sendFirstUnchangedChanged(gatedRealtime, "cenc_eqlist", 'f');
        gatedRealtime.environment.scheduler().runAll();
        assertEquals(2, listPlayer.messages().size());
        assertEquals(2, gatedRealtime.environment.consoleMessages().size());

        RuntimeFixture disabledLists = runtimeFixture(true, eqlistConfig(false, false));
        NotificationTestSupport.RecordingPlayer realtimePlayer = disabledLists.environment.addPlayer(
                "realtime-player", "lobby", Set.of(
                        "mceew.notify.all",
                        NotificationSource.JMA_ALERT.getPermissionNode(),
                        NotificationSource.CENC_EEW.getPermissionNode(),
                        NotificationSource.JMA_EARTHQUAKE_LIST.getPermissionNode(),
                        NotificationSource.CENC_EARTHQUAKE_LIST.getPermissionNode()));
        disabledLists.runtime.start();

        assertEquals(VelocityMessageProcessor.Outcome.FRESH_REALTIME,
                disabledLists.runtime.processApplicationMessage(
                        freshFixture("jma_eew")).outcome());
        assertEquals(VelocityMessageProcessor.Outcome.FRESH_REALTIME,
                disabledLists.runtime.processApplicationMessage(
                        freshFixture("cenc_eew")).outcome());
        sendFirstUnchangedChanged(disabledLists, "jma_eqlist", 'c');
        sendFirstUnchangedChanged(disabledLists, "cenc_eqlist", 'd');
        disabledLists.environment.scheduler().runAll();
        assertEquals(2, realtimePlayer.messages().size());
        assertEquals(2, disabledLists.environment.consoleMessages().size());
        assertTrue(disabledLists.runtime.messageProcessor()
                .latestJmaEarthquakeList().isPresent());
        assertTrue(disabledLists.runtime.messageProcessor()
                .latestCencEarthquakeList().isPresent());
        assertEquals(1, gatedRealtime.connector.connectionCount());
        assertEquals(1, disabledLists.connector.connectionCount());
    }

    private void assertIndependentEqlistSwitches(
            boolean jmaBroadcast,
            boolean cencBroadcast,
            String expectedMessageFragment
    ) throws Exception {
        RuntimeFixture fixture = runtimeFixture(
                false, eqlistConfig(jmaBroadcast, cencBroadcast));
        NotificationTestSupport.RecordingPlayer player = fixture.environment.addPlayer(
                "player-" + jmaBroadcast, "lobby", Set.of(
                        "mceew.notify.all",
                        NotificationSource.JMA_EARTHQUAKE_LIST.getPermissionNode(),
                        NotificationSource.CENC_EARTHQUAKE_LIST.getPermissionNode()));
        fixture.runtime.start();

        sendFirstUnchangedChanged(fixture, "jma_eqlist", jmaBroadcast ? 'c' : 'd');
        sendFirstUnchangedChanged(fixture, "cenc_eqlist", cencBroadcast ? 'e' : 'f');
        fixture.environment.scheduler().runAll();

        assertEquals(1, player.messages().size());
        assertTrue(LegacyComponentSerializer.legacySection()
                .serialize(player.messages().get(0)).contains(expectedMessageFragment));
        assertEquals(1, fixture.environment.consoleMessages().size());
        assertTrue(fixture.runtime.messageProcessor().latestJmaEarthquakeList().isPresent());
        assertTrue(fixture.runtime.messageProcessor().latestCencEarthquakeList().isPresent());
        assertEquals(1, fixture.connector.connectionCount());
    }

    private RuntimeFixture runtimeFixture(boolean sourcesEnabled) throws Exception {
        return runtimeFixture(new boolean[]{
                sourcesEnabled, sourcesEnabled, sourcesEnabled,
                sourcesEnabled, sourcesEnabled, sourcesEnabled}, null);
    }

    private RuntimeFixture runtimeFixture(boolean[] sources) throws Exception {
        return runtimeFixture(sources, null);
    }

    private RuntimeFixture runtimeFixture(boolean sourcesEnabled, String configContent)
            throws Exception {
        return runtimeFixture(new boolean[]{
                sourcesEnabled, sourcesEnabled, sourcesEnabled,
                sourcesEnabled, sourcesEnabled, sourcesEnabled}, configContent);
    }

    private RuntimeFixture runtimeFixture(boolean[] sources, String configContent) throws Exception {
        Path data = temporaryDirectory.resolve("mceew");
        if (configContent != null) {
            Files.createDirectories(data);
            Files.writeString(data.resolve("config.yml"), configContent, StandardCharsets.UTF_8);
        }
        VelocityConfigSnapshot loaded = new VelocityConfigLoader(data).load();
        VelocityConfigSnapshot config = new VelocityConfigSnapshot(
                1, true,
                sources[0], sources[1], sources[2],
                sources[3], sources[4], sources[5],
                loaded.notificationConfig());
        NotificationTestSupport.Environment environment = new NotificationTestSupport.Environment();
        TestVelocityApi.CapturingLogger logger = TestVelocityApi.logger();
        VelocityDelayScheduler scheduler = new VelocityDelayScheduler(environment.proxy(), this);
        VelocityNotificationDispatcher dispatcher = new VelocityNotificationDispatcher(
                environment.proxy(), logger.proxy(), scheduler, config.notificationConfig());
        VelocityNotificationOrchestrator orchestrator = new VelocityNotificationOrchestrator(
                config.notificationConfig(), dispatcher);
        TestWebSocketSupport.RecordingConnector connector =
                new TestWebSocketSupport.RecordingConnector(true);
        VelocityMceewRuntime runtime = new VelocityMceewRuntime(
                config, scheduler, connector, logger.proxy(), orchestrator);
        return new RuntimeFixture(environment, connector, runtime);
    }

    private static void sendFirstUnchangedChanged(
            RuntimeFixture fixture,
            String name,
            char changedMd5Character
    ) {
        String initial = fixture(name);
        JsonObject changed = JsonParser.parseString(initial).getAsJsonObject();
        changed.addProperty("md5", String.valueOf(changedMd5Character).repeat(32));
        fixture.connector.attempt(0).message(initial);
        fixture.connector.attempt(0).message(initial);
        fixture.connector.attempt(0).message(changed.toString());
    }

    private static String changedEqlist(String name, char md5Character, String region) {
        JsonObject changed = JsonParser.parseString(fixture(name)).getAsJsonObject();
        changed.addProperty("md5", String.valueOf(md5Character).repeat(32));
        changed.getAsJsonObject("No1").addProperty("location", region);
        return changed.toString();
    }

    private static String eqlistConfig(boolean jmaBroadcast, boolean cencBroadcast) {
        return "platform_config_version: 1\n"
                + "global: {}\n"
                + "notifications:\n"
                + "  sources:\n"
                + "    jma_eqlist:\n"
                + "      broadcast: " + jmaBroadcast + "\n"
                + "    cenc_eqlist:\n"
                + "      broadcast: " + cencBroadcast + "\n"
                + "targets:\n"
                + "  default:\n"
                + "    mode: all\n"
                + "  sources: {}\n"
                + "groups: {}\n"
                + "servers: {}\n";
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
        private final NotificationTestSupport.Environment environment;
        private final TestWebSocketSupport.RecordingConnector connector;
        private final VelocityMceewRuntime runtime;

        private RuntimeFixture(
                NotificationTestSupport.Environment environment,
                TestWebSocketSupport.RecordingConnector connector,
                VelocityMceewRuntime runtime
        ) {
            this.environment = environment;
            this.connector = connector;
            this.runtime = runtime;
        }
    }
}
