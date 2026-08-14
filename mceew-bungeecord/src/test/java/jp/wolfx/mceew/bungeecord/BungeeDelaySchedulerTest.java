package jp.wolfx.mceew.bungeecord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import jp.wolfx.mceew.websocket.WebSocketConnectionManager;
import org.junit.jupiter.api.Test;

class BungeeDelaySchedulerTest {
    @Test
    void delayedTaskRunsOnceAndReleasesOwnership() {
        FakeBackend backend = new FakeBackend();
        BungeeDelayScheduler scheduler = new BungeeDelayScheduler(backend);
        AtomicInteger calls = new AtomicInteger();

        scheduler.schedule(calls::incrementAndGet, 7, TimeUnit.SECONDS);

        assertEquals(1, scheduler.ownedTaskCount());
        assertEquals(7, backend.tasks.get(0).delay);
        assertEquals(TimeUnit.SECONDS, backend.tasks.get(0).unit);
        backend.run(0);
        backend.run(0);
        assertEquals(1, calls.get());
        assertEquals(0, scheduler.ownedTaskCount());
    }

    @Test
    void asynchronousExecutionUsesRunAsyncBackend() {
        FakeBackend backend = new FakeBackend();
        BungeeDelayScheduler scheduler = new BungeeDelayScheduler(backend);
        AtomicInteger calls = new AtomicInteger();

        scheduler.executeAsync(calls::incrementAndGet);

        assertTrue(backend.tasks.get(0).async);
        backend.run(0);
        assertEquals(1, calls.get());
    }

    @Test
    void returnedActionCancelsTaskIdempotently() {
        FakeBackend backend = new FakeBackend();
        BungeeDelayScheduler scheduler = new BungeeDelayScheduler(backend);
        AtomicInteger calls = new AtomicInteger();
        WebSocketConnectionManager.ScheduledAction action =
                scheduler.schedule(calls::incrementAndGet, 1, TimeUnit.MILLISECONDS);

        action.cancel();
        action.cancel();
        backend.run(0);

        assertEquals(0, calls.get());
        assertEquals(1, backend.tasks.get(0).cancelCalls);
        assertEquals(0, scheduler.ownedTaskCount());
    }

    @Test
    void closeCancelsEveryOwnedTaskAndOwnerExactlyOnce() {
        FakeBackend backend = new FakeBackend();
        BungeeDelayScheduler scheduler = new BungeeDelayScheduler(backend);
        scheduler.schedule(() -> { }, 1, TimeUnit.SECONDS);
        scheduler.executeAsync(() -> { });

        scheduler.close();
        scheduler.close();

        assertTrue(scheduler.isClosed());
        assertEquals(0, scheduler.ownedTaskCount());
        assertEquals(1, backend.cancelOwnerCalls);
        assertEquals(1, backend.tasks.get(0).cancelCalls);
        assertEquals(1, backend.tasks.get(1).cancelCalls);
        assertThrows(IllegalStateException.class,
                () -> scheduler.executeAsync(() -> { }));
    }

    @Test
    void synchronousBackendCompletionCannotLeakOwnedTask() {
        FakeBackend backend = new FakeBackend();
        backend.runImmediately = true;
        BungeeDelayScheduler scheduler = new BungeeDelayScheduler(backend);
        AtomicInteger calls = new AtomicInteger();

        scheduler.executeAsync(calls::incrementAndGet);

        assertEquals(1, calls.get());
        assertEquals(0, scheduler.ownedTaskCount());
        assertEquals(1, backend.tasks.get(0).cancelCalls,
                "late attachment is cancelled after synchronous completion");
    }

    static final class FakeBackend implements BungeeDelayScheduler.Backend {
        final List<FakeTask> tasks = new ArrayList<>();
        boolean runImmediately;
        boolean failSubmissions;
        private int cancelOwnerCalls;

        @Override
        public BungeeDelayScheduler.TaskHandle schedule(
                Runnable task, long delay, TimeUnit unit) {
            return add(task, delay, unit, false);
        }

        @Override
        public BungeeDelayScheduler.TaskHandle runAsync(Runnable task) {
            return add(task, 0, TimeUnit.NANOSECONDS, true);
        }

        private FakeTask add(Runnable task, long delay, TimeUnit unit, boolean async) {
            if (failSubmissions) {
                throw new IllegalStateException("scheduler rejected task");
            }
            FakeTask fake = new FakeTask(task, delay, unit, async);
            tasks.add(fake);
            if (runImmediately) {
                fake.run();
            }
            return fake;
        }

        @Override
        public void cancelOwner() {
            cancelOwnerCalls++;
        }

        void run(int index) {
            tasks.get(index).run();
        }
    }

    static final class FakeTask implements BungeeDelayScheduler.TaskHandle {
        private final Runnable task;
        private final long delay;
        private final TimeUnit unit;
        private final boolean async;
        private boolean cancelled;
        private boolean complete;
        private int cancelCalls;

        FakeTask(Runnable task, long delay, TimeUnit unit, boolean async) {
            this.task = task;
            this.delay = delay;
            this.unit = unit;
            this.async = async;
        }

        void run() {
            if (!cancelled && !complete) {
                complete = true;
                task.run();
            }
        }

        @Override
        public void cancel() {
            if (!cancelled) {
                cancelled = true;
                cancelCalls++;
            }
        }
    }
}
