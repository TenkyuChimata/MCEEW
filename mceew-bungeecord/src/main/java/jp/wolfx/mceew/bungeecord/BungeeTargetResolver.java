package jp.wolfx.mceew.bungeecord;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import jp.wolfx.mceew.notification.NotificationSource;

/** Resolves one immutable, UUID-deduplicated player snapshot at delivery time. */
final class BungeeTargetResolver {
    static final class Recipient {
        private final BungeeNotificationPlatform.Player player;
        private final String backendName;

        private Recipient(BungeeNotificationPlatform.Player player, String backendName) {
            this.player = player;
            this.backendName = backendName;
        }

        BungeeNotificationPlatform.Player player() {
            return player;
        }

        String backendName() {
            return backendName;
        }
    }

    private final BungeeNotificationPlatform platform;
    private final BungeeConfigSnapshot config;
    private final Logger logger;
    private final Set<String> warnedUnknownServers = ConcurrentHashMap.newKeySet();

    BungeeTargetResolver(
            BungeeNotificationPlatform platform,
            BungeeConfigSnapshot config,
            Logger logger
    ) {
        this.platform = Objects.requireNonNull(platform, "platform");
        this.config = Objects.requireNonNull(config, "config");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    Collection<Recipient> resolve(NotificationSource source) {
        BungeeConfigSnapshot.TargetSpec target = config.targetFor(source);
        if (target.mode() == BungeeConfigSnapshot.TargetMode.NONE) {
            return Collections.emptyList();
        }

        Set<String> selectedServers = target.mode() == BungeeConfigSnapshot.TargetMode.SELECTED
                ? config.selectedServers(source)
                : Collections.emptySet();
        if (target.mode() == BungeeConfigSnapshot.TargetMode.SELECTED) {
            warnUnknownServers(selectedServers);
        }

        Collection<BungeeNotificationPlatform.Player> connected =
                new ArrayList<>(platform.onlinePlayers());
        Map<UUID, Recipient> recipients = new LinkedHashMap<>();
        for (BungeeNotificationPlatform.Player player : connected) {
            try {
                String backend = player.currentBackendName();
                backend = backend == null ? null : BungeeConfigLoader.normalizeName(backend);
                if (target.mode() == BungeeConfigSnapshot.TargetMode.ALL
                        || (backend != null && selectedServers.contains(backend))) {
                    recipients.putIfAbsent(
                            player.uniqueId(), new Recipient(player, backend));
                }
            } catch (RuntimeException error) {
                logger.log(Level.WARNING,
                        "MCEEW BungeeCord skipped a player whose backend state changed "
                                + "during target resolution.",
                        error);
            }
        }
        return Collections.unmodifiableList(new ArrayList<>(recipients.values()));
    }

    private void warnUnknownServers(Set<String> selectedServers) {
        for (String server : selectedServers) {
            if (!platform.isBackendRegistered(server) && warnedUnknownServers.add(server)) {
                logger.warning(
                        "MCEEW BungeeCord target references an unregistered backend server: "
                                + server);
            }
        }
    }
}
