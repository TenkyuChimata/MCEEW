package jp.wolfx.mceew.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import jp.wolfx.mceew.velocity.generated.VelocityBuildInfo;
import org.bstats.velocity.Metrics;
import org.slf4j.Logger;

@Plugin(
        id = "mceew",
        name = "MCEEW",
        version = VelocityBuildInfo.VERSION,
        description = "Minecraft Earthquake Early Warning",
        authors = {"TenkyuChimata"})
public final class MCEEWVelocity {
    private static final int BSTATS_PLUGIN_ID = 33363;

    @FunctionalInterface
    interface MetricsCreator {
        Metrics create(Object plugin, int pluginId);
    }

    @FunctionalInterface
    interface RuntimeFactory {
        VelocityMceewRuntime create(
                VelocityConfigSnapshot config,
                VelocityDelayScheduler delayScheduler,
                Logger logger);
    }

    enum ReloadOutcome {
        SUCCESS,
        IN_PROGRESS,
        FAILED,
        UNAVAILABLE
    }

    private final Object lifecycleLock = new Object();
    private final ProxyServer proxyServer;
    private final Logger logger;
    private final VelocityConfigLoader configLoader;
    private final VelocityDelayScheduler delayScheduler;
    private final MetricsCreator metricsCreator;
    private final RuntimeFactory runtimeFactory;
    private final VelocityCommand command;

    private LifecycleState lifecycleState = LifecycleState.UNINITIALIZED;
    private CommandMeta commandMeta;
    private Metrics metrics;
    private boolean reloadInProgress;
    private volatile VelocityConfigSnapshot configSnapshot;
    private volatile VelocityMceewRuntime operationalRuntime;

    @Inject
    public MCEEWVelocity(
            ProxyServer proxyServer,
            Logger logger,
            @DataDirectory Path dataDirectory,
            Metrics.Factory metricsFactory) {
        this(proxyServer, logger, dataDirectory, metricsCreator(metricsFactory),
                productionRuntimeFactory(proxyServer));
    }

    MCEEWVelocity(
            ProxyServer proxyServer,
            Logger logger,
            Path dataDirectory
    ) {
        this(proxyServer, logger, dataDirectory, (plugin, pluginId) -> null,
                productionRuntimeFactory(proxyServer));
    }

    MCEEWVelocity(
            ProxyServer proxyServer,
            Logger logger,
            Path dataDirectory,
            RuntimeFactory runtimeFactory
    ) {
        this(proxyServer, logger, dataDirectory, (plugin, pluginId) -> null, runtimeFactory);
    }

    MCEEWVelocity(
            ProxyServer proxyServer,
            Logger logger,
            Path dataDirectory,
            MetricsCreator metricsCreator,
            RuntimeFactory runtimeFactory
    ) {
        this.proxyServer = Objects.requireNonNull(proxyServer, "proxyServer");
        this.logger = Objects.requireNonNull(logger, "logger");
        Objects.requireNonNull(dataDirectory, "dataDirectory");
        this.configLoader = new VelocityConfigLoader(dataDirectory);
        this.delayScheduler = new VelocityDelayScheduler(proxyServer, this);
        this.metricsCreator = Objects.requireNonNull(metricsCreator, "metricsCreator");
        this.runtimeFactory = Objects.requireNonNull(runtimeFactory, "runtimeFactory");
        command = new VelocityCommand(this, logger);
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        Objects.requireNonNull(event, "event");
        synchronized (lifecycleLock) {
            if (lifecycleState != LifecycleState.UNINITIALIZED) {
                return;
            }
            lifecycleState = LifecycleState.INITIALIZING;
        }

        if (!registerCommand()) {
            synchronized (lifecycleLock) {
                if (lifecycleState == LifecycleState.INITIALIZING) {
                    lifecycleState = LifecycleState.FAILED;
                }
            }
            return;
        }

        initializeMetrics();

        VelocityConfigSnapshot loaded;
        try {
            loaded = configLoader.loadSnapshot();
        } catch (VelocityConfigException error) {
            synchronized (lifecycleLock) {
                if (lifecycleState == LifecycleState.INITIALIZING) {
                    lifecycleState = LifecycleState.FAILED;
                }
            }
            logger.error("MCEEW Velocity configuration could not be loaded; runtime remains inactive.", error);
            return;
        }

        VelocityMceewRuntime startedRuntime = null;
        if (loaded.runtimeEnabled()) {
            try {
                startedRuntime = runtimeFactory.create(loaded, delayScheduler, logger);
                startedRuntime.start();
            } catch (RuntimeException | Error error) {
                if (startedRuntime != null) {
                    startedRuntime.close();
                }
                synchronized (lifecycleLock) {
                    if (lifecycleState == LifecycleState.INITIALIZING) {
                        lifecycleState = LifecycleState.FAILED;
                    }
                }
                logger.error(
                        "MCEEW Velocity operational runtime could not be started; runtime remains inactive.",
                        error);
                return;
            }
        }

        boolean activated = false;
        synchronized (lifecycleLock) {
            if (lifecycleState == LifecycleState.INITIALIZING) {
                configSnapshot = loaded;
                operationalRuntime = startedRuntime;
                lifecycleState = LifecycleState.ACTIVE;
                activated = true;
            }
        }
        if (!activated && startedRuntime != null) {
            startedRuntime.close();
        }
        if (activated) {
            logger.info("MCEEW Velocity {} platform shell initialized.", VelocityBuildInfo.VERSION);
            if (!loaded.runtimeEnabled()) {
                logger.info("MCEEW Velocity operational runtime is disabled by configuration.");
            }
        }
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        Objects.requireNonNull(event, "event");
        VelocityMceewRuntime runtime;
        Metrics initializedMetrics;
        CommandMeta registeredCommand;
        synchronized (lifecycleLock) {
            if (lifecycleState == LifecycleState.SHUTDOWN) {
                return;
            }
            lifecycleState = LifecycleState.SHUTDOWN;
            reloadInProgress = false;
            configSnapshot = null;
            runtime = operationalRuntime;
            operationalRuntime = null;
            initializedMetrics = metrics;
            metrics = null;
            registeredCommand = commandMeta;
            commandMeta = null;
        }
        if (registeredCommand != null) {
            try {
                proxyServer.getCommandManager().unregister(registeredCommand);
            } catch (RuntimeException error) {
                logger.error("MCEEW Velocity command could not be unregistered cleanly.", error);
            }
        }
        shutdownMetrics(initializedMetrics);
        if (runtime != null) {
            runtime.close();
        }
        delayScheduler.close();
        logger.info("MCEEW Velocity platform shell shut down.");
    }

