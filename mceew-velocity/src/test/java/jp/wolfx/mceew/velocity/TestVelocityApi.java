package jp.wolfx.mceew.velocity;

import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.scheduler.ScheduledTask;
import com.velocitypowered.api.scheduler.Scheduler;
import com.velocitypowered.api.scheduler.TaskStatus;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.slf4j.Logger;

final class TestVelocityApi {
    private TestVelocityApi() {
    }

    static ProxyServer proxyServer(RecordingScheduler scheduler) {
        InvocationHandler handler = (proxy, method, arguments) -> {
            if (method.getDeclaringClass() == Object.class) {
                return objectMethod(proxy, method, arguments);
            }
            if ("getScheduler".equals(method.getName())) {
                scheduler.schedulerRequests++;
                return scheduler;
            }
            return defaultValue(method.getReturnType());
        };
        return (ProxyServer) Proxy.newProxyInstance(
                ProxyServer.class.getClassLoader(), new Class<?>[]{ProxyServer.class}, handler);
    }

    static CapturingLogger logger() {
        return new CapturingLogger();
    }

    static final class CapturingLogger implements InvocationHandler {
        private final List<String> infoMessages = new ArrayList<>();
        private final List<String> errorMessages = new ArrayList<>();
        private final Logger proxy = (Logger) Proxy.newProxyInstance(
                Logger.class.getClassLoader(), new Class<?>[]{Logger.class}, this);

        Logger proxy() {
            return proxy;
        }

        int infoCountContaining(String text) {
            return countContaining(infoMessages, text);
        }

        int errorCountContaining(String text) {
            return countContaining(errorMessages, text);
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] arguments) {
            if (method.getDeclaringClass() == Object.class) {
                return objectMethod(proxy, method, arguments);
            }
            if (arguments != null && arguments.length > 0 && arguments[0] instanceof String) {
                if ("info".equals(method.getName())) {
                    infoMessages.add((String) arguments[0]);
                } else if ("error".equals(method.getName())) {
                    errorMessages.add((String) arguments[0]);
                }
            }
            return defaultValue(method.getReturnType());
        }

        private static int countContaining(List<String> messages, String text) {
            int count = 0;
            for (String message : messages) {
                if (message.contains(text)) {
                    count++;
                }
            }
            return count;
        }
    }

    static final class RecordingScheduler implements Scheduler {
        private final List<RecordingTask> tasks = new ArrayList<>();
        private int schedulerRequests;
        private long lastDelay;
        private TimeUnit lastDelayUnit;

        @Override
        public TaskBuilder buildTask(Object plugin, Runnable runnable) {
            return new RecordingTaskBuilder(plugin, runnable, null);
        }

        @Override
        public TaskBuilder buildTask(Object plugin, Consumer<ScheduledTask> consumer) {
            return new RecordingTaskBuilder(plugin, null, consumer);
        }

        @Override
        public Collection<ScheduledTask> tasksByPlugin(Object plugin) {
            List<ScheduledTask> matching = new ArrayList<>();
            for (RecordingTask task : tasks) {
                if (task.plugin() == plugin) {
                    matching.add(task);
                }
            }
            return Collections.unmodifiableList(matching);
        }

        void runAll() {
            for (RecordingTask task : new ArrayList<>(tasks)) {
                task.runIfScheduled();
            }
        }

        List<RecordingTask> tasks() {
            return Collections.unmodifiableList(tasks);
        }

        int schedulerRequests() {
            return schedulerRequests;
        }

        long lastDelay() {
            return lastDelay;
        }

        TimeUnit lastDelayUnit() {
            return lastDelayUnit;
        }

        private final class RecordingTaskBuilder implements TaskBuilder {
            private final Object plugin;
            private final Runnable runnable;
            private final Consumer<ScheduledTask> consumer;
            private long delay;
            private TimeUnit delayUnit = TimeUnit.NANOSECONDS;

            private RecordingTaskBuilder(
                    Object plugin, Runnable runnable, Consumer<ScheduledTask> consumer) {
                this.plugin = plugin;
                this.runnable = runnable;
                this.consumer = consumer;
            }

            @Override
            public TaskBuilder delay(long delay, TimeUnit unit) {
                this.delay = delay;
                this.delayUnit = unit;
                lastDelay = delay;
                lastDelayUnit = unit;
                return this;
            }

            @Override
            public TaskBuilder repeat(long repeat, TimeUnit unit) {
                return this;
            }

            @Override
            public TaskBuilder clearDelay() {
                delay = 0;
                delayUnit = TimeUnit.NANOSECONDS;
                return this;
            }

            @Override
            public TaskBuilder clearRepeat() {
                return this;
            }

            @Override
            public ScheduledTask schedule() {
                RecordingTask task = new RecordingTask(plugin, runnable, consumer, delay, delayUnit);
                tasks.add(task);
                return task;
            }
        }

        static final class RecordingTask implements ScheduledTask {
            private final Object plugin;
            private final Runnable runnable;
            private final Consumer<ScheduledTask> consumer;
            private final long delay;
            private final TimeUnit delayUnit;
            private TaskStatus status = TaskStatus.SCHEDULED;

            private RecordingTask(
                    Object plugin,
                    Runnable runnable,
                    Consumer<ScheduledTask> consumer,
                    long delay,
                    TimeUnit delayUnit) {
                this.plugin = plugin;
                this.runnable = runnable;
                this.consumer = consumer;
                this.delay = delay;
                this.delayUnit = delayUnit;
            }

            @Override
            public Object plugin() {
                return plugin;
            }

            @Override
            public TaskStatus status() {
                return status;
            }

            @Override
            public void cancel() {
                status = TaskStatus.CANCELLED;
            }

            void runIfScheduled() {
                if (status != TaskStatus.SCHEDULED) {
                    return;
                }
                if (runnable != null) {
                    runnable.run();
                } else {
                    consumer.accept(this);
                }
                if (status == TaskStatus.SCHEDULED) {
                    status = TaskStatus.FINISHED;
                }
            }

            long delay() {
                return delay;
            }

            TimeUnit delayUnit() {
                return delayUnit;
            }
        }
    }

    private static Object objectMethod(Object proxy, Method method, Object[] arguments) {
        if ("toString".equals(method.getName())) {
            return "test-" + proxy.getClass().getInterfaces()[0].getSimpleName();
        }
        if ("hashCode".equals(method.getName())) {
            return System.identityHashCode(proxy);
        }
        if ("equals".equals(method.getName())) {
            return proxy == arguments[0];
        }
        return null;
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == char.class) {
            return '\0';
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == short.class) {
            return (short) 0;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == float.class) {
            return 0.0f;
        }
        if (type == double.class) {
            return 0.0d;
        }
        return null;
    }
}
