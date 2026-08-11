package jp.wolfx.mceew;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Prepares config.yml before Bukkit exposes it to the plugin runtime.
 */
final class ConfigManager {
    static final int CURRENT_CONFIG_VERSION = 9;
    private static final String CONFIG_NAME = "config.yml";
    private static final String LEGACY_SOUND_DEFAULT = "BLOCK_NOTE_BLOCK_PLING";
    private static final String CURRENT_SOUND_DEFAULT = "block.note_block.pling";
    private static final Pattern CURRENT_SOUND_KEY = Pattern.compile(
            "(?:[a-z0-9._-]+:)?[a-z0-9/._-]+"
    );
    private static final List<String> HISTORICAL_SOUND_PATHS = List.of(
            "Sound.type",
            "Sound.Alert.type",
            "Sound.Forecast.type",
            "Sound.Sichuan.type",
            "Sound.Taiwan.type",
            "Sound.Fjea.type",
            "Sound.Cwa.type"
    );
    private static final List<String> HISTORICAL_RUNTIME_SOUND_PATHS = List.of(
            "Sound.Alert.type",
            "Sound.Forecast.type",
            "Sound.Sichuan.type",
            "Sound.Fjea.type",
            "Sound.Cwa.type"
    );

    @FunctionalInterface
    interface DefaultsProvider {
        InputStream open() throws IOException;
    }

    interface FileAccess {
        boolean exists(Path path);

        void createDirectories(Path directory) throws IOException;

        byte[] read(Path path) throws IOException;

        void writeAndSync(Path path, byte[] contents) throws IOException;

        void copy(Path source, Path target) throws IOException;

        void replace(Path source, Path target) throws IOException;

        void deleteIfExists(Path path) throws IOException;
    }

    @FunctionalInterface
    private interface Migration {
        boolean apply(YamlConfiguration configuration);
    }

    enum Outcome {
        CREATED,
        UNCHANGED,
        UPGRADED,
        REPAIRED,
        RECOVERED_INVALID_YAML,
        FUTURE_VERSION
    }

    private enum LegacyLayout {
        FLAT_JAPAN_EEW,
        STRUCTURED_MULTI_SOURCE
    }

    static final class PrepareResult {
        private final Outcome outcome;
        private final Integer originalVersion;
        private final int repairedValues;
        private final Path backup;

        private PrepareResult(
                Outcome outcome, Integer originalVersion, int repairedValues, Path backup) {
            this.outcome = outcome;
            this.originalVersion = originalVersion;
            this.repairedValues = repairedValues;
            this.backup = backup;
        }

        Outcome outcome() {
            return outcome;
        }

        Integer originalVersion() {
            return originalVersion;
        }

        int repairedValues() {
            return repairedValues;
        }

        Path backup() {
            return backup;
        }
    }

    static final class ConfigPreparationException extends Exception {
        private final String stage;
        private final Integer detectedVersion;
        private final Path backup;

        private ConfigPreparationException(
                String stage, Integer detectedVersion, Path backup, Throwable cause) {
            super("Configuration preparation failed during " + stage, cause);
            this.stage = stage;
            this.detectedVersion = detectedVersion;
            this.backup = backup;
        }

        String stage() {
            return stage;
        }

        Integer detectedVersion() {
            return detectedVersion;
        }

        Path backup() {
            return backup;
        }
    }

    private final Path dataDirectory;
    private final Path configPath;
    private final DefaultsProvider defaultsProvider;
    private final Logger logger;
    private final FileAccess files;
    private final Map<Integer, Migration> migrations = new LinkedHashMap<>();

    static ConfigManager forPlugin(JavaPlugin plugin) {
        Objects.requireNonNull(plugin, "plugin");
        return new ConfigManager(
                plugin.getDataFolder().toPath(),
                () -> {
                    InputStream defaults = plugin.getResource(CONFIG_NAME);
                    if (defaults == null) {
                        throw new IOException("Bundled " + CONFIG_NAME + " was not found");
                    }
                    return defaults;
                },
                plugin.getLogger(),
                new NioFileAccess()
        );
    }

