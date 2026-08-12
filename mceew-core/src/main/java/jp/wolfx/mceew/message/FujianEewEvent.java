package jp.wolfx.mceew.message;

/**
 * Immutable values parsed from a Fujian real-time EEW message.
 */
public final class FujianEewEvent implements RealtimeEewEvent {
    private final String reportTime;
    private final String originTime;
    private final String reportNumber;
    private final String latitude;
    private final String longitude;
    private final String region;
    private final String magnitude;
    private final boolean finalReport;

    FujianEewEvent(
            String reportTime,
            String originTime,
            String reportNumber,
            String latitude,
            String longitude,
            String region,
            String magnitude,
            boolean finalReport
    ) {
        this.reportTime = reportTime;
        this.originTime = originTime;
        this.reportNumber = reportNumber;
        this.latitude = latitude;
        this.longitude = longitude;
        this.region = region;
        this.magnitude = magnitude;
        this.finalReport = finalReport;
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

    public boolean isFinalReport() {
        return finalReport;
    }
}
