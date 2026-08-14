package jp.wolfx.mceew.bungeecord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import jp.wolfx.mceew.notification.NotificationSource;
import org.junit.jupiter.api.Test;

class BungeeNotificationDispatcherTest {
    @Test
    void allTargetsPlayersWithAndWithoutBackendsAndDeduplicatesByUuid() {
        Fixture fixture = fixture(BungeeNotificationTestSupport.config().build());
        BungeeNotificationTestSupport.FakePlayer backend =
                fixture.platform.addPlayer("backend", "lobby");
        BungeeNotificationTestSupport.FakePlayer connecting =
                fixture.platform.addPlayer("connecting", null);
        fixture.platform.duplicate(backend);

        fixture.dispatcher.dispatch(event(NotificationSource.SICHUAN_EEW));
        run(fixture);

        assertEquals(1, backend.chats().size());
        assertEquals(1, backend.titles().size());
        assertEquals(1, connecting.chats().size());
        assertEquals(1, connecting.titles().size());
        assertEquals(1, fixture.platform.consoleMessages().size());
    }

    @Test
    void noneTargetsNoPlayersButLeavesConsoleIndependent() {
        Fixture fixture = fixture(BungeeNotificationTestSupport.config()
                .defaultTarget(BungeeConfigSnapshot.TargetMode.NONE, Set.of(), Set.of())
                .build());
        BungeeNotificationTestSupport.FakePlayer player =
                fixture.platform.addPlayer("player", "lobby");

        fixture.dispatcher.dispatch(event(NotificationSource.SICHUAN_EEW));
        run(fixture);

        assertTrue(player.chats().isEmpty());
        assertTrue(player.permissionQueries().isEmpty());
        assertEquals(1, fixture.platform.consoleMessages().size());
    }

    @Test
    void selectedTargetsExplicitServersUnionGroupsAndExcludesNoBackend() {
        Fixture fixture = fixture(BungeeNotificationTestSupport.config()
                .group("games", "survival", "minigames")
                .defaultTarget(
                        BungeeConfigSnapshot.TargetMode.SELECTED,
                        Set.of("lobby", "not-registered"),
                        Set.of("games"))
                .build());
        BungeeNotificationTestSupport.FakePlayer lobby =
                fixture.platform.addPlayer("lobby", "LOBBY");
        BungeeNotificationTestSupport.FakePlayer survival =
                fixture.platform.addPlayer("survival", "survival");
        BungeeNotificationTestSupport.FakePlayer minigames =
                fixture.platform.addPlayer("minigames", "minigames");
        BungeeNotificationTestSupport.FakePlayer other =
                fixture.platform.addPlayer("other", "creative");
        BungeeNotificationTestSupport.FakePlayer connecting =
                fixture.platform.addPlayer("connecting", null);

        fixture.dispatcher.dispatch(event(NotificationSource.SICHUAN_EEW));
        run(fixture);

        assertEquals(1, lobby.chats().size());
        assertEquals(1, survival.chats().size());
        assertEquals(1, minigames.chats().size());
        assertTrue(other.chats().isEmpty());
        assertTrue(connecting.chats().isEmpty());
    }

    @Test
    void sourceTargetReplacesDefaultAndBackendChangesApplyWithoutReload() {
        Fixture fixture = fixture(BungeeNotificationTestSupport.config()
                .defaultTarget(BungeeConfigSnapshot.TargetMode.ALL, Set.of(), Set.of())
                .sourceTarget(
                        NotificationSource.JMA_FORECAST,
                        BungeeConfigSnapshot.TargetMode.SELECTED,
                        Set.of("lobby"),
                        Set.of())
                .build());
        BungeeNotificationTestSupport.FakePlayer player =
                fixture.platform.addPlayer("player", "survival");
        fixture.platform.registerBackend("lobby");

        fixture.dispatcher.dispatch(event(NotificationSource.JMA_FORECAST));
        run(fixture);
        assertTrue(player.chats().isEmpty());

        player.backend("lobby");
        fixture.dispatcher.dispatch(event(NotificationSource.JMA_FORECAST));
        run(fixture);
        assertEquals(1, player.chats().size());

        player.backend("survival");
        fixture.dispatcher.dispatch(event(NotificationSource.CENC_EEW));
        run(fixture);
        assertEquals(2, player.chats().size(), "default all applies to the unrelated source");
    }

