package jp.wolfx.mceew.velocity;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import jp.wolfx.mceew.format.LegacyTextFormatter;
import jp.wolfx.mceew.notification.NotificationProfile;
import jp.wolfx.mceew.notification.NotificationSource;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

final class VelocityConfigLoader {
    static final int CURRENT_PLATFORM_CONFIG_VERSION = 1;
    static final String CONFIG_FILE_NAME = "config.yml";

    private final Path dataDirectory;
    private final ClassLoader resourceLoader;

    VelocityConfigLoader(Path dataDirectory) {
        this(dataDirectory, MCEEWVelocity.class.getClassLoader());
    }

    VelocityConfigLoader(Path dataDirectory, ClassLoader resourceLoader) {
        this.dataDirectory = Objects.requireNonNull(dataDirectory, "dataDirectory");
        this.resourceLoader = Objects.requireNonNull(resourceLoader, "resourceLoader");
    }

    VelocityConfigSnapshot load() throws VelocityConfigException {
        return loadSnapshot();
    }

    /** Reads, parses, and validates a complete immutable snapshot without applying it. */
    VelocityConfigSnapshot loadSnapshot() throws VelocityConfigException {
        Path configPath = dataDirectory.resolve(CONFIG_FILE_NAME);
        try {
            Files.createDirectories(dataDirectory);
            if (Files.notExists(configPath)) {
                createDefault(configPath);
            }
            if (!Files.isRegularFile(configPath)) {
                throw new VelocityConfigException("Velocity config is not a regular file: " + configPath);
            }
            return parse(configPath, loadBundledDefaults());
        } catch (VelocityConfigException error) {
            throw error;
        } catch (IOException error) {
            throw new VelocityConfigException("Unable to read Velocity config: " + configPath, error);
        }
    }

