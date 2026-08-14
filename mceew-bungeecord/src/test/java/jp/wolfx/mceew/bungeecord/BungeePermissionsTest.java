package jp.wolfx.mceew.bungeecord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumMap;
import java.util.Map;
import jp.wolfx.mceew.notification.NotificationSource;
import org.junit.jupiter.api.Test;

class BungeePermissionsTest {
    @Test
    void suppressionContractMapsEverySourceExactly() {
        Map<NotificationSource, String> expected = new EnumMap<>(NotificationSource.class);
        expected.put(NotificationSource.JMA_ALERT, "mceew.suppress.jma.alert");
        expected.put(NotificationSource.JMA_FORECAST, "mceew.suppress.jma.forecast");
        expected.put(NotificationSource.SICHUAN_EEW, "mceew.suppress.sc");
        expected.put(NotificationSource.FUJIAN_EEW, "mceew.suppress.fj");
        expected.put(NotificationSource.CWA_EEW, "mceew.suppress.cwa");
        expected.put(NotificationSource.CENC_EEW, "mceew.suppress.cenc.eew");
        expected.put(NotificationSource.CHONGQING_EEW, "mceew.suppress.cq");
        expected.put(NotificationSource.JMA_EARTHQUAKE_LIST, "mceew.suppress.jma.eqlist");
        expected.put(NotificationSource.CENC_EARTHQUAKE_LIST, "mceew.suppress.cenc.eqlist");

        assertEquals(expected, BungeePermissions.sourceSuppressions());
        expected.forEach((source, permission) ->
                assertEquals(permission, BungeePermissions.suppressionFor(source)));
    }

    @Test
    void globalAndAdminNodesAreIndependent() {
        assertEquals("mceew.suppress.all", BungeePermissions.SUPPRESS_ALL);
        assertEquals("mceew.admin", BungeePermissions.ADMIN);
    }

    @Test
    void noBungeePermissionConstantUsesReceiveOrWildcardSemantics() {
        assertFalse(BungeePermissions.SUPPRESS_ALL.contains("notify"));
        assertFalse(BungeePermissions.SUPPRESS_ALL.contains("*"));
        for (String permission : BungeePermissions.sourceSuppressions().values()) {
            assertTrue(permission.startsWith("mceew.suppress."));
            assertFalse(permission.contains("notify"));
            assertFalse(permission.contains("*"));
        }
    }

    @Test
    void suppressionMapCannotBeMutated() {
        assertThrows(UnsupportedOperationException.class, () ->
                BungeePermissions.sourceSuppressions().put(
                        NotificationSource.JMA_ALERT, "changed"));
    }
}
