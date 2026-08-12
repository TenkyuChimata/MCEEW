package jp.wolfx.mceew.velocity;

import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.scheduler.ScheduledTask;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import jp.wolfx.mceew.websocket.WebSocketConnectionManager;

/** Velocity implementation of the core delayed-execution contract. */
public final class VelocityDelayScheduler
        implements WebSocketConnectionManager.DelayScheduler, AutoCloseable {
    private final ProxyServer proxyServer;
    private final Object pluginOwner;
    private final Set<OwnedAction> ownedActions = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean closed = new AtomicBoolean();

    public VelocityDelayScheduler(ProxyServer proxyServer, Object pluginOwner) {
        this.proxyServer = Objects.requireNonNull(proxyServer, "proxyServer");
        this.pluginOwner = Objects.requireNonNull(pluginOwner, "pluginOwner");
    }

    @Override
    public WebSocketConnectionManager.ScheduledAction schedule(
            Runnable task, long delay, TimeUnit unit) {
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(unit, "unit");
        if (closed.get()) {
            throw new IllegalStateException("Velocity delay scheduler is closed");
        }

        OwnedAction action = new OwnedAction();
        ownedActions.add(action);
        if (closed.get()) {
            action.cancel();
            throw new IllegalStateException("Velocity delay scheduler is closed");
        }

        try {
            ScheduledTask scheduledTask = proxyServer.getScheduler()
                    .buildTask(pluginOwner, () -> {
                        try {
                            task.run();
                        } finally {
                            action.complete();
                        }
                    })
                    .delay(delay, unit)
                    .schedule();
            action.attach(scheduledTask);
            return action;
        } catch (RuntimeException | Error error) {
            action.complete();
            throw error;
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        for (OwnedAction action : new ArrayList<>(ownedActions)) {
            action.cancel();
        }
    }

    int ownedTaskCount() {
        return ownedActions.size();
    }

    boolean isClosed() {
        return closed.get();
    }

    private final class OwnedAction implements WebSocketConnectionManager.ScheduledAction {
        private final AtomicBoolean complete = new AtomicBoolean();
        private volatile ScheduledTask scheduledTask;

        void attach(ScheduledTask task) {
            scheduledTask = Objects.requireNonNull(task, "task");
            if (complete.get()) {
                task.cancel();
            }
        }

        void complete() {
            if (complete.compareAndSet(false, true)) {
                ownedActions.remove(this);
            }
        }

        @Override
        public void cancel() {
            if (complete.compareAndSet(false, true)) {
                ScheduledTask task = scheduledTask;
                if (task != null) {
                    task.cancel();
                }
                ownedActions.remove(this);
            }
        }
    }
}
