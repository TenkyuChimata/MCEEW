package jp.wolfx.mceew.bungeecord;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
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
import jp.wolfx.mceew.notification.NotificationSource;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

final class BungeeConfigLoader {
    static final int CURRENT_PLATFORM_CONFIG_VERSION = 1;
    static final String CONFIG_FILE_NAME = "config.yml";

    private final Path dataDirectory;
    private final ClassLoader resourceLoader;

    BungeeConfigLoader(Path dataDirectory) {
        this(dataDirectory, MCEEWBungeeCord.class.getClassLoader());
    }

    BungeeConfigLoader(Path dataDirectory, ClassLoader resourceLoader) {
        this.dataDirectory = Objects.requireNonNull(dataDirectory, "dataDirectory");
        this.resourceLoader = Objects.requireNonNull(resourceLoader, "resourceLoader");
    }

    BungeeConfigSnapshot loadSnapshot() throws BungeeConfigException {
        Path configPath = dataDirectory.resolve(CONFIG_FILE_NAME);
        try {
            Files.createDirectories(dataDirectory);
            if (Files.notExists(configPath)) {
                createDefault(configPath);
            }
            if (!Files.isRegularFile(configPath)) {
                throw new BungeeConfigException(
                        "BungeeCord config is not a regular file: " + configPath);
            }
            return parse(configPath, loadBundledDefaults());
        } catch (BungeeConfigException error) {
            throw error;
        } catch (IOException error) {
            throw new BungeeConfigException(
                    "Unable to read BungeeCord config: " + configPath, error);
        }
    }

