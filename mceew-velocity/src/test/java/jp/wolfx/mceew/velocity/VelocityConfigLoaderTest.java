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
import java.util.Set;
import jp.wolfx.mceew.notification.NotificationIntent;
import jp.wolfx.mceew.notification.NotificationIntentFactory;
import jp.wolfx.mceew.notification.NotificationSource;
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
        assertTrue(content.contains("platform_config_version: 1"));
        assertTrue(content.contains("enabled: true"));
        assertTrue(content.contains("enable_jp: true"));
        assertTrue(content.contains("enable_sc: true"));
        assertTrue(content.contains("enable_fj: true"));
        assertTrue(content.contains("enable_cwa: true"));
        assertTrue(content.contains("enable_cenceew: true"));
        assertTrue(content.contains("enable_cq: true"));
        assertTrue(content.contains("time_format:"));
        assertTrue(content.contains("broadcast: true"));
        assertTrue(content.contains("alert: true"));
        assertTrue(content.contains("jma_alert:"));
        assertTrue(content.contains("cenc_eew:"));
        assertTrue(content.contains("jma_eqlist:"));
        assertTrue(content.contains("cenc_eqlist:"));
        assertTrue(content.contains("mode: all"));
        for (String placeholder : new String[]{
            "%flag%", "%report_time%", "%origin_time%", "%num%", "%lat%", "%lon%",
            "%region%", "%mag%", "%depth%", "%shindo%", "%type%", "%info%", "# \\n New line"
        }) {
            assertTrue(content.contains(placeholder), placeholder);
        }
        assertFalse(content.contains("platform-config-version:"));
        assertFalse(content.contains("time-format:"));
        assertFalse(content.contains("jma-alert:"));
        assertFalse(content.contains("jma-forecast:"));
        assertFalse(content.contains("cenc-eew:"));
        assertFalse(content.contains("jma-eqlist:"));
        assertFalse(content.contains("cenc-eqlist:"));
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
    void missingOperationalKeysUseInMemoryDefaultsWithoutRewrite()
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
                .replace("enable_jp: true", "enable_jp: yes-please")
                .getBytes(StandardCharsets.UTF_8);
        Path config = writeConfig(original);

        VelocityConfigException error = assertThrows(
                VelocityConfigException.class,
                () -> new VelocityConfigLoader(config.getParent()).load());

        assertTrue(error.getMessage().contains("global.sources.enable_jp must be a boolean"));
        assertArrayEquals(original, Files.readAllBytes(config));
    }

    @Test
    void malformedYamlFailsSafelyAndRemainsUntouched() throws IOException {
        byte[] original = "platform_config_version: [\n".getBytes(StandardCharsets.UTF_8);
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

        assertTrue(error.getMessage().contains("Unsupported platform_config_version 2"));
        assertArrayEquals(original, Files.readAllBytes(config));
    }

    @Test
    void unknownKeysAreAcceptedAndNotRewritten() throws Exception {
        String content = validConfig("none")
                + "future_feature:\n"
                + "  nested_value: keep-me\n";
        byte[] original = content.getBytes(StandardCharsets.UTF_8);
        Path config = writeConfig(original);

        VelocityConfigSnapshot snapshot = new VelocityConfigLoader(config.getParent()).load();

        assertEquals(1, snapshot.platformConfigVersion());
        assertArrayEquals(original, Files.readAllBytes(config));
    }

    @Test
    void minimalConfigInheritsNotificationDefaultsWithoutRewrite() throws Exception {
        byte[] original = validConfig("all").getBytes(StandardCharsets.UTF_8);
        Path config = writeConfig(original);

        VelocityConfigSnapshot snapshot = new VelocityConfigLoader(config.getParent()).load();

        VelocityChannelPolicy policy = snapshot.notificationConfig()
                .proxyChannels(NotificationSource.JMA_ALERT);
        assertTrue(policy.chat());
        assertTrue(policy.title());
        assertTrue(policy.sound());
        assertEquals("yyyy年MM月dd日 HH時mm分ss秒",
                snapshot.notificationConfig().timeFormat());
        assertArrayEquals(original, Files.readAllBytes(config));
    }

    @Test
    void canonicalTimeFormatLoadsWithoutRewriting() throws Exception {
        String content = validConfig("all").replace(
                "global: {}", "global: {}\nnotifications:\n  time_format: yyyy/MM/dd HH:mm:ss");
        byte[] original = content.getBytes(StandardCharsets.UTF_8);
        Path config = writeConfig(original);

        VelocityConfigSnapshot snapshot = new VelocityConfigLoader(config.getParent()).load();

        assertEquals("yyyy/MM/dd HH:mm:ss", snapshot.notificationConfig().timeFormat());
        assertArrayEquals(original, Files.readAllBytes(config));
    }

    @Test
    void bundledJmaAlertProfilePreservesBukkitLegacyGoldenText() throws Exception {
        VelocityConfigSnapshot snapshot = new VelocityConfigLoader(
                temporaryDirectory.resolve("mceew")).load();

        NotificationIntent intent = NotificationIntentFactory.jma(
                "警報", "report", "origin", "7", "35.0", "139.0", "Region",
                "6.0", "10km", "§c6弱", "最終報", true, true, true,
                snapshot.notificationConfig().source(NotificationSource.JMA_ALERT).profile(),
                snapshot.notificationConfig().source(NotificationSource.JMA_FORECAST).profile());

        assertEquals("§c緊急地震速報 (警報) | 第7報 最終報\n"
                        + " §eorigin §f発生\n"
                        + " §f震央: §eRegion (北緯: §e35.0度 東経: §e139.0度)\n"
                        + " §fマグニチュード: §e6.0\n"
                        + " §f深さ: §e10km\n"
                        + " §f最大震度: §r§c6弱\n"
                        + " §f更新時間: §ereport",
                intent.getChat().render());
        assertEquals("§c緊急地震速報 (警報)", intent.getTitle().renderTitle());
    }

    @Test
    void channelPrecedenceIsServerSourceThenServerThenSourceThenGlobal() throws Exception {
        String content = validConfig("all")
                .replace("servers: {}", ""
                        + "notifications:\n"
                        + "  defaults:\n"
                        + "    broadcast: false\n"
                        + "    title: false\n"
                        + "    alert: false\n"
                        + "  sources:\n"
                        + "    jma_alert:\n"
                        + "      channels:\n"
                        + "        broadcast: true\n"
                        + "        title: true\n"
                        + "        alert: true\n"
                        + "servers:\n"
                        + "  server-only:\n"
                        + "    notifications:\n"
                        + "      broadcast: false\n"
                        + "      title: false\n"
                        + "      alert: false\n"
                        + "  server-source:\n"
                        + "    notifications:\n"
                        + "      broadcast: false\n"
                        + "      title: false\n"
                        + "      alert: false\n"
                        + "    sources:\n"
                        + "      jma_alert:\n"
                        + "        broadcast: true\n"
                        + "        title: true\n"
                        + "        alert: true");
        VelocityConfigSnapshot snapshot = new VelocityConfigLoader(
                writeConfig(content.getBytes(StandardCharsets.UTF_8)).getParent()).load();
        VelocityNotificationConfig notifications = snapshot.notificationConfig();

        assertPolicy(notifications.proxyChannels(NotificationSource.SICHUAN_EEW), false);
        assertPolicy(notifications.proxyChannels(NotificationSource.JMA_ALERT), true);
        assertPolicy(notifications.playerChannels(
                NotificationSource.JMA_ALERT, "server-only"), false);
        assertPolicy(notifications.playerChannels(
                NotificationSource.JMA_ALERT, "SERVER-SOURCE"), true);
        assertPolicy(notifications.playerChannels(NotificationSource.JMA_ALERT, null), true);
    }

    @Test
    void wrongNotificationChannelTypeFailsWithoutRewrite() throws IOException {
        String content = validConfig("all").replace("servers: {}", ""
                + "notifications:\n"
                + "  defaults:\n"
                + "    broadcast: yes-please\n"
                + "servers: {}");
        byte[] original = content.getBytes(StandardCharsets.UTF_8);
        Path config = writeConfig(original);

        VelocityConfigException error = assertThrows(
                VelocityConfigException.class,
                () -> new VelocityConfigLoader(config.getParent()).load());

        assertTrue(error.getMessage().contains(
                "notifications.defaults.broadcast must be a boolean"));
        assertArrayEquals(original, Files.readAllBytes(config));
    }

    @Test
    void retiredTopLevelAndNotificationKeysAreRejected() throws IOException {
        assertRetiredKeyRejected(
                validConfig("all").replace(
                        "platform_config_version", "platform-config-version"),
                "platform_config_version");
        assertRetiredKeyRejected(
                validConfig("all").replace(
                        "global: {}", "global: {}\nnotifications:\n  time-format: yyyy"),
                "notifications.time_format");
        assertRetiredKeyRejected(
                validConfig("all").replace(
                        "global: {}", "global: {}\nnotifications:\n  defaults:\n    chat: true"),
                "notifications.defaults.broadcast");
        assertRetiredKeyRejected(
                validConfig("all").replace(
                        "global: {}", "global: {}\nnotifications:\n  defaults:\n    sound: true"),
                "notifications.defaults.alert");
    }

    @Test
    void retiredOperationalSourceKeysAreRejected() throws IOException {
        String[] oldKeys = {"jma", "sichuan", "fujian", "cwa", "cenc", "chongqing"};
        String[] newKeys = {
            "enable_jp", "enable_sc", "enable_fj", "enable_cwa", "enable_cenceew", "enable_cq"
        };
        for (int index = 0; index < oldKeys.length; index++) {
            assertRetiredKeyRejected(
                    validConfig("all").replace(
                            "global: {}",
                            "global:\n  sources:\n    " + oldKeys[index] + ": true"),
                    "global.sources." + newKeys[index]);
        }
    }

    @Test
    void retiredKebabCaseNotificationSourceKeysAreRejected() throws IOException {
        String[] oldKeys = {
            "jma-alert", "jma-forecast", "cenc-eew", "jma-eqlist", "cenc-eqlist"
        };
        for (String oldKey : oldKeys) {
            String content = validConfig("all").replace(
                    "global: {}", "global: {}\nnotifications:\n  sources:\n    "
                            + oldKey + ": {}");
            Path config = writeConfig(content.getBytes(StandardCharsets.UTF_8));

            VelocityConfigException error = assertThrows(
                    VelocityConfigException.class,
                    () -> new VelocityConfigLoader(config.getParent()).load());

            assertTrue(error.getMessage().contains("must use lower_snake_case"));
        }
    }

    @Test
    void unknownTargetGroupFailsValidation() throws IOException {
        String content = validConfig("selected").replace(
                "    mode: selected\n",
                "    mode: selected\n    groups:\n      - missing\n");
        Path config = writeConfig(content.getBytes(StandardCharsets.UTF_8));

        VelocityConfigException error = assertThrows(
                VelocityConfigException.class,
                () -> new VelocityConfigLoader(config.getParent()).load());

        assertTrue(error.getMessage().contains("references unknown group: missing"));
    }

    @Test
    void sourceTargetIsCompleteReplacementAndNamesAreCaseNormalized() throws Exception {
        String content = validConfig("none")
                .replace("  sources: {}", ""
                        + "  sources:\n"
                        + "    jma_alert:\n"
                        + "      mode: selected\n"
                        + "      servers:\n"
                        + "        - Lobby\n"
                        + "      groups:\n"
                        + "        - Primary")
                .replace("groups: {}", "groups:\n  primary:\n    - Survival");
        VelocityConfigSnapshot snapshot = new VelocityConfigLoader(
                writeConfig(content.getBytes(StandardCharsets.UTF_8)).getParent()).load();

        VelocityTargetConfig targets = snapshot.notificationConfig().targets();
        assertEquals(VelocityTargetConfig.Mode.NONE,
                targets.targetFor(NotificationSource.SICHUAN_EEW).mode());
        assertEquals(VelocityTargetConfig.Mode.SELECTED,
                targets.targetFor(NotificationSource.JMA_ALERT).mode());
        assertEquals(Set.of("lobby", "survival"),
                targets.selectedServers(NotificationSource.JMA_ALERT));
    }

    private Path writeConfig(byte[] content) throws IOException {
        Path dataDirectory = temporaryDirectory.resolve("mceew");
        Files.createDirectories(dataDirectory);
        Path config = dataDirectory.resolve("config.yml");
        Files.write(config, content);
        return config;
    }

    private void assertRetiredKeyRejected(String content, String canonicalPath) throws IOException {
        byte[] original = content.getBytes(StandardCharsets.UTF_8);
        Path config = writeConfig(original);

        VelocityConfigException error = assertThrows(
                VelocityConfigException.class,
                () -> new VelocityConfigLoader(config.getParent()).load());

        assertTrue(error.getMessage().contains(canonicalPath));
        assertArrayEquals(original, Files.readAllBytes(config));
    }

    private static String validConfig(String mode) {
        return validConfigWithVersion("1").replace("mode: all", "mode: " + mode);
    }

    private static String validConfigWithVersion(String version) {
        return "platform_config_version: " + version + "\n"
                + "global: {}\n"
                + "targets:\n"
                + "  default:\n"
                + "    mode: all\n"
                + "  sources: {}\n"
                + "groups: {}\n"
                + "servers: {}\n";
    }

    private static String operationalConfig(boolean enabled) {
        return "platform_config_version: 1\n"
                + "global:\n"
                + "  enabled: " + enabled + "\n"
                + "  sources:\n"
                + "    enable_jp: " + enabled + "\n"
                + "    enable_sc: " + enabled + "\n"
                + "    enable_fj: " + enabled + "\n"
                + "    enable_cwa: " + enabled + "\n"
                + "    enable_cenceew: " + enabled + "\n"
                + "    enable_cq: " + enabled + "\n"
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

    private static void assertPolicy(VelocityChannelPolicy policy, boolean expected) {
        assertEquals(expected, policy.chat());
        assertEquals(expected, policy.title());
        assertEquals(expected, policy.sound());
    }
}
