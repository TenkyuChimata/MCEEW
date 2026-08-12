package jp.wolfx.mceew.velocity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class VelocityModulePurityTest {
    @Test
    void velocityProductionBytecodeIsJava17AndContainsNoBukkitFamilyReferences() throws Exception {
        Path output = Path.of(requiredSystemProperty("mceew.velocity.output"));
        List<Path> classes = classFiles(output);
        assertFalse(classes.isEmpty(), "No Velocity production classes were compiled");

        for (Path classFile : classes) {
            byte[] bytes = Files.readAllBytes(classFile);
            assertEquals(61, classMajor(bytes), classFile + " must target Java 17");
            assertContainsNone(bytes, classFile, List.of(
                    "org/bukkit",
                    "io/papermc",
                    "dev/folia",
                    "org/bstats",
                    "net/kyori/adventure",
                    "com/velocitypowered/api/proxy/Player",
                    "com/velocitypowered/api/proxy/server/RegisteredServer",
                    "getAllPlayers",
                    "hasPermission",
                    "sendMessage",
                    "showTitle",
                    "playSound"));
        }
    }

    @Test
    void coreBytecodeRemainsJava11AndFreeOfPlatformReferences() throws Exception {
        Path reactorRoot = Path.of(requiredSystemProperty("mceew.reactor.root"));
        Path output = reactorRoot.resolve("mceew-core/target/classes");
        List<Path> classes = classFiles(output);
        assertFalse(classes.isEmpty(), "No core production classes were compiled");

        for (Path classFile : classes) {
            byte[] bytes = Files.readAllBytes(classFile);
            assertEquals(55, classMajor(bytes), classFile + " must target Java 11");
            assertContainsNone(bytes, classFile, List.of(
                    "org/bukkit",
                    "io/papermc",
                    "dev/folia",
                    "com/velocitypowered",
                    "net/kyori/adventure",
                    "org/bstats",
                    "org/slf4j"));
        }
    }

    private static List<Path> classFiles(Path root) throws IOException {
        assertTrue(Files.isDirectory(root), "Missing classes directory: " + root);
        try (Stream<Path> paths = Files.walk(root)) {
            return paths.filter(path -> path.getFileName().toString().endsWith(".class"))
                    .collect(Collectors.toList());
        }
    }

    private static void assertContainsNone(byte[] bytes, Path classFile, List<String> forbidden) {
        String constantPool = new String(bytes, StandardCharsets.ISO_8859_1);
        for (String value : forbidden) {
            assertFalse(constantPool.contains(value), classFile + " references " + value);
        }
    }

    private static int classMajor(byte[] bytes) {
        return ((bytes[6] & 0xff) << 8) | (bytes[7] & 0xff);
    }

    private static String requiredSystemProperty(String name) {
        String value = System.getProperty(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Required Maven test property is missing: " + name);
        }
        return value;
    }
}
