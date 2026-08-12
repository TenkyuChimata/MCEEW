package jp.wolfx.mceew.velocity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import jp.wolfx.mceew.velocity.generated.VelocityBuildInfo;
import org.junit.jupiter.api.Test;

class VelocityMetadataTest {
    @Test
    void generatedMetadataUsesMavenProjectVersion() throws Exception {
        String projectVersion = requiredSystemProperty("mceew.project.version");
        InputStream resource = getClass().getClassLoader().getResourceAsStream("velocity-plugin.json");
        assertNotNull(resource, "Velocity annotation processor did not generate velocity-plugin.json");

        JsonObject metadata;
        try (InputStream input = resource;
             InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            metadata = JsonParser.parseReader(reader).getAsJsonObject();
        }

        assertEquals("mceew", metadata.get("id").getAsString());
        assertEquals("MCEEW", metadata.get("name").getAsString());
        assertEquals(projectVersion, metadata.get("version").getAsString());
        assertEquals("Minecraft Earthquake Early Warning", metadata.get("description").getAsString());
        assertEquals("TenkyuChimata", metadata.getAsJsonArray("authors").get(0).getAsString());
        assertEquals("jp.wolfx.mceew.velocity.MCEEWVelocity", metadata.get("main").getAsString());
        assertEquals(projectVersion, VelocityBuildInfo.VERSION);
    }

    private static String requiredSystemProperty(String name) {
        String value = System.getProperty(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Required Maven test property is missing: " + name);
        }
        return value;
    }
}
