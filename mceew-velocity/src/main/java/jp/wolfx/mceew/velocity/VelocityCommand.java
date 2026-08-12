package jp.wolfx.mceew.velocity;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.slf4j.Logger;

/** Concrete Velocity adapter for the established MCEEW command surface. */
final class VelocityCommand implements SimpleCommand {
    static final String ADMIN_PERMISSION = "mceew.admin";
    private static final String RUNTIME_UNAVAILABLE =
            "§c[MCEEW] Operational runtime is unavailable.";
    private static final LegacyComponentSerializer LEGACY =
            LegacyComponentSerializer.legacySection();
    private static final List<String> INFO_SOURCES = List.of("jma", "cenc");
    private static final List<String> TEST_SOURCES = List.of(
            "forecast", "alert", "sc", "fj", "cwa", "cenc", "cq");

    private final MCEEWVelocity plugin;
    private final Logger logger;

    VelocityCommand(MCEEWVelocity plugin, Logger logger) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Override
    public void execute(Invocation invocation) {
        Objects.requireNonNull(invocation, "invocation");
        try {
            execute(invocation.source(), invocation.arguments());
        } catch (RuntimeException error) {
            logger.error("MCEEW Velocity command execution failed.", error);
            send(invocation.source(), "§c[MCEEW] Command could not be completed.");
        }
    }

    private void execute(CommandSource source, String[] arguments) {
        if (arguments.length == 0) {
            sendRoot(source);
            return;
        }
        String subcommand = arguments[0].toLowerCase(Locale.ROOT);
        switch (subcommand) {
            case "reload":
                executeReload(source);
                break;
            case "info":
                executeInfo(source, arguments);
                break;
            case "test":
                executeTest(source, arguments);
                break;
            default:
                // Bukkit returns false here and has no configured usage text.
                break;
        }
    }

    private void sendRoot(CommandSource source) {
        send(source, "§a[MCEEW] Plugin version: v" +
                jp.wolfx.mceew.velocity.generated.VelocityBuildInfo.VERSION);
        send(source, "§a[MCEEW] §3/eew§a - Show available commands");
        send(source, "§a[MCEEW] §3/eew test§a - Send a test EEW alert");
        send(source, "§a[MCEEW] §3/eew info§a - Display latest earthquake information");
        send(source, "§a[MCEEW] §3/eew reload§a - Reload plugin configuration");
    }

    private void executeInfo(CommandSource source, String[] arguments) {
        if (arguments.length != 2) {
            send(source,
                    "§a[MCEEW] §3/eew info jma§a - Show Japan JMA earthquake information.");
            send(source,
                    "§a[MCEEW] §3/eew info cenc§a - Show China CENC earthquake information.");
            return;
        }
        String information;
        if ("jma".equalsIgnoreCase(arguments[1])) {
            information = plugin.latestJmaEarthquakeInformation();
        } else if ("cenc".equalsIgnoreCase(arguments[1])) {
            information = plugin.latestCencEarthquakeInformation();
        } else {
            return;
        }
        send(source, information == null ? RUNTIME_UNAVAILABLE : information);
    }

    private void executeTest(CommandSource source, String[] arguments) {
        if (!source.hasPermission(ADMIN_PERMISSION)) {
            return;
        }
        if (arguments.length != 2) {
            send(source,
                    "§a[MCEEW] §3/eew test forecast§a - Send JMA forecast EEW test.");
            send(source,
                    "§a[MCEEW] §3/eew test alert§a - Send JMA alert EEW test.");
            send(source, "§a[MCEEW] §3/eew test sc§a - Send Sichuan EEW test.");
            send(source, "§a[MCEEW] §3/eew test fj§a - Send Taiwan/Fujian EEW test.");
            send(source, "§a[MCEEW] §3/eew test cwa§a - Send Taiwan CWA EEW test.");
            send(source, "§a[MCEEW] §3/eew test cenc§a - Send China CENC EEW test.");
            send(source, "§a[MCEEW] §3/eew test cq§a - Send Chongqing EEW test.");
            return;
        }
        String sourceKey = arguments[1].toLowerCase(Locale.ROOT);
        if (!TEST_SOURCES.contains(sourceKey)) {
            return;
        }
        if (!plugin.dispatchTest(sourceKey)) {
            send(source, RUNTIME_UNAVAILABLE);
        }
    }

    private void executeReload(CommandSource source) {
        if (!source.hasPermission(ADMIN_PERMISSION)) {
            return;
        }
        plugin.requestReload(outcome -> {
            switch (outcome) {
                case SUCCESS:
                    send(source, "§a[MCEEW] Configuration reloaded successfully.");
                    break;
                case IN_PROGRESS:
                    send(source, "§e[MCEEW] Configuration reload is already in progress.");
                    break;
                case FAILED:
                    send(source,
                            "§c[MCEEW] Configuration reload failed; the existing file was left unchanged.");
                    break;
                case UNAVAILABLE:
                    send(source, RUNTIME_UNAVAILABLE);
                    break;
                default:
                    throw new IllegalStateException("Unhandled reload outcome: " + outcome);
            }
        });
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        Objects.requireNonNull(invocation, "invocation");
        String[] arguments = invocation.arguments();
        if (arguments.length == 0) {
            return firstLevelSuggestions(invocation.source(), "");
        }
        if (arguments.length == 1) {
            return firstLevelSuggestions(invocation.source(), arguments[0]);
        }
        if (arguments.length == 2 && "info".equalsIgnoreCase(arguments[0])) {
            return matching(INFO_SOURCES, arguments[1]);
        }
        if (arguments.length == 2 && "test".equalsIgnoreCase(arguments[0])
                && invocation.source().hasPermission(ADMIN_PERMISSION)) {
            return matching(TEST_SOURCES, arguments[1]);
        }
        return List.of();
    }

    private static List<String> firstLevelSuggestions(CommandSource source, String prefix) {
        List<String> candidates = new ArrayList<>();
        candidates.add("info");
        if (source.hasPermission(ADMIN_PERMISSION)) {
            candidates.add("test");
            candidates.add("reload");
        }
        return matching(candidates, prefix);
    }

    private static List<String> matching(List<String> candidates, String prefix) {
        String normalized = prefix.toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (String candidate : candidates) {
            if (candidate.startsWith(normalized)) {
                matches.add(candidate);
            }
        }
        return List.copyOf(matches);
    }

    private void send(CommandSource source, String legacy) {
        try {
            source.sendMessage(LEGACY.deserialize(legacy));
        } catch (RuntimeException error) {
            logger.warn("MCEEW Velocity could not send a command response.", error);
        }
    }
}
