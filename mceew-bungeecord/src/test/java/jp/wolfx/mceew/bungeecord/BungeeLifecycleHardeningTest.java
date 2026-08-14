package jp.wolfx.mceew.bungeecord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.http.WebSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntConsumer;
import java.util.logging.Logger;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import jp.wolfx.mceew.BungeeMessageProcessor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BungeeLifecycleHardeningTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void testAndInfoDuringReloadUseOnlyTheCommittedGeneration() throws Exception {
        Harness harness = harness(snapshot(true, "all"));
        BungeeNotificationTestSupport.FakePlayer player =
                harness.platform.addPlayer("player", "lobby");
        harness.shell.initialize();
        BungeeMceewRuntime runtime = harness.runtime();
        runtime.processApplicationMessage(fixture("jma_eqlist"));
        String cached = harness.shell.latestJmaEarthquakeInformation();

        harness.config.set(snapshot(true, "none"));
        List<BungeePluginShell.ReloadOutcome> outcomes = new ArrayList<>();
        int reloadTask = harness.requestReload(outcomes::add);

        assertEquals(BungeeCommandService.TestOutcome.IN_PROGRESS,
                harness.shell.dispatchTest("forecast"));
        assertEquals(cached, harness.shell.latestJmaEarthquakeInformation());
        harness.backend.run(reloadTask);

        assertEquals(List.of(BungeePluginShell.ReloadOutcome.SUCCESS), outcomes);
        assertSame(runtime, harness.shell.operationalRuntimeIdentity());
        assertEquals(cached, harness.shell.latestJmaEarthquakeInformation());
        assertEquals(BungeeCommandService.TestOutcome.DISPATCHED,
                harness.shell.dispatchTest("forecast"));
        runLast(harness.backend);
        assertEquals(1, player.chats().size(), "target-none emits only the fixed warning");
        assertTrue(player.titles().isEmpty());
    }

    @Test
    void completionFailuresAndOneHundredConcurrentRequestsCannotStrandReload() throws Exception {
        Harness harness = harness(snapshot(false, "all"));
        harness.shell.initialize();
        harness.config.set(snapshot(false, "none"));
        List<BungeePluginShell.ReloadOutcome> first = new ArrayList<>();
        int reloadTask = harness.requestReload(outcome -> {
            first.add(outcome);
            throw new IllegalStateException("sender disconnected");
        });
        List<BungeePluginShell.ReloadOutcome> rejected = new ArrayList<>();
        for (int index = 0; index < 100; index++) {
            harness.shell.requestReload(outcome -> {
                rejected.add(outcome);
                if (rejected.size() == 1) {
                    throw new IllegalStateException("rejected sender disconnected");
                }
            });
        }

        assertEquals(100, rejected.size());
        assertTrue(rejected.stream()
                .allMatch(outcome -> outcome == BungeePluginShell.ReloadOutcome.IN_PROGRESS));
        assertEquals(1, harness.scheduler.ownedTaskCount());
        harness.backend.run(reloadTask);

        assertEquals(List.of(BungeePluginShell.ReloadOutcome.SUCCESS), first);
        assertFalse(harness.shell.reloadInProgress());
        assertEquals(0, harness.scheduler.ownedTaskCount());
        assertEquals(BungeeConfigSnapshot.TargetMode.NONE,
                harness.shell.configSnapshot().defaultTarget().mode());
    }

    @Test
    void shutdownDuringConfigReadCannotPrepareOrPublishResources() throws Exception {
        AtomicReference<BungeePluginShell> shellReference = new AtomicReference<>();
        AtomicInteger loads = new AtomicInteger();
        AtomicInteger runtimeCreations = new AtomicInteger();
        BungeeDelaySchedulerTest.FakeBackend backend =
                new BungeeDelaySchedulerTest.FakeBackend();
        BungeeDelayScheduler scheduler = new BungeeDelayScheduler(backend);
        BungeeConfigSnapshot disabled = snapshot(false, "all");
        BungeeConfigSnapshot enabled = snapshot(true, "all");
        BungeePluginShell.ConfigSource source = () -> {
            if (loads.incrementAndGet() == 1) {
                return disabled;
            }
            shellReference.get().close();
            return enabled;
        };
        BungeePluginShell shell = new BungeePluginShell(
                source, scheduler, logger("shutdown-config-read"),
                (config, runtimeScheduler, runtimeLogger) -> {
                    runtimeCreations.incrementAndGet();
                    throw new AssertionError("runtime must not be prepared after shutdown");
                });
        shellReference.set(shell);
        shell.initialize();
        List<BungeePluginShell.ReloadOutcome> outcomes = new ArrayList<>();
        shell.requestReload(outcomes::add);

        backend.run(0);

        assertEquals(BungeePluginShell.State.SHUTDOWN, shell.state());
        assertEquals(0, runtimeCreations.get());
        assertTrue(outcomes.isEmpty());
        assertTrue(scheduler.isClosed());
    }

    @Test
    void shutdownDuringRuntimePreparationClosesAbandonedRuntimeWithoutConnecting()
            throws Exception {
        AtomicReference<BungeePluginShell> shellReference = new AtomicReference<>();
        AtomicReference<BungeeConfigSnapshot> current =
                new AtomicReference<>(snapshot(false, "all"));
        BungeeDelaySchedulerTest.FakeBackend backend =
                new BungeeDelaySchedulerTest.FakeBackend();
        BungeeDelayScheduler scheduler = new BungeeDelayScheduler(backend);
        TestWebSocketSupport.RecordingConnector connector =
                new TestWebSocketSupport.RecordingConnector(true);
        AtomicReference<BungeeMceewRuntime> prepared = new AtomicReference<>();
        BungeePluginShell shell = new BungeePluginShell(
                current::get, scheduler, logger("shutdown-runtime-prepare"),
                (config, runtimeScheduler, runtimeLogger) -> {
                    shellReference.get().close();
                    BungeeMceewRuntime runtime = new BungeeMceewRuntime(
                            config, runtimeScheduler, connector, runtimeLogger);
                    prepared.set(runtime);
                    return runtime;
                });
        shellReference.set(shell);
        shell.initialize();
        current.set(snapshot(true, "all"));
        List<BungeePluginShell.ReloadOutcome> outcomes = new ArrayList<>();
        shell.requestReload(outcomes::add);

        backend.run(0);

        assertEquals(BungeePluginShell.State.SHUTDOWN, shell.state());
        assertFalse(prepared.get().isActive());
        assertEquals(0, connector.connectionCount());
        assertTrue(outcomes.isEmpty());
    }

    @Test
    void shutdownDuringPolicyPreparationClosesOldAndPreparedPolicies() throws Exception {
        Harness harness = harness(snapshot(true, "all"));
        harness.shell.initialize();
        harness.sinkHook = attempt -> {
            if (attempt == 2) {
                harness.shell.close();
            }
        };
        harness.config.set(snapshot(true, "none"));
        List<BungeePluginShell.ReloadOutcome> outcomes = new ArrayList<>();
        int task = harness.requestReload(outcomes::add);

        harness.backend.run(task);

        assertEquals(BungeePluginShell.State.SHUTDOWN, harness.shell.state());
        assertEquals(2, harness.sinks.size());
        assertTrue(harness.sinks.stream().allMatch(TrackingSink::isClosed));
        assertTrue(outcomes.isEmpty());
        assertEquals(1, harness.connector.connectionCount());
        assertEquals(0, harness.scheduler.ownedTaskCount());
    }

    @Test
    void runtimeStartFailureIsRecoverableWithoutHalfPublishedGeneration() throws Exception {
        AtomicReference<BungeeConfigSnapshot> current =
                new AtomicReference<>(snapshot(true, "all"));
        BungeeDelaySchedulerTest.FakeBackend backend =
                new BungeeDelaySchedulerTest.FakeBackend();
        BungeeDelayScheduler scheduler = new BungeeDelayScheduler(backend);
        TestWebSocketSupport.RecordingConnector working =
                new TestWebSocketSupport.RecordingConnector(true);
        AtomicInteger runtimeAttempts = new AtomicInteger();
        BungeePluginShell shell = new BungeePluginShell(
                current::get, scheduler, logger("runtime-start-failure"),
                (config, runtimeScheduler, runtimeLogger) -> {
                    int attempt = runtimeAttempts.incrementAndGet();
                    if (attempt == 1) {
                        throw new IllegalStateException("runtime preparation unavailable");
                    }
                    return new BungeeMceewRuntime(
                            config, runtimeScheduler, working, runtimeLogger);
                });

        shell.initialize();
        assertEquals(BungeePluginShell.State.FAILED, shell.state());
        assertNull(shell.operationalRuntimeIdentity());
        List<BungeePluginShell.ReloadOutcome> outcomes = new ArrayList<>();
        shell.requestReload(outcomes::add);
        backend.run(0);

        assertEquals(List.of(BungeePluginShell.ReloadOutcome.SUCCESS), outcomes);
        assertEquals(BungeePluginShell.State.ACTIVE, shell.state());
        assertTrue(shell.hasOperationalRuntime());
        assertEquals(2, runtimeAttempts.get());
        assertEquals(1, working.connectionCount());
    }

    @Test
    void unexpectedConfigSourceFailuresRemainRecoverableAndPreserveCommittedState()
            throws Exception {
        AtomicReference<BungeeConfigSnapshot> current = new AtomicReference<>();
        AtomicInteger loads = new AtomicInteger();
        BungeeConfigSnapshot disabled = snapshot(false, "all");
        BungeeConfigSnapshot replacement = snapshot(false, "none");
        BungeeDelaySchedulerTest.FakeBackend backend =
                new BungeeDelaySchedulerTest.FakeBackend();
        BungeeDelayScheduler scheduler = new BungeeDelayScheduler(backend);
        BungeePluginShell shell = new BungeePluginShell(
                () -> {
                    int load = loads.incrementAndGet();
                    if (load == 1 || load == 3) {
                        throw new IllegalStateException("unexpected config source failure");
                    }
                    return current.get();
                },
                scheduler,
                logger("unexpected-config-source"),
                (config, runtimeScheduler, runtimeLogger) -> {
                    throw new AssertionError("disabled config must not construct runtime");
                });

        shell.initialize();
        assertEquals(BungeePluginShell.State.FAILED, shell.state());
        assertNull(shell.configSnapshot());

        current.set(disabled);
        assertEquals(BungeePluginShell.ReloadOutcome.SUCCESS, reload(shell, backend));
        assertSame(disabled, shell.configSnapshot());

        current.set(replacement);
        assertEquals(BungeePluginShell.ReloadOutcome.FAILED, reload(shell, backend));
        assertSame(disabled, shell.configSnapshot());

        assertEquals(BungeePluginShell.ReloadOutcome.SUCCESS, reload(shell, backend));
        assertSame(replacement, shell.configSnapshot());
    }

    @Test
    void policyPreparationFailurePreservesRuntimeAndCanRecover() throws Exception {
        Harness harness = harness(snapshot(true, "all"));
        harness.shell.initialize();
        BungeeMceewRuntime runtime = harness.runtime();
        Object manager = runtime.webSocketManagerIdentity();
        harness.sinkHook = attempt -> {
            if (attempt == 2) {
                throw new IllegalStateException("policy creation failed");
            }
        };
        harness.config.set(snapshot(true, "none"));

        assertEquals(BungeePluginShell.ReloadOutcome.RUNTIME_FAILED, harness.reload());
        assertSame(runtime, harness.shell.operationalRuntimeIdentity());
        assertSame(manager, runtime.webSocketManagerIdentity());
        assertEquals(1, harness.connector.connectionCount());
        assertFalse(harness.sinks.get(0).isClosed());

        harness.sinkHook = ignored -> { };
        assertEquals(BungeePluginShell.ReloadOutcome.SUCCESS, harness.reload());
        assertSame(runtime, harness.shell.operationalRuntimeIdentity());
        assertTrue(harness.sinks.get(0).isClosed());
        assertEquals(1, harness.connector.connectionCount());
    }

    @Test
    void queuedOldPolicyDeliveryIsCancelledWhenNewGenerationCommits() throws Exception {
        Harness harness = harness(snapshot(true, "all"));
        BungeeNotificationTestSupport.FakePlayer player =
                harness.platform.addPlayer("player", "lobby");
        harness.shell.initialize();
        TrackingSink old = harness.sinks.get(0);
        assertEquals(BungeeCommandService.TestOutcome.DISPATCHED,
                harness.shell.dispatchTest("forecast"));
        assertEquals(1, old.pendingDeliveryCount());
        int queuedDelivery = harness.backend.tasks.size() - 1;

        harness.config.set(snapshot(true, "none"));
        List<BungeePluginShell.ReloadOutcome> outcomes = new ArrayList<>();
        int reloadTask = harness.requestReload(outcomes::add);
        harness.backend.run(reloadTask);
        harness.backend.run(queuedDelivery);

        assertEquals(List.of(BungeePluginShell.ReloadOutcome.SUCCESS), outcomes);
        assertTrue(old.isClosed());
        assertEquals(0, old.pendingDeliveryCount());
        assertTrue(player.chats().isEmpty());
        assertTrue(harness.platform.consoleMessages().isEmpty());
    }

    @Test
    void reconnectAndBootstrapTasksRespectReloadDisableAndShutdown() throws Exception {
        Harness harness = harness(snapshot(true, "all"));
        harness.shell.initialize();
        BungeeMceewRuntime runtime = harness.runtime();
        TestWebSocketSupport.RecordingWebSocket firstSocket =
                harness.connector.attempt(0).socket();
        int bootstrapTask = harness.backend.tasks.size() - 1;

        harness.config.set(snapshot(true, "none"));
        assertEquals(BungeePluginShell.ReloadOutcome.SUCCESS, harness.reload());
        harness.backend.run(bootstrapTask);
        assertEquals(List.of("query_jmaeqlist", "query_cenceqlist"),
                firstSocket.textMessages());
        assertSame(runtime, harness.shell.operationalRuntimeIdentity());

        harness.connector.attempt(0).closeFromPeer(WebSocket.NORMAL_CLOSURE, "retry");
        int reconnectTask = harness.backend.tasks.size() - 1;
        harness.config.set(snapshot(true, "all"));
        assertEquals(BungeePluginShell.ReloadOutcome.SUCCESS, harness.reload());
        harness.backend.run(reconnectTask);
        assertEquals(2, harness.connector.connectionCount());
        assertSame(runtime, harness.shell.operationalRuntimeIdentity());

        harness.connector.attempt(1).closeFromPeer(WebSocket.NORMAL_CLOSURE, "disable");
        int cancelledReconnect = harness.backend.tasks.size() - 1;
        harness.config.set(snapshot(false, "all"));
        assertEquals(BungeePluginShell.ReloadOutcome.SUCCESS, harness.reload());
        harness.backend.run(cancelledReconnect);
        assertEquals(2, harness.connector.connectionCount());
        assertFalse(runtime.isActive());

        harness.shell.close();
        assertEquals(0, harness.scheduler.ownedTaskCount());
    }

    @Test
    void notificationSinkFailureCannotEscapeIntoWebsocketLifecycle() throws Exception {
        BungeeDelaySchedulerTest.FakeBackend backend =
                new BungeeDelaySchedulerTest.FakeBackend();
        BungeeDelayScheduler scheduler = new BungeeDelayScheduler(backend);
        TestWebSocketSupport.RecordingConnector connector =
                new TestWebSocketSupport.RecordingConnector(true);
        BungeeNotificationSink failing = new BungeeNotificationSink() {
            @Override
            public void accept(BungeeMessageProcessor.ProcessingResult result) {
                throw new IllegalStateException("downstream failure");
            }

            @Override
            public boolean dispatchTest(String sourceKey) {
                throw new IllegalStateException("test failure");
            }

            @Override
            public void close() {
            }
        };
        BungeeMceewRuntime runtime = new BungeeMceewRuntime(
                snapshot(true, "all"), scheduler, connector,
                logger("sink-failure"), failing);
        runtime.start();

        runtime.processApplicationMessage(freshFixture("sc_eew"));
        assertFalse(runtime.dispatchTest("forecast"));

        assertTrue(runtime.isActive());
        assertEquals(1, connector.connectionCount());
    }

    @Test
    void fiftyReloadsAndFiveDisableCyclesBalanceEveryOwnedGeneration() throws Exception {
        Harness harness = harness(snapshot(true, "all"));
        harness.shell.initialize();
        BungeeMceewRuntime currentRuntime = harness.runtime();
        int runtimeChanges = 0;

        for (int iteration = 1; iteration <= 50; iteration++) {
            harness.config.set(snapshot(true, iteration % 2 == 0 ? "all" : "none"));
            assertEquals(BungeePluginShell.ReloadOutcome.SUCCESS, harness.reload());
            assertSame(currentRuntime, harness.shell.operationalRuntimeIdentity());
            assertEquals(1, harness.connector.connectionCount() - runtimeChanges);

            if (iteration % 10 == 0) {
                harness.config.set(snapshot(false, "all"));
                assertEquals(BungeePluginShell.ReloadOutcome.SUCCESS, harness.reload());
                assertFalse(currentRuntime.isActive());
                harness.config.set(snapshot(true, "all"));
                assertEquals(BungeePluginShell.ReloadOutcome.SUCCESS, harness.reload());
                BungeeMceewRuntime replacement = harness.runtime();
                assertNotSame(currentRuntime, replacement);
                currentRuntime = replacement;
                runtimeChanges++;
            }
        }

        assertEquals(6, harness.runtimeCreations.get());
        assertEquals(6, harness.connector.connectionCount());
        assertEquals(56, harness.sinks.size());
        assertEquals(55, harness.sinks.stream().filter(TrackingSink::isClosed).count());
        assertEquals(1, harness.scheduler.ownedTaskCount(),
                "only the current runtime's paced bootstrap remains pending");

        harness.shell.close();
        assertEquals(56, harness.sinks.stream().filter(TrackingSink::isClosed).count());
        assertEquals(0, harness.scheduler.ownedTaskCount());
    }

    private Harness harness(BungeeConfigSnapshot initial) {
        return new Harness(initial);
    }

    private BungeeConfigSnapshot snapshot(boolean enabled, String mode) throws Exception {
        Path directory = temporaryDirectory.resolve("config-" + System.nanoTime());
        Files.createDirectories(directory);
        Files.writeString(directory.resolve("config.yml"),
                "platform_config_version: 1\n"
                        + "global:\n  enabled: " + enabled + "\n"
                        + "targets:\n  default:\n    mode: " + mode
                        + "\n  sources: {}\n"
                        + "groups: {}\nservers: {}\n");
        return new BungeeConfigLoader(
                directory, getClass().getClassLoader()).loadSnapshot();
    }

    private static String fixture(String name) {
        Path root = Path.of(requiredSystemProperty("mceew.reactor.root"));
        Path path = root.resolve(
                "mceew-bukkit/src/test/resources/websocket/current-schema/" + name + ".json");
        try {
            return Files.readString(path);
        } catch (IOException error) {
            throw new IllegalStateException("Unable to read fixture: " + path, error);
        }
    }

    private static String freshFixture(String name) {
        JsonObject payload = JsonParser.parseString(fixture(name)).getAsJsonObject();
        payload.addProperty("ReportTime", "not-a-timestamp");
        return payload.toString();
    }

    private static String requiredSystemProperty(String name) {
        String value = System.getProperty(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Required Maven test property is missing: " + name);
        }
        return value;
    }

    private static Logger logger(String name) {
        Logger logger = Logger.getLogger("BungeeLifecycleHardeningTest." + name);
        logger.setUseParentHandlers(false);
        return logger;
    }

    private static void runLast(BungeeDelaySchedulerTest.FakeBackend backend) {
        backend.run(backend.tasks.size() - 1);
    }

    private static BungeePluginShell.ReloadOutcome reload(
            BungeePluginShell shell,
            BungeeDelaySchedulerTest.FakeBackend backend
    ) {
        List<BungeePluginShell.ReloadOutcome> outcomes = new ArrayList<>();
        int task = backend.tasks.size();
        shell.requestReload(outcomes::add);
        backend.run(task);
        assertEquals(1, outcomes.size());
        return outcomes.get(0);
    }

    private final class Harness {
        private final AtomicReference<BungeeConfigSnapshot> config;
        private final BungeeDelaySchedulerTest.FakeBackend backend =
                new BungeeDelaySchedulerTest.FakeBackend();
        private final BungeeDelayScheduler scheduler = new BungeeDelayScheduler(backend);
        private final TestWebSocketSupport.RecordingConnector connector =
                new TestWebSocketSupport.RecordingConnector(true);
        private final BungeeNotificationTestSupport.FakePlatform platform =
                new BungeeNotificationTestSupport.FakePlatform();
        private final AtomicInteger runtimeCreations = new AtomicInteger();
        private final AtomicInteger sinkAttempts = new AtomicInteger();
        private final List<TrackingSink> sinks = new ArrayList<>();
        private final BungeePluginShell shell;
        private IntConsumer sinkHook = ignored -> { };

        private Harness(BungeeConfigSnapshot initial) {
            config = new AtomicReference<>(initial);
            Logger logger = logger("harness-" + System.nanoTime());
            shell = new BungeePluginShell(
                    config::get,
                    scheduler,
                    logger,
                    (snapshot, runtimeScheduler, runtimeLogger) -> {
                        runtimeCreations.incrementAndGet();
                        BungeeMceewRuntime.NotificationOrchestratorFactory factory = current -> {
                            int attempt = sinkAttempts.incrementAndGet();
                            sinkHook.accept(attempt);
                            BungeeNotificationDispatcher dispatcher =
                                    new BungeeNotificationDispatcher(
                                            platform, runtimeScheduler, current, runtimeLogger);
                            TrackingSink sink = new TrackingSink(
                                    new BungeeNotificationOrchestrator(current, dispatcher),
                                    dispatcher);
                            sinks.add(sink);
                            return sink;
                        };
                        return new BungeeMceewRuntime(
                                snapshot, runtimeScheduler, connector, runtimeLogger, factory);
                    });
        }

        private BungeeMceewRuntime runtime() {
            return (BungeeMceewRuntime) shell.operationalRuntimeIdentity();
        }

        private int requestReload(
                java.util.function.Consumer<BungeePluginShell.ReloadOutcome> completion
        ) {
            int index = backend.tasks.size();
            shell.requestReload(completion);
            return index;
        }

        private BungeePluginShell.ReloadOutcome reload() {
            List<BungeePluginShell.ReloadOutcome> outcomes = new ArrayList<>();
            int index = requestReload(outcomes::add);
            backend.run(index);
            assertEquals(1, outcomes.size());
            return outcomes.get(0);
        }
    }

    private static final class TrackingSink implements BungeeNotificationSink {
        private final BungeeNotificationOrchestrator delegate;
        private final BungeeNotificationDispatcher dispatcher;
        private boolean closed;

        private TrackingSink(
                BungeeNotificationOrchestrator delegate,
                BungeeNotificationDispatcher dispatcher
        ) {
            this.delegate = delegate;
            this.dispatcher = dispatcher;
        }

        @Override
        public void accept(BungeeMessageProcessor.ProcessingResult result) {
            delegate.accept(result);
        }

        @Override
        public boolean dispatchTest(String sourceKey) {
            return delegate.dispatchTest(sourceKey);
        }

        @Override
        public void close() {
            if (!closed) {
                closed = true;
                delegate.close();
            }
        }

        private boolean isClosed() {
            return closed;
        }

        private int pendingDeliveryCount() {
            return dispatcher.pendingDeliveryCount();
        }
    }
}
