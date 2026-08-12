package jp.wolfx.mceew.notification;

/**
 * Immutable runtime templates and sound settings for one real-time notification path.
 */
public final class NotificationProfile {
    private final String broadcastTemplate;
    private final String titleTemplate;
    private final String subtitleTemplate;
    private final String soundKey;
    private final double soundVolume;
    private final double soundPitch;

    public NotificationProfile(
            String broadcastTemplate,
            String titleTemplate,
            String subtitleTemplate,
            String soundKey,
            double soundVolume,
            double soundPitch
    ) {
        this.broadcastTemplate = broadcastTemplate;
        this.titleTemplate = titleTemplate;
        this.subtitleTemplate = subtitleTemplate;
        this.soundKey = soundKey;
        this.soundVolume = soundVolume;
        this.soundPitch = soundPitch;
    }

    String getBroadcastTemplate() {
        return broadcastTemplate;
    }

    String getTitleTemplate() {
        return titleTemplate;
    }

    String getSubtitleTemplate() {
        return subtitleTemplate;
    }

    String getSoundKey() {
        return soundKey;
    }

    double getSoundVolume() {
        return soundVolume;
    }

    double getSoundPitch() {
        return soundPitch;
    }
}
