package jp.wolfx.mceew.notification;

/**
 * Identifies the existing notification path and its source-specific permission node.
 */
public enum NotificationSource {
    JMA_ALERT("mceew.notify.jma.alert"),
    JMA_FORECAST("mceew.notify.jma.forecast"),
    SICHUAN_EEW("mceew.notify.sc"),
    FUJIAN_EEW("mceew.notify.fj"),
    CWA_EEW("mceew.notify.cwa"),
    CENC_EEW("mceew.notify.cenc.eew"),
    CHONGQING_EEW("mceew.notify.cq"),
    JMA_EARTHQUAKE_LIST("mceew.notify.jma.eqlist"),
    CENC_EARTHQUAKE_LIST("mceew.notify.cenc.eqlist");

    private final String permissionNode;

    NotificationSource(String permissionNode) {
        this.permissionNode = permissionNode;
    }

    public String getPermissionNode() {
        return permissionNode;
    }
}
