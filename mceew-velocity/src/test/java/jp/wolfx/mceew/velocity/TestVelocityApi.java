package jp.wolfx.mceew.velocity;

import com.velocitypowered.api.command.Command;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.permission.Tristate;
import com.velocitypowered.api.proxy.ConsoleCommandSource;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
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
            if ("getCommandManager".equals(method.getName())) {
                return scheduler.commandManager().proxy();
            }
            return defaultValue(method.getReturnType());
        };
        return (ProxyServer) Proxy.newProxyInstance(
                ProxyServer.class.getClassLoader(), new Class<?>[]{ProxyServer.class}, handler);
    }

    static CapturingLogger logger() {
        return new CapturingLogger();
    }

    static CommandSource commandSource(Set<String> permissions, List<Component> messages) {
        return commandSource(CommandSource.class, permissionValues(permissions), messages);
    }

    static CommandSource commandSource(
            Map<String, Tristate> permissions,
            List<Component> messages
    ) {
        return commandSource(CommandSource.class, permissions, messages);
    }

    static ConsoleCommandSource consoleCommandSource(
            Set<String> permissions, List<Component> messages) {
        return (ConsoleCommandSource) commandSource(
                ConsoleCommandSource.class, permissionValues(permissions), messages);
    }

    static CommandSource failingCommandSource(
            Set<String> permissions, RuntimeException sendFailure) {
        InvocationHandler handler = (proxy, method, arguments) -> {
            if (method.getDeclaringClass() == Object.class) {
                return objectMethod(proxy, method, arguments);
            }
            if ("hasPermission".equals(method.getName())) {
                return permissions.contains((String) arguments[0]);
            }
            if ("getPermissionValue".equals(method.getName())) {
                return permissions.contains((String) arguments[0])
                        ? Tristate.TRUE : Tristate.UNDEFINED;
            }
            if ("sendMessage".equals(method.getName())) {
                throw sendFailure;
            }
            return defaultValue(method.getReturnType());
        };
        return (CommandSource) Proxy.newProxyInstance(
                CommandSource.class.getClassLoader(),
                new Class<?>[]{CommandSource.class}, handler);
    }

    private static CommandSource commandSource(
            Class<? extends CommandSource> type,
            Map<String, Tristate> permissions,
            List<Component> messages
    ) {
        InvocationHandler handler = (proxy, method, arguments) -> {
            if (method.getDeclaringClass() == Object.class) {
                return objectMethod(proxy, method, arguments);
            }
            if ("hasPermission".equals(method.getName())) {
                return permissionValue(permissions, (String) arguments[0]).asBoolean();
            }
            if ("getPermissionValue".equals(method.getName())) {
                return permissionValue(permissions, (String) arguments[0]);
            }
            if ("sendMessage".equals(method.getName())) {
                messages.add((Component) arguments[0]);
                return null;
            }
            return defaultValue(method.getReturnType());
        };
        return (CommandSource) Proxy.newProxyInstance(
                type.getClassLoader(),
                new Class<?>[]{type},
                handler);
    }

    private static Map<String, Tristate> permissionValues(Set<String> permissions) {
        Map<String, Tristate> values = new LinkedHashMap<>();
        for (String permission : permissions) {
            values.put(permission, Tristate.TRUE);
        }
        return Map.copyOf(values);
    }

    private static Tristate permissionValue(
            Map<String, Tristate> permissions,
            String permission
    ) {
        return permissions.getOrDefault(permission, Tristate.UNDEFINED);
    }

    static SimpleCommand.Invocation invocation(
            CommandSource source, String alias, String... arguments) {
        InvocationHandler handler = (proxy, method, values) -> {
            if (method.getDeclaringClass() == Object.class) {
                return objectMethod(proxy, method, values);
            }
            switch (method.getName()) {
                case "source":
                    return source;
                case "alias":
                    return alias;
                case "arguments":
                    return arguments;
                default:
                    return defaultValue(method.getReturnType());
            }
        };
        return (SimpleCommand.Invocation) Proxy.newProxyInstance(
                SimpleCommand.Invocation.class.getClassLoader(),
                new Class<?>[]{SimpleCommand.Invocation.class},
                handler);
    }

    static final class CapturingLogger implements InvocationHandler {
        private final List<String> infoMessages = new ArrayList<>();
        private final List<String> warningMessages = new ArrayList<>();
        private final List<String> errorMessages = new ArrayList<>();
        private final List<String> debugMessages = new ArrayList<>();
        private final List<Throwable> throwables = new ArrayList<>();
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

        int warningCountContaining(String text) {
            return countContaining(warningMessages, text);
        }

        int debugCountContaining(String text) {
            return countContaining(debugMessages, text);
        }

        boolean capturedThrowable(Throwable expected) {
            return throwables.contains(expected);
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] arguments) {
            if (method.getDeclaringClass() == Object.class) {
                return objectMethod(proxy, method, arguments);
            }
            if (arguments != null && arguments.length > 0 && arguments[0] instanceof String) {
                if ("info".equals(method.getName())) {
                    infoMessages.add((String) arguments[0]);
                } else if ("warn".equals(method.getName())) {
                    warningMessages.add((String) arguments[0]);
                } else if ("error".equals(method.getName())) {
                    errorMessages.add((String) arguments[0]);
                } else if ("debug".equals(method.getName())) {
                    debugMessages.add((String) arguments[0]);
                }
                for (Object argument : arguments) {
                    if (argument instanceof Throwable) {
                        throwables.add((Throwable) argument);
                    }
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
        private final RecordingCommandManager commandManager = new RecordingCommandManager();
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

        RecordingCommandManager commandManager() {
            return commandManager;
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

    static final class RecordingCommandManager implements InvocationHandler {
        private final Map<String, Command> commands = new LinkedHashMap<>();
        private final Map<String, CommandMeta> metadata = new LinkedHashMap<>();
        private final CommandManager proxy = (CommandManager) Proxy.newProxyInstance(
                CommandManager.class.getClassLoader(),
                new Class<?>[]{CommandManager.class},
                this);
        private int registrations;
        private int unregistrations;
        private RuntimeException registrationFailure;

        CommandManager proxy() {
            return proxy;
        }

        SimpleCommand command(String alias) {
            return (SimpleCommand) commands.get(normalize(alias));
        }

        boolean hasCommand(String alias) {
            return commands.containsKey(normalize(alias));
        }

        int registrations() {
            return registrations;
        }

        int unregistrations() {
            return unregistrations;
        }

        void failRegistration(RuntimeException failure) {
            registrationFailure = failure;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] arguments) {
            if (method.getDeclaringClass() == Object.class) {
                return objectMethod(proxy, method, arguments);
            }
            switch (method.getName()) {
                case "metaBuilder":
                    if (arguments[0] instanceof String) {
                        return commandMetaBuilder((String) arguments[0]);
                    }
                    break;
                case "register":
                    if (arguments.length == 2 && arguments[0] instanceof CommandMeta) {
                        register((CommandMeta) arguments[0], (Command) arguments[1]);
                        return null;
                    }
                    break;
                case "unregister":
                    if (arguments[0] instanceof CommandMeta) {
                        unregister((CommandMeta) arguments[0]);
                    } else {
                        unregister((String) arguments[0]);
                    }
                    return null;
                case "getCommandMeta":
                    return metadata.get(normalize((String) arguments[0]));
                case "getAliases":
                    return Collections.unmodifiableSet(commands.keySet());
                case "hasCommand":
                    return hasCommand((String) arguments[0]);
                default:
                    break;
            }
            return defaultValue(method.getReturnType());
        }

        private void register(CommandMeta meta, Command command) {
            if (registrationFailure != null) {
                throw registrationFailure;
            }
            for (String alias : meta.getAliases()) {
                if (commands.containsKey(normalize(alias))) {
                    throw new IllegalArgumentException("Alias already registered: " + alias);
                }
            }
            for (String alias : meta.getAliases()) {
                String normalized = normalize(alias);
                commands.put(normalized, command);
                metadata.put(normalized, meta);
            }
            registrations++;
        }

        private void unregister(CommandMeta meta) {
            for (String alias : meta.getAliases()) {
                commands.remove(normalize(alias));
                metadata.remove(normalize(alias));
            }
            unregistrations++;
        }

        private void unregister(String alias) {
            CommandMeta meta = metadata.get(normalize(alias));
            if (meta != null) {
                unregister(meta);
            }
        }

        private static CommandMeta.Builder commandMetaBuilder(String primaryAlias) {
            class BuilderHandler implements InvocationHandler {
                private final List<String> aliases = new ArrayList<>(List.of(primaryAlias));
                private Object plugin;

                @Override
                public Object invoke(Object proxy, Method method, Object[] arguments) {
                    if (method.getDeclaringClass() == Object.class) {
                        return objectMethod(proxy, method, arguments);
                    }
                    switch (method.getName()) {
                        case "aliases":
                            Collections.addAll(aliases, (String[]) arguments[0]);
                            return proxy;
                        case "plugin":
                            plugin = arguments[0];
                            return proxy;
                        case "hint":
                            return proxy;
                        case "build":
                            return commandMeta(List.copyOf(aliases), plugin);
                        default:
                            return defaultValue(method.getReturnType());
                    }
                }
            }
            return (CommandMeta.Builder) Proxy.newProxyInstance(
                    CommandMeta.Builder.class.getClassLoader(),
                    new Class<?>[]{CommandMeta.Builder.class},
                    new BuilderHandler());
        }

        private static CommandMeta commandMeta(List<String> aliases, Object plugin) {
            InvocationHandler handler = (proxy, method, arguments) -> {
                if (method.getDeclaringClass() == Object.class) {
                    return objectMethod(proxy, method, arguments);
                }
                switch (method.getName()) {
                    case "getAliases":
                        return aliases;
                    case "getHints":
                        return List.of();
                    case "getPlugin":
                        return plugin;
                    default:
                        return defaultValue(method.getReturnType());
                }
            };
            return (CommandMeta) Proxy.newProxyInstance(
                    CommandMeta.class.getClassLoader(),
                    new Class<?>[]{CommandMeta.class},
                    handler);
        }

        private static String normalize(String alias) {
            return alias.toLowerCase(Locale.ROOT);
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

    static Object defaultValue(Class<?> type) {
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
