package jp.wolfx.mceew.velocity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import jp.wolfx.mceew.websocket.WebSocketConnectionManager;
import com.velocitypowered.api.scheduler.TaskStatus;
import org.junit.jupiter.api.Test;

class VelocityDelaySchedulerTest {
    @Test
    void mapsDelayAndExecutesTask() {
        TestVelocityApi.RecordingScheduler platform = new TestVelocityApi.RecordingScheduler();
        VelocityDelayScheduler scheduler = new VelocityDelayScheduler(
                TestVelocityApi.proxyServer(platform), this);
        AtomicInteger executions = new AtomicInteger();

        scheduler.schedule(executions::incrementAndGet, 1200L, TimeUnit.MILLISECONDS);

        assertEquals(1200L, platform.lastDelay());
        assertEquals(TimeUnit.MILLISECONDS, platform.lastDelayUnit());
        assertEquals(1, scheduler.ownedTaskCount());
        platform.runAll();
        assertEquals(1, executions.get());
        assertEquals(0, scheduler.ownedTaskCount());
    }

    @Test
    void cancellationPreventsExecution() {
        TestVelocityApi.RecordingScheduler platform = new TestVelocityApi.RecordingScheduler();
        VelocityDelayScheduler scheduler = new VelocityDelayScheduler(
                TestVelocityApi.proxyServer(platform), this);
        AtomicInteger executions = new AtomicInteger();

        WebSocketConnectionManager.ScheduledAction action =
                scheduler.schedule(executions::incrementAndGet, 1L, TimeUnit.SECONDS);
        action.cancel();
        platform.runAll();

        assertEquals(0, executions.get());
        assertEquals(0, scheduler.ownedTaskCount());
        assertEquals(TaskStatus.CANCELLED, platform.tasks().get(0).status());
    }

    @Test
    void closeCancelsAllOwnedTasksAndIsIdempotent() {
        TestVelocityApi.RecordingScheduler platform = new TestVelocityApi.RecordingScheduler();
        VelocityDelayScheduler scheduler = new VelocityDelayScheduler(
                TestVelocityApi.proxyServer(platform), this);
        scheduler.schedule(() -> { }, 1L, TimeUnit.SECONDS);
        scheduler.schedule(() -> { }, 2L, TimeUnit.SECONDS);

        scheduler.close();
        scheduler.close();

        assertTrue(scheduler.isClosed());
        assertEquals(0, scheduler.ownedTaskCount());
        assertTrue(platform.tasks().stream()
                .allMatch(task -> task.status() == TaskStatus.CANCELLED));
    }

    @Test
    void closedSchedulerRejectsNewTasks() {
        TestVelocityApi.RecordingScheduler platform = new TestVelocityApi.RecordingScheduler();
        VelocityDelayScheduler scheduler = new VelocityDelayScheduler(
                TestVelocityApi.proxyServer(platform), this);
        scheduler.close();

        assertThrows(IllegalStateException.class,
                () -> scheduler.schedule(() -> { }, 1L, TimeUnit.MILLISECONDS));
        assertFalse(platform.tasks().stream().anyMatch(task -> task.status() == TaskStatus.SCHEDULED));
    }
}
