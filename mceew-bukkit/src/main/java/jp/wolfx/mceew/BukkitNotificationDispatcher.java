package jp.wolfx.mceew;

import jp.wolfx.mceew.notification.NotificationIntent;
import jp.wolfx.mceew.notification.NotificationSource;
import jp.wolfx.mceew.scheduler.PlatformScheduler;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Delivers platform-neutral notification intents through the Bukkit/Folia APIs.
 */
final class BukkitNotificationDispatcher {
    private static final Pattern SOUND_KEY_PATTERN = Pattern.compile(
            "(?:[a-z0-9._-]+:)?[a-z0-9/._-]+"
    );

    private final PlatformScheduler platformScheduler;
    private final Logger logger;
    private final Consumer<String> consoleSender;

    BukkitNotificationDispatcher(PlatformScheduler platformScheduler, Logger logger) {
        this(platformScheduler, logger,
                message -> Bukkit.getConsoleSender().sendMessage(message));
    }

    // TEST SEAM: substitutes only the final console write, not scheduling or rendering.
    BukkitNotificationDispatcher(
            PlatformScheduler platformScheduler,
            Logger logger,
            Consumer<String> consoleSender
    ) {
        this.platformScheduler = platformScheduler;
        this.logger = logger;
        this.consoleSender = consoleSender;
    }

    void deliverJma(Supplier<NotificationIntent> intentSupplier) {
        deliverConsole(intentSupplier.get());
        platformScheduler.forEachPlayer(player -> {
            NotificationIntent intent = intentSupplier.get();
            if (intent.getChat() != null
                    && canReceive(player, intent.getPermissionNode())) {
                player.sendMessage(intent.getChat().render());
            }
            if (intent.getTitle() != null
                    && canReceive(player, intent.getPermissionNode())) {
                sendTitle(player, intent.getTitle());
            }
            if (intent.getSound() != null
                    && canReceive(player, intent.getPermissionNode())) {
                playSound(player, intent.getSound());
            }
        });
    }

    void deliverRegional(
            NotificationSource source,
            Supplier<NotificationIntent> intentSupplier
    ) {
        deliverConsole(intentSupplier.get());
        platformScheduler.forEachPlayer(player -> {
            if (canReceive(player, source.getPermissionNode())) {
                NotificationIntent intent = intentSupplier.get();
                if (intent.getChat() != null) {
                    player.sendMessage(intent.getChat().render());
                }
                if (intent.getTitle() != null) {
                    sendTitle(player, intent.getTitle());
                }
                if (intent.getSound() != null) {
                    playSound(player, intent.getSound());
                }
            }
        });
    }

    void deliverEarthquakeList(NotificationIntent intent) {
        deliverConsole(intent);
        platformScheduler.forEachPlayer(player -> {
            if (canReceive(player, intent.getPermissionNode())) {
                player.sendMessage(intent.getChat().render());
            }
        });
    }

    void deliverTestWarning(String message) {
        sendConsoleMessage(message);
        platformScheduler.forEachPlayer(player -> player.sendMessage(message));
    }

    private boolean canReceive(Player player, String node) {
        return player.hasPermission("mceew.notify.all") && player.hasPermission(node);
    }

    private void deliverConsole(NotificationIntent intent) {
        if (intent.isConsoleDelivery()) {
            sendConsoleMessage(intent.getChat().render());
        }
    }

    private void sendConsoleMessage(String message) {
        platformScheduler.runGlobal(() -> consoleSender.accept(message));
    }

    private void sendTitle(Player player, NotificationIntent.TitleNotice title) {
        player.sendTitle(
                title.renderTitle(),
                title.renderSubtitle(),
                title.getFadeInTicks(),
                title.getStayTicks(),
                title.getFadeOutTicks()
        );
    }

    private void playSound(Player player, NotificationIntent.SoundNotice sound) {
        String soundKey = sound.getKey();
        if (soundKey == null || !SOUND_KEY_PATTERN.matcher(soundKey).matches()) {
            logger.warning("Unknown sound type: " + soundKey);
            return;
        }
        try {
            player.playSound(
                    player.getLocation(),
                    soundKey,
                    (float) sound.getVolume(),
                    (float) sound.getPitch()
            );
        } catch (IllegalArgumentException exception) {
            logger.warning("Unknown sound type: " + soundKey);
        }
    }
}
