package jp.wolfx.mceew.websocket;

import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.net.http.WebSocketHandshakeException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.function.Consumer;

/**
 * Owns the single Wolfx WebSocket connection and all of its reconnect state.
 */
public final class WebSocketConnectionManager {
    static final String JMA_QUERY = "query_jmaeqlist";
    static final String CENC_QUERY = "query_cenceqlist";
    static final long BOOTSTRAP_QUERY_INTERVAL_MILLIS = 1200L;
    static final List<String> BOOTSTRAP_QUERIES = List.of(JMA_QUERY, CENC_QUERY);

    @FunctionalInterface
    public interface Connector {
        CompletableFuture<WebSocket> connect(WebSocket.Listener listener);
    }

    @FunctionalInterface
    public interface DelayScheduler {
        ScheduledAction schedule(Runnable task, long delay, TimeUnit unit);
    }

    @FunctionalInterface
    public interface ScheduledAction {
        void cancel();
    }

    private final Object lock = new Object();
    private final Connector connector;
    private final DelayScheduler delayScheduler;
    private final Consumer<String> messageConsumer;
    private final Logger logger;
    private final long reconnectDelay;
    private final TimeUnit reconnectDelayUnit;

    private long generation;
    private boolean stopped = true;
    private long bootstrapGeneration = -1L;
    private WebSocket activeSocket;
    private CompletableFuture<WebSocket> connecting;
    private ScheduledAction scheduledReconnect;
    private ScheduledAction scheduledBootstrap;

    public WebSocketConnectionManager(
            Connector connector,
            DelayScheduler delayScheduler,
            Consumer<String> messageConsumer,
            Logger logger,
            long reconnectDelay,
            TimeUnit reconnectDelayUnit
    ) {
        this.connector = Objects.requireNonNull(connector, "connector");
        this.delayScheduler = Objects.requireNonNull(delayScheduler, "delayScheduler");
        this.messageConsumer = Objects.requireNonNull(messageConsumer, "messageConsumer");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.reconnectDelay = reconnectDelay;
        this.reconnectDelayUnit = Objects.requireNonNull(reconnectDelayUnit, "reconnectDelayUnit");
    }

    public void start() {
        long token;
        synchronized (lock) {
            if (!stopped) {
                return;
            }
            stopped = false;
            token = ++generation;
        }
        connect(token);
    }

    public void restart() {
        WebSocket oldSocket;
        CompletableFuture<WebSocket> oldConnection;
        ScheduledAction oldReconnect;
        ScheduledAction oldBootstrap;
        long token;
        synchronized (lock) {
            stopped = false;
            token = ++generation;
            oldSocket = activeSocket;
            activeSocket = null;
            oldConnection = connecting;
            connecting = null;
            oldReconnect = scheduledReconnect;
            scheduledReconnect = null;
            oldBootstrap = scheduledBootstrap;
            scheduledBootstrap = null;
        }
        cancel(oldReconnect);
        cancel(oldBootstrap);
        cancel(oldConnection);
        closeBeforeRestart(oldSocket, token);
    }

    public void stop() {
        WebSocket oldSocket;
        CompletableFuture<WebSocket> oldConnection;
        ScheduledAction oldReconnect;
        ScheduledAction oldBootstrap;
        synchronized (lock) {
            if (stopped && activeSocket == null && connecting == null
                    && scheduledReconnect == null && scheduledBootstrap == null) {
                return;
            }
            stopped = true;
            generation++;
            oldSocket = activeSocket;
            activeSocket = null;
            oldConnection = connecting;
            connecting = null;
            oldReconnect = scheduledReconnect;
            scheduledReconnect = null;
            oldBootstrap = scheduledBootstrap;
            scheduledBootstrap = null;
        }
        cancel(oldReconnect);
        cancel(oldBootstrap);
        cancel(oldConnection);
        closeWithoutReconnect(oldSocket, "Plugin disabled");
    }

    private void connect(long token) {
        ConnectionListener listener = new ConnectionListener(token);
        CompletableFuture<WebSocket> future;
        try {
            future = Objects.requireNonNull(connector.connect(listener), "connector future");
        } catch (Throwable error) {
            handleConnectFailure(token, error);
            return;
        }

        boolean stale;
        synchronized (lock) {
            stale = !isCurrentGeneration(token);
            if (!stale && activeSocket == null) {
                connecting = future;
            }
        }
        if (stale) {
            future.cancel(true);
        }

        future.whenComplete((socket, error) -> {
            if (error != null) {
                handleConnectFailure(token, error);
                return;
            }
            boolean current;
            synchronized (lock) {
                current = isCurrentGeneration(token);
                if (connecting == future) {
                    connecting = null;
                }
            }
            if (!current && socket != null) {
                socket.abort();
            }
        });
    }