    private void createDefault(Path configPath) throws IOException, VelocityConfigException {
        Path temporary = Files.createTempFile(dataDirectory, CONFIG_FILE_NAME + ".", ".tmp");
        try {
            try (InputStream input = resourceLoader.getResourceAsStream(CONFIG_FILE_NAME)) {
                if (input == null) {
                    throw new VelocityConfigException("Bundled Velocity config is missing: " + CONFIG_FILE_NAME);
                }
                Files.copy(input, temporary, StandardCopyOption.REPLACE_EXISTING);
            }
            moveNewFile(temporary, configPath);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private Map<?, ?> loadBundledDefaults() throws IOException, VelocityConfigException {
        try (InputStream input = resourceLoader.getResourceAsStream(CONFIG_FILE_NAME)) {
            if (input == null) {
                throw new VelocityConfigException("Bundled Velocity config is missing: " + CONFIG_FILE_NAME);
            }
            return loadMapping(new InputStreamReader(input, StandardCharsets.UTF_8),
                    "Bundled Velocity config");
        }
    }

    private static void moveNewFile(Path temporary, Path configPath) throws IOException {
        try {
            Files.move(temporary, configPath, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, configPath);
        } catch (FileAlreadyExistsException ignored) {
            // Another initializer created it first; load that complete file instead.
        }
    }

    private static VelocityConfigSnapshot parse(Path configPath, Map<?, ?> bundled)
            throws IOException, VelocityConfigException {
        Map<?, ?> root;
        try (Reader reader = Files.newBufferedReader(configPath, StandardCharsets.UTF_8)) {
            root = loadMapping(reader, "Velocity config: " + configPath);
        }

        Object versionValue = root.get("platform-config-version");
        if (!(versionValue instanceof Integer)) {
            throw new VelocityConfigException("platform-config-version must be an integer");
        }
        int version = (Integer) versionValue;
        if (version != CURRENT_PLATFORM_CONFIG_VERSION) {
            throw new VelocityConfigException("Unsupported platform-config-version " + version
                    + "; expected " + CURRENT_PLATFORM_CONFIG_VERSION);
        }

        Map<?, ?> global = requireMapping(root, "global", "global");
        boolean runtimeEnabled = optionalBoolean(global, "enabled", true, "global.enabled");
        Map<?, ?> sources = optionalMapping(global, "sources", "global.sources");
        boolean jmaEnabled = optionalBoolean(sources, "jma", true, "global.sources.jma");
        boolean sichuanEnabled = optionalBoolean(
                sources, "sichuan", true, "global.sources.sichuan");
        boolean fujianEnabled = optionalBoolean(
                sources, "fujian", true, "global.sources.fujian");
        boolean cwaEnabled = optionalBoolean(sources, "cwa", true, "global.sources.cwa");
        boolean cencEnabled = optionalBoolean(sources, "cenc", true, "global.sources.cenc");
        boolean chongqingEnabled = optionalBoolean(
                sources, "chongqing", true, "global.sources.chongqing");

        VelocityTargetConfig targetConfig = parseTargets(root);
        VelocityNotificationConfig notificationConfig = parseNotifications(
                root, bundled, targetConfig);
        return new VelocityConfigSnapshot(
                version,
                runtimeEnabled,
                jmaEnabled,
                sichuanEnabled,
                fujianEnabled,
                cwaEnabled,
                cencEnabled,
                chongqingEnabled,
                notificationConfig);
    }

    private static Map<?, ?> loadMapping(Reader reader, String description)
            throws VelocityConfigException {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        Object document;
        try {
            document = new Yaml(new SafeConstructor(options)).load(reader);
        } catch (YAMLException error) {
            throw new VelocityConfigException("Malformed " + description, error);
        }
        if (!(document instanceof Map)) {
            throw new VelocityConfigException(description + " root must be a mapping");
        }
        return (Map<?, ?>) document;
    }

    private static VelocityNotificationConfig parseNotifications(
            Map<?, ?> root,
            Map<?, ?> bundled,
            VelocityTargetConfig targets
    ) throws VelocityConfigException {
        Map<?, ?> userNotifications = optionalMapping(root, "notifications", "notifications");
        Map<?, ?> defaultNotifications = requireMapping(
                bundled, "notifications", "bundled notifications");

        String timeFormat = optionalStringWithFallback(
                userNotifications,
                defaultNotifications,
                "time-format",
                "notifications.time-format");
        try {
            DateTimeFormatter.ofPattern(timeFormat);
        } catch (IllegalArgumentException error) {
            throw new VelocityConfigException("notifications.time-format is invalid", error);
        }

        Map<?, ?> userDefaults = optionalMapping(
                userNotifications, "defaults", "notifications.defaults");
        Map<?, ?> bundledDefaults = requireMapping(
                defaultNotifications, "defaults", "bundled notifications.defaults");
        VelocityChannelPolicy channelDefaults = new VelocityChannelPolicy(
                optionalBooleanWithFallback(userDefaults, bundledDefaults, "chat",
                        "notifications.defaults.chat"),
                optionalBooleanWithFallback(userDefaults, bundledDefaults, "title",
                        "notifications.defaults.title"),
                optionalBooleanWithFallback(userDefaults, bundledDefaults, "sound",
                        "notifications.defaults.sound"));

        Map<?, ?> userSources = optionalMapping(
                userNotifications, "sources", "notifications.sources");
        Map<?, ?> bundledSources = requireMapping(
                defaultNotifications, "sources", "bundled notifications.sources");
        validateSourceKeys(userSources, "notifications.sources");
        Map<NotificationSource, VelocityNotificationConfig.SourceSettings> sourceSettings =
                new EnumMap<>(NotificationSource.class);
        for (Map.Entry<String, NotificationSource> entry
                : VelocityNotificationSources.entries().entrySet()) {
            String key = entry.getKey();
            NotificationSource source = entry.getValue();
            Map<?, ?> userSource = optionalMapping(userSources, key,
                    "notifications.sources." + key);
            Map<?, ?> bundledSource = requireMapping(
                    bundledSources, key, "bundled notifications.sources." + key);
            sourceSettings.put(source, parseSourceSettings(
                    source, key, userSource, bundledSource));
        }

        Map<String, VelocityNotificationConfig.ServerSettings> serverSettings =
                parseServers(root);
        return new VelocityNotificationConfig(
                timeFormat, channelDefaults, sourceSettings, serverSettings, targets);
    }

    private static VelocityNotificationConfig.SourceSettings parseSourceSettings(
            NotificationSource source,
            String key,
            Map<?, ?> user,
            Map<?, ?> bundled
    ) throws VelocityConfigException {
        VelocityChannelOverrides channels = parseOverrides(
                optionalMapping(user, "channels", "notifications.sources." + key + ".channels"),
                "notifications.sources." + key + ".channels");
        String message = legacy(optionalStringWithFallback(
                user, bundled, "message", "notifications.sources." + key + ".message"));
        if (VelocityNotificationSources.isEarthquakeList(source)) {
            return new VelocityNotificationConfig.SourceSettings(null, message, channels);
        }

        String title = legacy(optionalStringWithFallback(
                user, bundled, "title", "notifications.sources." + key + ".title"));
        String subtitle = legacy(optionalStringWithFallback(
                user, bundled, "subtitle", "notifications.sources." + key + ".subtitle"));
        Map<?, ?> userSound = optionalMapping(
                user, "sound", "notifications.sources." + key + ".sound");
        Map<?, ?> bundledSound = requireMapping(
                bundled, "sound", "bundled notifications.sources." + key + ".sound");
        String soundKey = optionalStringWithFallback(
                userSound, bundledSound, "key", "notifications.sources." + key + ".sound.key");
        double volume = optionalNumberWithFallback(
                userSound, bundledSound, "volume", "notifications.sources." + key + ".sound.volume");
        double pitch = optionalNumberWithFallback(
                userSound, bundledSound, "pitch", "notifications.sources." + key + ".sound.pitch");
        return new VelocityNotificationConfig.SourceSettings(
                new NotificationProfile(message, title, subtitle, soundKey, volume, pitch),
                null,
                channels);
    }

    private static VelocityTargetConfig parseTargets(Map<?, ?> root)
            throws VelocityConfigException {
        Map<?, ?> groupsRaw = requireMapping(root, "groups", "groups");
        Map<String, Set<String>> groups = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : groupsRaw.entrySet()) {
            String originalName = stringKey(entry.getKey(), "groups");
            String name = normalizeName(originalName);
            if (groups.containsKey(name)) {
                throw new VelocityConfigException(
                        "Duplicate group name after case normalization: " + originalName);
            }
            groups.put(name, parseNameSet(entry.getValue(), "groups." + originalName));
        }

        Map<?, ?> targets = requireMapping(root, "targets", "targets");
        VelocityTargetConfig.TargetSpec defaultTarget = parseTargetSpec(
                requireMapping(targets, "default", "targets.default"), "targets.default");
        Map<?, ?> sourceTargetsRaw = requireMapping(targets, "sources", "targets.sources");
        validateSourceKeys(sourceTargetsRaw, "targets.sources");
        Map<NotificationSource, VelocityTargetConfig.TargetSpec> sourceTargets =
                new EnumMap<>(NotificationSource.class);
        for (Map.Entry<?, ?> entry : sourceTargetsRaw.entrySet()) {
            String key = stringKey(entry.getKey(), "targets.sources");
            NotificationSource source = VelocityNotificationSources.fromKey(key);
            sourceTargets.put(source, parseTargetSpec(
                    mappingValue(entry.getValue(), "targets.sources." + key),
                    "targets.sources." + key));
        }

        validateTargetGroups(defaultTarget, groups, "targets.default");
        for (Map.Entry<NotificationSource, VelocityTargetConfig.TargetSpec> entry
                : sourceTargets.entrySet()) {
            validateTargetGroups(entry.getValue(), groups,
                    "targets.sources." + VelocityNotificationSources.key(entry.getKey()));
        }
        return new VelocityTargetConfig(defaultTarget, sourceTargets, groups);
    }

    private static VelocityTargetConfig.TargetSpec parseTargetSpec(
            Map<?, ?> raw,
            String path
    ) throws VelocityConfigException {
        Object modeValue = raw.get("mode");
        if (!(modeValue instanceof String)) {
            throw new VelocityConfigException(path + ".mode must be 'all', 'selected', or 'none'");
        }
        VelocityTargetConfig.Mode mode;
        switch (((String) modeValue).toLowerCase(Locale.ROOT)) {
            case "all":
                mode = VelocityTargetConfig.Mode.ALL;
                break;
            case "selected":
                mode = VelocityTargetConfig.Mode.SELECTED;
                break;
            case "none":
                mode = VelocityTargetConfig.Mode.NONE;
                break;
            default:
                throw new VelocityConfigException(
                        path + ".mode must be 'all', 'selected', or 'none'");
        }
        Set<String> servers = optionalNameSet(raw, "servers", path + ".servers");
        Set<String> groups = optionalNameSet(raw, "groups", path + ".groups");
        return new VelocityTargetConfig.TargetSpec(mode, servers, groups);
    }

    private static Map<String, VelocityNotificationConfig.ServerSettings> parseServers(
            Map<?, ?> root
    ) throws VelocityConfigException {
        Map<?, ?> serversRaw = requireMapping(root, "servers", "servers");
        Map<String, VelocityNotificationConfig.ServerSettings> servers = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : serversRaw.entrySet()) {
            String originalName = stringKey(entry.getKey(), "servers");
            String name = normalizeName(originalName);
            if (servers.containsKey(name)) {
                throw new VelocityConfigException(
                        "Duplicate server name after case normalization: " + originalName);
            }
            String path = "servers." + originalName;
            Map<?, ?> raw = mappingValue(entry.getValue(), path);
            VelocityChannelOverrides channels = parseOverrides(
                    optionalMapping(raw, "notifications", path + ".notifications"),
                    path + ".notifications");
            Map<?, ?> sources = optionalMapping(raw, "sources", path + ".sources");
            validateSourceKeys(sources, path + ".sources");
            Map<NotificationSource, VelocityChannelOverrides> sourceChannels =
                    new EnumMap<>(NotificationSource.class);
            for (Map.Entry<?, ?> sourceEntry : sources.entrySet()) {
                String sourceKey = stringKey(sourceEntry.getKey(), path + ".sources");
                sourceChannels.put(
                        VelocityNotificationSources.fromKey(sourceKey),
                        parseOverrides(
                                mappingValue(sourceEntry.getValue(), path + ".sources." + sourceKey),
                                path + ".sources." + sourceKey));
            }
            servers.put(name, new VelocityNotificationConfig.ServerSettings(
                    channels, sourceChannels));
        }
        return servers;
    }

