package jp.wolfx.mceew.velocity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import jp.wolfx.mceew.VelocityMessageProcessor;
import jp.wolfx.mceew.notification.NotificationSource;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VelocityTransactionalReloadTest {
    private static final LegacyComponentSerializer LEGACY =
            LegacyComponentSerializer.legacySection();

    @TempDir
    Path temporaryDirectory;

    @Test
    void enabledToEnabledAtomicallyAppliesTemplatesWithoutReplacingManagerOrCache() {
        ReloadHarness harness = reloadHarness("enabled-enabled", config(true, true, null, "all"));
        NotificationTestSupport.RecordingPlayer player = harness.environment.addPlayer(
                "recipient", "lobby", Set.of(
                        "mceew.notify.all",
                        NotificationSource.SICHUAN_EEW.getPermissionNode(),
                        NotificationSource.JMA_EARTHQUAKE_LIST.getPermissionNode(),
                        NotificationSource.CENC_EARTHQUAKE_LIST.getPermissionNode()));
        VelocityMceewRuntime runtime = harness.runtime();
        Object runtimeIdentity = runtime;
        Object managerIdentity = runtime.webSocketManagerIdentity();
        Object processorIdentity = runtime.messageProcessor();
        runtime.processApplicationMessage(fixture("jma_eqlist"));
        runtime.processApplicationMessage(fixture("cenc_eqlist"));
        String oldJma = runtime.latestJmaEarthquakeInformation();
        String oldCenc = runtime.latestCencEarthquakeInformation();

        runtime.processApplicationMessage(freshFixture("sc_eew"));
        harness.environment.scheduler().runAll();
        assertTrue(LEGACY.serialize(player.messages().get(0)).startsWith("§c四川地震预警"));
        int connections = harness.connector.connectionCount();

        writeConfig(harness.dataDirectory,
                config(true, true, "&aReloaded %region%", "all"));
        assertEquals(List.of(MCEEWVelocity.ReloadOutcome.SUCCESS), harness.reload());

        assertSame(runtimeIdentity, harness.plugin.operationalRuntimeIdentity());
        assertSame(managerIdentity, runtime.webSocketManagerIdentity());
        assertSame(processorIdentity, runtime.messageProcessor());
        assertEquals(connections, harness.connector.connectionCount());
        assertEquals(oldJma, runtime.latestJmaEarthquakeInformation());
        assertEquals(oldCenc, runtime.latestCencEarthquakeInformation());
        assertEquals(VelocityMessageProcessor.Outcome.CACHE_UNCHANGED,
                runtime.processApplicationMessage(fixture("jma_eqlist")).outcome());
        assertEquals(VelocityMessageProcessor.Outcome.CACHE_UNCHANGED,
                runtime.processApplicationMessage(fixture("cenc_eqlist")).outcome());

        int oldMessages = player.messages().size();
        runtime.processApplicationMessage(freshFixture("sc_eew"));
        harness.environment.scheduler().runAll();
        assertEquals(oldMessages + 1, player.messages().size());
        assertEquals("§aReloaded 四川雅安市汉源县",
                LEGACY.serialize(player.messages().get(player.messages().size() - 1)));
        assertEquals(1, harness.runtimeCreations.get());
        assertEquals(1, runtime.coreLogHandlerCount());
    }

    @Test
    void sourceGateReloadTakesEffectBeforeParseAndEqlistRemainsIndependent() {
        ReloadHarness harness = reloadHarness("source-gate", config(true, true, null, "all"));
        VelocityMceewRuntime runtime = harness.runtime();
        Object manager = runtime.webSocketManagerIdentity();
        Object processor = runtime.messageProcessor();

        writeConfig(harness.dataDirectory, config(true, false, false, null, "all"));
        assertEquals(List.of(MCEEWVelocity.ReloadOutcome.SUCCESS), harness.reload());
        assertEquals(VelocityMessageProcessor.Outcome.DISABLED_REALTIME,
                runtime.processApplicationMessage("{\"type\":\"jma_eew\"}").outcome());
        assertEquals(VelocityMessageProcessor.Outcome.DISABLED_REALTIME,
                runtime.processApplicationMessage("{\"type\":\"cenc_eew\"}").outcome());
        assertEquals(VelocityMessageProcessor.Outcome.CACHE_FIRST_VALUE,
                runtime.processApplicationMessage(fixture("jma_eqlist")).outcome());
        assertEquals(VelocityMessageProcessor.Outcome.CACHE_FIRST_VALUE,
                runtime.processApplicationMessage(fixture("cenc_eqlist")).outcome());
        assertTrue(runtime.messageProcessor().latestJmaEarthquakeList().isPresent());
        assertTrue(runtime.messageProcessor().latestCencEarthquakeList().isPresent());

        writeConfig(harness.dataDirectory, config(true, true, true, null, "all"));
        assertEquals(List.of(MCEEWVelocity.ReloadOutcome.SUCCESS), harness.reload());
        assertEquals(VelocityMessageProcessor.Outcome.FRESH_REALTIME,
                runtime.processApplicationMessage(freshFixture("jma_eew")).outcome());
        assertEquals(VelocityMessageProcessor.Outcome.FRESH_REALTIME,
                runtime.processApplicationMessage(freshFixture("cenc_eew")).outcome());
        assertSame(manager, runtime.webSocketManagerIdentity());
        assertSame(processor, runtime.messageProcessor());
        assertEquals(1, harness.connector.connectionCount());
    }

    @Test
    void eqlistBroadcastReloadPreservesManagerCacheAndConnection() {
        ReloadHarness harness = reloadHarness(
                "eqlist-broadcast", eqlistConfig(true, true));
        NotificationTestSupport.RecordingPlayer player = harness.environment.addPlayer(
                "recipient", "lobby", Set.of(
                        "mceew.notify.all",
                        NotificationSource.JMA_EARTHQUAKE_LIST.getPermissionNode(),
                        NotificationSource.CENC_EARTHQUAKE_LIST.getPermissionNode()));
        VelocityMceewRuntime runtime = harness.runtime();
        Object manager = runtime.webSocketManagerIdentity();
        Object processor = runtime.messageProcessor();
        int connections = harness.connector.connectionCount();

        runtime.processApplicationMessage(fixture("jma_eqlist"));
        runtime.processApplicationMessage(fixture("cenc_eqlist"));
        runtime.processApplicationMessage(changedEqlist("jma_eqlist", 'c', "初回更新"));
        runtime.processApplicationMessage(changedEqlist("cenc_eqlist", 'd', "初次更新"));
        harness.environment.scheduler().runAll();
        assertEquals(2, player.messages().size());
        assertEquals(2, harness.environment.consoleMessages().size());

        writeConfig(harness.dataDirectory, eqlistConfig(false, false));
        assertEquals(List.of(MCEEWVelocity.ReloadOutcome.SUCCESS), harness.reload());
        runtime.processApplicationMessage(changedEqlist("jma_eqlist", 'e', "通知停止後"));
        runtime.processApplicationMessage(changedEqlist("cenc_eqlist", 'f', "通知停止后"));
        harness.environment.scheduler().runAll();

        assertEquals(2, player.messages().size());
        assertEquals(2, harness.environment.consoleMessages().size());
        assertEquals("通知停止後", runtime.messageProcessor()
                .latestJmaEarthquakeList().orElseThrow().render("%region%"));
        assertEquals("通知停止后", runtime.messageProcessor()
                .latestCencEarthquakeList().orElseThrow().render("%region%"));
        assertSame(manager, runtime.webSocketManagerIdentity());
        assertSame(processor, runtime.messageProcessor());
        assertEquals(connections, harness.connector.connectionCount());

        writeConfig(harness.dataDirectory, eqlistConfig(true, true));
        assertEquals(List.of(MCEEWVelocity.ReloadOutcome.SUCCESS), harness.reload());
        runtime.processApplicationMessage(changedEqlist("jma_eqlist", 'a', "通知再開"));
        runtime.processApplicationMessage(changedEqlist("cenc_eqlist", 'b', "通知恢复"));
        harness.environment.scheduler().runAll();

        assertEquals(4, player.messages().size());
        assertEquals(4, harness.environment.consoleMessages().size());
        assertSame(manager, runtime.webSocketManagerIdentity());
        assertSame(processor, runtime.messageProcessor());
        assertEquals(connections, harness.connector.connectionCount());
    }

    @Test
    void targetReloadAffectsFutureEventsAndQueuedOldGenerationIsCancelled() {
        ReloadHarness harness = reloadHarness("targets", config(true, true, null, "all"));
        NotificationTestSupport.RecordingPlayer player = harness.environment.addPlayer(
                "recipient", "lobby", Set.of(
                        "mceew.notify.all", NotificationSource.SICHUAN_EEW.getPermissionNode()));
        VelocityMceewRuntime runtime = harness.runtime();
        runtime.processApplicationMessage(freshFixture("sc_eew"));
        harness.environment.scheduler().runAll();
        assertEquals(1, player.messages().size());

        writeConfig(harness.dataDirectory, config(true, true, null, "none"));
        List<MCEEWVelocity.ReloadOutcome> outcomes = new ArrayList<>();
        harness.plugin.requestReload(outcomes::add);
        runtime.processApplicationMessage(freshFixture("sc_eew"));
        int playerBeforeCommit = player.messages().size();
        int consoleBeforeCommit = harness.environment.consoleMessages().size();
        harness.environment.scheduler().runAll();

        assertEquals(List.of(MCEEWVelocity.ReloadOutcome.SUCCESS), outcomes);
        assertEquals(playerBeforeCommit, player.messages().size(),
                "queued old-generation delivery is invalidated");
        assertEquals(consoleBeforeCommit, harness.environment.consoleMessages().size(),
                "queued old-generation console delivery is invalidated");

        runtime.processApplicationMessage(freshFixture("sc_eew"));
        harness.environment.scheduler().runAll();
        assertEquals(playerBeforeCommit, player.messages().size());
        assertEquals(consoleBeforeCommit + 1, harness.environment.consoleMessages().size());
        assertEquals(1, harness.connector.connectionCount());
    }

    @Test
    void malformedReloadPreservesOldConfigRuntimeSocketCacheAndDeliveryPolicy() {
        ReloadHarness harness = reloadHarness("malformed", config(true, true, null, "all"));
        NotificationTestSupport.RecordingPlayer player = harness.environment.addPlayer(
                "recipient", "lobby", Set.of(
                        "mceew.notify.all", NotificationSource.SICHUAN_EEW.getPermissionNode()));
        VelocityMceewRuntime runtime = harness.runtime();
        Object runtimeIdentity = runtime;
        Object managerIdentity = runtime.webSocketManagerIdentity();
        Object processorIdentity = runtime.messageProcessor();
        runtime.processApplicationMessage(fixture("jma_eqlist"));
        runtime.processApplicationMessage(fixture("cenc_eqlist"));
        String oldJma = runtime.latestJmaEarthquakeInformation();
        String oldCenc = runtime.latestCencEarthquakeInformation();
        int connections = harness.connector.connectionCount();
        int closeCalls = harness.connector.attempt(0).socket().closeCalls();
        String malformed = "platform_config_version: [\n";
        FilesWrite.write(harness.dataDirectory.resolve("config.yml"), malformed);

        assertEquals(List.of(MCEEWVelocity.ReloadOutcome.FAILED), harness.reload());

        assertEquals(malformed,
                FilesRead.read(harness.dataDirectory.resolve("config.yml")));
        assertSame(runtimeIdentity, harness.plugin.operationalRuntimeIdentity());
        assertSame(managerIdentity, runtime.webSocketManagerIdentity());
        assertSame(processorIdentity, runtime.messageProcessor());
        assertEquals(oldJma, runtime.latestJmaEarthquakeInformation());
        assertEquals(oldCenc, runtime.latestCencEarthquakeInformation());
        assertEquals(connections, harness.connector.connectionCount());
        assertEquals(closeCalls, harness.connector.attempt(0).socket().closeCalls());
        assertEquals(VelocityMessageProcessor.Outcome.FRESH_REALTIME,
                runtime.processApplicationMessage(freshFixture("sc_eew")).outcome());
        harness.environment.scheduler().runAll();
        assertTrue(LEGACY.serialize(player.messages().get(0)).startsWith("§c四川地震预警"));
    }

    @Test
    void enabledDisabledDisabledEnabledTransitionsOwnExactlyOneRuntime() {
        ReloadHarness harness = reloadHarness("transitions", config(true, true, null, "all"));
        harness.environment.scheduler().runAll();
        VelocityMceewRuntime original = harness.runtime();
        assertEquals(1, harness.connector.connectionCount());

        writeConfig(harness.dataDirectory, config(false, true, null, "all"));
        assertEquals(List.of(MCEEWVelocity.ReloadOutcome.SUCCESS), harness.reload());
        assertFalse(harness.plugin.hasOperationalRuntime());
        assertFalse(harness.plugin.loadedRuntimeEnabled());
        assertNull(harness.plugin.latestJmaEarthquakeInformation());
        assertEquals(1, harness.connector.connectionCount());
        assertEquals(1, harness.connector.attempt(0).socket().closeCalls());
        assertTrue(harness.plugin.isCommandRegistered());

        writeConfig(harness.dataDirectory, config(false, false, null, "none"));
        assertEquals(List.of(MCEEWVelocity.ReloadOutcome.SUCCESS), harness.reload());
        assertEquals(1, harness.runtimeCreations.get());
        assertEquals(1, harness.connector.connectionCount());

        writeConfig(harness.dataDirectory, config(true, false, null, "none"));
        assertEquals(List.of(MCEEWVelocity.ReloadOutcome.SUCCESS), harness.reload());
        assertTrue(harness.plugin.hasOperationalRuntime());
        assertTrue(harness.plugin.loadedRuntimeEnabled());
        assertNotEquals(original, harness.runtime());
        assertEquals(2, harness.runtimeCreations.get());
        assertEquals(2, harness.connector.connectionCount());
        assertEquals(0, harness.connector.attempt(1).socket().closeCalls());
    }

    @Test
    void failedStartupCanReloadInvalidThenRecoverWithoutProxyRestart() {
        Path data = temporaryDirectory.resolve("startup-recovery");
        writeConfig(data, "platform_config_version: [\n");
        ReloadHarness harness = reloadHarnessWithoutInitialization(data);
        harness.plugin.onProxyInitialize(new ProxyInitializeEvent());

        assertEquals("FAILED", harness.plugin.lifecycleStateName());
        assertTrue(harness.plugin.isCommandRegistered());
        assertFalse(harness.plugin.hasOperationalRuntime());
        assertEquals(0, harness.connector.connectionCount());
        SimpleCommand command = harness.environment.scheduler().commandManager().command("eew");
        List<Component> replies = new ArrayList<>();
        CommandSource administrator = TestVelocityApi.commandSource(
                Set.of(VelocityCommand.ADMIN_PERMISSION), replies);
        command.execute(TestVelocityApi.invocation(administrator, "eew", "reload"));
        harness.environment.scheduler().runAll();
        assertEquals(List.of(
                "§c[MCEEW] Configuration reload failed; the existing file was left unchanged."),
                legacy(replies));
        assertEquals("FAILED", harness.plugin.lifecycleStateName());

        writeConfig(data, config(true, true, null, "all"));
        replies.clear();
        command.execute(TestVelocityApi.invocation(administrator, "eew", "reload"));
        harness.environment.scheduler().runAll();
        assertEquals(List.of("§a[MCEEW] Configuration reloaded successfully."), legacy(replies));
        assertEquals("ACTIVE", harness.plugin.lifecycleStateName());
        assertTrue(harness.plugin.hasOperationalRuntime());
        assertTrue(harness.plugin.isCommandRegistered());
        assertEquals(1, harness.runtimeCreations.get());
        assertEquals(1, harness.connector.connectionCount());
    }

    @Test
    void disabledToEnabledPreparationFailureRetainsTheValidDisabledState() {
        Path data = temporaryDirectory.resolve("enable-failure");
        writeConfig(data, config(false, true, null, "all"));
        TestVelocityApi.RecordingScheduler scheduler = new TestVelocityApi.RecordingScheduler();
        TestVelocityApi.CapturingLogger logger = TestVelocityApi.logger();
        AtomicInteger runtimeCreations = new AtomicInteger();
        MCEEWVelocity plugin = new MCEEWVelocity(
                TestVelocityApi.proxyServer(scheduler), logger.proxy(), data,
                (loaded, delayScheduler, platformLogger) -> {
                    runtimeCreations.incrementAndGet();
                    throw new IllegalStateException("deliberate runtime preparation failure");
                });
        plugin.onProxyInitialize(new ProxyInitializeEvent());
        assertEquals("ACTIVE", plugin.lifecycleStateName());
        assertFalse(plugin.loadedRuntimeEnabled());

        writeConfig(data, config(true, true, null, "all"));
        List<MCEEWVelocity.ReloadOutcome> outcomes = new ArrayList<>();
        plugin.requestReload(outcomes::add);
        scheduler.runAll();

        assertEquals(List.of(MCEEWVelocity.ReloadOutcome.FAILED), outcomes);
        assertEquals("ACTIVE", plugin.lifecycleStateName());
        assertFalse(plugin.loadedRuntimeEnabled());
        assertFalse(plugin.hasOperationalRuntime());
        assertTrue(plugin.isCommandRegistered());
        assertEquals(1, runtimeCreations.get());
        assertEquals(1, logger.errorCountContaining("reload preparation failed"));
    }

    @Test
    void repeatedAndConcurrentReloadsDoNotAccumulateRuntimeOrInterleave() {
        ReloadHarness harness = reloadHarness("repeat", config(true, true, null, "all"));
        VelocityMceewRuntime runtime = harness.runtime();
        Object manager = runtime.webSocketManagerIdentity();

        for (int index = 0; index < 10; index++) {
            writeConfig(harness.dataDirectory,
                    config(true, index % 2 == 0, "&aGeneration " + index, "all"));
            assertEquals(List.of(MCEEWVelocity.ReloadOutcome.SUCCESS), harness.reload());
        }

        assertSame(runtime, harness.plugin.operationalRuntimeIdentity());
        assertSame(manager, runtime.webSocketManagerIdentity());
        assertEquals(1, harness.runtimeCreations.get());
        assertEquals(1, harness.connector.connectionCount());
        assertEquals(1, runtime.coreLogHandlerCount());
        assertEquals(0, harness.plugin.delayScheduler().ownedTaskCount());

        List<MCEEWVelocity.ReloadOutcome> first = new ArrayList<>();
        List<MCEEWVelocity.ReloadOutcome> second = new ArrayList<>();
        harness.plugin.requestReload(first::add);
        harness.plugin.requestReload(second::add);
        assertTrue(first.isEmpty());
        assertEquals(List.of(MCEEWVelocity.ReloadOutcome.IN_PROGRESS), second);
        assertTrue(harness.plugin.isReloadInProgress());
        harness.environment.scheduler().runAll();
        assertEquals(List.of(MCEEWVelocity.ReloadOutcome.SUCCESS), first);
        assertFalse(harness.plugin.isReloadInProgress());
        assertEquals(1, harness.connector.connectionCount());
    }

    @Test
    void shutdownCancelsPendingReloadUnregistersCommandsAndCannotResurrectRuntime() {
        ReloadHarness harness = reloadHarness("shutdown-race", config(true, true, null, "all"));
        writeConfig(harness.dataDirectory, config(false, true, null, "all"));
        List<MCEEWVelocity.ReloadOutcome> outcomes = new ArrayList<>();
        harness.plugin.requestReload(outcomes::add);
        assertTrue(harness.plugin.isReloadInProgress());

        harness.plugin.onProxyShutdown(new ProxyShutdownEvent());
        harness.environment.scheduler().runAll();

        assertTrue(outcomes.isEmpty());
        assertEquals("SHUTDOWN", harness.plugin.lifecycleStateName());
        assertFalse(harness.plugin.hasOperationalRuntime());
        assertFalse(harness.plugin.isCommandRegistered());
        assertFalse(harness.environment.scheduler().commandManager().hasCommand("eew"));
        assertFalse(harness.environment.scheduler().commandManager().hasCommand("mceew"));
        assertEquals(1, harness.environment.scheduler().commandManager().unregistrations());
        assertEquals(1, harness.connector.connectionCount());
        assertEquals(1, harness.connector.attempt(0).socket().closeCalls());
    }

    @Test
    void reloadCommandReportsSuccessOnlyAfterCommitAndUsesExactFailureText() {
        ReloadHarness harness = reloadHarness("command-reload", config(false, true, null, "all"));
        SimpleCommand command = harness.environment.scheduler().commandManager().command("eew");
        List<Component> replies = new ArrayList<>();
        CommandSource admin = TestVelocityApi.commandSource(
                Set.of(VelocityCommand.ADMIN_PERMISSION), replies);

        command.execute(TestVelocityApi.invocation(admin, "eew", "reload"));
        assertTrue(replies.isEmpty());
        harness.environment.scheduler().runAll();
        assertEquals(List.of("§a[MCEEW] Configuration reloaded successfully."), legacy(replies));
        assertFalse(harness.plugin.hasOperationalRuntime());
        assertEquals(0, harness.connector.connectionCount());

        writeConfig(harness.dataDirectory, "global: not-a-mapping\n");
        replies.clear();
        command.execute(TestVelocityApi.invocation(admin, "eew", "reload"));
        harness.environment.scheduler().runAll();
        assertEquals(List.of(
                "§c[MCEEW] Configuration reload failed; the existing file was left unchanged."),
                legacy(replies));
        assertFalse(harness.plugin.hasOperationalRuntime());
        assertEquals(0, harness.connector.connectionCount());
    }

    private ReloadHarness reloadHarness(String name, String initialConfig) {
        Path data = temporaryDirectory.resolve(name);
        writeConfig(data, initialConfig);
        ReloadHarness harness = reloadHarnessWithoutInitialization(data);
        harness.plugin.onProxyInitialize(new ProxyInitializeEvent());
        assertTrue(harness.plugin.isCommandRegistered());
        return harness;
    }

    private ReloadHarness reloadHarnessWithoutInitialization(Path data) {
        NotificationTestSupport.Environment environment = new NotificationTestSupport.Environment();
        TestVelocityApi.CapturingLogger logger = TestVelocityApi.logger();
        TestWebSocketSupport.RecordingConnector connector =
                new TestWebSocketSupport.RecordingConnector(true);
        AtomicInteger runtimeCreations = new AtomicInteger();
        List<VelocityMceewRuntime> runtimes = new ArrayList<>();
        MCEEWVelocity plugin = new MCEEWVelocity(
                environment.proxy(), logger.proxy(), data,
                (config, scheduler, platformLogger) -> {
                    runtimeCreations.incrementAndGet();
                    VelocityMceewRuntime runtime = new VelocityMceewRuntime(
                            config, scheduler, connector, platformLogger,
                            notificationConfig -> new VelocityNotificationOrchestrator(
                                    notificationConfig,
                                    new VelocityNotificationDispatcher(
                                            environment.proxy(), platformLogger,
                                            scheduler, notificationConfig)));
                    runtimes.add(runtime);
                    return runtime;
                });
        return new ReloadHarness(
                data, environment, logger, connector, runtimeCreations, runtimes, plugin);
    }

    private static String config(
            boolean enabled,
            boolean jmaEnabled,
            String sichuanMessage,
            String targetMode
    ) {
        return config(enabled, jmaEnabled, true, sichuanMessage, targetMode);
    }

    private static String config(
            boolean enabled,
            boolean jmaEnabled,
            boolean cencEnabled,
            String sichuanMessage,
            String targetMode
    ) {
        StringBuilder yaml = new StringBuilder()
                .append("platform_config_version: 1\n")
                .append("global:\n")
                .append("  enabled: ").append(enabled).append('\n')
                .append("  sources:\n")
                .append("    enable_jp: ").append(jmaEnabled).append('\n')
                .append("    enable_cenceew: ").append(cencEnabled).append('\n');
        if (sichuanMessage != null) {
            yaml.append("notifications:\n")
                    .append("  sources:\n")
                    .append("    sichuan:\n")
                    .append("      message: \"")
                    .append(sichuanMessage.replace("\"", "\\\""))
                    .append("\"\n");
        }
        return yaml.append("targets:\n")
                .append("  default:\n")
                .append("    mode: ").append(targetMode).append('\n')
                .append("  sources: {}\n")
                .append("groups: {}\n")
                .append("servers: {}\n")
                .toString();
    }

    private static String freshFixture(String name) {
        String payload = fixture(name);
        return "jma_eew".equals(name)
                ? payload.replaceFirst(
                        "\"AnnouncedTime\"\\s*:\\s*\"[^\"]*\"",
                        "\"AnnouncedTime\":\"not-a-timestamp\"")
                : payload.replaceFirst(
                        "\"ReportTime\"\\s*:\\s*\"[^\"]*\"",
                        "\"ReportTime\":\"not-a-timestamp\"");
    }

    private static String changedEqlist(String name, char md5Character, String region) {
        JsonObject changed = JsonParser.parseString(fixture(name)).getAsJsonObject();
        changed.addProperty("md5", String.valueOf(md5Character).repeat(32));
        changed.getAsJsonObject("No1").addProperty("location", region);
        return changed.toString();
    }

    private static String eqlistConfig(boolean jmaBroadcast, boolean cencBroadcast) {
        return config(true, true, null, "all").replace("targets:\n", ""
                + "notifications:\n"
                + "  sources:\n"
                + "    jma_eqlist:\n"
                + "      broadcast: " + jmaBroadcast + "\n"
                + "    cenc_eqlist:\n"
                + "      broadcast: " + cencBroadcast + "\n"
                + "targets:\n");
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

    private static void writeConfig(Path dataDirectory, String config) {
        try {
            Files.createDirectories(dataDirectory);
            Files.writeString(dataDirectory.resolve("config.yml"), config, StandardCharsets.UTF_8);
        } catch (IOException error) {
            throw new IllegalStateException(error);
        }
    }

    private static List<String> legacy(List<Component> components) {
        List<String> result = new ArrayList<>();
        for (Component component : components) {
            result.add(LEGACY.serialize(component));
        }
        return result;
    }

    private static final class FilesWrite {
        private static void write(Path path, String value) {
            try {
                Files.writeString(path, value, StandardCharsets.UTF_8);
            } catch (IOException error) {
                throw new IllegalStateException(error);
            }
        }
    }

    private static final class FilesRead {
        private static String read(Path path) {
            try {
                return Files.readString(path, StandardCharsets.UTF_8);
            } catch (IOException error) {
                throw new IllegalStateException(error);
            }
        }
    }

    private static final class ReloadHarness {
        private final Path dataDirectory;
        private final NotificationTestSupport.Environment environment;
        private final TestVelocityApi.CapturingLogger logger;
        private final TestWebSocketSupport.RecordingConnector connector;
        private final AtomicInteger runtimeCreations;
        private final List<VelocityMceewRuntime> runtimes;
        private final MCEEWVelocity plugin;

        private ReloadHarness(
                Path dataDirectory,
                NotificationTestSupport.Environment environment,
                TestVelocityApi.CapturingLogger logger,
                TestWebSocketSupport.RecordingConnector connector,
                AtomicInteger runtimeCreations,
                List<VelocityMceewRuntime> runtimes,
                MCEEWVelocity plugin
        ) {
            this.dataDirectory = dataDirectory;
            this.environment = environment;
            this.logger = logger;
            this.connector = connector;
            this.runtimeCreations = runtimeCreations;
            this.runtimes = runtimes;
            this.plugin = plugin;
        }

        private VelocityMceewRuntime runtime() {
            VelocityMceewRuntime runtime =
                    (VelocityMceewRuntime) plugin.operationalRuntimeIdentity();
            assertNotNull(runtime);
            return runtime;
        }

        private List<MCEEWVelocity.ReloadOutcome> reload() {
            List<MCEEWVelocity.ReloadOutcome> outcomes = new ArrayList<>();
            plugin.requestReload(outcomes::add);
            environment.scheduler().runAll();
            return outcomes;
        }
    }
}
