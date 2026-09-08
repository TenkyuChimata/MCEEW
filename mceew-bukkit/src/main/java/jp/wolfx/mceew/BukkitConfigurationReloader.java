package jp.wolfx.mceew;

import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Coordinates the existing prepare, Bukkit reload, and runtime apply sequence. */
final class BukkitConfigurationReloader {
    @FunctionalInterface
    interface Preparation {
        void prepare() throws ConfigManager.ConfigPreparationException;
    }

    private final Preparation preparation;
    private final Runnable reloadConfig;
    private final Runnable applyRuntimeConfiguration;
    private final Logger logger;

    BukkitConfigurationReloader(
            Preparation preparation,
            Runnable reloadConfig,
            Runnable applyRuntimeConfiguration,
            Logger logger
    ) {
        this.preparation = Objects.requireNonNull(preparation, "preparation");
        this.reloadConfig = Objects.requireNonNull(reloadConfig, "reloadConfig");
        this.applyRuntimeConfiguration = Objects.requireNonNull(
                applyRuntimeConfiguration, "applyRuntimeConfiguration");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    synchronized boolean prepareAndApply() {
        try {
            preparation.prepare();
        } catch (ConfigManager.ConfigPreparationException error) {
            return false;
        }
        try {
            reloadConfig.run();
            applyRuntimeConfiguration.run();
            return true;
        } catch (RuntimeException error) {
            logger.log(Level.SEVERE,
                    "Configuration was prepared but could not be loaded into the runtime.",
                    error);
            return false;
        }
    }
}
