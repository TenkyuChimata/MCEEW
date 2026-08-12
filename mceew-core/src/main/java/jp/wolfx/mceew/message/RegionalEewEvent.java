package jp.wolfx.mceew.message;

/**
 * Immutable values shared by the Sichuan, CWA, CENC, and Chongqing feeds.
 */
public final class RegionalEewEvent implements RealtimeEewEvent {
    public enum Source {
        SICHUAN,
        CWA,
        CENC,
        CHONGQING
    }

    private final Source source;
    private final String reportTime;
    private final String originTime;
    private final String reportNumber;
    private final String latitude;
    private final String longitude;
    private final String region;
    private final String magnitude;
    private final String depth;
    private final String maximumIntensity;

    RegionalEewEvent(
            Source source,
            String reportTime,
            String originTime,
            String reportNumber,
            String latitude,
            String longitude,
            String region,
            String magnitude,
            String depth,
            String maximumIntensity
    ) {
        this.source = source;
        this.reportTime = reportTime;
        this.originTime = originTime;
        this.reportNumber = reportNumber;
        this.latitude = latitude;
        this.longitude = longitude;
        this.region = region;
        this.magnitude = magnitude;
        this.depth = depth;
        this.maximumIntensity = maximumIntensity;
    }

    public Source getSource() {
        return source;
    }

    @Override
    public String getReportTime() {
        return reportTime;
    }

    @Override
    public String getOriginTime() {
        return originTime;
    }

    @Override
    public String getReportNumber() {
        return reportNumber;
    }

    @Override
    public String getLatitude() {
        return latitude;
    }

    @Override
    public String getLongitude() {
        return longitude;
    }

    @Override
    public String getRegion() {
        return region;
    }

    @Override
    public String getMagnitude() {
        return magnitude;
    }

    public String getDepth() {
        return depth;
    }

    public String getMaximumIntensity() {
        return maximumIntensity;
    }
}