    private void createDefault(Path configPath) throws IOException, BungeeConfigException {
        Path temporary = Files.createTempFile(dataDirectory, CONFIG_FILE_NAME + ".", ".tmp");
        try {
            try (InputStream input = resourceLoader.getResourceAsStream(CONFIG_FILE_NAME)) {
                if (input == null) {
                    throw new BungeeConfigException(
                            "Bundled BungeeCord config is missing: " + CONFIG_FILE_NAME);
                }
                Files.copy(input, temporary, StandardCopyOption.REPLACE_EXISTING);
            }
            moveNewFile(temporary, configPath);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private Map<?, ?> loadBundledDefaults() throws IOException, BungeeConfigException {
        try (InputStream input = resourceLoader.getResourceAsStream(CONFIG_FILE_NAME)) {
            if (input == null) {
                throw new BungeeConfigException(
                        "Bundled BungeeCord config is missing: " + CONFIG_FILE_NAME);
            }
            return loadMapping(
                    new InputStreamReader(input, StandardCharsets.UTF_8),
                    "bundled BungeeCord config");
        }
    }

    private static void moveNewFile(Path temporary, Path configPath) throws IOException {
        try {
            Files.move(temporary, configPath);
        } catch (FileAlreadyExistsException ignored) {
            // Another initializer won the race; load its complete file instead.
        }
    }

    private static BungeeConfigSnapshot parse(Path configPath, Map<?, ?> bundled)
            throws IOException, BungeeConfigException {
        Map<?, ?> root;
        try (Reader reader = Files.newBufferedReader(configPath, StandardCharsets.UTF_8)) {
            root = loadMapping(reader, "BungeeCord config: " + configPath);
        }

        Object versionValue = root.get("platform_config_version");
        if (!(versionValue instanceof Integer)) {
            throw new BungeeConfigException("platform_config_version must be an integer");
        }
        int version = (Integer) versionValue;
        if (version != CURRENT_PLATFORM_CONFIG_VERSION) {
            throw new BungeeConfigException("Unsupported platform_config_version " + version
                    + "; expected " + CURRENT_PLATFORM_CONFIG_VERSION);
        }

        Map<?, ?> global = requireMapping(root, "global", "global");
        boolean runtimeEnabled = optionalBoolean(global, "enabled", true, "global.enabled");
        Map<?, ?> sourceGates = optionalMapping(global, "sources", "global.sources");
        BungeeConfigSnapshot.SourceGates gates = new BungeeConfigSnapshot.SourceGates(
                optionalBoolean(sourceGates, "enable_jp", true,
                        "global.sources.enable_jp"),
                optionalBoolean(sourceGates, "enable_sc", true,
                        "global.sources.enable_sc"),
                optionalBoolean(sourceGates, "enable_fj", true,
                        "global.sources.enable_fj"),
                optionalBoolean(sourceGates, "enable_cwa", true,
                        "global.sources.enable_cwa"),
                optionalBoolean(sourceGates, "enable_cenceew", true,
                        "global.sources.enable_cenceew"),
                optionalBoolean(sourceGates, "enable_cq", true,
                        "global.sources.enable_cq"));

        ParsedTargets targets = parseTargets(root);
        ParsedNotifications notifications = parseNotifications(root, bundled);
        Map<String, BungeeConfigSnapshot.ServerSettings> servers = parseServers(root);
        return new BungeeConfigSnapshot(
                version,
                runtimeEnabled,
                gates,
                notifications.timeFormat,
                notifications.defaults,
                notifications.sources,
                targets.defaultTarget,
                targets.sourceTargets,
                targets.groups,
                servers);
    }

    private static Map<?, ?> loadMapping(Reader reader, String description)
            throws BungeeConfigException {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        Object document;
        try {
            document = new Yaml(new SafeConstructor(options)).load(reader);
        } catch (YAMLException error) {
            throw new BungeeConfigException("Malformed " + description, error);
        }
        if (!(document instanceof Map)) {
            throw new BungeeConfigException(description + " root must be a mapping");
        }
        return (Map<?, ?>) document;
    }

    private static ParsedNotifications parseNotifications(Map<?, ?> root, Map<?, ?> bundled)
            throws BungeeConfigException {
        Map<?, ?> userNotifications = optionalMapping(root, "notifications", "notifications");
        Map<?, ?> bundledNotifications = requireMapping(
                bundled, "notifications", "bundled notifications");
        rejectUnsupported(userNotifications, "notifications");

        String timeFormat = optionalStringWithFallback(
                userNotifications,
                bundledNotifications,
                "time_format",
                "notifications.time_format");
        try {
            DateTimeFormatter.ofPattern(timeFormat);
        } catch (IllegalArgumentException error) {
            throw new BungeeConfigException("notifications.time_format is invalid", error);
        }

        Map<?, ?> userDefaults = optionalMapping(
                userNotifications, "defaults", "notifications.defaults");
        Map<?, ?> bundledDefaults = requireMapping(
                bundledNotifications, "defaults", "bundled notifications.defaults");
        rejectUnsupported(userDefaults, "notifications.defaults");
        BungeeConfigSnapshot.ChannelPolicy defaults = new BungeeConfigSnapshot.ChannelPolicy(
                optionalBooleanWithFallback(
                        userDefaults, bundledDefaults, "broadcast",
                        "notifications.defaults.broadcast"),
                optionalBooleanWithFallback(
                        userDefaults, bundledDefaults, "title",
                        "notifications.defaults.title"));

        Map<?, ?> userSources = optionalMapping(
                userNotifications, "sources", "notifications.sources");
        Map<?, ?> bundledSources = requireMapping(
                bundledNotifications, "sources", "bundled notifications.sources");
        validateSourceKeys(userSources, "notifications.sources");
        Map<NotificationSource, BungeeConfigSnapshot.SourceSettings> sources =
                new EnumMap<>(NotificationSource.class);
        for (Map.Entry<String, NotificationSource> entry
                : BungeeNotificationSources.entries().entrySet()) {
            String key = entry.getKey();
            NotificationSource source = entry.getValue();
            String path = "notifications.sources." + key;
            Map<?, ?> userSource = optionalMapping(userSources, key, path);
            Map<?, ?> bundledSource = requireMapping(
                    bundledSources, key, "bundled " + path);
            rejectUnsupported(userSource, path);
            String message = optionalStringWithFallback(
                    userSource, bundledSource, "message", path + ".message");
            if (BungeeNotificationSources.isEarthquakeList(source)) {
                if (userSource.containsKey("channels")) {
                    throw new BungeeConfigException(
                            path + ".channels is not supported; use " + path + ".broadcast");
                }
                sources.put(source, new BungeeConfigSnapshot.SourceSettings(
                        message,
                        null,
                        null,
                        new BungeeConfigSnapshot.ChannelOverrides(
                                nullableBoolean(userSource, "broadcast", path + ".broadcast"),
                                null)));
                continue;
            }

            Map<?, ?> channels = optionalMapping(userSource, "channels", path + ".channels");
            rejectUnsupported(channels, path + ".channels");
            sources.put(source, new BungeeConfigSnapshot.SourceSettings(
                    message,
                    optionalStringWithFallback(userSource, bundledSource, "title",
                            path + ".title"),
                    optionalStringWithFallback(userSource, bundledSource, "subtitle",
                            path + ".subtitle"),
                    parseOverrides(channels, path + ".channels")));
        }
        return new ParsedNotifications(timeFormat, defaults, sources);
    }

    private static ParsedTargets parseTargets(Map<?, ?> root) throws BungeeConfigException {
        Map<?, ?> rawGroups = requireMapping(root, "groups", "groups");
        Map<String, Set<String>> groups = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : rawGroups.entrySet()) {
            String originalName = stringKey(entry.getKey(), "groups");
            String name = normalizeName(originalName);
            if (groups.containsKey(name)) {
                throw new BungeeConfigException(
                        "Duplicate group name after case normalization: " + originalName);
            }
            groups.put(name, parseNameSet(entry.getValue(), "groups." + originalName));
        }

        Map<?, ?> rawTargets = requireMapping(root, "targets", "targets");
        BungeeConfigSnapshot.TargetSpec defaultTarget = parseTargetSpec(
                requireMapping(rawTargets, "default", "targets.default"),
                "targets.default");
        Map<?, ?> rawSourceTargets = requireMapping(
                rawTargets, "sources", "targets.sources");
        validateSourceKeys(rawSourceTargets, "targets.sources");
        Map<NotificationSource, BungeeConfigSnapshot.TargetSpec> sourceTargets =
                new EnumMap<>(NotificationSource.class);
        for (Map.Entry<?, ?> entry : rawSourceTargets.entrySet()) {
            String key = stringKey(entry.getKey(), "targets.sources");
            sourceTargets.put(
                    BungeeNotificationSources.fromKey(key),
                    parseTargetSpec(
                            mappingValue(entry.getValue(), "targets.sources." + key),
                            "targets.sources." + key));
        }

        validateTargetGroups(defaultTarget, groups, "targets.default");
        for (Map.Entry<NotificationSource, BungeeConfigSnapshot.TargetSpec> entry
                : sourceTargets.entrySet()) {
            validateTargetGroups(
                    entry.getValue(),
                    groups,
                    "targets.sources." + BungeeNotificationSources.key(entry.getKey()));
        }
        return new ParsedTargets(defaultTarget, sourceTargets, groups);
    }

    private static BungeeConfigSnapshot.TargetSpec parseTargetSpec(Map<?, ?> raw, String path)
            throws BungeeConfigException {
        Object modeValue = raw.get("mode");
        if (!(modeValue instanceof String)) {
            throw new BungeeConfigException(
                    path + ".mode must be 'all', 'selected', or 'none'");
        }
        BungeeConfigSnapshot.TargetMode mode;
        switch (((String) modeValue).toLowerCase(Locale.ROOT)) {
            case "all":
                mode = BungeeConfigSnapshot.TargetMode.ALL;
                break;
            case "selected":
                mode = BungeeConfigSnapshot.TargetMode.SELECTED;
                break;
            case "none":
                mode = BungeeConfigSnapshot.TargetMode.NONE;
                break;
            default:
                throw new BungeeConfigException(
                        path + ".mode must be 'all', 'selected', or 'none'");
        }
        return new BungeeConfigSnapshot.TargetSpec(
                mode,
                optionalNameSet(raw, "servers", path + ".servers"),
                optionalNameSet(raw, "groups", path + ".groups"));
    }

    private static Map<String, BungeeConfigSnapshot.ServerSettings> parseServers(Map<?, ?> root)
            throws BungeeConfigException {
        Map<?, ?> rawServers = requireMapping(root, "servers", "servers");
        Map<String, BungeeConfigSnapshot.ServerSettings> servers = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : rawServers.entrySet()) {
            String originalName = stringKey(entry.getKey(), "servers");
            String name = normalizeName(originalName);
            if (servers.containsKey(name)) {
                throw new BungeeConfigException(
                        "Duplicate server name after case normalization: " + originalName);
            }
            String path = "servers." + originalName;
            Map<?, ?> raw = mappingValue(entry.getValue(), path);
            Map<?, ?> rawChannels = optionalMapping(
                    raw, "notifications", path + ".notifications");
            rejectUnsupported(rawChannels, path + ".notifications");
            BungeeConfigSnapshot.ChannelOverrides channels = parseOverrides(
                    rawChannels, path + ".notifications");
            Map<?, ?> rawSources = optionalMapping(raw, "sources", path + ".sources");
            validateSourceKeys(rawSources, path + ".sources");
            Map<NotificationSource, BungeeConfigSnapshot.ChannelOverrides> sourceChannels =
                    new EnumMap<>(NotificationSource.class);
            for (Map.Entry<?, ?> sourceEntry : rawSources.entrySet()) {
                String sourceKey = stringKey(sourceEntry.getKey(), path + ".sources");
                String sourcePath = path + ".sources." + sourceKey;
                Map<?, ?> sourceRaw = mappingValue(sourceEntry.getValue(), sourcePath);
                rejectUnsupported(sourceRaw, sourcePath);
                sourceChannels.put(
                        BungeeNotificationSources.fromKey(sourceKey),
                        parseOverrides(sourceRaw, sourcePath));
            }
            servers.put(name, new BungeeConfigSnapshot.ServerSettings(
                    channels, sourceChannels));
        }
        return servers;
    }

