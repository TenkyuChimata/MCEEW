package jp.wolfx.mceew.bungeecord;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.api.plugin.TabExecutor;

final class BungeeCommand extends Command implements TabExecutor {
    private static final List<String> INFO_SOURCES = List.of("jma", "cenc");
    private static final List<String> TEST_SOURCES =
            List.of("forecast", "alert", "sc", "fj", "cwa", "cenc", "cq");
    private static final String RUNTIME_UNAVAILABLE =
            "§e[MCEEW] MCEEW runtime is not currently available.";
    private static final String NO_PERMISSION =
            "§c[MCEEW] You do not have permission to use this command.";

    private final BungeeCommandService service;
    private final String version;
    private final Logger logger;

    BungeeCommand(BungeeCommandService service, String version, Logger logger) {
        super("eew", null, "mceew");
        this.service = Objects.requireNonNull(service, "service");
        this.version = Objects.requireNonNull(version, "version");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Override
    public void execute(CommandSender sender, String[] arguments) {
        Objects.requireNonNull(sender, "sender");
        Objects.requireNonNull(arguments, "arguments");
        try {
            executeSafely(sender, arguments);
        } catch (RuntimeException error) {
            logger.log(Level.SEVERE, "MCEEW BungeeCord command execution failed.", error);
            send(sender, "§c[MCEEW] Command could not be completed.");
        }
    }

    private void executeSafely(CommandSender sender, String[] arguments) {
        if (arguments.length == 0) {
            showRoot(sender);
            return;
        }
        switch (arguments[0].toLowerCase(Locale.ROOT)) {
            case "info":
                executeInfo(sender, arguments);
                break;
            case "test":
                executeTest(sender, arguments);
                break;
            case "reload":
                if (arguments.length == 1) {
                    executeReload(sender);
                } else {
                    send(sender, "§e[MCEEW] Usage: /eew reload");
                }
                break;
            default:
                send(sender, "§e[MCEEW] Unknown subcommand. Use /eew to view commands.");
                break;
        }
    }

    private void showRoot(CommandSender sender) {
        send(sender, "§a[MCEEW] Plugin version: v" + version);
        send(sender, "§a[MCEEW] Platform: BungeeCord / Waterfall");
        send(sender, "§a[MCEEW] §3/eew§a - Show available commands");
        send(sender, "§a[MCEEW] §3/eew info§a - Display latest earthquake information");
        if (hasAdminPermission(sender)) {
            send(sender, "§a[MCEEW] §3/eew test§a - Send a test EEW notification");
            send(sender, "§a[MCEEW] §3/eew reload§a - Reload plugin configuration");
        }
    }

    private void executeInfo(CommandSender sender, String[] arguments) {
        if (arguments.length != 2) {
            showInfoUsage(sender);
            return;
        }
        String information;
        if ("jma".equalsIgnoreCase(arguments[1])) {
            information = service.latestJmaEarthquakeInformation();
        } else if ("cenc".equalsIgnoreCase(arguments[1])) {
            information = service.latestCencEarthquakeInformation();
        } else {
            showInfoUsage(sender);
            return;
        }
        send(sender, information == null ? RUNTIME_UNAVAILABLE : information);
    }

    private void showInfoUsage(CommandSender sender) {
        send(sender,
                "§a[MCEEW] §3/eew info jma§a - Show Japan JMA earthquake information.");
        send(sender,
                "§a[MCEEW] §3/eew info cenc§a - Show China CENC earthquake information.");
    }

    private void executeTest(CommandSender sender, String[] arguments) {
        if (!hasAdminPermission(sender)) {
            send(sender, NO_PERMISSION);
            return;
        }
        if (arguments.length != 2) {
            showTestUsage(sender);
            return;
        }
        String source = arguments[1].toLowerCase(Locale.ROOT);
        if (!TEST_SOURCES.contains(source)) {
            showTestUsage(sender);
            return;
        }
        switch (service.dispatchTest(source)) {
            case DISPATCHED:
                break;
            case IN_PROGRESS:
                send(sender,
                        "§e[MCEEW] A configuration reload is in progress; try the test again.");
                break;
            case UNAVAILABLE:
                send(sender, RUNTIME_UNAVAILABLE);
                break;
            case FAILED:
                send(sender, "§c[MCEEW] Test notification could not be dispatched.");
                break;
            default:
                throw new IllegalStateException("Unhandled test outcome");
        }
    }

    private void showTestUsage(CommandSender sender) {
        send(sender,
                "§a[MCEEW] §3/eew test forecast§a - Send JMA forecast EEW test.");
        send(sender, "§a[MCEEW] §3/eew test alert§a - Send JMA alert EEW test.");
        send(sender, "§a[MCEEW] §3/eew test sc§a - Send Sichuan EEW test.");
        send(sender, "§a[MCEEW] §3/eew test fj§a - Send Taiwan/Fujian EEW test.");
        send(sender, "§a[MCEEW] §3/eew test cwa§a - Send Taiwan CWA EEW test.");
        send(sender, "§a[MCEEW] §3/eew test cenc§a - Send China CENC EEW test.");
        send(sender, "§a[MCEEW] §3/eew test cq§a - Send Chongqing EEW test.");
    }

    private void executeReload(CommandSender sender) {
        if (!hasAdminPermission(sender)) {
            send(sender, NO_PERMISSION);
            return;
        }
        service.requestReload(outcome -> {
            switch (outcome) {
                case SUCCESS:
                    send(sender, "§a[MCEEW] Configuration reloaded successfully.");
                    break;
                case IN_PROGRESS:
                    send(sender, "§e[MCEEW] Configuration reload is already in progress.");
                    break;
                case INVALID_CONFIG:
                    send(sender,
                            "§c[MCEEW] Configuration could not be read or validated; "
                                    + "the active state was preserved.");
                    break;
                case RUNTIME_FAILED:
                    send(sender,
                            "§c[MCEEW] Reloaded configuration could not be activated; "
                                    + "the active state was preserved.");
                    break;
                case FAILED:
                    send(sender,
                            "§c[MCEEW] Configuration reload failed; the active state was preserved.");
                    break;
                case UNAVAILABLE:
                    send(sender, RUNTIME_UNAVAILABLE);
                    break;
                default:
                    throw new IllegalStateException("Unhandled reload outcome: " + outcome);
            }
        });
    }

    @Override
    public Iterable<String> onTabComplete(CommandSender sender, String[] arguments) {
        Objects.requireNonNull(sender, "sender");
        Objects.requireNonNull(arguments, "arguments");
        try {
            if (arguments.length == 0) {
                return firstLevelSuggestions(sender, "");
            }
            if (arguments.length == 1) {
                return firstLevelSuggestions(sender, arguments[0]);
            }
            if (arguments.length == 2 && "info".equalsIgnoreCase(arguments[0])) {
                return matching(INFO_SOURCES, arguments[1]);
            }
            if (arguments.length == 2 && "test".equalsIgnoreCase(arguments[0])
                    && hasAdminPermission(sender)) {
                return matching(TEST_SOURCES, arguments[1]);
            }
            return List.of();
        } catch (RuntimeException error) {
            logger.log(Level.WARNING,
                    "MCEEW BungeeCord tab completion failed.", error);
            return List.of();
        }
    }

    private List<String> firstLevelSuggestions(CommandSender sender, String prefix) {
        List<String> candidates = new ArrayList<>();
        candidates.add("info");
        if (hasAdminPermission(sender)) {
            candidates.addAll(Arrays.asList("test", "reload"));
        }
        return matching(candidates, prefix);
    }

    private boolean hasAdminPermission(CommandSender sender) {
        try {
            return sender.hasPermission(BungeePermissions.ADMIN);
        } catch (RuntimeException error) {
            logger.log(Level.WARNING,
                    "MCEEW BungeeCord could not evaluate an administrative permission.", error);
            return false;
        }
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

    private void send(CommandSender sender, String legacy) {
        try {
            sender.sendMessage(TextComponent.fromLegacy(legacy));
        } catch (RuntimeException error) {
            logger.log(Level.WARNING,
                    "MCEEW BungeeCord could not send a command response.", error);
        }
    }
}
