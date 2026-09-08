package jp.wolfx.mceew;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import jp.wolfx.mceew.scheduler.PlatformScheduler;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Logger;

final class MceewCharacterizationSupport {
    private static final String FIXTURE_ROOT = "websocket/current-schema/";
    private static final String PROJECT_VERSION_PROPERTY = "mceew.project.version";

    private MceewCharacterizationSupport() {
    }

    static Harness harness() {
        return new Harness(defaultConfiguration());
    }

    static Harness harness(YamlConfiguration configuration) {
        return new Harness(configuration);
    }

    static JsonObject fixture(String name) {
        String path = FIXTURE_ROOT + name + ".json";
        try (InputStream input = MceewCharacterizationSupport.class
                .getClassLoader().getResourceAsStream(path)) {
            if (input == null) {
                throw new IllegalStateException("Fixture not found: " + path);
            }
            return JsonParser.parseString(
                    new String(input.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException error) {
            throw new IllegalStateException("Unable to read fixture " + path, error);
        }
    }

    static String freshPayload(String name) {
        JsonObject payload = fixture(name);
        if (payload.has("AnnouncedTime")) {
            payload.addProperty("AnnouncedTime", ZonedDateTime.now(ZoneId.of("Asia/Tokyo"))
                    .format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss")));
        }
        if (payload.has("ReportTime")) {
            payload.addProperty("ReportTime", ZonedDateTime.now(ZoneId.of("Asia/Shanghai"))
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        }
        return payload.toString();
    }

    static YamlConfiguration defaultConfiguration() {
        try (InputStream input = MceewCharacterizationSupport.class
                .getClassLoader().getResourceAsStream("config.yml")) {
            if (input == null) {
                throw new IllegalStateException("config.yml test resource not found");
            }
            YamlConfiguration configuration = new YamlConfiguration();
            configuration.loadFromString(
                    new String(input.readAllBytes(), StandardCharsets.UTF_8));
            return configuration;
        } catch (Exception error) {
            throw new IllegalStateException("Unable to load bundled config.yml", error);
        }
    }

    static CommandSender sender(boolean operator, List<String> messages) {
        return (CommandSender) Proxy.newProxyInstance(
                CommandSender.class.getClassLoader(),
                new Class<?>[]{CommandSender.class},
                (proxy, method, arguments) -> {
                    switch (method.getName()) {
                        case "sendMessage":
                            if (arguments != null && arguments.length > 0) {
                                if (arguments[0] instanceof String[]) {
                                    messages.addAll(Arrays.asList((String[]) arguments[0]));
                                } else if (arguments[0] instanceof String) {
                                    messages.add((String) arguments[0]);
                                }
                            }
                            return null;
                        case "isOp":
                            return operator;
                        case "getName":
                            return operator ? "operator" : "sender";
                        case "toString":
                            return "FakeCommandSender";
                        case "hashCode":
                            return System.identityHashCode(proxy);
                        case "equals":
                            return proxy == arguments[0];
                        default:
                            return defaultValue(method.getReturnType());
                    }
                }
        );
    }

    static final class Harness {
        final BukkitMceewRuntime runtime;
        final BukkitMceewCommand command;
        final RecordingPlayer player = new RecordingPlayer();
        final List<String> console = new ArrayList<>();
        final YamlConfiguration configuration;

        private Harness(YamlConfiguration configuration) {
            this.configuration = configuration;
            ImmediateScheduler scheduler = new ImmediateScheduler(player.proxy);
            BukkitNotificationDispatcher dispatcher = new BukkitNotificationDispatcher(
                    scheduler, Logger.getLogger("MCEEW-characterization"), console::add);
            runtime = new BukkitMceewRuntime(
                    new EarthquakeInfoCache(), dispatcher);
            reloadRuntimeConfiguration();
            BukkitConfigurationReloader reloader = new BukkitConfigurationReloader(
                    () -> { }, () -> { }, this::reloadRuntimeConfiguration,
                    Logger.getLogger("MCEEW-characterization.reload"));
            command = new BukkitMceewCommand(
                    projectVersion(), runtime, reloader, () -> { });
        }

        void reloadRuntimeConfiguration() {
            runtime.applyConfiguration(configuration);
        }

        void route(String payload) {
            runtime.handleWebSocketMessage(payload);
        }

        void routeFresh(String fixture) {
            route(freshPayload(fixture));
        }

        void clearOutput() {
            console.clear();
            player.chat.clear();
            player.titles.clear();
            player.sounds.clear();
        }

        EarthquakeInfoCache cache() {
            return runtime.earthquakeInfoCache();
        }
    }

    static String projectVersion() {
        String version = System.getProperty(PROJECT_VERSION_PROPERTY);
        if (version == null || version.isBlank()) {
            throw new IllegalStateException(
                    "Missing Maven test property: " + PROJECT_VERSION_PROPERTY);
        }
        return version;
    }

    static final class RecordingPlayer {
        final List<String> chat = new ArrayList<>();
        final List<RecordedTitle> titles = new ArrayList<>();
        final List<RecordedSound> sounds = new ArrayList<>();
        final Map<String, Boolean> permissions = new HashMap<>();
        final Set<String> queriedPermissions = new LinkedHashSet<>();
        final List<String> permissionQueries = new ArrayList<>();
        final Player proxy;

        RecordingPlayer() {
            proxy = (Player) Proxy.newProxyInstance(
                    Player.class.getClassLoader(), new Class<?>[]{Player.class},
                    (instance, method, arguments) -> {
                        switch (method.getName()) {
                            case "hasPermission":
                                String permission = (String) arguments[0];
                                queriedPermissions.add(permission);
                                permissionQueries.add(permission);
                                return permissions.getOrDefault(permission, true);
                            case "sendMessage":
                                if (arguments[0] instanceof String) {
                                    chat.add((String) arguments[0]);
                                } else if (arguments[0] instanceof String[]) {
                                    chat.addAll(Arrays.asList((String[]) arguments[0]));
                                }
                                return null;
                            case "sendTitle":
                                titles.add(new RecordedTitle(
                                        (String) arguments[0], (String) arguments[1],
                                        (Integer) arguments[2], (Integer) arguments[3],
                                        (Integer) arguments[4]));
                                return null;
                            case "playSound":
                                if (arguments.length == 4 && arguments[1] instanceof String) {
                                    sounds.add(new RecordedSound(
                                            (String) arguments[1],
                                            ((Number) arguments[2]).floatValue(),
                                            ((Number) arguments[3]).floatValue()));
                                }
                                return null;
                            case "isOnline":
                                return true;
                            case "getName":
                                return "characterization-player";
                            case "toString":
                                return "RecordingPlayer";
                            case "hashCode":
                                return System.identityHashCode(instance);
                            case "equals":
                                return instance == arguments[0];
                            default:
                                return defaultValue(method.getReturnType());
                        }
                    });
        }
    }

    static final class RecordedTitle {
        final String title;
        final String subtitle;
        final int fadeIn;
        final int stay;
        final int fadeOut;

        private RecordedTitle(
                String title, String subtitle, int fadeIn, int stay, int fadeOut) {
            this.title = title;
            this.subtitle = subtitle;
            this.fadeIn = fadeIn;
            this.stay = stay;
            this.fadeOut = fadeOut;
        }
    }

    static final class RecordedSound {
        final String key;
        final float volume;
        final float pitch;

        private RecordedSound(String key, float volume, float pitch) {
            this.key = key;
            this.volume = volume;
            this.pitch = pitch;
        }
    }

    static final class ImmediateScheduler implements PlatformScheduler {
        private final Player player;

        ImmediateScheduler(Player player) {
            this.player = player;
        }

        @Override
        public boolean isFolia() {
            return false;
        }

        @Override
        public void runAsync(Runnable task) {
            task.run();
        }

        @Override
        public TaskHandle runAsyncDelayed(Runnable task, long delay, TimeUnit unit) {
            return () -> {
            };
        }

        @Override
        public void runGlobal(Runnable task) {
            task.run();
        }

        @Override
        public void forEachPlayer(Consumer<Player> action) {
            action.accept(player);
        }

        @Override
        public void cancelTasks() {
        }
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
            return 0F;
        }
        if (type == double.class) {
            return 0D;
        }
        return null;
    }
}
