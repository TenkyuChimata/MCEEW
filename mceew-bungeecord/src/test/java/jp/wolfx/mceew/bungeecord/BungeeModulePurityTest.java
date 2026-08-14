package jp.wolfx.mceew.bungeecord;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class BungeeModulePurityTest {
    private static final List<String> PROHIBITED_BYTECODE_REFERENCES = List.of(
            "io/github/waterfallmc/",
            "net/md_5/bungee/protocol/",
            "org/bukkit/",
            "com/velocitypowered/",
            "sun/misc/" + "Unsafe",
            "jdk/internal/misc/" + "Unsafe",
            "sun/reflect/" + "Reflection" + "Factory",
            "java/lang/reflect/",
            "net/luckperms/",
            "org/bstats/");

    @Test
    void productionBytecodeUsesNoForbiddenPlatformOrInternalApi() throws Exception {
        Path output = Path.of(System.getProperty("mceew.bungeecord.output"));
        try (Stream<Path> files = Files.walk(output.resolve("jp/wolfx/mceew/bungeecord"))) {
            for (Path file : (Iterable<Path>) files.filter(path ->
                    path.toString().endsWith(".class"))::iterator) {
                String bytes = new String(Files.readAllBytes(file), StandardCharsets.ISO_8859_1);
                for (String prohibited : PROHIBITED_BYTECODE_REFERENCES) {
                    assertFalse(bytes.contains(prohibited), file + " references " + prohibited);
                }
            }
        }
    }

    @Test
    void productionSourcesContainNoUnsafeReflectionPacketsOrProviderIntegration()
            throws Exception {
        Path root = Path.of(System.getProperty("mceew.reactor.root"));
        Path sourceRoot = root.resolve("mceew-bungeecord/src/main/java");
        List<String> prohibited = List.of(
                "sun.misc." + "Unsafe",
                "jdk.internal.misc." + "Unsafe",
                "Reflection" + "Factory",
                "Class.forName",
                "getDeclaredField",
                "setAccessible",
                "net.md_5.bungee.protocol",
                "Protocolize",
                "LuckPerms",
                "org.bstats");
        try (Stream<Path> files = Files.walk(sourceRoot)) {
            for (Path file : (Iterable<Path>) files.filter(path ->
                    path.toString().endsWith(".java"))::iterator) {
                String source = Files.readString(file);
                for (String token : prohibited) {
                    assertFalse(source.contains(token), file + " contains " + token);
                }
            }
        }
    }

    @Test
    void existingPlatformAdaptersAreNotCopiedIntoModuleOutput() throws IOException {
        Path output = Path.of(System.getProperty("mceew.bungeecord.output"));
        assertFalse(Files.exists(output.resolve("jp/wolfx/mceew/velocity")));
        assertFalse(Files.exists(output.resolve("org/bukkit")));
        assertFalse(Files.exists(output.resolve("com/velocitypowered")));
        assertTrue(Files.isRegularFile(output.resolve(
                "jp/wolfx/mceew/bungeecord/MCEEWBungeeCord.class")));
    }

    @Test
    void testSourcesUseNoJdkInternalConstructionBypass() throws Exception {
        Path root = Path.of(System.getProperty("mceew.reactor.root"));
        Path testRoot = root.resolve("mceew-bungeecord/src/test/java");
        try (Stream<Path> files = Files.walk(testRoot)) {
            for (Path file : (Iterable<Path>) files.filter(path ->
                    path.toString().endsWith(".java"))::iterator) {
                String source = Files.readString(file);
                assertFalse(source.contains("sun.misc." + "Unsafe"), file.toString());
                assertFalse(source.contains("jdk.internal.misc." + "Unsafe"), file.toString());
                assertFalse(source.contains("Reflection" + "Factory"), file.toString());
            }
        }
    }

    @Test
    void phaseTwoRuntimeHasNoPlayerTargetPermissionOrDeliveryDependency() throws Exception {
        Path root = Path.of(System.getProperty("mceew.reactor.root"));
        List<Path> runtimeSources = List.of(
                root.resolve("mceew-bungeecord/src/main/java/jp/wolfx/mceew/"
                        + "BungeeMessageProcessor.java"),
                root.resolve("mceew-bungeecord/src/main/java/jp/wolfx/mceew/bungeecord/"
                        + "BungeeMceewRuntime.java"));
        List<String> prohibited = List.of(
                "ProxiedPlayer",
                "getPlayers(",
                "getServer(",
                "mceew.suppress.",
                "NotificationDispatcher",
                "sendMessage(",
                "sendTitle(");
        for (Path source : runtimeSources) {
            String content = Files.readString(source);
            for (String token : prohibited) {
                assertFalse(content.contains(token), source + " contains " + token);
            }
        }
    }
}
