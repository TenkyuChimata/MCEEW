package jp.wolfx.mceew;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MceewMessageRoutingTest {
    @Test
    void everyRealtimeTypeRoutesOnlyToItsCurrentPermissionAndNotificationPath() {
        Map<String, String> permissionByFixture = Map.of(
                "jma_eew", "mceew.notify.jma.alert",
                "sc_eew", "mceew.notify.sc",
                "fj_eew", "mceew.notify.fj",
                "cwa_eew", "mceew.notify.cwa",
                "cenc_eew", "mceew.notify.cenc.eew",
                "cq_eew", "mceew.notify.cq"
        );

        for (Map.Entry<String, String> entry : permissionByFixture.entrySet()) {
            MceewCharacterizationSupport.Harness harness =
                    MceewCharacterizationSupport.harness();

            harness.routeFresh(entry.getKey());

            assertEquals(1, harness.console.size(), entry.getKey());
            assertEquals(1, harness.player.chat.size(), entry.getKey());
            assertEquals(1, harness.player.titles.size(), entry.getKey());
            assertEquals(1, harness.player.sounds.size(), entry.getKey());
            assertEquals(
                    List.of("mceew.notify.all", entry.getValue()),
                    List.copyOf(harness.player.queriedPermissions),
                    entry.getKey());
        }
    }

    @Test
    void earthquakeListTypesRouteToTheirOwnCachesAndChangedNotificationPaths() {
        MceewCharacterizationSupport.Harness harness = MceewCharacterizationSupport.harness();
        JsonObject jma = MceewCharacterizationSupport.fixture("jma_eqlist");
        JsonObject cenc = MceewCharacterizationSupport.fixture("cenc_eqlist");

        harness.route(jma.toString());
        harness.route(cenc.toString());

        assertNotNull(harness.cache().getJma());
        assertNotNull(harness.cache().getCenc());
        assertTrue(harness.console.isEmpty(), "first list snapshots are bootstrap-only");

        jma.addProperty("md5", "cccccccccccccccccccccccccccccccc");
        cenc.addProperty("md5", "dddddddddddddddddddddddddddddddd");
        harness.route(jma.toString());
        assertEquals(List.of("mceew.notify.all", "mceew.notify.jma.eqlist"),
                List.copyOf(harness.player.queriedPermissions));
        harness.player.queriedPermissions.clear();
        harness.route(cenc.toString());

        assertEquals(2, harness.console.size());
        assertEquals(2, harness.player.chat.size());
        assertTrue(harness.player.titles.isEmpty());
        assertTrue(harness.player.sounds.isEmpty());
        assertEquals(List.of("mceew.notify.all", "mceew.notify.cenc.eqlist"),
                List.copyOf(harness.player.queriedPermissions));
    }

    @Test
    void heartbeatAndUnknownTypeAreIgnoredWithoutTouchingStateOrOutputs() {
        MceewCharacterizationSupport.Harness harness = MceewCharacterizationSupport.harness();

        harness.route(MceewCharacterizationSupport.fixture("heartbeat").toString());
        harness.route(MceewCharacterizationSupport.fixture("unknown").toString());

        assertTrue(harness.console.isEmpty());
        assertTrue(harness.player.chat.isEmpty());
        assertTrue(harness.player.titles.isEmpty());
        assertTrue(harness.player.sounds.isEmpty());
        assertNull(harness.cache().getJma());
        assertNull(harness.cache().getCenc());
    }

    @Test
    void eachDisabledRealtimeSourceIsIgnoredWithoutDisablingOtherSources() {
        Map<String, String> flagByFixture = Map.of(
                "jma_eew", "jpEewBoolean",
                "sc_eew", "scEewBoolean",
                "fj_eew", "fjEewBoolean",
                "cwa_eew", "cwaEewBoolean",
                "cenc_eew", "cencEewBoolean",
                "cq_eew", "cqEewBoolean"
        );

        for (Map.Entry<String, String> entry : flagByFixture.entrySet()) {
            MceewCharacterizationSupport.Harness harness =
                    MceewCharacterizationSupport.harness();
            MceewCharacterizationSupport.field(harness.plugin, entry.getValue(), false);

            harness.routeFresh(entry.getKey());

            assertTrue(harness.console.isEmpty(), entry.getKey());
            assertTrue(harness.player.chat.isEmpty(), entry.getKey());
            assertTrue(harness.player.titles.isEmpty(), entry.getKey());
            assertTrue(harness.player.sounds.isEmpty(), entry.getKey());

            if (!entry.getKey().equals("jma_eew")) {
                harness.routeFresh("jma_eew");
                assertFalse(harness.player.chat.isEmpty(), entry.getKey());
            }
        }
    }

    @Test
    void disabledEarthquakeListActionsStillUpdateChangedCachesWithoutNotification() {
        MceewCharacterizationSupport.Harness harness = MceewCharacterizationSupport.harness();
        MceewCharacterizationSupport.field(harness.plugin, "jmaEqlistBoolean", false);
        MceewCharacterizationSupport.field(harness.plugin, "cencEqlistBoolean", false);
        JsonObject jma = MceewCharacterizationSupport.fixture("jma_eqlist");
        JsonObject cenc = MceewCharacterizationSupport.fixture("cenc_eqlist");

        harness.route(jma.toString());
        harness.route(cenc.toString());
        jma.addProperty("md5", "cccccccccccccccccccccccccccccccc");
        cenc.addProperty("md5", "dddddddddddddddddddddddddddddddd");
        harness.route(jma.toString());
        harness.route(cenc.toString());

        assertEquals("cccccccccccccccccccccccccccccccc", harness.cache().getJma().md5);
        assertEquals("dddddddddddddddddddddddddddddddd", harness.cache().getCenc().md5);
        assertTrue(harness.console.isEmpty());
        assertTrue(harness.player.chat.isEmpty());
    }

    @Test
    void malformedJsonPropagatesFromTheApplicationConsumerBoundary() {
        MceewCharacterizationSupport.Harness harness = MceewCharacterizationSupport.harness();

        assertThrows(RuntimeException.class, () -> harness.route("{not-json"));

        // WebSocketConnectionManagerTest.rejectedApplicationMessageDoesNotDisconnectOrStopConsumption
        // characterizes that this consumer exception is isolated by the connection manager.
        harness.route(MceewCharacterizationSupport.fixture("heartbeat").toString());
        assertTrue(harness.console.isEmpty());
    }
}
