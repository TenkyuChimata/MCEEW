package jp.wolfx.mceew;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigManagerTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void firstInstallCreatesTheCompleteCurrentConfigurationWithoutBackup() throws Exception {
        TestContext context = context(new FaultFileAccess());

        ConfigManager.PrepareResult result = context.manager.prepareConfig();
        YamlConfiguration config = load(context.configPath);

        assertEquals(ConfigManager.Outcome.CREATED, result.outcome());
        assertEquals(ConfigManager.CURRENT_CONFIG_VERSION, config.getInt("config-version"));
        assertTrue(config.isBoolean("enable_cq"));
        assertTrue(config.isString("Message.Chongqing.broadcast"));
        assertTrue(config.isString("Message.Chongqing.title"));
        assertTrue(config.isString("Message.Chongqing.subtitle"));
        assertTrue(config.isString("Sound.Chongqing.type"));
        assertTrue(config.isDouble("Sound.Chongqing.volume"));
        assertTrue(config.isDouble("Sound.Chongqing.pitch"));
        assertEquals(0, backupCount(context.dataDirectory));
    }

    @Test
    void v8UpgradePreservesCustomValuesAndAddsAllV9ChongqingDefaults() throws Exception {
        TestContext context = context(new FaultFileAccess());
        writeConfig(context.configPath, config -> {
            config.set("config-version", 8);
            config.set("enable_jp", false);
            config.set("enable_sc", false);
            config.set("time_format", "yyyy-MM-dd HH:mm:ss");
            config.set("Action.broadcast", false);
            config.set("Message.Alert.broadcast", "CUSTOM BROADCAST");
            config.set("Sound.Alert.type", "custom.sound");
            config.set("enable_cq", null);
            config.set("Message.Chongqing", null);
            config.set("Sound.Chongqing", null);
        });
        byte[] original = Files.readAllBytes(context.configPath);

        ConfigManager.PrepareResult result = context.manager.prepareConfig();
        YamlConfiguration upgraded = load(context.configPath);

        assertEquals(ConfigManager.Outcome.UPGRADED, result.outcome());
        assertEquals(9, upgraded.getInt("config-version"));
        assertFalse(upgraded.getBoolean("enable_jp"));
        assertFalse(upgraded.getBoolean("enable_sc"));
        assertEquals("yyyy-MM-dd HH:mm:ss", upgraded.getString("time_format"));
        assertFalse(upgraded.getBoolean("Action.broadcast"));
        assertEquals("CUSTOM BROADCAST", upgraded.getString("Message.Alert.broadcast"));
        assertEquals("custom.sound", upgraded.getString("Sound.Alert.type"));
        assertTrue(upgraded.getBoolean("enable_cq"));
        assertNotNull(upgraded.getString("Message.Chongqing.broadcast"));
        assertNotNull(upgraded.getString("Message.Chongqing.title"));
        assertNotNull(upgraded.getString("Message.Chongqing.subtitle"));
        assertNotNull(upgraded.getString("Sound.Chongqing.type"));
        assertTrue(upgraded.get("Sound.Chongqing.volume") instanceof Number);
        assertTrue(upgraded.get("Sound.Chongqing.pitch") instanceof Number);
        assertArrayEquals(original, Files.readAllBytes(context.dataDirectory.resolve("config.yml.v8.bak")));
    }

    @Test
    void migrationGapFailsWithoutChangingOrAdvancingKnownOldConfiguration() throws Exception {
        FaultFileAccess files = new FaultFileAccess();
        TestContext context = context(files);
        writeConfig(context.configPath, config -> {
            config.set("config-version", 7);
            config.set("enable_cq", null);
            config.set("custom-user-key", "preserve-me");
        });
        byte[] original = Files.readAllBytes(context.configPath);

        ConfigManager.ConfigPreparationException error = assertThrows(
                ConfigManager.ConfigPreparationException.class,
                context.manager::prepareConfig);

        assertTrue(error.stage().contains("missing required step v7 -> v8"));
        assertEquals(7, error.detectedVersion());
        assertArrayEquals(original, Files.readAllBytes(context.configPath));
        assertEquals(7, load(context.configPath).getInt("config-version"));
        assertEquals("preserve-me", load(context.configPath).getString("custom-user-key"));
        assertEquals(0, files.writeCount);
        assertEquals(0, files.copyCount);
        assertEquals(0, backupCount(context.dataDirectory));
        assertTrue(context.logs.contains("missing required step v7 -> v8"));
    }

    @Test
    void partialSectionPreservesCustomLeafAndRepairsOnlyMissingLeaves() throws Exception {
        TestContext context = context(new FaultFileAccess());
        YamlConfiguration defaults = defaults();
        writeConfig(context.configPath, config -> {
            config.set("Message.Chongqing.broadcast", "CUSTOM CHONGQING");
            config.set("Message.Chongqing.title", null);
            config.set("Message.Chongqing.subtitle", null);
        });

        ConfigManager.PrepareResult result = context.manager.prepareConfig();
        YamlConfiguration repaired = load(context.configPath);

        assertEquals(ConfigManager.Outcome.REPAIRED, result.outcome());
        assertEquals("CUSTOM CHONGQING", repaired.getString("Message.Chongqing.broadcast"));
        assertEquals(defaults.getString("Message.Chongqing.title"),
                repaired.getString("Message.Chongqing.title"));
        assertEquals(defaults.getString("Message.Chongqing.subtitle"),
                repaired.getString("Message.Chongqing.subtitle"));
    }

    @Test
    void currentConfigurationRepairsInvalidBooleanNumberAndStringTypes() throws Exception {
        TestContext context = context(new FaultFileAccess());
        YamlConfiguration defaults = defaults();
        writeConfig(context.configPath, config -> {
            config.set("enable_cq", "not-a-boolean");
            config.set("Sound.Chongqing.volume", "foo");
            config.set("Message.Chongqing.title", List.of("not", "a", "string"));
        });

        ConfigManager.PrepareResult result = context.manager.prepareConfig();
        YamlConfiguration repaired = load(context.configPath);

        assertEquals(ConfigManager.Outcome.REPAIRED, result.outcome());
        assertEquals(defaults.getBoolean("enable_cq"), repaired.getBoolean("enable_cq"));
        assertEquals(defaults.getDouble("Sound.Chongqing.volume"),
                repaired.getDouble("Sound.Chongqing.volume"));
        assertEquals(defaults.getString("Message.Chongqing.title"),
                repaired.getString("Message.Chongqing.title"));
    }

    @Test
    void currentVersionStillRepairsMissingValues() throws Exception {
        TestContext context = context(new FaultFileAccess());
        writeConfig(context.configPath, config -> config.set("enable_cq", null));

        ConfigManager.PrepareResult result = context.manager.prepareConfig();

        assertEquals(ConfigManager.Outcome.REPAIRED, result.outcome());
        assertTrue(load(context.configPath).getBoolean("enable_cq"));
        assertTrue(Files.exists(context.dataDirectory.resolve("config.yml.v9.bak")));
    }

    @Test
    void unknownUserKeysAndSectionsArePreserved() throws Exception {
        TestContext context = context(new FaultFileAccess());
        writeConfig(context.configPath, config -> {
            config.set("custom-user-key", "hello");
            config.set("SomePluginExtension.foo", "bar");
            config.set("Message.Alert.title", "CUSTOM TITLE");
            config.set("enable_cq", null);
        });

        context.manager.prepareConfig();
        YamlConfiguration repaired = load(context.configPath);

        assertEquals("hello", repaired.getString("custom-user-key"));
        assertEquals("bar", repaired.getString("SomePluginExtension.foo"));
        assertEquals("CUSTOM TITLE", repaired.getString("Message.Alert.title"));
    }

    @Test
    void invalidYamlIsBackedUpAndReplacedWithRunnableDefaults() throws Exception {
        TestContext context = context(new FaultFileAccess());
        byte[] invalid = "Action:\n  title: [unterminated\n".getBytes(StandardCharsets.UTF_8);
        Files.createDirectories(context.dataDirectory);
        Files.write(context.configPath, invalid);

        ConfigManager.PrepareResult result = context.manager.prepareConfig();
        Path backup = context.dataDirectory.resolve("config.yml.invalid.bak");

        assertEquals(ConfigManager.Outcome.RECOVERED_INVALID_YAML, result.outcome());
        assertArrayEquals(invalid, Files.readAllBytes(backup));
        assertEquals(9, load(context.configPath).getInt("config-version"));
        assertFalse(Files.exists(context.dataDirectory.resolve("config.yml.tmp")));
        assertFalse(Files.exists(context.dataDirectory.resolve("config.yml.invalid.bak.tmp")));
        assertTrue(context.logs.contains("invalid and was repaired automatically"));
        assertTrue(context.logs.contains("config.yml.invalid.bak"));
    }

    @Test
    void backupFailureLeavesOriginalAndTemporaryFilesUntouched() throws Exception {
        FaultFileAccess files = new FaultFileAccess();
        files.failCopyAfterWrite = true;
        TestContext context = context(files);
        writeConfig(context.configPath, config -> config.set("enable_cq", null));
        byte[] original = Files.readAllBytes(context.configPath);

        ConfigManager.ConfigPreparationException error = assertThrows(
                ConfigManager.ConfigPreparationException.class,
                context.manager::prepareConfig);

        assertEquals("backup creation", error.stage());
        assertArrayEquals(original, Files.readAllBytes(context.configPath));
        assertFalse(Files.exists(context.dataDirectory.resolve("config.yml.tmp")));
        assertFalse(Files.exists(context.dataDirectory.resolve("config.yml.v9.bak.tmp")));
    }

    @Test
    void writeFailureLeavesOriginalConfigAndUsableBackup() throws Exception {
        FaultFileAccess files = new FaultFileAccess();
        files.failWriteAfterWrite = true;
        TestContext context = context(files);
        writeConfig(context.configPath, config -> {
            config.set("config-version", 8);
            config.set("enable_cq", null);
        });
        byte[] original = Files.readAllBytes(context.configPath);

        ConfigManager.ConfigPreparationException error = assertThrows(
                ConfigManager.ConfigPreparationException.class,
                context.manager::prepareConfig);

        assertEquals("atomic configuration write", error.stage());
        assertArrayEquals(original, Files.readAllBytes(context.configPath));
        assertArrayEquals(original,
                Files.readAllBytes(context.dataDirectory.resolve("config.yml.v8.bak")));
        assertFalse(Files.exists(context.dataDirectory.resolve("config.yml.tmp")));
    }

    @Test
    void upgradeIsIdempotentAndSecondStartupDoesNotWriteOrBackupAgain() throws Exception {
        FaultFileAccess files = new FaultFileAccess();
        TestContext context = context(files);
        writeConfig(context.configPath, config -> {
            config.set("config-version", 8);
            config.set("enable_cq", null);
        });

        ConfigManager.PrepareResult first = context.manager.prepareConfig();
        int writesAfterUpgrade = files.writeCount;
        int copiesAfterUpgrade = files.copyCount;
        byte[] upgraded = Files.readAllBytes(context.configPath);
        ConfigManager.PrepareResult second = context.manager.prepareConfig();

        assertEquals(ConfigManager.Outcome.UPGRADED, first.outcome());
        assertEquals(ConfigManager.Outcome.UNCHANGED, second.outcome());
        assertEquals(writesAfterUpgrade, files.writeCount);
        assertEquals(copiesAfterUpgrade, files.copyCount);
        assertArrayEquals(upgraded, Files.readAllBytes(context.configPath));
        assertEquals(1, backupCount(context.dataDirectory));
    }

    @Test
    void repeatedPreparationOfValidConfigPerformsNoWritesOrBackups() throws Exception {
        FaultFileAccess files = new FaultFileAccess();
        TestContext context = context(files);
        writeConfig(context.configPath, config -> {
        });

        assertEquals(ConfigManager.Outcome.UNCHANGED,
                context.manager.prepareConfig().outcome());
        assertEquals(ConfigManager.Outcome.UNCHANGED,
                context.manager.prepareConfig().outcome());
        assertEquals(0, files.writeCount);
        assertEquals(0, files.copyCount);
        assertEquals(0, backupCount(context.dataDirectory));
    }

    @Test
    void missingVersionUsesGenericRepairAndPreservesUserValues() throws Exception {
        TestContext context = context(new FaultFileAccess());
        writeConfig(context.configPath, config -> {
            config.set("config-version", null);
            config.set("Message.Alert.title", "LEGACY CUSTOM TITLE");
            config.set("enable_cq", null);
        });

        ConfigManager.PrepareResult result = context.manager.prepareConfig();
        YamlConfiguration upgraded = load(context.configPath);

        assertEquals(ConfigManager.Outcome.UPGRADED, result.outcome());
        assertEquals(9, upgraded.getInt("config-version"));
        assertEquals("LEGACY CUSTOM TITLE", upgraded.getString("Message.Alert.title"));
        assertTrue(upgraded.getBoolean("enable_cq"));
        assertTrue(Files.exists(context.dataDirectory.resolve("config.yml.legacy.bak")));
    }

    @Test
    void futureVersionIsValidatedButNeverDowngradedOrRewritten() throws Exception {
        FaultFileAccess files = new FaultFileAccess();
        TestContext context = context(files);
        writeConfig(context.configPath, config -> {
            config.set("config-version", 10);
            config.set("future-setting", "keep-me");
        });
        byte[] original = Files.readAllBytes(context.configPath);

        ConfigManager.PrepareResult result = context.manager.prepareConfig();

        assertEquals(ConfigManager.Outcome.FUTURE_VERSION, result.outcome());
        assertArrayEquals(original, Files.readAllBytes(context.configPath));
        assertEquals(10, load(context.configPath).getInt("config-version"));
        assertEquals("keep-me", load(context.configPath).getString("future-setting"));
        assertEquals(0, files.writeCount);
        assertEquals(0, backupCount(context.dataDirectory));
        assertTrue(context.logs.contains("newer than supported v9"));
    }

    @Test
    void incompatibleFutureVersionFailsWithoutChangingTheFile() throws Exception {
        TestContext context = context(new FaultFileAccess());
        writeConfig(context.configPath, config -> {
            config.set("config-version", 10);
            config.set("Message.Chongqing.title", null);
        });
        byte[] original = Files.readAllBytes(context.configPath);

        ConfigManager.ConfigPreparationException error = assertThrows(
                ConfigManager.ConfigPreparationException.class,
                context.manager::prepareConfig);

        assertEquals("future-version compatibility validation", error.stage());
        assertArrayEquals(original, Files.readAllBytes(context.configPath));
        assertEquals(0, backupCount(context.dataDirectory));
    }

    @Test
    void numericWrapperDifferencesAreCompatibleAndDoNotCauseRepair() throws Exception {
        FaultFileAccess files = new FaultFileAccess();
        TestContext context = context(files);
        writeConfig(context.configPath, config -> {
            config.set("Sound.Chongqing.volume", 1);
            config.set("Sound.Chongqing.pitch", 1L);
        });
        byte[] original = Files.readAllBytes(context.configPath);

        ConfigManager.PrepareResult result = context.manager.prepareConfig();

        assertEquals(ConfigManager.Outcome.UNCHANGED, result.outcome());
        assertArrayEquals(original, Files.readAllBytes(context.configPath));
        assertEquals(0, files.writeCount);
        assertEquals(1.0, load(context.configPath).getDouble("Sound.Chongqing.volume"));
        assertEquals(1.0, load(context.configPath).getDouble("Sound.Chongqing.pitch"));
    }

    private TestContext context(FaultFileAccess files) throws IOException {
        Path dataDirectory = temporaryDirectory.resolve("MCEEW-" + System.nanoTime());
        CapturingHandler logs = new CapturingHandler();
        Logger logger = Logger.getLogger(getClass().getName() + System.nanoTime());
        logger.setUseParentHandlers(false);
        logger.setLevel(Level.ALL);
        logger.addHandler(logs);
        byte[] defaults = defaultBytes();
        ConfigManager manager = new ConfigManager(
                dataDirectory,
                () -> new ByteArrayInputStream(defaults),
                logger,
                files
        );
        return new TestContext(dataDirectory, manager, logs);
    }

    private byte[] defaultBytes() throws IOException {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("config.yml")) {
            if (input == null) {
                throw new IOException("config.yml test resource not found");
            }
            return input.readAllBytes();
        }
    }

    private YamlConfiguration defaults() throws IOException, InvalidConfigurationException {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.loadFromString(new String(defaultBytes(), StandardCharsets.UTF_8));
        return configuration;
    }

    private void writeConfig(Path path, Consumer<YamlConfiguration> changes) throws Exception {
        YamlConfiguration configuration = defaults();
        changes.accept(configuration);
        Files.createDirectories(path.getParent());
        Files.write(path, configuration.saveToString().getBytes(StandardCharsets.UTF_8));
    }

    private YamlConfiguration load(Path path) throws Exception {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.load(path.toFile());
        return configuration;
    }

    private long backupCount(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return 0;
        }
        try (Stream<Path> files = Files.list(directory)) {
            return files.filter(path -> path.getFileName().toString().endsWith(".bak")).count();
        }
    }

    private static final class TestContext {
        private final Path dataDirectory;
        private final Path configPath;
        private final ConfigManager manager;
        private final CapturingHandler logs;

        private TestContext(
                Path dataDirectory, ConfigManager manager, CapturingHandler logs) {
            this.dataDirectory = dataDirectory;
            this.configPath = dataDirectory.resolve("config.yml");
            this.manager = manager;
            this.logs = logs;
        }
    }

    private static final class FaultFileAccess implements ConfigManager.FileAccess {
        private final ConfigManager.NioFileAccess delegate = new ConfigManager.NioFileAccess();
        private boolean failCopyAfterWrite;
        private boolean failWriteAfterWrite;
        private int copyCount;
        private int writeCount;

        @Override
        public boolean exists(Path path) {
            return delegate.exists(path);
        }

        @Override
        public void createDirectories(Path directory) throws IOException {
            delegate.createDirectories(directory);
        }

        @Override
        public byte[] read(Path path) throws IOException {
            return delegate.read(path);
        }

        @Override
        public void writeAndSync(Path path, byte[] contents) throws IOException {
            writeCount++;
            delegate.writeAndSync(path, contents);
            if (failWriteAfterWrite) {
                throw new IOException("simulated write failure");
            }
        }

        @Override
        public void copy(Path source, Path target) throws IOException {
            copyCount++;
            delegate.copy(source, target);
            if (failCopyAfterWrite) {
                throw new IOException("simulated backup failure");
            }
        }

        @Override
        public void replace(Path source, Path target) throws IOException {
            delegate.replace(source, target);
        }

        @Override
        public void deleteIfExists(Path path) throws IOException {
            delegate.deleteIfExists(path);
        }
    }

    private static final class CapturingHandler extends Handler {
        private final List<String> messages = new ArrayList<>();

        @Override
        public void publish(LogRecord record) {
            messages.add(record.getMessage());
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }

        private boolean contains(String text) {
            for (String message : messages) {
                if (message.contains(text)) {
                    return true;
                }
            }
            return false;
        }
    }
}
