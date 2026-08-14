package jp.wolfx.mceew.bungeecord;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

final class BungeePluginShell implements BungeeCommandService, AutoCloseable {
    @FunctionalInterface
    interface RuntimeFactory {
        BungeeMceewRuntime create(
                BungeeConfigSnapshot config,
                BungeeDelayScheduler scheduler,
                Logger logger);
    }

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
    private final RuntimeFactory runtimeFactory;

    private State state = State.UNINITIALIZED;
    private boolean reloadInProgress;
    private long lifecycleGeneration;
    private volatile BungeeConfigSnapshot configSnapshot;
    private volatile BungeeMceewRuntime operationalRuntime;

    BungeePluginShell(
            BungeeConfigLoader configLoader,
            BungeeDelayScheduler scheduler,
            Logger logger,
            RuntimeFactory runtimeFactory
    ) {
        this.configLoader = Objects.requireNonNull(configLoader, "configLoader");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.runtimeFactory = Objects.requireNonNull(runtimeFactory, "runtimeFactory");
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

        BungeeMceewRuntime startedRuntime = null;
        if (loaded.runtimeEnabled()) {
            try {
                startedRuntime = runtimeFactory.create(loaded, scheduler, logger);
                startedRuntime.start();
            } catch (RuntimeException | Error error) {
                if (startedRuntime != null) {
                    startedRuntime.close();
                }
                synchronized (lifecycleLock) {
                    if (state == State.UNINITIALIZED) {
                        state = State.FAILED;
                    }
                }
                logger.log(Level.SEVERE,
                        "MCEEW BungeeCord operational runtime could not be started; "
                                + "runtime remains inactive.",
                        error);
                return;
            }
        }

        boolean activated = false;
        synchronized (lifecycleLock) {
            if (state != State.UNINITIALIZED) {
                activated = false;
            } else {
                configSnapshot = loaded;
                operationalRuntime = startedRuntime;
                state = State.ACTIVE;
                activated = true;
            }
        }
        if (!activated && startedRuntime != null) {
            startedRuntime.close();
        }
        if (activated) {
            logRuntimeState(loaded);
        }
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

        BungeeConfigSnapshot previousConfig;
        BungeeMceewRuntime previousRuntime;
        synchronized (lifecycleLock) {
            if (state == State.SHUTDOWN || lifecycleGeneration != expectedGeneration) {
                reloadInProgress = false;
                return;
            }
            previousConfig = configSnapshot;
            previousRuntime = operationalRuntime;
        }

        BungeeMceewRuntime.PreparedConfiguration preparedConfiguration = null;
        BungeeMceewRuntime preparedRuntime = null;
        try {
            if (loaded.runtimeEnabled()) {
                if (previousRuntime != null) {
                    preparedConfiguration = previousRuntime.prepareConfiguration(loaded);
                } else {
                    preparedRuntime = runtimeFactory.create(loaded, scheduler, logger);
                }
            }
        } catch (RuntimeException | Error error) {
            closePrepared(preparedRuntime);
            logger.log(Level.SEVERE,
                    "MCEEW BungeeCord reload preparation failed; "
                            + "the active state was preserved.",
                    error);
            finishReload(expectedGeneration, ReloadOutcome.FAILED, completion);
            return;
        }

        BungeeMceewRuntime runtimeToClose = null;
        boolean committed = false;
        try {
            synchronized (lifecycleLock) {
                if (state != State.SHUTDOWN
                        && lifecycleGeneration == expectedGeneration
                        && configSnapshot == previousConfig
                        && operationalRuntime == previousRuntime) {
                    if (previousRuntime != null && loaded.runtimeEnabled()) {
                        previousRuntime.commitConfiguration(preparedConfiguration);
                        preparedConfiguration = null;
                    } else if (previousRuntime != null) {
                        operationalRuntime = null;
                        runtimeToClose = previousRuntime;
                    } else if (loaded.runtimeEnabled()) {
                        preparedRuntime.start();
                        operationalRuntime = preparedRuntime;
                        preparedRuntime = null;
                    }
                    configSnapshot = loaded;
                    state = State.ACTIVE;
                    committed = true;
                }
            }
        } catch (RuntimeException | Error error) {
            logger.log(Level.SEVERE,
                    "MCEEW BungeeCord reload commit failed; "
                            + "the previous state remains active.",
                    error);
        }

        closePrepared(preparedRuntime);
        if (runtimeToClose != null) {
            runtimeToClose.close();
        }
        if (committed) {
            logRuntimeState(loaded);
        }
        finishReload(
                expectedGeneration,
                committed ? ReloadOutcome.SUCCESS : ReloadOutcome.FAILED,
                completion);
    }

    private static void closePrepared(BungeeMceewRuntime preparedRuntime) {
        if (preparedRuntime != null) {
            preparedRuntime.close();
        }
    }

    private void finishReload(
            long expectedGeneration,
            ReloadOutcome outcome,
            Consumer<ReloadOutcome> completion
    ) {
        boolean notify;
        synchronized (lifecycleLock) {
            if (state == State.SHUTDOWN || lifecycleGeneration != expectedGeneration) {
                notify = false;
            } else {
                notify = true;
                reloadInProgress = false;
                if (configSnapshot == null) {
                    state = State.FAILED;
                }
            }
        }
        if (notify) {
            completion.accept(outcome);
        }
    }

    private void logRuntimeState(BungeeConfigSnapshot loaded) {
        if (loaded.runtimeEnabled()) {
            logger.info("MCEEW BungeeCord operational runtime is active.");
        } else {
            logger.info("MCEEW BungeeCord operational runtime is disabled by configuration.");
        }
    }

    @Override
    public String latestJmaEarthquakeInformation() {
        BungeeMceewRuntime runtime = operationalRuntime;
        return runtime == null ? null : runtime.latestJmaEarthquakeInformation();
    }

    @Override
    public String latestCencEarthquakeInformation() {
        BungeeMceewRuntime runtime = operationalRuntime;
        return runtime == null ? null : runtime.latestCencEarthquakeInformation();
    }

    @Override
    public boolean dispatchTest(String sourceKey) {
        Objects.requireNonNull(sourceKey, "sourceKey");
        return false;
    }

    @Override
    public void close() {
        BungeeMceewRuntime runtime;
        synchronized (lifecycleLock) {
            if (state == State.SHUTDOWN) {
                return;
            }
            state = State.SHUTDOWN;
            lifecycleGeneration++;
            reloadInProgress = false;
            configSnapshot = null;
            runtime = operationalRuntime;
            operationalRuntime = null;
        }
        if (runtime != null) {
            runtime.close();
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

    boolean hasOperationalRuntime() {
        BungeeMceewRuntime runtime = operationalRuntime;
        return runtime != null && runtime.isActive();
    }

    Object operationalRuntimeIdentity() {
        return operationalRuntime;
    }
}
