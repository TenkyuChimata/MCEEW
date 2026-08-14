package jp.wolfx.mceew.velocity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.velocitypowered.api.network.ProtocolVersion;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Set;
import jp.wolfx.mceew.notification.NotificationIntent;
import jp.wolfx.mceew.notification.NotificationIntentFactory;
import jp.wolfx.mceew.notification.NotificationSource;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.title.Title;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VelocityNotificationDispatcherTest {
    private static final String ALL = "mceew.notify.all";

    @TempDir
    Path temporaryDirectory;

    @Test
    void jmaUsesOneSchedulerTaskAndChecksPermissionPerEnabledChannel() throws Exception {
        Fixture fixture = fixture(config("", "all", "servers: {}"));
        NotificationTestSupport.RecordingPlayer player = fixture.environment.addPlayer(
                "player", "lobby", Set.of(ALL, NotificationSource.JMA_ALERT.getPermissionNode()));

        fixture.dispatcher.dispatch(jmaEvent(fixture.config));

        assertTrue(player.messages().isEmpty());
        assertEquals(1, fixture.environment.scheduler().tasks().size());
        fixture.environment.scheduler().runAll();

        assertEquals(1, player.messages().size());
        assertEquals(1, player.titles().size());
        assertEquals(1, player.sounds().size());
        assertEquals(6, player.permissionQueries().size());
        assertEquals(1, fixture.environment.consoleMessages().size());
    }

    @Test
    void jmaWithAllChannelsDisabledDoesNotQueryPermission() throws Exception {
        Fixture fixture = fixture(config(disabledDefaults(), "all", "servers: {}"));
        NotificationTestSupport.RecordingPlayer player = fixture.environment.addPlayer(
                "player", "lobby", Set.of(ALL, NotificationSource.JMA_ALERT.getPermissionNode()));

        fixture.dispatcher.dispatch(jmaEvent(fixture.config));
        fixture.environment.scheduler().runAll();

        assertTrue(player.permissionQueries().isEmpty());
        assertTrue(player.messages().isEmpty());
        assertTrue(fixture.environment.consoleMessages().isEmpty());
    }

    @Test
    void regionalChecksPermissionOnceEvenWhenAllChannelsDisabled() throws Exception {
        Fixture fixture = fixture(config(disabledDefaults(), "all", "servers: {}"));
        NotificationTestSupport.RecordingPlayer player = fixture.environment.addPlayer(
                "player", "lobby", Set.of(ALL, NotificationSource.SICHUAN_EEW.getPermissionNode()));

        fixture.dispatcher.dispatch(regionalEvent(fixture.config, NotificationSource.SICHUAN_EEW));
        fixture.environment.scheduler().runAll();

        assertEquals(Set.of(ALL, NotificationSource.SICHUAN_EEW.getPermissionNode()),
                Set.copyOf(player.permissionQueries()));
        assertEquals(2, player.permissionQueries().size());
        assertTrue(player.messages().isEmpty());
    }

    @Test
    void permissionRequiresAllAndSourceWithShortCircuit() throws Exception {
        Fixture fixture = fixture(config(channels(false, false), "all", "servers: {}"));
        NotificationTestSupport.RecordingPlayer both = fixture.environment.addPlayer(
                "both", "lobby", Set.of(ALL, NotificationSource.SICHUAN_EEW.getPermissionNode()));
        NotificationTestSupport.RecordingPlayer allOnly = fixture.environment.addPlayer(
                "all-only", "lobby", Set.of(ALL));
        NotificationTestSupport.RecordingPlayer sourceOnly = fixture.environment.addPlayer(
                "source-only", "lobby", Set.of(NotificationSource.SICHUAN_EEW.getPermissionNode()));
        NotificationTestSupport.RecordingPlayer neither = fixture.environment.addPlayer(
                "neither", "lobby", Set.of());

        fixture.dispatcher.dispatch(regionalEvent(fixture.config, NotificationSource.SICHUAN_EEW));
        fixture.environment.scheduler().runAll();

        assertEquals(1, both.messages().size());
        assertTrue(allOnly.messages().isEmpty());
        assertTrue(sourceOnly.messages().isEmpty());
        assertTrue(neither.messages().isEmpty());
        assertEquals(2, allOnly.permissionQueries().size());
        assertEquals(1, sourceOnly.permissionQueries().size());
        assertEquals(1, neither.permissionQueries().size());
    }

    @Test
    void consoleIsIndependentOfTargetingAndPlayerPermission() throws Exception {
        Fixture fixture = fixture(config(channels(false, false), "none", "servers: {}"));
        NotificationTestSupport.RecordingPlayer denied = fixture.environment.addPlayer(
                "denied", "lobby", Set.of());

        fixture.dispatcher.dispatch(regionalEvent(fixture.config, NotificationSource.SICHUAN_EEW));
        fixture.environment.scheduler().runAll();

        assertEquals(1, fixture.environment.consoleMessages().size());
        assertTrue(denied.permissionQueries().isEmpty());
        assertTrue(denied.messages().isEmpty());
    }

    @Test
    void legacyChatAndTitleTimingMatchBukkitSemantics() throws Exception {
        Fixture fixture = fixture(config("", "all", "servers: {}"));
        NotificationTestSupport.RecordingPlayer player = fixture.environment.addPlayer(
                "player", "lobby", Set.of(ALL, NotificationSource.SICHUAN_EEW.getPermissionNode()));

        fixture.dispatcher.dispatch(regionalEvent(fixture.config, NotificationSource.SICHUAN_EEW));
        fixture.environment.scheduler().runAll();

        String legacy = LegacyComponentSerializer.legacySection().serialize(player.messages().get(0));
        assertTrue(legacy.startsWith("§c四川地震预警 | 第1报"));
        Title title = player.titles().get(0);
        assertEquals("四川地震预警",
                PlainTextComponentSerializer.plainText().serialize(title.title()));
        assertEquals(Duration.ofMillis(500), title.times().fadeIn());
        assertEquals(Duration.ofMillis(3500), title.times().stay());
        assertEquals(Duration.ofMillis(1000), title.times().fadeOut());
    }

    @Test
    void supportedPlayerWithBackendReceivesMasterSoundAtConfiguredValues() throws Exception {
        Fixture fixture = fixture(config("", "all", "servers: {}"));
        NotificationTestSupport.RecordingPlayer player = fixture.environment.addPlayer(
                "player", "lobby", Set.of(ALL, NotificationSource.SICHUAN_EEW.getPermissionNode()));

        fixture.dispatcher.dispatch(regionalEvent(fixture.config, NotificationSource.SICHUAN_EEW));
        fixture.environment.scheduler().runAll();

        Sound sound = player.sounds().get(0);
        assertEquals("minecraft:block.note_block.pling", sound.name().asString());
        assertEquals(Sound.Source.MASTER, sound.source());
        assertEquals(1000.0f, sound.volume());
        assertEquals(1.0f, sound.pitch());
    }

    @Test
    void unsupportedClientAndNoBackendSkipOnlySound() throws Exception {
        Fixture fixture = fixture(config("", "all", "servers: {}"));
        Set<String> permissions = Set.of(ALL, NotificationSource.SICHUAN_EEW.getPermissionNode());
        NotificationTestSupport.RecordingPlayer oldClient = fixture.environment.addPlayer(
                "old", "lobby", permissions);
        oldClient.protocolVersion(ProtocolVersion.MINECRAFT_1_19_1);
        NotificationTestSupport.RecordingPlayer noBackend = fixture.environment.addPlayer(
                "connecting", null, permissions);

        fixture.dispatcher.dispatch(regionalEvent(fixture.config, NotificationSource.SICHUAN_EEW));
        fixture.environment.scheduler().runAll();

        assertTrue(oldClient.sounds().isEmpty());
        assertTrue(noBackend.sounds().isEmpty());
        assertEquals(1, oldClient.messages().size());
        assertEquals(1, oldClient.titles().size());
        assertEquals(1, noBackend.messages().size());
        assertEquals(1, noBackend.titles().size());
    }

    @Test
    void invalidSoundWarnsOnceAndDoesNotSuppressOtherChannels() throws Exception {
        String notifications = "notifications:\n"
                + "  sources:\n"
                + "    sichuan:\n"
                + "      sound:\n"
                + "        key: INVALID KEY\n";
        Fixture fixture = fixture(config(notifications, "all", "servers: {}"));
        NotificationTestSupport.RecordingPlayer player = fixture.environment.addPlayer(
                "player", "lobby", Set.of(ALL, NotificationSource.SICHUAN_EEW.getPermissionNode()));

        fixture.dispatcher.dispatch(regionalEvent(fixture.config, NotificationSource.SICHUAN_EEW));
        fixture.dispatcher.dispatch(regionalEvent(fixture.config, NotificationSource.SICHUAN_EEW));
        fixture.environment.scheduler().runAll();

        assertTrue(player.sounds().isEmpty());
        assertEquals(2, player.messages().size());
        assertEquals(1, fixture.logger.warningCountContaining("Unknown sound type"));
    }

    @Test
    void serverSourceOverrideCanReenableSoundDisabledByInheritedPolicy() throws Exception {
        String servers = "servers:\n"
                + "  lobby:\n"
                + "    sources:\n"
                + "      sichuan:\n"
                + "        alert: true\n";
        Fixture fixture = fixture(config(disabledDefaults(), "all", servers));
        NotificationTestSupport.RecordingPlayer player = fixture.environment.addPlayer(
                "player", "lobby", Set.of(ALL, NotificationSource.SICHUAN_EEW.getPermissionNode()));

        fixture.dispatcher.dispatch(regionalEvent(fixture.config, NotificationSource.SICHUAN_EEW));
        fixture.environment.scheduler().runAll();

        assertEquals(1, player.sounds().size());
        assertTrue(player.messages().isEmpty());
        assertTrue(player.titles().isEmpty());
    }

    @Test
    void eqlistChecksPermissionOnceAndUsesOnlyChat() throws Exception {
        Fixture fixture = fixture(config("", "all", "servers: {}"));
        NotificationTestSupport.RecordingPlayer player = fixture.environment.addPlayer(
                "player", "lobby", Set.of(ALL,
                        NotificationSource.JMA_EARTHQUAKE_LIST.getPermissionNode()));

        fixture.dispatcher.dispatch(eqlistEvent(NotificationSource.JMA_EARTHQUAKE_LIST));
        fixture.environment.scheduler().runAll();

        assertEquals(2, player.permissionQueries().size());
        assertEquals(1, player.messages().size());
        assertTrue(player.titles().isEmpty());
        assertTrue(player.sounds().isEmpty());
        assertEquals(1, fixture.environment.consoleMessages().size());
    }

    @Test
    void eqlistServerSourceOverrideAffectsPlayersButNotProxyConsole() throws Exception {
        Fixture disabledOnLobby = fixture(config(""
                + "notifications:\n"
                + "  sources:\n"
                + "    jma_eqlist:\n"
                + "      broadcast: true\n", "all", ""
                + "servers:\n"
                + "  lobby:\n"
                + "    sources:\n"
                + "      jma_eqlist:\n"
                + "        broadcast: false\n"));
        NotificationTestSupport.RecordingPlayer blocked = disabledOnLobby.environment.addPlayer(
                "blocked", "lobby", Set.of(
                        ALL, NotificationSource.JMA_EARTHQUAKE_LIST.getPermissionNode()));

        disabledOnLobby.dispatcher.dispatch(
                eqlistEvent(NotificationSource.JMA_EARTHQUAKE_LIST));
        disabledOnLobby.environment.scheduler().runAll();

        assertTrue(blocked.messages().isEmpty());
        assertEquals(1, disabledOnLobby.environment.consoleMessages().size());

        Fixture enabledOnLobby = fixture(config(""
                + "notifications:\n"
                + "  defaults:\n"
                + "    broadcast: false\n", "all", ""
                + "servers:\n"
                + "  lobby:\n"
                + "    sources:\n"
                + "      jma_eqlist:\n"
                + "        broadcast: true\n"));
        NotificationTestSupport.RecordingPlayer allowed = enabledOnLobby.environment.addPlayer(
                "allowed", "lobby", Set.of(
                        ALL, NotificationSource.JMA_EARTHQUAKE_LIST.getPermissionNode()));

        enabledOnLobby.dispatcher.dispatch(
                eqlistEvent(NotificationSource.JMA_EARTHQUAKE_LIST));
        enabledOnLobby.environment.scheduler().runAll();

        assertEquals(1, allowed.messages().size());
        assertTrue(enabledOnLobby.environment.consoleMessages().isEmpty());
    }

    @Test
    void oneBrokenRecipientDoesNotSuppressAnotherOrOtherChannels() throws Exception {
        Fixture fixture = fixture(config("", "all", "servers: {}"));
        Set<String> permissions = Set.of(ALL, NotificationSource.SICHUAN_EEW.getPermissionNode());
        NotificationTestSupport.RecordingPlayer broken = fixture.environment.addPlayer(
                "broken", "lobby", permissions);
        broken.failSendMessage(new IllegalStateException("gone"));
        NotificationTestSupport.RecordingPlayer healthy = fixture.environment.addPlayer(
                "healthy", "lobby", permissions);

        fixture.dispatcher.dispatch(regionalEvent(fixture.config, NotificationSource.SICHUAN_EEW));
        fixture.environment.scheduler().runAll();

        assertEquals(1, healthy.messages().size());
        assertEquals(1, healthy.titles().size());
        assertEquals(1, broken.titles().size());
        assertEquals(1, fixture.logger.warningCountContaining("chat delivery failed"));
    }

    @Test
    void closeBeforeScheduledTaskPreventsStaleDelivery() throws Exception {
        Fixture fixture = fixture(config("", "all", "servers: {}"));
        NotificationTestSupport.RecordingPlayer player = fixture.environment.addPlayer(
                "player", "lobby", Set.of(ALL, NotificationSource.SICHUAN_EEW.getPermissionNode()));

        fixture.dispatcher.dispatch(regionalEvent(fixture.config, NotificationSource.SICHUAN_EEW));
        fixture.dispatcher.close();
        fixture.environment.scheduler().runAll();

        assertTrue(player.messages().isEmpty());
        assertTrue(fixture.environment.consoleMessages().isEmpty());
    }

    private Fixture fixture(String config) throws Exception {
        Path data = temporaryDirectory.resolve("mceew");
        Files.createDirectories(data);
        Files.writeString(data.resolve("config.yml"), config, StandardCharsets.UTF_8);
        VelocityNotificationConfig notificationConfig =
                new VelocityConfigLoader(data).load().notificationConfig();
        NotificationTestSupport.Environment environment = new NotificationTestSupport.Environment();
        TestVelocityApi.CapturingLogger logger = TestVelocityApi.logger();
        VelocityDelayScheduler scheduler = new VelocityDelayScheduler(environment.proxy(), this);
        VelocityNotificationDispatcher dispatcher = new VelocityNotificationDispatcher(
                environment.proxy(), logger.proxy(), scheduler, notificationConfig);
        return new Fixture(environment, logger, notificationConfig, dispatcher);
    }

    private static VelocityNotificationEvent jmaEvent(VelocityNotificationConfig config) {
        return event(NotificationSource.JMA_ALERT, VelocityNotificationEvent.DeliveryStyle.JMA,
                channels -> NotificationIntentFactory.jma(
                        "警報", "report", "origin", "1", "35", "139", "region", "6",
                        "10km", "§c6弱", "最終報", channels.chat(), channels.title(),
                        channels.sound(),
                        config.source(NotificationSource.JMA_ALERT).profile(),
                        config.source(NotificationSource.JMA_FORECAST).profile()));
    }

    private static VelocityNotificationEvent regionalEvent(
            VelocityNotificationConfig config,
            NotificationSource source
    ) {
        return event(source, VelocityNotificationEvent.DeliveryStyle.REGIONAL,
                channels -> NotificationIntentFactory.regional(
                        source, "report", "origin", "1", "30", "104", "region", "5",
                        "10km", "§c8", channels.chat(), channels.title(), channels.sound(),
                        config.source(source).profile()));
    }

    private static VelocityNotificationEvent eqlistEvent(NotificationSource source) {
        return event(source, VelocityNotificationEvent.DeliveryStyle.EARTHQUAKE_LIST,
                channels -> NotificationIntentFactory.earthquakeList(
                        source, true, channels.chat(), () -> "§eearthquake list")
                        .orElse(null));
    }

    private static VelocityNotificationEvent event(
            NotificationSource source,
            VelocityNotificationEvent.DeliveryStyle style,
            java.util.function.Function<VelocityChannelPolicy, NotificationIntent> factory
    ) {
        return new VelocityNotificationEvent() {
            @Override
            public NotificationSource source() {
                return source;
            }

            @Override
            public DeliveryStyle deliveryStyle() {
                return style;
            }

            @Override
            public NotificationIntent build(VelocityChannelPolicy channels) {
                return factory.apply(channels);
            }
        };
    }

    private static String config(String notifications, String targetMode, String servers) {
        return "platform_config_version: 1\n"
                + "global: {}\n"
                + notifications
                + "targets:\n"
                + "  default:\n"
                + "    mode: " + targetMode + "\n"
                + "  sources: {}\n"
                + "groups: {}\n"
                + servers;
    }

    private static String disabledDefaults() {
        return "notifications:\n"
                + "  defaults:\n"
                + "    broadcast: false\n"
                + "    title: false\n"
                + "    alert: false\n";
    }

    private static String channels(boolean title, boolean sound) {
        return "notifications:\n"
                + "  defaults:\n"
                + "    broadcast: true\n"
                + "    title: " + title + "\n"
                + "    alert: " + sound + "\n";
    }

    private static final class Fixture {
        private final NotificationTestSupport.Environment environment;
        private final TestVelocityApi.CapturingLogger logger;
        private final VelocityNotificationConfig config;
        private final VelocityNotificationDispatcher dispatcher;

        private Fixture(
                NotificationTestSupport.Environment environment,
                TestVelocityApi.CapturingLogger logger,
                VelocityNotificationConfig config,
                VelocityNotificationDispatcher dispatcher
        ) {
            this.environment = environment;
            this.logger = logger;
            this.config = config;
            this.dispatcher = dispatcher;
        }
    }
}
