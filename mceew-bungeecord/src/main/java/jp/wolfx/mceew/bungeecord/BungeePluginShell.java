package jp.wolfx.mceew.bungeecord;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

final class BungeePluginShell implements BungeeCommandService, AutoCloseable {
    @FunctionalInterface
    interface ConfigSource {
        BungeeConfigSnapshot loadSnapshot() throws BungeeConfigException;
    }

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
        INVALID_CONFIG,
        RUNTIME_FAILED,
        FAILED,
        UNAVAILABLE
    }

    enum State {
        UNINITIALIZED,
        INITIALIZING,
        ACTIVE,
        FAILED,
        SHUTDOWN
    }

    private final Object lifecycleLock = new Object();
    private final ConfigSource configSource;
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
        this(Objects.requireNonNull(configLoader, "configLoader")::loadSnapshot,
                scheduler, logger, runtimeFactory);
    }

    BungeePluginShell(
            ConfigSource configSource,
            BungeeDelayScheduler scheduler,
            Logger logger,
            RuntimeFactory runtimeFactory
    ) {
        this.configSource = Objects.requireNonNull(configSource, "configSource");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.runtimeFactory = Objects.requireNonNull(runtimeFactory, "runtimeFactory");
    }

    void initialize() {
        synchronized (lifecycleLock) {
            if (state != State.UNINITIALIZED) {
                return;
            }
            state = State.INITIALIZING;
        }

        BungeeConfigSnapshot loaded;
        try {
            loaded = configSource.loadSnapshot();
        } catch (BungeeConfigException error) {
            synchronized (lifecycleLock) {
                if (state == State.INITIALIZING) {
                    state = State.FAILED;
                }
            }
            logger.log(Level.SEVERE,
                    "MCEEW BungeeCord configuration could not be loaded; runtime remains inactive.",
                    error);
            return;
        } catch (RuntimeException error) {
            synchronized (lifecycleLock) {
                if (state == State.INITIALIZING) {
                    state = State.FAILED;
                }
            }
            logger.log(Level.SEVERE,
                    "MCEEW BungeeCord configuration loading failed unexpectedly; "
                            + "runtime remains inactive.",
                    error);
            return;
        }

        synchronized (lifecycleLock) {
            if (state != State.INITIALIZING) {
                return;
            }
        }

        BungeeMceewRuntime startedRuntime = null;
        if (loaded.runtimeEnabled()) {
            try {
                startedRuntime = runtimeFactory.create(loaded, scheduler, logger);
                synchronized (lifecycleLock) {
                    if (state != State.INITIALIZING) {
                        closePrepared(startedRuntime, "abandoned startup runtime");
                        return;
                    }
                }
                startedRuntime.start();
            } catch (RuntimeException | Error error) {
                if (startedRuntime != null) {
                    closePrepared(startedRuntime, "failed startup runtime");
                }
                synchronized (lifecycleLock) {
                    if (state == State.INITIALIZING) {
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
            if (state != State.INITIALIZING) {
                activated = false;
            } else {
                configSnapshot = loaded;
                operationalRuntime = startedRuntime;
                state = State.ACTIVE;
                activated = true;
            }
        }
        if (!activated && startedRuntime != null) {
            closePrepared(startedRuntime, "abandoned startup runtime");
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
            if (state == State.SHUTDOWN
                    || state == State.UNINITIALIZED
                    || state == State.INITIALIZING) {
                immediate = ReloadOutcome.UNAVAILABLE;
            } else if (reloadInProgress) {
                immediate = ReloadOutcome.IN_PROGRESS;
            } else {
                reloadInProgress = true;
                generation = lifecycleGeneration;
            }
        }
        if (immediate != null) {
            notifyCompletion(completion, immediate);
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
            loaded = configSource.loadSnapshot();
        } catch (BungeeConfigException error) {
            logger.log(Level.SEVERE,
                    "MCEEW BungeeCord configuration reload failed; the active state was preserved.",
                    error);
            finishReload(expectedGeneration, ReloadOutcome.INVALID_CONFIG, completion);
            return;
        } catch (RuntimeException error) {
            logger.log(Level.SEVERE,
                    "MCEEW BungeeCord configuration reload failed unexpectedly; "
                            + "the active state was preserved.",
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
            closePrepared(preparedConfiguration, "prepared notification policy");
            closePrepared(preparedRuntime, "prepared operational runtime");
            logger.log(Level.SEVERE,
                    "MCEEW BungeeCord reload preparation failed; "
                            + "the active state was preserved.",
                    error);
            finishReload(expectedGeneration, ReloadOutcome.RUNTIME_FAILED, completion);
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

        closePrepared(preparedConfiguration, "abandoned notification policy");
        closePrepared(preparedRuntime, "abandoned operational runtime");
        if (runtimeToClose != null) {
            closePrepared(runtimeToClose, "disabled operational runtime");
        }
        if (committed) {
            logRuntimeState(loaded);
        }
        finishReload(
                expectedGeneration,
                committed ? ReloadOutcome.SUCCESS : ReloadOutcome.FAILED,
                completion);
    }

    private void closePrepared(BungeeMceewRuntime preparedRuntime, String description) {
        if (preparedRuntime != null) {
            try {
                preparedRuntime.close();
            } catch (RuntimeException error) {
                logger.log(Level.WARNING,
                        "MCEEW BungeeCord could not close " + description + ".", error);
            }
        }
    }

    private void closePrepared(
            BungeeMceewRuntime.PreparedConfiguration preparedConfiguration,
            String description
    ) {
        if (preparedConfiguration != null) {
            try {
                preparedConfiguration.close();
            } catch (RuntimeException error) {
                logger.log(Level.WARNING,
                        "MCEEW BungeeCord could not close " + description + ".", error);
            }
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
            notifyCompletion(completion, outcome);
        }
    }

    private void notifyCompletion(
            Consumer<ReloadOutcome> completion,
            ReloadOutcome outcome
    ) {
        try {
            completion.accept(outcome);
        } catch (RuntimeException error) {
            logger.log(Level.WARNING,
                    "MCEEW BungeeCord could not deliver the reload completion response.", error);
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
    public TestOutcome dispatchTest(String sourceKey) {
        Objects.requireNonNull(sourceKey, "sourceKey");
        BungeeMceewRuntime runtime;
        synchronized (lifecycleLock) {
            if (state == State.SHUTDOWN
                    || state == State.UNINITIALIZED
                    || state == State.INITIALIZING) {
                return TestOutcome.UNAVAILABLE;
            }
            if (reloadInProgress) {
                return TestOutcome.IN_PROGRESS;
            }
            runtime = operationalRuntime;
        }
        if (runtime == null) {
            return TestOutcome.UNAVAILABLE;
        }
        return runtime.dispatchTest(sourceKey)
                ? TestOutcome.DISPATCHED
                : TestOutcome.FAILED;
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
        try {
            if (runtime != null) {
                closePrepared(runtime, "operational runtime during shutdown");
            }
        } finally {
            scheduler.close();
        }
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
