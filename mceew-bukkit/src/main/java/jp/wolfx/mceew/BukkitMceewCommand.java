package jp.wolfx.mceew;

import org.bukkit.command.CommandSender;

import java.util.Objects;

/** Implements the existing Bukkit command behavior without requiring a JavaPlugin instance. */
final class BukkitMceewCommand {
    private final String version;
    private final BukkitMceewRuntime runtime;
    private final BukkitConfigurationReloader configurationReloader;
    private final Runnable restartWebSocket;

    BukkitMceewCommand(
            String version,
            BukkitMceewRuntime runtime,
            BukkitConfigurationReloader configurationReloader,
            Runnable restartWebSocket
    ) {
        this.version = Objects.requireNonNull(version, "version");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.configurationReloader = Objects.requireNonNull(
                configurationReloader, "configurationReloader");
        this.restartWebSocket = Objects.requireNonNull(restartWebSocket, "restartWebSocket");
    }

    boolean execute(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("§a[MCEEW] Plugin version: v" + version);
            sender.sendMessage("§a[MCEEW] §3/eew§a - Show available commands");
            sender.sendMessage("§a[MCEEW] §3/eew test§a - Send a test EEW alert");
            sender.sendMessage("§a[MCEEW] §3/eew info§a - Display latest earthquake information");
            sender.sendMessage("§a[MCEEW] §3/eew reload§a - Reload plugin configuration");
            return true;
        } else if (args[0].equalsIgnoreCase("reload") && sender.isOp()) {
            if (!configurationReloader.prepareAndApply()) {
                sender.sendMessage("§c[MCEEW] Configuration reload failed; the existing file was left unchanged.");
                return true;
            }
            restartWebSocket.run();
            sender.sendMessage("§a[MCEEW] Configuration reloaded successfully.");
            return true;
        } else if (args[0].equalsIgnoreCase("info")) {
            if (args.length == 2) {
                if (args[1].equalsIgnoreCase("jma")) {
                    sender.sendMessage(runtime.earthquakeInfo(false));
                    return true;
                } else if (args[1].equalsIgnoreCase("cenc")) {
                    sender.sendMessage(runtime.earthquakeInfo(true));
                    return true;
                }
            } else {
                sender.sendMessage("§a[MCEEW] §3/eew info jma§a - Show Japan JMA earthquake information.");
                sender.sendMessage("§a[MCEEW] §3/eew info cenc§a - Show China CENC earthquake information.");
                return true;
            }
        } else if (args[0].equalsIgnoreCase("test") && sender.isOp()) {
            if (args.length == 2) {
                if (args[1].equalsIgnoreCase("forecast")) {
                    runtime.eewTest(0);
                    return true;
                } else if (args[1].equalsIgnoreCase("alert")) {
                    runtime.eewTest(1);
                    return true;
                } else if (args[1].equalsIgnoreCase("sc")) {
                    runtime.eewTest(2);
                    return true;
                } else if (args[1].equalsIgnoreCase("fj")) {
                    runtime.eewTest(3);
                    return true;
                } else if (args[1].equalsIgnoreCase("cwa")) {
                    runtime.eewTest(4);
                    return true;
                } else if (args[1].equalsIgnoreCase("cenc")) {
                    runtime.eewTest(5);
                    return true;
                } else if (args[1].equalsIgnoreCase("cq")) {
                    runtime.eewTest(6);
                    return true;
                }
            } else {
                sender.sendMessage("§a[MCEEW] §3/eew test forecast§a - Send JMA forecast EEW test.");
                sender.sendMessage("§a[MCEEW] §3/eew test alert§a - Send JMA alert EEW test.");
                sender.sendMessage("§a[MCEEW] §3/eew test sc§a - Send Sichuan EEW test.");
                sender.sendMessage("§a[MCEEW] §3/eew test fj§a - Send Taiwan/Fujian EEW test.");
                sender.sendMessage("§a[MCEEW] §3/eew test cwa§a - Send Taiwan CWA EEW test.");
                sender.sendMessage("§a[MCEEW] §3/eew test cenc§a - Send China CENC EEW test.");
                sender.sendMessage("§a[MCEEW] §3/eew test cq§a - Send Chongqing EEW test.");
                return true;
            }
        }
        return false;
    }
}
