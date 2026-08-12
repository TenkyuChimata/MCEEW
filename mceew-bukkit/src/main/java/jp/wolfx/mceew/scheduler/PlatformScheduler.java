package jp.wolfx.mceew.scheduler;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.TimeUnit;
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

    TaskHandle runAsyncDelayed(Runnable task, long delay, TimeUnit unit);

    void runGlobal(Runnable task);

    void forEachPlayer(Consumer<Player> action);

    void cancelTasks();

    interface TaskHandle {
        void cancel();
    }
}
