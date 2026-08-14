package jp.wolfx.mceew.bungeecord;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import jp.wolfx.mceew.notification.NotificationSource;

final class BungeeNotificationSources {
    private static final Map<String, NotificationSource> BY_KEY;
    private static final Map<NotificationSource, String> BY_SOURCE;

    static {
        Map<String, NotificationSource> byKey = new LinkedHashMap<>();
        byKey.put("jma_alert", NotificationSource.JMA_ALERT);
        byKey.put("jma_forecast", NotificationSource.JMA_FORECAST);
        byKey.put("sichuan", NotificationSource.SICHUAN_EEW);
        byKey.put("fujian", NotificationSource.FUJIAN_EEW);
        byKey.put("cwa", NotificationSource.CWA_EEW);
        byKey.put("cenc_eew", NotificationSource.CENC_EEW);
        byKey.put("chongqing", NotificationSource.CHONGQING_EEW);
        byKey.put("jma_eqlist", NotificationSource.JMA_EARTHQUAKE_LIST);
        byKey.put("cenc_eqlist", NotificationSource.CENC_EARTHQUAKE_LIST);
        BY_KEY = Collections.unmodifiableMap(byKey);

        Map<NotificationSource, String> bySource = new EnumMap<>(NotificationSource.class);
        for (Map.Entry<String, NotificationSource> entry : byKey.entrySet()) {
            bySource.put(entry.getValue(), entry.getKey());
        }
        BY_SOURCE = Collections.unmodifiableMap(bySource);
    }

    private BungeeNotificationSources() {
    }

    static Map<String, NotificationSource> entries() {
        return BY_KEY;
    }

    static NotificationSource fromKey(String key) {
        return BY_KEY.get(key);
    }

    static String key(NotificationSource source) {
        String key = BY_SOURCE.get(Objects.requireNonNull(source, "source"));
        if (key == null) {
            throw new IllegalArgumentException("Unsupported notification source: " + source);
        }
        return key;
    }

    static boolean isEarthquakeList(NotificationSource source) {
        return source == NotificationSource.JMA_EARTHQUAKE_LIST
                || source == NotificationSource.CENC_EARTHQUAKE_LIST;
    }
}
