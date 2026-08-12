package jp.wolfx.mceew.notification;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Applies the existing Action gates and constructs platform-neutral notification intents.
 */
public final class NotificationIntentFactory {
    private static final int TITLE_FADE_IN_TICKS = 10;
    private static final int TITLE_STAY_TICKS = 70;
    private static final int TITLE_FADE_OUT_TICKS = 20;

    private NotificationIntentFactory() {
    }

    public static NotificationIntent jma(
            String flag,
            String reportTime,
            String originTime,
            String reportNumber,
            String latitude,
            String longitude,
            String region,
            String magnitude,
            String depth,
            String shindo,
            String reportType,
            boolean broadcastEnabled,
            boolean titleEnabled,
            boolean alertEnabled,
            NotificationProfile alertProfile,
            NotificationProfile forecastProfile
    ) {
        boolean alert = Objects.equals(flag, "警報");
        NotificationProfile profile = alert ? alertProfile : forecastProfile;
        NotificationSource source = alert
                ? NotificationSource.JMA_ALERT
                : NotificationSource.JMA_FORECAST;
        return realtime(
                source,
                broadcastEnabled,
                titleEnabled,
                alertEnabled,
                profile,
                "%flag%", flag,
                "%report_time%", reportTime,
                "%origin_time%", originTime,
                "%num%", reportNumber,
                "%lat%", latitude,
                "%lon%", longitude,
                "%region%", region,
                "%mag%", magnitude,
                "%depth%", depth,
                "%shindo%", shindo,
                "%type%", reportType);
    }

    public static NotificationIntent regional(
            NotificationSource source,
            String reportTime,
            String originTime,
            String reportNumber,
            String latitude,
            String longitude,
            String region,
            String magnitude,
            String depth,
            String intensity,
            boolean broadcastEnabled,
            boolean titleEnabled,
            boolean alertEnabled,
            NotificationProfile profile
    ) {
        return realtime(
                source,
                broadcastEnabled,
                titleEnabled,
                alertEnabled,
                profile,
                "%report_time%", reportTime,
                "%origin_time%", originTime,
                "%num%", reportNumber,
                "%lat%", latitude,
                "%lon%", longitude,
                "%region%", region,
                "%mag%", magnitude,
                "%depth%", depth,
                "%shindo%", intensity);
    }

    public static NotificationIntent fujian(
            String reportTime,
            String originTime,
            String reportNumber,
            String latitude,
            String longitude,
            String region,
            String magnitude,
            String reportType,
            boolean broadcastEnabled,
            boolean titleEnabled,
            boolean alertEnabled,
            NotificationProfile profile
    ) {
        return realtime(
                NotificationSource.FUJIAN_EEW,
                broadcastEnabled,
                titleEnabled,
                alertEnabled,
                profile,
                "%report_time%", reportTime,
                "%origin_time%", originTime,
                "%num%", reportNumber,
                "%lat%", latitude,
                "%lon%", longitude,
                "%region%", region,
                "%mag%", magnitude,
                "%type%", reportType);
    }

    public static Optional<NotificationIntent> earthquakeList(
            NotificationSource source,
            boolean changed,
            boolean enabled,
            Supplier<String> formattedMessage
    ) {
        if (!changed || !enabled) {
            return Optional.empty();
        }
        NotificationIntent.ChatNotice chat = new NotificationIntent.ChatNotice(
                formattedMessage.get());
        return Optional.of(new NotificationIntent(source, true, chat, null, null));
    }

    private static NotificationIntent realtime(
            NotificationSource source,
            boolean broadcastEnabled,
            boolean titleEnabled,
            boolean alertEnabled,
            NotificationProfile profile,
            String... replacements
    ) {
        NotificationIntent.ChatNotice chat = broadcastEnabled
                ? new NotificationIntent.ChatNotice(
                        profile.getBroadcastTemplate(), replacements)
                : null;
        NotificationIntent.TitleNotice title = titleEnabled
                ? new NotificationIntent.TitleNotice(
                        new NotificationIntent.ChatNotice(
                                profile.getTitleTemplate(), replacements),
                        new NotificationIntent.ChatNotice(
                                profile.getSubtitleTemplate(), replacements),
                        TITLE_FADE_IN_TICKS,
                        TITLE_STAY_TICKS,
                        TITLE_FADE_OUT_TICKS)
                : null;
        NotificationIntent.SoundNotice sound = alertEnabled
                ? new NotificationIntent.SoundNotice(
                        profile.getSoundKey(),
                        profile.getSoundVolume(),
                        profile.getSoundPitch())
                : null;
        return new NotificationIntent(source, broadcastEnabled, chat, title, sound);
    }
}
