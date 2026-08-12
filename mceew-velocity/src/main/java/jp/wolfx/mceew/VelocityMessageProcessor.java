package jp.wolfx.mceew;

import com.google.gson.JsonObject;
import java.time.ZoneId;
import java.util.Objects;
import java.util.Optional;
import jp.wolfx.mceew.format.EarthquakeTimeFormatter;
import jp.wolfx.mceew.format.LegacyTextFormatter;
import jp.wolfx.mceew.message.RealtimeEewEvent;
import jp.wolfx.mceew.message.WolfxMessageRouter;
import jp.wolfx.mceew.notification.NotificationSource;

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
        private final EarthquakeListPresentation earthquakeList;

        private ProcessingResult(
                WolfxMessageRouter.MessageType messageType,
                Outcome outcome,
                RealtimeEewEvent realtimeEvent,
                EarthquakeListPresentation earthquakeList
        ) {
            this.messageType = messageType;
            this.outcome = outcome;
            this.realtimeEvent = realtimeEvent;
            this.earthquakeList = earthquakeList;
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

        /** Immutable list snapshot present for a successful earthquake-list cache transition. */
        public EarthquakeListPresentation earthquakeList() {
            return earthquakeList;
        }
    }

    /** Read-only presentation boundary over an immutable cached earthquake-list snapshot. */
    public static final class EarthquakeListPresentation {
        private final NotificationSource source;
        private final EarthquakeInfoCache.JmaSnapshot jma;
        private final EarthquakeInfoCache.CencSnapshot cenc;

        private EarthquakeListPresentation(
                NotificationSource source,
                EarthquakeInfoCache.JmaSnapshot jma,
                EarthquakeInfoCache.CencSnapshot cenc
        ) {
            this.source = source;
            this.jma = jma;
            this.cenc = cenc;
        }

        public NotificationSource source() {
            return source;
        }

        public String render(String template) {
            return jma != null ? jma.format(template) : cenc.format(template);
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
    private final String outputTimeFormat;

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
                JMA_TIME_PATTERN,
                EarthquakeTimeFormatter::isFresh);
    }

    public VelocityMessageProcessor(
            boolean jmaEnabled,
            boolean sichuanEnabled,
            boolean fujianEnabled,
            boolean cwaEnabled,
            boolean cencEnabled,
            boolean chongqingEnabled,
            String outputTimeFormat
    ) {
        this(jmaEnabled, sichuanEnabled, fujianEnabled, cwaEnabled, cencEnabled,
                chongqingEnabled, outputTimeFormat, EarthquakeTimeFormatter::isFresh);
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
        this(jmaEnabled, sichuanEnabled, fujianEnabled, cwaEnabled, cencEnabled,
                chongqingEnabled, JMA_TIME_PATTERN, freshnessEvaluator);
    }

    VelocityMessageProcessor(
            boolean jmaEnabled,
            boolean sichuanEnabled,
            boolean fujianEnabled,
            boolean cwaEnabled,
            boolean cencEnabled,
            boolean chongqingEnabled,
            String outputTimeFormat,
            FreshnessEvaluator freshnessEvaluator
    ) {
        this.jmaEnabled = jmaEnabled;
        this.sichuanEnabled = sichuanEnabled;
        this.fujianEnabled = fujianEnabled;
        this.cwaEnabled = cwaEnabled;
        this.cencEnabled = cencEnabled;
        this.chongqingEnabled = chongqingEnabled;
        this.outputTimeFormat = Objects.requireNonNull(outputTimeFormat, "outputTimeFormat");
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
                return result(type, Outcome.IGNORED, null, null);
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

    public Optional<EarthquakeListPresentation> latestJmaEarthquakeList() {
        EarthquakeInfoCache.JmaSnapshot snapshot = earthquakeInfoCache.getJma();
        return snapshot == null
                ? Optional.empty()
                : Optional.of(jmaPresentation(snapshot));
    }

    public Optional<EarthquakeListPresentation> latestCencEarthquakeList() {
        EarthquakeInfoCache.CencSnapshot snapshot = earthquakeInfoCache.getCenc();
        return snapshot == null
                ? Optional.empty()
                : Optional.of(cencPresentation(snapshot));
    }

    private ProcessingResult processRealtime(WolfxMessageRouter.RoutedMessage routed) {
        WolfxMessageRouter.MessageType type = routed.getType();
        if (!sourceEnabled(type)) {
            return result(type, Outcome.DISABLED_REALTIME, null, null);
        }

        RealtimeEewEvent event = router.parseRealtime(routed);
        String pattern = type == WolfxMessageRouter.MessageType.JMA_EEW
                ? JMA_TIME_PATTERN
                : CHINA_TIME_PATTERN;
        ZoneId zone = type == WolfxMessageRouter.MessageType.JMA_EEW ? TOKYO : SHANGHAI;
        if (!freshnessEvaluator.isFresh(event.getReportTime(), pattern, zone)) {
            return result(type, Outcome.STALE_REALTIME, null, null);
        }
        return result(type, Outcome.FRESH_REALTIME, event, null);
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
                outputTimeFormat,
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
        EarthquakeInfoCache.UpdateResult update = earthquakeInfoCache.updateJma(snapshot);
        return cacheResult(
                WolfxMessageRouter.MessageType.JMA_EARTHQUAKE_LIST,
                update,
                jmaPresentation(snapshot));
    }

    private ProcessingResult cacheCenc(JsonObject data) {
        JsonObject latest = data.get("No1").getAsJsonObject();
        String originTime = EarthquakeTimeFormatter.format(
                CHINA_TIME_PATTERN,
                outputTimeFormat,
                SHANGHAI.getId(),
                latest.get("time").getAsString());
        EarthquakeInfoCache.CencSnapshot snapshot = EarthquakeInfoCache.CencSnapshot.fromEqlist(
                data,
                originTime,
                LegacyTextFormatter.intensity(latest.get("intensity").getAsString()));
        EarthquakeInfoCache.UpdateResult update = earthquakeInfoCache.updateCenc(snapshot);
        return cacheResult(
                WolfxMessageRouter.MessageType.CENC_EARTHQUAKE_LIST,
                update,
                cencPresentation(snapshot));
    }

    private static ProcessingResult cacheResult(
            WolfxMessageRouter.MessageType type,
            EarthquakeInfoCache.UpdateResult update,
            EarthquakeListPresentation presentation
    ) {
        switch (update) {
            case FIRST_VALUE:
                return result(type, Outcome.CACHE_FIRST_VALUE, null, presentation);
            case UNCHANGED:
                return result(type, Outcome.CACHE_UNCHANGED, null, presentation);
            case CHANGED:
                return result(type, Outcome.CACHE_CHANGED, null, presentation);
            default:
                throw new IllegalStateException("Unhandled cache transition: " + update);
        }
    }

    private static ProcessingResult result(
            WolfxMessageRouter.MessageType type,
            Outcome outcome,
            RealtimeEewEvent event,
            EarthquakeListPresentation presentation
    ) {
        return new ProcessingResult(type, outcome, event, presentation);
    }

    private static EarthquakeListPresentation jmaPresentation(
            EarthquakeInfoCache.JmaSnapshot snapshot
    ) {
        return new EarthquakeListPresentation(
                NotificationSource.JMA_EARTHQUAKE_LIST, snapshot, null);
    }

    private static EarthquakeListPresentation cencPresentation(
            EarthquakeInfoCache.CencSnapshot snapshot
    ) {
        return new EarthquakeListPresentation(
                NotificationSource.CENC_EARTHQUAKE_LIST, null, snapshot);
    }
}