    ConfigManager(
            Path dataDirectory,
            DefaultsProvider defaultsProvider,
            Logger logger,
            FileAccess files
    ) {
        this.dataDirectory = Objects.requireNonNull(dataDirectory, "dataDirectory");
        this.configPath = dataDirectory.resolve(CONFIG_NAME);
        this.defaultsProvider = Objects.requireNonNull(defaultsProvider, "defaultsProvider");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.files = Objects.requireNonNull(files, "files");
        migrations.put(1, this::migrate1To2);
        migrations.put(2, this::migrate2To3);
        migrations.put(3, this::migrate3To4);
        migrations.put(4, this::migrate4To5);
        migrations.put(5, this::migrate5To6);
        migrations.put(6, this::migrate6To7);
        migrations.put(7, this::migrate7To8);
        migrations.put(8, this::migrate8To9);
    }

    synchronized PrepareResult prepareConfig() throws ConfigPreparationException {
        Defaults defaults = loadDefaults();
        try {
            files.createDirectories(dataDirectory);
        } catch (IOException error) {
            throw failure("data-directory creation", null, null, error);
        }

        if (!files.exists(configPath)) {
            atomicWrite(configPath, defaults.bytes, null, null);
            return new PrepareResult(Outcome.CREATED, null, 0, null);
        }

        byte[] original;
        try {
            original = files.read(configPath);
        } catch (IOException error) {
            throw failure("configuration read", null, null, error);
        }

        YamlConfiguration user;
        try {
            user = parse(original);
        } catch (InvalidConfigurationException error) {
            Path backup = backupPath(null, true);
            createBackup(backup, null);
            atomicWrite(configPath, defaults.bytes, null, backup);
            logger.warning("Configuration YAML was invalid and was repaired automatically. Backup: "
                    + backup.getFileName());
            return new PrepareResult(Outcome.RECOVERED_INVALID_YAML, null, 0, backup);
        }

        Object rawVersion = user.get("config-version", null);
        Integer originalVersion = readVersion(rawVersion);
        if (rawVersion != null && originalVersion == null) {
            throw failure("config-version validation", null, null,
                    new IllegalStateException(
                            "config-version must be a non-negative whole number"));
        }
        if (originalVersion != null && originalVersion > CURRENT_CONFIG_VERSION) {
            int invalidValues = countInvalidValues(user, defaults.configuration, true);
            if (invalidValues > 0) {
                IllegalStateException incompatible = new IllegalStateException(
                        "Newer configuration is missing or has invalid current-version fields");
                throw failure("future-version compatibility validation",
                        originalVersion, null, incompatible);
            }
            logger.warning("Configuration version v" + originalVersion
                    + " is newer than supported v" + CURRENT_CONFIG_VERSION
                    + "; the file was left unchanged.");
            return new PrepareResult(Outcome.FUTURE_VERSION, originalVersion, 0, null);
        }

        boolean migrationChanged = false;
        LegacyLayout legacyLayout = null;
        int migrationStart = originalVersion == null ? 1 : originalVersion;
        if (originalVersion == null) {
            legacyLayout = detectLegacyLayout(user);
            if (legacyLayout == null) {
                throw failure("legacy configuration schema detection", null, null,
                        new IllegalStateException(
                                "The unversioned configuration does not match a supported "
                                        + "historical bundled schema"));
            }
        }
        if (migrationStart < CURRENT_CONFIG_VERSION) {
            validateMigrationChain(migrationStart);
            validateHistoricalSoundValues(user, migrationStart, originalVersion);
            if (legacyLayout != null) {
                migrationChanged = applyLegacyMigration(user, legacyLayout);
            }
            migrationChanged |= applyMigrations(user, migrationStart);
        }
        int repairedValues = repairFromDefaults(user, defaults.configuration);
        boolean versionChanged = originalVersion == null
                || originalVersion != CURRENT_CONFIG_VERSION;
        if (versionChanged) {
            user.set("config-version", CURRENT_CONFIG_VERSION);
        }

        if (!migrationChanged && repairedValues == 0 && !versionChanged) {
            return new PrepareResult(Outcome.UNCHANGED, originalVersion, 0, null);
        }

        Path backup = backupPath(originalVersion, false);
        createBackup(backup, originalVersion);
        byte[] migrated;
        try {
            migrated = user.saveToString().getBytes(StandardCharsets.UTF_8);
        } catch (RuntimeException error) {
            throw failure("configuration serialization", originalVersion, backup, error);
        }
        atomicWrite(configPath, migrated, originalVersion, backup);

        Outcome outcome = versionChanged ? Outcome.UPGRADED : Outcome.REPAIRED;
        if (versionChanged) {
            String from = originalVersion == null ? "an unknown legacy version" : "v" + originalVersion;
            logger.info("Configuration upgraded automatically from " + from + " to v"
                    + CURRENT_CONFIG_VERSION + ". Backup: " + backup.getFileName());
        } else {
            logger.info("Configuration repaired automatically. Backup: " + backup.getFileName());
        }
        return new PrepareResult(outcome, originalVersion, repairedValues, backup);
    }

