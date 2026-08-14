package jp.wolfx.mceew.bungeecord;

import java.nio.file.Path;
import java.util.logging.Level;
import net.md_5.bungee.api.plugin.Plugin;

public final class MCEEWBungeeCord extends Plugin {
    private BungeePluginShell shell;
    private BungeeCommand command;

    @Override
    public void onEnable() {
        Path dataDirectory = getDataFolder().toPath();
        BungeeDelayScheduler scheduler = new BungeeDelayScheduler(
                getProxy().getScheduler(), this);
        BungeePluginShell newShell = new BungeePluginShell(
                new BungeeConfigLoader(dataDirectory),
                scheduler,
                getLogger(),
                BungeeMceewRuntime::production);
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
            getProxy().getPluginManager().unregisterCommand(registeredCommand);
        }

        BungeePluginShell activeShell = shell;
        shell = null;
        if (activeShell != null) {
            activeShell.close();
        }
        getLogger().info("MCEEW BungeeCord platform shell shut down.");
    }
}
