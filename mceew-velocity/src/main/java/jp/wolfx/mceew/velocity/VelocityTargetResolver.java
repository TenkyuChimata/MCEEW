package jp.wolfx.mceew.velocity;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import jp.wolfx.mceew.notification.NotificationSource;
import org.slf4j.Logger;

/** Resolves one immutable, UUID-deduplicated recipient snapshot at dispatch time. */
final class VelocityTargetResolver {
    static final class Recipient {
        private final Player player;
        private final String backendName;

        private Recipient(Player player, String backendName) {
            this.player = player;
            this.backendName = backendName;
        }

        Player player() {
            return player;
        }

        String backendName() {
            return backendName;
        }
    }

    private final ProxyServer proxyServer;
    private final VelocityTargetConfig config;
    private final Logger logger;
    private final Set<String> warnedUnknownServers = ConcurrentHashMap.newKeySet();

    VelocityTargetResolver(
            ProxyServer proxyServer,
            VelocityTargetConfig config,
            Logger logger
    ) {
        this.proxyServer = Objects.requireNonNull(proxyServer, "proxyServer");
        this.config = Objects.requireNonNull(config, "config");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    Collection<Recipient> resolve(NotificationSource source) {
        VelocityTargetConfig.TargetSpec target = config.targetFor(source);
        if (target.mode() == VelocityTargetConfig.Mode.NONE) {
            return ListHolder.EMPTY;
        }

        Collection<Player> connected = new ArrayList<>(proxyServer.getAllPlayers());
        Map<UUID, Recipient> recipients = new LinkedHashMap<>();
        Set<String> selectedServers = target.mode() == VelocityTargetConfig.Mode.SELECTED
                ? config.selectedServers(source)
                : Collections.emptySet();
        if (target.mode() == VelocityTargetConfig.Mode.SELECTED) {
            warnUnknownServers(selectedServers);
        }
        for (Player player : connected) {
            try {
                String backend = player.getCurrentServer()
                        .map(connection -> VelocityConfigLoader.normalizeName(
                                connection.getServerInfo().getName()))
                        .orElse(null);
                if (target.mode() == VelocityTargetConfig.Mode.ALL
                        || (backend != null && selectedServers.contains(backend))) {
                    recipients.putIfAbsent(
                            player.getUniqueId(), new Recipient(player, backend));
                }
            } catch (RuntimeException error) {
                logger.warn("MCEEW Velocity skipped a player whose backend state changed "
                        + "during target resolution.", error);
            }
        }
        return Collections.unmodifiableList(new ArrayList<>(recipients.values()));
    }

    private void warnUnknownServers(Set<String> selectedServers) {
        for (String server : selectedServers) {
            if (proxyServer.getServer(server).isEmpty() && warnedUnknownServers.add(server)) {
                logger.warn("MCEEW Velocity target references an unregistered backend server: {}", server);
            }
        }
    }

    private static final class ListHolder {
        private static final Collection<Recipient> EMPTY = Collections.emptyList();
    }
}
