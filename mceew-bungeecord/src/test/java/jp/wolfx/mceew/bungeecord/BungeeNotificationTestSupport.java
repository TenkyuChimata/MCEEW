package jp.wolfx.mceew.bungeecord;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;
import jp.wolfx.mceew.notification.NotificationIntentFactory;
import jp.wolfx.mceew.notification.NotificationProfile;
import jp.wolfx.mceew.notification.NotificationSource;

final class BungeeNotificationTestSupport {
    private BungeeNotificationTestSupport() {
    }

    static Builder config() {
        return new Builder();
    }

    static BungeeNotificationEvent event(NotificationSource source) {
        NotificationProfile profile = new NotificationProfile(
                "chat " + source + " %region%",
                "title " + source,
                "subtitle %region%",
                null,
                0.0,
                0.0);
        return new BungeeNotificationEvent() {
            @Override
            public NotificationSource source() {
                return source;
            }

            @Override
            public jp.wolfx.mceew.notification.NotificationIntent build(
                    BungeeChannelPolicy channels
            ) {
                if (BungeeNotificationSources.isEarthquakeList(source)) {
                    return NotificationIntentFactory.earthquakeList(
                            source, true, channels.broadcast(), () -> "eqlist " + source)
                            .orElse(null);
                }
                return NotificationIntentFactory.regional(
                        source, "report", "origin", "1", "30", "104", "region", "5",
                        "10km", "6", channels.broadcast(), channels.title(), false, profile);
            }
        };
    }

    static Logger logger(String name) {
        Logger logger = Logger.getLogger("BungeeNotificationTest." + name);
        logger.setUseParentHandlers(false);
        return logger;
    }

    static void runAll(BungeeDelaySchedulerTest.FakeBackend backend) {
        for (int index = 0; index < backend.tasks.size(); index++) {
            backend.run(index);
        }
    }

    static final class Builder {
        private BungeeConfigSnapshot.TargetSpec defaultTarget = target(
                BungeeConfigSnapshot.TargetMode.ALL, Set.of(), Set.of());
        private final Map<NotificationSource, BungeeConfigSnapshot.TargetSpec> sourceTargets =
                new EnumMap<>(NotificationSource.class);
        private final Map<String, Set<String>> groups = new LinkedHashMap<>();
        private final Map<String, BungeeConfigSnapshot.ServerSettings> servers =
                new LinkedHashMap<>();
        private BungeeConfigSnapshot.ChannelPolicy defaults =
                new BungeeConfigSnapshot.ChannelPolicy(true, true);
        private final Map<NotificationSource, BungeeConfigSnapshot.ChannelOverrides>
                sourceChannels = new EnumMap<>(NotificationSource.class);

        Builder defaults(boolean broadcast, boolean title) {
            defaults = new BungeeConfigSnapshot.ChannelPolicy(broadcast, title);
            return this;
        }

        Builder sourceChannels(NotificationSource source, Boolean broadcast, Boolean title) {
            sourceChannels.put(source,
                    new BungeeConfigSnapshot.ChannelOverrides(broadcast, title));
            return this;
        }

        Builder defaultTarget(
                BungeeConfigSnapshot.TargetMode mode,
                Set<String> serverNames,
                Set<String> groupNames
        ) {
            defaultTarget = target(mode, serverNames, groupNames);
            return this;
        }

        Builder sourceTarget(
                NotificationSource source,
                BungeeConfigSnapshot.TargetMode mode,
                Set<String> serverNames,
                Set<String> groupNames
        ) {
            sourceTargets.put(source, target(mode, serverNames, groupNames));
            return this;
        }

        Builder group(String name, String... backendNames) {
            groups.put(normalize(name), normalizedSet(Set.of(backendNames)));
            return this;
        }

        Builder server(
                String name,
                Boolean broadcast,
                Boolean title,
                NotificationSource source,
                Boolean sourceBroadcast,
                Boolean sourceTitle
        ) {
            Map<NotificationSource, BungeeConfigSnapshot.ChannelOverrides> sourceOverrides =
                    new EnumMap<>(NotificationSource.class);
            if (source != null) {
                sourceOverrides.put(source, new BungeeConfigSnapshot.ChannelOverrides(
                        sourceBroadcast, sourceTitle));
            }
            servers.put(normalize(name), new BungeeConfigSnapshot.ServerSettings(
                    new BungeeConfigSnapshot.ChannelOverrides(broadcast, title),
                    sourceOverrides));
            return this;
        }

        BungeeConfigSnapshot build() {
            Map<NotificationSource, BungeeConfigSnapshot.SourceSettings> settings =
                    new EnumMap<>(NotificationSource.class);
            for (NotificationSource source : BungeePermissions.sourceSuppressions().keySet()) {
                boolean eqlist = BungeeNotificationSources.isEarthquakeList(source);
                settings.put(source, new BungeeConfigSnapshot.SourceSettings(
                        "message " + source + " %region%",
                        eqlist ? null : "title " + source,
                        eqlist ? null : "subtitle %region%",
                        sourceChannels.getOrDefault(
                                source, new BungeeConfigSnapshot.ChannelOverrides(null, null))));
            }
            return new BungeeConfigSnapshot(
                    1,
                    true,
                    new BungeeConfigSnapshot.SourceGates(
                            true, true, true, true, true, true),
                    "yyyy/MM/dd HH:mm:ss",
                    defaults,
                    settings,
                    defaultTarget,
                    sourceTargets,
                    groups,
                    servers);
        }

