package jp.wolfx.mceew;

import jp.wolfx.mceew.websocket.WebSocketConnectionManager;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MceewCommandCharacterizationTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void rootInfoAndUnknownCommandsPreserveReturnValuesAndExactReplies() {
        MceewCharacterizationSupport.Harness harness = MceewCharacterizationSupport.harness();
        List<String> messages = new ArrayList<>();
        CommandSender sender = MceewCharacterizationSupport.sender(true, messages);

        assertTrue(command(harness, sender));
        assertEquals(List.of(
                "§a[MCEEW] Plugin version: v2.7.0",
                "§a[MCEEW] §3/eew§a - Show available commands",
                "§a[MCEEW] §3/eew test§a - Send a test EEW alert",
                "§a[MCEEW] §3/eew info§a - Display latest earthquake information",
                "§a[MCEEW] §3/eew reload§a - Reload plugin configuration"), messages);

        messages.clear();
        assertTrue(command(harness, sender, "info"));
        assertEquals(List.of(
                "§a[MCEEW] §3/eew info jma§a - Show Japan JMA earthquake information.",
                "§a[MCEEW] §3/eew info cenc§a - Show China CENC earthquake information."),
                messages);

        messages.clear();
        assertFalse(command(harness, sender, "info", "unknown"));
        assertTrue(messages.isEmpty());
        assertFalse(command(harness, sender, "unknown"));
        assertTrue(messages.isEmpty());
    }

    @Test
    void infoCommandsReturnUnavailableThenLatestCachedValues() {
        MceewCharacterizationSupport.Harness harness = MceewCharacterizationSupport.harness();
        List<String> messages = new ArrayList<>();
        CommandSender sender = MceewCharacterizationSupport.sender(false, messages);

        assertTrue(command(harness, sender, "info", "jma"));
        assertTrue(command(harness, sender, "info", "cenc"));
        assertEquals(List.of(
                EarthquakeInfoCache.NOT_AVAILABLE, EarthquakeInfoCache.NOT_AVAILABLE), messages);

        harness.route(MceewCharacterizationSupport.fixture("jma_eqlist").toString());
        harness.route(MceewCharacterizationSupport.fixture("cenc_eqlist").toString());
        messages.clear();
        assertTrue(command(harness, sender, "info", "jma"));
        assertTrue(command(harness, sender, "info", "cenc"));

        assertEquals(2, messages.size());
        assertTrue(messages.get(0).startsWith("§e地震情報\n"));
        assertTrue(messages.get(0).contains("能登半島沖"));
        assertTrue(messages.get(0).contains("津波警報等"));
        assertTrue(messages.get(1).startsWith("§e中国地震台网 (正式测定)\n"));
        assertTrue(messages.get(1).contains("四川测试地区"));
    }

    @Test
    void testSubcommandsDispatchEveryCurrentSourceAndAppendFixedWarning() {
        Map<String, String> permissionByCommand = new LinkedHashMap<>();
        permissionByCommand.put("alert", "mceew.notify.jma.alert");
        permissionByCommand.put("forecast", "mceew.notify.jma.forecast");
        permissionByCommand.put("sc", "mceew.notify.sc");
        permissionByCommand.put("fj", "mceew.notify.fj");
        permissionByCommand.put("cwa", "mceew.notify.cwa");
        permissionByCommand.put("cenc", "mceew.notify.cenc.eew");
        permissionByCommand.put("cq", "mceew.notify.cq");

        for (Map.Entry<String, String> entry : permissionByCommand.entrySet()) {
            MceewCharacterizationSupport.Harness harness =
                    MceewCharacterizationSupport.harness();
            List<String> replies = new ArrayList<>();
            CommandSender operator = MceewCharacterizationSupport.sender(true, replies);

            assertTrue(command(harness, operator, "test", entry.getKey()), entry.getKey());
            assertTrue(replies.isEmpty(), entry.getKey());
            assertEquals(2, harness.console.size(), entry.getKey());
            assertEquals(2, harness.player.chat.size(), entry.getKey());
            assertEquals("§eWarning: This is an Earthquake Early Warning test.",
                    harness.console.get(1), entry.getKey());
            assertEquals("§eWarning: This is an Earthquake Early Warning test.",
                    harness.player.chat.get(1), entry.getKey());
            assertEquals(1, harness.player.titles.size(), entry.getKey());
            assertEquals(1, harness.player.sounds.size(), entry.getKey());
            assertTrue(harness.player.queriedPermissions.contains(entry.getValue()),
                    entry.getKey());
        }
    }

    @Test
    void testHelpUnknownAndOperatorGatePreserveCurrentBehavior() {
        MceewCharacterizationSupport.Harness harness = MceewCharacterizationSupport.harness();
        List<String> messages = new ArrayList<>();
        CommandSender operator = MceewCharacterizationSupport.sender(true, messages);
        assertTrue(command(harness, operator, "test"));
        assertEquals(List.of(
                "§a[MCEEW] §3/eew test forecast§a - Send JMA forecast EEW test.",
                "§a[MCEEW] §3/eew test alert§a - Send JMA alert EEW test.",
                "§a[MCEEW] §3/eew test sc§a - Send Sichuan EEW test.",
                "§a[MCEEW] §3/eew test fj§a - Send Taiwan/Fujian EEW test.",
                "§a[MCEEW] §3/eew test cwa§a - Send Taiwan CWA EEW test.",
                "§a[MCEEW] §3/eew test cenc§a - Send China CENC EEW test.",
                "§a[MCEEW] §3/eew test cq§a - Send Chongqing EEW test."), messages);

        messages.clear();
        assertFalse(command(harness, operator, "test", "unknown"));
        assertTrue(messages.isEmpty());

        CommandSender nonOperator = MceewCharacterizationSupport.sender(false, messages);
        assertFalse(command(harness, nonOperator, "test", "alert"));
        assertFalse(command(harness, nonOperator, "reload"));
        assertTrue(messages.isEmpty());
        assertTrue(harness.console.isEmpty());
    }

    @Test
    void successfulReloadPreparesThenLoadsRuntimeAndRestartsWebSocketExactlyOnce()
            throws Exception {
        MceewCharacterizationSupport.Harness harness = MceewCharacterizationSupport.harness();
        Path dataDirectory = temporaryDirectory.resolve("success");
        Files.createDirectories(dataDirectory);
        YamlConfiguration reloaded = MceewCharacterizationSupport.defaultConfiguration();
        reloaded.set("enable_sc", false);
        Files.writeString(dataDirectory.resolve("config.yml"), reloaded.saveToString(),
                StandardCharsets.UTF_8);
        installReloadFiles(harness, dataDirectory);
        MceewCharacterizationSupport.field(harness.plugin, "configManager",
                configManager(dataDirectory, this::defaultConfigStream));
        AtomicInteger connects = new AtomicInteger();
        AtomicBoolean observedLoadedValue = new AtomicBoolean();
        WebSocketConnectionManager manager = webSocketManager(() -> {
            connects.incrementAndGet();
            observedLoadedValue.set(!(Boolean) MceewCharacterizationSupport.field(
                    harness.plugin, "scEewBoolean"));
        });
        MceewCharacterizationSupport.field(harness.plugin, "webSocketManager", manager);
        List<String> messages = new ArrayList<>();

        assertTrue(command(harness,
                MceewCharacterizationSupport.sender(true, messages), "reload"));

        assertEquals(List.of("§a[MCEEW] Configuration reloaded successfully."), messages);
        assertEquals(1, connects.get());
        assertTrue(observedLoadedValue.get(), "runtime config is loaded before restart connects");
        assertFalse((Boolean) MceewCharacterizationSupport.field(
                harness.plugin, "scEewBoolean"));
    }

    @Test
    void failedPreparationKeepsRuntimeAndLoadedConfigAndSkipsRestart() throws Exception {
        MceewCharacterizationSupport.Harness harness = MceewCharacterizationSupport.harness();
        Path dataDirectory = temporaryDirectory.resolve("failure");
        Files.createDirectories(dataDirectory);
        YamlConfiguration onDisk = MceewCharacterizationSupport.defaultConfiguration();
        onDisk.set("enable_sc", false);
        Files.writeString(dataDirectory.resolve("config.yml"), onDisk.saveToString(),
                StandardCharsets.UTF_8);
        installReloadFiles(harness, dataDirectory);
        Object originalLoadedConfig = MceewCharacterizationSupport.field(harness.plugin, "newConfig");
        MceewCharacterizationSupport.field(harness.plugin, "configManager",
                configManager(dataDirectory, () -> {
                    throw new IOException("deliberate defaults failure");
                }));
        AtomicInteger connects = new AtomicInteger();
        MceewCharacterizationSupport.field(harness.plugin, "webSocketManager",
                webSocketManager(connects::incrementAndGet));
        List<String> messages = new ArrayList<>();

        assertTrue(command(harness,
                MceewCharacterizationSupport.sender(true, messages), "reload"));

        assertEquals(List.of(
                "§c[MCEEW] Configuration reload failed; the existing file was left unchanged."),
                messages);
        assertTrue((Boolean) MceewCharacterizationSupport.field(
                harness.plugin, "scEewBoolean"));
        assertSame(originalLoadedConfig,
                MceewCharacterizationSupport.field(harness.plugin, "newConfig"),
                "reloadConfig was not called");
        assertEquals(0, connects.get());
    }

    private static boolean command(
            MceewCharacterizationSupport.Harness harness,
            CommandSender sender, String... arguments) {
        return harness.plugin.onCommand(sender, null, "eew", arguments);
    }

    private void installReloadFiles(
            MceewCharacterizationSupport.Harness harness, Path dataDirectory) {
        MceewCharacterizationSupport.javaPluginField(
                harness.plugin, "dataFolder", dataDirectory.toFile());
        MceewCharacterizationSupport.javaPluginField(
                harness.plugin, "configFile", dataDirectory.resolve("config.yml").toFile());
        MceewCharacterizationSupport.javaPluginField(
                harness.plugin, "classLoader", getClass().getClassLoader());
    }

    private InputStream defaultConfigStream() throws IOException {
        InputStream input = getClass().getClassLoader().getResourceAsStream("config.yml");
        if (input == null) {
            throw new IOException("test config.yml missing");
        }
        return input;
    }

    private static ConfigManager configManager(
            Path dataDirectory, ConfigManager.DefaultsProvider defaults) {
        Logger logger = Logger.getLogger(
                "MceewCommandCharacterizationTest." + System.nanoTime());
        logger.setUseParentHandlers(false);
        return new ConfigManager(dataDirectory, defaults, logger, new TestFileAccess());
    }

    private static WebSocketConnectionManager webSocketManager(Runnable onConnect) {
        return new WebSocketConnectionManager(
                listener -> {
                    onConnect.run();
                    return CompletableFuture.completedFuture((WebSocket) null);
                },
                (task, delay, unit) -> () -> { },
                message -> { },
                Logger.getLogger("MceewCommandCharacterizationTest.websocket"),
                5, TimeUnit.SECONDS);
    }

    private static final class TestFileAccess implements ConfigManager.FileAccess {
        @Override
        public boolean exists(Path path) {
            return Files.exists(path);
        }

        @Override
        public void createDirectories(Path directory) throws IOException {
            Files.createDirectories(directory);
        }

        @Override
        public byte[] read(Path path) throws IOException {
            return Files.readAllBytes(path);
        }

        @Override
        public void writeAndSync(Path path, byte[] contents) throws IOException {
            Files.write(path, contents, StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        }

        @Override
        public void copy(Path source, Path target) throws IOException {
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        }

        @Override
        public void replace(Path source, Path target) throws IOException {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }

        @Override
        public void deleteIfExists(Path path) throws IOException {
            Files.deleteIfExists(path);
        }
    }
}
