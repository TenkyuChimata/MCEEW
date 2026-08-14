package jp.wolfx.mceew.velocity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.bstats.velocity.Metrics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VelocityMetricsLifecycleTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void normalAndDuplicateInitializeUseTheExactPluginIdOnlyOnce() throws Exception {
        Path data = temporaryDirectory.resolve("normal");
        writeConfig(data, true);
        TestVelocityApi.RecordingScheduler scheduler = new TestVelocityApi.RecordingScheduler();
        RecordingMetricsCreator metrics = new RecordingMetricsCreator();
        TestWebSocketSupport.RecordingConnector connector =
                new TestWebSocketSupport.RecordingConnector(true);
        MCEEWVelocity plugin = plugin(data, scheduler, metrics, connector);

        assertEquals(0, metrics.calls);
        plugin.onProxyInitialize(new ProxyInitializeEvent());
        plugin.onProxyInitialize(new ProxyInitializeEvent());

        assertEquals(1, metrics.calls);
        assertEquals(List.of(33363), metrics.pluginIds);
        assertSame(plugin, metrics.plugins.get(0));
        assertEquals(1, connector.connectionCount());
        assertTrue(plugin.isCommandRegistered());

        plugin.onProxyShutdown(new ProxyShutdownEvent());
        plugin.onProxyShutdown(new ProxyShutdownEvent());
        plugin.onProxyInitialize(new ProxyInitializeEvent());

        assertEquals(1, metrics.calls);
        assertEquals("SHUTDOWN", plugin.lifecycleStateName());
        assertFalse(plugin.hasOperationalRuntime());
    }

    @Test
    void disabledOperationalRuntimeStillInitializesMetricsOnceWithoutWolfx() throws Exception {
        Path data = temporaryDirectory.resolve("disabled");
        writeConfig(data, false);
        TestVelocityApi.RecordingScheduler scheduler = new TestVelocityApi.RecordingScheduler();
        RecordingMetricsCreator metrics = new RecordingMetricsCreator();
        TestWebSocketSupport.RecordingConnector connector =
                new TestWebSocketSupport.RecordingConnector(true);
        MCEEWVelocity plugin = plugin(data, scheduler, metrics, connector);

        plugin.onProxyInitialize(new ProxyInitializeEvent());

        assertEquals(1, metrics.calls);
        assertEquals(List.of(33363), metrics.pluginIds);
        assertTrue(plugin.isOperational());
        assertFalse(plugin.hasOperationalRuntime());
        assertEquals(0, connector.connectionCount());
    }

    @Test
    void everySuccessfulAndFailedReloadLeavesMetricsAtOneInstance() throws Exception {
        Path data = temporaryDirectory.resolve("reloads");
        writeConfig(data, true);
        TestVelocityApi.RecordingScheduler scheduler = new TestVelocityApi.RecordingScheduler();
        RecordingMetricsCreator metrics = new RecordingMetricsCreator();
        TestWebSocketSupport.RecordingConnector connector =
                new TestWebSocketSupport.RecordingConnector(true);
        MCEEWVelocity plugin = plugin(data, scheduler, metrics, connector);
        plugin.onProxyInitialize(new ProxyInitializeEvent());

        assertReload(plugin, scheduler, data, true, MCEEWVelocity.ReloadOutcome.SUCCESS);
        assertEquals(1, connector.connectionCount());
        assertReload(plugin, scheduler, data, false, MCEEWVelocity.ReloadOutcome.SUCCESS);
        assertFalse(plugin.hasOperationalRuntime());
        assertEquals(1, connector.connectionCount());
        assertReload(plugin, scheduler, data, true, MCEEWVelocity.ReloadOutcome.SUCCESS);
        assertTrue(plugin.hasOperationalRuntime());
        assertEquals(2, connector.connectionCount());

        Files.writeString(data.resolve("config.yml"),
                "platform_config_version: [\n", StandardCharsets.UTF_8);
        List<MCEEWVelocity.ReloadOutcome> outcomes = new ArrayList<>();
        plugin.requestReload(outcomes::add);
        scheduler.runAll();

        assertEquals(List.of(MCEEWVelocity.ReloadOutcome.FAILED), outcomes);
        assertEquals(1, metrics.calls);
        assertEquals(List.of(33363), metrics.pluginIds);
        assertTrue(plugin.hasOperationalRuntime());
        assertEquals(2, connector.connectionCount());
    }

    @Test
    void invalidStartupAndRecoveryReloadKeepTheOriginalMetricsInstance() throws Exception {
        Path data = temporaryDirectory.resolve("recovery");
        Files.createDirectories(data);
        Files.writeString(data.resolve("config.yml"),
                "platform_config_version: [\n", StandardCharsets.UTF_8);
        TestVelocityApi.RecordingScheduler scheduler = new TestVelocityApi.RecordingScheduler();
        RecordingMetricsCreator metrics = new RecordingMetricsCreator();
        TestWebSocketSupport.RecordingConnector connector =
                new TestWebSocketSupport.RecordingConnector(true);
        MCEEWVelocity plugin = plugin(data, scheduler, metrics, connector);

        plugin.onProxyInitialize(new ProxyInitializeEvent());
        assertEquals("FAILED", plugin.lifecycleStateName());
        assertEquals(1, metrics.calls);
        assertEquals(0, connector.connectionCount());

        assertReload(plugin, scheduler, data, true, MCEEWVelocity.ReloadOutcome.SUCCESS);

        assertEquals("ACTIVE", plugin.lifecycleStateName());
        assertEquals(1, metrics.calls);
        assertEquals(List.of(33363), metrics.pluginIds);
        assertEquals(1, connector.connectionCount());
    }

    @Test
    void metricsFactoryRuntimeFailureIsLoggedAndDoesNotBlockThePlugin() throws Exception {
        Path data = temporaryDirectory.resolve("metrics-failure");
        writeConfig(data, true);
        TestVelocityApi.RecordingScheduler scheduler = new TestVelocityApi.RecordingScheduler();
        IllegalStateException failure = new IllegalStateException("deliberate metrics failure");
        RecordingMetricsCreator metrics = new RecordingMetricsCreator(failure);
        TestWebSocketSupport.RecordingConnector connector =
                new TestWebSocketSupport.RecordingConnector(true);
        TestVelocityApi.CapturingLogger logger = TestVelocityApi.logger();
        MCEEWVelocity plugin = plugin(data, scheduler, metrics, connector, logger);

        plugin.onProxyInitialize(new ProxyInitializeEvent());

        assertEquals(1, metrics.calls);
        assertEquals(List.of(33363), metrics.pluginIds);
        assertEquals(1, logger.warningCountContaining("continuing without metrics"));
        assertTrue(logger.capturedThrowable(failure));
        assertTrue(plugin.isOperational());
        assertTrue(plugin.isCommandRegistered());
        assertTrue(plugin.hasOperationalRuntime());
        assertEquals(1, connector.connectionCount());
    }

    private MCEEWVelocity plugin(
            Path data,
            TestVelocityApi.RecordingScheduler scheduler,
            RecordingMetricsCreator metrics,
            TestWebSocketSupport.RecordingConnector connector
    ) {
        return plugin(data, scheduler, metrics, connector, TestVelocityApi.logger());
    }

    private MCEEWVelocity plugin(
            Path data,
            TestVelocityApi.RecordingScheduler scheduler,
            RecordingMetricsCreator metrics,
            TestWebSocketSupport.RecordingConnector connector,
            TestVelocityApi.CapturingLogger logger
    ) {
        return new MCEEWVelocity(
                TestVelocityApi.proxyServer(scheduler), logger.proxy(), data, metrics,
                (config, delayScheduler, platformLogger) -> new VelocityMceewRuntime(
                        config, delayScheduler, connector, platformLogger));
    }

    private static void assertReload(
            MCEEWVelocity plugin,
            TestVelocityApi.RecordingScheduler scheduler,
            Path data,
            boolean enabled,
            MCEEWVelocity.ReloadOutcome expected
    ) throws IOException {
        writeConfig(data, enabled);
        List<MCEEWVelocity.ReloadOutcome> outcomes = new ArrayList<>();
        plugin.requestReload(outcomes::add);
        scheduler.runAll();
        assertEquals(List.of(expected), outcomes);
    }

    private static void writeConfig(Path data, boolean enabled) throws IOException {
        Files.createDirectories(data);
        Files.writeString(data.resolve("config.yml"),
                "platform_config_version: 1\n"
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

    private static final class RecordingMetricsCreator implements MCEEWVelocity.MetricsCreator {
        private final List<Integer> pluginIds = new ArrayList<>();
        private final List<Object> plugins = new ArrayList<>();
        private final RuntimeException failure;
        private int calls;

        private RecordingMetricsCreator() {
            this(null);
        }

        private RecordingMetricsCreator(RuntimeException failure) {
            this.failure = failure;
        }

        @Override
        public Metrics create(Object plugin, int pluginId) {
            calls++;
            plugins.add(plugin);
            pluginIds.add(pluginId);
            if (failure != null) {
                throw failure;
            }
            return null;
        }
    }
}
