package jp.wolfx.mceew.velocity;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import jp.wolfx.mceew.notification.NotificationSource;

/** Stable public config keys for the core notification identities. */
final class VelocityNotificationSources {
    private static final Map<String, NotificationSource> BY_KEY;
    private static final Map<NotificationSource, String> BY_SOURCE;

    static {
        Map<String, NotificationSource> byKey = new LinkedHashMap<>();
        byKey.put("jma-alert", NotificationSource.JMA_ALERT);
        byKey.put("jma-forecast", NotificationSource.JMA_FORECAST);
        byKey.put("sichuan", NotificationSource.SICHUAN_EEW);
        byKey.put("fujian", NotificationSource.FUJIAN_EEW);
        byKey.put("cwa", NotificationSource.CWA_EEW);
        byKey.put("cenc-eew", NotificationSource.CENC_EEW);
        byKey.put("chongqing", NotificationSource.CHONGQING_EEW);
        byKey.put("jma-eqlist", NotificationSource.JMA_EARTHQUAKE_LIST);
        byKey.put("cenc-eqlist", NotificationSource.CENC_EARTHQUAKE_LIST);
        BY_KEY = Collections.unmodifiableMap(byKey);

        Map<NotificationSource, String> bySource = new EnumMap<>(NotificationSource.class);
        byKey.forEach((key, source) -> bySource.put(source, key));
        BY_SOURCE = Collections.unmodifiableMap(bySource);
    }

    private VelocityNotificationSources() {
    }

    static Map<String, NotificationSource> entries() {
        return BY_KEY;
    }

    static NotificationSource fromKey(String key) {
        if (key == null) {
            return null;
        }
        return BY_KEY.get(key.toLowerCase(Locale.ROOT));
    }

    static String key(NotificationSource source) {
        String key = BY_SOURCE.get(source);
        if (key == null) {
            throw new IllegalArgumentException("Unsupported notification source: " + source);
        }
        return key;
    }

    static boolean isJmaRealtime(NotificationSource source) {
        return source == NotificationSource.JMA_ALERT
                || source == NotificationSource.JMA_FORECAST;
    }

    static boolean isEarthquakeList(NotificationSource source) {
        return source == NotificationSource.JMA_EARTHQUAKE_LIST
                || source == NotificationSource.CENC_EARTHQUAKE_LIST;
    }
}
