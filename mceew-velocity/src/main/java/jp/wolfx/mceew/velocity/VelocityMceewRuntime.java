package jp.wolfx.mceew.velocity;

import com.velocitypowered.api.proxy.ProxyServer;
import java.net.URI;
import java.net.http.HttpClient;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import jp.wolfx.mceew.VelocityMessageProcessor;
import jp.wolfx.mceew.notification.NotificationSource;
import jp.wolfx.mceew.websocket.WebSocketConnectionManager;
import org.slf4j.Logger;

/** Owns the single proxy-global Wolfx connection, router, and earthquake cache pipeline. */
final class VelocityMceewRuntime implements AutoCloseable {
    static final URI WOLFX_ENDPOINT = URI.create("wss://ws-api.wolfx.jp/all_eew");
    private static final long RECONNECT_DELAY_SECONDS = 5L;
    private static final String INFORMATION_NOT_AVAILABLE =
            "[MCEEW] Earthquake information is not available yet.";

    @FunctionalInterface
    interface NotificationOrchestratorFactory {
        VelocityNotificationOrchestrator create(VelocityNotificationConfig config);
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
    private final VelocityMessageProcessor messageProcessor;
    private final VelocityJulLogger coreLogger;
    private final WebSocketConnectionManager webSocketManager;
    private final NotificationOrchestratorFactory orchestratorFactory;
    private final AtomicReference<ConfigGeneration> configGeneration;

    private State state = State.NEW;

    static VelocityMceewRuntime production(
            VelocityConfigSnapshot config,
            VelocityDelayScheduler delayScheduler,
            Logger logger,
            ProxyServer proxyServer
    ) {
        HttpClient httpClient = HttpClient.newHttpClient();
        WebSocketConnectionManager.Connector connector = listener -> httpClient
                .newWebSocketBuilder()
                .buildAsync(WOLFX_ENDPOINT, listener);
        NotificationOrchestratorFactory orchestratorFactory = notificationConfig ->
                new VelocityNotificationOrchestrator(
                        notificationConfig,
                        new VelocityNotificationDispatcher(
                                proxyServer, logger, delayScheduler, notificationConfig));
        return new VelocityMceewRuntime(
                config, delayScheduler, connector, logger, orchestratorFactory);
    }

    VelocityMceewRuntime(
            VelocityConfigSnapshot config,
            VelocityDelayScheduler delayScheduler,
            WebSocketConnectionManager.Connector connector,
            Logger logger
    ) {
        this(config, delayScheduler, connector, logger,
                (NotificationOrchestratorFactory) null);
    }

    VelocityMceewRuntime(
            VelocityConfigSnapshot config,
            VelocityDelayScheduler delayScheduler,
            WebSocketConnectionManager.Connector connector,
            Logger logger,
            VelocityNotificationOrchestrator notificationOrchestrator
    ) {
        this(config, delayScheduler, connector, logger, null, notificationOrchestrator);
    }

    VelocityMceewRuntime(
            VelocityConfigSnapshot config,
            VelocityDelayScheduler delayScheduler,
            WebSocketConnectionManager.Connector connector,
            Logger logger,
            NotificationOrchestratorFactory orchestratorFactory
    ) {
        this(config, delayScheduler, connector, logger, orchestratorFactory,
                orchestratorFactory == null
                        ? null
                        : orchestratorFactory.create(config.notificationConfig()));
    }

    private VelocityMceewRuntime(
            VelocityConfigSnapshot config,
            VelocityDelayScheduler delayScheduler,
            WebSocketConnectionManager.Connector connector,
            Logger logger,
            NotificationOrchestratorFactory orchestratorFactory,
            VelocityNotificationOrchestrator initialOrchestrator
    ) {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(delayScheduler, "delayScheduler");
        Objects.requireNonNull(connector, "connector");
        this.orchestratorFactory = orchestratorFactory;
        messageProcessor = new VelocityMessageProcessor(
                config.jmaEnabled(),
                config.sichuanEnabled(),
                config.fujianEnabled(),
                config.cwaEnabled(),
                config.cencEnabled(),
                config.chongqingEnabled(),
                config.notificationConfigOrNull() == null
                        ? "yyyy/MM/dd HH:mm:ss"
                        : config.notificationConfig().timeFormat());
        configGeneration = new AtomicReference<>(new ConfigGeneration(
                processingPolicy(config), config.notificationConfigOrNull(), initialOrchestrator));
        coreLogger = new VelocityJulLogger(Objects.requireNonNull(logger, "logger"));
        webSocketManager = new WebSocketConnectionManager(
                connector,
                delayScheduler,
                this::processMessage,
                coreLogger.logger(),
                RECONNECT_DELAY_SECONDS,
                TimeUnit.SECONDS);
    }

