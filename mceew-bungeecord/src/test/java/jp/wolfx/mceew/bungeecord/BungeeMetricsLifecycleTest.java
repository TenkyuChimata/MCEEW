package jp.wolfx.mceew.bungeecord;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;

class BungeeMetricsLifecycleTest {
    private static final Logger LOGGER = quietLogger();

    @Test
    void initializesExactlyOnceWithTheDedicatedBungeeProjectId() {
        List<Integer> pluginIds = new ArrayList<>();
        AtomicInteger shutdowns = new AtomicInteger();
        BungeeMetricsLifecycle lifecycle = new BungeeMetricsLifecycle(LOGGER, pluginId -> {
            pluginIds.add(pluginId);
            return shutdowns::incrementAndGet;
        });

        lifecycle.initialize();
        lifecycle.initialize();

        assertTrue(lifecycle.isInitialized());
        assertEquals(List.of(33371), pluginIds);
        assertFalse(pluginIds.contains(17261), "Bukkit's project ID must not be reused");
        assertFalse(pluginIds.contains(33363), "Velocity's project ID must not be reused");
        lifecycle.close();
        lifecycle.close();
        assertEquals(1, shutdowns.get());
    }

    @Test
    void runtimeAndConfigurationTransitionsNeverRecreatePlatformMetrics() {
        AtomicInteger creations = new AtomicInteger();
        AtomicInteger shutdowns = new AtomicInteger();
        BungeeMetricsLifecycle lifecycle = new BungeeMetricsLifecycle(LOGGER, pluginId -> {
            creations.incrementAndGet();
            return shutdowns::incrementAndGet;
        });
        lifecycle.initialize();

        List<String> transitions = List.of(
                "enabled-to-enabled",
                "enabled-to-disabled",
                "disabled-to-enabled",
                "disabled-to-disabled",
                "invalid-reload",
                "failed-runtime-start",
                "startup-recovery");
        transitions.forEach(ignored -> lifecycle.initialize());

        assertEquals(1, creations.get());
        assertEquals(0, shutdowns.get());
        lifecycle.close();
        assertEquals(1, shutdowns.get());
    }

    @Test
    void creationFailureIsNonFatalAndIsNotRetriedDuringReloads() {
        AtomicInteger attempts = new AtomicInteger();
        BungeeMetricsLifecycle lifecycle = new BungeeMetricsLifecycle(LOGGER, pluginId -> {
            attempts.incrementAndGet();
            throw new IllegalStateException("simulated telemetry failure");
        });

        assertDoesNotThrow(lifecycle::initialize);
        assertDoesNotThrow(lifecycle::initialize);
        assertFalse(lifecycle.isInitialized());
        assertEquals(1, attempts.get());
        assertDoesNotThrow(lifecycle::close);
    }

    @Test
    void metricsShutdownFailureIsContainedAndCloseRemainsIdempotent() {
        AtomicInteger shutdowns = new AtomicInteger();
        BungeeMetricsLifecycle lifecycle = new BungeeMetricsLifecycle(LOGGER, pluginId -> () -> {
            shutdowns.incrementAndGet();
            throw new IllegalStateException("simulated telemetry shutdown failure");
        });
        lifecycle.initialize();

        assertDoesNotThrow(lifecycle::close);
        assertDoesNotThrow(lifecycle::close);
        assertEquals(1, shutdowns.get());
    }

    @Test
    void closeBeforeInitializationPreventsLaterConstruction() {
        AtomicInteger attempts = new AtomicInteger();
        BungeeMetricsLifecycle lifecycle = new BungeeMetricsLifecycle(LOGGER, pluginId -> {
            attempts.incrementAndGet();
            return () -> { };
        });

        lifecycle.close();
        lifecycle.initialize();

        assertEquals(0, attempts.get());
        assertFalse(lifecycle.isInitialized());
    }

    @Test
    void shutdownDuringConstructionClosesTheAbandonedMetricsHandle() throws Exception {
        CountDownLatch creatorEntered = new CountDownLatch(1);
        CountDownLatch releaseCreator = new CountDownLatch(1);
        AtomicInteger shutdowns = new AtomicInteger();
        BungeeMetricsLifecycle lifecycle = new BungeeMetricsLifecycle(LOGGER, pluginId -> {
            creatorEntered.countDown();
            try {
                if (!releaseCreator.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("test timed out waiting to release metrics creation");
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("metrics creation test was interrupted", ex);
            }
            return shutdowns::incrementAndGet;
        });
        Thread initializer = new Thread(lifecycle::initialize, "bungee-metrics-test-initializer");
        initializer.start();
        assertTrue(creatorEntered.await(5, TimeUnit.SECONDS));

        lifecycle.close();
        releaseCreator.countDown();
        initializer.join(5_000L);

        assertFalse(initializer.isAlive());
        assertFalse(lifecycle.isInitialized());
        assertEquals(1, shutdowns.get());
    }

    @Test
    void productionWiringInitializesMetricsBeforeConfigAndKeepsReloadShellIndependent()
            throws Exception {
        Path root = Path.of(System.getProperty("mceew.reactor.root"));
        String plugin = Files.readString(root.resolve(
                "mceew-bungeecord/src/main/java/jp/wolfx/mceew/bungeecord/MCEEWBungeeCord.java"));
        String shell = Files.readString(root.resolve(
                "mceew-bungeecord/src/main/java/jp/wolfx/mceew/bungeecord/BungeePluginShell.java"));

        assertTrue(plugin.indexOf("newMetricsLifecycle.initialize()")
                < plugin.indexOf("newShell.initialize()"));
        assertEquals(1, occurrences(plugin, "new BungeeMetricsLifecycle("));
        assertFalse(shell.contains("BungeeMetricsLifecycle"));
        assertFalse(shell.contains("org.bstats"));
    }

    private int occurrences(String value, String token) {
        int count = 0;
        int index = 0;
        while ((index = value.indexOf(token, index)) >= 0) {
            count++;
            index += token.length();
        }
        return count;
    }

    private static Logger quietLogger() {
        Logger logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        return logger;
    }
}
