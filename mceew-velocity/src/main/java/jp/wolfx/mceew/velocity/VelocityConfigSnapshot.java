package jp.wolfx.mceew.velocity;

final class VelocityConfigSnapshot {
    private final int platformConfigVersion;

    VelocityConfigSnapshot(int platformConfigVersion) {
        this.platformConfigVersion = platformConfigVersion;
    }

    int platformConfigVersion() {
        return platformConfigVersion;
    }
}
