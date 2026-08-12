package jp.wolfx.mceew.velocity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.scheduler.TaskStatus;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MCEEWVelocityLifecycleTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void constructorOnlyCapturesDependencies() {
        Path dataDirectory = temporaryDirectory.resolve("mceew");
        TestVelocityApi.RecordingScheduler scheduler = new TestVelocityApi.RecordingScheduler();
        TestVelocityApi.CapturingLogger logger = TestVelocityApi.logger();

        MCEEWVelocity plugin = new MCEEWVelocity(
                TestVelocityApi.proxyServer(scheduler), logger.proxy(), dataDirectory);

        assertEquals("UNINITIALIZED", plugin.lifecycleStateName());
        assertFalse(plugin.isOperational());
        assertFalse(Files.exists(dataDirectory));
        assertEquals(0, scheduler.schedulerRequests());
        assertEquals(0, scheduler.tasks().size());
    }

    @Test
    void initializeBootstrapsOnlyOnce() {
        Path dataDirectory = temporaryDirectory.resolve("mceew");
        TestVelocityApi.RecordingScheduler scheduler = new TestVelocityApi.RecordingScheduler();
        TestVelocityApi.CapturingLogger logger = TestVelocityApi.logger();
        TestWebSocketSupport.RecordingConnector connector =
                new TestWebSocketSupport.RecordingConnector(true);
        AtomicInteger runtimeCreations = new AtomicInteger();
        MCEEWVelocity plugin = new MCEEWVelocity(
                TestVelocityApi.proxyServer(scheduler),
                logger.proxy(),
                dataDirectory,
                (config, delayScheduler, platformLogger) -> {
                    runtimeCreations.incrementAndGet();
                    return new VelocityMceewRuntime(
                            config, delayScheduler, connector, platformLogger);
                });

        plugin.onProxyInitialize(new ProxyInitializeEvent());
        Object runtimeIdentity = plugin.operationalRuntimeIdentity();
        plugin.onProxyInitialize(new ProxyInitializeEvent());

        assertTrue(plugin.isOperational());
        assertTrue(plugin.hasOperationalRuntime());
        assertEquals("ACTIVE", plugin.lifecycleStateName());
        assertEquals(1, plugin.loadedPlatformConfigVersion());
        assertTrue(plugin.loadedRuntimeEnabled());
        assertTrue(Files.isRegularFile(dataDirectory.resolve("config.yml")));
        assertEquals(1, logger.infoCountContaining("platform shell initialized"));
        assertEquals(1, runtimeCreations.get());
        assertEquals(1, connector.connectionCount());
        assertEquals(runtimeIdentity, plugin.operationalRuntimeIdentity());
        assertEquals(1, ((VelocityMceewRuntime) runtimeIdentity).coreLogHandlerCount());
        assertTrue(plugin.isCommandRegistered());
        assertTrue(scheduler.commandManager().hasCommand("eew"));
        assertTrue(scheduler.commandManager().hasCommand("mceew"));
        assertEquals(1, scheduler.commandManager().registrations());
    }

    @Test
    void shutdownCancelsOwnedTasksAndIsIdempotent() {
        TestVelocityApi.RecordingScheduler scheduler = new TestVelocityApi.RecordingScheduler();
        TestVelocityApi.CapturingLogger logger = TestVelocityApi.logger();
        TestWebSocketSupport.RecordingConnector connector =
                new TestWebSocketSupport.RecordingConnector(true);
        MCEEWVelocity plugin = new MCEEWVelocity(
                TestVelocityApi.proxyServer(scheduler),
                logger.proxy(),
                temporaryDirectory.resolve("mceew"),
                (config, delayScheduler, platformLogger) -> new VelocityMceewRuntime(
                        config, delayScheduler, connector, platformLogger));
        plugin.onProxyInitialize(new ProxyInitializeEvent());
        VelocityMceewRuntime runtime =
                (VelocityMceewRuntime) plugin.operationalRuntimeIdentity();
        AtomicInteger executions = new AtomicInteger();
        plugin.delayScheduler().schedule(executions::incrementAndGet, 30L, TimeUnit.SECONDS);

        plugin.onProxyShutdown(new ProxyShutdownEvent());
        plugin.onProxyShutdown(new ProxyShutdownEvent());
        scheduler.runAll();

        assertFalse(plugin.isOperational());
        assertEquals("SHUTDOWN", plugin.lifecycleStateName());
        assertEquals(-1, plugin.loadedPlatformConfigVersion());
        assertFalse(plugin.hasOperationalRuntime());
        assertTrue(plugin.delayScheduler().isClosed());
        assertEquals(0, plugin.delayScheduler().ownedTaskCount());
        assertEquals(0, executions.get());
        assertTrue(scheduler.tasks().stream()
                .allMatch(task -> task.status() == TaskStatus.CANCELLED));
        assertEquals(1, logger.infoCountContaining("platform shell shut down"));
        assertEquals(1, connector.attempt(0).socket().closeCalls());
        assertEquals(0, runtime.coreLogHandlerCount());
        assertFalse(plugin.isCommandRegistered());
        assertFalse(scheduler.commandManager().hasCommand("eew"));
        assertFalse(scheduler.commandManager().hasCommand("mceew"));
        assertEquals(1, scheduler.commandManager().unregistrations());
    }

    @Test
    void disabledRuntimeInitializesShellWithoutCreatingConnection() throws IOException {
        Path dataDirectory = temporaryDirectory.resolve("mceew-disabled");
        writeConfig(dataDirectory, false);
        TestVelocityApi.RecordingScheduler scheduler = new TestVelocityApi.RecordingScheduler();
        TestWebSocketSupport.RecordingConnector connector =
                new TestWebSocketSupport.RecordingConnector(true);
        AtomicInteger runtimeCreations = new AtomicInteger();
        MCEEWVelocity plugin = new MCEEWVelocity(
                TestVelocityApi.proxyServer(scheduler),
                TestVelocityApi.logger().proxy(),
                dataDirectory,
                (config, delayScheduler, platformLogger) -> {
                    runtimeCreations.incrementAndGet();
                    return new VelocityMceewRuntime(
                            config, delayScheduler, connector, platformLogger);
                });

        plugin.onProxyInitialize(new ProxyInitializeEvent());

        assertTrue(plugin.isOperational());
        assertFalse(plugin.loadedRuntimeEnabled());
        assertFalse(plugin.hasOperationalRuntime());
        assertEquals(0, runtimeCreations.get());
        assertEquals(0, connector.connectionCount());
        assertEquals(0, scheduler.tasks().size());
    }

    @Test
    void failedConfigLeavesRuntimeInactive() throws IOException {
        Path dataDirectory = temporaryDirectory.resolve("mceew");
        Files.createDirectories(dataDirectory);
        Files.writeString(dataDirectory.resolve("config.yml"), "platform-config-version: [\n");
        TestVelocityApi.CapturingLogger logger = TestVelocityApi.logger();
        TestWebSocketSupport.RecordingConnector connector =
                new TestWebSocketSupport.RecordingConnector(true);
        MCEEWVelocity plugin = new MCEEWVelocity(
                TestVelocityApi.proxyServer(new TestVelocityApi.RecordingScheduler()),
                logger.proxy(),
                dataDirectory,
                (config, delayScheduler, platformLogger) -> new VelocityMceewRuntime(
                        config, delayScheduler, connector, platformLogger));

        plugin.onProxyInitialize(new ProxyInitializeEvent());
        plugin.onProxyInitialize(new ProxyInitializeEvent());

        assertFalse(plugin.isOperational());
        assertEquals("FAILED", plugin.lifecycleStateName());
        assertEquals(-1, plugin.loadedPlatformConfigVersion());
        assertEquals(1, logger.errorCountContaining("runtime remains inactive"));
        assertEquals(0, connector.connectionCount());
        assertTrue(plugin.isCommandRegistered());
    }

    @Test
    void runtimeConstructionFailureLeavesNoPartialRuntime() {
        TestVelocityApi.CapturingLogger logger = TestVelocityApi.logger();
        MCEEWVelocity plugin = new MCEEWVelocity(
                TestVelocityApi.proxyServer(new TestVelocityApi.RecordingScheduler()),
                logger.proxy(),
                temporaryDirectory.resolve("mceew-start-failure"),
                (config, delayScheduler, platformLogger) -> {
                    throw new IllegalStateException("runtime factory failed");
                });

        plugin.onProxyInitialize(new ProxyInitializeEvent());

        assertEquals("FAILED", plugin.lifecycleStateName());
        assertFalse(plugin.isOperational());
        assertFalse(plugin.hasOperationalRuntime());
        assertEquals(1, logger.errorCountContaining("operational runtime could not be started"));
    }

    @Test
    void initializeAfterShutdownDoesNotReactivate() {
        TestVelocityApi.RecordingScheduler scheduler = new TestVelocityApi.RecordingScheduler();
        MCEEWVelocity plugin = new MCEEWVelocity(
                TestVelocityApi.proxyServer(scheduler),
                TestVelocityApi.logger().proxy(),
                temporaryDirectory.resolve("mceew"));

        plugin.onProxyShutdown(new ProxyShutdownEvent());
        plugin.onProxyInitialize(new ProxyInitializeEvent());

        assertEquals("SHUTDOWN", plugin.lifecycleStateName());
        assertFalse(plugin.isOperational());
        assertFalse(Files.exists(temporaryDirectory.resolve("mceew")));
    }

    @Test
    void commandRegistrationFailurePreventsOperationalRuntimeStartup() {
        TestVelocityApi.RecordingScheduler scheduler = new TestVelocityApi.RecordingScheduler();
        scheduler.commandManager().failRegistration(
                new IllegalArgumentException("deliberate alias collision"));
        TestVelocityApi.CapturingLogger logger = TestVelocityApi.logger();
        AtomicInteger runtimeCreations = new AtomicInteger();
        MCEEWVelocity plugin = new MCEEWVelocity(
                TestVelocityApi.proxyServer(scheduler), logger.proxy(),
                temporaryDirectory.resolve("command-registration-failure"),
                (config, delayScheduler, platformLogger) -> {
                    runtimeCreations.incrementAndGet();
                    throw new AssertionError("runtime must not be created");
                });

        plugin.onProxyInitialize(new ProxyInitializeEvent());

        assertEquals("FAILED", plugin.lifecycleStateName());
        assertFalse(plugin.isCommandRegistered());
        assertFalse(plugin.hasOperationalRuntime());
        assertEquals(0, runtimeCreations.get());
        assertEquals(1, logger.errorCountContaining("command registration failed"));
        assertFalse(Files.exists(temporaryDirectory.resolve("command-registration-failure")));
    }

    private static void writeConfig(Path dataDirectory, boolean enabled) throws IOException {
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
                        + "servers: {}\n");
    }
}
