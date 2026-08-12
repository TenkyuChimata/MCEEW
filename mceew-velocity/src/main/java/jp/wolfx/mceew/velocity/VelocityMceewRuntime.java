package jp.wolfx.mceew.velocity;

import com.velocitypowered.api.proxy.ProxyServer;
import java.net.URI;
import java.net.http.HttpClient;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import jp.wolfx.mceew.VelocityMessageProcessor;
import jp.wolfx.mceew.websocket.WebSocketConnectionManager;
import org.slf4j.Logger;

/** Owns the single proxy-global Wolfx connection, router, and earthquake cache pipeline. */
final class VelocityMceewRuntime implements AutoCloseable {
    static final URI WOLFX_ENDPOINT = URI.create("wss://ws-api.wolfx.jp/all_eew");
    private static final long RECONNECT_DELAY_SECONDS = 5L;

    private final Object stateLock = new Object();
    private final VelocityMessageProcessor messageProcessor;
    private final VelocityJulLogger coreLogger;
    private final WebSocketConnectionManager webSocketManager;
    private final VelocityNotificationOrchestrator notificationOrchestrator;

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
        VelocityNotificationDispatcher dispatcher = new VelocityNotificationDispatcher(
                proxyServer, logger, delayScheduler, config.notificationConfig());
        VelocityNotificationOrchestrator orchestrator = new VelocityNotificationOrchestrator(
                config.notificationConfig(), dispatcher);
        return new VelocityMceewRuntime(
                config, delayScheduler, connector, logger, orchestrator);
    }

    VelocityMceewRuntime(
            VelocityConfigSnapshot config,
            VelocityDelayScheduler delayScheduler,
            WebSocketConnectionManager.Connector connector,
            Logger logger
    ) {
        this(config, delayScheduler, connector, logger, null);
    }

    VelocityMceewRuntime(
            VelocityConfigSnapshot config,
            VelocityDelayScheduler delayScheduler,
            WebSocketConnectionManager.Connector connector,
            Logger logger,
            VelocityNotificationOrchestrator notificationOrchestrator
    ) {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(delayScheduler, "delayScheduler");
        Objects.requireNonNull(connector, "connector");
        VelocityNotificationConfig notificationConfig = config.notificationConfigOrNull();
        messageProcessor = new VelocityMessageProcessor(
                config.jmaEnabled(),
                config.sichuanEnabled(),
                config.fujianEnabled(),
                config.cwaEnabled(),
                config.cencEnabled(),
                config.chongqingEnabled(),
                notificationConfig == null
                        ? "yyyy/MM/dd HH:mm:ss"
                        : notificationConfig.timeFormat());
        this.notificationOrchestrator = notificationOrchestrator;
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
        VelocityMessageProcessor.ProcessingResult result = messageProcessor.process(message);
        if (notificationOrchestrator != null) {
            notificationOrchestrator.accept(result);
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
            if (notificationOrchestrator != null) {
                notificationOrchestrator.close();
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

    private enum State {
        NEW,
        ACTIVE,
        CLOSED
    }
}
