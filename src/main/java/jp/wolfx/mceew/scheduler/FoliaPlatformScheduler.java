package jp.wolfx.mceew.scheduler;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.function.Consumer;

/**
 * Keeps all Folia linkage behind reflection so the plugin can be loaded by Spigot.
 * The reflected signatures are part of Folia's scheduler API from 1.19.4 onward.
 */
final class FoliaPlatformScheduler implements PlatformScheduler {
    private final JavaPlugin plugin;
    private final Object asyncScheduler;
    private final Object globalScheduler;
    private final Method asyncRunNow;
    private final Method asyncCancelTasks;
    private final Method globalExecute;
    private final Method globalCancelTasks;
    private final Method entityGetScheduler;
    private final Method entityRun;

    FoliaPlatformScheduler(JavaPlugin plugin) {
        this.plugin = plugin;
        try {
            ClassLoader classLoader = plugin.getClass().getClassLoader();
            Class<?> asyncSchedulerType = Class.forName(
                    "io.papermc.paper.threadedregions.scheduler.AsyncScheduler", false, classLoader);
            Class<?> globalSchedulerType = Class.forName(
                    "io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler", false, classLoader);
            Class<?> entitySchedulerType = Class.forName(
                    "io.papermc.paper.threadedregions.scheduler.EntityScheduler", false, classLoader);
            Class<?> serverType = Class.forName("org.bukkit.Server", false, classLoader);
            Class<?> entityType = Class.forName("org.bukkit.entity.Entity", false, classLoader);

            Method getAsyncScheduler = serverType.getMethod("getAsyncScheduler");
            Method getGlobalScheduler = serverType.getMethod("getGlobalRegionScheduler");
            asyncScheduler = getAsyncScheduler.invoke(Bukkit.getServer());
            globalScheduler = getGlobalScheduler.invoke(Bukkit.getServer());

            asyncRunNow = asyncSchedulerType.getMethod("runNow", Plugin.class, Consumer.class);
            asyncCancelTasks = asyncSchedulerType.getMethod("cancelTasks", Plugin.class);
            globalExecute = globalSchedulerType.getMethod("execute", Plugin.class, Runnable.class);
            globalCancelTasks = globalSchedulerType.getMethod("cancelTasks", Plugin.class);
            entityGetScheduler = entityType.getMethod("getScheduler");
            entityRun = entitySchedulerType.getMethod(
                    "run", Plugin.class, Consumer.class, Runnable.class);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to initialize the Folia scheduler adapter", exception);
        }
    }

    @Override
    public boolean isFolia() {
        return true;
    }

    @Override
    public void runAsync(Runnable task) {
        invoke(asyncRunNow, asyncScheduler, plugin, taskConsumer(task));
    }

    @Override
    public void runGlobal(Runnable task) {
        invoke(globalExecute, globalScheduler, plugin, task);
    }

    @Override
    public void forEachPlayer(Consumer<Player> action) {
        runGlobal(() -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                Object entityScheduler = invoke(entityGetScheduler, player);
                Consumer<Object> entityTask = ignored -> {
                    if (plugin.isEnabled() && player.isOnline()) {
                        action.accept(player);
                    }
                };
                invoke(entityRun, entityScheduler, plugin, entityTask, (Runnable) () -> {
                });
            }
        });
    }

    @Override
    public void cancelTasks() {
        invoke(asyncCancelTasks, asyncScheduler, plugin);
        invoke(globalCancelTasks, globalScheduler, plugin);
    }

    private Consumer<Object> taskConsumer(Runnable task) {
        return ignored -> {
            if (plugin.isEnabled()) {
                task.run();
            }
        };
    }

    @SuppressWarnings("unchecked")
    private <T> T invoke(Method method, Object target, Object... arguments) {
        try {
            return (T) method.invoke(target, arguments);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Cannot access Folia scheduler method " + method.getName(), exception);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw new IllegalStateException("Folia scheduler method failed: " + method.getName(), cause);
        }
    }
}
