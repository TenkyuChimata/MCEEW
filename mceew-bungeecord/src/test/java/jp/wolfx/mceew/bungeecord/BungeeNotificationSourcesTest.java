package jp.wolfx.mceew.bungeecord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.LinkedHashMap;
import java.util.Map;
import jp.wolfx.mceew.notification.NotificationSource;
import org.junit.jupiter.api.Test;

class BungeeNotificationSourcesTest {
    @Test
    void canonicalConfigMappingMatchesVelocityVocabulary() {
        Map<String, NotificationSource> expected = new LinkedHashMap<>();
        expected.put("jma_alert", NotificationSource.JMA_ALERT);
        expected.put("jma_forecast", NotificationSource.JMA_FORECAST);
        expected.put("sichuan", NotificationSource.SICHUAN_EEW);
        expected.put("fujian", NotificationSource.FUJIAN_EEW);
        expected.put("cwa", NotificationSource.CWA_EEW);
        expected.put("cenc_eew", NotificationSource.CENC_EEW);
        expected.put("chongqing", NotificationSource.CHONGQING_EEW);
        expected.put("jma_eqlist", NotificationSource.JMA_EARTHQUAKE_LIST);
        expected.put("cenc_eqlist", NotificationSource.CENC_EARTHQUAKE_LIST);

        assertEquals(expected, BungeeNotificationSources.entries());
        expected.forEach((key, source) -> {
            assertEquals(source, BungeeNotificationSources.fromKey(key));
            assertEquals(key, BungeeNotificationSources.key(source));
        });
    }

    @Test
    void retiredAndAbbreviatedYamlNamesAreNotAliases() {
        assertNull(BungeeNotificationSources.fromKey("jma-alert"));
        assertNull(BungeeNotificationSources.fromKey("sc"));
        assertNull(BungeeNotificationSources.fromKey("fj"));
        assertNull(BungeeNotificationSources.fromKey("cq"));
    }

    @Test
    void mappingsAreImmutableAndNonNull() {
        assertThrows(UnsupportedOperationException.class, () ->
                BungeeNotificationSources.entries().put(
                        "extra", NotificationSource.JMA_ALERT));
        assertThrows(NullPointerException.class, () -> BungeeNotificationSources.key(null));
    }
}