    private void closeBeforeRestart(WebSocket socket, long token) {
        if (socket == null) {
            connectIfCurrent(token);
            return;
        }
        try {
            socket.sendClose(WebSocket.NORMAL_CLOSURE, "Plugin reload")
                    .orTimeout(2, TimeUnit.SECONDS)
                    .whenComplete((ignored, error) -> {
                        socket.abort();
                        connectIfCurrent(token);
                    });
        } catch (Throwable error) {
            socket.abort();
            connectIfCurrent(token);
        }
    }

    private void closeWithoutReconnect(WebSocket socket, String reason) {
        if (socket == null) {
            return;
        }
        try {
            socket.sendClose(WebSocket.NORMAL_CLOSURE, reason)
                    .orTimeout(2, TimeUnit.SECONDS)
                    .whenComplete((ignored, error) -> socket.abort());
        } catch (Throwable error) {
            socket.abort();
        }
    }

    private void connectIfCurrent(long token) {
        synchronized (lock) {
            if (!isCurrentGeneration(token)) {
                return;
            }
        }
        connect(token);
    }

    private void handleConnectFailure(long token, Throwable error) {
        synchronized (lock) {
            if (!isCurrentGeneration(token)) {
                return;
            }
            connecting = null;
        }
        logFailure("Failed to connect to WebSocket API.", error);
        scheduleReconnect(token);
    }

    private void handleConnectionFailure(long token, WebSocket socket, Throwable error, String message) {
        ScheduledAction bootstrap;
        synchronized (lock) {
            if (!isCurrentSocket(token, socket)) {
                return;
            }
            activeSocket = null;
            bootstrap = scheduledBootstrap;
            scheduledBootstrap = null;
        }
        cancel(bootstrap);
        logFailure(message, error);
        socket.abort();
        scheduleReconnect(token);
    }

    private void scheduleReconnect(long token) {
        synchronized (lock) {
            if (!isCurrentGeneration(token) || scheduledReconnect != null) {
                return;
            }
            logger.warning("Trying to reconnect to WebSocket API in "
                    + reconnectDelay + " " + reconnectDelayUnit.toString().toLowerCase() + ".");
            try {
                scheduledReconnect = delayScheduler.schedule(
                        () -> runReconnect(token), reconnectDelay, reconnectDelayUnit);
            } catch (Throwable error) {
                logger.log(Level.WARNING, "Unable to schedule WebSocket reconnect.", error);
            }
        }
    }

    private void runReconnect(long token) {
        long nextToken;
        synchronized (lock) {
            if (!isCurrentGeneration(token) || scheduledReconnect == null) {
                return;
            }
            scheduledReconnect = null;
            nextToken = ++generation;
        }
        connect(nextToken);
    }

    private void sendBootstrapQuery(long token, WebSocket socket, int queryIndex) {
        String query = BOOTSTRAP_QUERIES.get(queryIndex);
        CompletableFuture<WebSocket> send = null;
        Throwable sendFailure = null;
        synchronized (lock) {
            if (!isCurrentBootstrap(token, socket)) {
                return;
            }
            try {
                send = socket.sendText(query, true);
            } catch (Throwable error) {
                sendFailure = error;
            }
        }
        if (sendFailure != null) {
            handleBootstrapFailure(token, socket, query, sendFailure);
            return;
        }
        CompletableFuture<WebSocket> sendResult = send;
        sendResult.whenComplete((ignored, error) -> {
            if (error != null) {
                handleBootstrapFailure(token, socket, query, error);
            } else if (queryIndex + 1 < BOOTSTRAP_QUERIES.size()) {
                scheduleBootstrapQuery(token, socket, queryIndex + 1);
            }
        });
    }

    private void scheduleBootstrapQuery(long token, WebSocket socket, int queryIndex) {
        Throwable scheduleFailure = null;
        synchronized (lock) {
            if (!isCurrentBootstrap(token, socket) || scheduledBootstrap != null) {
                return;
            }
            try {
                scheduledBootstrap = delayScheduler.schedule(
                        () -> runBootstrapQuery(token, socket, queryIndex),
                        BOOTSTRAP_QUERY_INTERVAL_MILLIS,
                        TimeUnit.MILLISECONDS
                );
            } catch (Throwable error) {
                scheduleFailure = error;
            }
        }
        if (scheduleFailure != null) {
            handleBootstrapFailure(
                    token, socket, BOOTSTRAP_QUERIES.get(queryIndex), scheduleFailure);
        }
    }

    private void runBootstrapQuery(long token, WebSocket socket, int queryIndex) {
        synchronized (lock) {
            if (!isCurrentBootstrap(token, socket) || scheduledBootstrap == null) {
                return;
            }
            scheduledBootstrap = null;
        }
        sendBootstrapQuery(token, socket, queryIndex);
    }

