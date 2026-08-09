package jp.wolfx.mceew.scheduler;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.function.Consumer;

public interface PlatformScheduler {
    static PlatformScheduler create(JavaPlugin plugin) {
        try {
            Class.forName(
                    "io.papermc.paper.threadedregions.RegionizedServer",
                    false,
                    plugin.getClass().getClassLoader()
            );
            return new FoliaPlatformScheduler(plugin);
        } catch (ClassNotFoundException ignored) {
            return new BukkitPlatformScheduler(plugin);
        }
    }

    boolean isFolia();

    void runAsync(Runnable task);

    void runGlobal(Runnable task);

    void forEachPlayer(Consumer<Player> action);

    void cancelTasks();
}
