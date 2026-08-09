package jp.wolfx.mceew;

/**
 * Atomically published immutable snapshots shared by WebSocket and command threads.
 */
final class EarthquakeInfoCache {
    static final String NOT_AVAILABLE = "[MCEEW] Earthquake information is not available yet.";

    private volatile JmaSnapshot jma;
    private volatile CencSnapshot cenc;

    JmaSnapshot getJma() {
        return jma;
    }

    void setJma(JmaSnapshot snapshot) {
        jma = snapshot;
    }

    CencSnapshot getCenc() {
        return cenc;
    }

    void setCenc(CencSnapshot snapshot) {
        cenc = snapshot;
    }

    String formatJma(String template) {
        JmaSnapshot snapshot = jma;
        if (snapshot == null) {
            return NOT_AVAILABLE;
        }
        return template.replaceAll("%origin_time%", snapshot.originTime)
                .replaceAll("%region%", snapshot.region)
                .replaceAll("%mag%", snapshot.magnitude)
                .replaceAll("%depth%", snapshot.depth)
                .replaceAll("%lat%", snapshot.latitude)
                .replaceAll("%lon%", snapshot.longitude)
                .replaceAll("%shindo%", snapshot.displayIntensity)
                .replaceAll("%info%", snapshot.info);
    }

    String formatCenc(String template) {
        CencSnapshot snapshot = cenc;
        if (snapshot == null) {
            return NOT_AVAILABLE;
        }
        return template.replaceAll("%flag%", snapshot.type)
                .replaceAll("%origin_time%", snapshot.originTime)
                .replaceAll("%region%", snapshot.region)
                .replaceAll("%mag%", snapshot.magnitude)
                .replaceAll("%depth%", snapshot.depth)
                .replaceAll("%lat%", snapshot.latitude)
                .replaceAll("%lon%", snapshot.longitude)
                .replaceAll("%shindo%", snapshot.displayIntensity);
    }

    static final class JmaSnapshot {
        final String md5;
        final String originTime;
        final String region;
        final String magnitude;
        final String depth;
        final String latitude;
        final String longitude;
        final String displayIntensity;
        final String info;

        JmaSnapshot(String md5, String originTime, String region, String magnitude, String depth,
                    String latitude, String longitude, String displayIntensity, String info) {
            this.md5 = md5;
            this.originTime = originTime;
            this.region = region;
            this.magnitude = magnitude;
            this.depth = depth;
            this.latitude = latitude;
            this.longitude = longitude;
            this.displayIntensity = displayIntensity;
            this.info = info;
        }
    }

    static final class CencSnapshot {
        final String md5;
        final String type;
        final String originTime;
        final String region;
        final String magnitude;
        final String depth;
        final String latitude;
        final String longitude;
        final String displayIntensity;

        CencSnapshot(String md5, String type, String originTime, String region, String magnitude,
                     String depth, String latitude, String longitude, String displayIntensity) {
            this.md5 = md5;
            this.type = type;
            this.originTime = originTime;
            this.region = region;
            this.magnitude = magnitude;
            this.depth = depth;
            this.latitude = latitude;
            this.longitude = longitude;
            this.displayIntensity = displayIntensity;
        }
    }
}
