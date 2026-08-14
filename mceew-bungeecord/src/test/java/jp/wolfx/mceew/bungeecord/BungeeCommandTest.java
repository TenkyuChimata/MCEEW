package jp.wolfx.mceew.bungeecord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.logging.Logger;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.chat.BaseComponent;
import org.junit.jupiter.api.Test;

class BungeeCommandTest {
    @Test
    void rootAndAliasArePublicAndVersionIsInjected() {
        FakeService service = new FakeService();
        BungeeCommand command = command(service, "9.9.9-test");
        FakeSender sender = new FakeSender();

        command.execute(sender, new String[0]);

        assertEquals("eew", command.getName());
        assertEquals(List.of("mceew"), List.of(command.getAliases()));
        assertTrue(sender.contains("Plugin version: v9.9.9-test"));
        assertTrue(sender.contains("Platform: BungeeCord / Waterfall"));
        assertTrue(sender.contains("/eew info"));
        assertFalse(sender.contains("/eew test"));
        assertEquals(null, command.getPermission());
    }

    @Test
    void infoGrammarIsPublicAndUsesOnlyLocalServiceState() {
        FakeService service = new FakeService();
        BungeeCommand command = command(service, "test-version");
        FakeSender sender = new FakeSender();
        service.jma = "cached jma";
        service.cenc = "cached cenc";

        command.execute(sender, new String[]{"info", "jma"});
        command.execute(sender, new String[]{"INFO", "CENC"});

        assertTrue(sender.contains("cached jma"));
        assertTrue(sender.contains("cached cenc"));
    }

    @Test
    void unavailableInfoReportsRuntimeStateWithoutNetworkWork() {
        FakeService service = new FakeService();
        FakeSender sender = new FakeSender();

        command(service, "test-version").execute(sender, new String[]{"info", "jma"});

        assertTrue(sender.contains("runtime is not currently available"));
        assertEquals(0, service.testCalls.size());
    }

    @Test
    void testRequiresPositiveAdminPermission() {
        FakeService service = new FakeService();
        BungeeCommand command = command(service, "test-version");
        FakeSender denied = new FakeSender();
        FakeSender allowed = new FakeSender();
        allowed.permissions.put(BungeePermissions.ADMIN, true);

        command.execute(denied, new String[]{"test", "forecast"});
        command.execute(allowed, new String[]{"test", "forecast"});

        assertEquals(List.of("forecast"), service.testCalls);
        assertTrue(denied.contains("do not have permission"));
        assertTrue(allowed.contains("runtime is not currently available"));
    }

    @Test
    void onlyCanonicalTestGrammarDispatches() {
        FakeService service = new FakeService();
        BungeeCommand command = command(service, "test-version");
        FakeSender sender = new FakeSender();
        sender.permissions.put(BungeePermissions.ADMIN, true);

        for (String source : List.of("forecast", "alert", "sc", "fj", "cwa", "cenc", "cq")) {
            command.execute(sender, new String[]{"test", source});
        }
        command.execute(sender, new String[]{"test", "sound"});

        assertEquals(List.of("forecast", "alert", "sc", "fj", "cwa", "cenc", "cq"),
                service.testCalls);
    }

    @Test
    void operationalTestDispatchDoesNotReportUnavailable() {
        FakeService service = new FakeService();
        service.testOutcome = BungeeCommandService.TestOutcome.DISPATCHED;
        BungeeCommand command = command(service, "test-version");
        FakeSender administrator = new FakeSender();
        administrator.permissions.put(BungeePermissions.ADMIN, true);

        command.execute(administrator, new String[]{"test", "forecast"});

        assertEquals(List.of("forecast"), service.testCalls);
        assertFalse(administrator.contains("runtime is not currently available"));
    }

