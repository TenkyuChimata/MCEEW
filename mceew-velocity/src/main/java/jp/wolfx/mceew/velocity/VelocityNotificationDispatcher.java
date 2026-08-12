package jp.wolfx.mceew.velocity;

import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import java.time.Duration;
import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import jp.wolfx.mceew.notification.NotificationIntent;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.slf4j.Logger;

/** Concrete Velocity delivery boundary for platform-neutral notification events. */
final class VelocityNotificationDispatcher implements AutoCloseable {
    private static final String ALL_PERMISSION = "mceew.notify.all";
    private static final long TICK_MILLIS = 50L;
    private static final LegacyComponentSerializer LEGACY =
            LegacyComponentSerializer.legacySection();

    private final ProxyServer proxyServer;
    private final Logger logger;
    private final VelocityDelayScheduler scheduler;
    private final VelocityNotificationConfig config;
    private final VelocityTargetResolver targetResolver;
    private final Set<String> warnedInvalidSoundKeys = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean closed = new AtomicBoolean();

    VelocityNotificationDispatcher(
            ProxyServer proxyServer,
            Logger logger,
            VelocityDelayScheduler scheduler,
            VelocityNotificationConfig config
    ) {
        this.proxyServer = Objects.requireNonNull(proxyServer, "proxyServer");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.config = Objects.requireNonNull(config, "config");
        targetResolver = new VelocityTargetResolver(proxyServer, config.targets(), logger);
    }

    void dispatch(VelocityNotificationEvent event) {
        Objects.requireNonNull(event, "event");
        if (closed.get()) {
            return;
        }
        try {
            scheduler.schedule(() -> deliverIfActive(event), 0L, TimeUnit.NANOSECONDS);
        } catch (IllegalStateException error) {
            if (!closed.get()) {
                throw error;
            }
        }
    }

    private void deliverIfActive(VelocityNotificationEvent event) {
        if (closed.get()) {
            return;
        }
        deliverConsole(event);

        Collection<VelocityTargetResolver.Recipient> recipients;
        try {
            recipients = targetResolver.resolve(event.source());
        } catch (RuntimeException error) {
            logger.error("MCEEW Velocity could not resolve notification targets for {}.",
                    VelocityNotificationSources.key(event.source()), error);
            return;
        }
        for (VelocityTargetResolver.Recipient recipient : recipients) {
            if (closed.get()) {
                return;
            }
            try {
                deliverPlayer(event, recipient);
            } catch (RuntimeException error) {
                logger.warn("MCEEW Velocity could not deliver {} to player {}.",
                        VelocityNotificationSources.key(event.source()),
                        recipient.player().getUniqueId(), error);
            }
        }
    }

    private void deliverConsole(VelocityNotificationEvent event) {
        try {
            NotificationIntent intent = event.build(config.proxyChannels(event.source()));
            if (intent != null && intent.isConsoleDelivery() && intent.getChat() != null) {
                proxyServer.getConsoleCommandSource().sendMessage(
                        component(intent.getChat().render()));
            }
        } catch (RuntimeException error) {
            logger.warn("MCEEW Velocity could not deliver {} to the proxy console.",
                    VelocityNotificationSources.key(event.source()), error);
        }
    }

    private void deliverPlayer(
            VelocityNotificationEvent event,
            VelocityTargetResolver.Recipient recipient
    ) {
        VelocityChannelPolicy channels = config.playerChannels(
                event.source(), recipient.backendName());
        switch (event.deliveryStyle()) {
            case JMA:
                deliverJma(event, recipient, channels);
                break;
            case REGIONAL:
                deliverRegional(event, recipient, channels);
                break;
            case EARTHQUAKE_LIST:
                deliverEarthquakeList(event, recipient, channels);
                break;
            default:
                throw new IllegalStateException(
                        "Unhandled Velocity delivery style: " + event.deliveryStyle());
        }
    }

