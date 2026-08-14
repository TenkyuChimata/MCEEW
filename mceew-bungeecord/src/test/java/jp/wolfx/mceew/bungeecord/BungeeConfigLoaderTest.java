package jp.wolfx.mceew.bungeecord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import jp.wolfx.mceew.notification.NotificationSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BungeeConfigLoaderTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void bundledConfigCreatesAndParsesCompleteVersionOneSnapshot() throws Exception {
        BungeeConfigSnapshot config = loader().loadSnapshot();

        assertEquals(1, config.platformConfigVersion());
        assertTrue(config.runtimeEnabled());
        assertTrue(config.sourceGates().jma());
        assertTrue(config.sourceGates().sichuan());
        assertTrue(config.sourceGates().fujian());
        assertTrue(config.sourceGates().cwa());
        assertTrue(config.sourceGates().cenc());
        assertTrue(config.sourceGates().chongqing());
        assertEquals("yyyy年MM月dd日 HH時mm分ss秒", config.timeFormat());
        assertTrue(config.notificationDefaults().broadcast());
        assertTrue(config.notificationDefaults().title());
        assertEquals(9, config.notificationSources().size());
        assertEquals(BungeeConfigSnapshot.TargetMode.ALL, config.defaultTarget().mode());
        assertTrue(config.sourceTargets().isEmpty());
        assertTrue(config.groups().isEmpty());
        assertTrue(config.servers().isEmpty());
        assertTrue(Files.isRegularFile(temporaryDirectory.resolve("config.yml")));
    }

    @Test
    void bundledConfigHasCanonicalSourcesAndNoUnsupportedChannels() throws Exception {
        String bundled = bundledConfig();
        for (String key : Set.of(
                "jma_alert", "jma_forecast", "sichuan", "fujian", "cwa",
                "cenc_eew", "chongqing", "jma_eqlist", "cenc_eqlist")) {
            assertTrue(bundled.contains("    " + key + ":"), key);
        }
        assertTrue(bundled.contains("    broadcast: true"));
        assertTrue(bundled.contains("    title: true"));
        assertFalse(bundled.lines()
                .map(String::trim)
                .anyMatch(line -> line.startsWith("alert:")));
        assertFalse(bundled.lines()
                .map(String::trim)
                .anyMatch(line -> line.startsWith("sound:")));
    }

    @Test
    void eqlistBroadcastDefaultsAreExplicit() throws Exception {
        BungeeConfigSnapshot config = loader().loadSnapshot();

        assertEquals(Boolean.TRUE, config.notificationSources()
                .get(NotificationSource.JMA_EARTHQUAKE_LIST).channels().broadcast());
        assertEquals(Boolean.TRUE, config.notificationSources()
                .get(NotificationSource.CENC_EARTHQUAKE_LIST).channels().broadcast());
        assertNull(config.notificationSources()
                .get(NotificationSource.JMA_EARTHQUAKE_LIST).channels().title());
    }

    @Test
    void sourceGatesUseStrictBooleans() throws Exception {
        write(minimalConfig("all").replace(
                "  enabled: false",
                "  enabled: false\n  sources:\n    enable_jp: false\n    enable_sc: false\n"
                        + "    enable_fj: false\n    enable_cwa: false\n"
                        + "    enable_cenceew: false\n    enable_cq: false"));
        BungeeConfigSnapshot config = loader().loadSnapshot();

        assertFalse(config.runtimeEnabled());
        assertFalse(config.sourceGates().jma());
        assertFalse(config.sourceGates().sichuan());
        assertFalse(config.sourceGates().fujian());
        assertFalse(config.sourceGates().cwa());
        assertFalse(config.sourceGates().cenc());
        assertFalse(config.sourceGates().chongqing());
    }

    @Test
    void missingVersionIsRejected() throws Exception {
        write(minimalConfig("all").replace("platform_config_version: 1\n", ""));
        assertErrorContains("platform_config_version must be an integer");
    }

    @Test
    void futureVersionIsRejected() throws Exception {
        write(minimalConfig("all").replace(
                "platform_config_version: 1", "platform_config_version: 2"));
        assertErrorContains("Unsupported platform_config_version 2");
    }

    @Test
    void invalidVersionTypeIsRejected() throws Exception {
        write(minimalConfig("all").replace(
                "platform_config_version: 1", "platform_config_version: \"1\""));
        assertErrorContains("platform_config_version must be an integer");
    }

    @Test
    void malformedYamlIsRejected() throws Exception {
        write("platform_config_version: [\n");
        assertErrorContains("Malformed BungeeCord config");
    }

    @Test
    void duplicateKeysAreRejected() throws Exception {
        write(minimalConfig("all") + "global: {}\n");
        assertErrorContains("Malformed BungeeCord config");
    }

    @Test
    void quotedBooleanIsRejectedRatherThanCoerced() throws Exception {
        write(minimalConfig("all").replace("enabled: false", "enabled: \"false\""));
        assertErrorContains("global.enabled must be a boolean");
    }

    @Test
    void allTargetModesParse() throws Exception {
        for (String mode : Set.of("all", "selected", "none")) {
            Path directory = temporaryDirectory.resolve(mode);
            Files.createDirectories(directory);
            Files.writeString(directory.resolve("config.yml"), minimalConfig(mode));
            BungeeConfigSnapshot config = new BungeeConfigLoader(
                    directory, getClass().getClassLoader()).loadSnapshot();
            assertEquals(mode.toUpperCase(), config.defaultTarget().mode().name());
        }
    }

    @Test
    void unknownTargetModeIsRejected() throws Exception {
        write(minimalConfig("nearest"));
        assertErrorContains("mode must be 'all', 'selected', or 'none'");
    }

    @Test
    void groupsTargetsAndServerOverridesParseImmutably() throws Exception {
        write("platform_config_version: 1\n"
                + "global:\n  enabled: false\n"
                + "targets:\n  default:\n    mode: selected\n"
                + "    servers: [Lobby]\n    groups: [Primary]\n"
                + "  sources:\n    jma_alert:\n      mode: none\n"
                + "groups:\n  Primary: [Survival, Events]\n"
                + "servers:\n  Lobby:\n    notifications:\n      broadcast: false\n"
                + "    sources:\n      jma_alert:\n        title: true\n");

        BungeeConfigSnapshot config = loader().loadSnapshot();
        assertEquals(Set.of("lobby"), config.defaultTarget().servers());
        assertEquals(Set.of("primary"), config.defaultTarget().groups());
        assertEquals(Set.of("survival", "events"), config.groups().get("primary"));
        assertEquals(BungeeConfigSnapshot.TargetMode.NONE,
                config.sourceTargets().get(NotificationSource.JMA_ALERT).mode());
        BungeeConfigSnapshot.ServerSettings lobby = config.servers().get("lobby");
        assertEquals(Boolean.FALSE, lobby.channels().broadcast());
        assertEquals(Boolean.TRUE,
                lobby.sourceChannels().get(NotificationSource.JMA_ALERT).title());
        assertThrows(UnsupportedOperationException.class,
                () -> config.groups().put("new", Set.of()));
        assertThrows(UnsupportedOperationException.class,
                () -> config.groups().get("primary").add("new"));
        assertThrows(UnsupportedOperationException.class,
                () -> config.defaultTarget().servers().add("new"));
        assertThrows(UnsupportedOperationException.class,
                () -> config.sourceTargets().clear());
        assertThrows(UnsupportedOperationException.class,
                () -> config.servers().clear());
        assertThrows(UnsupportedOperationException.class,
                () -> lobby.sourceChannels().clear());
    }

    @Test
    void unknownSourceIdentifierIsRejected() throws Exception {
        write(minimalConfig("all").replace(
                "  sources: {}",
                "  sources:\n    sc:\n      mode: all"));
        assertErrorContains("Unknown notification source key under targets.sources");
    }

    @Test
    void unknownTargetGroupIsRejected() throws Exception {
        write(minimalConfig("all").replace(
                "    mode: all",
                "    mode: selected\n    groups: [missing]"));
        assertErrorContains("references unknown group: missing");
    }

    @Test
    void velocityAlertDefaultIsRejectedClearly() throws Exception {
        write(minimalConfig("all")
                + "notifications:\n  defaults:\n    alert: true\n");
        assertErrorContains("notifications.defaults.alert is not supported");
    }

    @Test
    void velocitySoundObjectIsRejectedClearly() throws Exception {
        write(minimalConfig("all")
                + "notifications:\n  sources:\n    jma_alert:\n      sound:\n"
                + "        key: block.note_block.pling\n");
        assertErrorContains("notifications.sources.jma_alert.sound is not supported");
    }

    @Test
    void serverAlertOverrideIsRejectedClearly() throws Exception {
        write(minimalConfig("all").replace(
                "servers: {}",
                "servers:\n  lobby:\n    notifications:\n      alert: true"));
        assertErrorContains("servers.lobby.notifications.alert is not supported");
    }

    @Test
    void existingUserConfigIsNeverOverwritten() throws Exception {
        String userConfig = minimalConfig("none");
        write(userConfig);

        BungeeConfigSnapshot config = loader().loadSnapshot();

        assertEquals(BungeeConfigSnapshot.TargetMode.NONE, config.defaultTarget().mode());
        assertEquals(userConfig,
                Files.readString(temporaryDirectory.resolve("config.yml")));
    }

    @Test
    void missingRequiredTopLevelMapsAreRejected() throws Exception {
        write("platform_config_version: 1\nglobal: {}\n");
        assertErrorContains("groups must be a mapping");
    }

    @Test
    void bundledMessagesRemainAvailableToSparseUserConfig() throws Exception {
        write(minimalConfig("all"));
        BungeeConfigSnapshot config = loader().loadSnapshot();

        assertTrue(config.notificationSources().get(NotificationSource.JMA_ALERT)
                .message().startsWith("&c緊急地震速報"));
        assertNotNull(config.notificationSources().get(NotificationSource.JMA_ALERT).title());
        assertTrue(config.notificationSources().get(NotificationSource.CENC_EARTHQUAKE_LIST)
                .message().startsWith("&e中国地震台网"));
    }

    private BungeeConfigLoader loader() {
        return new BungeeConfigLoader(temporaryDirectory, getClass().getClassLoader());
    }

    private void assertErrorContains(String expected) {
        BungeeConfigException error = assertThrows(
                BungeeConfigException.class, () -> loader().loadSnapshot());
        assertTrue(error.getMessage().contains(expected), error.getMessage());
    }

    private void write(String config) throws IOException {
        Files.createDirectories(temporaryDirectory);
        Files.writeString(temporaryDirectory.resolve("config.yml"), config);
    }

    private static String minimalConfig(String mode) {
        return "platform_config_version: 1\n"
                + "global:\n  enabled: false\n"
                + "targets:\n  default:\n    mode: " + mode + "\n  sources: {}\n"
                + "groups: {}\nservers: {}\n";
    }

    private String bundledConfig() throws IOException {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("config.yml")) {
            assertNotNull(input);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
