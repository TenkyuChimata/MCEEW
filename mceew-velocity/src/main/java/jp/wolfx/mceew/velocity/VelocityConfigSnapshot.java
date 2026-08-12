package jp.wolfx.mceew.velocity;

final class VelocityConfigSnapshot {
    private final int platformConfigVersion;
    private final boolean runtimeEnabled;
    private final boolean jmaEnabled;
    private final boolean sichuanEnabled;
    private final boolean fujianEnabled;
    private final boolean cwaEnabled;
    private final boolean cencEnabled;
    private final boolean chongqingEnabled;

    VelocityConfigSnapshot(
            int platformConfigVersion,
            boolean runtimeEnabled,
            boolean jmaEnabled,
            boolean sichuanEnabled,
            boolean fujianEnabled,
            boolean cwaEnabled,
            boolean cencEnabled,
            boolean chongqingEnabled
    ) {
        this.platformConfigVersion = platformConfigVersion;
        this.runtimeEnabled = runtimeEnabled;
        this.jmaEnabled = jmaEnabled;
        this.sichuanEnabled = sichuanEnabled;
        this.fujianEnabled = fujianEnabled;
        this.cwaEnabled = cwaEnabled;
        this.cencEnabled = cencEnabled;
        this.chongqingEnabled = chongqingEnabled;
    }

    int platformConfigVersion() {
        return platformConfigVersion;
    }

    boolean runtimeEnabled() {
        return runtimeEnabled;
    }

    boolean jmaEnabled() {
        return jmaEnabled;
    }

    boolean sichuanEnabled() {
        return sichuanEnabled;
    }

    boolean fujianEnabled() {
        return fujianEnabled;
    }

    boolean cwaEnabled() {
        return cwaEnabled;
    }

    boolean cencEnabled() {
        return cencEnabled;
    }

    boolean chongqingEnabled() {
        return chongqingEnabled;
    }
}