    private Defaults loadDefaults() throws ConfigPreparationException {
        byte[] bytes;
        try (InputStream input = defaultsProvider.open()) {
            if (input == null) {
                throw new IOException("Bundled " + CONFIG_NAME + " provider returned null");
            }
            bytes = input.readAllBytes();
        } catch (IOException error) {
            throw failure("bundled defaults read", null, null, error);
        }

        YamlConfiguration configuration;
        try {
            configuration = parse(bytes);
        } catch (InvalidConfigurationException error) {
            throw failure("bundled defaults parse", null, null, error);
        }
        Integer version = readVersion(configuration.get("config-version", null));
        if (version == null || version != CURRENT_CONFIG_VERSION) {
            throw failure("bundled defaults version validation", version, null,
                    new IllegalStateException("Bundled config-version must be "
                            + CURRENT_CONFIG_VERSION));
        }
        return new Defaults(bytes, configuration);
    }

    private YamlConfiguration parse(byte[] contents) throws InvalidConfigurationException {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.loadFromString(new String(contents, StandardCharsets.UTF_8));
        return configuration;
    }

    private Integer readVersion(Object value) {
        if (!(value instanceof Number)) {
            return null;
        }
        Number number = (Number) value;
        double numeric = number.doubleValue();
        int integer = number.intValue();
        if (!Double.isFinite(numeric) || numeric != integer || integer < 0) {
            return null;
        }
        return integer;
    }

    private LegacyLayout detectLegacyLayout(YamlConfiguration configuration) {
        boolean flatJapanEew = hasAll(configuration,
                "EEW",
                "time_format",
                "Action.broadcast",
                "Action.title",
                "Action.alert",
                "Action.notification",
                "Message.broadcast",
                "Message.title",
                "Message.subtitle",
                "Sound.type",
                "Sound.volume",
                "Sound.pitch"
        );

        boolean structuredMultiSource = hasAll(configuration,
                "EEW",
                "enable_jma",
                "enable_sc",
                "time_format",
                "time_format_final",
                "Action.final",
                "Message.Alert.broadcast",
                "Message.Forecast.broadcast",
                "Message.Final.broadcast",
                "Message.Sichuan.broadcast",
                "Color.Shindo",
                "Color.Intensity",
                "Sound.Alert.type",
                "Sound.Forecast.type",
                "Sound.Sichuan.type",
                "Version.2.0.0"
        );

        if (flatJapanEew == structuredMultiSource) {
            return null;
        }
        return flatJapanEew
                ? LegacyLayout.FLAT_JAPAN_EEW
                : LegacyLayout.STRUCTURED_MULTI_SOURCE;
    }

    private boolean hasAll(YamlConfiguration configuration, String... paths) {
        for (String path : paths) {
            if (configuration.get(path, null) == null) {
                return false;
            }
        }
        return true;
    }

