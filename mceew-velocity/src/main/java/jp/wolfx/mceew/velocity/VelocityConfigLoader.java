package jp.wolfx.mceew.velocity;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Objects;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

final class VelocityConfigLoader {
    static final int CURRENT_PLATFORM_CONFIG_VERSION = 1;
    static final String CONFIG_FILE_NAME = "config.yml";

    private final Path dataDirectory;
    private final ClassLoader resourceLoader;

    VelocityConfigLoader(Path dataDirectory) {
        this(dataDirectory, MCEEWVelocity.class.getClassLoader());
    }

    VelocityConfigLoader(Path dataDirectory, ClassLoader resourceLoader) {
        this.dataDirectory = Objects.requireNonNull(dataDirectory, "dataDirectory");
        this.resourceLoader = Objects.requireNonNull(resourceLoader, "resourceLoader");
    }

    VelocityConfigSnapshot load() throws VelocityConfigException {
        Path configPath = dataDirectory.resolve(CONFIG_FILE_NAME);
        try {
            Files.createDirectories(dataDirectory);
            if (Files.notExists(configPath)) {
                createDefault(configPath);
            }
            if (!Files.isRegularFile(configPath)) {
                throw new VelocityConfigException("Velocity config is not a regular file: " + configPath);
            }
            return parse(configPath);
        } catch (VelocityConfigException error) {
            throw error;
        } catch (IOException error) {
            throw new VelocityConfigException("Unable to read Velocity config: " + configPath, error);
        }
    }

    private void createDefault(Path configPath) throws IOException, VelocityConfigException {
        Path temporary = Files.createTempFile(dataDirectory, CONFIG_FILE_NAME + ".", ".tmp");
        try {
            try (InputStream input = resourceLoader.getResourceAsStream(CONFIG_FILE_NAME)) {
                if (input == null) {
                    throw new VelocityConfigException("Bundled Velocity config is missing: " + CONFIG_FILE_NAME);
                }
                Files.copy(input, temporary, StandardCopyOption.REPLACE_EXISTING);
            }
            moveNewFile(temporary, configPath);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void moveNewFile(Path temporary, Path configPath) throws IOException {
        try {
            Files.move(temporary, configPath, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, configPath);
        } catch (FileAlreadyExistsException ignored) {
            // Another initializer created it first; load that complete file instead.
        }
    }

    private static VelocityConfigSnapshot parse(Path configPath)
            throws IOException, VelocityConfigException {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        Object document;
        try (Reader reader = Files.newBufferedReader(configPath, StandardCharsets.UTF_8)) {
            document = new Yaml(new SafeConstructor(options)).load(reader);
        } catch (YAMLException error) {
            throw new VelocityConfigException("Malformed Velocity config: " + configPath, error);
        }

        if (!(document instanceof Map)) {
            throw new VelocityConfigException("Velocity config root must be a mapping: " + configPath);
        }
        Map<?, ?> root = (Map<?, ?>) document;
        Object versionValue = root.get("platform-config-version");
        if (!(versionValue instanceof Integer)) {
            throw new VelocityConfigException("platform-config-version must be an integer");
        }
        int version = (Integer) versionValue;
        if (version != CURRENT_PLATFORM_CONFIG_VERSION) {
            throw new VelocityConfigException("Unsupported platform-config-version " + version
                    + "; expected " + CURRENT_PLATFORM_CONFIG_VERSION);
        }

        Map<?, ?> global = requireMapping(root, "global");
        boolean runtimeEnabled = optionalBoolean(global, "enabled", true, "global.enabled");
        Map<?, ?> sources = optionalMapping(global, "sources", "global.sources");
        boolean jmaEnabled = optionalBoolean(sources, "jma", true, "global.sources.jma");
        boolean sichuanEnabled = optionalBoolean(
                sources, "sichuan", true, "global.sources.sichuan");
        boolean fujianEnabled = optionalBoolean(
                sources, "fujian", true, "global.sources.fujian");
        boolean cwaEnabled = optionalBoolean(sources, "cwa", true, "global.sources.cwa");
        boolean cencEnabled = optionalBoolean(sources, "cenc", true, "global.sources.cenc");
        boolean chongqingEnabled = optionalBoolean(
                sources, "chongqing", true, "global.sources.chongqing");
        Map<?, ?> targets = requireMapping(root, "targets");
        Map<?, ?> defaultTarget = requireMapping(targets, "default");
        Object defaultMode = defaultTarget.get("mode");
        if (!(defaultMode instanceof String)
                || !("all".equals(defaultMode) || "selected".equals(defaultMode) || "none".equals(defaultMode))) {
            throw new VelocityConfigException(
                    "targets.default.mode must be 'all', 'selected', or 'none'");
        }
        requireMapping(targets, "sources");
        requireMapping(root, "groups");
        requireMapping(root, "servers");
        return new VelocityConfigSnapshot(
                version,
                runtimeEnabled,
                jmaEnabled,
                sichuanEnabled,
                fujianEnabled,
                cwaEnabled,
                cencEnabled,
                chongqingEnabled);
    }

    private static Map<?, ?> requireMapping(Map<?, ?> parent, String key)
            throws VelocityConfigException {
        Object value = parent.get(key);
        if (!(value instanceof Map)) {
            throw new VelocityConfigException(key + " must be a mapping");
        }
        return (Map<?, ?>) value;
    }

    private static Map<?, ?> optionalMapping(Map<?, ?> parent, String key, String path)
            throws VelocityConfigException {
        Object value = parent.get(key);
        if (!parent.containsKey(key)) {
            return Map.of();
        }
        if (!(value instanceof Map)) {
            throw new VelocityConfigException(path + " must be a mapping");
        }
        return (Map<?, ?>) value;
    }

    private static boolean optionalBoolean(
            Map<?, ?> parent, String key, boolean defaultValue, String path)
            throws VelocityConfigException {
        Object value = parent.get(key);
        if (!parent.containsKey(key)) {
            return defaultValue;
        }
        if (!(value instanceof Boolean)) {
            throw new VelocityConfigException(path + " must be a boolean");
        }
        return (Boolean) value;
    }
}
