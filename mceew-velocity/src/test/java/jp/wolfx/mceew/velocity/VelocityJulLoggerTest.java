package jp.wolfx.mceew.velocity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.logging.Level;
import org.junit.jupiter.api.Test;

class VelocityJulLoggerTest {
    @Test
    void mapsJulLevelsAndPreservesThrowable() {
        TestVelocityApi.CapturingLogger platform = TestVelocityApi.logger();
        VelocityJulLogger bridge = new VelocityJulLogger(platform.proxy());
        IllegalStateException failure = new IllegalStateException("broken connection");

        bridge.logger().log(Level.SEVERE, "severe", failure);
        bridge.logger().warning("warning");
        bridge.logger().info("information");
        bridge.logger().fine("fine detail");

        assertEquals(1, platform.errorCountContaining("severe"));
        assertEquals(1, platform.warningCountContaining("warning"));
        assertEquals(1, platform.infoCountContaining("information"));
        assertEquals(1, platform.debugCountContaining("fine detail"));
        assertTrue(platform.capturedThrowable(failure));
    }

    @Test
    void closeRemovesOnlyTheOwnedHandlerAndIsIdempotent() {
        VelocityJulLogger bridge = new VelocityJulLogger(TestVelocityApi.logger().proxy());
        assertEquals(1, bridge.handlerCount());

        bridge.close();
        bridge.close();

        assertEquals(0, bridge.handlerCount());
    }
}
