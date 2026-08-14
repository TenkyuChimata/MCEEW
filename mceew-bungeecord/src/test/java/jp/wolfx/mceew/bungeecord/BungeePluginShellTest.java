package jp.wolfx.mceew.bungeecord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import jp.wolfx.mceew.BungeeMessageProcessor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BungeePluginShellTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void disabledConfigurationInitializesShellWithoutOperationalRuntime() throws Exception {
        write(config(false, "all"));
        Harness harness = harness();

        harness.shell.initialize();

        assertEquals(BungeePluginShell.State.ACTIVE, harness.shell.state());
        assertFalse(harness.shell.configSnapshot().runtimeEnabled());
        assertNull(harness.shell.latestJmaEarthquakeInformation());
        assertNull(harness.shell.latestCencEarthquakeInformation());
        assertFalse(harness.shell.dispatchTest("forecast"));
        assertEquals(0, harness.backend.tasks.size());
    }

    @Test
    void enabledConfigurationStartsExactlyOneOperationalRuntime() throws Exception {
        write(config(true, "all"));
        Harness harness = harness();

        harness.shell.initialize();
        harness.shell.initialize();

        assertEquals(BungeePluginShell.State.ACTIVE, harness.shell.state());
        assertTrue(harness.shell.configSnapshot().runtimeEnabled());
        assertTrue(harness.shell.hasOperationalRuntime());
        assertFalse(harness.shell.dispatchTest("alert"));
        assertEquals(1, harness.runtimeCreations.get());
        assertEquals(1, harness.connector.connectionCount());
    }

    @Test
    void invalidStartupKeepsRecoverableShell() throws Exception {
        write("platform_config_version: [\n");
        Harness harness = harness();
        harness.shell.initialize();
        assertEquals(BungeePluginShell.State.FAILED, harness.shell.state());
        assertNull(harness.shell.configSnapshot());

        write(config(false, "all"));
        List<BungeePluginShell.ReloadOutcome> outcomes = new ArrayList<>();
        harness.shell.requestReload(outcomes::add);
        harness.backend.run(0);

        assertEquals(List.of(BungeePluginShell.ReloadOutcome.SUCCESS), outcomes);
        assertEquals(BungeePluginShell.State.ACTIVE, harness.shell.state());
        assertFalse(harness.shell.configSnapshot().runtimeEnabled());
    }

    @Test
    void invalidReloadPreservesPreviousSnapshot() throws Exception {
        write(config(false, "all"));
        Harness harness = harness();
        harness.shell.initialize();
        BungeeConfigSnapshot original = harness.shell.configSnapshot();
        write("platform_config_version: [\n");

        List<BungeePluginShell.ReloadOutcome> outcomes = new ArrayList<>();
        harness.shell.requestReload(outcomes::add);
        harness.backend.run(0);

        assertEquals(List.of(BungeePluginShell.ReloadOutcome.FAILED), outcomes);
        assertSame(original, harness.shell.configSnapshot());
        assertEquals(BungeePluginShell.State.ACTIVE, harness.shell.state());
    }

    @Test
    void validReloadAtomicallyReplacesSnapshot() throws Exception {
        write(config(false, "all"));
        Harness harness = harness();
        harness.shell.initialize();
        BungeeConfigSnapshot original = harness.shell.configSnapshot();
        write(config(false, "none"));

        List<BungeePluginShell.ReloadOutcome> outcomes = new ArrayList<>();
        harness.shell.requestReload(outcomes::add);
        assertSame(original, harness.shell.configSnapshot());
        harness.backend.run(0);

        assertEquals(List.of(BungeePluginShell.ReloadOutcome.SUCCESS), outcomes);
        assertNotSame(original, harness.shell.configSnapshot());
        assertEquals(BungeeConfigSnapshot.TargetMode.NONE,
                harness.shell.configSnapshot().defaultTarget().mode());
    }

    @Test
    void concurrentReloadIsRejectedWithoutSecondTask() throws Exception {
        write(config(false, "all"));
        Harness harness = harness();
        harness.shell.initialize();
        List<BungeePluginShell.ReloadOutcome> first = new ArrayList<>();
        List<BungeePluginShell.ReloadOutcome> second = new ArrayList<>();

        harness.shell.requestReload(first::add);
        harness.shell.requestReload(second::add);

        assertTrue(harness.shell.reloadInProgress());
        assertEquals(List.of(BungeePluginShell.ReloadOutcome.IN_PROGRESS), second);
        assertEquals(1, harness.backend.tasks.size());
        harness.backend.run(0);
        assertEquals(List.of(BungeePluginShell.ReloadOutcome.SUCCESS), first);
        assertFalse(harness.shell.reloadInProgress());
    }

    @Test
    void shutdownCancelsPendingReloadAndPreventsCommit() throws Exception {
        write(config(false, "all"));
        Harness harness = harness();
        harness.shell.initialize();
        List<BungeePluginShell.ReloadOutcome> outcomes = new ArrayList<>();
        harness.shell.requestReload(outcomes::add);
        write(config(false, "none"));

        harness.shell.close();
        harness.backend.run(0);

        assertEquals(BungeePluginShell.State.SHUTDOWN, harness.shell.state());
        assertNull(harness.shell.configSnapshot());
        assertTrue(outcomes.isEmpty(), "cancelled task never commits or calls back");
        assertTrue(harness.scheduler.isClosed());
    }

    @Test
    void shutdownIsIdempotentAndReloadBecomesUnavailable() throws Exception {
        write(config(false, "all"));
        Harness harness = harness();
        harness.shell.initialize();
        harness.shell.close();
        harness.shell.close();
        List<BungeePluginShell.ReloadOutcome> outcomes = new ArrayList<>();

        harness.shell.requestReload(outcomes::add);

        assertEquals(List.of(BungeePluginShell.ReloadOutcome.UNAVAILABLE), outcomes);
        assertEquals(BungeePluginShell.State.SHUTDOWN, harness.shell.state());
    }

    @Test
    void repeatedInitializeDoesNotReloadOrScheduleWork() throws Exception {
        write(config(false, "all"));
        Harness harness = harness();
        harness.shell.initialize();
        BungeeConfigSnapshot original = harness.shell.configSnapshot();

        harness.shell.initialize();

        assertSame(original, harness.shell.configSnapshot());
        assertEquals(0, harness.backend.tasks.size());
    }

    @Test
    void enabledReloadPreservesRuntimeManagerProcessorCacheAndConnection() throws Exception {
        write(config(true, "all"));
        Harness harness = harness();
        harness.shell.initialize();
        BungeeMceewRuntime runtime = (BungeeMceewRuntime) harness.shell.operationalRuntimeIdentity();
        Object manager = runtime.webSocketManagerIdentity();
        Object processor = runtime.messageProcessor();
        runtime.processApplicationMessage(fixture("jma_eqlist"));
        String cached = harness.shell.latestJmaEarthquakeInformation();
        int connections = harness.connector.connectionCount();

        write(config(true, "none"));
        List<BungeePluginShell.ReloadOutcome> outcomes = new ArrayList<>();
        harness.shell.requestReload(outcomes::add);
        runLast(harness.backend);

        assertEquals(List.of(BungeePluginShell.ReloadOutcome.SUCCESS), outcomes);
        assertSame(runtime, harness.shell.operationalRuntimeIdentity());
        assertSame(manager, runtime.webSocketManagerIdentity());
        assertSame(processor, runtime.messageProcessor());
        assertEquals(cached, harness.shell.latestJmaEarthquakeInformation());
        assertEquals(connections, harness.connector.connectionCount());
        assertEquals(1, harness.runtimeCreations.get());
    }

    @Test
    void enabledDisabledDisabledEnabledTransitionsOwnExactlyOneRuntimeAtATime()
            throws Exception {
        write(config(true, "all"));
        Harness harness = harness();
        harness.shell.initialize();
        BungeeMceewRuntime first = (BungeeMceewRuntime) harness.shell.operationalRuntimeIdentity();
        first.processApplicationMessage(fixture("jma_eqlist"));
        assertTrue(harness.shell.latestJmaEarthquakeInformation().contains("能登半島沖"));

        write(config(false, "all"));
        assertReload(harness, BungeePluginShell.ReloadOutcome.SUCCESS);
        assertFalse(harness.shell.hasOperationalRuntime());
        assertFalse(first.isActive());
        assertNull(harness.shell.latestJmaEarthquakeInformation());
        assertEquals(1, harness.connector.attempt(0).socket().closeCalls());

        write(config(false, "none"));
        assertReload(harness, BungeePluginShell.ReloadOutcome.SUCCESS);
        assertEquals(1, harness.runtimeCreations.get());

        write(config(true, "none"));
        assertReload(harness, BungeePluginShell.ReloadOutcome.SUCCESS);
        assertTrue(harness.shell.hasOperationalRuntime());
        assertNotSame(first, harness.shell.operationalRuntimeIdentity());
        assertEquals(2, harness.runtimeCreations.get());
        assertEquals(2, harness.connector.connectionCount());
        assertEquals("[MCEEW] Earthquake information is not available yet.",
                harness.shell.latestJmaEarthquakeInformation(),
                "a newly enabled runtime intentionally owns a new empty cache");
    }

    @Test
    void sourceGateReloadTakesEffectWithoutReplacingRuntimeOrConnection() throws Exception {
        write(configWithSichuanGate(true));
        Harness harness = harness();
        harness.shell.initialize();
        BungeeMceewRuntime runtime = (BungeeMceewRuntime) harness.shell.operationalRuntimeIdentity();
        Object manager = runtime.webSocketManagerIdentity();
        Object processor = runtime.messageProcessor();

        write(configWithSichuanGate(false));
        assertReload(harness, BungeePluginShell.ReloadOutcome.SUCCESS);
        assertEquals(BungeeMessageProcessor.Outcome.DISABLED_REALTIME,
                runtime.processApplicationMessage("{\"type\":\"sc_eew\"}").outcome());

        write(configWithSichuanGate(true));
        assertReload(harness, BungeePluginShell.ReloadOutcome.SUCCESS);
        assertEquals(BungeeMessageProcessor.Outcome.STALE_REALTIME,
                runtime.processApplicationMessage(fixture("sc_eew")).outcome());
        assertSame(runtime, harness.shell.operationalRuntimeIdentity());
        assertSame(manager, runtime.webSocketManagerIdentity());
        assertSame(processor, runtime.messageProcessor());
        assertEquals(1, harness.connector.connectionCount());
        assertEquals(1, harness.runtimeCreations.get());
    }

    @Test
    void concurrentEnabledReloadIsRejectedWithoutDuplicateRuntimeOrConnection()
            throws Exception {
        write(config(true, "all"));
        Harness harness = harness();
        harness.shell.initialize();
        Object runtime = harness.shell.operationalRuntimeIdentity();
        List<BungeePluginShell.ReloadOutcome> first = new ArrayList<>();
        List<BungeePluginShell.ReloadOutcome> second = new ArrayList<>();

        harness.shell.requestReload(first::add);
        harness.shell.requestReload(second::add);
        runLast(harness.backend);

        assertEquals(List.of(BungeePluginShell.ReloadOutcome.SUCCESS), first);
        assertEquals(List.of(BungeePluginShell.ReloadOutcome.IN_PROGRESS), second);
        assertSame(runtime, harness.shell.operationalRuntimeIdentity());
        assertEquals(1, harness.runtimeCreations.get());
        assertEquals(1, harness.connector.connectionCount());
    }

    @Test
    void stoppedRuntimeCallbacksCannotMutateReplacementRuntimeCache() throws Exception {
        write(config(true, "all"));
        Harness harness = harness();
        harness.shell.initialize();

        write(config(false, "all"));
        assertReload(harness, BungeePluginShell.ReloadOutcome.SUCCESS);
        write(config(true, "all"));
        assertReload(harness, BungeePluginShell.ReloadOutcome.SUCCESS);
        BungeeMceewRuntime replacement =
                (BungeeMceewRuntime) harness.shell.operationalRuntimeIdentity();

        harness.connector.attempt(0).message(fixture("jma_eqlist"));
        assertFalse(replacement.messageProcessor().hasJmaCacheValue());

        harness.connector.attempt(1).message(fixture("jma_eqlist"));
        assertTrue(replacement.messageProcessor().hasJmaCacheValue());
        assertEquals(2, harness.connector.connectionCount());
    }

    @Test
    void invalidEnabledReloadPreservesRuntimeSocketCacheAndSourcePolicy() throws Exception {
        write(config(true, "all"));
        Harness harness = harness();
        harness.shell.initialize();
        BungeeMceewRuntime runtime = (BungeeMceewRuntime) harness.shell.operationalRuntimeIdentity();
        runtime.processApplicationMessage(fixture("jma_eqlist"));
        Object manager = runtime.webSocketManagerIdentity();
        Object processor = runtime.messageProcessor();
        String cached = harness.shell.latestJmaEarthquakeInformation();
        int connections = harness.connector.connectionCount();
        write("platform_config_version: [\n");

        assertReload(harness, BungeePluginShell.ReloadOutcome.FAILED);

        assertSame(runtime, harness.shell.operationalRuntimeIdentity());
        assertSame(manager, runtime.webSocketManagerIdentity());
        assertSame(processor, runtime.messageProcessor());
        assertEquals(cached, harness.shell.latestJmaEarthquakeInformation());
        assertEquals(connections, harness.connector.connectionCount());
    }

    @Test
    void failedStartupRecoversToOneEnabledRuntime() throws Exception {
        write("platform_config_version: [\n");
        Harness harness = harness();
        harness.shell.initialize();
        assertEquals(BungeePluginShell.State.FAILED, harness.shell.state());
        assertEquals(0, harness.connector.connectionCount());

        write(config(true, "all"));
        assertReload(harness, BungeePluginShell.ReloadOutcome.SUCCESS);

        assertEquals(BungeePluginShell.State.ACTIVE, harness.shell.state());
        assertTrue(harness.shell.hasOperationalRuntime());
        assertEquals(1, harness.runtimeCreations.get());
        assertEquals(1, harness.connector.connectionCount());
    }

    @Test
    void runtimePreparationFailurePreservesValidDisabledState() throws Exception {
        write(config(false, "all"));
        BungeeDelaySchedulerTest.FakeBackend backend =
                new BungeeDelaySchedulerTest.FakeBackend();
        BungeeDelayScheduler scheduler = new BungeeDelayScheduler(backend);
        Logger logger = logger("runtime-failure");
        BungeePluginShell shell = new BungeePluginShell(
                new BungeeConfigLoader(temporaryDirectory, getClass().getClassLoader()),
                scheduler,
                logger,
                (loaded, delayScheduler, platformLogger) -> {
                    throw new IllegalStateException("deliberate runtime construction failure");
                });
        shell.initialize();
        BungeeConfigSnapshot disabled = shell.configSnapshot();

        write(config(true, "all"));
        List<BungeePluginShell.ReloadOutcome> outcomes = new ArrayList<>();
        shell.requestReload(outcomes::add);
        backend.run(0);

        assertEquals(List.of(BungeePluginShell.ReloadOutcome.FAILED), outcomes);
        assertSame(disabled, shell.configSnapshot());
        assertFalse(shell.hasOperationalRuntime());
        assertEquals(BungeePluginShell.State.ACTIVE, shell.state());
    }

    @Test
    void startupRuntimeConstructionFailureLeavesShellRecoverable() throws Exception {
        write(config(true, "all"));
        BungeeDelaySchedulerTest.FakeBackend backend =
                new BungeeDelaySchedulerTest.FakeBackend();
        BungeeDelayScheduler scheduler = new BungeeDelayScheduler(backend);
        TestWebSocketSupport.RecordingConnector connector =
                new TestWebSocketSupport.RecordingConnector(true);
        AtomicInteger attempts = new AtomicInteger();
        BungeePluginShell shell = new BungeePluginShell(
                new BungeeConfigLoader(temporaryDirectory, getClass().getClassLoader()),
                scheduler,
                logger("startup-runtime-failure"),
                (loaded, delayScheduler, platformLogger) -> {
                    if (attempts.incrementAndGet() == 1) {
                        throw new IllegalStateException("deliberate first construction failure");
                    }
                    return new BungeeMceewRuntime(
                            loaded, delayScheduler, connector, platformLogger);
                });

        shell.initialize();
        assertEquals(BungeePluginShell.State.FAILED, shell.state());
        assertNull(shell.configSnapshot());
        assertFalse(shell.hasOperationalRuntime());
        assertEquals(0, connector.connectionCount());

        List<BungeePluginShell.ReloadOutcome> outcomes = new ArrayList<>();
        shell.requestReload(outcomes::add);
        backend.run(0);

        assertEquals(List.of(BungeePluginShell.ReloadOutcome.SUCCESS), outcomes);
        assertEquals(BungeePluginShell.State.ACTIVE, shell.state());
        assertTrue(shell.hasOperationalRuntime());
        assertEquals(2, attempts.get());
        assertEquals(1, connector.connectionCount());
    }

    @Test
    void shutdownStopsRuntimeOnceAndCannotBeResurrectedByReload() throws Exception {
        write(config(true, "all"));
        Harness harness = harness();
        harness.shell.initialize();
        BungeeMceewRuntime runtime = (BungeeMceewRuntime) harness.shell.operationalRuntimeIdentity();
        List<BungeePluginShell.ReloadOutcome> outcomes = new ArrayList<>();
        harness.shell.requestReload(outcomes::add);

        harness.shell.close();
        harness.shell.close();
        runLast(harness.backend);

        assertEquals(BungeePluginShell.State.SHUTDOWN, harness.shell.state());
        assertFalse(runtime.isActive());
        assertNull(harness.shell.operationalRuntimeIdentity());
        assertEquals(1, harness.connector.connectionCount());
        assertEquals(1, harness.connector.attempt(0).socket().closeCalls());
        assertTrue(outcomes.isEmpty());
    }

    private Harness harness() {
        BungeeDelaySchedulerTest.FakeBackend backend =
                new BungeeDelaySchedulerTest.FakeBackend();
        BungeeDelayScheduler scheduler = new BungeeDelayScheduler(backend);
        Logger logger = Logger.getLogger("BungeePluginShellTest");
        logger.setUseParentHandlers(false);
        TestWebSocketSupport.RecordingConnector connector =
                new TestWebSocketSupport.RecordingConnector(true);
        AtomicInteger runtimeCreations = new AtomicInteger();
        BungeePluginShell shell = new BungeePluginShell(
                new BungeeConfigLoader(temporaryDirectory, getClass().getClassLoader()),
                scheduler,
                logger,
                (loaded, delayScheduler, platformLogger) -> {
                    runtimeCreations.incrementAndGet();
                    return new BungeeMceewRuntime(
                            loaded, delayScheduler, connector, platformLogger);
                });
        return new Harness(backend, scheduler, shell, connector, runtimeCreations);
    }

    private static void assertReload(
            Harness harness,
            BungeePluginShell.ReloadOutcome expected
    ) {
        List<BungeePluginShell.ReloadOutcome> outcomes = new ArrayList<>();
        harness.shell.requestReload(outcomes::add);
        runLast(harness.backend);
        assertEquals(List.of(expected), outcomes);
    }

    private static void runLast(BungeeDelaySchedulerTest.FakeBackend backend) {
        backend.run(backend.tasks.size() - 1);
    }

    private static Logger logger(String name) {
        Logger logger = Logger.getLogger("BungeePluginShellTest." + name);
        logger.setUseParentHandlers(false);
        return logger;
    }

    private static String fixture(String name) {
        Path root = Path.of(requiredSystemProperty("mceew.reactor.root"));
        Path fixture = root.resolve(
                "mceew-bukkit/src/test/resources/websocket/current-schema/" + name + ".json");
        try {
            return Files.readString(fixture);
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

    private void write(String value) throws IOException {
        Files.createDirectories(temporaryDirectory);
        Files.writeString(temporaryDirectory.resolve("config.yml"), value);
    }

    private static String config(boolean enabled, String mode) {
        return "platform_config_version: 1\n"
                + "global:\n  enabled: " + enabled + "\n"
                + "targets:\n  default:\n    mode: " + mode + "\n  sources: {}\n"
                + "groups: {}\nservers: {}\n";
    }

    private static String configWithSichuanGate(boolean enabled) {
        return config(true, "all").replace(
                "global:\n  enabled: true\n",
                "global:\n  enabled: true\n  sources:\n    enable_sc: " + enabled + "\n");
    }

    private static final class Harness {
        private final BungeeDelaySchedulerTest.FakeBackend backend;
        private final BungeeDelayScheduler scheduler;
        private final BungeePluginShell shell;
        private final TestWebSocketSupport.RecordingConnector connector;
        private final AtomicInteger runtimeCreations;

        private Harness(
                BungeeDelaySchedulerTest.FakeBackend backend,
                BungeeDelayScheduler scheduler,
                BungeePluginShell shell,
                TestWebSocketSupport.RecordingConnector connector,
                AtomicInteger runtimeCreations
        ) {
            this.backend = backend;
            this.scheduler = scheduler;
            this.shell = shell;
            this.connector = connector;
            this.runtimeCreations = runtimeCreations;
        }
    }
}
