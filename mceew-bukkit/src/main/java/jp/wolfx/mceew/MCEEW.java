package jp.wolfx.mceew;

import jp.wolfx.mceew.scheduler.PlatformScheduler;
import jp.wolfx.mceew.websocket.WebSocketConnectionManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.bstats.bukkit.Metrics;

import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.util.Hashtable;
import java.util.concurrent.TimeUnit;

public final class MCEEW extends JavaPlugin {
    private static final HttpClient client = HttpClient.newHttpClient();

    private String version;
    private PlatformScheduler platformScheduler;
    private WebSocketConnectionManager webSocketManager;
    private ConfigManager configManager;
    private final EarthquakeInfoCache earthquakeInfoCache = new EarthquakeInfoCache();
    private BukkitMceewRuntime runtime;
    private BukkitConfigurationReloader configurationReloader;
    private BukkitMceewCommand commandHandler;

    @Override
    public void onEnable() {
        version = getDescription().getVersion();
        platformScheduler = PlatformScheduler.create(this);
        BukkitNotificationDispatcher notificationDispatcher =
                new BukkitNotificationDispatcher(platformScheduler, getLogger());
        runtime = new BukkitMceewRuntime(earthquakeInfoCache, notificationDispatcher);
        configManager = ConfigManager.forPlugin(this);
        webSocketManager = new WebSocketConnectionManager(
                listener -> client.newWebSocketBuilder()
                        .buildAsync(URI.create("wss://ws-api.wolfx.jp/all_eew"), listener),
                (task, delay, unit) -> {
                    PlatformScheduler.TaskHandle handle =
                            platformScheduler.runAsyncDelayed(task, delay, unit);
                    return handle::cancel;
                },
                runtime::handleWebSocketMessage,
                getLogger(),
                5,
                TimeUnit.SECONDS
        );
        configurationReloader = new BukkitConfigurationReloader(
                configManager::prepareConfig,
                this::reloadConfig,
                () -> runtime.applyConfiguration(getConfig()),
                getLogger());
        commandHandler = new BukkitMceewCommand(
                version, runtime, configurationReloader, webSocketManager::restart);
        if (!configurationReloader.prepareAndApply()) {
            throw new IllegalStateException("Unable to prepare MCEEW configuration");
        }
        getLogger().info(platformScheduler.isFolia()
                ? "Using Folia API for scheduler."
                : "Using Bukkit API for scheduler.");
        webSocketManager.start();
        platformScheduler.runAsync(this::updater);
        new Metrics(this, 17261);
    }

    private String fetchVersionFromDnsTxt() throws Exception {
        Hashtable<String, String> env = new Hashtable<>();
        env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
        // 也可以指定 resolver，例如 Cloudflare：env.put("java.naming.provider.url", "dns://1.1.1.1");
        DirContext ctx = new InitialDirContext(env);

        Attributes attrs = ctx.getAttributes("mceew.mtf.edu.kg", new String[]{"TXT"});
        Attribute txt = attrs.get("TXT");
        if (txt == null || txt.size() == 0) return null;

        // 一个域名可能有多条 TXT，这里遍历找包含 version= 的那条
        for (int i = 0; i < txt.size(); i++) {
            String record = String.valueOf(txt.get(i));

            // JNDI 返回的 TXT 可能自带引号，先去掉
            record = record.replace("\"", "").trim();

            // 允许记录里包含多个键值，例如: foo=bar version=1.2.3
            // 但你目前是单值：version=1.2.3
            int idx = record.indexOf("version=");
            if (idx >= 0) {
                String v = record.substring(idx + "version=".length()).trim();

                // 如果后面还有空格/分号之类，切掉
                int cut = v.indexOf(' ');
                if (cut > 0) v = v.substring(0, cut);
                cut = v.indexOf(';');
                if (cut > 0) v = v.substring(0, cut);

                // 只保留数字和点（防御性）
                v = v.replaceAll("[^0-9.]", "");
                return v;
            }
        }
        return null;
    }

    private int compareSemver(String a, String b) {
        int[] av = parseSemver(a);
        int[] bv = parseSemver(b);

        int n = Math.max(av.length, bv.length);
        for (int i = 0; i < n; i++) {
            int ai = i < av.length ? av[i] : 0;
            int bi = i < bv.length ? bv[i] : 0;
            if (ai != bi) return Integer.compare(ai, bi);
        }
        return 0;
    }

    private int[] parseSemver(String v) {
        if (v == null) return new int[]{0, 0, 0};
        v = v.trim();

        // 防御：只留 x.y.z 数字点
        v = v.replaceAll("[^0-9.]", "");
        if (v.isEmpty()) return new int[]{0, 0, 0};

        String[] parts = v.split("\\.");
        int[] out = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                out[i] = Integer.parseInt(parts[i].isEmpty() ? "0" : parts[i]);
            } catch (NumberFormatException e) {
                out[i] = 0;
            }
        }
        return out;
    }

    private void updater() {
        try {
            // 1) 从 DNS TXT 读取版本号（格式：version=x.x.x）
            String apiVersion = fetchVersionFromDnsTxt(); // 例如 "2.6.2"
            if (apiVersion == null || apiVersion.isBlank()) {
                throw new IOException("Empty version from DNS TXT");
            }

            // 2) 本地版本号清洗（去掉 -bxxx 之类后缀）
            String localVersion = version.replaceAll("-b.*", "");

            // 3) 版本比较（语义化比较，避免 2.10.0 vs 2.6.9 这种出错）
            int cmp = compareSemver(apiVersion, localVersion);

            if (cmp > 0) {
                getLogger().warning("New plugin version v" + apiVersion
                        + " detected, Please download a new version from https://www.spigotmc.org/resources/mceew-earthquake-early-warning.104549/");
            } else {
                getLogger().info(String.format("Plugin is up to date. Current version: v%s", apiVersion));
            }

        } catch (Exception e) {
            getLogger().warning("Failed to check for plugin updates via DNS TXT.");
            getLogger().warning(String.valueOf(e));
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        return commandHandler.execute(sender, args);
    }

    @Override
    public void onDisable() {
        if (webSocketManager != null) {
            webSocketManager.stop();
        }
        if (platformScheduler != null) {
            platformScheduler.cancelTasks();
        }
    }
}
