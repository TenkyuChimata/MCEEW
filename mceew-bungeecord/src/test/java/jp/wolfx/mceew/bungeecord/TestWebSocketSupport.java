package jp.wolfx.mceew.bungeecord;

import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;
import jp.wolfx.mceew.websocket.WebSocketConnectionManager;

final class TestWebSocketSupport {
    private TestWebSocketSupport() {
    }

    static final class RecordingConnector implements WebSocketConnectionManager.Connector {
        private final boolean autoOpen;
        private final List<Attempt> attempts = new ArrayList<>();

        RecordingConnector(boolean autoOpen) {
            this.autoOpen = autoOpen;
        }

        @Override
        public CompletableFuture<WebSocket> connect(WebSocket.Listener listener) {
            Attempt attempt = new Attempt(listener, new RecordingWebSocket());
            attempts.add(attempt);
            if (autoOpen) {
                attempt.open();
            }
            return attempt.connection;
        }

        int connectionCount() {
            return attempts.size();
        }

        Attempt attempt(int index) {
            return attempts.get(index);
        }
    }

    static final class Attempt {
        private final WebSocket.Listener listener;
        private final RecordingWebSocket socket;
        private final CompletableFuture<WebSocket> connection = new CompletableFuture<>();
        private boolean opened;

        private Attempt(WebSocket.Listener listener, RecordingWebSocket socket) {
            this.listener = listener;
            this.socket = socket;
        }

        void open() {
            if (opened) {
                return;
            }
            opened = true;
            listener.onOpen(socket);
            connection.complete(socket);
        }

        void closeFromPeer(int statusCode, String reason) {
            CompletionStage<?> completion = listener.onClose(socket, statusCode, reason);
            completion.toCompletableFuture().join();
        }

        void message(String payload) {
            listener.onText(socket, payload, true).toCompletableFuture().join();
        }

        void fragment(String payload, int split) {
            listener.onText(socket, payload.substring(0, split), false)
                    .toCompletableFuture().join();
            listener.onText(socket, payload.substring(split), true)
                    .toCompletableFuture().join();
        }

        RecordingWebSocket socket() {
            return socket;
        }
    }

    static final class RecordingWebSocket implements WebSocket {
        private final List<String> textMessages = new ArrayList<>();
        private final AtomicInteger closeCalls = new AtomicInteger();
        private final AtomicInteger requestCalls = new AtomicInteger();
        private volatile boolean aborted;

        @Override
        public CompletableFuture<WebSocket> sendText(CharSequence data, boolean last) {
            textMessages.add(data.toString());
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
            requestCalls.incrementAndGet();
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

        List<String> textMessages() {
            return Collections.unmodifiableList(textMessages);
        }

        int closeCalls() {
            return closeCalls.get();
        }

        int requestCalls() {
            return requestCalls.get();
        }

        boolean aborted() {
            return aborted;
        }
    }
}