    private void validateHistoricalSoundValues(
            YamlConfiguration configuration,
            int migrationStart,
            Integer detectedVersion
    ) throws ConfigPreparationException {
        if (migrationStart > 6) {
            return;
        }
        for (String path : HISTORICAL_RUNTIME_SOUND_PATHS) {
            validateHistoricalSoundValue(configuration, path, detectedVersion);
        }
        if (configuration.get("Sound.Alert.type", null) == null
                || configuration.get("Sound.Forecast.type", null) == null) {
            validateHistoricalSoundValue(configuration, "Sound.type", detectedVersion);
        }
        if (configuration.get("Sound.Cwa.type", null) == null) {
            validateHistoricalSoundValue(configuration, "Sound.Taiwan.type", detectedVersion);
        }
    }

    private void validateHistoricalSoundValue(
            YamlConfiguration configuration,
            String path,
            Integer detectedVersion
    ) throws ConfigPreparationException {
        Object value = configuration.get(path, null);
        if (!(value instanceof String)) {
            return;
        }
        String sound = (String) value;
        if (LEGACY_SOUND_DEFAULT.equals(sound)
                || CURRENT_SOUND_KEY.matcher(sound).matches()) {
            return;
        }
        throw failure("v6 -> v7 sound migration validation", detectedVersion, null,
                new IllegalStateException(
                        "Historical sound value at " + path
                                + " cannot be converted safely: " + sound));
    }

    private void validateMigrationChain(int version) throws ConfigPreparationException {
        for (int from = version; from < CURRENT_CONFIG_VERSION; from++) {
            if (!migrations.containsKey(from)) {
                String missingStep = "v" + from + " -> v" + (from + 1);
                throw failure(
                        "migration chain validation; missing required step " + missingStep,
                        version,
                        null,
                        new IllegalStateException(
                                "Missing configuration migration step " + missingStep)
                );
            }
        }
    }

    private boolean applyMigrations(YamlConfiguration configuration, int version) {
        boolean changed = false;
        for (int from = version; from < CURRENT_CONFIG_VERSION; from++) {
            changed |= migrations.get(from).apply(configuration);
        }
        return changed;
    }

    private boolean applyLegacyMigration(
            YamlConfiguration configuration, LegacyLayout layout) {
        if (layout == LegacyLayout.FLAT_JAPAN_EEW) {
            boolean changed = copyIfAbsent(configuration, "EEW", "enable_jp");
            changed |= copyIfAbsent(configuration,
                    "Message.broadcast", "Message.Alert.broadcast");
            changed |= copyIfAbsent(configuration,
                    "Message.broadcast", "Message.Forecast.broadcast");
            changed |= copyIfAbsent(configuration,
                    "Message.title", "Message.Alert.title");
            changed |= copyIfAbsent(configuration,
                    "Message.title", "Message.Forecast.title");
            changed |= copyIfAbsent(configuration,
                    "Message.subtitle", "Message.Alert.subtitle");
            changed |= copyIfAbsent(configuration,
                    "Message.subtitle", "Message.Forecast.subtitle");
            changed |= copyIfAbsent(configuration,
                    "Sound.type", "Sound.Alert.type");
            changed |= copyIfAbsent(configuration,
                    "Sound.type", "Sound.Forecast.type");
            changed |= copyIfAbsent(configuration,
                    "Sound.volume", "Sound.Alert.volume");
            changed |= copyIfAbsent(configuration,
                    "Sound.volume", "Sound.Forecast.volume");
            changed |= copyIfAbsent(configuration,
                    "Sound.pitch", "Sound.Alert.pitch");
            changed |= copyIfAbsent(configuration,
                    "Sound.pitch", "Sound.Forecast.pitch");
            return changed;
        }
        return copyIfAbsent(configuration, "EEW", "enable_jp");
    }

