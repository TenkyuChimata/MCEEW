package jp.wolfx.mceew.bungeecord;

import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import jp.wolfx.mceew.notification.NotificationIntent;

/** Schedules and isolates Bungee player/console delivery for core notification intents. */
final class BungeeNotificationDispatcher implements AutoCloseable {
    private final BungeeNotificationPlatform platform;
    private final BungeeDelayScheduler scheduler;
    private final BungeeConfigSnapshot config;
    private final Logger logger;
    private final BungeeTargetResolver targetResolver;
    private final Set<PendingDelivery> pendingDeliveries = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean closed = new AtomicBoolean();

    BungeeNotificationDispatcher(
            BungeeNotificationPlatform platform,
            BungeeDelayScheduler scheduler,
            BungeeConfigSnapshot config,
            Logger logger
    ) {
        this.platform = Objects.requireNonNull(platform, "platform");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.config = Objects.requireNonNull(config, "config");
        this.logger = Objects.requireNonNull(logger, "logger");
        targetResolver = new BungeeTargetResolver(platform, config, logger);
    }

    void dispatch(BungeeNotificationEvent event) {
        Objects.requireNonNull(event, "event");
        schedule(() -> deliverIfActive(event));
    }

    boolean dispatchTest(BungeeNotificationEvent event, String warning) {
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(warning, "warning");
        return schedule(() -> {
            if (closed.get()) {
                return;
            }
            deliverIfActive(event);
            deliverTestWarning(warning);
        });
    }

    private boolean schedule(Runnable delivery) {
        if (closed.get()) {
            return false;
        }
        PendingDelivery pending = new PendingDelivery(delivery);
        pendingDeliveries.add(pending);
        if (closed.get()) {
            pending.cancel();
            return false;
        }
        try {
            pending.attach(scheduler.schedule(pending::run, 0L, TimeUnit.NANOSECONDS));
            return true;
        } catch (RuntimeException error) {
            pending.cancel();
            if (!closed.get()) {
                logger.log(Level.WARNING,
                        "MCEEW BungeeCord could not schedule notification delivery.", error);
            }
            return false;
        }
    }

    private void deliverIfActive(BungeeNotificationEvent event) {
        if (closed.get()) {
            return;
        }
        deliverConsole(event);

        Collection<BungeeTargetResolver.Recipient> recipients;
        try {
            recipients = targetResolver.resolve(event.source());
        } catch (RuntimeException error) {
            logger.log(Level.WARNING,
                    "MCEEW BungeeCord could not resolve notification targets for "
                            + BungeeNotificationSources.key(event.source()) + ".",
                    error);
            return;
        }
        for (BungeeTargetResolver.Recipient recipient : recipients) {
            if (closed.get()) {
                return;
            }
            try {
                deliverPlayer(event, recipient);
            } catch (RuntimeException error) {
                logger.log(Level.WARNING,
                        "MCEEW BungeeCord could not deliver "
                                + BungeeNotificationSources.key(event.source())
                                + " to player " + playerIdentifier(recipient.player()) + ".",
                        error);
            }
        }
    }

    private void deliverConsole(BungeeNotificationEvent event) {
        try {
            NotificationIntent intent = event.build(
                    channelPolicy(config.proxyChannels(event.source())));
            if (intent != null && intent.isConsoleDelivery() && intent.getChat() != null) {
                platform.sendConsole(intent.getChat().render());
            }
        } catch (RuntimeException error) {
            logger.log(Level.WARNING,
                    "MCEEW BungeeCord could not deliver "
                            + BungeeNotificationSources.key(event.source())
                            + " to the proxy console.",
                    error);
        }
    }