    private static BungeeConfigSnapshot.ChannelOverrides parseOverrides(
            Map<?, ?> raw,
            String path
    ) throws BungeeConfigException {
        rejectUnsupported(raw, path);
        return new BungeeConfigSnapshot.ChannelOverrides(
                nullableBoolean(raw, "broadcast", path + ".broadcast"),
                nullableBoolean(raw, "title", path + ".title"));
    }

    private static void rejectUnsupported(Map<?, ?> raw, String path)
            throws BungeeConfigException {
        if (raw.containsKey("alert")) {
            throw new BungeeConfigException(
                    path + ".alert is not supported on BungeeCord/Waterfall");
        }
        if (raw.containsKey("sound")) {
            throw new BungeeConfigException(
                    path + ".sound is not supported on BungeeCord/Waterfall");
        }
    }

    private static void validateSourceKeys(Map<?, ?> raw, String path)
            throws BungeeConfigException {
        for (Object rawKey : raw.keySet()) {
            String key = stringKey(rawKey, path);
            if (BungeeNotificationSources.fromKey(key) == null) {
                throw new BungeeConfigException(
                        "Unknown notification source key under " + path + "; expected one of "
                                + BungeeNotificationSources.entries().keySet());
            }
        }
    }

    private static void validateTargetGroups(
            BungeeConfigSnapshot.TargetSpec target,
            Map<String, Set<String>> groups,
            String path
    ) throws BungeeConfigException {
        for (String group : target.groups()) {
            if (!groups.containsKey(group)) {
                throw new BungeeConfigException(
                        path + ".groups references unknown group: " + group);
            }
        }
    }