    @Test
    void reloadRequiresAdminAndReportsEveryOutcome() {
        FakeService service = new FakeService();
        BungeeCommand command = command(service, "test-version");
        FakeSender denied = new FakeSender();
        command.execute(denied, new String[]{"reload"});
        assertEquals(0, service.reloadCalls);

        FakeSender allowed = new FakeSender();
        allowed.permissions.put(BungeePermissions.ADMIN, true);
        for (BungeePluginShell.ReloadOutcome outcome : BungeePluginShell.ReloadOutcome.values()) {
            service.reloadOutcome = outcome;
            command.execute(allowed, new String[]{"reload"});
        }

        assertEquals(BungeePluginShell.ReloadOutcome.values().length, service.reloadCalls);
        assertTrue(allowed.contains("reloaded successfully"));
        assertTrue(allowed.contains("already in progress"));
        assertTrue(allowed.contains("could not be read or validated"));
        assertTrue(allowed.contains("could not be activated"));
        assertTrue(allowed.contains("active state was preserved"));
        assertTrue(allowed.contains("runtime is not currently available"));
    }

    @Test
    void tabCompletionIsDeterministicAndAdminAware() {
        BungeeCommand command = command(new FakeService(), "test-version");
        FakeSender publicSender = new FakeSender();
        FakeSender admin = new FakeSender();
        admin.permissions.put(BungeePermissions.ADMIN, true);

        assertEquals(List.of("info"), list(command.onTabComplete(publicSender, new String[]{""})));
        assertEquals(List.of("info", "test", "reload"),
                list(command.onTabComplete(admin, new String[]{""})));
        assertEquals(List.of("jma", "cenc"),
                list(command.onTabComplete(publicSender, new String[]{"info", ""})));
        assertEquals(List.of("forecast", "fj"),
                list(command.onTabComplete(admin, new String[]{"test", "f"})));
        assertTrue(list(command.onTabComplete(
                publicSender, new String[]{"test", ""})).isEmpty());
    }

    @Test
    void extraAndUnknownArgumentsDoNotPretendSuccess() {
        FakeService service = new FakeService();
        BungeeCommand command = command(service, "test-version");
        FakeSender sender = new FakeSender();
        sender.permissions.put(BungeePermissions.ADMIN, true);

        command.execute(sender, new String[]{"reload", "extra"});
        command.execute(sender, new String[]{"unknown"});

        assertEquals(0, service.reloadCalls);
        assertTrue(sender.contains("Usage: /eew reload"));
        assertTrue(sender.contains("Unknown subcommand"));
    }

    @Test
    void malformedInfoAndTestGrammarAlwaysReturnsDeterministicUsage() {
        FakeService service = new FakeService();
        BungeeCommand command = command(service, "test-version");
        FakeSender sender = new FakeSender();
        sender.permissions.put(BungeePermissions.ADMIN, true);

        command.execute(sender, new String[]{"info"});
        command.execute(sender, new String[]{"info", "unknown"});
        command.execute(sender, new String[]{"info", "jma", "extra"});
        command.execute(sender, new String[]{"test"});
        command.execute(sender, new String[]{"test", "unknown"});
        command.execute(sender, new String[]{"test", "cenc", "extra"});

        assertEquals(6, sender.messages.stream().filter(message ->
                message.contains("/eew info jma")).count()
                + sender.messages.stream().filter(message ->
                        message.contains("/eew test forecast")).count());
        assertTrue(service.testCalls.isEmpty());
    }

    @Test
    void commandKeywordsAndCanonicalArgumentsAreCaseInsensitive() {
        FakeService service = new FakeService();
        service.jma = "cached jma";
        service.testOutcome = BungeeCommandService.TestOutcome.DISPATCHED;
        BungeeCommand command = command(service, "test-version");
        FakeSender admin = new FakeSender();
        admin.permissions.put(BungeePermissions.ADMIN, true);

        command.execute(admin, new String[]{"INFO", "JMA"});
        command.execute(admin, new String[]{"TeSt", "CeNc"});
        command.execute(admin, new String[]{"ReLoAd"});

        assertTrue(admin.contains("cached jma"));
        assertEquals(List.of("cenc"), service.testCalls);
        assertEquals(1, service.reloadCalls);
    }