        private static BungeeConfigSnapshot.TargetSpec target(
                BungeeConfigSnapshot.TargetMode mode,
                Set<String> serverNames,
                Set<String> groupNames
        ) {
            return new BungeeConfigSnapshot.TargetSpec(
                    mode, normalizedSet(serverNames), normalizedSet(groupNames));
        }

        private static Set<String> normalizedSet(Set<String> values) {
            Set<String> result = new LinkedHashSet<>();
            for (String value : values) {
                result.add(normalize(value));
            }
            return result;
        }

        private static String normalize(String value) {
            return BungeeConfigLoader.normalizeName(value);
        }
    }

    static final class FakePlatform implements BungeeNotificationPlatform {
        private final List<Player> players = new ArrayList<>();
        private final Set<String> registeredBackends = new LinkedHashSet<>();
        private final List<String> consoleMessages = new ArrayList<>();
        private boolean failConsole;

        FakePlayer addPlayer(String name, String backend) {
            FakePlayer player = new FakePlayer(name, backend);
            players.add(player);
            if (backend != null) {
                registeredBackends.add(BungeeConfigLoader.normalizeName(backend));
            }
            return player;
        }

        void duplicate(Player player) {
            players.add(player);
        }

        void registerBackend(String backend) {
            registeredBackends.add(BungeeConfigLoader.normalizeName(backend));
        }

        List<String> consoleMessages() {
            return consoleMessages;
        }

        void failConsole(boolean value) {
            failConsole = value;
        }

        @Override
        public Collection<Player> onlinePlayers() {
            return List.copyOf(players);
        }

        @Override
        public boolean isBackendRegistered(String backendName) {
            return registeredBackends.contains(BungeeConfigLoader.normalizeName(backendName));
        }

        @Override
        public void sendConsole(String legacyMessage) {
            if (failConsole) {
                throw new IllegalStateException("console unavailable");
            }
            consoleMessages.add(legacyMessage);
        }
    }

    static final class FakePlayer implements BungeeNotificationPlatform.Player {
        private final UUID uniqueId;
        private final Map<String, Boolean> permissions = new LinkedHashMap<>();
        private final List<String> permissionQueries = new ArrayList<>();
        private final List<String> chats = new ArrayList<>();
        private final List<Title> titles = new ArrayList<>();
        private String backend;
        private boolean failBackend;
        private boolean failPermission;
        private boolean failChat;
        private boolean failTitle;

        FakePlayer(String name, String backend) {
            uniqueId = UUID.nameUUIDFromBytes(name.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            this.backend = backend;
        }

        void backend(String value) {
            backend = value;
        }

        void permission(String node, boolean value) {
            permissions.put(node, value);
        }

        void clearPermission(String node) {
            permissions.remove(node);
        }

        void failBackend(boolean value) {
            failBackend = value;
        }

        void failPermission(boolean value) {
            failPermission = value;
        }

        void failChat(boolean value) {
            failChat = value;
        }

        void failTitle(boolean value) {
            failTitle = value;
        }

        List<String> permissionQueries() {
            return Collections.unmodifiableList(permissionQueries);
        }

        List<String> chats() {
            return Collections.unmodifiableList(chats);
        }

        List<Title> titles() {
            return Collections.unmodifiableList(titles);
        }

        @Override
        public UUID uniqueId() {
            return uniqueId;
        }

        @Override
        public String currentBackendName() {
            if (failBackend) {
                throw new IllegalStateException("player switched servers");
            }
            return backend;
        }

        @Override
        public boolean hasPermission(String permission) {
            permissionQueries.add(permission);
            if (failPermission) {
                throw new IllegalStateException("permission provider unavailable");
            }
            return permissions.getOrDefault(permission, false);
        }

        @Override
        public void sendChat(String legacyMessage) {
            if (failChat) {
                throw new IllegalStateException("player disconnected during chat");
            }
            chats.add(legacyMessage);
        }

        @Override
        public void sendTitle(
                String legacyTitle,
                String legacySubtitle,
                int fadeInTicks,
                int stayTicks,
                int fadeOutTicks
        ) {
            if (failTitle) {
                throw new IllegalStateException("player disconnected during title");
            }
            titles.add(new Title(
                    legacyTitle, legacySubtitle, fadeInTicks, stayTicks, fadeOutTicks));
        }

        static final class Title {
            private final String title;
            private final String subtitle;
            private final int fadeIn;
            private final int stay;
            private final int fadeOut;

            private Title(
                    String title,
                    String subtitle,
                    int fadeIn,
                    int stay,
                    int fadeOut
            ) {
                this.title = title;
                this.subtitle = subtitle;
                this.fadeIn = fadeIn;
                this.stay = stay;
                this.fadeOut = fadeOut;
            }

            String title() {
                return title;
            }

            String subtitle() {
                return subtitle;
            }

            int fadeIn() {
                return fadeIn;
            }

            int stay() {
                return stay;
            }

            int fadeOut() {
                return fadeOut;
            }
        }
    }
}
