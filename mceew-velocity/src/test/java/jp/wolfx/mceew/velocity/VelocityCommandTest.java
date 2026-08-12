package jp.wolfx.mceew.velocity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import jp.wolfx.mceew.notification.NotificationSource;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VelocityCommandTest {
    private static final LegacyComponentSerializer LEGACY =
            LegacyComponentSerializer.legacySection();

    @TempDir
    Path temporaryDirectory;

    @Test
    void rootAndAliasHaveCompleteExactVersionedOutput() {
        CommandHarness harness = commandHarness("root");
        List<Component> replies = new ArrayList<>();
        CommandSource source = TestVelocityApi.commandSource(Set.of(), replies);
        List<String> expected = List.of(
                "§a[MCEEW] Plugin version: v" + requiredSystemProperty("mceew.project.version"),
                "§a[MCEEW] §3/eew§a - Show available commands",
                "§a[MCEEW] §3/eew test§a - Send a test EEW alert",
                "§a[MCEEW] §3/eew info§a - Display latest earthquake information",
                "§a[MCEEW] §3/eew reload§a - Reload plugin configuration");

        harness.command.execute(TestVelocityApi.invocation(source, "eew"));
        assertEquals(expected, legacy(replies));

        replies.clear();
        harness.command.execute(TestVelocityApi.invocation(source, "mceew"));
        assertEquals(expected, legacy(replies));
        assertEquals(1, harness.environment.scheduler().commandManager().registrations());
    }

    @Test
    void missingUnknownAndCaseInsensitiveRoutesMatchBukkitSurface() {
        CommandHarness harness = commandHarness("routing");
        List<Component> replies = new ArrayList<>();
        CommandSource source = TestVelocityApi.commandSource(
                Set.of(VelocityCommand.ADMIN_PERMISSION), replies);

        harness.command.execute(TestVelocityApi.invocation(source, "eew", "INFO"));
        assertEquals(List.of(
                "§a[MCEEW] §3/eew info jma§a - Show Japan JMA earthquake information.",
                "§a[MCEEW] §3/eew info cenc§a - Show China CENC earthquake information."),
                legacy(replies));

        replies.clear();
        harness.command.execute(TestVelocityApi.invocation(source, "eew", "info", "bad"));
        harness.command.execute(TestVelocityApi.invocation(source, "eew", "unknown"));
        harness.command.execute(TestVelocityApi.invocation(source, "eew", "help"));
        assertTrue(replies.isEmpty());

        harness.command.execute(TestVelocityApi.invocation(source, "eew", "test"));
        assertEquals(List.of(
                "§a[MCEEW] §3/eew test forecast§a - Send JMA forecast EEW test.",
                "§a[MCEEW] §3/eew test alert§a - Send JMA alert EEW test.",
                "§a[MCEEW] §3/eew test sc§a - Send Sichuan EEW test.",
                "§a[MCEEW] §3/eew test fj§a - Send Taiwan/Fujian EEW test.",
                "§a[MCEEW] §3/eew test cwa§a - Send Taiwan CWA EEW test.",
                "§a[MCEEW] §3/eew test cenc§a - Send China CENC EEW test.",
                "§a[MCEEW] §3/eew test cq§a - Send Chongqing EEW test."),
                legacy(replies));
    }

    @Test
    void infoReadsOnlyImmutableCachePresentationsWithoutNetworkSideEffects() {
        CommandHarness harness = commandHarness("info");
        List<Component> replies = new ArrayList<>();
        CommandSource source = TestVelocityApi.commandSource(Set.of(), replies);
        harness.environment.scheduler().runAll();
        List<String> bootstrapMessages = List.copyOf(
                harness.connector.attempt(0).socket().textMessages());

        harness.command.execute(TestVelocityApi.invocation(source, "eew", "info", "jma"));
        harness.command.execute(TestVelocityApi.invocation(source, "eew", "info", "cenc"));
        assertEquals(List.of(
                "[MCEEW] Earthquake information is not available yet.",
                "[MCEEW] Earthquake information is not available yet."), legacy(replies));

        harness.connector.attempt(0).message(fixture("jma_eqlist"));
        harness.connector.attempt(0).message(fixture("cenc_eqlist"));
        replies.clear();
        int requestsBefore = harness.connector.attempt(0).socket().requestCalls();
        harness.command.execute(TestVelocityApi.invocation(source, "eew", "info", "jma"));
        harness.command.execute(TestVelocityApi.invocation(source, "eew", "info", "cenc"));

        List<String> rendered = legacy(replies);
        assertEquals(2, rendered.size());
        assertTrue(rendered.get(0).startsWith("§e地震情報\n"));
        assertTrue(rendered.get(0).contains("能登半島沖"));
        assertTrue(rendered.get(1).startsWith("§e中国地震台网 (正式测定)\n"));
        assertTrue(rendered.get(1).contains("四川测试地区"));
        assertEquals(bootstrapMessages,
                harness.connector.attempt(0).socket().textMessages());
        assertEquals(requestsBefore, harness.connector.attempt(0).socket().requestCalls());
        assertEquals(1, harness.connector.connectionCount());
    }

    @Test
    void everyTestSourceUsesNormalDeliveryThenBroadcastsTheFixedWarningLocally() {
        CommandHarness harness = commandHarness("test-sources");
        Set<String> notificationPermissions = Set.of(
                "mceew.notify.all",
                NotificationSource.JMA_ALERT.getPermissionNode(),
                NotificationSource.JMA_FORECAST.getPermissionNode(),
                NotificationSource.SICHUAN_EEW.getPermissionNode(),
                NotificationSource.FUJIAN_EEW.getPermissionNode(),
                NotificationSource.CWA_EEW.getPermissionNode(),
                NotificationSource.CENC_EEW.getPermissionNode(),
                NotificationSource.CHONGQING_EEW.getPermissionNode());
        NotificationTestSupport.RecordingPlayer player = harness.environment.addPlayer(
                "recipient", "lobby", notificationPermissions);
        List<Component> replies = new ArrayList<>();
        CommandSource administrator = TestVelocityApi.commandSource(
                Set.of(VelocityCommand.ADMIN_PERMISSION), replies);
        harness.environment.scheduler().runAll();
        List<String> bootstrapMessages = List.copyOf(
                harness.connector.attempt(0).socket().textMessages());

        for (String source : List.of("forecast", "alert", "sc", "fj", "cwa", "cenc", "cq")) {
            int playerMessages = player.messages().size();
            int titles = player.titles().size();
            int sounds = player.sounds().size();
            int consoleMessages = harness.environment.consoleMessages().size();

            harness.command.execute(TestVelocityApi.invocation(
                    administrator, "eew", "test", source.toUpperCase()));
            harness.environment.scheduler().runAll();

            assertEquals(playerMessages + 2, player.messages().size(), source);
            assertEquals(titles + 1, player.titles().size(), source);
            assertEquals(sounds + 1, player.sounds().size(), source);
            assertEquals(consoleMessages + 2,
                    harness.environment.consoleMessages().size(), source);
            assertEquals("§eWarning: This is an Earthquake Early Warning test.",
                    LEGACY.serialize(player.messages().get(player.messages().size() - 1)), source);
        }

        assertTrue(replies.isEmpty());
        assertEquals(bootstrapMessages,
                harness.connector.attempt(0).socket().textMessages());
        assertEquals(1, harness.connector.connectionCount());
        assertTrue(harness.plugin.hasOperationalRuntime());
        VelocityMceewRuntime runtime =
                (VelocityMceewRuntime) harness.plugin.operationalRuntimeIdentity();
        assertFalse(runtime.messageProcessor().latestJmaEarthquakeList().isPresent());
        assertFalse(runtime.messageProcessor().latestCencEarthquakeList().isPresent());
    }

    @Test
    void administrativePermissionDenialIsSilentAndSuggestionsArePermissionAware() {
        CommandHarness harness = commandHarness("permissions");
        List<Component> replies = new ArrayList<>();
        CommandSource denied = TestVelocityApi.commandSource(Set.of(), replies);
        int taskCount = harness.environment.scheduler().tasks().size();

        harness.command.execute(TestVelocityApi.invocation(denied, "eew", "test", "alert"));
        harness.command.execute(TestVelocityApi.invocation(denied, "eew", "reload"));
        assertTrue(replies.isEmpty());
        assertEquals(taskCount, harness.environment.scheduler().tasks().size());
        assertEquals(List.of("info"), harness.command.suggest(
                TestVelocityApi.invocation(denied, "eew")));

        CommandSource administrator = TestVelocityApi.commandSource(
                Set.of(VelocityCommand.ADMIN_PERMISSION), replies);
        assertEquals(List.of("info", "test", "reload"), harness.command.suggest(
                TestVelocityApi.invocation(administrator, "eew")));
        assertEquals(List.of("reload"), harness.command.suggest(
                TestVelocityApi.invocation(administrator, "eew", "r")));
        assertEquals(List.of("jma", "cenc"), harness.command.suggest(
                TestVelocityApi.invocation(administrator, "eew", "info", "")));
        assertEquals(List.of("forecast", "alert", "sc", "fj", "cwa", "cenc", "cq"),
                harness.command.suggest(
                        TestVelocityApi.invocation(administrator, "eew", "test", "")));
        assertEquals(List.of("cwa", "cenc", "cq"), harness.command.suggest(
                TestVelocityApi.invocation(administrator, "eew", "test", "c")));
    }

    @Test
    void consoleAndPlayerSourcesUseTheSamePublicPermissionAndAdventureBoundaries() {
        CommandHarness harness = commandHarness("sender-types");
        List<Component> consoleReplies = new ArrayList<>();
        CommandSource console = TestVelocityApi.consoleCommandSource(
                Set.of(VelocityCommand.ADMIN_PERMISSION), consoleReplies);
        harness.command.execute(TestVelocityApi.invocation(console, "eew"));
        assertEquals(5, consoleReplies.size());

        NotificationTestSupport.RecordingPlayer player = harness.environment.addPlayer(
                "sender", "lobby", Set.of());
        harness.command.execute(TestVelocityApi.invocation(
                player.player(), "mceew", "info", "jma"));
        assertEquals(List.of("[MCEEW] Earthquake information is not available yet."),
                legacy(player.messages()));

        int scheduled = harness.environment.scheduler().tasks().size();
        harness.command.execute(TestVelocityApi.invocation(
                player.player(), "mceew", "reload"));
        assertEquals(scheduled, harness.environment.scheduler().tasks().size());
        assertEquals(1, player.messages().size(), "permission denial remains silent");
    }

    @Test
    void commandSenderFailureCannotAffectRuntimeSocketOrCache() {
        CommandHarness harness = commandHarness("sender-failure");
        VelocityMceewRuntime runtime =
                (VelocityMceewRuntime) harness.plugin.operationalRuntimeIdentity();
        Object manager = runtime.webSocketManagerIdentity();
        int connections = harness.connector.connectionCount();

        harness.command.execute(TestVelocityApi.invocation(
                TestVelocityApi.failingCommandSource(
                        Set.of(VelocityCommand.ADMIN_PERMISSION),
                        new IllegalStateException("deliberate sender failure")),
                "eew"));

        assertTrue(harness.plugin.hasOperationalRuntime());
        assertSame(manager, runtime.webSocketManagerIdentity());
        assertEquals(connections, harness.connector.connectionCount());
        assertEquals(0, harness.connector.attempt(0).socket().closeCalls());
        assertFalse(runtime.messageProcessor().latestJmaEarthquakeList().isPresent());
        assertFalse(runtime.messageProcessor().latestCencEarthquakeList().isPresent());
    }

    @Test
    void disabledAndFailedStatesKeepRootAndReloadAvailableWhileInfoAndTestFailClearly()
            throws IOException {
        Path disabledData = temporaryDirectory.resolve("disabled");
        writeMinimalConfig(disabledData, false);
        TestVelocityApi.RecordingScheduler disabledScheduler =
                new TestVelocityApi.RecordingScheduler();
        MCEEWVelocity disabled = new MCEEWVelocity(
                TestVelocityApi.proxyServer(disabledScheduler),
                TestVelocityApi.logger().proxy(), disabledData);
        disabled.onProxyInitialize(new ProxyInitializeEvent());

        assertTrue(disabled.isCommandRegistered());
        SimpleCommand disabledCommand = disabledScheduler.commandManager().command("eew");
        assertNotNull(disabledCommand);
        List<Component> replies = new ArrayList<>();
        CommandSource admin = TestVelocityApi.commandSource(
                Set.of(VelocityCommand.ADMIN_PERMISSION), replies);
        disabledCommand.execute(TestVelocityApi.invocation(admin, "eew", "info", "jma"));
        disabledCommand.execute(TestVelocityApi.invocation(admin, "eew", "test", "alert"));
        assertEquals(List.of(
                "§c[MCEEW] Operational runtime is unavailable.",
                "§c[MCEEW] Operational runtime is unavailable."), legacy(replies));

        Path failedData = temporaryDirectory.resolve("failed");
        Files.createDirectories(failedData);
        Files.writeString(failedData.resolve("config.yml"),
                "platform-config-version: [\n", StandardCharsets.UTF_8);
        TestVelocityApi.RecordingScheduler failedScheduler =
                new TestVelocityApi.RecordingScheduler();
        MCEEWVelocity failed = new MCEEWVelocity(
                TestVelocityApi.proxyServer(failedScheduler),
                TestVelocityApi.logger().proxy(), failedData);
        failed.onProxyInitialize(new ProxyInitializeEvent());

        assertEquals("FAILED", failed.lifecycleStateName());
        assertTrue(failed.isCommandRegistered());
        assertTrue(failedScheduler.commandManager().hasCommand("mceew"));
        replies.clear();
        SimpleCommand failedCommand = failedScheduler.commandManager().command("mceew");
        failedCommand.execute(TestVelocityApi.invocation(admin, "mceew"));
        assertEquals(5, replies.size());
        replies.clear();
        failedCommand.execute(TestVelocityApi.invocation(admin, "mceew", "info", "cenc"));
        failedCommand.execute(TestVelocityApi.invocation(admin, "mceew", "test", "cq"));
        assertEquals(List.of(
                "§c[MCEEW] Operational runtime is unavailable.",
                "§c[MCEEW] Operational runtime is unavailable."), legacy(replies));
    }

    private CommandHarness commandHarness(String name) {
        Path data = temporaryDirectory.resolve(name);
        NotificationTestSupport.Environment environment = new NotificationTestSupport.Environment();
        TestVelocityApi.CapturingLogger logger = TestVelocityApi.logger();
        TestWebSocketSupport.RecordingConnector connector =
                new TestWebSocketSupport.RecordingConnector(true);
        MCEEWVelocity plugin = new MCEEWVelocity(
                environment.proxy(), logger.proxy(), data,
                (config, scheduler, platformLogger) -> new VelocityMceewRuntime(
                        config, scheduler, connector, platformLogger,
                        notificationConfig -> new VelocityNotificationOrchestrator(
                                notificationConfig,
                                new VelocityNotificationDispatcher(
                                        environment.proxy(), platformLogger,
                                        scheduler, notificationConfig))));
        plugin.onProxyInitialize(new ProxyInitializeEvent());
        SimpleCommand command = environment.scheduler().commandManager().command("eew");
        assertNotNull(command);
        assertEquals(command, environment.scheduler().commandManager().command("mceew"));
        return new CommandHarness(environment, connector, plugin, command);
    }

    private static List<String> legacy(List<Component> components) {
        List<String> messages = new ArrayList<>();
        for (Component component : components) {
            messages.add(LEGACY.serialize(component));
        }
        return messages;
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

    private static void writeMinimalConfig(Path dataDirectory, boolean enabled)
            throws IOException {
        Files.createDirectories(dataDirectory);
        Files.writeString(dataDirectory.resolve("config.yml"),
                "platform-config-version: 1\n"
                        + "global:\n"
                        + "  enabled: " + enabled + "\n"
                        + "targets:\n"
                        + "  default:\n"
                        + "    mode: all\n"
                        + "  sources: {}\n"
                        + "groups: {}\n"
                        + "servers: {}\n",
                StandardCharsets.UTF_8);
    }

    private static final class CommandHarness {
        private final NotificationTestSupport.Environment environment;
        private final TestWebSocketSupport.RecordingConnector connector;
        private final MCEEWVelocity plugin;
        private final SimpleCommand command;

        private CommandHarness(
                NotificationTestSupport.Environment environment,
                TestWebSocketSupport.RecordingConnector connector,
                MCEEWVelocity plugin,
                SimpleCommand command
        ) {
            this.environment = environment;
            this.connector = connector;
            this.plugin = plugin;
            this.command = command;
        }
    }
}
