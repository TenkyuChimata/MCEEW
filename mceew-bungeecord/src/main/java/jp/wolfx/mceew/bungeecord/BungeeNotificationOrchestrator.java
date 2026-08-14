package jp.wolfx.mceew.bungeecord;

import java.util.Objects;
import jp.wolfx.mceew.BungeeMessageProcessor;
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

/** Converts eligible processing results into deferred, Bungee-delivered core intents. */
final class BungeeNotificationOrchestrator implements BungeeNotificationSink {
    private static final String JMA_TIME_PATTERN = "yyyy/MM/dd HH:mm:ss";
    private static final String CHINA_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";
    private static final String TEST_WARNING =
            "§eWarning: This is an Earthquake Early Warning test.";

    private final BungeeConfigSnapshot config;
    private final BungeeNotificationDispatcher dispatcher;

    BungeeNotificationOrchestrator(
            BungeeConfigSnapshot config,
            BungeeNotificationDispatcher dispatcher
    ) {
        this.config = Objects.requireNonNull(config, "config");
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
    }

    @Override
    public void accept(BungeeMessageProcessor.ProcessingResult result) {
        if (result.outcome() == BungeeMessageProcessor.Outcome.FRESH_REALTIME) {
            dispatcher.dispatch(realtime(result.realtimeEvent()));
        } else if (result.outcome() == BungeeMessageProcessor.Outcome.CACHE_CHANGED) {
            dispatcher.dispatch(earthquakeList(result.earthquakeList()));
        }
    }

    @Override
    public boolean dispatchTest(String sourceKey) {
        BungeeNotificationEvent event;
        switch (sourceKey) {
            case "forecast":
                event = testJma(
                        NotificationSource.JMA_FORECAST,
                        "予報", "2024/02/29 18:36:36", "2024/02/29 18:35:38",
                        "6", "35.4", "140.6", "千葉県東方沖", "4.7", "10km",
                        LegacyTextFormatter.shindo("3"), "");
                break;
            case "alert":
                event = testJma(
                        NotificationSource.JMA_ALERT,
                        "警報", "2024/01/01 16:14:18", "2024/01/01 16:10:08",
                        "46", "37.6", "137.2", "能登半島沖", "7.4", "10km",
                        LegacyTextFormatter.shindo("7"), "最終報");
                break;
            case "sc":
                event = testRegional(
                        NotificationSource.SICHUAN_EEW,
                        "2024-02-28 21:23:37", "2024-02-28 21:23:30", "1",
                        "29.3", "102.82", "四川雅安市汉源县", "3.3", "10km",
                        LegacyTextFormatter.intensity("5"));
                break;
            case "fj":
                event = testFujian();
                break;
            case "cwa":
                event = testRegional(
                        NotificationSource.CWA_EEW,
                        "2024-04-03 07:58:27", "2024-04-03 07:58:10", "2",
                        "23.89", "121.56", "花蓮縣壽豐鄉", "6.8", "20km",
                        LegacyTextFormatter.shindo("6弱"));
                break;
            case "cenc":
                event = testRegional(
                        NotificationSource.CENC_EEW,
                        "2025-09-12 05:50:58", "2025-09-12 05:50:58", "1",
                        "33.002", "102.89", "四川阿坝州红原县", "4.4", "5km",
                        LegacyTextFormatter.intensity("6.1"));
                break;
            case "cq":
                event = testRegional(
                        NotificationSource.CHONGQING_EEW,
                        "2026-08-07 13:08:30", "2026-08-07 13:08:30", "1",
                        "28.517", "104.673", "四川宜宾市高县", "4.8", "4km",
                        LegacyTextFormatter.intensity("6.6"));
                break;
            default:
                throw new IllegalArgumentException("Unknown test source: " + sourceKey);
        }
        return dispatcher.dispatchTest(event, TEST_WARNING);
    }

    private BungeeNotificationEvent testJma(
            NotificationSource source,
            String flag,
            String reportTime,
            String rawOriginTime,
            String reportNumber,
            String latitude,
            String longitude,
            String region,
            String magnitude,
            String depth,
            String shindo,
            String reportType
    ) {
        NotificationProfile alert = profile(NotificationSource.JMA_ALERT);
        NotificationProfile forecast = profile(NotificationSource.JMA_FORECAST);
        String originTime = formatTime(JMA_TIME_PATTERN, "Asia/Tokyo", rawOriginTime);
        return deferred(source, channels -> NotificationIntentFactory.jma(
                flag, reportTime, originTime, reportNumber, latitude, longitude,
                region, magnitude, depth, shindo, reportType,
                channels.broadcast(), channels.title(), false, alert, forecast));
    }

    private BungeeNotificationEvent testRegional(
            NotificationSource source,
            String reportTime,
            String rawOriginTime,
            String reportNumber,
            String latitude,
            String longitude,
            String region,
            String magnitude,
            String depth,
            String intensity
    ) {
        NotificationProfile profile = profile(source);
        String originTime = formatTime(CHINA_TIME_PATTERN, "Asia/Shanghai", rawOriginTime);
        return deferred(source, channels -> NotificationIntentFactory.regional(
                source, reportTime, originTime, reportNumber, latitude, longitude,
                region, magnitude, depth, intensity,
                channels.broadcast(), channels.title(), false, profile));
    }

