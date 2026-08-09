package jp.wolfx.mceew.websocket;

import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLSession;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.net.http.WebSocketHandshakeException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebSocketConnectionManagerTest {
    @Test
    void initialQueriesWaitForThePreviousSendToComplete() {
        FakeReconnectScheduler scheduler = new FakeReconnectScheduler();
        FakeConnector connector = new FakeConnector(true);
        WebSocketConnectionManager manager = manager(connector, scheduler);

        manager.start();

        FakeWebSocket socket = connector.attempts.get(0).socket;
        assertEquals(List.of(WebSocketConnectionManager.JMA_QUERY), socket.textMessages);

        socket.firstTextSend.complete(socket);

        assertEquals(List.of(
                WebSocketConnectionManager.JMA_QUERY,
                WebSocketConnectionManager.CENC_QUERY
        ), socket.textMessages);
        assertEquals(0, scheduler.pendingCount());
    }

    @Test
    void failedQueryFutureIsLoggedAndReconnectsOnce() {
        FakeReconnectScheduler scheduler = new FakeReconnectScheduler();
        FakeConnector connector = new FakeConnector(true);
        RecordingHandler logs = new RecordingHandler();
        WebSocketConnectionManager manager = manager(connector, scheduler, logs);
        manager.start();

        connector.attempts.get(0).socket.firstTextSend.completeExceptionally(
                new IllegalStateException("send failed"));

        assertTrue(logs.contains("Failed to send initial WebSocket earthquake-list queries."));
        assertEquals(1, scheduler.pendingCount());
    }

    @Test
    void reloadClosesTheOldConnectionAndCreatesExactlyOneReplacement() {
        FakeReconnectScheduler scheduler = new FakeReconnectScheduler();
        FakeConnector connector = new FakeConnector(false);
        WebSocketConnectionManager manager = manager(connector, scheduler);
        manager.start();

        FakeWebSocket oldSocket = connector.attempts.get(0).socket;
        manager.restart();

        assertEquals(1, oldSocket.closeCalls.get());
        assertTrue(oldSocket.aborted);
        assertEquals(2, connector.attempts.size());
        assertEquals(0, scheduler.pendingCount());
    }

    @Test
    void intentionalCloseDoesNotScheduleReconnect() {
        FakeReconnectScheduler scheduler = new FakeReconnectScheduler();
        FakeConnector connector = new FakeConnector(false);
        WebSocketConnectionManager manager = manager(connector, scheduler);
        manager.start();

        Attempt old = connector.attempts.get(0);
        manager.restart();
        old.listener.onClose(old.socket, WebSocket.NORMAL_CLOSURE, "reload");

        assertEquals(2, connector.attempts.size());
        assertEquals(0, scheduler.pendingCount());
    }

    @Test
    void staleCloseAndErrorCannotAffectTheReplacementConnection() {
        FakeReconnectScheduler scheduler = new FakeReconnectScheduler();
        FakeConnector connector = new FakeConnector(false);
        WebSocketConnectionManager manager = manager(connector, scheduler);
        manager.start();

        Attempt old = connector.attempts.get(0);
        manager.restart();
        old.listener.onClose(old.socket, 1006, "late close");
        old.listener.onError(old.socket, new IllegalStateException("late error"));

        assertEquals(2, connector.attempts.size());
        assertEquals(0, scheduler.pendingCount());
        assertFalse(connector.attempts.get(1).socket.aborted);
    }

    @Test
    void abnormalCloseSchedulesOnlyOneReconnect() {
        FakeReconnectScheduler scheduler = new FakeReconnectScheduler();
        FakeConnector connector = new FakeConnector(false);
        WebSocketConnectionManager manager = manager(connector, scheduler);
        manager.start();

        Attempt attempt = connector.attempts.get(0);
        attempt.listener.onClose(attempt.socket, 1006, "network failure");
        attempt.listener.onError(attempt.socket, new IllegalStateException("duplicate callback"));

        assertEquals(1, scheduler.pendingCount());
        scheduler.runNext();
        assertEquals(2, connector.attempts.size());
        assertEquals(0, scheduler.pendingCount());
    }

    @Test
    void disableCancelsReconnectAndRejectsLaterCallbacks() {
        FakeReconnectScheduler scheduler = new FakeReconnectScheduler();
        FakeConnector connector = new FakeConnector(false);
        WebSocketConnectionManager manager = manager(connector, scheduler);
        manager.start();

        Attempt attempt = connector.attempts.get(0);
        attempt.listener.onError(attempt.socket, new IllegalStateException("network failure"));
        assertEquals(1, scheduler.pendingCount());

        manager.stop();
        scheduler.runAllIncludingCancelled();
        attempt.listener.onClose(attempt.socket, 1006, "late close");
        attempt.listener.onError(attempt.socket, new IllegalStateException("late error"));

        assertEquals(1, connector.attempts.size());
        assertEquals(0, scheduler.pendingCount());
    }

    @Test
    void disableClosesTheCurrentConnectionWithoutReconnect() {
        FakeReconnectScheduler scheduler = new FakeReconnectScheduler();
        FakeConnector connector = new FakeConnector(false);
        WebSocketConnectionManager manager = manager(connector, scheduler);
        manager.start();

        FakeWebSocket socket = connector.attempts.get(0).socket;
        manager.stop();

        assertEquals(1, socket.closeCalls.get());
        assertTrue(socket.aborted);
        assertEquals(0, scheduler.pendingCount());
        assertEquals(1, connector.attempts.size());
    }

    @Test
    void handshakeFailureLogIncludesStatusButNotHeaders() {
        FakeReconnectScheduler scheduler = new FakeReconnectScheduler();
        RecordingHandler logs = new RecordingHandler();
        HttpResponse<Void> response = handshakeResponse(429);
        WebSocketHandshakeException handshake = new WebSocketHandshakeException(response);
        WebSocketConnectionManager manager = manager(listener -> {
            CompletableFuture<WebSocket> failed = new CompletableFuture<>();
            failed.completeExceptionally(handshake);
            return failed;
        }, scheduler, logs);

        manager.start();

        assertTrue(logs.contains("WebSocket handshake HTTP status: 429"));
        assertFalse(logs.contains("secret-token"));
        assertEquals(1, scheduler.pendingCount());
    }

    private WebSocketConnectionManager manager(
            FakeConnector connector, FakeReconnectScheduler scheduler) {
        return manager(connector, scheduler, null);
    }

    private WebSocketConnectionManager manager(
            WebSocketConnectionManager.Connector connector,
            FakeReconnectScheduler scheduler,
            RecordingHandler handler) {
        Logger logger = Logger.getLogger(getClass().getName() + System.nanoTime());
        logger.setUseParentHandlers(false);
        logger.setLevel(handler == null ? Level.OFF : Level.ALL);
        if (handler != null) {
            logger.addHandler(handler);
        }
        return new WebSocketConnectionManager(
                connector, scheduler, ignored -> {
                }, logger, 5, TimeUnit.SECONDS);
    }

    private HttpResponse<Void> handshakeResponse(int statusCode) {
        URI uri = URI.create("wss://ws-api.wolfx.jp/all_eew");
        return new HttpResponse<Void>() {
            @Override
            public int statusCode() {
                return statusCode;
            }

            @Override
            public HttpRequest request() {
                return HttpRequest.newBuilder(URI.create("https://ws-api.wolfx.jp/all_eew")).build();
            }

            @Override
            public Optional<HttpResponse<Void>> previousResponse() {
                return Optional.empty();
            }

            @Override
            public HttpHeaders headers() {
                return HttpHeaders.of(
                        Map.of("Authorization", List.of("secret-token")), (name, value) -> true);
            }

            @Override
            public Void body() {
                return null;
            }

            @Override
            public Optional<SSLSession> sslSession() {
                return Optional.empty();
            }

            @Override
            public URI uri() {
                return uri;
            }

            @Override
            public HttpClient.Version version() {
                return HttpClient.Version.HTTP_1_1;
            }
        };
    }

    private static final class FakeConnector implements WebSocketConnectionManager.Connector {
        private final boolean holdFirstTextSend;
        private final List<Attempt> attempts = new ArrayList<>();

        private FakeConnector(boolean holdFirstTextSend) {
            this.holdFirstTextSend = holdFirstTextSend;
        }

        @Override
        public CompletableFuture<WebSocket> connect(WebSocket.Listener listener) {
            FakeWebSocket socket = new FakeWebSocket(holdFirstTextSend && attempts.isEmpty());
            attempts.add(new Attempt(listener, socket));
            listener.onOpen(socket);
            return CompletableFuture.completedFuture(socket);
        }
    }

    private static final class Attempt {
        private final WebSocket.Listener listener;
        private final FakeWebSocket socket;

        private Attempt(WebSocket.Listener listener, FakeWebSocket socket) {
            this.listener = listener;
            this.socket = socket;
        }
    }

    private static final class FakeReconnectScheduler
            implements WebSocketConnectionManager.ReconnectScheduler {
        private final List<FakeScheduledAction> tasks = new ArrayList<>();

        @Override
        public WebSocketConnectionManager.ScheduledAction schedule(
                Runnable task, long delay, TimeUnit unit) {
            FakeScheduledAction action = new FakeScheduledAction(task);
            tasks.add(action);
            return action;
        }

        private int pendingCount() {
            int count = 0;
            for (FakeScheduledAction task : tasks) {
                if (!task.cancelled && !task.executed) {
                    count++;
                }
            }
            return count;
        }

        private void runNext() {
            for (FakeScheduledAction task : tasks) {
                if (!task.cancelled && !task.executed) {
                    task.executed = true;
                    task.runnable.run();
                    return;
                }
            }
        }

        private void runAllIncludingCancelled() {
            for (FakeScheduledAction task : new ArrayList<>(tasks)) {
                if (!task.executed) {
                    task.executed = true;
                    task.runnable.run();
                }
            }
        }
    }

    private static final class FakeScheduledAction
            implements WebSocketConnectionManager.ScheduledAction {
        private final Runnable runnable;
        private boolean cancelled;
        private boolean executed;

        private FakeScheduledAction(Runnable runnable) {
            this.runnable = runnable;
        }

        @Override
        public void cancel() {
            cancelled = true;
        }
    }

    private static final class FakeWebSocket implements WebSocket {
        private final List<String> textMessages = new ArrayList<>();
        private final CompletableFuture<WebSocket> firstTextSend = new CompletableFuture<>();
        private final AtomicInteger closeCalls = new AtomicInteger();
        private final boolean holdFirstTextSend;
        private boolean aborted;

        private FakeWebSocket(boolean holdFirstTextSend) {
            this.holdFirstTextSend = holdFirstTextSend;
        }

        @Override
        public CompletableFuture<WebSocket> sendText(CharSequence data, boolean last) {
            textMessages.add(data.toString());
            if (holdFirstTextSend && textMessages.size() == 1) {
                return firstTextSend;
            }
            return CompletableFuture.completedFuture(this);
        }

        @Override
        public CompletableFuture<WebSocket> sendBinary(ByteBuffer data, boolean last) {
            return CompletableFuture.completedFuture(this);
        }

        @Override
        public CompletableFuture<WebSocket> sendPing(ByteBuffer message) {
            return CompletableFuture.completedFuture(this);
        }

        @Override
        public CompletableFuture<WebSocket> sendPong(ByteBuffer message) {
            return CompletableFuture.completedFuture(this);
        }

        @Override
        public CompletableFuture<WebSocket> sendClose(int statusCode, String reason) {
            closeCalls.incrementAndGet();
            return CompletableFuture.completedFuture(this);
        }

        @Override
        public void request(long n) {
        }

        @Override
        public String getSubprotocol() {
            return "";
        }

        @Override
        public boolean isOutputClosed() {
            return aborted || closeCalls.get() > 0;
        }

        @Override
        public boolean isInputClosed() {
            return aborted;
        }

        @Override
        public void abort() {
            aborted = true;
        }
    }

    private static final class RecordingHandler extends Handler {
        private final List<String> messages = new ArrayList<>();

        @Override
        public void publish(LogRecord record) {
            messages.add(record.getMessage());
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }

        private boolean contains(String text) {
            for (String message : messages) {
                if (message.contains(text)) {
                    return true;
                }
            }
            return false;
        }
    }
}
