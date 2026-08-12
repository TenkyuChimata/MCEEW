package jp.wolfx.mceew;

import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompatibilityTest {
    private static final Path MAIN_CLASSES = Path.of("target", "classes", "jp", "wolfx", "mceew");

    @Test
    void pluginClassesInitializeOnTheSpigotOnlyTestClasspath() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        assertEquals("jp.wolfx.mceew.MCEEW",
                Class.forName("jp.wolfx.mceew.MCEEW", true, classLoader).getName());
        assertEquals("jp.wolfx.mceew.scheduler.FoliaPlatformScheduler",
                Class.forName(
                        "jp.wolfx.mceew.scheduler.FoliaPlatformScheduler", true, classLoader
                ).getName());
    }

    @Test
    void productionBytecodeTargetsJava11WithoutDirectPaperOrFoliaLinkage() throws IOException {
        for (Path classFile : productionClassFiles()) {
            byte[] bytecode = Files.readAllBytes(classFile);
            int majorVersion = ((bytecode[6] & 0xff) << 8) | (bytecode[7] & 0xff);
            assertEquals(55, majorVersion, classFile + " must target Java 11");

            String symbols = new String(bytecode, StandardCharsets.ISO_8859_1);
            assertFalse(symbols.contains("net/kyori/adventure/"),
                    classFile + " directly links Kyori Adventure");
            assertFalse(symbols.contains("io/papermc/paper/"),
                    classFile + " directly links Paper or Folia");
        }
    }

    @Test
    void pluginDescriptorKeepsCompatibilityAndCommandDeclarations() throws IOException {
        String descriptor = Files.readString(Path.of("target", "classes", "plugin.yml"));
        assertTrue(descriptor.lines().anyMatch(line -> line.equals(
                "version: " + MceewCharacterizationSupport.projectVersion())));
        assertTrue(descriptor.contains("api-version: 1.13"));
        assertTrue(descriptor.contains("folia-supported: true"));
        assertTrue(descriptor.contains("  eew:"));
        assertTrue(descriptor.contains("  mceew:"));
        assertTrue(descriptor.contains("mceew.notify.all:"));
    }

    @Test
    void foliaProfileExposesAllReflectedSchedulerSignatures() throws Exception {
        Class<?> asyncScheduler = optionalClass(
                "io.papermc.paper.threadedregions.scheduler.AsyncScheduler");
        if (asyncScheduler == null) {
            return;
        }

        Class<?> globalScheduler = Class.forName(
                "io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler");
        Class<?> entityScheduler = Class.forName(
                "io.papermc.paper.threadedregions.scheduler.EntityScheduler");
        Class<?> scheduledTask = Class.forName(
                "io.papermc.paper.threadedregions.scheduler.ScheduledTask");

        Method runNow = asyncScheduler.getMethod("runNow", Plugin.class, Consumer.class);
        Method runDelayed = asyncScheduler.getMethod(
                "runDelayed", Plugin.class, Consumer.class, long.class, TimeUnit.class);
        Method cancelAsync = asyncScheduler.getMethod("cancelTasks", Plugin.class);
        Method cancelScheduled = scheduledTask.getMethod("cancel");
        Method executeGlobal = globalScheduler.getMethod("execute", Plugin.class, Runnable.class);
        Method cancelGlobal = globalScheduler.getMethod("cancelTasks", Plugin.class);
        Method runEntity = entityScheduler.getMethod(
                "run", Plugin.class, Consumer.class, Runnable.class);

        assertEquals("runNow", runNow.getName());
        assertEquals("runDelayed", runDelayed.getName());
        assertEquals("cancelTasks", cancelAsync.getName());
        assertEquals("cancel", cancelScheduled.getName());
        assertEquals("execute", executeGlobal.getName());
        assertEquals("cancelTasks", cancelGlobal.getName());
        assertEquals("run", runEntity.getName());
    }

    private Class<?> optionalClass(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException ignored) {
            return null;
        }
    }

    private List<Path> productionClassFiles() throws IOException {
        try (Stream<Path> files = Files.walk(MAIN_CLASSES)) {
            return files.filter(path -> path.toString().endsWith(".class"))
                    .collect(Collectors.toList());
        }
    }
}
