package jp.wolfx.mceew.velocity;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
        Path config = dataDirectory.resolve("config.yml");
        assertTrue(Files.isRegularFile(config));
        String content = Files.readString(config);
        assertTrue(content.contains("platform-config-version: 1"));
        assertTrue(content.contains("mode: all"));
    }

    @Test
    void currentConfigLoadsWithoutRewritingUserFile() throws Exception {
        byte[] original = validConfig("selected").getBytes(StandardCharsets.UTF_8);
        Path config = writeConfig(original);

        VelocityConfigSnapshot snapshot = new VelocityConfigLoader(config.getParent()).load();

        assertEquals(1, snapshot.platformConfigVersion());
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
}
