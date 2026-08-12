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

    @Test
    void phaseTwoConfigInheritsNotificationDefaultsWithoutRewrite() throws Exception {
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
                        + "    chat: false\n"
                        + "    title: false\n"
                        + "    sound: false\n"
                        + "  sources:\n"
                        + "    jma-alert:\n"
                        + "      channels:\n"
                        + "        chat: true\n"
                        + "        title: true\n"
                        + "        sound: true\n"
                        + "servers:\n"
                        + "  server-only:\n"
                        + "    notifications:\n"
                        + "      chat: false\n"
                        + "      title: false\n"
                        + "      sound: false\n"
                        + "  server-source:\n"
                        + "    notifications:\n"
                        + "      chat: false\n"
                        + "      title: false\n"
                        + "      sound: false\n"
                        + "    sources:\n"
                        + "      jma-alert:\n"
                        + "        chat: true\n"
                        + "        title: true\n"
                        + "        sound: true");
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
                + "    chat: yes-please\n"
                + "servers: {}");
        byte[] original = content.getBytes(StandardCharsets.UTF_8);
        Path config = writeConfig(original);

        VelocityConfigException error = assertThrows(
                VelocityConfigException.class,
                () -> new VelocityConfigLoader(config.getParent()).load());

        assertTrue(error.getMessage().contains("notifications.defaults.chat must be a boolean"));
        assertArrayEquals(original, Files.readAllBytes(config));
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
                        + "    jma-alert:\n"
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

    private static void assertPolicy(VelocityChannelPolicy policy, boolean expected) {
        assertEquals(expected, policy.chat());
        assertEquals(expected, policy.title());
        assertEquals(expected, policy.sound());
    }
}