    private static Map<?, ?> requireMapping(Map<?, ?> parent, String key, String path)
            throws BungeeConfigException {
        if (!parent.containsKey(key)) {
            throw new BungeeConfigException(path + " must be a mapping");
        }
        return mappingValue(parent.get(key), path);
    }

    private static Map<?, ?> optionalMapping(Map<?, ?> parent, String key, String path)
            throws BungeeConfigException {
        if (!parent.containsKey(key)) {
            return Map.of();
        }
        return mappingValue(parent.get(key), path);
    }

    private static Map<?, ?> mappingValue(Object value, String path)
            throws BungeeConfigException {
        if (!(value instanceof Map)) {
            throw new BungeeConfigException(path + " must be a mapping");
        }
        return (Map<?, ?>) value;
    }

    private static boolean optionalBoolean(
            Map<?, ?> parent,
            String key,
            boolean fallback,
            String path
    ) throws BungeeConfigException {
        if (!parent.containsKey(key)) {
            return fallback;
        }
        Object value = parent.get(key);
        if (!(value instanceof Boolean)) {
            throw new BungeeConfigException(path + " must be a boolean");
        }
        return (Boolean) value;
    }

    private static boolean optionalBooleanWithFallback(
            Map<?, ?> user,
            Map<?, ?> bundled,
            String key,
            String path
    ) throws BungeeConfigException {
        if (user.containsKey(key)) {
            return optionalBoolean(user, key, false, path);
        }
        return optionalBoolean(bundled, key, false, "bundled " + path);
    }