    private void handleBootstrapFailure(
            long token, WebSocket socket, String query, Throwable error) {
        handleConnectionFailure(token, socket, error,
                "Failed to send WebSocket bootstrap query: " + query);
    }

    private boolean isCurrentBootstrap(long token, WebSocket socket) {
        return bootstrapGeneration == token && isCurrentSocket(token, socket);
    }

    private boolean isCurrentGeneration(long token) {
        return !stopped && token == generation;
    }

    private boolean isCurrentSocket(long token, WebSocket socket) {
        return isCurrentGeneration(token) && activeSocket == socket;
    }

    private void logFailure(String message, Throwable error) {
        Throwable handshake = findCause(error, WebSocketHandshakeException.class);
        if (handshake != null) {
            HttpResponse<?> response = ((WebSocketHandshakeException) handshake).getResponse();
            logger.warning(message + " WebSocket handshake HTTP status: "
                    + response.statusCode() + ", HTTP version: " + response.version() + ".");
        }
        logger.log(Level.WARNING, message, unwrap(error));
    }

    private Throwable unwrap(Throwable error) {
        Throwable current = error;
        while ((current instanceof CompletionException) && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private Throwable findCause(Throwable error, Class<? extends Throwable> type) {
        Throwable current = error;
        while (current != null) {
            if (type.isInstance(current)) {
                return current;
            }
            current = current.getCause();
        }
        return null;
    }

    private void cancel(ScheduledAction action) {
        if (action != null) {
            action.cancel();
        }
    }

    private void cancel(CompletableFuture<WebSocket> future) {
        if (future != null) {
            future.cancel(true);
        }
    }

    private final class ConnectionListener implements WebSocket.Listener {
        private final long token;
        private final StringBuilder messageBuffer = new StringBuilder();

        private ConnectionListener(long token) {
            this.token = token;
        }

        @Override
        public void onOpen(WebSocket socket) {
            boolean startBootstrap;
            synchronized (lock) {
                if (!isCurrentGeneration(token) || (activeSocket != null && activeSocket != socket)) {
                    socket.abort();
                    return;
                }
                if (activeSocket == socket) {
                    return;
                }
                activeSocket = socket;
                connecting = null;
                startBootstrap = bootstrapGeneration != token;
                if (startBootstrap) {
                    bootstrapGeneration = token;
                }
            }
            logger.info("Connected to WebSocket API.");
            requestNext(token, socket);
            if (startBootstrap) {
                sendBootstrapQuery(token, socket, 0);
            }
        }

        @Override
        public CompletionStage<?> onText(WebSocket socket, CharSequence data, boolean last) {
            synchronized (lock) {
                if (!isCurrentSocket(token, socket)) {
                    return CompletableFuture.completedFuture(null);
                }
            }
            try {
                messageBuffer.append(data);
                if (last) {
                    String completeMessage = messageBuffer.toString();
                    messageBuffer.setLength(0);
                    synchronized (lock) {
                        if (!isCurrentSocket(token, socket)) {
                            return CompletableFuture.completedFuture(null);
                        }
                        messageConsumer.accept(completeMessage);
                    }
                }
            } catch (Throwable error) {
                logger.log(Level.WARNING,
                        "Failed to process a WebSocket API message; the message was ignored.",
                        error);
            }
            requestNext(token, socket);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<?> onClose(WebSocket socket, int statusCode, String reason) {
            ScheduledAction bootstrap;
            synchronized (lock) {
                if (!isCurrentSocket(token, socket)) {
                    return CompletableFuture.completedFuture(null);
                }
                activeSocket = null;
                bootstrap = scheduledBootstrap;
                scheduledBootstrap = null;
            }
            cancel(bootstrap);
            if (statusCode == WebSocket.NORMAL_CLOSURE) {
                logger.info("WebSocket API connection closed normally.");
            } else {
                logger.warning("WebSocket API connection closed unexpectedly (status "
                        + statusCode + "): " + reason);
            }
            scheduleReconnect(token);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void onError(WebSocket socket, Throwable error) {
            handleConnectionFailure(token, socket, error,
                    "WebSocket API connection failed.");
        }

        private void requestNext(long token, WebSocket socket) {
            Throwable requestFailure = null;
            synchronized (lock) {
                if (!isCurrentSocket(token, socket)) {
                    return;
                }
                try {
                    socket.request(1);
                } catch (Throwable error) {
                    requestFailure = error;
                }
            }
            if (requestFailure != null) {
                handleConnectionFailure(token, socket, requestFailure,
                        "Failed to request the next WebSocket API message.");
            }
        }
    }
}
