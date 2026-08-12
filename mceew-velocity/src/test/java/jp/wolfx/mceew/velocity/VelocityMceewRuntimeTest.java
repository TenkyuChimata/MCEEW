package jp.wolfx.mceew.velocity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.http.WebSocket;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class VelocityMceewRuntimeTest {
    @Test
    void startCreatesOneManagerConnectionAndPreservesBootstrapPacing() {
        TestVelocityApi.RecordingScheduler platform = new TestVelocityApi.RecordingScheduler();
        VelocityDelayScheduler scheduler = new VelocityDelayScheduler(
                TestVelocityApi.proxyServer(platform), this);
        TestWebSocketSupport.RecordingConnector connector =
                new TestWebSocketSupport.RecordingConnector(true);
        VelocityMceewRuntime runtime = runtime(scheduler, connector);
        Object managerIdentity = runtime.webSocketManagerIdentity();
        Object processorIdentity = runtime.messageProcessor();

        runtime.start();
        runtime.start();

        assertTrue(runtime.isActive());
        assertEquals(1, connector.connectionCount());
        assertSame(managerIdentity, runtime.webSocketManagerIdentity());
        assertSame(processorIdentity, runtime.messageProcessor());
        assertEquals(List.of("query_jmaeqlist"),
                connector.attempt(0).socket().textMessages());
        assertEquals(1200L, platform.lastDelay());
        assertEquals(TimeUnit.MILLISECONDS, platform.lastDelayUnit());

        platform.runAll();

        assertEquals(List.of("query_jmaeqlist", "query_cenceqlist"),
                connector.attempt(0).socket().textMessages());
    }

    @Test
    void reconnectRemainsInsideTheSameRuntimeAndManager() {
        TestVelocityApi.RecordingScheduler platform = new TestVelocityApi.RecordingScheduler();
        VelocityDelayScheduler scheduler = new VelocityDelayScheduler(
                TestVelocityApi.proxyServer(platform), this);
        TestWebSocketSupport.RecordingConnector connector =
                new TestWebSocketSupport.RecordingConnector(true);
        VelocityMceewRuntime runtime = runtime(scheduler, connector);
        Object managerIdentity = runtime.webSocketManagerIdentity();
        runtime.start();

        connector.attempt(0).closeFromPeer(WebSocket.NORMAL_CLOSURE, "peer restart");
        platform.runAll();

        assertEquals(2, connector.connectionCount());
        assertSame(managerIdentity, runtime.webSocketManagerIdentity());
        assertTrue(runtime.isActive());
    }

    @Test
    void shutdownCancelsReconnectAndCannotCreateAnotherConnection() {
        TestVelocityApi.RecordingScheduler platform = new TestVelocityApi.RecordingScheduler();
        VelocityDelayScheduler scheduler = new VelocityDelayScheduler(
                TestVelocityApi.proxyServer(platform), this);
        TestWebSocketSupport.RecordingConnector connector =
                new TestWebSocketSupport.RecordingConnector(true);
        VelocityMceewRuntime runtime = runtime(scheduler, connector);
        runtime.start();
        connector.attempt(0).closeFromPeer(WebSocket.NORMAL_CLOSURE, "peer restart");

        runtime.close();
        runtime.close();
        platform.runAll();

        assertFalse(runtime.isActive());
        assertEquals(1, connector.connectionCount());
        assertEquals(0, runtime.coreLogHandlerCount());
    }

    @Test
    void staleConnectionCallbackCannotReviveClosedRuntime() {
        TestVelocityApi.RecordingScheduler platform = new TestVelocityApi.RecordingScheduler();
        VelocityDelayScheduler scheduler = new VelocityDelayScheduler(
                TestVelocityApi.proxyServer(platform), this);
        TestWebSocketSupport.RecordingConnector connector =
                new TestWebSocketSupport.RecordingConnector(false);
        VelocityMceewRuntime runtime = runtime(scheduler, connector);
        runtime.start();

        runtime.close();
        connector.attempt(0).open();
        platform.runAll();

        assertFalse(runtime.isActive());
        assertTrue(connector.attempt(0).socket().aborted());
        assertTrue(connector.attempt(0).socket().textMessages().isEmpty());
        assertEquals(1, connector.connectionCount());
    }

    @Test
    void websocketApplicationMessagesReachTheSingleProcessorAndCache() {
        TestVelocityApi.RecordingScheduler platform = new TestVelocityApi.RecordingScheduler();
        VelocityDelayScheduler scheduler = new VelocityDelayScheduler(
                TestVelocityApi.proxyServer(platform), this);
        TestWebSocketSupport.RecordingConnector connector =
                new TestWebSocketSupport.RecordingConnector(true);
        VelocityMceewRuntime runtime = runtime(scheduler, connector);
        runtime.start();

        connector.attempt(0).message(fixture("jma_eqlist"));
        connector.attempt(0).message(fixture("cenc_eqlist"));

        assertTrue(runtime.messageProcessor().hasJmaCacheValue());
        assertTrue(runtime.messageProcessor().hasCencCacheValue());
        assertEquals(3, connector.attempt(0).socket().requestCalls());
        assertEquals(1, connector.connectionCount());
    }

    @Test
    void malformedEnabledApplicationMessageIsIsolatedWithoutReconnect() {
        TestVelocityApi.RecordingScheduler platform = new TestVelocityApi.RecordingScheduler();
        VelocityDelayScheduler scheduler = new VelocityDelayScheduler(
                TestVelocityApi.proxyServer(platform), this);
        TestWebSocketSupport.RecordingConnector connector =
                new TestWebSocketSupport.RecordingConnector(true);
        VelocityMceewRuntime runtime = runtime(scheduler, connector);
        runtime.start();

        connector.attempt(0).message("{\"type\":\"jma_eew\"}");

        assertEquals(1, connector.connectionCount());
        assertEquals(2, connector.attempt(0).socket().requestCalls());
        assertTrue(runtime.isActive());
    }

    private VelocityMceewRuntime runtime(
            VelocityDelayScheduler scheduler,
            TestWebSocketSupport.RecordingConnector connector
    ) {
        return new VelocityMceewRuntime(
                enabledConfig(), scheduler, connector, TestVelocityApi.logger().proxy());
    }

    private static VelocityConfigSnapshot enabledConfig() {
        return new VelocityConfigSnapshot(1, true, true, true, true, true, true, true);
    }

    private static String fixture(String name) {
        Path root = Path.of(requiredSystemProperty("mceew.reactor.root"));
        Path fixture = root.resolve(
                "mceew-bukkit/src/test/resources/websocket/current-schema/" + name + ".json");
        try {
            return Files.readString(fixture, StandardCharsets.UTF_8);
        } catch (IOException error) {
            throw new IllegalStateException("Unable to read fixture: " + fixture, error);
        }
    }

    private static String requiredSystemProperty(String name) {
        String value = System.getProperty(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Required Maven test property is missing: " + name);
        }
        return value;
    }
}