    private boolean migrate1To2(YamlConfiguration configuration) {
        boolean jmaPathExisted = configuration.get("Action.jma", null) != null;
        boolean changed = copyIfAbsent(configuration, "Action.final", "Action.jma");
        changed |= copyIfAbsent(configuration,
                "Message.Final.broadcast", "Message.Jma.broadcast");
        changed |= copyIfAbsent(configuration, "enable_cwb", "enable_cwa");

        Object globalValue = configuration.get("EEW", null);
        if (globalValue instanceof Boolean) {
            boolean globalEnabled = (Boolean) globalValue;
            changed |= applyBooleanGate(configuration, "enable_jp", globalEnabled, true);
            changed |= applyBooleanGate(configuration, "enable_sc", globalEnabled, false);
            if (!jmaPathExisted) {
                changed |= applyBooleanGate(
                        configuration, "Action.jma", globalEnabled, false);
            }
        }
        return changed;
    }

    private boolean migrate2To3(YamlConfiguration configuration) {
        // v3 only changes bundled message defaults.
        return false;
    }

    private boolean migrate3To4(YamlConfiguration configuration) {
        // v4 replaces the removed CWA source with a distinct Fujian source. It is not a rename.
        return false;
    }

    private boolean migrate4To5(YamlConfiguration configuration) {
        // CWA is added back in v5. Preserve CWA values retained from v1-v3 configurations.
        boolean changed = copyIfAbsent(configuration,
                "Message.Taiwan.broadcast", "Message.Cwa.broadcast");
        changed |= copyIfAbsent(configuration,
                "Message.Taiwan.title", "Message.Cwa.title");
        changed |= copyIfAbsent(configuration,
                "Message.Taiwan.subtitle", "Message.Cwa.subtitle");
        changed |= copyIfAbsent(configuration,
                "Sound.Taiwan.type", "Sound.Cwa.type");
        changed |= copyIfAbsent(configuration,
                "Sound.Taiwan.volume", "Sound.Cwa.volume");
        changed |= copyIfAbsent(configuration,
                "Sound.Taiwan.pitch", "Sound.Cwa.pitch");
        return changed;
    }

    private boolean migrate5To6(YamlConfiguration configuration) {
        // v6 only changes the bundled CWA message default.
        return false;
    }

    private boolean migrate6To7(YamlConfiguration configuration) {
        boolean changed = false;
        for (String path : HISTORICAL_SOUND_PATHS) {
            if (LEGACY_SOUND_DEFAULT.equals(configuration.get(path, null))) {
                configuration.set(path, CURRENT_SOUND_DEFAULT);
                changed = true;
            }
        }
        return changed;
    }

    private boolean migrate7To8(YamlConfiguration configuration) {
        // v8 adds CENC EEW keys; generic recursive default repair supplies them.
        return false;
    }

    private boolean migrate8To9(YamlConfiguration configuration) {
        // v9 only adds keys; the generic recursive default repair supplies them.
        return false;
    }

    private boolean copyIfAbsent(
            YamlConfiguration configuration, String oldPath, String newPath) {
        Object oldValue = configuration.get(oldPath, null);
        if (oldValue == null || configuration.get(newPath, null) != null) {
            return false;
        }
        configuration.set(newPath, oldValue);
        return true;
    }

    private boolean applyBooleanGate(
            YamlConfiguration configuration,
            String path,
            boolean gate,
            boolean createWhenMissing
    ) {
        Object value = configuration.get(path, null);
        if (value == null) {
            if (createWhenMissing) {
                configuration.set(path, gate);
                return true;
            }
            return false;
        }
        if (!(value instanceof Boolean)) {
            if (!gate) {
                configuration.set(path, false);
                return true;
            }
            return false;
        }
        boolean converted = gate && (Boolean) value;
        if (converted == (Boolean) value) {
            return false;
        }
        configuration.set(path, converted);
        return true;
    }

    private int repairFromDefaults(
            YamlConfiguration user, YamlConfiguration defaults) {
        int repairs = 0;
        for (String path : defaults.getKeys(true)) {
            if (defaults.isConfigurationSection(path)) {
                continue;
            }
            Object defaultValue = defaults.get(path, null);
            Object userValue = user.get(path, null);
            if (userValue == null || !isCompatibleType(defaultValue, userValue)) {
                user.set(path, defaultValue);
                repairs++;
            }
        }
        return repairs;
    }

