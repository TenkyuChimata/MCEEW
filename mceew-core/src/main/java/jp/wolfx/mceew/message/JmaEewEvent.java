package jp.wolfx.mceew.message;

/**
 * Immutable values parsed from a JMA real-time EEW message.
 */
public final class JmaEewEvent implements RealtimeEewEvent {
    private final String flag;
    private final String reportTime;
    private final String originTime;
    private final String reportNumber;
    private final String latitude;
    private final String longitude;
    private final String region;
    private final String magnitude;
    private final String depth;
    private final String maximumIntensity;
    private final boolean training;
    private final boolean assumption;
    private final boolean finalReport;
    private final boolean cancelled;

    JmaEewEvent(
            String flag,
            String reportTime,
            String originTime,
            String reportNumber,
            String latitude,
            String longitude,
            String region,
            String magnitude,
            String depth,
            String maximumIntensity,
            boolean training,
            boolean assumption,
            boolean finalReport,
            boolean cancelled
    ) {
        this.flag = flag;
        this.reportTime = reportTime;
        this.originTime = originTime;
        this.reportNumber = reportNumber;
        this.latitude = latitude;
        this.longitude = longitude;
        this.region = region;
        this.magnitude = magnitude;
        this.depth = depth;
        this.maximumIntensity = maximumIntensity;
        this.training = training;
        this.assumption = assumption;
        this.finalReport = finalReport;
        this.cancelled = cancelled;
    }

    public String getFlag() {
        return flag;
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

    public boolean isTraining() {
        return training;
    }

    public boolean isAssumption() {
        return assumption;
    }

    public boolean isFinalReport() {
        return finalReport;
    }

    public boolean isCancelled() {
        return cancelled;
    }
}
