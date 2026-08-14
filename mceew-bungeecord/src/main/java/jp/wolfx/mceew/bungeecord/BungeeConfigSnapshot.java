package jp.wolfx.mceew.bungeecord;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import jp.wolfx.mceew.notification.NotificationSource;

final class BungeeConfigSnapshot {
    enum TargetMode {
        ALL,
        SELECTED,
        NONE
    }

    static final class SourceGates {
        private final boolean jma;
        private final boolean sichuan;
        private final boolean fujian;
        private final boolean cwa;
        private final boolean cenc;
        private final boolean chongqing;

        SourceGates(
                boolean jma,
                boolean sichuan,
                boolean fujian,
                boolean cwa,
                boolean cenc,
                boolean chongqing
        ) {
            this.jma = jma;
            this.sichuan = sichuan;
            this.fujian = fujian;
            this.cwa = cwa;
            this.cenc = cenc;
            this.chongqing = chongqing;
        }

        boolean jma() {
            return jma;
        }

        boolean sichuan() {
            return sichuan;
        }

        boolean fujian() {
            return fujian;
        }

        boolean cwa() {
            return cwa;
        }

        boolean cenc() {
            return cenc;
        }

        boolean chongqing() {
            return chongqing;
        }
    }

    static final class ChannelPolicy {
        private final boolean broadcast;
        private final boolean title;

        ChannelPolicy(boolean broadcast, boolean title) {
            this.broadcast = broadcast;
            this.title = title;
        }

        boolean broadcast() {
            return broadcast;
        }

        boolean title() {
            return title;
        }
    }

    static final class ChannelOverrides {
        private static final ChannelOverrides EMPTY = new ChannelOverrides(null, null);

        private final Boolean broadcast;
        private final Boolean title;

        ChannelOverrides(Boolean broadcast, Boolean title) {
            this.broadcast = broadcast;
            this.title = title;
        }

        Boolean broadcast() {
            return broadcast;
        }

        Boolean title() {
            return title;
        }

        ChannelPolicy applyTo(ChannelPolicy inherited) {
            return new ChannelPolicy(
                    broadcast == null ? inherited.broadcast() : broadcast,
                    title == null ? inherited.title() : title);
        }
    }

    static final class SourceSettings {
        private final String message;
        private final String title;
        private final String subtitle;
        private final ChannelOverrides channels;

        SourceSettings(
                String message,
                String title,
                String subtitle,
                ChannelOverrides channels
        ) {
            this.message = Objects.requireNonNull(message, "message");
            this.title = title;
            this.subtitle = subtitle;
            this.channels = Objects.requireNonNull(channels, "channels");
        }

        String message() {
            return message;
        }

        String title() {
            return title;
        }

        String subtitle() {
            return subtitle;
        }

        ChannelOverrides channels() {
            return channels;
        }
    }

    static final class TargetSpec {
        private final TargetMode mode;
        private final Set<String> servers;
        private final Set<String> groups;

        TargetSpec(TargetMode mode, Set<String> servers, Set<String> groups) {
            this.mode = Objects.requireNonNull(mode, "mode");
            this.servers = immutableSet(servers);
            this.groups = immutableSet(groups);
        }

        TargetMode mode() {
            return mode;
        }

        Set<String> servers() {
            return servers;
        }

        Set<String> groups() {
            return groups;
        }
    }

    static final class ServerSettings {
        private final ChannelOverrides channels;
        private final Map<NotificationSource, ChannelOverrides> sourceChannels;

        ServerSettings(
                ChannelOverrides channels,
                Map<NotificationSource, ChannelOverrides> sourceChannels
        ) {
            this.channels = Objects.requireNonNull(channels, "channels");
            this.sourceChannels = immutableEnumMap(sourceChannels);
        }

        ChannelOverrides channels() {
            return channels;
        }

        Map<NotificationSource, ChannelOverrides> sourceChannels() {
            return sourceChannels;
        }

        ChannelOverrides sourceChannels(NotificationSource source) {
            return sourceChannels.getOrDefault(source, ChannelOverrides.EMPTY);
        }
    }