    private int countInvalidValues(
            YamlConfiguration user, YamlConfiguration defaults, boolean ignoreVersion) {
        int invalid = 0;
        for (String path : defaults.getKeys(true)) {
            if (defaults.isConfigurationSection(path)
                    || (ignoreVersion && path.equals("config-version"))) {
                continue;
            }
            Object defaultValue = defaults.get(path, null);
            Object userValue = user.get(path, null);
            if (userValue == null || !isCompatibleType(defaultValue, userValue)) {
                invalid++;
            }
        }
        return invalid;
    }

    private boolean isCompatibleType(Object defaultValue, Object userValue) {
        if (defaultValue instanceof Number) {
            return userValue instanceof Number;
        }
        if (defaultValue instanceof List) {
            return userValue instanceof List;
        }
        if (defaultValue instanceof ConfigurationSection) {
            return userValue instanceof ConfigurationSection;
        }
        return defaultValue != null && defaultValue.getClass().isInstance(userValue);
    }

    private Path backupPath(Integer version, boolean invalidYaml) {
        String suffix;
        if (invalidYaml) {
            suffix = "invalid";
        } else if (version == null) {
            suffix = "legacy";
        } else {
            suffix = "v" + version;
        }
        return dataDirectory.resolve(CONFIG_NAME + "." + suffix + ".bak");
    }

    private void createBackup(Path backup, Integer version) throws ConfigPreparationException {
        Path temporary = backup.resolveSibling(backup.getFileName() + ".tmp");
        try {
            files.deleteIfExists(temporary);
            files.copy(configPath, temporary);
            files.replace(temporary, backup);
        } catch (IOException error) {
            cleanupTemporary(temporary);
            throw failure("backup creation", version, null, error);
        }
    }

    private void atomicWrite(
            Path target, byte[] contents, Integer version, Path backup)
            throws ConfigPreparationException {
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
        try {
            files.deleteIfExists(temporary);
            files.writeAndSync(temporary, contents);
            files.replace(temporary, target);
        } catch (IOException error) {
            cleanupTemporary(temporary);
            throw failure("atomic configuration write", version, backup, error);
        }
    }

    private void cleanupTemporary(Path temporary) {
        try {
            files.deleteIfExists(temporary);
        } catch (IOException cleanupError) {
            logger.log(Level.WARNING,
                    "Unable to clean temporary configuration file " + temporary.getFileName(),
                    cleanupError);
        }
    }

    private ConfigPreparationException failure(
            String stage, Integer version, Path backup, Throwable error) {
        String detected = version == null ? "unknown" : "v" + version;
        String backupText = backup == null ? "none" : backup.getFileName().toString();
        logger.log(Level.SEVERE,
                "Configuration preparation failed during " + stage
                        + " (detected version: " + detected + ", backup: " + backupText
                        + "). The original configuration was not deliberately replaced.",
                error);
        return new ConfigPreparationException(stage, version, backup, error);
    }

    private static final class Defaults {
        private final byte[] bytes;
        private final YamlConfiguration configuration;

        private Defaults(byte[] bytes, YamlConfiguration configuration) {
            this.bytes = bytes;
            this.configuration = configuration;
        }
    }

    static final class NioFileAccess implements FileAccess {
        @Override
        public boolean exists(Path path) {
            return Files.exists(path);
        }

        @Override
        public void createDirectories(Path directory) throws IOException {
            Files.createDirectories(directory);
        }

        @Override
        public byte[] read(Path path) throws IOException {
            return Files.readAllBytes(path);
        }

        @Override
        public void writeAndSync(Path path, byte[] contents) throws IOException {
            try (FileChannel channel = FileChannel.open(
                    path,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE
            )) {
                ByteBuffer buffer = ByteBuffer.wrap(contents);
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                channel.force(true);
            }
        }

        @Override
        public void copy(Path source, Path target) throws IOException {
            Files.copy(source, target);
            try (FileChannel channel = FileChannel.open(target, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
        }

        @Override
        public void replace(Path source, Path target) throws IOException {
            try {
                Files.move(source, target,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
            }
        }

        @Override
        public void deleteIfExists(Path path) throws IOException {
            Files.deleteIfExists(path);
        }
    }
}