    private void deliverJma(
            VelocityNotificationEvent event,
            VelocityTargetResolver.Recipient recipient,
            VelocityChannelPolicy channels
    ) {
        NotificationIntent intent = event.build(channels);
        Player player = recipient.player();
        if (intent.getChat() != null && canReceive(player, intent.getPermissionNode())) {
            safely(event, player, "chat", () -> sendChat(player, intent.getChat()));
        }
        if (intent.getTitle() != null && canReceive(player, intent.getPermissionNode())) {
            safely(event, player, "title", () -> sendTitle(player, intent.getTitle()));
        }
        if (intent.getSound() != null && canReceive(player, intent.getPermissionNode())) {
            safely(event, player, "sound", () -> playSound(recipient, intent.getSound()));
        }
    }

    private void deliverRegional(
            VelocityNotificationEvent event,
            VelocityTargetResolver.Recipient recipient,
            VelocityChannelPolicy channels
    ) {
        Player player = recipient.player();
        if (!canReceive(player, event.source().getPermissionNode())) {
            return;
        }
        NotificationIntent intent = event.build(channels);
        if (intent.getChat() != null) {
            safely(event, player, "chat", () -> sendChat(player, intent.getChat()));
        }
        if (intent.getTitle() != null) {
            safely(event, player, "title", () -> sendTitle(player, intent.getTitle()));
        }
        if (intent.getSound() != null) {
            safely(event, player, "sound", () -> playSound(recipient, intent.getSound()));
        }
    }

    private void deliverEarthquakeList(
            VelocityNotificationEvent event,
            VelocityTargetResolver.Recipient recipient,
            VelocityChannelPolicy channels
    ) {
        Player player = recipient.player();
        if (!canReceive(player, event.source().getPermissionNode())) {
            return;
        }
        NotificationIntent intent = event.build(channels);
        if (intent != null && intent.getChat() != null) {
            safely(event, player, "chat", () -> sendChat(player, intent.getChat()));
        }
    }

    private boolean canReceive(Player player, String sourcePermission) {
        return player.hasPermission(ALL_PERMISSION) && player.hasPermission(sourcePermission);
    }

    private static void sendChat(Player player, NotificationIntent.ChatNotice chat) {
        player.sendMessage(component(chat.render()));
    }

    private static void sendTitle(Player player, NotificationIntent.TitleNotice notice) {
        Title.Times times = Title.Times.times(
                ticks(notice.getFadeInTicks()),
                ticks(notice.getStayTicks()),
                ticks(notice.getFadeOutTicks()));
        player.showTitle(Title.title(
                component(notice.renderTitle()),
                component(notice.renderSubtitle()),
                times));
    }

    private void playSound(
            VelocityTargetResolver.Recipient recipient,
            NotificationIntent.SoundNotice notice
    ) {
        Player player = recipient.player();
        if (recipient.backendName() == null
                || player.getCurrentServer().isEmpty()
                || player.getProtocolVersion().getProtocol()
                < ProtocolVersion.MINECRAFT_1_19_3.getProtocol()) {
            return;
        }

        Key key;
        try {
            key = Key.key(notice.getKey());
        } catch (RuntimeException error) {
            warnInvalidSoundKey(notice.getKey());
            return;
        }
        Sound sound = Sound.sound(
                key,
                Sound.Source.MASTER,
                (float) notice.getVolume(),
                (float) notice.getPitch());
        player.playSound(sound, Sound.Emitter.self());
    }

    private void warnInvalidSoundKey(String soundKey) {
        String printable = String.valueOf(soundKey);
        if (warnedInvalidSoundKeys.add(printable)) {
            logger.warn("Unknown sound type: {}", printable);
        }
    }

    private void safely(
            VelocityNotificationEvent event,
            Player player,
            String channel,
            Runnable delivery
    ) {
        try {
            delivery.run();
        } catch (RuntimeException error) {
            logger.warn("MCEEW Velocity " + channel + " delivery failed for {} to player {}.",
                    VelocityNotificationSources.key(event.source()),
                    player.getUniqueId(),
                    error);
        }
    }

    private static Component component(String legacy) {
        return LEGACY.deserialize(legacy);
    }

    private static Duration ticks(int ticks) {
        return Duration.ofMillis(ticks * TICK_MILLIS);
    }

    @Override
    public void close() {
        closed.set(true);
    }

    boolean isClosed() {
        return closed.get();
    }
}
