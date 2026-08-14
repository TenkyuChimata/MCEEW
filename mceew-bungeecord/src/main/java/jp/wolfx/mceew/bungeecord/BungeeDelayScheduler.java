package jp.wolfx.mceew.bungeecord;

import java.util.ArrayList;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import jp.wolfx.mceew.websocket.WebSocketConnectionManager;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.api.scheduler.ScheduledTask;
import net.md_5.bungee.api.scheduler.TaskScheduler;

/** Bungee-owned task adapter for future core delayed work and asynchronous config reads. */
public final class BungeeDelayScheduler
        implements WebSocketConnectionManager.DelayScheduler, AutoCloseable {
    interface TaskHandle {
        void cancel();
    }

    interface Backend {
        TaskHandle schedule(Runnable task, long delay, TimeUnit unit);

        TaskHandle runAsync(Runnable task);

        void cancelOwner();
    }

    private final Backend backend;
    private final Set<OwnedAction> ownedActions = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean closed = new AtomicBoolean();

    public BungeeDelayScheduler(TaskScheduler scheduler, Plugin owner) {
        this(new BungeeBackend(scheduler, owner));
    }

    BungeeDelayScheduler(Backend backend) {
        this.backend = Objects.requireNonNull(backend, "backend");
    }

    @Override
    public WebSocketConnectionManager.ScheduledAction schedule(
            Runnable task,
            long delay,
            TimeUnit unit
    ) {
        return submit(task, action -> backend.schedule(action, delay, unit));
    }

    WebSocketConnectionManager.ScheduledAction executeAsync(Runnable task) {
        return submit(task, backend::runAsync);
    }

    private WebSocketConnectionManager.ScheduledAction submit(
            Runnable task,
            Submission submission
    ) {
        Objects.requireNonNull(task, "task");
        if (closed.get()) {
            throw new IllegalStateException("BungeeCord scheduler is closed");
        }

        OwnedAction action = new OwnedAction();
        ownedActions.add(action);
        if (closed.get()) {
            action.cancel();
            throw new IllegalStateException("BungeeCord scheduler is closed");
        }

        try {
            TaskHandle handle = submission.submit(() -> {
                try {
                    task.run();
                } finally {
                    action.complete();
                }
            });
            action.attach(handle);
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
        backend.cancelOwner();
    }

    int ownedTaskCount() {
        return ownedActions.size();
    }

    boolean isClosed() {
        return closed.get();
    }

    @FunctionalInterface
    private interface Submission {
        TaskHandle submit(Runnable action);
    }

    private final class OwnedAction implements WebSocketConnectionManager.ScheduledAction {
        private final AtomicBoolean complete = new AtomicBoolean();
        private volatile TaskHandle handle;

        void attach(TaskHandle taskHandle) {
            handle = Objects.requireNonNull(taskHandle, "taskHandle");
            if (complete.get()) {
                taskHandle.cancel();
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
                TaskHandle taskHandle = handle;
                if (taskHandle != null) {
                    taskHandle.cancel();
                }
                ownedActions.remove(this);
            }
        }
    }

    private static final class BungeeBackend implements Backend {
        private final TaskScheduler scheduler;
        private final Plugin owner;

        private BungeeBackend(TaskScheduler scheduler, Plugin owner) {
            this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
            this.owner = Objects.requireNonNull(owner, "owner");
        }

        @Override
        public TaskHandle schedule(Runnable task, long delay, TimeUnit unit) {
            ScheduledTask scheduled = scheduler.schedule(owner, task, delay, unit);
            return scheduled::cancel;
        }

        @Override
        public TaskHandle runAsync(Runnable task) {
            ScheduledTask scheduled = scheduler.runAsync(owner, task);
            return scheduled::cancel;
        }

        @Override
        public void cancelOwner() {
            scheduler.cancel(owner);
        }
    }
}
