package jp.wolfx.mceew.bungeecord;

import java.util.Collection;
import java.util.UUID;

/** Narrow, testable boundary over the public Bungee player and console delivery API. */
interface BungeeNotificationPlatform {
    Collection<Player> onlinePlayers();

    boolean isBackendRegistered(String backendName);

    void sendConsole(String legacyMessage);

    interface Player {
        UUID uniqueId();

        String currentBackendName();

        boolean hasPermission(String permission);

        void sendChat(String legacyMessage);

        void sendTitle(
                String legacyTitle,
                String legacySubtitle,
                int fadeInTicks,
                int stayTicks,
                int fadeOutTicks);
    }
}