    private BungeeNotificationEvent testFujian() {
        NotificationProfile profile = profile(NotificationSource.FUJIAN_EEW);
        String originTime = formatTime(
                CHINA_TIME_PATTERN, "Asia/Shanghai", "2024-02-29 13:26:28");
        return deferred(NotificationSource.FUJIAN_EEW,
                channels -> NotificationIntentFactory.fujian(
                        "2024-02-29 13:27:40", originTime, "4", "23.47", "120.26",
                        "台湾嘉义县", "4.4", "最終報",
                        channels.broadcast(), channels.title(), false, profile));
    }

    private BungeeNotificationEvent realtime(RealtimeEewEvent event) {
        if (event instanceof JmaEewEvent) {
            return jma((JmaEewEvent) event);
        }
        if (event instanceof FujianEewEvent) {
            return fujian((FujianEewEvent) event);
        }
        if (event instanceof RegionalEewEvent) {
            return regional((RegionalEewEvent) event);
        }
        throw new IllegalArgumentException(
                "Unsupported realtime event: " + event.getClass().getName());
    }

    private BungeeNotificationEvent jma(JmaEewEvent event) {
        NotificationSource source = "警報".equals(event.getFlag())
                ? NotificationSource.JMA_ALERT
                : NotificationSource.JMA_FORECAST;
        NotificationProfile alert = profile(NotificationSource.JMA_ALERT);
        NotificationProfile forecast = profile(NotificationSource.JMA_FORECAST);
        String originTime = formatTime(
                JMA_TIME_PATTERN, "Asia/Tokyo", event.getOriginTime());
        String depth = LegacyTextFormatter.depthKilometers(event.getDepth());
        String shindo = LegacyTextFormatter.shindo(event.getMaximumIntensity());
        String reportType = LegacyTextFormatter.jmaReportType(
                event.isTraining(), event.isAssumption(),
                event.isFinalReport(), event.isCancelled());
        return deferred(source, channels -> NotificationIntentFactory.jma(
                event.getFlag(), event.getReportTime(), originTime,
                event.getReportNumber(), event.getLatitude(), event.getLongitude(),
                event.getRegion(), event.getMagnitude(), depth, shindo, reportType,
                channels.broadcast(), channels.title(), false, alert, forecast));
    }

    private BungeeNotificationEvent fujian(FujianEewEvent event) {
        NotificationProfile profile = profile(NotificationSource.FUJIAN_EEW);
        String originTime = formatTime(
                CHINA_TIME_PATTERN, "Asia/Shanghai", event.getOriginTime());
        String reportType = LegacyTextFormatter.finalReportType(event.isFinalReport());
        return deferred(NotificationSource.FUJIAN_EEW,
                channels -> NotificationIntentFactory.fujian(
                        event.getReportTime(), originTime, event.getReportNumber(),
                        event.getLatitude(), event.getLongitude(), event.getRegion(),
                        event.getMagnitude(), reportType,
                        channels.broadcast(), channels.title(), false, profile));
    }

    private BungeeNotificationEvent regional(RegionalEewEvent event) {
        NotificationSource source = notificationSource(event.getSource());
        NotificationProfile profile = profile(source);
        String originTime = formatTime(
                CHINA_TIME_PATTERN, "Asia/Shanghai", event.getOriginTime());
        String depth = LegacyTextFormatter.depthKilometers(event.getDepth());
        String intensity = event.getSource() == RegionalEewEvent.Source.CWA
                ? LegacyTextFormatter.shindo(event.getMaximumIntensity())
                : LegacyTextFormatter.intensity(event.getMaximumIntensity());
        return deferred(source, channels -> NotificationIntentFactory.regional(
                source, event.getReportTime(), originTime, event.getReportNumber(),
                event.getLatitude(), event.getLongitude(), event.getRegion(),
                event.getMagnitude(), depth, intensity,
                channels.broadcast(), channels.title(), false, profile));
    }

    private BungeeNotificationEvent earthquakeList(
            BungeeMessageProcessor.EarthquakeListPresentation presentation
    ) {
        NotificationSource source = presentation.source();
        String template = LegacyTextFormatter.legacyColors(config.source(source).message());
        return deferred(source, channels -> NotificationIntentFactory.earthquakeList(
                source, true, channels.broadcast(), () -> presentation.render(template))
                .orElse(null));
    }

    private NotificationProfile profile(NotificationSource source) {
        BungeeConfigSnapshot.SourceSettings settings = config.source(source);
        return new NotificationProfile(
                LegacyTextFormatter.legacyColors(settings.message()),
                LegacyTextFormatter.legacyColors(settings.title()),
                LegacyTextFormatter.legacyColors(settings.subtitle()),
                null,
                0.0,
                0.0);
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

    private static BungeeNotificationEvent deferred(
            NotificationSource source,
            IntentBuilder builder
    ) {
        return new BungeeNotificationEvent() {
            @Override
            public NotificationSource source() {
                return source;
            }

            @Override
            public NotificationIntent build(BungeeChannelPolicy channels) {
                return builder.build(channels);
            }
        };
    }

    @Override
    public void close() {
        dispatcher.close();
    }

    BungeeNotificationDispatcher dispatcher() {
        return dispatcher;
    }

    @FunctionalInterface
    private interface IntentBuilder {
        NotificationIntent build(BungeeChannelPolicy channels);
    }
}
