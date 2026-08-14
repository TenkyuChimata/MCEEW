package jp.wolfx.mceew.bungeecord;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import jp.wolfx.mceew.notification.NotificationSource;

final class BungeePermissions {
    static final String ADMIN = "mceew.admin";
    static final String SUPPRESS_ALL = "mceew.suppress.all";

    private static final Map<NotificationSource, String> SOURCE_SUPPRESSION;

    static {
        Map<NotificationSource, String> permissions = new EnumMap<>(NotificationSource.class);
        permissions.put(NotificationSource.JMA_ALERT, "mceew.suppress.jma.alert");
        permissions.put(NotificationSource.JMA_FORECAST, "mceew.suppress.jma.forecast");
        permissions.put(NotificationSource.SICHUAN_EEW, "mceew.suppress.sc");
        permissions.put(NotificationSource.FUJIAN_EEW, "mceew.suppress.fj");
        permissions.put(NotificationSource.CWA_EEW, "mceew.suppress.cwa");
        permissions.put(NotificationSource.CENC_EEW, "mceew.suppress.cenc.eew");
        permissions.put(NotificationSource.CHONGQING_EEW, "mceew.suppress.cq");
        permissions.put(NotificationSource.JMA_EARTHQUAKE_LIST, "mceew.suppress.jma.eqlist");
        permissions.put(NotificationSource.CENC_EARTHQUAKE_LIST, "mceew.suppress.cenc.eqlist");
        SOURCE_SUPPRESSION = Collections.unmodifiableMap(permissions);
    }

    private BungeePermissions() {
    }

    static String suppressionFor(NotificationSource source) {
        String permission = SOURCE_SUPPRESSION.get(Objects.requireNonNull(source, "source"));
        if (permission == null) {
            throw new IllegalArgumentException("Unsupported notification source: " + source);
        }
        return permission;
    }

    static Map<NotificationSource, String> sourceSuppressions() {
        return SOURCE_SUPPRESSION;
    }
}
