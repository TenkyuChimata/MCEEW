package jp.wolfx.mceew.velocity;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import jp.wolfx.mceew.notification.NotificationSource;

/** Immutable target-selection rules and named backend groups. */
final class VelocityTargetConfig {
    enum Mode {
        ALL,
        SELECTED,
        NONE
    }

    static final class TargetSpec {
        private final Mode mode;
        private final Set<String> servers;
        private final Set<String> groups;

        TargetSpec(Mode mode, Set<String> servers, Set<String> groups) {
            this.mode = mode;
            this.servers = Collections.unmodifiableSet(new LinkedHashSet<>(servers));
            this.groups = Collections.unmodifiableSet(new LinkedHashSet<>(groups));
        }

        Mode mode() {
            return mode;
        }

        Set<String> servers() {
            return servers;
        }

        Set<String> groups() {
            return groups;
        }
    }

    private final TargetSpec defaultTarget;
    private final Map<NotificationSource, TargetSpec> sourceTargets;
    private final Map<String, Set<String>> groups;

    VelocityTargetConfig(
            TargetSpec defaultTarget,
            Map<NotificationSource, TargetSpec> sourceTargets,
            Map<String, Set<String>> groups
    ) {
        this.defaultTarget = defaultTarget;
        Map<NotificationSource, TargetSpec> sourceTargetCopy =
                new EnumMap<>(NotificationSource.class);
        sourceTargetCopy.putAll(sourceTargets);
        this.sourceTargets = Collections.unmodifiableMap(sourceTargetCopy);
        Map<String, Set<String>> groupCopy = new LinkedHashMap<>();
        groups.forEach((name, servers) -> groupCopy.put(
                name, Collections.unmodifiableSet(new LinkedHashSet<>(servers))));
        this.groups = Collections.unmodifiableMap(groupCopy);
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
}
