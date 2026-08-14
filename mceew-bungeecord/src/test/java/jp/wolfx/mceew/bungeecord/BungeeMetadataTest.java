package jp.wolfx.mceew.bungeecord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

class BungeeMetadataTest {
    @Test
    void metadataUsesExpectedMainAndMavenVersion() throws Exception {
        Map<?, ?> metadata = metadata();

        assertEquals("MCEEW", metadata.get("name"));
        assertEquals("jp.wolfx.mceew.bungeecord.MCEEWBungeeCord", metadata.get("main"));
        assertEquals(System.getProperty("mceew.project.version"), metadata.get("version"));
        assertEquals("TenkyuChimata", metadata.get("author"));
        assertEquals("Minecraft Earthquake Early Warning", metadata.get("description"));
        assertFalse(metadata.containsKey("permission"));
        assertFalse(metadata.containsKey("depends"));
        assertFalse(metadata.containsKey("softDepends"));
    }

    @Test
    void rootReactorAndModuleDeclareSingleVersionAuthority() throws Exception {
        Path root = Path.of(System.getProperty("mceew.reactor.root"));
        String rootPom = Files.readString(root.resolve("pom.xml"));
        String modulePom = Files.readString(root.resolve("mceew-bungeecord/pom.xml"));

        assertTrue(rootPom.contains("<module>mceew-bungeecord</module>"));
        assertTrue(modulePom.contains("<version>${revision}</version>"));
        assertTrue(modulePom.contains("<finalName>MCEEW-BungeeCord-${project.version}</finalName>"));
        assertFalse(modulePom.contains("<version>"
                + System.getProperty("mceew.project.version") + "</version>"));
    }

    @Test
    void compileBaselineAndDependencyScopesAreFrozen() throws Exception {
        Path root = Path.of(System.getProperty("mceew.reactor.root"));
        String pom = Files.readString(root.resolve("mceew-bungeecord/pom.xml"));

        assertTrue(pom.contains("<java.version>11</java.version>"));
        assertTrue(pom.contains("<bungeecord.api.version>1.21-R0.4-SNAPSHOT"
                + "</bungeecord.api.version>"));
        assertTrue(pom.contains("<artifactId>bungeecord-api</artifactId>"));
        assertTrue(pom.contains("<scope>provided</scope>"));
        assertTrue(pom.contains("https://hub.spigotmc.org/nexus/repository/public/"));
        assertTrue(pom.contains("<artifactId>bstats-bungeecord</artifactId>"));
        assertTrue(pom.contains("<version>3.2.1</version>"));
        assertTrue(pom.contains("<pattern>org.bstats</pattern>"));
        assertTrue(pom.contains("<shadedPattern>jp.wolfx.mceew.bungeecord.libs.bstats"
                + "</shadedPattern>"));
        assertFalse(pom.contains("waterfall"));
        assertFalse(pom.contains("bstats-bukkit"));
        assertFalse(pom.contains("bstats-velocity"));
        assertFalse(pom.contains("luckperms"));
    }

    private Map<?, ?> metadata() throws IOException {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("bungee.yml")) {
            assertNotNull(input);
            LoaderOptions options = new LoaderOptions();
            options.setAllowDuplicateKeys(false);
            Object value = new Yaml(new SafeConstructor(options)).load(
                    new InputStreamReader(input, StandardCharsets.UTF_8));
            return (Map<?, ?>) value;
        }
    }
}
