package jp.wolfx.mceew;

import com.google.gson.JsonObject;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Atomically published immutable snapshots shared by WebSocket and command threads.
 */
final class EarthquakeInfoCache {
    static final String NOT_AVAILABLE = "[MCEEW] Earthquake information is not available yet.";
    private static final Pattern MD5_PATTERN = Pattern.compile("[0-9a-fA-F]{32}");

    private volatile JmaSnapshot jma;
    private volatile CencSnapshot cenc;

    enum UpdateResult {
        FIRST_VALUE,
        UNCHANGED,
        CHANGED;

        boolean shouldNotify(boolean enabled) {
            return this == CHANGED && enabled;
        }
    }

    JmaSnapshot getJma() {
        return jma;
    }

    synchronized UpdateResult updateJma(JmaSnapshot snapshot) {
        requireUsableMd5(snapshot.md5);
        UpdateResult result = classify(jma, snapshot.md5);
        jma = snapshot;
        return result;
    }

    CencSnapshot getCenc() {
        return cenc;
    }

    synchronized UpdateResult updateCenc(CencSnapshot snapshot) {
        requireUsableMd5(snapshot.md5);
        UpdateResult result = classify(cenc, snapshot.md5);
        cenc = snapshot;
        return result;
    }

    String formatJma(String template) {
        JmaSnapshot snapshot = jma;
        if (snapshot == null) {
            return NOT_AVAILABLE;
        }
        return snapshot.format(template);
    }

    String formatCenc(String template) {
        CencSnapshot snapshot = cenc;
        if (snapshot == null) {
            return NOT_AVAILABLE;
        }
        return snapshot.format(template);
    }

    private UpdateResult classify(Snapshot previous, String incomingMd5) {
        if (previous == null) {
            return UpdateResult.FIRST_VALUE;
        }
        return Objects.equals(previous.md5(), incomingMd5)
                ? UpdateResult.UNCHANGED
                : UpdateResult.CHANGED;
    }

    private void requireUsableMd5(String md5) {
        if (md5 == null || !MD5_PATTERN.matcher(md5).matches()) {
            throw new IllegalArgumentException(
                    "Earthquake information md5 must contain 32 hexadecimal characters");
        }
    }

    private interface Snapshot {
        String md5();
    }

    static final class JmaSnapshot implements Snapshot {
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

        @Override
        public String md5() {
            return md5;
        }

        String format(String template) {
            return template.replaceAll("%origin_time%", originTime)
                    .replaceAll("%region%", region)
                    .replaceAll("%mag%", magnitude)
                    .replaceAll("%depth%", depth)
                    .replaceAll("%lat%", latitude)
                    .replaceAll("%lon%", longitude)
                    .replaceAll("%shindo%", displayIntensity)
                    .replaceAll("%info%", info);
        }
    }

    static final class CencSnapshot implements Snapshot {
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

        @Override
        public String md5() {
            return md5;
        }

        String format(String template) {
            return template.replaceAll("%flag%", type)
                    .replaceAll("%origin_time%", originTime)
                    .replaceAll("%region%", region)
                    .replaceAll("%mag%", magnitude)
                    .replaceAll("%depth%", depth)
                    .replaceAll("%lat%", latitude)
                    .replaceAll("%lon%", longitude)
                    .replaceAll("%shindo%", displayIntensity);
        }

        static CencSnapshot fromEqlist(
                JsonObject data, String originTime, String displayIntensity) {
            JsonObject latest = data.get("No1").getAsJsonObject();
            String type = "reviewed".equals(latest.get("type").getAsString())
                    ? "正式测定"
                    : "自动测定";
            return new CencSnapshot(
                    data.get("md5").getAsString(),
                    type,
                    originTime,
                    latest.get("location").getAsString(),
                    latest.get("magnitude").getAsString(),
                    latest.get("depth").getAsString() + "km",
                    latest.get("latitude").getAsString(),
                    latest.get("longitude").getAsString(),
                    displayIntensity
            );
        }
    }
}
