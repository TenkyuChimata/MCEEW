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

/**
 * Prepares config.yml before Bukkit exposes it to the plugin runtime.
 */
final class ConfigManager {
    static final int CURRENT_CONFIG_VERSION = 9;
    private static final String CONFIG_NAME = "config.yml";

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

        Integer originalVersion = readVersion(user.get("config-version", null));
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
        if (originalVersion != null && originalVersion < CURRENT_CONFIG_VERSION) {
            validateMigrationChain(originalVersion);
            migrationChanged = applyMigrations(user, originalVersion);
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

    private boolean migrate8To9(YamlConfiguration configuration) {
        // v9 only adds keys; the generic recursive default repair supplies them.
        return false;
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