    private static VelocityChannelOverrides parseOverrides(Map<?, ?> raw, String path)
            throws VelocityConfigException {
        return new VelocityChannelOverrides(
                nullableBoolean(raw, "chat", path + ".chat"),
                nullableBoolean(raw, "title", path + ".title"),
                nullableBoolean(raw, "sound", path + ".sound"));
    }

    private static void validateSourceKeys(Map<?, ?> raw, String path)
            throws VelocityConfigException {
        for (Object rawKey : raw.keySet()) {
            String key = stringKey(rawKey, path);
            if (VelocityNotificationSources.fromKey(key) == null) {
                throw new VelocityConfigException("Unknown notification source key: " + path + "." + key);
            }
        }
    }

    private static void validateTargetGroups(
            VelocityTargetConfig.TargetSpec target,
            Map<String, Set<String>> groups,
            String path
    ) throws VelocityConfigException {
        for (String group : target.groups()) {
            if (!groups.containsKey(group)) {
                throw new VelocityConfigException(path + " references unknown group: " + group);
            }
        }
    }

    private static Map<?, ?> requireMapping(Map<?, ?> parent, String key, String path)
            throws VelocityConfigException {
        Object value = parent.get(key);
        if (!(value instanceof Map)) {
            throw new VelocityConfigException(path + " must be a mapping");
        }
        return (Map<?, ?>) value;
    }

