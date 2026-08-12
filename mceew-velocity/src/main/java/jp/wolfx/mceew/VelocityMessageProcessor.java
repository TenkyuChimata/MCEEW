package jp.wolfx.mceew;

import com.google.gson.JsonObject;
import java.time.ZoneId;
import java.util.Objects;
import jp.wolfx.mceew.format.EarthquakeTimeFormatter;
import jp.wolfx.mceew.format.LegacyTextFormatter;
import jp.wolfx.mceew.message.RealtimeEewEvent;
import jp.wolfx.mceew.message.WolfxMessageRouter;

/**
 * Velocity-side orchestration of the shared router, freshness rules, and earthquake cache.
 * This class deliberately lives beside the package-private cache without changing the core API.
 */
public final class VelocityMessageProcessor {
    private static final String JMA_TIME_PATTERN = "yyyy/MM/dd HH:mm:ss";
    private static final String CHINA_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";
    private static final ZoneId TOKYO = ZoneId.of("Asia/Tokyo");
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    @FunctionalInterface
    interface FreshnessEvaluator {
        boolean isFresh(String reportTime, String pattern, ZoneId zone);
    }

    public enum Outcome {
        FRESH_REALTIME,
        STALE_REALTIME,
        DISABLED_REALTIME,
        CACHE_FIRST_VALUE,
        CACHE_UNCHANGED,
        CACHE_CHANGED,
        IGNORED
    }

    public static final class ProcessingResult {
        private final WolfxMessageRouter.MessageType messageType;
        private final Outcome outcome;
        private final RealtimeEewEvent realtimeEvent;

        private ProcessingResult(
                WolfxMessageRouter.MessageType messageType,
                Outcome outcome,
                RealtimeEewEvent realtimeEvent
        ) {
            this.messageType = messageType;
            this.outcome = outcome;
            this.realtimeEvent = realtimeEvent;
        }

        public WolfxMessageRouter.MessageType messageType() {
            return messageType;
        }

        public Outcome outcome() {
            return outcome;
        }

        /** A value is present only when a real-time event is enabled and fresh. */
        public RealtimeEewEvent realtimeEvent() {
            return realtimeEvent;
        }
    }

    private final WolfxMessageRouter router = new WolfxMessageRouter();
    private final EarthquakeInfoCache earthquakeInfoCache = new EarthquakeInfoCache();
    private final boolean jmaEnabled;
    private final boolean sichuanEnabled;
    private final boolean fujianEnabled;
    private final boolean cwaEnabled;
    private final boolean cencEnabled;
    private final boolean chongqingEnabled;
    private final FreshnessEvaluator freshnessEvaluator;

    public VelocityMessageProcessor(
            boolean jmaEnabled,
            boolean sichuanEnabled,
            boolean fujianEnabled,
            boolean cwaEnabled,
            boolean cencEnabled,
            boolean chongqingEnabled
    ) {
        this(
                jmaEnabled,
                sichuanEnabled,
                fujianEnabled,
                cwaEnabled,
                cencEnabled,
                chongqingEnabled,
                EarthquakeTimeFormatter::isFresh);
    }

    VelocityMessageProcessor(
            boolean jmaEnabled,
            boolean sichuanEnabled,
            boolean fujianEnabled,
            boolean cwaEnabled,
            boolean cencEnabled,
            boolean chongqingEnabled,
            FreshnessEvaluator freshnessEvaluator
    ) {
        this.jmaEnabled = jmaEnabled;
        this.sichuanEnabled = sichuanEnabled;
        this.fujianEnabled = fujianEnabled;
        this.cwaEnabled = cwaEnabled;
        this.cencEnabled = cencEnabled;
        this.chongqingEnabled = chongqingEnabled;
        this.freshnessEvaluator = Objects.requireNonNull(freshnessEvaluator, "freshnessEvaluator");
    }

    public ProcessingResult process(String message) {
        WolfxMessageRouter.RoutedMessage routed = router.route(message);
        WolfxMessageRouter.MessageType type = routed.getType();
        switch (type) {
            case JMA_EEW:
            case SICHUAN_EEW:
            case FUJIAN_EEW:
            case CWA_EEW:
            case CENC_EEW:
            case CHONGQING_EEW:
                return processRealtime(routed);
            case JMA_EARTHQUAKE_LIST:
                return cacheJma(routed.getPayload());
            case CENC_EARTHQUAKE_LIST:
                return cacheCenc(routed.getPayload());
            case HEARTBEAT:
            case UNKNOWN:
                return result(type, Outcome.IGNORED, null);
            default:
                throw new IllegalStateException("Unhandled Wolfx message type: " + type);
        }
    }

