package jp.wolfx.mceew.velocity;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.LinkedHashMap;
import java.util.Map;
import jp.wolfx.mceew.notification.NotificationSource;
import org.junit.jupiter.api.Test;

class VelocityNotificationSourcesTest {
    @Test
    void stableConfigKeysMapToCoreSourcesAndPermissionNodes() {
        Map<String, String> expected = new LinkedHashMap<>();
        expected.put("jma_alert", "mceew.notify.jma.alert");
        expected.put("jma_forecast", "mceew.notify.jma.forecast");
        expected.put("sichuan", "mceew.notify.sc");
        expected.put("fujian", "mceew.notify.fj");
        expected.put("cwa", "mceew.notify.cwa");
        expected.put("cenc_eew", "mceew.notify.cenc.eew");
        expected.put("chongqing", "mceew.notify.cq");
        expected.put("jma_eqlist", "mceew.notify.jma.eqlist");
        expected.put("cenc_eqlist", "mceew.notify.cenc.eqlist");

        assertEquals(expected.keySet(), VelocityNotificationSources.entries().keySet());
        for (Map.Entry<String, String> entry : expected.entrySet()) {
            NotificationSource source = VelocityNotificationSources.fromKey(entry.getKey());
            assertEquals(entry.getValue(), source.getPermissionNode());
            assertEquals(entry.getKey(), VelocityNotificationSources.key(source));
        }
    }
}
