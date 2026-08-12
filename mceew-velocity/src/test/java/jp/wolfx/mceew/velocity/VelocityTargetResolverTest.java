package jp.wolfx.mceew.velocity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import jp.wolfx.mceew.notification.NotificationSource;
import org.junit.jupiter.api.Test;

class VelocityTargetResolverTest {
    @Test
    void allIncludesPlayersWithAndWithoutBackend() {
        NotificationTestSupport.Environment environment = new NotificationTestSupport.Environment();
        environment.addPlayer("lobby-player", "lobby", Set.of());
        environment.addPlayer("connecting-player", null, Set.of());

        Collection<VelocityTargetResolver.Recipient> recipients = resolver(
                environment, target(VelocityTargetConfig.Mode.ALL, Set.of(), Set.of()), Map.of())
                .resolve(NotificationSource.JMA_ALERT);

        assertEquals(2, recipients.size());
        assertTrue(recipients.stream().anyMatch(recipient -> recipient.backendName() == null));
    }

    @Test
    void noneReturnsNoPlayers() {
        NotificationTestSupport.Environment environment = new NotificationTestSupport.Environment();
        environment.addPlayer("player", "lobby", Set.of());

        assertTrue(resolver(
                environment, target(VelocityTargetConfig.Mode.NONE, Set.of(), Set.of()), Map.of())
                .resolve(NotificationSource.JMA_ALERT).isEmpty());
    }

    @Test
    void selectedUsesExplicitServersAndExcludesNoBackend() {
        NotificationTestSupport.Environment environment = new NotificationTestSupport.Environment();
        environment.addPlayer("lobby-player", "Lobby", Set.of());
        environment.addPlayer("other-player", "survival", Set.of());
        environment.addPlayer("connecting-player", null, Set.of());

        Collection<VelocityTargetResolver.Recipient> recipients = resolver(
                environment,
                target(VelocityTargetConfig.Mode.SELECTED, Set.of("lobby"), Set.of()),
                Map.of()).resolve(NotificationSource.JMA_ALERT);

        assertEquals(Set.of("lobby"), backends(recipients));
    }

    @Test
    void selectedExpandsGroupAndUnionsExplicitServer() {
        NotificationTestSupport.Environment environment = new NotificationTestSupport.Environment();
        environment.addPlayer("lobby-player", "lobby", Set.of());
        environment.addPlayer("survival-player", "survival", Set.of());
        environment.addPlayer("creative-player", "creative", Set.of());
        Map<String, Set<String>> groups = Map.of(
                "primary", new LinkedHashSet<>(Set.of("survival", "creative")));

        Collection<VelocityTargetResolver.Recipient> recipients = resolver(
                environment,
                target(VelocityTargetConfig.Mode.SELECTED, Set.of("lobby"), Set.of("primary")),
                groups).resolve(NotificationSource.JMA_ALERT);

        assertEquals(Set.of("lobby", "survival", "creative"), backends(recipients));
    }

    @Test
    void sourceTargetCompletelyReplacesDefault() {
        NotificationTestSupport.Environment environment = new NotificationTestSupport.Environment();
        environment.addPlayer("player", "lobby", Set.of());
        VelocityTargetConfig config = config(
                target(VelocityTargetConfig.Mode.ALL, Set.of(), Set.of()),
                Map.of(NotificationSource.JMA_ALERT,
                        target(VelocityTargetConfig.Mode.NONE, Set.of(), Set.of())),
                Map.of());

        assertTrue(new VelocityTargetResolver(
                environment.proxy(), config, TestVelocityApi.logger().proxy())
                .resolve(NotificationSource.JMA_ALERT).isEmpty());
        assertEquals(1, new VelocityTargetResolver(
                environment.proxy(), config, TestVelocityApi.logger().proxy())
                .resolve(NotificationSource.SICHUAN_EEW).size());
    }

