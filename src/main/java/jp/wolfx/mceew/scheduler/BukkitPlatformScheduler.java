package jp.wolfx.mceew.scheduler;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.function.Consumer;

final class BukkitPlatformScheduler implements PlatformScheduler {
    private final JavaPlugin plugin;

    BukkitPlatformScheduler(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean isFolia() {
        return false;
    }

    @Override
    public void runAsync(Runnable task) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
    }

    @Override
    public void runGlobal(Runnable task) {
        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    @Override
    public void forEachPlayer(Consumer<Player> action) {
        runGlobal(() -> Bukkit.getOnlinePlayers().forEach(action));
    }

    @Override
    public void cancelTasks() {
        Bukkit.getScheduler().cancelTasks(plugin);
    }
}
