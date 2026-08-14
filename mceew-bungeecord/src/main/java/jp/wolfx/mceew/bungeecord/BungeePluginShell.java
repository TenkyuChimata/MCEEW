package jp.wolfx.mceew.bungeecord;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

final class BungeePluginShell implements BungeeCommandService, AutoCloseable {
    enum ReloadOutcome {
        SUCCESS,
        IN_PROGRESS,
        FAILED,
        UNAVAILABLE
    }

    enum State {
        UNINITIALIZED,
        ACTIVE,
        FAILED,
        SHUTDOWN
    }

    private final Object lifecycleLock = new Object();
    private final BungeeConfigLoader configLoader;
    private final BungeeDelayScheduler scheduler;
    private final Logger logger;

    private State state = State.UNINITIALIZED;
    private boolean reloadInProgress;
    private long lifecycleGeneration;
    private volatile BungeeConfigSnapshot configSnapshot;

    BungeePluginShell(
            BungeeConfigLoader configLoader,
            BungeeDelayScheduler scheduler,
            Logger logger
    ) {
        this.configLoader = Objects.requireNonNull(configLoader, "configLoader");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    void initialize() {
        synchronized (lifecycleLock) {
            if (state != State.UNINITIALIZED) {
                return;
            }
        }

        BungeeConfigSnapshot loaded;
        try {
            loaded = configLoader.loadSnapshot();
        } catch (BungeeConfigException error) {
            synchronized (lifecycleLock) {
                if (state == State.UNINITIALIZED) {
                    state = State.FAILED;
                }
            }
            logger.log(Level.SEVERE,
                    "MCEEW BungeeCord configuration could not be loaded; runtime remains inactive.",
                    error);
            return;
        }

        synchronized (lifecycleLock) {
            if (state != State.UNINITIALIZED) {
                return;
            }
            configSnapshot = loaded;
            state = State.ACTIVE;
        }
        logRuntimeState(loaded);
    }

    @Override
    public void requestReload(Consumer<ReloadOutcome> completion) {
        Objects.requireNonNull(completion, "completion");
        ReloadOutcome immediate = null;
        long generation = 0;
        synchronized (lifecycleLock) {
            if (state == State.SHUTDOWN || state == State.UNINITIALIZED) {
                immediate = ReloadOutcome.UNAVAILABLE;
            } else if (reloadInProgress) {
                immediate = ReloadOutcome.IN_PROGRESS;
            } else {
                reloadInProgress = true;
                generation = lifecycleGeneration;
            }
        }
        if (immediate != null) {
            completion.accept(immediate);
            return;
        }

        final long expectedGeneration = generation;
        try {
            scheduler.executeAsync(() -> performReload(expectedGeneration, completion));
        } catch (RuntimeException error) {
            logger.log(Level.SEVERE, "MCEEW BungeeCord reload could not be scheduled.", error);
            finishReload(expectedGeneration, ReloadOutcome.FAILED, completion);
        }
    }

    private void performReload(long expectedGeneration, Consumer<ReloadOutcome> completion) {
        BungeeConfigSnapshot loaded;
        try {
            loaded = configLoader.loadSnapshot();
        } catch (BungeeConfigException error) {
            logger.log(Level.SEVERE,
                    "MCEEW BungeeCord configuration reload failed; the active state was preserved.",
                    error);
            finishReload(expectedGeneration, ReloadOutcome.FAILED, completion);
            return;
        }

        boolean committed = false;
        synchronized (lifecycleLock) {
            if (state != State.SHUTDOWN && lifecycleGeneration == expectedGeneration) {
                configSnapshot = loaded;
                state = State.ACTIVE;
                reloadInProgress = false;
                committed = true;
            }
        }
        if (!committed) {
            completion.accept(ReloadOutcome.UNAVAILABLE);
            return;
        }
        logRuntimeState(loaded);
        completion.accept(ReloadOutcome.SUCCESS);
    }

    private void finishReload(
            long expectedGeneration,
            ReloadOutcome outcome,
            Consumer<ReloadOutcome> completion
    ) {
        ReloadOutcome effective = outcome;
        synchronized (lifecycleLock) {
            if (state == State.SHUTDOWN || lifecycleGeneration != expectedGeneration) {
                effective = ReloadOutcome.UNAVAILABLE;
            } else {
                reloadInProgress = false;
                if (configSnapshot == null) {
                    state = State.FAILED;
                }
            }
        }
        completion.accept(effective);
    }

    private void logRuntimeState(BungeeConfigSnapshot loaded) {
        if (loaded.runtimeEnabled()) {
            logger.info("MCEEW BungeeCord configuration requests the operational runtime; "
                    + "the runtime foundation is currently inactive.");
        } else {
            logger.info("MCEEW BungeeCord operational runtime is disabled by configuration.");
        }
    }

    @Override
    public String latestJmaEarthquakeInformation() {
        return null;
    }

    @Override
    public String latestCencEarthquakeInformation() {
        return null;
    }

    @Override
    public boolean dispatchTest(String sourceKey) {
        Objects.requireNonNull(sourceKey, "sourceKey");
        return false;
    }

    @Override
    public void close() {
        synchronized (lifecycleLock) {
            if (state == State.SHUTDOWN) {
                return;
            }
            state = State.SHUTDOWN;
            lifecycleGeneration++;
            reloadInProgress = false;
            configSnapshot = null;
        }
        scheduler.close();
    }

    State state() {
        synchronized (lifecycleLock) {
            return state;
        }
    }

    BungeeConfigSnapshot configSnapshot() {
        return configSnapshot;
    }

    boolean reloadInProgress() {
        synchronized (lifecycleLock) {
            return reloadInProgress;
        }
    }
}
