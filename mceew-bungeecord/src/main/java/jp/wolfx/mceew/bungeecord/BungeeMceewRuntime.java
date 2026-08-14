package jp.wolfx.mceew.bungeecord;

import java.net.URI;
import java.net.http.HttpClient;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
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

    @FunctionalInterface
    interface NotificationOrchestratorFactory {
        BungeeNotificationSink create(BungeeConfigSnapshot config);
    }

    static final class PreparedConfiguration implements AutoCloseable {
        private final ConfigGeneration generation;
        private boolean consumed;

        private PreparedConfiguration(ConfigGeneration generation) {
            this.generation = generation;
        }

        private ConfigGeneration consume() {
            if (consumed) {
                throw new IllegalStateException("Prepared runtime configuration was already used");
            }
            consumed = true;
            return generation;
        }

        @Override
        public void close() {
            if (!consumed) {
                consumed = true;
                generation.close();
            }
        }
    }

    private final Object stateLock = new Object();
    private final BungeeMessageProcessor messageProcessor;
    private final WebSocketConnectionManager webSocketManager;
    private final NotificationOrchestratorFactory orchestratorFactory;
    private final AtomicReference<ConfigGeneration> configGeneration;
    private final Logger logger;

    private State state = State.NEW;

    static BungeeMceewRuntime production(
            BungeeConfigSnapshot config,
            BungeeDelayScheduler delayScheduler,
            Logger logger,
            BungeeNotificationPlatform notificationPlatform
    ) {
        HttpClient httpClient = HttpClient.newHttpClient();
        WebSocketConnectionManager.Connector connector = listener -> httpClient
                .newWebSocketBuilder()
                .buildAsync(WOLFX_ENDPOINT, listener);
        NotificationOrchestratorFactory orchestratorFactory = currentConfig ->
                new BungeeNotificationOrchestrator(
                        currentConfig,
                        new BungeeNotificationDispatcher(
                                notificationPlatform,
                                delayScheduler,
                                currentConfig,
                                logger));
        return new BungeeMceewRuntime(
                config, delayScheduler, connector, logger, orchestratorFactory);
    }

    BungeeMceewRuntime(
            BungeeConfigSnapshot config,
            BungeeDelayScheduler delayScheduler,
            WebSocketConnectionManager.Connector connector,
            Logger logger
    ) {
        this(config, delayScheduler, connector, logger,
                (NotificationOrchestratorFactory) null);
    }

    BungeeMceewRuntime(
            BungeeConfigSnapshot config,
            BungeeDelayScheduler delayScheduler,
            WebSocketConnectionManager.Connector connector,
            Logger logger,
            BungeeNotificationSink notificationOrchestrator
    ) {
        this(config, delayScheduler, connector, logger, null, notificationOrchestrator);
    }

    BungeeMceewRuntime(
            BungeeConfigSnapshot config,
            BungeeDelayScheduler delayScheduler,
            WebSocketConnectionManager.Connector connector,
            Logger logger,
            NotificationOrchestratorFactory orchestratorFactory
    ) {
        this(config, delayScheduler, connector, logger, orchestratorFactory,
                orchestratorFactory == null ? null : orchestratorFactory.create(config));
    }

    private BungeeMceewRuntime(
            BungeeConfigSnapshot config,
            BungeeDelayScheduler delayScheduler,
            WebSocketConnectionManager.Connector connector,
            Logger logger,
            NotificationOrchestratorFactory orchestratorFactory,
            BungeeNotificationSink initialOrchestrator
    ) {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(delayScheduler, "delayScheduler");
        Objects.requireNonNull(connector, "connector");
        this.logger = Objects.requireNonNull(logger, "logger");
        BungeeConfigSnapshot.SourceGates gates = config.sourceGates();
        this.orchestratorFactory = orchestratorFactory;
        messageProcessor = new BungeeMessageProcessor(
                gates.jma(), gates.sichuan(), gates.fujian(), gates.cwa(), gates.cenc(),
                gates.chongqing(), config.timeFormat());
        configGeneration = new AtomicReference<>(new ConfigGeneration(
                processingPolicy(config), config, initialOrchestrator));
        webSocketManager = new WebSocketConnectionManager(
                connector,
                delayScheduler,
                this::processMessage,
                this.logger,
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
        BungeeMessageProcessor.ProcessingResult result =
                messageProcessor.process(message, generation.processingPolicy);
        if (generation.notificationOrchestrator != null) {
            try {
                generation.notificationOrchestrator.accept(result);
            } catch (RuntimeException error) {
                logger.log(Level.WARNING,
                        "MCEEW BungeeCord notification processing failed; "
                                + "the Wolfx connection remains active.",
                        error);
            }
        }
        return result;
    }

    PreparedConfiguration prepareConfiguration(BungeeConfigSnapshot config) {
        Objects.requireNonNull(config, "config");
        if (!config.runtimeEnabled()) {
            throw new IllegalArgumentException(
                    "Disabled configuration does not require an operational runtime generation");
        }
        BungeeNotificationSink preparedOrchestrator = orchestratorFactory == null
                ? null
                : orchestratorFactory.create(config);
        return new PreparedConfiguration(new ConfigGeneration(
                processingPolicy(config), config, preparedOrchestrator));
    }

    void commitConfiguration(PreparedConfiguration prepared) {
        Objects.requireNonNull(prepared, "prepared");
        ConfigGeneration replacement = prepared.consume();
        ConfigGeneration previous = configGeneration.getAndSet(replacement);
        if (previous != null) {
            try {
                previous.close();
            } catch (RuntimeException error) {
                logger.log(Level.WARNING,
                        "MCEEW BungeeCord could not completely close the previous "
                                + "notification policy.",
                        error);
            }
        }
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

    boolean dispatchTest(String sourceKey) {
        ConfigGeneration generation = configGeneration.get();
        if (generation == null || generation.notificationOrchestrator == null
                || !isActive()) {
            return false;
        }
        try {
            return generation.notificationOrchestrator.dispatchTest(sourceKey);
        } catch (RuntimeException error) {
            logger.log(Level.WARNING,
                    "MCEEW BungeeCord test notification dispatch failed.", error);
            return false;
        }
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
        try {
            ConfigGeneration generation = configGeneration.getAndSet(null);
            if (generation != null) {
                generation.close();
            }
        } finally {
            webSocketManager.stop();
        }
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

    Object notificationOrchestratorIdentity() {
        ConfigGeneration generation = configGeneration.get();
        return generation == null ? null : generation.notificationOrchestrator;
    }

    private static BungeeMessageProcessor.ProcessingPolicy processingPolicy(
            BungeeConfigSnapshot config
    ) {
        BungeeConfigSnapshot.SourceGates gates = config.sourceGates();
        return new BungeeMessageProcessor.ProcessingPolicy(
                gates.jma(), gates.sichuan(), gates.fujian(), gates.cwa(), gates.cenc(),
                gates.chongqing(), config.timeFormat());
    }

    private static final class ConfigGeneration implements AutoCloseable {
        private final BungeeMessageProcessor.ProcessingPolicy processingPolicy;
        private final BungeeConfigSnapshot config;
        private final BungeeNotificationSink notificationOrchestrator;

        private ConfigGeneration(
                BungeeMessageProcessor.ProcessingPolicy processingPolicy,
                BungeeConfigSnapshot config,
                BungeeNotificationSink notificationOrchestrator
        ) {
            this.processingPolicy = processingPolicy;
            this.config = config;
            this.notificationOrchestrator = notificationOrchestrator;
        }

        @Override
        public void close() {
            if (notificationOrchestrator != null) {
                notificationOrchestrator.close();
            }
        }
    }

    private enum State {
        NEW,
        ACTIVE,
        CLOSED
    }
}