    @Test
    void deniedAdminCommandsAreSideEffectFreeAndExplainDenial() {
        FakeService service = new FakeService();
        BungeeCommand command = command(service, "test-version");
        FakeSender denied = new FakeSender();

        command.execute(denied, new String[]{"test", "forecast"});
        command.execute(denied, new String[]{"reload"});

        assertTrue(service.testCalls.isEmpty());
        assertEquals(0, service.reloadCalls);
        assertEquals(2, denied.messages.stream()
                .filter(message -> message.contains("do not have permission")).count());
    }

    @Test
    void everyTestDispatchOutcomeHasAccurateFeedback() {
        FakeService service = new FakeService();
        BungeeCommand command = command(service, "test-version");
        FakeSender admin = new FakeSender();
        admin.permissions.put(BungeePermissions.ADMIN, true);

        service.testOutcome = BungeeCommandService.TestOutcome.IN_PROGRESS;
        command.execute(admin, new String[]{"test", "forecast"});
        service.testOutcome = BungeeCommandService.TestOutcome.UNAVAILABLE;
        command.execute(admin, new String[]{"test", "alert"});
        service.testOutcome = BungeeCommandService.TestOutcome.FAILED;
        command.execute(admin, new String[]{"test", "sc"});

        assertTrue(admin.contains("reload is in progress"));
        assertTrue(admin.contains("runtime is not currently available"));
        assertTrue(admin.contains("could not be dispatched"));
    }

    @Test
    void tabCompletionFiltersPrefixesDepthAndAdministrativeVisibility() {
        BungeeCommand command = command(new FakeService(), "test-version");
        FakeSender player = new FakeSender();
        FakeSender console = new FakeSender();
        console.name = "CONSOLE";
        console.permissions.put(BungeePermissions.ADMIN, true);

        assertEquals(List.of("info"), list(command.onTabComplete(player, new String[]{"I"})));
        assertEquals(List.of("cwa", "cenc", "cq"),
                list(command.onTabComplete(console, new String[]{"TEST", "c"})));
        assertEquals(List.of("reload"),
                list(command.onTabComplete(console, new String[]{"r"})));
        assertTrue(list(command.onTabComplete(
                console, new String[]{"info", "jma", ""})).isEmpty());
        assertTrue(list(command.onTabComplete(
                console, new String[]{"reload", "extra"})).isEmpty());
        assertEquals(List.of("mceew"), List.of(command.getAliases()),
                "the same TabExecutor owns eew and its alias");
    }

    @Test
    void consoleLikeSenderHasAdministrativeBehaviorThroughNormalPermissionApi() {
        FakeService service = new FakeService();
        service.testOutcome = BungeeCommandService.TestOutcome.DISPATCHED;
        BungeeCommand command = command(service, "test-version");
        FakeSender console = new FakeSender();
        console.name = "CONSOLE";
        console.permissions.put(BungeePermissions.ADMIN, true);

        command.execute(console, new String[0]);
        command.execute(console, new String[]{"test", "cq"});
        command.execute(console, new String[]{"reload"});

        assertTrue(console.contains("/eew test"));
        assertEquals(List.of("cq"), service.testCalls);
        assertEquals(1, service.reloadCalls);
    }

    @Test
    void administrativePermissionFailureDefaultsToDenyWithoutBreakingPublicCommands() {
        FakeService service = new FakeService();
        BungeeCommand command = command(service, "test-version");
        FakeSender sender = new FakeSender();
        sender.failPermission = true;

        command.execute(sender, new String[0]);
        command.execute(sender, new String[]{"info", "jma"});
        command.execute(sender, new String[]{"test", "forecast"});
        command.execute(sender, new String[]{"reload"});

        assertTrue(sender.contains("Plugin version"));
        assertTrue(sender.contains("runtime is not currently available"));
        assertEquals(2, sender.messages.stream()
                .filter(message -> message.contains("do not have permission")).count());
        assertTrue(service.testCalls.isEmpty());
        assertEquals(0, service.reloadCalls);
        assertEquals(List.of("info"), list(command.onTabComplete(sender, new String[]{""})));
    }

