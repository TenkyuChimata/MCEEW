package jp.wolfx.mceew.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import java.nio.file.Path;
import java.util.Objects;
import jp.wolfx.mceew.velocity.generated.VelocityBuildInfo;
import org.slf4j.Logger;

@Plugin(
        id = "mceew",
        name = "MCEEW",
        version = VelocityBuildInfo.VERSION,
        description = "Minecraft Earthquake Early Warning",
        authors = {"TenkyuChimata"})
public final class MCEEWVelocity {
    @FunctionalInterface
    interface RuntimeFactory {
        VelocityMceewRuntime create(
                VelocityConfigSnapshot config,
                VelocityDelayScheduler delayScheduler,
                Logger logger);
    }

    private final Object lifecycleLock = new Object();
    private final Logger logger;
    private final VelocityConfigLoader configLoader;
    private final VelocityDelayScheduler delayScheduler;
    private final RuntimeFactory runtimeFactory;

    private LifecycleState lifecycleState = LifecycleState.UNINITIALIZED;
    private volatile VelocityConfigSnapshot configSnapshot;
    private volatile VelocityMceewRuntime operationalRuntime;

    @Inject
    public MCEEWVelocity(
            ProxyServer proxyServer,
            Logger logger,
            @DataDirectory Path dataDirectory) {
        this(proxyServer, logger, dataDirectory,
                (config, scheduler, platformLogger) -> VelocityMceewRuntime.production(
                        config, scheduler, platformLogger, proxyServer));
    }

    MCEEWVelocity(
            ProxyServer proxyServer,
            Logger logger,
            Path dataDirectory,
            RuntimeFactory runtimeFactory
    ) {
        Objects.requireNonNull(proxyServer, "proxyServer");
        this.logger = Objects.requireNonNull(logger, "logger");
        Objects.requireNonNull(dataDirectory, "dataDirectory");
        this.configLoader = new VelocityConfigLoader(dataDirectory);
        this.delayScheduler = new VelocityDelayScheduler(proxyServer, this);
        this.runtimeFactory = Objects.requireNonNull(runtimeFactory, "runtimeFactory");
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

        VelocityConfigSnapshot loaded;
        try {
            loaded = configLoader.load();
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
        synchronized (lifecycleLock) {
            if (lifecycleState == LifecycleState.SHUTDOWN) {
                return;
            }
            lifecycleState = LifecycleState.SHUTDOWN;
            configSnapshot = null;
            runtime = operationalRuntime;
            operationalRuntime = null;
        }
        if (runtime != null) {
            runtime.close();
        }
        delayScheduler.close();
        logger.info("MCEEW Velocity platform shell shut down.");
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