    private void deliverPlayer(
            BungeeNotificationEvent event,
            BungeeTargetResolver.Recipient recipient
    ) {
        BungeeNotificationPlatform.Player player = recipient.player();
        if (player.hasPermission(BungeePermissions.SUPPRESS_ALL)
                || player.hasPermission(BungeePermissions.suppressionFor(event.source()))) {
            return;
        }

        NotificationIntent intent = event.build(channelPolicy(
                config.playerChannels(event.source(), recipient.backendName())));
        if (intent == null) {
            return;
        }
        if (intent.getChat() != null) {
            safely(event, player, "chat", () -> player.sendChat(intent.getChat().render()));
        }
        if (intent.getTitle() != null) {
            safely(event, player, "title", () -> player.sendTitle(
                    intent.getTitle().renderTitle(),
                    intent.getTitle().renderSubtitle(),
                    intent.getTitle().getFadeInTicks(),
                    intent.getTitle().getStayTicks(),
                    intent.getTitle().getFadeOutTicks()));
        }
    }

    private void deliverTestWarning(String warning) {
        if (closed.get()) {
            return;
        }
        try {
            platform.sendConsole(warning);
        } catch (RuntimeException error) {
            logger.log(Level.WARNING,
                    "MCEEW BungeeCord could not deliver the test warning to the proxy console.",
                    error);
        }

        Collection<BungeeNotificationPlatform.Player> players;
        try {
            players = platform.onlinePlayers();
        } catch (RuntimeException error) {
            logger.log(Level.WARNING,
                    "MCEEW BungeeCord could not enumerate players for the test warning.", error);
            return;
        }
        for (BungeeNotificationPlatform.Player player : players) {
            if (closed.get()) {
                return;
            }
            try {
                player.sendChat(warning);
            } catch (RuntimeException error) {
                logger.log(Level.WARNING,
                        "MCEEW BungeeCord could not deliver the test warning to player "
                                + playerIdentifier(player) + ".",
                        error);
            }
        }
    }

    private void safely(
            BungeeNotificationEvent event,
            BungeeNotificationPlatform.Player player,
            String channel,
            Runnable delivery
    ) {
        try {
            delivery.run();
        } catch (RuntimeException error) {
            logger.log(Level.WARNING,
                    "MCEEW BungeeCord " + channel + " delivery failed for "
                            + BungeeNotificationSources.key(event.source())
                            + " to player " + playerIdentifier(player) + ".",
                    error);
        }
    }

    private static BungeeChannelPolicy channelPolicy(
            BungeeConfigSnapshot.ChannelPolicy policy
    ) {
        return new BungeeChannelPolicy(policy.broadcast(), policy.title());
    }

    private static String playerIdentifier(BungeeNotificationPlatform.Player player) {
        try {
            return String.valueOf(player.uniqueId());
        } catch (RuntimeException ignored) {
            return "<disconnected>";
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        for (PendingDelivery pending : pendingDeliveries.toArray(new PendingDelivery[0])) {
            pending.cancel();
        }
    }

    boolean isClosed() {
        return closed.get();
    }

    int pendingDeliveryCount() {
        return pendingDeliveries.size();
    }

    private final class PendingDelivery {
        private final Runnable delivery;
        private final AtomicBoolean complete = new AtomicBoolean();
        private volatile jp.wolfx.mceew.websocket.WebSocketConnectionManager.ScheduledAction action;

        private PendingDelivery(Runnable delivery) {
            this.delivery = delivery;
        }

        private void attach(
                jp.wolfx.mceew.websocket.WebSocketConnectionManager.ScheduledAction action
        ) {
            this.action = Objects.requireNonNull(action, "action");
            if (complete.get()) {
                action.cancel();
            }
        }

        private void run() {
            if (!complete.compareAndSet(false, true)) {
                return;
            }
            pendingDeliveries.remove(this);
            delivery.run();
        }

        private void cancel() {
            if (!complete.compareAndSet(false, true)) {
                return;
            }
            pendingDeliveries.remove(this);
            jp.wolfx.mceew.websocket.WebSocketConnectionManager.ScheduledAction scheduled = action;
            if (scheduled != null) {
                scheduled.cancel();
            }
        }
    }
}
