package jp.wolfx.mceew;

import com.google.gson.JsonObject;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MceewNotificationCharacterizationTest {
    @Test
    void broadcastFalseSuppressesBothPlayerChatAndConsoleButNotTitleOrSound() {
        YamlConfiguration config = MceewCharacterizationSupport.defaultConfiguration();
        config.set("Action.broadcast", false);
        MceewCharacterizationSupport.Harness harness =
                MceewCharacterizationSupport.harness(config);

        harness.routeFresh("sc_eew");

        assertTrue(harness.console.isEmpty());
        assertTrue(harness.player.chat.isEmpty());
        assertEquals(1, harness.player.titles.size());
        assertEquals(1, harness.player.sounds.size());
    }

    @Test
    void titleFalseSuppressesTitleOnlyAndAlertFalseSuppressesSoundOnly() {
        YamlConfiguration noTitle = MceewCharacterizationSupport.defaultConfiguration();
        noTitle.set("Action.title", false);
        MceewCharacterizationSupport.Harness titleHarness =
                MceewCharacterizationSupport.harness(noTitle);
        titleHarness.routeFresh("fj_eew");
        assertEquals(1, titleHarness.console.size());
        assertEquals(1, titleHarness.player.chat.size());
        assertTrue(titleHarness.player.titles.isEmpty());
        assertEquals(1, titleHarness.player.sounds.size());

        YamlConfiguration noAlert = MceewCharacterizationSupport.defaultConfiguration();
        noAlert.set("Action.alert", false);
        MceewCharacterizationSupport.Harness alertHarness =
                MceewCharacterizationSupport.harness(noAlert);
        alertHarness.routeFresh("cwa_eew");
        assertEquals(1, alertHarness.console.size());
        assertEquals(1, alertHarness.player.chat.size());
        assertEquals(1, alertHarness.player.titles.size());
        assertTrue(alertHarness.player.sounds.isEmpty());
    }

    @Test
    void permissionDecisionRequiresAllAndTheSourceSpecificNode() {
        assertPermissionDecision(true, true, true);
        assertPermissionDecision(true, false, false);
        assertPermissionDecision(false, true, false);
        assertPermissionDecision(false, false, false);
    }

    @Test
    void permissionDenialDoesNotSuppressRealtimeConsoleBroadcast() {
        MceewCharacterizationSupport.Harness harness = MceewCharacterizationSupport.harness();
        harness.player.permissions.put("mceew.notify.all", false);
        harness.player.permissions.put("mceew.notify.cenc.eew", false);

        harness.routeFresh("cenc_eew");

        assertEquals(1, harness.console.size());
        assertTrue(harness.player.chat.isEmpty());
        assertTrue(harness.player.titles.isEmpty());
        assertTrue(harness.player.sounds.isEmpty());
    }

    @Test
    void jmaAlertAndForecastUseSeparatePermissionAndConfigurationPaths() {
        YamlConfiguration config = MceewCharacterizationSupport.defaultConfiguration();
        config.set("Message.Alert.broadcast", "ALERT:%flag%");
        config.set("Message.Forecast.broadcast", "FORECAST:%flag%");
        config.set("Sound.Alert.type", "mceew:alert");
        config.set("Sound.Forecast.type", "mceew:forecast");
        MceewCharacterizationSupport.Harness alert =
                MceewCharacterizationSupport.harness(config);
        alert.routeFresh("jma_eew");
        assertEquals(List.of("ALERT:警報"), alert.player.chat);
        assertEquals("mceew:alert", alert.player.sounds.get(0).key);
        assertTrue(alert.player.queriedPermissions.contains("mceew.notify.jma.alert"));
        assertFalse(alert.player.queriedPermissions.contains("mceew.notify.jma.forecast"));

        MceewCharacterizationSupport.Harness forecast =
                MceewCharacterizationSupport.harness(config);
        com.google.gson.JsonObject payload = com.google.gson.JsonParser.parseString(
                MceewCharacterizationSupport.freshPayload("jma_eew")).getAsJsonObject();
        payload.addProperty("Title", "緊急地震速報（予報）");
        forecast.route(payload.toString());
        assertEquals(List.of("FORECAST:予報"), forecast.player.chat);
        assertEquals("mceew:forecast", forecast.player.sounds.get(0).key);
        assertTrue(forecast.player.queriedPermissions.contains("mceew.notify.jma.forecast"));
        assertFalse(forecast.player.queriedPermissions.contains("mceew.notify.jma.alert"));
    }

    @Test
    void chongqingUsesItsOwnEnableMessageSoundAndPermissionPaths() {
        YamlConfiguration config = MceewCharacterizationSupport.defaultConfiguration();
        config.set("Message.Chongqing.broadcast", "CQ:%num%:%region%");
        config.set("Message.CencEEW.broadcast", "WRONG-CENC");
        config.set("Sound.Chongqing.type", "mceew:cq");
        config.set("Sound.Chongqing.volume", 2.5D);
        config.set("Sound.Chongqing.pitch", 0.75D);
        config.set("Sound.CencEEW.type", "mceew:wrong");
        MceewCharacterizationSupport.Harness harness =
                MceewCharacterizationSupport.harness(config);

        harness.routeFresh("cq_eew");

        assertEquals(List.of("CQ:1:四川宜宾市高县"), harness.console);
        assertEquals(harness.console, harness.player.chat);
        assertTrue(harness.player.queriedPermissions.contains("mceew.notify.cq"));
        assertFalse(harness.player.queriedPermissions.contains("mceew.notify.cenc.eew"));
        assertEquals("mceew:cq", harness.player.sounds.get(0).key);
        assertEquals(2.5F, harness.player.sounds.get(0).volume);
        assertEquals(0.75F, harness.player.sounds.get(0).pitch);

        MceewCharacterizationSupport.field(harness.plugin, "cqEewBoolean", false);
        harness.clearOutput();
        harness.routeFresh("cq_eew");
        assertTrue(harness.console.isEmpty());
        assertTrue(harness.player.chat.isEmpty());
    }

    @Test
    void allRealtimeActionsDisabledPreserveCurrentPermissionQueryShape() {
        YamlConfiguration config = MceewCharacterizationSupport.defaultConfiguration();
        config.set("Action.broadcast", false);
        config.set("Action.title", false);
        config.set("Action.alert", false);

        MceewCharacterizationSupport.Harness regional =
                MceewCharacterizationSupport.harness(config);
        regional.routeFresh("sc_eew");
        assertEquals(List.of("mceew.notify.all", "mceew.notify.sc"),
                List.copyOf(regional.player.queriedPermissions));
        assertTrue(regional.console.isEmpty());
        assertTrue(regional.player.chat.isEmpty());
        assertTrue(regional.player.titles.isEmpty());
        assertTrue(regional.player.sounds.isEmpty());

        MceewCharacterizationSupport.Harness jma =
                MceewCharacterizationSupport.harness(config);
        jma.routeFresh("jma_eew");
        assertTrue(jma.player.queriedPermissions.isEmpty());
        assertTrue(jma.console.isEmpty());
        assertTrue(jma.player.chat.isEmpty());
        assertTrue(jma.player.titles.isEmpty());
        assertTrue(jma.player.sounds.isEmpty());
    }

    @Test
    void earthquakeListChangedNotificationsRemainIndependentOfBroadcastAction() {
        YamlConfiguration config = MceewCharacterizationSupport.defaultConfiguration();
        config.set("Action.broadcast", false);
        MceewCharacterizationSupport.Harness harness =
                MceewCharacterizationSupport.harness(config);
        JsonObject jma = MceewCharacterizationSupport.fixture("jma_eqlist");
        JsonObject cenc = MceewCharacterizationSupport.fixture("cenc_eqlist");

        harness.route(jma.toString());
        harness.route(cenc.toString());
        jma.addProperty("md5", "11111111111111111111111111111111");
        cenc.addProperty("md5", "22222222222222222222222222222222");
        harness.route(jma.toString());
        harness.route(cenc.toString());

        assertEquals(2, harness.console.size());
        assertEquals(harness.console, harness.player.chat);
        assertTrue(harness.player.titles.isEmpty());
        assertTrue(harness.player.sounds.isEmpty());
    }

    private static void assertPermissionDecision(
            boolean all, boolean source, boolean expectedReceive) {
        MceewCharacterizationSupport.Harness harness = MceewCharacterizationSupport.harness();
        harness.player.permissions.put("mceew.notify.all", all);
        harness.player.permissions.put("mceew.notify.sc", source);
        harness.routeFresh("sc_eew");

        assertEquals(expectedReceive, !harness.player.chat.isEmpty(),
                "all=" + all + ", source=" + source);
        assertEquals(expectedReceive, !harness.player.titles.isEmpty(),
                "all=" + all + ", source=" + source);
        assertEquals(expectedReceive, !harness.player.sounds.isEmpty(),
                "all=" + all + ", source=" + source);
        assertEquals(1, harness.console.size(),
                "console does not consult player permissions");
    }
}
