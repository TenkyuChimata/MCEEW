package jp.wolfx.mceew.velocity;

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

    private State state = State.NEW;

    static VelocityMceewRuntime production(
            VelocityConfigSnapshot config,
            VelocityDelayScheduler delayScheduler,
            Logger logger
    ) {
        HttpClient httpClient = HttpClient.newHttpClient();
        WebSocketConnectionManager.Connector connector = listener -> httpClient
                .newWebSocketBuilder()
                .buildAsync(WOLFX_ENDPOINT, listener);
        return new VelocityMceewRuntime(config, delayScheduler, connector, logger);
    }

    VelocityMceewRuntime(
            VelocityConfigSnapshot config,
            VelocityDelayScheduler delayScheduler,
            WebSocketConnectionManager.Connector connector,
            Logger logger
    ) {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(delayScheduler, "delayScheduler");
        Objects.requireNonNull(connector, "connector");
        messageProcessor = new VelocityMessageProcessor(
                config.jmaEnabled(),
                config.sichuanEnabled(),
                config.fujianEnabled(),
                config.cwaEnabled(),
                config.cencEnabled(),
                config.chongqingEnabled());
        coreLogger = new VelocityJulLogger(Objects.requireNonNull(logger, "logger"));
        webSocketManager = new WebSocketConnectionManager(
                connector,
                delayScheduler,
                messageProcessor::process,
                coreLogger.logger(),
                RECONNECT_DELAY_SECONDS,
                TimeUnit.SECONDS);
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
            webSocketManager.stop();
        } finally {
            coreLogger.close();
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
