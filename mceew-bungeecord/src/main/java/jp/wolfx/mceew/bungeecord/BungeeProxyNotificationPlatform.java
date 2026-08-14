package jp.wolfx.mceew.bungeecord;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.connection.Server;
import net.md_5.bungee.api.chat.TextComponent;

/** Public-Bungee-API implementation of the notification delivery boundary. */
final class BungeeProxyNotificationPlatform implements BungeeNotificationPlatform {
    private final ProxyServer proxyServer;

    BungeeProxyNotificationPlatform(ProxyServer proxyServer) {
        this.proxyServer = Objects.requireNonNull(proxyServer, "proxyServer");
    }

    @Override
    public Collection<Player> onlinePlayers() {
        List<Player> snapshot = new ArrayList<>();
        for (ProxiedPlayer player : new ArrayList<>(proxyServer.getPlayers())) {
            snapshot.add(new PlayerAdapter(player));
        }
        return List.copyOf(snapshot);
    }

    @Override
    public boolean isBackendRegistered(String backendName) {
        return proxyServer.getServerInfo(backendName) != null;
    }

    @Override
    public void sendConsole(String legacyMessage) {
        proxyServer.getConsole().sendMessage(TextComponent.fromLegacy(legacyMessage));
    }

    private final class PlayerAdapter implements Player {
        private final ProxiedPlayer player;

        private PlayerAdapter(ProxiedPlayer player) {
            this.player = Objects.requireNonNull(player, "player");
        }

        @Override
        public UUID uniqueId() {
            return player.getUniqueId();
        }

        @Override
        public String currentBackendName() {
            Server server = player.getServer();
            return server == null || server.getInfo() == null ? null : server.getInfo().getName();
        }

        @Override
        public boolean hasPermission(String permission) {
            return player.hasPermission(permission);
        }

        @Override
        public void sendChat(String legacyMessage) {
            player.sendMessage(TextComponent.fromLegacy(legacyMessage));
        }

        @Override
        public void sendTitle(
                String legacyTitle,
                String legacySubtitle,
                int fadeInTicks,
                int stayTicks,
                int fadeOutTicks
        ) {
            proxyServer.createTitle()
                    .title(TextComponent.fromLegacy(legacyTitle))
                    .subTitle(TextComponent.fromLegacy(legacySubtitle))
                    .fadeIn(fadeInTicks)
                    .stay(stayTicks)
                    .fadeOut(fadeOutTicks)
                    .send(player);
        }
    }
}