    public boolean hasJmaCacheValue() {
        return earthquakeInfoCache.getJma() != null;
    }

    public boolean hasCencCacheValue() {
        return earthquakeInfoCache.getCenc() != null;
    }

    private ProcessingResult processRealtime(WolfxMessageRouter.RoutedMessage routed) {
        WolfxMessageRouter.MessageType type = routed.getType();
        if (!sourceEnabled(type)) {
            return result(type, Outcome.DISABLED_REALTIME, null);
        }

        RealtimeEewEvent event = router.parseRealtime(routed);
        String pattern = type == WolfxMessageRouter.MessageType.JMA_EEW
                ? JMA_TIME_PATTERN
                : CHINA_TIME_PATTERN;
        ZoneId zone = type == WolfxMessageRouter.MessageType.JMA_EEW ? TOKYO : SHANGHAI;
        if (!freshnessEvaluator.isFresh(event.getReportTime(), pattern, zone)) {
            return result(type, Outcome.STALE_REALTIME, null);
        }
        return result(type, Outcome.FRESH_REALTIME, event);
    }

    private boolean sourceEnabled(WolfxMessageRouter.MessageType type) {
        switch (type) {
            case JMA_EEW:
                return jmaEnabled;
            case SICHUAN_EEW:
                return sichuanEnabled;
            case FUJIAN_EEW:
                return fujianEnabled;
            case CWA_EEW:
                return cwaEnabled;
            case CENC_EEW:
                return cencEnabled;
            case CHONGQING_EEW:
                return chongqingEnabled;
            default:
                throw new IllegalArgumentException("Not a real-time source: " + type);
        }
    }

    private ProcessingResult cacheJma(JsonObject data) {
        JsonObject latest = data.get("No1").getAsJsonObject();
        String originTime = EarthquakeTimeFormatter.format(
                JMA_TIME_PATTERN,
                JMA_TIME_PATTERN,
                TOKYO.getId(),
                latest.get("time_full").getAsString());
        EarthquakeInfoCache.JmaSnapshot snapshot = new EarthquakeInfoCache.JmaSnapshot(
                data.get("md5").getAsString(),
                originTime,
                latest.get("location").getAsString(),
                latest.get("magnitude").getAsString(),
                latest.get("depth").getAsString(),
                latest.get("latitude").getAsString(),
                latest.get("longitude").getAsString(),
                LegacyTextFormatter.shindo(latest.get("shindo").getAsString()),
                latest.get("info").getAsString());
        return cacheResult(
                WolfxMessageRouter.MessageType.JMA_EARTHQUAKE_LIST,
                earthquakeInfoCache.updateJma(snapshot));
    }

    private ProcessingResult cacheCenc(JsonObject data) {
        JsonObject latest = data.get("No1").getAsJsonObject();
        String originTime = EarthquakeTimeFormatter.format(
                CHINA_TIME_PATTERN,
                CHINA_TIME_PATTERN,
                SHANGHAI.getId(),
                latest.get("time").getAsString());
        EarthquakeInfoCache.CencSnapshot snapshot = EarthquakeInfoCache.CencSnapshot.fromEqlist(
                data,
                originTime,
                LegacyTextFormatter.intensity(latest.get("intensity").getAsString()));
        return cacheResult(
                WolfxMessageRouter.MessageType.CENC_EARTHQUAKE_LIST,
                earthquakeInfoCache.updateCenc(snapshot));
    }

    private static ProcessingResult cacheResult(
            WolfxMessageRouter.MessageType type,
            EarthquakeInfoCache.UpdateResult update
    ) {
        switch (update) {
            case FIRST_VALUE:
                return result(type, Outcome.CACHE_FIRST_VALUE, null);
            case UNCHANGED:
                return result(type, Outcome.CACHE_UNCHANGED, null);
            case CHANGED:
                return result(type, Outcome.CACHE_CHANGED, null);
            default:
                throw new IllegalStateException("Unhandled cache transition: " + update);
        }
    }

    private static ProcessingResult result(
            WolfxMessageRouter.MessageType type,
            Outcome outcome,
            RealtimeEewEvent event
    ) {
        return new ProcessingResult(type, outcome, event);
    }
}
