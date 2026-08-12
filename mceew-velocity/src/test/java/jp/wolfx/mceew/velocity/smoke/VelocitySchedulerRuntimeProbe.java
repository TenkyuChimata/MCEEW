package jp.wolfx.mceew.velocity.smoke;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.proxy.ProxyServer;
import java.util.concurrent.TimeUnit;
import jp.wolfx.mceew.velocity.VelocityDelayScheduler;
import org.slf4j.Logger;

/** Test-fixture plugin used only by the cross-version runtime smoke job. */
public final class VelocitySchedulerRuntimeProbe {
    private final Logger logger;
    private final VelocityDelayScheduler scheduler;

    @Inject
    public VelocitySchedulerRuntimeProbe(ProxyServer proxyServer, Logger logger) {
        this.logger = logger;
        this.scheduler = new VelocityDelayScheduler(proxyServer, this);
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        scheduler.schedule(
                () -> logger.info("MCEEW Velocity scheduler smoke task executed."),
                50L,
                TimeUnit.MILLISECONDS);
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        scheduler.close();
        logger.info("MCEEW Velocity scheduler smoke probe shut down.");
    }
}
