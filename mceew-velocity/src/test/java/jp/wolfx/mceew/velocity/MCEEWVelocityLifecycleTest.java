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
        MCEEWVelocity plugin = new MCEEWVelocity(
                TestVelocityApi.proxyServer(scheduler), logger.proxy(), dataDirectory);

        plugin.onProxyInitialize(new ProxyInitializeEvent());
        plugin.onProxyInitialize(new ProxyInitializeEvent());

        assertTrue(plugin.isOperational());
        assertEquals("ACTIVE", plugin.lifecycleStateName());
        assertEquals(1, plugin.loadedPlatformConfigVersion());
        assertTrue(Files.isRegularFile(dataDirectory.resolve("config.yml")));
        assertEquals(1, logger.infoCountContaining("platform shell initialized"));
        assertEquals(0, scheduler.tasks().size());
    }

    @Test
    void shutdownCancelsOwnedTasksAndIsIdempotent() {
        TestVelocityApi.RecordingScheduler scheduler = new TestVelocityApi.RecordingScheduler();
        TestVelocityApi.CapturingLogger logger = TestVelocityApi.logger();
        MCEEWVelocity plugin = new MCEEWVelocity(
                TestVelocityApi.proxyServer(scheduler), logger.proxy(), temporaryDirectory.resolve("mceew"));
        plugin.onProxyInitialize(new ProxyInitializeEvent());
        AtomicInteger executions = new AtomicInteger();
        plugin.delayScheduler().schedule(executions::incrementAndGet, 30L, TimeUnit.SECONDS);

        plugin.onProxyShutdown(new ProxyShutdownEvent());
        plugin.onProxyShutdown(new ProxyShutdownEvent());
        scheduler.runAll();

        assertFalse(plugin.isOperational());
        assertEquals("SHUTDOWN", plugin.lifecycleStateName());
        assertEquals(-1, plugin.loadedPlatformConfigVersion());
        assertTrue(plugin.delayScheduler().isClosed());
        assertEquals(0, plugin.delayScheduler().ownedTaskCount());
        assertEquals(0, executions.get());
        assertEquals(TaskStatus.CANCELLED, scheduler.tasks().get(0).status());
        assertEquals(1, logger.infoCountContaining("platform shell shut down"));
    }

    @Test
    void failedConfigLeavesRuntimeInactive() throws IOException {
        Path dataDirectory = temporaryDirectory.resolve("mceew");
        Files.createDirectories(dataDirectory);
        Files.writeString(dataDirectory.resolve("config.yml"), "platform-config-version: [\n");
        TestVelocityApi.CapturingLogger logger = TestVelocityApi.logger();
        MCEEWVelocity plugin = new MCEEWVelocity(
                TestVelocityApi.proxyServer(new TestVelocityApi.RecordingScheduler()),
                logger.proxy(),
                dataDirectory);

        plugin.onProxyInitialize(new ProxyInitializeEvent());
        plugin.onProxyInitialize(new ProxyInitializeEvent());

        assertFalse(plugin.isOperational());
        assertEquals("FAILED", plugin.lifecycleStateName());
        assertEquals(-1, plugin.loadedPlatformConfigVersion());
        assertEquals(1, logger.errorCountContaining("runtime remains inactive"));
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
}
