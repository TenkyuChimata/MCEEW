package jp.wolfx.mceew;

import jp.wolfx.mceew.notification.NotificationIntent;
import jp.wolfx.mceew.notification.NotificationIntentFactory;
import jp.wolfx.mceew.notification.NotificationProfile;
import jp.wolfx.mceew.notification.NotificationSource;
import jp.wolfx.mceew.scheduler.PlatformScheduler;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BukkitNotificationDispatcherTest {
    @Test
    void jmaBroadcastOnlyDeliversConsoleAndPermittedPlayerChat() {
        DispatcherHarness harness = new DispatcherHarness(1);

        harness.dispatcher.deliverJma(() -> jma(true, false, false, "mceew:jma"));

        assertEquals(List.of("chat:region"), harness.console);
        assertEquals(List.of("chat:region"), harness.player(0).chat);
        assertTrue(harness.player(0).titles.isEmpty());
        assertTrue(harness.player(0).sounds.isEmpty());
        assertEquals(List.of("mceew.notify.all", "mceew.notify.jma.alert"),
                harness.player(0).permissionQueries);
    }

    @Test
    void jmaTitleOnlyPreservesTextAndTimings() {
        DispatcherHarness harness = new DispatcherHarness(1);

        harness.dispatcher.deliverJma(() -> jma(false, true, false, "mceew:jma"));

        assertTrue(harness.console.isEmpty());
        assertTrue(harness.player(0).chat.isEmpty());
        assertEquals(1, harness.player(0).titles.size());
        MceewCharacterizationSupport.RecordedTitle title = harness.player(0).titles.get(0);
        assertEquals("title:region", title.title);
        assertEquals("subtitle:intensity", title.subtitle);
        assertEquals(10, title.fadeIn);
        assertEquals(70, title.stay);
        assertEquals(20, title.fadeOut);
        assertTrue(harness.player(0).sounds.isEmpty());
    }

    @Test
    void jmaSoundOnlyPreservesValidKeyVolumeAndPitch() {
        DispatcherHarness harness = new DispatcherHarness(1);

        harness.dispatcher.deliverJma(() -> jma(false, false, true, "mceew:jma"));

        assertTrue(harness.console.isEmpty());
        assertEquals(1, harness.player(0).sounds.size());
        MceewCharacterizationSupport.RecordedSound sound = harness.player(0).sounds.get(0);
        assertEquals("mceew:jma", sound.key);
        assertEquals(2.5F, sound.volume);
        assertEquals(0.75F, sound.pitch);
        assertTrue(harness.warnings.isEmpty());
    }

    @Test
    void jmaAllActionsDisabledStillIteratesButDoesNotQueryPermission() {
        DispatcherHarness harness = new DispatcherHarness(1);

        harness.dispatcher.deliverJma(() -> jma(false, false, false, "mceew:jma"));

        assertEquals(1, harness.scheduler.forEachPlayerCalls);
        assertTrue(harness.player(0).permissionQueries.isEmpty());
        assertTrue(harness.console.isEmpty());
        assertTrue(harness.player(0).chat.isEmpty());
        assertTrue(harness.player(0).titles.isEmpty());
        assertTrue(harness.player(0).sounds.isEmpty());
    }

    @Test
    void jmaChecksPermissionIndependentlyForEveryEnabledChannel() {
        DispatcherHarness harness = new DispatcherHarness(1);

        harness.dispatcher.deliverJma(() -> jma(true, true, true, "mceew:jma"));

        assertEquals(List.of(
                        "mceew.notify.all", "mceew.notify.jma.alert",
                        "mceew.notify.all", "mceew.notify.jma.alert",
                        "mceew.notify.all", "mceew.notify.jma.alert"),
                harness.player(0).permissionQueries);
    }

    @Test
    void regionalAllActionsDisabledStillPerformsOnePermissionDecision() {
        DispatcherHarness harness = new DispatcherHarness(1);

        harness.dispatcher.deliverRegional(
                NotificationSource.SICHUAN_EEW,
                () -> regional(false, false, false, "mceew:regional"));

        assertEquals(List.of("mceew.notify.all", "mceew.notify.sc"),
                harness.player(0).permissionQueries);
        assertTrue(harness.console.isEmpty());
        assertTrue(harness.player(0).chat.isEmpty());
        assertTrue(harness.player(0).titles.isEmpty());
        assertTrue(harness.player(0).sounds.isEmpty());
    }

    @Test
    void regionalPermissionDenialDoesNotRenderMalformedPlayerText() {
        DispatcherHarness harness = new DispatcherHarness(1);
        harness.player(0).permissions.put("mceew.notify.sc", false);

        assertDoesNotThrow(() -> harness.dispatcher.deliverRegional(
                NotificationSource.SICHUAN_EEW,
                () -> regionalWithRegion(false, true, false, "mceew:regional", "$1")));

        assertEquals(List.of("mceew.notify.all", "mceew.notify.sc"),
                harness.player(0).permissionQueries);
        assertTrue(harness.player(0).titles.isEmpty());
    }

    @Test
    void realtimeConsoleDeliveryIsIndependentOfPlayerPermission() {
        DispatcherHarness harness = new DispatcherHarness(1);
        harness.player(0).permissions.put("mceew.notify.all", false);

        harness.dispatcher.deliverRegional(
                NotificationSource.SICHUAN_EEW,
                () -> regional(true, false, false, "mceew:regional"));

        assertEquals(List.of("chat:region"), harness.console);
        assertTrue(harness.player(0).chat.isEmpty());
        assertEquals(List.of("mceew.notify.all"),
                harness.player(0).permissionQueries);
    }

    @Test
    void invalidSoundWarnsAndSkipsPlayback() {
        DispatcherHarness harness = new DispatcherHarness(1);

        harness.dispatcher.deliverRegional(
                NotificationSource.SICHUAN_EEW,
                () -> regional(false, false, true, "BAD SOUND"));

        assertTrue(harness.player(0).sounds.isEmpty());
        assertEquals(List.of("Unknown sound type: BAD SOUND"), harness.warnings);
    }

    @Test
    void earthquakeListDeliversConsoleAndPermittedPlayerChatOnly() {
        DispatcherHarness harness = new DispatcherHarness(1);
        NotificationIntent intent = earthquakeList(
                NotificationSource.JMA_EARTHQUAKE_LIST, "list-message");

        harness.dispatcher.deliverEarthquakeList(intent);

        assertEquals(List.of("list-message"), harness.console);
        assertEquals(List.of("list-message"), harness.player(0).chat);
        assertTrue(harness.player(0).titles.isEmpty());
        assertTrue(harness.player(0).sounds.isEmpty());
        assertEquals(List.of("mceew.notify.all", "mceew.notify.jma.eqlist"),
                harness.player(0).permissionQueries);
    }

    @Test
    void earthquakeListConsoleIntentRemainsIndependentOfRealtimeBroadcastAction() {
        DispatcherHarness harness = new DispatcherHarness(1);
        NotificationIntent noRealtimeBroadcast = regional(
                false, true, true, "mceew:regional");
        NotificationIntent list = earthquakeList(
                NotificationSource.CENC_EARTHQUAKE_LIST, "cenc-list");

        assertFalse(noRealtimeBroadcast.isConsoleDelivery());
        assertTrue(list.isConsoleDelivery());
        harness.dispatcher.deliverEarthquakeList(list);

        assertEquals(List.of("cenc-list"), harness.console);
        assertEquals(List.of("cenc-list"), harness.player(0).chat);
    }

    @Test
    void permissionRuleRemainsAllAndSource() {
        assertRegionalPermissionDecision(true, true, true);
        assertRegionalPermissionDecision(true, false, false);
        assertRegionalPermissionDecision(false, true, false);
        assertRegionalPermissionDecision(false, false, false);
    }

    @Test
    void playerOperationsDelegateToPlatformSchedulerEnumeration() {
        DispatcherHarness harness = new DispatcherHarness(2);

        harness.dispatcher.deliverRegional(
                NotificationSource.SICHUAN_EEW,
                () -> regional(false, true, false, "mceew:regional"));

        assertEquals(1, harness.scheduler.forEachPlayerCalls);
        assertEquals(0, harness.scheduler.runGlobalCalls,
                "player operations are left to PlatformScheduler.forEachPlayer");
        assertEquals(1, harness.player(0).titles.size());
        assertEquals(1, harness.player(1).titles.size());
    }

    @Test
    void fixedTestWarningUsesConsoleAndEveryPlayerWithoutPermissions() {
        DispatcherHarness harness = new DispatcherHarness(2);

        harness.dispatcher.deliverTestWarning("warning");

        assertEquals(List.of("warning"), harness.console);
        assertEquals(List.of("warning"), harness.player(0).chat);
        assertEquals(List.of("warning"), harness.player(1).chat);
        assertTrue(harness.player(0).permissionQueries.isEmpty());
        assertTrue(harness.player(1).permissionQueries.isEmpty());
    }

    private static NotificationIntent jma(
            boolean broadcast, boolean title, boolean alert, String soundKey) {
        NotificationProfile profile = profile(soundKey);
        return NotificationIntentFactory.jma(
                "警報", "report", "origin", "1", "lat", "lon", "region", "mag",
                "depth", "intensity", "type", broadcast, title, alert, profile, profile);
    }

    private static NotificationIntent regional(
            boolean broadcast, boolean title, boolean alert, String soundKey) {
        return regionalWithRegion(broadcast, title, alert, soundKey, "region");
    }

    private static NotificationIntent regionalWithRegion(
            boolean broadcast,
            boolean title,
            boolean alert,
            String soundKey,
            String region
    ) {
        return NotificationIntentFactory.regional(
                NotificationSource.SICHUAN_EEW,
                "report", "origin", "1", "lat", "lon", region, "mag", "depth", "intensity",
                broadcast, title, alert, profile(soundKey));
    }

    private static NotificationProfile profile(String soundKey) {
        return new NotificationProfile(
                "chat:%region%",
                "title:%region%",
                "subtitle:%shindo%",
                soundKey,
                2.5D,
                0.75D);
    }

    private static NotificationIntent earthquakeList(
            NotificationSource source, String message) {
        return NotificationIntentFactory.earthquakeList(
                source, true, true, () -> message).orElseThrow();
    }

    private static void assertRegionalPermissionDecision(
            boolean all, boolean source, boolean expectedReceive) {
        DispatcherHarness harness = new DispatcherHarness(1);
        harness.player(0).permissions.put("mceew.notify.all", all);
        harness.player(0).permissions.put("mceew.notify.sc", source);

        harness.dispatcher.deliverRegional(
                NotificationSource.SICHUAN_EEW,
                () -> regional(true, false, false, "mceew:regional"));

        assertEquals(expectedReceive, !harness.player(0).chat.isEmpty(),
                "all=" + all + ", source=" + source);
        assertEquals(List.of("chat:region"), harness.console,
                "console does not consult player permissions");
    }

    private static final class DispatcherHarness {
        private final List<MceewCharacterizationSupport.RecordingPlayer> players =
                new ArrayList<>();
        private final List<String> console = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();
        private final RecordingScheduler scheduler;
        private final BukkitNotificationDispatcher dispatcher;

        private DispatcherHarness(int playerCount) {
            List<Player> proxies = new ArrayList<>();
            for (int index = 0; index < playerCount; index++) {
                MceewCharacterizationSupport.RecordingPlayer player =
                        new MceewCharacterizationSupport.RecordingPlayer();
                players.add(player);
                proxies.add(player.proxy);
            }
            scheduler = new RecordingScheduler(proxies);
            Logger logger = Logger.getAnonymousLogger();
            logger.setUseParentHandlers(false);
            logger.addHandler(new Handler() {
                @Override
                public void publish(LogRecord record) {
                    warnings.add(record.getMessage());
                }

                @Override
                public void flush() {
                }

                @Override
                public void close() {
                }
            });
            dispatcher = new BukkitNotificationDispatcher(
                    scheduler, logger, console::add);
        }

        private MceewCharacterizationSupport.RecordingPlayer player(int index) {
            return players.get(index);
        }
    }

    private static final class RecordingScheduler implements PlatformScheduler {
        private final List<Player> players;
        private int runGlobalCalls;
        private int forEachPlayerCalls;

        private RecordingScheduler(List<Player> players) {
            this.players = players;
        }

        @Override
        public boolean isFolia() {
            return false;
        }

        @Override
        public void runAsync(Runnable task) {
            task.run();
        }

        @Override
        public TaskHandle runAsyncDelayed(Runnable task, long delay, TimeUnit unit) {
            return () -> {
            };
        }

        @Override
        public void runGlobal(Runnable task) {
            runGlobalCalls++;
            task.run();
        }

        @Override
        public void forEachPlayer(Consumer<Player> action) {
            forEachPlayerCalls++;
            players.forEach(action);
        }

        @Override
        public void cancelTasks() {
        }
    }
}