    private void processMessage(String message) {
        processApplicationMessage(message);
    }

    // TEST SEAM: observes the complete policy generation used by one application message.
    VelocityMessageProcessor.ProcessingResult processApplicationMessage(String message) {
        ConfigGeneration generation = configGeneration.get();
        if (generation == null) {
            throw new IllegalStateException("Operational runtime has no active config generation");
        }
        VelocityMessageProcessor.ProcessingResult result =
                messageProcessor.process(message, generation.processingPolicy);
        if (generation.notificationOrchestrator != null) {
            generation.notificationOrchestrator.accept(result);
        }
        return result;
    }

    PreparedConfiguration prepareConfiguration(VelocityConfigSnapshot config) {
        Objects.requireNonNull(config, "config");
        if (!config.runtimeEnabled()) {
            throw new IllegalArgumentException(
                    "Disabled configuration does not require an operational runtime generation");
        }
        VelocityNotificationOrchestrator preparedOrchestrator = orchestratorFactory == null
                ? null
                : orchestratorFactory.create(config.notificationConfig());
        return new PreparedConfiguration(new ConfigGeneration(
                processingPolicy(config), config.notificationConfigOrNull(), preparedOrchestrator));
    }

    void commitConfiguration(PreparedConfiguration prepared) {
        Objects.requireNonNull(prepared, "prepared");
        ConfigGeneration replacement = prepared.consume();
        ConfigGeneration previous = configGeneration.getAndSet(replacement);
        if (previous != null) {
            previous.close();
        }
    }

    String latestJmaEarthquakeInformation() {
        return latestEarthquakeInformation(true);
    }

    String latestCencEarthquakeInformation() {
        return latestEarthquakeInformation(false);
    }

    private String latestEarthquakeInformation(boolean jma) {
        ConfigGeneration generation = configGeneration.get();
        if (generation == null || generation.notificationConfig == null) {
            return INFORMATION_NOT_AVAILABLE;
        }
        NotificationSource source = jma
                ? NotificationSource.JMA_EARTHQUAKE_LIST
                : NotificationSource.CENC_EARTHQUAKE_LIST;
        String template = generation.notificationConfig.source(source).earthquakeListTemplate();
        return jma
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
        generation.notificationOrchestrator.dispatchTest(sourceKey);
        return true;
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
            try {
                webSocketManager.stop();
            } finally {
                coreLogger.close();
            }
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

    VelocityMessageProcessor messageProcessor() {
        return messageProcessor;
    }

    int coreLogHandlerCount() {
        return coreLogger.handlerCount();
    }

    private static VelocityMessageProcessor.ProcessingPolicy processingPolicy(
            VelocityConfigSnapshot config
    ) {
        VelocityNotificationConfig notificationConfig = config.notificationConfigOrNull();
        return new VelocityMessageProcessor.ProcessingPolicy(
                config.jmaEnabled(), config.sichuanEnabled(), config.fujianEnabled(),
                config.cwaEnabled(), config.cencEnabled(), config.chongqingEnabled(),
                notificationConfig == null
                        ? "yyyy/MM/dd HH:mm:ss"
                        : notificationConfig.timeFormat());
    }

    private static final class ConfigGeneration implements AutoCloseable {
        private final VelocityMessageProcessor.ProcessingPolicy processingPolicy;
        private final VelocityNotificationConfig notificationConfig;
        private final VelocityNotificationOrchestrator notificationOrchestrator;

        private ConfigGeneration(
                VelocityMessageProcessor.ProcessingPolicy processingPolicy,
                VelocityNotificationConfig notificationConfig,
                VelocityNotificationOrchestrator notificationOrchestrator
        ) {
            this.processingPolicy = processingPolicy;
            this.notificationConfig = notificationConfig;
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