    @Test
    void duplicateMembershipAndDuplicateUuidDeliverOnce() {
        NotificationTestSupport.Environment environment = new NotificationTestSupport.Environment();
        UUID duplicate = UUID.randomUUID();
        environment.addPlayer(new NotificationTestSupport.RecordingPlayer(
                duplicate, "first", "lobby", Set.of()));
        environment.addPlayer(new NotificationTestSupport.RecordingPlayer(
                duplicate, "second", "lobby", Set.of()));
        Map<String, Set<String>> groups = Map.of("primary", Set.of("lobby"));

        Collection<VelocityTargetResolver.Recipient> recipients = resolver(
                environment,
                target(VelocityTargetConfig.Mode.SELECTED,
                        Set.of("lobby"), Set.of("primary")),
                groups).resolve(NotificationSource.JMA_ALERT);

        assertEquals(1, recipients.size());
    }

    @Test
    void unknownRegisteredServerWarnsOnlyOnceAndProducesNoRecipients() {
        NotificationTestSupport.Environment environment = new NotificationTestSupport.Environment();
        TestVelocityApi.CapturingLogger logger = TestVelocityApi.logger();
        VelocityTargetConfig config = config(
                target(VelocityTargetConfig.Mode.SELECTED, Set.of("offline"), Set.of()),
                Map.of(), Map.of());
        VelocityTargetResolver resolver = new VelocityTargetResolver(
                environment.proxy(), config, logger.proxy());

        assertTrue(resolver.resolve(NotificationSource.JMA_ALERT).isEmpty());
        assertTrue(resolver.resolve(NotificationSource.JMA_ALERT).isEmpty());
        assertEquals(1, logger.warningCountContaining("unregistered backend server"));
    }

    @Test
    void onePlayerBackendRaceDoesNotSuppressOtherRecipients() {
        NotificationTestSupport.Environment environment = new NotificationTestSupport.Environment();
        NotificationTestSupport.RecordingPlayer racing = environment.addPlayer(
                "racing", "lobby", Set.of());
        racing.failCurrentServer(new IllegalStateException("transferring"));
        environment.addPlayer("healthy", "lobby", Set.of());
        TestVelocityApi.CapturingLogger logger = TestVelocityApi.logger();
        VelocityTargetResolver resolver = new VelocityTargetResolver(
                environment.proxy(), config(
                        target(VelocityTargetConfig.Mode.ALL, Set.of(), Set.of()),
                        Map.of(), Map.of()), logger.proxy());

        Collection<VelocityTargetResolver.Recipient> recipients =
                resolver.resolve(NotificationSource.JMA_ALERT);

        assertEquals(1, recipients.size());
        assertEquals(1, logger.warningCountContaining("backend state changed"));
    }

    private static Set<String> backends(Collection<VelocityTargetResolver.Recipient> recipients) {
        return recipients.stream()
                .map(VelocityTargetResolver.Recipient::backendName)
                .collect(Collectors.toSet());
    }

    private static VelocityTargetResolver resolver(
            NotificationTestSupport.Environment environment,
            VelocityTargetConfig.TargetSpec target,
            Map<String, Set<String>> groups
    ) {
        return new VelocityTargetResolver(
                environment.proxy(), config(target, Map.of(), groups),
                TestVelocityApi.logger().proxy());
    }

    private static VelocityTargetConfig config(
            VelocityTargetConfig.TargetSpec defaultTarget,
            Map<NotificationSource, VelocityTargetConfig.TargetSpec> sourceTargets,
            Map<String, Set<String>> groups
    ) {
        return new VelocityTargetConfig(defaultTarget, sourceTargets, groups);
    }

    private static VelocityTargetConfig.TargetSpec target(
            VelocityTargetConfig.Mode mode,
            Set<String> servers,
            Set<String> groups
    ) {
        return new VelocityTargetConfig.TargetSpec(mode, servers, groups);
    }
}
