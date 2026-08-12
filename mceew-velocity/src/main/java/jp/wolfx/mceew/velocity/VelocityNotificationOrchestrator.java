package jp.wolfx.mceew.velocity;

import java.util.Objects;
import jp.wolfx.mceew.VelocityMessageProcessor;
import jp.wolfx.mceew.format.EarthquakeTimeFormatter;
import jp.wolfx.mceew.format.LegacyTextFormatter;
import jp.wolfx.mceew.message.FujianEewEvent;
import jp.wolfx.mceew.message.JmaEewEvent;
import jp.wolfx.mceew.message.RealtimeEewEvent;
import jp.wolfx.mceew.message.RegionalEewEvent;
import jp.wolfx.mceew.notification.NotificationIntent;
import jp.wolfx.mceew.notification.NotificationIntentFactory;
import jp.wolfx.mceew.notification.NotificationProfile;
import jp.wolfx.mceew.notification.NotificationSource;

/** Converts fresh/cache-changed processing results into deferred notification events. */
final class VelocityNotificationOrchestrator implements AutoCloseable {
    private static final String JMA_TIME_PATTERN = "yyyy/MM/dd HH:mm:ss";
    private static final String CHINA_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";

    private final VelocityNotificationConfig config;
    private final VelocityNotificationDispatcher dispatcher;

    VelocityNotificationOrchestrator(
            VelocityNotificationConfig config,
            VelocityNotificationDispatcher dispatcher
    ) {
        this.config = Objects.requireNonNull(config, "config");
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
    }

    void accept(VelocityMessageProcessor.ProcessingResult result) {
        if (result.outcome() == VelocityMessageProcessor.Outcome.FRESH_REALTIME) {
            dispatcher.dispatch(realtime(result.realtimeEvent()));
        } else if (result.outcome() == VelocityMessageProcessor.Outcome.CACHE_CHANGED) {
            dispatcher.dispatch(earthquakeList(result.earthquakeList()));
        }
    }

    private VelocityNotificationEvent realtime(RealtimeEewEvent event) {
        if (event instanceof JmaEewEvent) {
            return jma((JmaEewEvent) event);
        }
        if (event instanceof FujianEewEvent) {
            return fujian((FujianEewEvent) event);
        }
        if (event instanceof RegionalEewEvent) {
            return regional((RegionalEewEvent) event);
        }
        throw new IllegalArgumentException("Unsupported realtime event: " + event.getClass().getName());
    }

    private VelocityNotificationEvent jma(JmaEewEvent event) {
        NotificationSource source = "警報".equals(event.getFlag())
                ? NotificationSource.JMA_ALERT
                : NotificationSource.JMA_FORECAST;
        NotificationProfile alert = config.source(NotificationSource.JMA_ALERT).profile();
        NotificationProfile forecast = config.source(NotificationSource.JMA_FORECAST).profile();
        String originTime = formatTime(
                JMA_TIME_PATTERN, "Asia/Tokyo", event.getOriginTime());
        String depth = LegacyTextFormatter.depthKilometers(event.getDepth());
        String shindo = LegacyTextFormatter.shindo(event.getMaximumIntensity());
        String reportType = LegacyTextFormatter.jmaReportType(
                event.isTraining(), event.isAssumption(),
                event.isFinalReport(), event.isCancelled());
        return deferred(source, VelocityNotificationEvent.DeliveryStyle.JMA, channels ->
                NotificationIntentFactory.jma(
                        event.getFlag(), event.getReportTime(), originTime,
                        event.getReportNumber(), event.getLatitude(), event.getLongitude(),
                        event.getRegion(), event.getMagnitude(), depth, shindo, reportType,
                        channels.chat(), channels.title(), channels.sound(), alert, forecast));
    }

    private VelocityNotificationEvent fujian(FujianEewEvent event) {
        NotificationProfile profile = config.source(NotificationSource.FUJIAN_EEW).profile();
        String originTime = formatTime(
                CHINA_TIME_PATTERN, "Asia/Shanghai", event.getOriginTime());
        String reportType = LegacyTextFormatter.finalReportType(event.isFinalReport());
        return deferred(NotificationSource.FUJIAN_EEW,
                VelocityNotificationEvent.DeliveryStyle.REGIONAL,
                channels -> NotificationIntentFactory.fujian(
                        event.getReportTime(), originTime, event.getReportNumber(),
                        event.getLatitude(), event.getLongitude(), event.getRegion(),
                        event.getMagnitude(), reportType,
                        channels.chat(), channels.title(), channels.sound(), profile));
    }

    private VelocityNotificationEvent regional(RegionalEewEvent event) {
        NotificationSource source = notificationSource(event.getSource());
        NotificationProfile profile = config.source(source).profile();
        String originTime = formatTime(
                CHINA_TIME_PATTERN, "Asia/Shanghai", event.getOriginTime());
        String depth = LegacyTextFormatter.depthKilometers(event.getDepth());
        String intensity = event.getSource() == RegionalEewEvent.Source.CWA
                ? LegacyTextFormatter.shindo(event.getMaximumIntensity())
                : LegacyTextFormatter.intensity(event.getMaximumIntensity());
        return deferred(source, VelocityNotificationEvent.DeliveryStyle.REGIONAL,
                channels -> NotificationIntentFactory.regional(
                        source, event.getReportTime(), originTime, event.getReportNumber(),
                        event.getLatitude(), event.getLongitude(), event.getRegion(),
                        event.getMagnitude(), depth, intensity,
                        channels.chat(), channels.title(), channels.sound(), profile));
    }

    private VelocityNotificationEvent earthquakeList(
            VelocityMessageProcessor.EarthquakeListPresentation presentation
    ) {
        NotificationSource source = presentation.source();
        String template = config.source(source).earthquakeListTemplate();
        return deferred(source, VelocityNotificationEvent.DeliveryStyle.EARTHQUAKE_LIST,
                channels -> NotificationIntentFactory.earthquakeList(
                        source, true, channels.chat(), () -> presentation.render(template))
                        .orElse(null));
    }

    private String formatTime(String inputPattern, String timezone, String value) {
        return EarthquakeTimeFormatter.format(
                inputPattern, config.timeFormat(), timezone, value);
    }

    private static NotificationSource notificationSource(RegionalEewEvent.Source source) {
        switch (source) {
            case SICHUAN:
                return NotificationSource.SICHUAN_EEW;
            case CWA:
                return NotificationSource.CWA_EEW;
            case CENC:
                return NotificationSource.CENC_EEW;
            case CHONGQING:
                return NotificationSource.CHONGQING_EEW;
            default:
                throw new IllegalStateException("Unhandled regional source: " + source);
        }
    }

    private static VelocityNotificationEvent deferred(
            NotificationSource source,
            VelocityNotificationEvent.DeliveryStyle style,
            IntentBuilder builder
    ) {
        return new VelocityNotificationEvent() {
            @Override
            public NotificationSource source() {
                return source;
            }

            @Override
            public DeliveryStyle deliveryStyle() {
                return style;
            }

            @Override
            public NotificationIntent build(VelocityChannelPolicy channels) {
                return builder.build(channels);
            }
        };
    }

    @Override
    public void close() {
        dispatcher.close();
    }

    @FunctionalInterface
    private interface IntentBuilder {
        NotificationIntent build(VelocityChannelPolicy channels);
    }
}