    @Test
    void suppressionIsBooleanDefaultReceiveAndReevaluatedForEachDelivery() {
        Fixture fixture = fixture(BungeeNotificationTestSupport.config().build());
        BungeeNotificationTestSupport.FakePlayer player =
                fixture.platform.addPlayer("player", "lobby");
        NotificationSource source = NotificationSource.JMA_FORECAST;

        fixture.dispatcher.dispatch(event(source));
        run(fixture);
        assertEquals(1, player.chats().size(), "absent suppression means receive");

        player.permission(BungeePermissions.suppressionFor(source), true);
        fixture.dispatcher.dispatch(event(source));
        run(fixture);
        assertEquals(1, player.chats().size(), "source grant suppresses only that source");

        fixture.dispatcher.dispatch(event(NotificationSource.JMA_ALERT));
        run(fixture);
        assertEquals(2, player.chats().size(), "different source remains eligible");

        player.clearPermission(BungeePermissions.suppressionFor(source));
        player.permission(BungeePermissions.SUPPRESS_ALL, true);
        fixture.dispatcher.dispatch(event(source));
        run(fixture);
        assertEquals(2, player.chats().size(), "global grant suppresses every source");

        player.permission(BungeePermissions.SUPPRESS_ALL, false);
        fixture.dispatcher.dispatch(event(source));
        run(fixture);
        assertEquals(3, player.chats().size(), "explicit false resumes delivery without reload");
        assertTrue(player.permissionQueries().containsAll(List.of(
                BungeePermissions.SUPPRESS_ALL,
                BungeePermissions.suppressionFor(source))));
    }

    @Test
    void everyConcreteSourceSuppressionDeniesOnlyItsMappedSource() {
        Fixture fixture = fixture(BungeeNotificationTestSupport.config().build());
        java.util.Map<NotificationSource, BungeeNotificationTestSupport.FakePlayer> players =
                new java.util.EnumMap<>(NotificationSource.class);
        for (NotificationSource source : BungeePermissions.sourceSuppressions().keySet()) {
            BungeeNotificationTestSupport.FakePlayer player = fixture.platform.addPlayer(
                    source.name(), "lobby");
            player.permission(BungeePermissions.suppressionFor(source), true);
            players.put(source, player);
        }
        BungeeNotificationTestSupport.FakePlayer globallySuppressed =
                fixture.platform.addPlayer("global", "lobby");
        globallySuppressed.permission(BungeePermissions.SUPPRESS_ALL, true);

        for (NotificationSource source : BungeePermissions.sourceSuppressions().keySet()) {
            fixture.dispatcher.dispatch(event(source));
        }
        run(fixture);

        for (java.util.Map.Entry<NotificationSource, BungeeNotificationTestSupport.FakePlayer>
                entry : players.entrySet()) {
            assertEquals(8, entry.getValue().chats().size(), entry.getKey().name());
            assertFalse(entry.getValue().chats().stream()
                    .anyMatch(message -> message.contains(entry.getKey().name())),
                    entry.getKey().name());
        }
        assertTrue(globallySuppressed.chats().isEmpty());
        assertEquals(9, fixture.platform.consoleMessages().size());
    }

