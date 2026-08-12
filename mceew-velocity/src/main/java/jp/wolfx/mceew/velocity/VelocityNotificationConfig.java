package jp.wolfx.mceew.velocity;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import jp.wolfx.mceew.notification.NotificationProfile;
import jp.wolfx.mceew.notification.NotificationSource;

/** Typed immutable notification profiles, channel inheritance, and targets. */
final class VelocityNotificationConfig {
    static final class SourceSettings {
        private final NotificationProfile profile;
        private final String earthquakeListTemplate;
        private final VelocityChannelOverrides channels;

        SourceSettings(
                NotificationProfile profile,
                String earthquakeListTemplate,
                VelocityChannelOverrides channels
        ) {
            this.profile = profile;
            this.earthquakeListTemplate = earthquakeListTemplate;
            this.channels = channels;
        }

        NotificationProfile profile() {
            return profile;
        }

        String earthquakeListTemplate() {
            return earthquakeListTemplate;
        }

        VelocityChannelOverrides channels() {
            return channels;
        }
    }

    static final class ServerSettings {
        private final VelocityChannelOverrides channels;
        private final Map<NotificationSource, VelocityChannelOverrides> sourceChannels;

        ServerSettings(
                VelocityChannelOverrides channels,
                Map<NotificationSource, VelocityChannelOverrides> sourceChannels
        ) {
            this.channels = channels;
            Map<NotificationSource, VelocityChannelOverrides> sourceChannelCopy =
                    new EnumMap<>(NotificationSource.class);
            sourceChannelCopy.putAll(sourceChannels);
            this.sourceChannels = Collections.unmodifiableMap(sourceChannelCopy);
        }

        VelocityChannelOverrides channels() {
            return channels;
        }

        VelocityChannelOverrides sourceChannels(NotificationSource source) {
            return sourceChannels.getOrDefault(source, VelocityChannelOverrides.EMPTY);
        }
    }

    private final String timeFormat;
    private final VelocityChannelPolicy defaults;
    private final Map<NotificationSource, SourceSettings> sources;
    private final Map<String, ServerSettings> servers;
    private final VelocityTargetConfig targets;

    VelocityNotificationConfig(
            String timeFormat,
            VelocityChannelPolicy defaults,
            Map<NotificationSource, SourceSettings> sources,
            Map<String, ServerSettings> servers,
            VelocityTargetConfig targets
    ) {
        this.timeFormat = timeFormat;
        this.defaults = defaults;
        Map<NotificationSource, SourceSettings> sourceCopy =
                new EnumMap<>(NotificationSource.class);
        sourceCopy.putAll(sources);
        this.sources = Collections.unmodifiableMap(sourceCopy);
        this.servers = Collections.unmodifiableMap(new LinkedHashMap<>(servers));
        this.targets = targets;
    }

    String timeFormat() {
        return timeFormat;
    }

    SourceSettings source(NotificationSource source) {
        SourceSettings settings = sources.get(source);
        if (settings == null) {
            throw new IllegalArgumentException("Missing notification settings for " + source);
        }
        return settings;
    }

    VelocityChannelPolicy proxyChannels(NotificationSource source) {
        return source(source).channels().applyTo(defaults);
    }

    VelocityChannelPolicy playerChannels(NotificationSource source, String backendName) {
        VelocityChannelPolicy effective = proxyChannels(source);
        if (backendName == null) {
            return effective;
        }
        ServerSettings server = servers.get(VelocityConfigLoader.normalizeName(backendName));
        if (server == null) {
            return effective;
        }
        effective = server.channels().applyTo(effective);
        return server.sourceChannels(source).applyTo(effective);
    }

    VelocityTargetConfig targets() {
        return targets;
    }
}
