package jp.wolfx.mceew.message;

/**
 * Common wire values shared by the real-time Wolfx EEW payloads.
 */
public interface RealtimeEewEvent {
    String getReportTime();

    String getOriginTime();

    String getReportNumber();

    String getLatitude();

    String getLongitude();

    String getRegion();

    String getMagnitude();
}