    private static Boolean nullableBoolean(Map<?, ?> parent, String key, String path)
            throws BungeeConfigException {
        if (!parent.containsKey(key)) {
            return null;
        }
        Object value = parent.get(key);
        if (!(value instanceof Boolean)) {
            throw new BungeeConfigException(path + " must be a boolean");
        }
        return (Boolean) value;
    }

    private static String optionalStringWithFallback(
            Map<?, ?> user,
            Map<?, ?> bundled,
            String key,
            String path
    ) throws BungeeConfigException {
        Object value = user.containsKey(key) ? user.get(key) : bundled.get(key);
        if (!(value instanceof String)) {
            throw new BungeeConfigException(path + " must be a string");
        }
        return (String) value;
    }

    private static Set<String> optionalNameSet(Map<?, ?> parent, String key, String path)
            throws BungeeConfigException {
        if (!parent.containsKey(key)) {
            return Set.of();
        }
        return parseNameSet(parent.get(key), path);
    }

    private static Set<String> parseNameSet(Object value, String path)
            throws BungeeConfigException {
        if (!(value instanceof List)) {
            throw new BungeeConfigException(path + " must be a list of strings");
        }
        Set<String> names = new LinkedHashSet<>();
        for (Object item : (List<?>) value) {
            if (!(item instanceof String) || ((String) item).trim().isEmpty()) {
                throw new BungeeConfigException(path + " must contain non-empty strings");
            }
            names.add(normalizeName((String) item));
        }
        return names;
    }

    private static String stringKey(Object key, String path) throws BungeeConfigException {
        if (!(key instanceof String) || ((String) key).trim().isEmpty()) {
            throw new BungeeConfigException(path + " keys must be non-empty strings");
        }
        return (String) key;
    }

    private static String normalizeName(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static final class ParsedNotifications {
        private final String timeFormat;
        private final BungeeConfigSnapshot.ChannelPolicy defaults;
        private final Map<NotificationSource, BungeeConfigSnapshot.SourceSettings> sources;

        private ParsedNotifications(
                String timeFormat,
                BungeeConfigSnapshot.ChannelPolicy defaults,
                Map<NotificationSource, BungeeConfigSnapshot.SourceSettings> sources
        ) {
            this.timeFormat = timeFormat;
            this.defaults = defaults;
            this.sources = sources;
        }
    }

    private static final class ParsedTargets {
        private final BungeeConfigSnapshot.TargetSpec defaultTarget;
        private final Map<NotificationSource, BungeeConfigSnapshot.TargetSpec> sourceTargets;
        private final Map<String, Set<String>> groups;

        private ParsedTargets(
                BungeeConfigSnapshot.TargetSpec defaultTarget,
                Map<NotificationSource, BungeeConfigSnapshot.TargetSpec> sourceTargets,
                Map<String, Set<String>> groups
        ) {
            this.defaultTarget = defaultTarget;
            this.sourceTargets = sourceTargets;
            this.groups = groups;
        }
    }
}