    @Test
    void unexpectedServiceAndResponseFailuresStayInsideCommandBoundary() {
        FakeService service = new FakeService();
        service.failInfo = true;
        BungeeCommand command = command(service, "test-version");
        FakeSender sender = new FakeSender();

        assertDoesNotThrow(() -> command.execute(sender, new String[]{"info", "jma"}));
        assertTrue(sender.contains("Command could not be completed"));

        sender.failSend = true;
        assertDoesNotThrow(() -> command.execute(sender, new String[0]));
    }

    private static BungeeCommand command(FakeService service, String version) {
        Logger logger = Logger.getLogger("BungeeCommandTest." + System.nanoTime());
        logger.setUseParentHandlers(false);
        return new BungeeCommand(service, version, logger);
    }

    private static List<String> list(Iterable<String> values) {
        List<String> result = new ArrayList<>();
        values.forEach(result::add);
        return result;
    }

    private static final class FakeService implements BungeeCommandService {
        private String jma;
        private String cenc;
        private final List<String> testCalls = new ArrayList<>();
        private int reloadCalls;
        private BungeePluginShell.ReloadOutcome reloadOutcome =
                BungeePluginShell.ReloadOutcome.SUCCESS;
        private BungeeCommandService.TestOutcome testOutcome =
                BungeeCommandService.TestOutcome.UNAVAILABLE;
        private boolean failInfo;

        @Override
        public String latestJmaEarthquakeInformation() {
            if (failInfo) {
                throw new IllegalStateException("service unavailable");
            }
            return jma;
        }

        @Override
        public String latestCencEarthquakeInformation() {
            return cenc;
        }

        @Override
        public TestOutcome dispatchTest(String sourceKey) {
            testCalls.add(sourceKey);
            return testOutcome;
        }

        @Override
        public void requestReload(Consumer<BungeePluginShell.ReloadOutcome> completion) {
            reloadCalls++;
            completion.accept(reloadOutcome);
        }
    }

    @SuppressWarnings("deprecation")
    private static final class FakeSender implements CommandSender {
        private final Map<String, Boolean> permissions = new LinkedHashMap<>();
        private final Set<String> groups = new LinkedHashSet<>();
        private final List<String> messages = new ArrayList<>();
        private String name = "tester";
        private boolean failSend;
        private boolean failPermission;

        boolean contains(String text) {
            return messages.stream().anyMatch(message -> message.contains(text));
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public void sendMessage(String message) {
            messages.add(message);
        }

        @Override
        public void sendMessages(String... values) {
            messages.addAll(List.of(values));
        }

        @Override
        public void sendMessage(BaseComponent... message) {
            if (failSend) {
                throw new IllegalStateException("sender disconnected");
            }
            messages.add(BaseComponent.toLegacyText(message));
        }

        @Override
        public void sendMessage(BaseComponent message) {
            if (failSend) {
                throw new IllegalStateException("sender disconnected");
            }
            messages.add(message.toLegacyText());
        }

        @Override
        public Collection<String> getGroups() {
            return Set.copyOf(groups);
        }

        @Override
        public void addGroups(String... values) {
            groups.addAll(List.of(values));
        }

        @Override
        public void removeGroups(String... values) {
            groups.removeAll(List.of(values));
        }

        @Override
        public boolean hasPermission(String permission) {
            if (failPermission) {
                throw new IllegalStateException("permission provider unavailable");
            }
            return permissions.getOrDefault(permission, false);
        }

        @Override
        public void setPermission(String permission, boolean value) {
            permissions.put(permission, value);
        }

        @Override
        public Collection<String> getPermissions() {
            return Set.copyOf(permissions.keySet());
        }
    }
}
