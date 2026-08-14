package jp.wolfx.mceew.bungeecord;

import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

final class BungeeMetricsLifecycle implements AutoCloseable {
    static final int BSTATS_PLUGIN_ID = 33371;

    private final Logger logger;
    private final MetricsCreator creator;
    private boolean initializationAttempted;
    private boolean closed;
    private MetricsHandle metrics;

    BungeeMetricsLifecycle(Logger logger, MetricsCreator creator) {
        this.logger = Objects.requireNonNull(logger, "logger");
        this.creator = Objects.requireNonNull(creator, "creator");
    }

    void initialize() {
        synchronized (this) {
            if (initializationAttempted || closed) {
                return;
            }
            initializationAttempted = true;
        }

        MetricsHandle initializedMetrics;
        try {
            initializedMetrics = Objects.requireNonNull(
                    creator.create(BSTATS_PLUGIN_ID), "metrics creator returned null");
        } catch (RuntimeException ex) {
            logger.log(Level.WARNING,
                    "MCEEW BungeeCord bStats metrics could not be initialized; continuing without metrics.", ex);
            return;
        }

        synchronized (this) {
            if (!closed) {
                metrics = initializedMetrics;
                return;
            }
        }

        shutdown(initializedMetrics);
    }

    synchronized boolean isInitialized() {
        return metrics != null;
    }

    @Override
    public void close() {
        MetricsHandle activeMetrics;
        synchronized (this) {
            if (closed) {
                return;
            }
            closed = true;
            activeMetrics = metrics;
            metrics = null;
        }
        shutdown(activeMetrics);
    }

    private void shutdown(MetricsHandle handle) {
        if (handle == null) {
            return;
        }
        try {
            handle.shutdown();
        } catch (RuntimeException ex) {
            logger.log(Level.WARNING, "MCEEW BungeeCord bStats metrics did not shut down cleanly.", ex);
        }
    }

    @FunctionalInterface
    interface MetricsCreator {
        MetricsHandle create(int pluginId);
    }

    @FunctionalInterface
    interface MetricsHandle {
        void shutdown();
    }
}