    private void initializeMetrics() {
        Metrics initializedMetrics;
        try {
            initializedMetrics = metricsCreator.create(this, BSTATS_PLUGIN_ID);
        } catch (RuntimeException error) {
            logger.warn(
                    "MCEEW Velocity bStats metrics could not be initialized; continuing without metrics.",
                    error);
            return;
        }
        if (initializedMetrics == null) {
            return;
        }

        boolean retained = false;
        synchronized (lifecycleLock) {
            if (lifecycleState == LifecycleState.INITIALIZING && metrics == null) {
                metrics = initializedMetrics;
                retained = true;
            }
        }
        if (!retained) {
            shutdownMetrics(initializedMetrics);
        }
    }

    private void shutdownMetrics(Metrics initializedMetrics) {
        if (initializedMetrics == null) {
            return;
        }
        try {
            initializedMetrics.shutdown();
        } catch (RuntimeException error) {
            logger.warn("MCEEW Velocity bStats metrics could not be shut down cleanly.", error);
        }
    }

    private static MetricsCreator metricsCreator(Metrics.Factory metricsFactory) {
        Objects.requireNonNull(metricsFactory, "metricsFactory");
        return metricsFactory::make;
    }

    private static RuntimeFactory productionRuntimeFactory(ProxyServer proxyServer) {
        return (config, scheduler, platformLogger) -> VelocityMceewRuntime.production(
                config, scheduler, platformLogger, proxyServer);
    }

    private boolean registerCommand() {
        CommandManager commandManager;
        CommandMeta registered;
        try {
            commandManager = Objects.requireNonNull(
                    proxyServer.getCommandManager(), "Velocity command manager");
            registered = commandManager.metaBuilder("eew")
                    .aliases("mceew")
                    .plugin(this)
                    .build();
            commandManager.register(registered, command);
        } catch (RuntimeException error) {
            logger.error(
                    "MCEEW Velocity command registration failed; operational runtime will not start.",
                    error);
            return false;
        }

        synchronized (lifecycleLock) {
            if (lifecycleState == LifecycleState.INITIALIZING) {
                commandMeta = registered;
                return true;
            }
        }
        commandManager.unregister(registered);
        return false;
    }

    void requestReload(Consumer<ReloadOutcome> completion) {
        Objects.requireNonNull(completion, "completion");
        ReloadOutcome immediate = null;
        synchronized (lifecycleLock) {
            if (lifecycleState == LifecycleState.SHUTDOWN
                    || lifecycleState == LifecycleState.UNINITIALIZED
                    || lifecycleState == LifecycleState.INITIALIZING) {
                immediate = ReloadOutcome.UNAVAILABLE;
            } else if (reloadInProgress) {
                immediate = ReloadOutcome.IN_PROGRESS;
            } else {
                reloadInProgress = true;
            }
        }
        if (immediate != null) {
            completion.accept(immediate);
            return;
        }

        try {
            delayScheduler.schedule(
                    () -> performReload(completion), 0L, TimeUnit.NANOSECONDS);
        } catch (RuntimeException error) {
            logger.error("MCEEW Velocity reload could not be scheduled.", error);
            finishReload(ReloadOutcome.FAILED, completion);
        }
    }

