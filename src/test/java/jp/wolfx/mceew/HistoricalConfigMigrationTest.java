package jp.wolfx.mceew;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HistoricalConfigMigrationTest {
    private static final List<HistoricalCase> HISTORICAL_CONFIGS = List.of(
            historical("legacy-flat", "v1.1.0", "legacy-flat-v1.1.0.yml", null,
                    "1666464405938be221f4c760df84bdd728679f9f623d0126a06aafa0a7252c8d",
                    "EEW", "enable_jp",
                    "Action.broadcast", "Action.broadcast",
                    "Message.broadcast", "Message.Alert.broadcast",
                    "Sound.volume", "Sound.Alert.volume"),
            historical("legacy-structured-earliest", "v2.0.0",
                    "legacy-structured-v2.0.0.yml", null,
                    "f44520b7684df587362a8b28f5caedebb998620940142519a3cf9e4a4e0ab975",
                    "EEW", "enable_jp",
                    "Action.final", "Action.jma",
                    "Message.Final.broadcast", "Message.Jma.broadcast",
                    "Sound.Alert.volume", "Sound.Alert.volume"),
            historical("legacy-structured", "v2.1.4",
                    "legacy-structured-v2.1.4.yml", null,
                    "327cdc482525bfbcba347cc112cef0b9ce2baf350797c6f52d7811fca4592f0e",
                    "EEW", "enable_jp",
                    "Action.final", "Action.jma",
                    "Message.Final.broadcast", "Message.Jma.broadcast",
                    "Sound.Alert.volume", "Sound.Alert.volume"),
            historical("v1-early", "v2.1.5", "v1-early-v2.1.5.yml", 1,
                    "b86d023740209100b7370121eb00b83219081b06613212496b41bcc958c41f7b",
                    "EEW", "enable_jp",
                    "Action.final", "Action.jma",
                    "Message.Final.broadcast", "Message.Jma.broadcast",
                    "Sound.Alert.volume", "Sound.Alert.volume"),
            historical("v1-late", "v2.2.3", "v1-late-v2.2.3.yml", 1,
                    "e2baf0cc40f63835f130a2e23e47fdc9d49941544da30f2fbfd809ef1e9790d6",
                    "enable_cwb", "enable_cwa",
                    "Action.final", "Action.jma",
                    "Message.Taiwan.broadcast", "Message.Cwa.broadcast",
                    "Sound.Taiwan.volume", "Sound.Cwa.volume"),
            historical("v2", "v2.3.0", "v2-v2.3.0.yml", 2,
                    "50f2db57a7a7cabcd4d97b2a2f3d4244f03438a642c4f84fc18d2cf3ee15c803",
                    "enable_cwa", "enable_cwa",
                    "Action.jma", "Action.jma",
                    "Message.Taiwan.broadcast", "Message.Cwa.broadcast",
                    "Sound.Taiwan.volume", "Sound.Cwa.volume"),
            historical("v3", "v2.3.1", "v3-v2.3.1.yml", 3,
                    "f3a1d62424969000d8c7c97e08facd4a5a0c2d513eacff6a040d3640a65e9dbf",
                    "enable_cwa", "enable_cwa",
                    "Action.jma", "Action.jma",
                    "Message.Taiwan.broadcast", "Message.Cwa.broadcast",
                    "Sound.Taiwan.volume", "Sound.Cwa.volume"),
            historical("v4", "v2.4.0", "v4-v2.4.0.yml", 4,
                    "76c1d3f8030c01fdc5af253e091d9a9c493c31c572ee6d4c59f09949bca1cfc7",
                    "enable_fj", "enable_fj",
                    "Action.jma", "Action.jma",
                    "Message.Fjea.broadcast", "Message.Fjea.broadcast",
                    "Sound.Fjea.volume", "Sound.Fjea.volume"),
            historical("v5", "v2.4.1", "v5-v2.4.1.yml", 5,
                    "e7b11d39c4f52e08f23a34c89bd9334a8bbe1881f672239a6332ba581c1786b6",
                    "enable_cwa", "enable_cwa",
                    "Action.cenc", "Action.cenc",
                    "Message.Cwa.broadcast", "Message.Cwa.broadcast",
                    "Sound.Cwa.volume", "Sound.Cwa.volume"),
            historical("v6", "v2.5.0", "v6-v2.5.0.yml", 6,
                    "4149620b7be8230770cf989e473353ef6a4cb233a96b87977023176f4ee5e748",
                    "enable_sc", "enable_sc",
                    "Action.cenc", "Action.cenc",
                    "Message.Sichuan.broadcast", "Message.Sichuan.broadcast",
                    "Sound.Sichuan.volume", "Sound.Sichuan.volume"),
            historical("v7", "v2.5.1", "v7-v2.5.1.yml", 7,
                    "112d11e8564e8dc24d98c84a1bc8237f420d83255415e10ccae030ef27f0b76c",
                    "enable_fj", "enable_fj",
                    "Action.jma", "Action.jma",
                    "Message.Fjea.broadcast", "Message.Fjea.broadcast",
                    "Sound.Fjea.volume", "Sound.Fjea.volume"),
            historical("v8", "v2.6.4", "v8-v2.6.4.yml", 8,
                    "59ed662b7aaa871a9d6e526df3b38861907eaef035b494c4e1436cd7360f8528",
                    "enable_cenceew", "enable_cenceew",
                    "Action.cenc", "Action.cenc",
                    "Message.CencEEW.broadcast", "Message.CencEEW.broadcast",
                    "Sound.CencEEW.volume", "Sound.CencEEW.volume")
    );

    @TempDir
    Path temporaryDirectory;

    @TestFactory
    Stream<DynamicTest> fixturesAreVerbatimTagConfigsApartFromRequiredTerminalNewline() {
        return HISTORICAL_CONFIGS.stream().map(historical -> DynamicTest.dynamicTest(
                historical.name + " fixture from " + historical.sourceTag,
                () -> assertEquals(historical.tagSha256,
                        sha256WithoutAddedTerminalNewline(resourceBytes(historical.resource)))
        ));
    }

    @TestFactory
    Stream<DynamicTest> pristineHistoricalConfigsUpgradeDirectlyToV9() {
        return HISTORICAL_CONFIGS.stream().map(historical -> DynamicTest.dynamicTest(
                historical.name + " pristine direct upgrade",
                () -> {
                    TestContext context = context(historical.name + "-pristine");
                    byte[] original = resourceBytes(historical.resource);
                    writeBytes(context.configPath, original);

                    ConfigManager.PrepareResult first = context.manager.prepareConfig();
                    YamlConfiguration upgraded = load(context.configPath);

                    assertEquals(ConfigManager.Outcome.UPGRADED, first.outcome());
                    assertEquals(historical.version, first.originalVersion());
                    assertCompleteCurrentConfiguration(upgraded);
                    assertEquals("block.note_block.pling",
                            upgraded.getString("Sound.Alert.type"));
                    Path backup = expectedBackup(context, historical.version);
                    assertArrayEquals(original, Files.readAllBytes(backup));
                    assertEquals(1, backupCount(context.dataDirectory));

                    byte[] firstOutput = Files.readAllBytes(context.configPath);
                    assertEquals(ConfigManager.Outcome.UNCHANGED,
                            context.manager.prepareConfig().outcome());
                    assertArrayEquals(firstOutput, Files.readAllBytes(context.configPath));
                    assertEquals(1, backupCount(context.dataDirectory));
                }
        ));
    }

    @TestFactory
    Stream<DynamicTest> customizedHistoricalConfigsPreserveEquivalentUserSemantics() {
        return HISTORICAL_CONFIGS.stream().map(historical -> DynamicTest.dynamicTest(
                historical.name + " customized direct upgrade",
                () -> {
                    TestContext context = context(historical.name + "-customized");
                    YamlConfiguration customized = loadResource(historical.resource);
                    String customMessage = "CUSTOM MESSAGE FROM " + historical.sourceTag;
                    customized.set(historical.enableOldPath, false);
                    customized.set(historical.actionOldPath, false);
                    customized.set(historical.messageOldPath, customMessage);
                    customized.set(historical.soundOldPath, 321.25);
                    customized.set("time_format", "yyyy/MM/dd HH:mm:ss 'audit'");
                    customized.set("LegacyExtension.keep", historical.sourceTag);
                    writeBytes(context.configPath,
                            customized.saveToString().getBytes(StandardCharsets.UTF_8));

                    ConfigManager.PrepareResult first = context.manager.prepareConfig();
                    YamlConfiguration upgraded = load(context.configPath);

                    assertEquals(ConfigManager.Outcome.UPGRADED, first.outcome());
                    assertCompleteCurrentConfiguration(upgraded);
                    assertFalse(upgraded.getBoolean(historical.enableNewPath));
                    assertFalse(upgraded.getBoolean(historical.actionNewPath));
                    assertEquals(customMessage,
                            upgraded.getString(historical.messageNewPath));
                    assertEquals(321.25,
                            upgraded.getDouble(historical.soundNewPath));
                    assertEquals("yyyy/MM/dd HH:mm:ss 'audit'",
                            upgraded.getString("time_format"));
                    assertEquals(historical.sourceTag,
                            upgraded.getString("LegacyExtension.keep"));
                    assertNotNull(first.backup());
                    assertTrue(Files.exists(first.backup()));

                    byte[] firstOutput = Files.readAllBytes(context.configPath);
                    assertEquals(ConfigManager.Outcome.UNCHANGED,
                            context.manager.prepareConfig().outcome());
                    assertArrayEquals(firstOutput, Files.readAllBytes(context.configPath));
                    assertEquals(1, backupCount(context.dataDirectory));
                }
        ));
    }

    @Test
    void existingCurrentPathsWinOverMigratedHistoricalValues() throws Exception {
        TestContext context = context("current-path-precedence");
        YamlConfiguration configuration = loadResource("v1-late-v2.2.3.yml");
        configuration.set("Action.final", false);
        configuration.set("Action.jma", true);
        configuration.set("Message.Final.broadcast", "OLD JMA VALUE");
        configuration.set("Message.Jma.broadcast", "CURRENT JMA VALUE");
        configuration.set("enable_cwb", false);
        configuration.set("enable_cwa", true);
        configuration.set("Message.Taiwan.broadcast", "OLD CWA VALUE");
        configuration.set("Message.Cwa.broadcast", "CURRENT CWA VALUE");
        configuration.set("Sound.Taiwan.type", "ENTITY_ZOMBIE_VILLAGER_AMBIENT");
        configuration.set("Sound.Cwa.type", "custom.current.sound");
        configuration.set("Sound.Taiwan.volume", 2.0);
        configuration.set("Sound.Cwa.volume", 3.0);
        writeBytes(context.configPath,
                configuration.saveToString().getBytes(StandardCharsets.UTF_8));

        context.manager.prepareConfig();
        YamlConfiguration upgraded = load(context.configPath);

        assertTrue(upgraded.getBoolean("Action.jma"));
        assertEquals("CURRENT JMA VALUE", upgraded.getString("Message.Jma.broadcast"));
        assertTrue(upgraded.getBoolean("enable_cwa"));
        assertEquals("CURRENT CWA VALUE", upgraded.getString("Message.Cwa.broadcast"));
        assertEquals("custom.current.sound", upgraded.getString("Sound.Cwa.type"));
        assertEquals(3.0, upgraded.getDouble("Sound.Cwa.volume"));
    }

    @Test
    void historicalGlobalEewFlagStillGatesEverySourceItControlled() throws Exception {
        TestContext context = context("historical-global-gate");
        YamlConfiguration configuration = loadResource("v1-early-v2.1.5.yml");
        configuration.set("EEW", false);
        configuration.set("enable_jp", "invalid subordinate flag");
        configuration.set("enable_sc", "invalid subordinate flag");
        configuration.set("Action.final", true);
        writeBytes(context.configPath,
                configuration.saveToString().getBytes(StandardCharsets.UTF_8));

        context.manager.prepareConfig();
        YamlConfiguration upgraded = load(context.configPath);

        assertFalse(upgraded.getBoolean("enable_jp"));
        assertFalse(upgraded.getBoolean("enable_sc"));
        assertFalse(upgraded.getBoolean("Action.jma"));
    }

    @Test
    void removedHistoricalKeysRemainAvailableAsUnknownUserData() throws Exception {
        TestContext context = context("removed-keys-preserved");
        writeBytes(context.configPath,
                resourceBytes("legacy-structured-v2.0.0.yml"));

        context.manager.prepareConfig();
        YamlConfiguration upgraded = load(context.configPath);

        assertTrue(upgraded.getBoolean("Updater"));
        assertTrue(upgraded.getBoolean("enable_jma"));
        assertNotNull(upgraded.getString("time_format_final"));
        assertFalse(upgraded.getBoolean("Version.2.0.0"));
    }

    @Test
    void historicalTaiwanValuesFollowCwaWhenItReturnsAndNeverBecomeFujianValues()
            throws Exception {
        TestContext context = context("taiwan-is-not-fujian");
        YamlConfiguration configuration = loadResource("v3-v2.3.1.yml");
        String taiwanMessage = "CUSTOM HISTORICAL TAIWAN MESSAGE";
        configuration.set("Message.Taiwan.broadcast", taiwanMessage);
        writeBytes(context.configPath,
                configuration.saveToString().getBytes(StandardCharsets.UTF_8));

        context.manager.prepareConfig();
        YamlConfiguration upgraded = load(context.configPath);

        assertEquals(taiwanMessage, upgraded.getString("Message.Cwa.broadcast"));
        assertFalse(taiwanMessage.equals(upgraded.getString("Message.Fjea.broadcast")));
    }

    @Test
    void unknownLegacySoundEnumFailsBeforeMutationRatherThanGuessingAResourceKey()
            throws Exception {
        TestContext context = context("unknown-sound");
        YamlConfiguration configuration = loadResource("v6-v2.5.0.yml");
        configuration.set("Sound.Alert.type", "ENTITY_ZOMBIE_VILLAGER_AMBIENT");
        writeBytes(context.configPath,
                configuration.saveToString().getBytes(StandardCharsets.UTF_8));
        byte[] original = Files.readAllBytes(context.configPath);

        ConfigManager.ConfigPreparationException error = assertThrows(
                ConfigManager.ConfigPreparationException.class,
                context.manager::prepareConfig);

        assertEquals("v6 -> v7 sound migration validation", error.stage());
        assertArrayEquals(original, Files.readAllBytes(context.configPath));
        assertEquals(0, backupCount(context.dataDirectory));
    }

    @Test
    void unversionedMigrationValidatesTheWholeChainBeforeLegacyPathCopies()
            throws Exception {
        TestContext context = context("legacy-chain-gap");
        byte[] original = resourceBytes("legacy-flat-v1.1.0.yml");
        writeBytes(context.configPath, original);
        removeMigration(context.manager, 7);

        ConfigManager.ConfigPreparationException error = assertThrows(
                ConfigManager.ConfigPreparationException.class,
                context.manager::prepareConfig);

        assertTrue(error.stage().contains("missing required step v7 -> v8"));
        assertArrayEquals(original, Files.readAllBytes(context.configPath));
        assertEquals(0, backupCount(context.dataDirectory));
    }

    private void assertCompleteCurrentConfiguration(YamlConfiguration actual) throws Exception {
        YamlConfiguration expected = loadDefaults();
        assertEquals(ConfigManager.CURRENT_CONFIG_VERSION,
                actual.getInt("config-version"));
        for (String path : expected.getKeys(true)) {
            if (expected.isConfigurationSection(path)) {
                continue;
            }
            Object expectedValue = expected.get(path, null);
            Object actualValue = actual.get(path, null);
            assertNotNull(actualValue, "missing current runtime path " + path);
            if (expectedValue instanceof Number) {
                assertTrue(actualValue instanceof Number,
                        "invalid numeric current runtime path " + path);
            } else if (expectedValue instanceof List) {
                assertTrue(actualValue instanceof List,
                        "invalid list current runtime path " + path);
            } else if (expectedValue instanceof ConfigurationSection) {
                assertTrue(actualValue instanceof ConfigurationSection,
                        "invalid section current runtime path " + path);
            } else {
                assertTrue(expectedValue.getClass().isInstance(actualValue),
                        "invalid current runtime path " + path);
            }
        }
    }

    private TestContext context(String name) throws IOException {
        Path dataDirectory = temporaryDirectory.resolve(name + "-" + System.nanoTime());
        byte[] defaults = defaultBytes();
        ConfigManager manager = new ConfigManager(
                dataDirectory,
                () -> new ByteArrayInputStream(defaults),
                Logger.getLogger(getClass().getName() + "." + name + System.nanoTime()),
                new ConfigManager.NioFileAccess()
        );
        return new TestContext(dataDirectory, manager);
    }

    private YamlConfiguration loadDefaults() throws Exception {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.loadFromString(new String(defaultBytes(), StandardCharsets.UTF_8));
        return configuration;
    }

    private byte[] defaultBytes() throws IOException {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("config.yml")) {
            if (input == null) {
                throw new IOException("config.yml test resource not found");
            }
            return input.readAllBytes();
        }
    }

    private YamlConfiguration loadResource(String resource) throws Exception {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.loadFromString(
                new String(resourceBytes(resource), StandardCharsets.UTF_8));
        return configuration;
    }

    private byte[] resourceBytes(String resource) throws IOException {
        String path = "config-history/" + resource;
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(path)) {
            if (input == null) {
                throw new IOException("historical config fixture not found: " + path);
            }
            return input.readAllBytes();
        }
    }

    private void writeBytes(Path path, byte[] contents) throws IOException {
        Files.createDirectories(path.getParent());
        Files.write(path, contents);
    }

    private YamlConfiguration load(Path path) throws Exception {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.load(path.toFile());
        return configuration;
    }

    private Path expectedBackup(TestContext context, Integer version) {
        String suffix = version == null ? "legacy" : "v" + version;
        return context.dataDirectory.resolve("config.yml." + suffix + ".bak");
    }

    private long backupCount(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return 0;
        }
        try (Stream<Path> files = Files.list(directory)) {
            return files.filter(path -> path.getFileName().toString().endsWith(".bak"))
                    .count();
        }
    }

    private String sha256WithoutAddedTerminalNewline(byte[] fixture) throws Exception {
        byte[] tagBytes = fixture;
        if (fixture.length > 0 && fixture[fixture.length - 1] == '\n') {
            tagBytes = Arrays.copyOf(fixture, fixture.length - 1);
        }
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(tagBytes);
        return toHex(digest);
    }

    private String toHex(byte[] bytes) {
        StringBuilder value = new StringBuilder(bytes.length * 2);
        for (byte current : bytes) {
            value.append(String.format("%02x", current & 0xff));
        }
        return value.toString();
    }

    @SuppressWarnings("unchecked")
    private void removeMigration(ConfigManager manager, int version) throws Exception {
        Field field = ConfigManager.class.getDeclaredField("migrations");
        field.setAccessible(true);
        ((Map<Integer, ?>) field.get(manager)).remove(version);
    }

    private static HistoricalCase historical(
            String name,
            String sourceTag,
            String resource,
            Integer version,
            String tagSha256,
            String enableOldPath,
            String enableNewPath,
            String actionOldPath,
            String actionNewPath,
            String messageOldPath,
            String messageNewPath,
            String soundOldPath,
            String soundNewPath
    ) {
        return new HistoricalCase(
                name,
                sourceTag,
                resource,
                version,
                tagSha256,
                enableOldPath,
                enableNewPath,
                actionOldPath,
                actionNewPath,
                messageOldPath,
                messageNewPath,
                soundOldPath,
                soundNewPath
        );
    }

    private static final class HistoricalCase {
        private final String name;
        private final String sourceTag;
        private final String resource;
        private final Integer version;
        private final String tagSha256;
        private final String enableOldPath;
        private final String enableNewPath;
        private final String actionOldPath;
        private final String actionNewPath;
        private final String messageOldPath;
        private final String messageNewPath;
        private final String soundOldPath;
        private final String soundNewPath;

        private HistoricalCase(
                String name,
                String sourceTag,
                String resource,
                Integer version,
                String tagSha256,
                String enableOldPath,
                String enableNewPath,
                String actionOldPath,
                String actionNewPath,
                String messageOldPath,
                String messageNewPath,
                String soundOldPath,
                String soundNewPath
        ) {
            this.name = name;
            this.sourceTag = sourceTag;
            this.resource = resource;
            this.version = version;
            this.tagSha256 = tagSha256;
            this.enableOldPath = enableOldPath;
            this.enableNewPath = enableNewPath;
            this.actionOldPath = actionOldPath;
            this.actionNewPath = actionNewPath;
            this.messageOldPath = messageOldPath;
            this.messageNewPath = messageNewPath;
            this.soundOldPath = soundOldPath;
            this.soundNewPath = soundNewPath;
        }
    }

    private static final class TestContext {
        private final Path dataDirectory;
        private final Path configPath;
        private final ConfigManager manager;

        private TestContext(Path dataDirectory, ConfigManager manager) {
            this.dataDirectory = dataDirectory;
            this.configPath = dataDirectory.resolve("config.yml");
            this.manager = manager;
        }
    }
}