    private final int platformConfigVersion;
    private final boolean runtimeEnabled;
    private final SourceGates sourceGates;
    private final String timeFormat;
    private final ChannelPolicy notificationDefaults;
    private final Map<NotificationSource, SourceSettings> notificationSources;
    private final TargetSpec defaultTarget;
    private final Map<NotificationSource, TargetSpec> sourceTargets;
    private final Map<String, Set<String>> groups;
    private final Map<String, ServerSettings> servers;

    BungeeConfigSnapshot(
            int platformConfigVersion,
            boolean runtimeEnabled,
            SourceGates sourceGates,
            String timeFormat,
            ChannelPolicy notificationDefaults,
            Map<NotificationSource, SourceSettings> notificationSources,
            TargetSpec defaultTarget,
            Map<NotificationSource, TargetSpec> sourceTargets,
            Map<String, Set<String>> groups,
            Map<String, ServerSettings> servers
    ) {
        this.platformConfigVersion = platformConfigVersion;
        this.runtimeEnabled = runtimeEnabled;
        this.sourceGates = Objects.requireNonNull(sourceGates, "sourceGates");
        this.timeFormat = Objects.requireNonNull(timeFormat, "timeFormat");
        this.notificationDefaults = Objects.requireNonNull(
                notificationDefaults, "notificationDefaults");
        this.notificationSources = immutableEnumMap(notificationSources);
        this.defaultTarget = Objects.requireNonNull(defaultTarget, "defaultTarget");
        this.sourceTargets = immutableEnumMap(sourceTargets);
        this.groups = immutableGroups(groups);
        this.servers = Collections.unmodifiableMap(new LinkedHashMap<>(servers));
    }

    int platformConfigVersion() {
        return platformConfigVersion;
    }

    boolean runtimeEnabled() {
        return runtimeEnabled;
    }

    SourceGates sourceGates() {
        return sourceGates;
    }

    String timeFormat() {
        return timeFormat;
    }

    ChannelPolicy notificationDefaults() {
        return notificationDefaults;
    }

    Map<NotificationSource, SourceSettings> notificationSources() {
        return notificationSources;
    }

    TargetSpec defaultTarget() {
        return defaultTarget;
    }

    Map<NotificationSource, TargetSpec> sourceTargets() {
        return sourceTargets;
    }

    Map<String, Set<String>> groups() {
        return groups;
    }

    Map<String, ServerSettings> servers() {
        return servers;
    }

    SourceSettings source(NotificationSource source) {
        SourceSettings settings = notificationSources.get(source);
        if (settings == null) {
            throw new IllegalArgumentException("Missing notification settings for " + source);
        }
        return settings;
    }

    ChannelPolicy proxyChannels(NotificationSource source) {
        return source(source).channels().applyTo(notificationDefaults);
    }

    ChannelPolicy playerChannels(NotificationSource source, String backendName) {
        ChannelPolicy effective = proxyChannels(source);
        if (backendName == null) {
            return effective;
        }
        ServerSettings server = servers.get(BungeeConfigLoader.normalizeName(backendName));
        if (server == null) {
            return effective;
        }
        effective = server.channels().applyTo(effective);
        return server.sourceChannels(source).applyTo(effective);
    }

    TargetSpec targetFor(NotificationSource source) {
        return sourceTargets.getOrDefault(source, defaultTarget);
    }

    Set<String> selectedServers(NotificationSource source) {
        TargetSpec target = targetFor(source);
        Set<String> selected = new LinkedHashSet<>(target.servers());
        for (String group : target.groups()) {
            selected.addAll(groups.get(group));
        }
        return Collections.unmodifiableSet(selected);
    }

    private static <T> Map<NotificationSource, T> immutableEnumMap(
            Map<NotificationSource, T> values
    ) {
        Map<NotificationSource, T> copy = new EnumMap<>(NotificationSource.class);
        copy.putAll(values);
        return Collections.unmodifiableMap(copy);
    }

    private static Set<String> immutableSet(Set<String> values) {
        return Collections.unmodifiableSet(new LinkedHashSet<>(values));
    }

    private static Map<String, Set<String>> immutableGroups(Map<String, Set<String>> values) {
        Map<String, Set<String>> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Set<String>> entry : values.entrySet()) {
            copy.put(entry.getKey(), immutableSet(entry.getValue()));
        }
        return Collections.unmodifiableMap(copy);
    }
}