    private void performReload(Consumer<ReloadOutcome> completion) {
        VelocityConfigSnapshot loaded;
        try {
            loaded = configLoader.loadSnapshot();
        } catch (VelocityConfigException error) {
            logger.error(
                    "MCEEW Velocity configuration reload failed; the active state was preserved.",
                    error);
            finishReload(ReloadOutcome.FAILED, completion);
            return;
        }

        VelocityConfigSnapshot previousConfig;
        VelocityMceewRuntime previousRuntime;
        synchronized (lifecycleLock) {
            if (lifecycleState == LifecycleState.SHUTDOWN) {
                reloadInProgress = false;
                return;
            }
            previousConfig = configSnapshot;
            previousRuntime = operationalRuntime;
        }

        VelocityMceewRuntime.PreparedConfiguration preparedConfiguration = null;
        VelocityMceewRuntime preparedRuntime = null;
        try {
            if (loaded.runtimeEnabled()) {
                if (previousRuntime != null) {
                    preparedConfiguration = previousRuntime.prepareConfiguration(loaded);
                } else {
                    preparedRuntime = runtimeFactory.create(loaded, delayScheduler, logger);
                }
            }
        } catch (RuntimeException | Error error) {
            closePrepared(preparedConfiguration, preparedRuntime);
            logger.error(
                    "MCEEW Velocity reload preparation failed; the active state was preserved.",
                    error);
            finishReload(ReloadOutcome.FAILED, completion);
            return;
        }

        VelocityMceewRuntime runtimeToClose = null;
        boolean committed = false;
        try {
            synchronized (lifecycleLock) {
                if (lifecycleState != LifecycleState.SHUTDOWN
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
                    lifecycleState = LifecycleState.ACTIVE;
                    committed = true;
                }
            }
        } catch (RuntimeException | Error error) {
            logger.error(
                    "MCEEW Velocity reload commit failed; the previous state remains active.",
                    error);
        }

        closePrepared(preparedConfiguration, preparedRuntime);
        if (runtimeToClose != null) {
            runtimeToClose.close();
        }
        finishReload(
                committed ? ReloadOutcome.SUCCESS : ReloadOutcome.FAILED,
                completion);
    }

    private static void closePrepared(
            VelocityMceewRuntime.PreparedConfiguration preparedConfiguration,
            VelocityMceewRuntime preparedRuntime
    ) {
        if (preparedConfiguration != null) {
            preparedConfiguration.close();
        }
        if (preparedRuntime != null) {
            preparedRuntime.close();
        }
    }

    private void finishReload(ReloadOutcome outcome, Consumer<ReloadOutcome> completion) {
        boolean notify;
        synchronized (lifecycleLock) {
            notify = lifecycleState != LifecycleState.SHUTDOWN;
            reloadInProgress = false;
        }
        if (notify) {
            completion.accept(outcome);
        }
    }

    String latestJmaEarthquakeInformation() {
        VelocityMceewRuntime runtime;
        synchronized (lifecycleLock) {
            runtime = operationalRuntime;
        }
        return runtime == null ? null : runtime.latestJmaEarthquakeInformation();
    }

    String latestCencEarthquakeInformation() {
        VelocityMceewRuntime runtime;
        synchronized (lifecycleLock) {
            runtime = operationalRuntime;
        }
        return runtime == null ? null : runtime.latestCencEarthquakeInformation();
    }

    boolean dispatchTest(String sourceKey) {
        VelocityMceewRuntime runtime;
        synchronized (lifecycleLock) {
            runtime = operationalRuntime;
        }
        return runtime != null && runtime.dispatchTest(sourceKey);
    }

    boolean isOperational() {
        synchronized (lifecycleLock) {
            return lifecycleState == LifecycleState.ACTIVE;
        }
    }

    String lifecycleStateName() {
        synchronized (lifecycleLock) {
            return lifecycleState.name();
        }
    }

    int loadedPlatformConfigVersion() {
        VelocityConfigSnapshot snapshot = configSnapshot;
        return snapshot == null ? -1 : snapshot.platformConfigVersion();
    }

    boolean loadedRuntimeEnabled() {
        VelocityConfigSnapshot snapshot = configSnapshot;
        return snapshot != null && snapshot.runtimeEnabled();
    }

    boolean hasOperationalRuntime() {
        VelocityMceewRuntime runtime = operationalRuntime;
        return runtime != null && runtime.isActive();
    }

    Object operationalRuntimeIdentity() {
        return operationalRuntime;
    }

    boolean isCommandRegistered() {
        synchronized (lifecycleLock) {
            return commandMeta != null;
        }
    }

    boolean isReloadInProgress() {
        synchronized (lifecycleLock) {
            return reloadInProgress;
        }
    }

    VelocityDelayScheduler delayScheduler() {
        return delayScheduler;
    }

    private enum LifecycleState {
        UNINITIALIZED,
        INITIALIZING,
        ACTIVE,
        FAILED,
        SHUTDOWN
    }
}
