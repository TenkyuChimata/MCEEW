package jp.wolfx.mceew.notification;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotificationIntentFactoryTest {
    private static final NotificationProfile REGIONAL_PROFILE = new NotificationProfile(
            "chat:%report_time%|%origin_time%|%num%|%lat%|%lon%|%region%|%mag%|%depth%|%shindo%",
            "title:%region%",
            "subtitle:%shindo%",
            "mceew:regional",
            2.5D,
            0.75D);

    @Test
    void jmaAlertBuildsCurrentAlertIntent() {
        NotificationIntent intent = jma("警報");

        assertEquals(NotificationSource.JMA_ALERT, intent.getSource());
        assertEquals("mceew.notify.jma.alert", intent.getPermissionNode());
        assertRealtime(
                intent,
                "alert:警報|report|origin|46|37.6|137.2|能登半島沖|7.4|10km|§d7|最終報",
                "alert-title:警報",
                "alert-subtitle:能登半島沖:§d7",
                "mceew:alert");
    }

    @Test
    void jmaForecastBuildsCurrentForecastIntent() {
        NotificationIntent intent = jma("予報");

        assertEquals(NotificationSource.JMA_FORECAST, intent.getSource());
        assertEquals("mceew.notify.jma.forecast", intent.getPermissionNode());
        assertRealtime(
                intent,
                "forecast:予報|report|origin|46|37.6|137.2|能登半島沖|7.4|10km|§d7|最終報",
                "forecast-title:予報",
                "forecast-subtitle:能登半島沖:§d7",
                "mceew:forecast");
    }

    @Test
    void sichuanBuildsCurrentRegionalIntent() {
        assertRegional(NotificationSource.SICHUAN_EEW, "mceew.notify.sc");
    }

    @Test
    void cwaBuildsCurrentRegionalIntent() {
        assertRegional(NotificationSource.CWA_EEW, "mceew.notify.cwa");
    }

    @Test
    void cencEewBuildsCurrentRegionalIntent() {
        assertRegional(NotificationSource.CENC_EEW, "mceew.notify.cenc.eew");
    }

    @Test
    void chongqingBuildsCurrentRegionalIntent() {
        assertRegional(NotificationSource.CHONGQING_EEW, "mceew.notify.cq");
    }

    @Test
    void fujianBuildsItsCurrentTypeAwareIntent() {
        NotificationProfile profile = new NotificationProfile(
                "chat:%report_time%|%origin_time%|%num%|%lat%|%lon%|%region%|%mag%|%type%",
                "title:%region%",
                "subtitle:%mag%:%type%",
                "mceew:fujian",
                2.5D,
                0.75D);
        NotificationIntent intent = NotificationIntentFactory.fujian(
                "report", "origin", "4", "23.47", "120.26", "台湾嘉义县", "4.4", "最終報",
                true, true, true, profile);

        assertEquals(NotificationSource.FUJIAN_EEW, intent.getSource());
        assertEquals("mceew.notify.fj", intent.getPermissionNode());
        assertRealtime(
                intent,
                "chat:report|origin|4|23.47|120.26|台湾嘉义县|4.4|最終報",
                "title:台湾嘉义县",
                "subtitle:4.4:最終報",
                "mceew:fujian");
    }

    @Test
    void jmaEarthquakeListChangedBuildsChatAndConsoleOnly() {
        NotificationIntent intent = NotificationIntentFactory.earthquakeList(
                NotificationSource.JMA_EARTHQUAKE_LIST,
                true,
                true,
                () -> "JMA changed").orElseThrow();

        assertEarthquakeList(
                intent,
                NotificationSource.JMA_EARTHQUAKE_LIST,
                "mceew.notify.jma.eqlist",
                "JMA changed");
    }

    @Test
    void cencEarthquakeListChangedBuildsChatAndConsoleOnly() {
        NotificationIntent intent = NotificationIntentFactory.earthquakeList(
                NotificationSource.CENC_EARTHQUAKE_LIST,
                true,
                true,
                () -> "CENC changed").orElseThrow();

        assertEarthquakeList(
                intent,
                NotificationSource.CENC_EARTHQUAKE_LIST,
                "mceew.notify.cenc.eqlist",
                "CENC changed");
    }

    @Test
    void broadcastFalseSuppressesChatAndConsoleOnly() {
        NotificationIntent intent = regional(false, true, true);

        assertFalse(intent.isConsoleDelivery());
        assertNull(intent.getChat());
        assertEquals("title:region", intent.getTitle().renderTitle());
        assertEquals("mceew:regional", intent.getSound().getKey());
    }

    @Test
    void titleFalseSuppressesTitleOnly() {
        NotificationIntent intent = regional(true, false, true);

        assertTrue(intent.isConsoleDelivery());
        assertEquals("chat:report|origin|1|lat|lon|region|mag|depth|intensity",
                intent.getChat().render());
        assertNull(intent.getTitle());
        assertEquals("mceew:regional", intent.getSound().getKey());
    }

    @Test
    void alertFalseSuppressesSoundOnly() {
        NotificationIntent intent = regional(true, true, false);

        assertTrue(intent.isConsoleDelivery());
        assertEquals("title:region", intent.getTitle().renderTitle());
        assertNull(intent.getSound());
    }

    @Test
    void jmaActionFalseSuppressesChangedListIntentWithoutFormatting() {
        assertDisabledList(NotificationSource.JMA_EARTHQUAKE_LIST);
    }

    @Test
    void cencActionFalseSuppressesChangedListIntentWithoutFormatting() {
        assertDisabledList(NotificationSource.CENC_EARTHQUAKE_LIST);
    }

    @Test
    void firstOrUnchangedListResultProducesNoIntentEvenWhenActionIsEnabled() {
        AtomicInteger formattingCalls = new AtomicInteger();

        Optional<NotificationIntent> intent = NotificationIntentFactory.earthquakeList(
                NotificationSource.JMA_EARTHQUAKE_LIST,
                false,
                true,
                () -> {
                    formattingCalls.incrementAndGet();
                    return "not used";
                });

        assertTrue(intent.isEmpty());
        assertEquals(0, formattingCalls.get());
    }

    @Test
    void everySourceKeepsItsExistingPermissionNode() {
        assertEquals(Map.ofEntries(
                        Map.entry(NotificationSource.JMA_ALERT, "mceew.notify.jma.alert"),
                        Map.entry(NotificationSource.JMA_FORECAST, "mceew.notify.jma.forecast"),
                        Map.entry(NotificationSource.SICHUAN_EEW, "mceew.notify.sc"),
                        Map.entry(NotificationSource.FUJIAN_EEW, "mceew.notify.fj"),
                        Map.entry(NotificationSource.CWA_EEW, "mceew.notify.cwa"),
                        Map.entry(NotificationSource.CENC_EEW, "mceew.notify.cenc.eew"),
                        Map.entry(NotificationSource.CHONGQING_EEW, "mceew.notify.cq"),
                        Map.entry(NotificationSource.JMA_EARTHQUAKE_LIST, "mceew.notify.jma.eqlist"),
                        Map.entry(NotificationSource.CENC_EARTHQUAKE_LIST, "mceew.notify.cenc.eqlist")),
                Map.ofEntries(
                        Map.entry(NotificationSource.JMA_ALERT, NotificationSource.JMA_ALERT.getPermissionNode()),
                        Map.entry(NotificationSource.JMA_FORECAST, NotificationSource.JMA_FORECAST.getPermissionNode()),
                        Map.entry(NotificationSource.SICHUAN_EEW, NotificationSource.SICHUAN_EEW.getPermissionNode()),
                        Map.entry(NotificationSource.FUJIAN_EEW, NotificationSource.FUJIAN_EEW.getPermissionNode()),
                        Map.entry(NotificationSource.CWA_EEW, NotificationSource.CWA_EEW.getPermissionNode()),
                        Map.entry(NotificationSource.CENC_EEW, NotificationSource.CENC_EEW.getPermissionNode()),
                        Map.entry(NotificationSource.CHONGQING_EEW, NotificationSource.CHONGQING_EEW.getPermissionNode()),
                        Map.entry(NotificationSource.JMA_EARTHQUAKE_LIST, NotificationSource.JMA_EARTHQUAKE_LIST.getPermissionNode()),
                        Map.entry(NotificationSource.CENC_EARTHQUAKE_LIST, NotificationSource.CENC_EARTHQUAKE_LIST.getPermissionNode())));
    }

    private static NotificationIntent jma(String flag) {
        NotificationProfile alert = new NotificationProfile(
                "alert:%flag%|%report_time%|%origin_time%|%num%|%lat%|%lon%|%region%|%mag%|%depth%|%shindo%|%type%",
                "alert-title:%flag%",
                "alert-subtitle:%region%:%shindo%",
                "mceew:alert",
                2.5D,
                0.75D);
        NotificationProfile forecast = new NotificationProfile(
                "forecast:%flag%|%report_time%|%origin_time%|%num%|%lat%|%lon%|%region%|%mag%|%depth%|%shindo%|%type%",
                "forecast-title:%flag%",
                "forecast-subtitle:%region%:%shindo%",
                "mceew:forecast",
                2.5D,
                0.75D);
        return NotificationIntentFactory.jma(
                flag, "report", "origin", "46", "37.6", "137.2", "能登半島沖", "7.4",
                "10km", "§d7", "最終報", true, true, true, alert, forecast);
    }

    private static NotificationIntent regional(
            boolean broadcast, boolean title, boolean alert) {
        return NotificationIntentFactory.regional(
                NotificationSource.SICHUAN_EEW,
                "report", "origin", "1", "lat", "lon", "region", "mag", "depth", "intensity",
                broadcast, title, alert, REGIONAL_PROFILE);
    }

    private static void assertRegional(NotificationSource source, String permissionNode) {
        NotificationIntent intent = NotificationIntentFactory.regional(
                source,
                "report", "origin", "1", "lat", "lon", "region", "mag", "depth", "intensity",
                true, true, true, REGIONAL_PROFILE);

        assertEquals(source, intent.getSource());
        assertEquals(permissionNode, intent.getPermissionNode());
        assertRealtime(
                intent,
                "chat:report|origin|1|lat|lon|region|mag|depth|intensity",
                "title:region",
                "subtitle:intensity",
                "mceew:regional");
    }

    private static void assertRealtime(
            NotificationIntent intent,
            String chat,
            String title,
            String subtitle,
            String soundKey
    ) {
        assertTrue(intent.isConsoleDelivery());
        assertEquals(chat, intent.getChat().render());
        assertEquals(title, intent.getTitle().renderTitle());
        assertEquals(subtitle, intent.getTitle().renderSubtitle());
        assertEquals(10, intent.getTitle().getFadeInTicks());
        assertEquals(70, intent.getTitle().getStayTicks());
        assertEquals(20, intent.getTitle().getFadeOutTicks());
        assertEquals(soundKey, intent.getSound().getKey());
        assertEquals(2.5D, intent.getSound().getVolume());
        assertEquals(0.75D, intent.getSound().getPitch());
    }

    private static void assertEarthquakeList(
            NotificationIntent intent,
            NotificationSource source,
            String permissionNode,
            String message
    ) {
        assertEquals(source, intent.getSource());
        assertEquals(permissionNode, intent.getPermissionNode());
        assertTrue(intent.isConsoleDelivery());
        assertEquals(message, intent.getChat().render());
        assertNull(intent.getTitle());
        assertNull(intent.getSound());
    }

    private static void assertDisabledList(NotificationSource source) {
        AtomicInteger formattingCalls = new AtomicInteger();

        Optional<NotificationIntent> intent = NotificationIntentFactory.earthquakeList(
                source,
                true,
                false,
                () -> {
                    formattingCalls.incrementAndGet();
                    return "not used";
                });

        assertTrue(intent.isEmpty());
        assertEquals(0, formattingCalls.get());
    }
}
