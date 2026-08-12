package jp.wolfx.mceew.velocity;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VelocityConfigLoaderTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void missingConfigCreatesAndLoadsBundledDefault() throws Exception {
        Path dataDirectory = temporaryDirectory.resolve("mceew");

        VelocityConfigSnapshot snapshot = new VelocityConfigLoader(dataDirectory).load();

        assertEquals(1, snapshot.platformConfigVersion());
        assertTrue(snapshot.runtimeEnabled());
        assertAllSources(snapshot, true);
        Path config = dataDirectory.resolve("config.yml");
        assertTrue(Files.isRegularFile(config));
        String content = Files.readString(config);
        assertTrue(content.contains("platform-config-version: 1"));
        assertTrue(content.contains("enabled: true"));
        assertTrue(content.contains("chongqing: true"));
        assertTrue(content.contains("mode: all"));
    }

    @Test
    void currentConfigLoadsWithoutRewritingUserFile() throws Exception {
        byte[] original = validConfig("selected").getBytes(StandardCharsets.UTF_8);
        Path config = writeConfig(original);

        VelocityConfigSnapshot snapshot = new VelocityConfigLoader(config.getParent()).load();

        assertEquals(1, snapshot.platformConfigVersion());
        assertTrue(snapshot.runtimeEnabled());
        assertAllSources(snapshot, true);
        assertArrayEquals(original, Files.readAllBytes(config));
    }

    @Test
    void explicitOperationalFlagsLoadWithoutRewriting() throws Exception {
        byte[] original = operationalConfig(false).getBytes(StandardCharsets.UTF_8);
        Path config = writeConfig(original);

        VelocityConfigSnapshot snapshot = new VelocityConfigLoader(config.getParent()).load();

        assertFalse(snapshot.runtimeEnabled());
        assertAllSources(snapshot, false);
        assertArrayEquals(original, Files.readAllBytes(config));
    }

    @Test
    void missingOperationalKeysInPhaseOneConfigUseInMemoryDefaultsWithoutRewrite()
            throws Exception {
        byte[] original = validConfig("all").getBytes(StandardCharsets.UTF_8);
        Path config = writeConfig(original);

        VelocityConfigSnapshot snapshot = new VelocityConfigLoader(config.getParent()).load();

        assertTrue(snapshot.runtimeEnabled());
        assertAllSources(snapshot, true);
        assertArrayEquals(original, Files.readAllBytes(config));
    }

    @Test
    void wrongRuntimeBooleanTypeFailsWithoutRewriting() throws IOException {
        byte[] original = operationalConfig(true)
                .replace("enabled: true", "enabled: \"true\"")
                .getBytes(StandardCharsets.UTF_8);
        Path config = writeConfig(original);

        VelocityConfigException error = assertThrows(
                VelocityConfigException.class,
                () -> new VelocityConfigLoader(config.getParent()).load());

        assertTrue(error.getMessage().contains("global.enabled must be a boolean"));
        assertArrayEquals(original, Files.readAllBytes(config));
    }

    @Test
    void wrongSourceBooleanTypeFailsWithoutRewriting() throws IOException {
        byte[] original = operationalConfig(true)
                .replace("jma: true", "jma: yes-please")
                .getBytes(StandardCharsets.UTF_8);
        Path config = writeConfig(original);

        VelocityConfigException error = assertThrows(
                VelocityConfigException.class,
                () -> new VelocityConfigLoader(config.getParent()).load());

        assertTrue(error.getMessage().contains("global.sources.jma must be a boolean"));
        assertArrayEquals(original, Files.readAllBytes(config));
    }

    @Test
    void malformedYamlFailsSafelyAndRemainsUntouched() throws IOException {
        byte[] original = "platform-config-version: [\n".getBytes(StandardCharsets.UTF_8);
        Path config = writeConfig(original);

        VelocityConfigException error = assertThrows(
                VelocityConfigException.class,
                () -> new VelocityConfigLoader(config.getParent()).load());

        assertTrue(error.getMessage().contains("Malformed Velocity config"));
        assertArrayEquals(original, Files.readAllBytes(config));
    }

    @Test
    void wrongVersionTypeFailsSafelyAndRemainsUntouched() throws IOException {
        byte[] original = validConfigWithVersion("\"1\"").getBytes(StandardCharsets.UTF_8);
        Path config = writeConfig(original);

        VelocityConfigException error = assertThrows(
                VelocityConfigException.class,
                () -> new VelocityConfigLoader(config.getParent()).load());

        assertTrue(error.getMessage().contains("must be an integer"));
        assertArrayEquals(original, Files.readAllBytes(config));
    }

    @Test
    void unsupportedFutureVersionFailsWithoutDowngradeOrRewrite() throws IOException {
        byte[] original = validConfigWithVersion("2").getBytes(StandardCharsets.UTF_8);
        Path config = writeConfig(original);

        VelocityConfigException error = assertThrows(
                VelocityConfigException.class,
                () -> new VelocityConfigLoader(config.getParent()).load());

        assertTrue(error.getMessage().contains("Unsupported platform-config-version 2"));
        assertArrayEquals(original, Files.readAllBytes(config));
    }

    @Test
    void unknownKeysAreAcceptedAndNotRewritten() throws Exception {
        String content = validConfig("none")
                + "future-feature:\n"
                + "  nested-value: keep-me\n";
        byte[] original = content.getBytes(StandardCharsets.UTF_8);
        Path config = writeConfig(original);

        VelocityConfigSnapshot snapshot = new VelocityConfigLoader(config.getParent()).load();

        assertEquals(1, snapshot.platformConfigVersion());
        assertArrayEquals(original, Files.readAllBytes(config));
    }

    private Path writeConfig(byte[] content) throws IOException {
        Path dataDirectory = temporaryDirectory.resolve("mceew");
        Files.createDirectories(dataDirectory);
        Path config = dataDirectory.resolve("config.yml");
        Files.write(config, content);
        return config;
    }

    private static String validConfig(String mode) {
        return validConfigWithVersion("1").replace("mode: all", "mode: " + mode);
    }

    private static String validConfigWithVersion(String version) {
        return "platform-config-version: " + version + "\n"
                + "global: {}\n"
                + "targets:\n"
                + "  default:\n"
                + "    mode: all\n"
                + "  sources: {}\n"
                + "groups: {}\n"
                + "servers: {}\n";
    }

    private static String operationalConfig(boolean enabled) {
        return "platform-config-version: 1\n"
                + "global:\n"
                + "  enabled: " + enabled + "\n"
                + "  sources:\n"
                + "    jma: " + enabled + "\n"
                + "    sichuan: " + enabled + "\n"
                + "    fujian: " + enabled + "\n"
                + "    cwa: " + enabled + "\n"
                + "    cenc: " + enabled + "\n"
                + "    chongqing: " + enabled + "\n"
                + "targets:\n"
                + "  default:\n"
                + "    mode: all\n"
                + "  sources: {}\n"
                + "groups: {}\n"
                + "servers: {}\n";
    }

    private static void assertAllSources(VelocityConfigSnapshot snapshot, boolean expected) {
        assertEquals(expected, snapshot.jmaEnabled());
        assertEquals(expected, snapshot.sichuanEnabled());
        assertEquals(expected, snapshot.fujianEnabled());
        assertEquals(expected, snapshot.cwaEnabled());
        assertEquals(expected, snapshot.cencEnabled());
        assertEquals(expected, snapshot.chongqingEnabled());
    }
}