    private static Map<?, ?> optionalMapping(Map<?, ?> parent, String key, String path)
            throws VelocityConfigException {
        if (!parent.containsKey(key)) {
            return Map.of();
        }
        return mappingValue(parent.get(key), path);
    }

    private static Map<?, ?> mappingValue(Object value, String path)
            throws VelocityConfigException {
        if (!(value instanceof Map)) {
            throw new VelocityConfigException(path + " must be a mapping");
        }
        return (Map<?, ?>) value;
    }

    private static boolean optionalBoolean(
            Map<?, ?> parent, String key, boolean defaultValue, String path)
            throws VelocityConfigException {
        if (!parent.containsKey(key)) {
            return defaultValue;
        }
        Object value = parent.get(key);
        if (!(value instanceof Boolean)) {
            throw new VelocityConfigException(path + " must be a boolean");
        }
        return (Boolean) value;
    }

    private static boolean optionalBooleanWithFallback(
            Map<?, ?> user,
            Map<?, ?> fallback,
            String key,
            String path
    ) throws VelocityConfigException {
        if (user.containsKey(key)) {
            return optionalBoolean(user, key, false, path);
        }
        return optionalBoolean(fallback, key, false, "bundled " + path);
    }

    private static Boolean nullableBoolean(Map<?, ?> parent, String key, String path)
            throws VelocityConfigException {
        if (!parent.containsKey(key)) {
            return null;
        }
        Object value = parent.get(key);
        if (!(value instanceof Boolean)) {
            throw new VelocityConfigException(path + " must be a boolean");
        }
        return (Boolean) value;
    }

