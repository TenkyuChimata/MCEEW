package jp.wolfx.mceew.bungeecord;

import java.net.URI;
import java.net.http.HttpClient;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import jp.wolfx.mceew.BungeeMessageProcessor;
import jp.wolfx.mceew.format.LegacyTextFormatter;
import jp.wolfx.mceew.notification.NotificationSource;
import jp.wolfx.mceew.websocket.WebSocketConnectionManager;

/** Owns the single proxy-global Wolfx connection, router, and earthquake cache pipeline. */
final class BungeeMceewRuntime implements AutoCloseable {
    static final URI WOLFX_ENDPOINT = URI.create("wss://ws-api.wolfx.jp/all_eew");
    private static final long RECONNECT_DELAY_SECONDS = 5L;
    private static final String INFORMATION_NOT_AVAILABLE =
            "[MCEEW] Earthquake information is not available yet.";

    static final class PreparedConfiguration {
        private ConfigGeneration generation;

        private PreparedConfiguration(ConfigGeneration generation) {
            this.generation = generation;
        }

        private ConfigGeneration consume() {
            ConfigGeneration value = generation;
            if (value == null) {
                throw new IllegalStateException("Prepared runtime configuration was already used");
            }
            generation = null;
            return value;
        }
    }

    private final Object stateLock = new Object();
    private final BungeeMessageProcessor messageProcessor;
    private final WebSocketConnectionManager webSocketManager;
    private final AtomicReference<ConfigGeneration> configGeneration;

    private State state = State.NEW;

    static BungeeMceewRuntime production(
            BungeeConfigSnapshot config,
            BungeeDelayScheduler delayScheduler,
            Logger logger
    ) {
        HttpClient httpClient = HttpClient.newHttpClient();
        WebSocketConnectionManager.Connector connector = listener -> httpClient
                .newWebSocketBuilder()
                .buildAsync(WOLFX_ENDPOINT, listener);
        return new BungeeMceewRuntime(config, delayScheduler, connector, logger);
    }

    BungeeMceewRuntime(
            BungeeConfigSnapshot config,
            BungeeDelayScheduler delayScheduler,
            WebSocketConnectionManager.Connector connector,
            Logger logger
    ) {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(delayScheduler, "delayScheduler");
        Objects.requireNonNull(connector, "connector");
        BungeeConfigSnapshot.SourceGates gates = config.sourceGates();
        messageProcessor = new BungeeMessageProcessor(
                gates.jma(), gates.sichuan(), gates.fujian(), gates.cwa(), gates.cenc(),
                gates.chongqing(), config.timeFormat());
        configGeneration = new AtomicReference<>(new ConfigGeneration(
                processingPolicy(config), config));
        webSocketManager = new WebSocketConnectionManager(
                connector,
                delayScheduler,
                this::processMessage,
                Objects.requireNonNull(logger, "logger"),
                RECONNECT_DELAY_SECONDS,
                TimeUnit.SECONDS);
    }

    private void processMessage(String message) {
        processApplicationMessage(message);
    }

    // TEST SEAM: observes the complete policy generation used by one application message.
    BungeeMessageProcessor.ProcessingResult processApplicationMessage(String message) {
        ConfigGeneration generation = configGeneration.get();
        if (generation == null) {
            throw new IllegalStateException("Operational runtime has no active config generation");
        }
        return messageProcessor.process(message, generation.processingPolicy);
    }

    PreparedConfiguration prepareConfiguration(BungeeConfigSnapshot config) {
        Objects.requireNonNull(config, "config");
        if (!config.runtimeEnabled()) {
            throw new IllegalArgumentException(
                    "Disabled configuration does not require an operational runtime generation");
        }
        return new PreparedConfiguration(new ConfigGeneration(processingPolicy(config), config));
    }

    void commitConfiguration(PreparedConfiguration prepared) {
        Objects.requireNonNull(prepared, "prepared");
        configGeneration.set(prepared.consume());
    }

    String latestJmaEarthquakeInformation() {
        return latestEarthquakeInformation(NotificationSource.JMA_EARTHQUAKE_LIST);
    }

    String latestCencEarthquakeInformation() {
        return latestEarthquakeInformation(NotificationSource.CENC_EARTHQUAKE_LIST);
    }

    private String latestEarthquakeInformation(NotificationSource source) {
        ConfigGeneration generation = configGeneration.get();
        if (generation == null) {
            return INFORMATION_NOT_AVAILABLE;
        }
        BungeeConfigSnapshot.SourceSettings settings =
                generation.config.notificationSources().get(source);
        if (settings == null) {
            return INFORMATION_NOT_AVAILABLE;
        }
        String template = LegacyTextFormatter.legacyColors(settings.message());
        return source == NotificationSource.JMA_EARTHQUAKE_LIST
                ? messageProcessor.latestJmaEarthquakeList()
                        .map(presentation -> presentation.render(template))
                        .orElse(INFORMATION_NOT_AVAILABLE)
                : messageProcessor.latestCencEarthquakeList()
                        .map(presentation -> presentation.render(template))
                        .orElse(INFORMATION_NOT_AVAILABLE);
    }

    void start() {
        synchronized (stateLock) {
            if (state != State.NEW) {
                return;
            }
            state = State.ACTIVE;
        }
        try {
            webSocketManager.start();
        } catch (RuntimeException | Error error) {
            close();
            throw error;
        }
    }

    @Override
    public void close() {
        synchronized (stateLock) {
            if (state == State.CLOSED) {
                return;
            }
            state = State.CLOSED;
        }
        configGeneration.set(null);
        webSocketManager.stop();
    }

    boolean isActive() {
        synchronized (stateLock) {
            return state == State.ACTIVE;
        }
    }

    Object webSocketManagerIdentity() {
        return webSocketManager;
    }

    BungeeMessageProcessor messageProcessor() {
        return messageProcessor;
    }

    private static BungeeMessageProcessor.ProcessingPolicy processingPolicy(
            BungeeConfigSnapshot config
    ) {
        BungeeConfigSnapshot.SourceGates gates = config.sourceGates();
        return new BungeeMessageProcessor.ProcessingPolicy(
                gates.jma(), gates.sichuan(), gates.fujian(), gates.cwa(), gates.cenc(),
                gates.chongqing(), config.timeFormat());
    }

    private static final class ConfigGeneration {
        private final BungeeMessageProcessor.ProcessingPolicy processingPolicy;
        private final BungeeConfigSnapshot config;

        private ConfigGeneration(
                BungeeMessageProcessor.ProcessingPolicy processingPolicy,
                BungeeConfigSnapshot config
        ) {
            this.processingPolicy = processingPolicy;
            this.config = config;
        }
    }

    private enum State {
        NEW,
        ACTIVE,
        CLOSED
    }
}
