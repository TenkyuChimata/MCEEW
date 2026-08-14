package jp.wolfx.mceew.bungeecord;

import java.nio.file.Path;
import java.util.logging.Level;
import net.md_5.bungee.api.plugin.Plugin;

public final class MCEEWBungeeCord extends Plugin {
    private BungeePluginShell shell;
    private BungeeCommand command;

    @Override
    public void onEnable() {
        if (shell != null || command != null) {
            getLogger().warning("MCEEW BungeeCord ignored a duplicate enable request.");
            return;
        }
        Path dataDirectory = getDataFolder().toPath();
        BungeeDelayScheduler scheduler = new BungeeDelayScheduler(
                getProxy().getScheduler(), this);
        BungeeNotificationPlatform notificationPlatform =
                new BungeeProxyNotificationPlatform(getProxy());
        BungeePluginShell newShell = new BungeePluginShell(
                new BungeeConfigLoader(dataDirectory),
                scheduler,
                getLogger(),
                (config, runtimeScheduler, logger) -> BungeeMceewRuntime.production(
                        config, runtimeScheduler, logger, notificationPlatform));
        BungeeCommand newCommand = new BungeeCommand(
                newShell, getDescription().getVersion(), getLogger());

        try {
            getProxy().getPluginManager().registerCommand(this, newCommand);
        } catch (RuntimeException error) {
            scheduler.close();
            getLogger().log(Level.SEVERE,
                    "MCEEW BungeeCord command registration failed; plugin shell remains inactive.",
                    error);
            return;
        }

        shell = newShell;
        command = newCommand;
        newShell.initialize();
        getLogger().info("MCEEW BungeeCord " + getDescription().getVersion()
                + " platform shell initialized.");
    }

    @Override
    public void onDisable() {
        BungeeCommand registeredCommand = command;
        command = null;
        if (registeredCommand != null) {
            try {
                getProxy().getPluginManager().unregisterCommand(registeredCommand);
            } catch (RuntimeException error) {
                getLogger().log(Level.WARNING,
                        "MCEEW BungeeCord command unregister failed during shutdown.", error);
            }
        }

        BungeePluginShell activeShell = shell;
        shell = null;
        if (activeShell != null) {
            try {
                activeShell.close();
            } catch (RuntimeException error) {
                getLogger().log(Level.WARNING,
                        "MCEEW BungeeCord shell cleanup failed during shutdown.", error);
            }
        }
        getLogger().info("MCEEW BungeeCord platform shell shut down.");
    }
}