    @Test
    void sourceServerAndServerSourceChannelPrecedenceAppliesIndependently() {
        NotificationSource source = NotificationSource.CENC_EEW;
        Fixture fixture = fixture(BungeeNotificationTestSupport.config()
                .defaults(true, false)
                .sourceChannels(source, false, true)
                .server("lobby", true, false, source, false, true)
                .server("survival", true, false, null, null, null)
                .build());
        BungeeNotificationTestSupport.FakePlayer lobby =
                fixture.platform.addPlayer("lobby", "lobby");
        BungeeNotificationTestSupport.FakePlayer survival =
                fixture.platform.addPlayer("survival", "survival");
        BungeeNotificationTestSupport.FakePlayer noBackend =
                fixture.platform.addPlayer("connecting", null);

        fixture.dispatcher.dispatch(event(source));
        run(fixture);

        assertTrue(lobby.chats().isEmpty(), "server+source broadcast wins");
        assertEquals(1, lobby.titles().size(), "server+source title wins");
        assertEquals(1, survival.chats().size(), "server broadcast wins over source");
        assertTrue(survival.titles().isEmpty(), "server title wins over source");
        assertTrue(noBackend.chats().isEmpty(), "source broadcast wins over global");
        assertEquals(1, noBackend.titles().size(), "source title wins over global");
        assertTrue(fixture.platform.consoleMessages().isEmpty(),
                "console uses global+source only and source disabled broadcast");
    }

    @Test
    void serverOverridesAndSuppressionNeverAffectConsole() {
        NotificationSource source = NotificationSource.JMA_ALERT;
        Fixture fixture = fixture(BungeeNotificationTestSupport.config()
                .server("lobby", false, false, source, false, false)
                .build());
        BungeeNotificationTestSupport.FakePlayer player =
                fixture.platform.addPlayer("player", "lobby");
        player.permission(BungeePermissions.SUPPRESS_ALL, true);

        fixture.dispatcher.dispatch(event(source));
        run(fixture);

        assertTrue(player.chats().isEmpty());
        assertTrue(player.titles().isEmpty());
        assertEquals(1, fixture.platform.consoleMessages().size());
    }

    @Test
    void chatTitleConsoleAndBackendFailuresAreIsolated() {
        Fixture fixture = fixture(BungeeNotificationTestSupport.config().build());
        BungeeNotificationTestSupport.FakePlayer broken =
                fixture.platform.addPlayer("broken", "lobby");
        broken.failChat(true);
        broken.failTitle(true);
        BungeeNotificationTestSupport.FakePlayer racing =
                fixture.platform.addPlayer("racing", "lobby");
        racing.failBackend(true);
        BungeeNotificationTestSupport.FakePlayer healthy =
                fixture.platform.addPlayer("healthy", "lobby");
        fixture.platform.failConsole(true);

        fixture.dispatcher.dispatch(event(NotificationSource.SICHUAN_EEW));
        run(fixture);

        assertEquals(1, healthy.chats().size());
        assertEquals(1, healthy.titles().size());
        assertTrue(broken.chats().isEmpty());
        assertTrue(broken.titles().isEmpty());
        assertEquals(1, racing.chats().size(),
                "all-target delivery falls back to global/source channels");
        assertEquals(1, racing.titles().size());
    }

    @Test
    void permissionFailureSkipsOnlyThatPlayerAndDoesNotAbortOthers() {
        Fixture fixture = fixture(BungeeNotificationTestSupport.config().build());
        BungeeNotificationTestSupport.FakePlayer failed =
                fixture.platform.addPlayer("permission-failed", "lobby");
        failed.failPermission(true);
        BungeeNotificationTestSupport.FakePlayer healthy =
                fixture.platform.addPlayer("healthy", "lobby");

        fixture.dispatcher.dispatch(event(NotificationSource.JMA_FORECAST));
        run(fixture);

        assertTrue(failed.chats().isEmpty());
        assertTrue(failed.titles().isEmpty());
        assertEquals(1, healthy.chats().size());
        assertEquals(1, healthy.titles().size());
        assertEquals(1, fixture.platform.consoleMessages().size());
    }

    @Test
    void schedulerFailureDropsNotificationWithoutLeakingOrThrowing() {
        Fixture fixture = fixture(BungeeNotificationTestSupport.config().build());
        BungeeNotificationTestSupport.FakePlayer player =
                fixture.platform.addPlayer("player", "lobby");
        fixture.backend.failSubmissions = true;

        fixture.dispatcher.dispatch(event(NotificationSource.CENC_EEW));
        boolean testAccepted = fixture.dispatcher.dispatchTest(
                event(NotificationSource.JMA_ALERT), "test warning");

        assertFalse(testAccepted);
        assertEquals(0, fixture.dispatcher.pendingDeliveryCount());
        assertTrue(player.chats().isEmpty());
        assertTrue(fixture.platform.consoleMessages().isEmpty());
    }

