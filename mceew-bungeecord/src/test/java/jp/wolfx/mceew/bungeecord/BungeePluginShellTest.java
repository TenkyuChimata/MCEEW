package jp.wolfx.mceew.bungeecord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BungeePluginShellTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void disabledConfigurationInitializesShellWithoutOperationalRuntime() throws Exception {
        write(config(false, "all"));
        Harness harness = harness();

        harness.shell.initialize();

        assertEquals(BungeePluginShell.State.ACTIVE, harness.shell.state());
        assertFalse(harness.shell.configSnapshot().runtimeEnabled());
        assertNull(harness.shell.latestJmaEarthquakeInformation());
        assertNull(harness.shell.latestCencEarthquakeInformation());
        assertFalse(harness.shell.dispatchTest("forecast"));
        assertEquals(0, harness.backend.tasks.size());
    }

    @Test
    void enabledConfigurationStillDoesNotStartFakePhaseOneRuntime() throws Exception {
        write(config(true, "all"));
        Harness harness = harness();

        harness.shell.initialize();

        assertEquals(BungeePluginShell.State.ACTIVE, harness.shell.state());
        assertTrue(harness.shell.configSnapshot().runtimeEnabled());
        assertFalse(harness.shell.dispatchTest("alert"));
        assertEquals(0, harness.backend.tasks.size());
    }

    @Test
    void invalidStartupKeepsRecoverableShell() throws Exception {
        write("platform_config_version: [\n");
        Harness harness = harness();
        harness.shell.initialize();
        assertEquals(BungeePluginShell.State.FAILED, harness.shell.state());
        assertNull(harness.shell.configSnapshot());

        write(config(false, "all"));
        List<BungeePluginShell.ReloadOutcome> outcomes = new ArrayList<>();
        harness.shell.requestReload(outcomes::add);
        harness.backend.run(0);

        assertEquals(List.of(BungeePluginShell.ReloadOutcome.SUCCESS), outcomes);
        assertEquals(BungeePluginShell.State.ACTIVE, harness.shell.state());
        assertFalse(harness.shell.configSnapshot().runtimeEnabled());
    }

    @Test
    void invalidReloadPreservesPreviousSnapshot() throws Exception {
        write(config(false, "all"));
        Harness harness = harness();
        harness.shell.initialize();
        BungeeConfigSnapshot original = harness.shell.configSnapshot();
        write("platform_config_version: [\n");

        List<BungeePluginShell.ReloadOutcome> outcomes = new ArrayList<>();
        harness.shell.requestReload(outcomes::add);
        harness.backend.run(0);

        assertEquals(List.of(BungeePluginShell.ReloadOutcome.FAILED), outcomes);
        assertSame(original, harness.shell.configSnapshot());
        assertEquals(BungeePluginShell.State.ACTIVE, harness.shell.state());
    }

    @Test
    void validReloadAtomicallyReplacesSnapshot() throws Exception {
        write(config(false, "all"));
        Harness harness = harness();
        harness.shell.initialize();
        BungeeConfigSnapshot original = harness.shell.configSnapshot();
        write(config(false, "none"));

        List<BungeePluginShell.ReloadOutcome> outcomes = new ArrayList<>();
        harness.shell.requestReload(outcomes::add);
        assertSame(original, harness.shell.configSnapshot());
        harness.backend.run(0);

        assertEquals(List.of(BungeePluginShell.ReloadOutcome.SUCCESS), outcomes);
        assertNotSame(original, harness.shell.configSnapshot());
        assertEquals(BungeeConfigSnapshot.TargetMode.NONE,
                harness.shell.configSnapshot().defaultTarget().mode());
    }

    @Test
    void concurrentReloadIsRejectedWithoutSecondTask() throws Exception {
        write(config(false, "all"));
        Harness harness = harness();
        harness.shell.initialize();
        List<BungeePluginShell.ReloadOutcome> first = new ArrayList<>();
        List<BungeePluginShell.ReloadOutcome> second = new ArrayList<>();

        harness.shell.requestReload(first::add);
        harness.shell.requestReload(second::add);

        assertTrue(harness.shell.reloadInProgress());
        assertEquals(List.of(BungeePluginShell.ReloadOutcome.IN_PROGRESS), second);
        assertEquals(1, harness.backend.tasks.size());
        harness.backend.run(0);
        assertEquals(List.of(BungeePluginShell.ReloadOutcome.SUCCESS), first);
        assertFalse(harness.shell.reloadInProgress());
    }

    @Test
    void shutdownCancelsPendingReloadAndPreventsCommit() throws Exception {
        write(config(false, "all"));
        Harness harness = harness();
        harness.shell.initialize();
        List<BungeePluginShell.ReloadOutcome> outcomes = new ArrayList<>();
        harness.shell.requestReload(outcomes::add);
        write(config(false, "none"));

        harness.shell.close();
        harness.backend.run(0);

        assertEquals(BungeePluginShell.State.SHUTDOWN, harness.shell.state());
        assertNull(harness.shell.configSnapshot());
        assertTrue(outcomes.isEmpty(), "cancelled task never commits or calls back");
        assertTrue(harness.scheduler.isClosed());
    }

    @Test
    void shutdownIsIdempotentAndReloadBecomesUnavailable() throws Exception {
        write(config(false, "all"));
        Harness harness = harness();
        harness.shell.initialize();
        harness.shell.close();
        harness.shell.close();
        List<BungeePluginShell.ReloadOutcome> outcomes = new ArrayList<>();

        harness.shell.requestReload(outcomes::add);

        assertEquals(List.of(BungeePluginShell.ReloadOutcome.UNAVAILABLE), outcomes);
        assertEquals(BungeePluginShell.State.SHUTDOWN, harness.shell.state());
    }

    @Test
    void repeatedInitializeDoesNotReloadOrScheduleWork() throws Exception {
        write(config(false, "all"));
        Harness harness = harness();
        harness.shell.initialize();
        BungeeConfigSnapshot original = harness.shell.configSnapshot();

        harness.shell.initialize();

        assertSame(original, harness.shell.configSnapshot());
        assertEquals(0, harness.backend.tasks.size());
    }

    private Harness harness() {
        BungeeDelaySchedulerTest.FakeBackend backend =
                new BungeeDelaySchedulerTest.FakeBackend();
        BungeeDelayScheduler scheduler = new BungeeDelayScheduler(backend);
        Logger logger = Logger.getLogger("BungeePluginShellTest");
        logger.setUseParentHandlers(false);
        BungeePluginShell shell = new BungeePluginShell(
                new BungeeConfigLoader(temporaryDirectory, getClass().getClassLoader()),
                scheduler,
                logger);
        return new Harness(backend, scheduler, shell);
    }

    private void write(String value) throws IOException {
        Files.createDirectories(temporaryDirectory);
        Files.writeString(temporaryDirectory.resolve("config.yml"), value);
    }

    private static String config(boolean enabled, String mode) {
        return "platform_config_version: 1\n"
                + "global:\n  enabled: " + enabled + "\n"
                + "targets:\n  default:\n    mode: " + mode + "\n  sources: {}\n"
                + "groups: {}\nservers: {}\n";
    }

    private static final class Harness {
        private final BungeeDelaySchedulerTest.FakeBackend backend;
        private final BungeeDelayScheduler scheduler;
        private final BungeePluginShell shell;

        private Harness(
                BungeeDelaySchedulerTest.FakeBackend backend,
                BungeeDelayScheduler scheduler,
                BungeePluginShell shell
        ) {
            this.backend = backend;
            this.scheduler = scheduler;
            this.shell = shell;
        }
    }
}