    private static String optionalStringWithFallback(
            Map<?, ?> user,
            Map<?, ?> fallback,
            String key,
            String path
    ) throws VelocityConfigException {
        Object value = user.containsKey(key) ? user.get(key) : fallback.get(key);
        if (!(value instanceof String)) {
            throw new VelocityConfigException(path + " must be a string");
        }
        return (String) value;
    }

    private static double optionalNumberWithFallback(
            Map<?, ?> user,
            Map<?, ?> fallback,
            String key,
            String path
    ) throws VelocityConfigException {
        Object value = user.containsKey(key) ? user.get(key) : fallback.get(key);
        if (!(value instanceof Number)) {
            throw new VelocityConfigException(path + " must be a number");
        }
        double number = ((Number) value).doubleValue();
        if (!Double.isFinite(number)) {
            throw new VelocityConfigException(path + " must be finite");
        }
        return number;
    }

    private static Set<String> optionalNameSet(Map<?, ?> parent, String key, String path)
            throws VelocityConfigException {
        if (!parent.containsKey(key)) {
            return Set.of();
        }
        return parseNameSet(parent.get(key), path);
    }

    private static Set<String> parseNameSet(Object value, String path)
            throws VelocityConfigException {
        if (!(value instanceof List)) {
            throw new VelocityConfigException(path + " must be a list of names");
        }
        Set<String> names = new LinkedHashSet<>();
        for (Object item : (List<?>) value) {
            if (!(item instanceof String) || ((String) item).isBlank()) {
                throw new VelocityConfigException(path + " must contain non-empty strings");
            }
            names.add(normalizeName((String) item));
        }
        return names;
    }

    private static String stringKey(Object value, String path) throws VelocityConfigException {
        if (!(value instanceof String) || ((String) value).isBlank()) {
            throw new VelocityConfigException(path + " keys must be non-empty strings");
        }
        return (String) value;
    }

    static String normalizeName(String value) {
        return value.toLowerCase(Locale.ROOT);
    }

    private static String legacy(String value) {
        return LegacyTextFormatter.legacyColors(value);
    }
}