    @Test
    void closeActivelyCancelsQueuedTasksAndCompletedTasksDoNotLeak() {
        Fixture fixture = fixture(BungeeNotificationTestSupport.config().build());
        BungeeNotificationTestSupport.FakePlayer player =
                fixture.platform.addPlayer("player", "lobby");

        fixture.dispatcher.dispatch(event(NotificationSource.SICHUAN_EEW));
        assertEquals(1, fixture.dispatcher.pendingDeliveryCount());
        fixture.dispatcher.close();
        assertEquals(0, fixture.dispatcher.pendingDeliveryCount());
        run(fixture);
        assertTrue(player.chats().isEmpty());

        Fixture completed = fixture(BungeeNotificationTestSupport.config().build());
        BungeeNotificationTestSupport.FakePlayer completedPlayer =
                completed.platform.addPlayer("completed", "lobby");
        for (int index = 0; index < 100; index++) {
            completed.dispatcher.dispatch(event(NotificationSource.SICHUAN_EEW));
        }
        run(completed);
        assertEquals(100, completedPlayer.chats().size());
        assertEquals(0, completed.dispatcher.pendingDeliveryCount());
    }

    @Test
    void deliveryAlreadyStartedBeforeCloseMayFinishWithoutResurrection() {
        Fixture fixture = fixture(BungeeNotificationTestSupport.config().build());
        BungeeNotificationTestSupport.FakePlayer player =
                fixture.platform.addPlayer("player", "lobby");
        fixture.backend.runImmediately = true;

        fixture.dispatcher.dispatch(event(NotificationSource.SICHUAN_EEW));
        fixture.dispatcher.close();

        assertEquals(1, player.chats().size());
        assertEquals(1, player.titles().size());
        assertEquals(0, fixture.dispatcher.pendingDeliveryCount());
    }

    @Test
    void closedDispatcherSuppressesAlreadyQueuedAndFutureDelivery() {
        Fixture fixture = fixture(BungeeNotificationTestSupport.config().build());
        BungeeNotificationTestSupport.FakePlayer player =
                fixture.platform.addPlayer("player", "lobby");

        fixture.dispatcher.dispatch(event(NotificationSource.SICHUAN_EEW));
        fixture.dispatcher.close();
        fixture.dispatcher.dispatch(event(NotificationSource.CENC_EEW));
        run(fixture);

        assertTrue(player.chats().isEmpty());
        assertTrue(fixture.platform.consoleMessages().isEmpty());
        assertTrue(fixture.dispatcher.isClosed());
    }

    private static Fixture fixture(BungeeConfigSnapshot config) {
        BungeeNotificationTestSupport.FakePlatform platform =
                new BungeeNotificationTestSupport.FakePlatform();
        BungeeDelaySchedulerTest.FakeBackend backend =
                new BungeeDelaySchedulerTest.FakeBackend();
        BungeeDelayScheduler scheduler = new BungeeDelayScheduler(backend);
        BungeeNotificationDispatcher dispatcher = new BungeeNotificationDispatcher(
                platform, scheduler, config, BungeeNotificationTestSupport.logger("dispatcher"));
        return new Fixture(platform, backend, dispatcher);
    }

    private static BungeeNotificationEvent event(NotificationSource source) {
        return BungeeNotificationTestSupport.event(source);
    }

    private static void run(Fixture fixture) {
        BungeeNotificationTestSupport.runAll(fixture.backend);
    }

    private static final class Fixture {
        private final BungeeNotificationTestSupport.FakePlatform platform;
        private final BungeeDelaySchedulerTest.FakeBackend backend;
        private final BungeeNotificationDispatcher dispatcher;

        private Fixture(
                BungeeNotificationTestSupport.FakePlatform platform,
                BungeeDelaySchedulerTest.FakeBackend backend,
                BungeeNotificationDispatcher dispatcher
        ) {
            this.platform = platform;
            this.backend = backend;
            this.dispatcher = dispatcher;
        }
    }
}
