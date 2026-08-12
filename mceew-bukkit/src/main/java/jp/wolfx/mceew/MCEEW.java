package jp.wolfx.mceew;

import com.google.gson.JsonObject;
import jp.wolfx.mceew.message.FujianEewEvent;
import jp.wolfx.mceew.message.JmaEewEvent;
import jp.wolfx.mceew.message.RegionalEewEvent;
import jp.wolfx.mceew.message.WolfxMessageRouter;
import jp.wolfx.mceew.scheduler.PlatformScheduler;
import jp.wolfx.mceew.websocket.WebSocketConnectionManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bstats.bukkit.Metrics;

import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Hashtable;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.net.http.HttpClient;
import java.util.function.Consumer;
import java.util.regex.Pattern;

public final class MCEEW extends JavaPlugin {
    private static final WolfxMessageRouter MESSAGE_ROUTER = new WolfxMessageRouter();
    private static final Pattern SOUND_KEY_PATTERN = Pattern.compile(
            "(?:[a-z0-9._-]+:)?[a-z0-9/._-]+"
    );
    private boolean jpEewBoolean;
    private boolean scEewBoolean;
    private boolean fjEewBoolean;
    private boolean cwaEewBoolean;
    private boolean cencEewBoolean;
    private boolean cqEewBoolean;
    private boolean broadcastBool;
    private boolean titleBool;
    private boolean alertBool;
    private boolean jmaEqlistBoolean;
    private boolean cencEqlistBoolean;
    private String timeFormat;
    private String alertBroadcastMessage;
    private String alertTitleMessage;
    private String alertSubtitleMessage;
    private String forecastBroadcastMessage;
    private String forecastTitleMessage;
    private String forecastSubtitleMessage;
    private String jmaEqlistBroadcastMessage;
    private String cencEqlistBroadcastMessage;
    private String sichuanBroadcastMessage;
    private String sichuanTitleMessage;
    private String sichuanSubtitleMessage;
    private String fjBroadcastMessage;
    private String fjTitleMessage;
    private String fjSubtitleMessage;
    private String cwaBroadcastMessage;
    private String cwaTitleMessage;
    private String cwaSubtitleMessage;
    private String cencBroadcastMessage;
    private String cencTitleMessage;
    private String cencSubtitleMessage;
    private String cqBroadcastMessage;
    private String cqTitleMessage;
    private String cqSubtitleMessage;
    private String alertAlertSoundType;
    private double alertAlertSoundVolume;
    private double alertAlertSoundPitch;
    private String forecastAlertSoundType;
    private double forecastAlertSoundVolume;
    private double forecastAlertSoundPitch;
    private String scAlertSoundType;
    private double scAlertSoundVolume;
    private double scAlertSoundPitch;
    private String fjAlertSoundType;
    private double fjAlertSoundVolume;
    private double fjAlertSoundPitch;
    private String cwaAlertSoundType;
    private double cwaAlertSoundVolume;
    private double cwaAlertSoundPitch;
    private String cencAlertSoundType;
    private double cencAlertSoundVolume;
    private double cencAlertSoundPitch;
    private String cqAlertSoundType;
    private double cqAlertSoundVolume;
    private double cqAlertSoundPitch;
    private final EarthquakeInfoCache earthquakeInfoCache = new EarthquakeInfoCache();
    private String version;
    private static final HttpClient client = HttpClient.newHttpClient();
    private PlatformScheduler platformScheduler;
    private WebSocketConnectionManager webSocketManager;
    private ConfigManager configManager;
    // TEST SEAM: null in production; lets characterization tests observe console output
    // without booting a Bukkit server.
    private Consumer<String> consoleMessageObserver;

    @Override
    public void onEnable() {
        version = getDescription().getVersion();
        platformScheduler = PlatformScheduler.create(this);
        configManager = ConfigManager.forPlugin(this);
        webSocketManager = new WebSocketConnectionManager(
                listener -> client.newWebSocketBuilder()
                        .buildAsync(URI.create("wss://ws-api.wolfx.jp/all_eew"), listener),
                (task, delay, unit) -> {
                    PlatformScheduler.TaskHandle handle =
                            platformScheduler.runAsyncDelayed(task, delay, unit);
                    return handle::cancel;
                },
                this::handleWebSocketMessage,
                getLogger(),
                5,
                TimeUnit.SECONDS
        );
        if (!prepareAndLoadConfiguration()) {
            throw new IllegalStateException("Unable to prepare MCEEW configuration");
        }
        getLogger().info(platformScheduler.isFolia()
                ? "Using Folia API for scheduler."
                : "Using Bukkit API for scheduler.");
        webSocketManager.start();
        platformScheduler.runAsync(this::updater);
        new Metrics(this, 17261);
    }

    private void eewTest(int flag) {
        if (flag == 1) {
            String flags = "警報";
            String originTimeStr = "2024/01/01 16:10:08";
            String reportTime = "2024/01/01 16:14:18";
            String num = "46";
            String lat = "37.6";
            String lon = "137.2";
            String region = "能登半島沖";
            String mag = "7.4";
            String depth = "10km";
            String shindo = "7";
            String type = "最終報";
            String originTime = getDate("yyyy/MM/dd HH:mm:ss", timeFormat, "Asia/Tokyo", originTimeStr);
            jmaEewAction(flags, reportTime, originTime, num, lat, lon, region, mag, depth, getShindoColor(shindo), type);
        } else if (flag == 2) {
            String originTimeStr = "2024-02-28 21:23:30";
            String reportTime = "2024-02-28 21:23:37";
            String num = "1";
            String lat = "29.3";
            String lon = "102.82";
            String region = "四川雅安市汉源县";
            String mag = "3.3";
            String depth = "10km";
            String intensity = "5";
            String originTime = getDate("yyyy-MM-dd HH:mm:ss", timeFormat, "Asia/Shanghai", originTimeStr);
            scEewAction(reportTime, originTime, num, lat, lon, region, mag, depth, getIntensityColor(intensity));
        } else if (flag == 3) {
            String originTimeStr = "2024-02-29 13:26:28";
            String reportTime = "2024-02-29 13:27:40";
            String num = "4";
            String lat = "23.47";
            String lon = "120.26";
            String region = "台湾嘉义县";
            String mag = "4.4";
            String type = "最終報";
            String originTime = getDate("yyyy-MM-dd HH:mm:ss", timeFormat, "Asia/Shanghai", originTimeStr);
            fjEewAction(reportTime, originTime, num, lat, lon, region, mag, type);
        } else if (flag == 4) {
            String originTimeStr = "2024-04-03 07:58:10";
            String reportTime = "2024-04-03 07:58:27";
            String num = "2";
            String lat = "23.89";
            String lon = "121.56";
            String region = "花蓮縣壽豐鄉";
            String mag = "6.8";
            String depth = "20km";
            String shindo = "6弱";
            String originTime = getDate("yyyy-MM-dd HH:mm:ss", timeFormat, "Asia/Shanghai", originTimeStr);
            cwaEewAction(reportTime, originTime, num, lat, lon, region, mag, depth, getShindoColor(shindo));
        } else if (flag == 5) {
            String originTimeStr = "2025-09-12 05:50:58";
            String reportTime = "2025-09-12 05:50:58";
            String num = "1";
            String lat = "33.002";
            String lon = "102.89";
            String region = "四川阿坝州红原县";
            String mag = "4.4";
            String depth = "5km";
            String intensity = "6.1";
            String originTime = getDate("yyyy-MM-dd HH:mm:ss", timeFormat, "Asia/Shanghai", originTimeStr);
            cencEewAction(reportTime, originTime, num, lat, lon, region, mag, depth, getIntensityColor(intensity));
        } else if (flag == 6) {
            String originTimeStr = "2026-08-07 13:08:30";
            String reportTime = "2026-08-07 13:08:30";
            String num = "1";
            String lat = "28.517";
            String lon = "104.673";
            String region = "四川宜宾市高县";
            String mag = "4.8";
            String depth = "4km";
            String intensity = "6.6";
            String originTime = getDate("yyyy-MM-dd HH:mm:ss", timeFormat, "Asia/Shanghai", originTimeStr);
            cqEewAction(reportTime, originTime, num, lat, lon, region, mag, depth, getIntensityColor(intensity));
        } else {
            String flags = "予報";
            String originTimeStr = "2024/02/29 18:35:38";
            String reportTime = "2024/02/29 18:36:36";
            String num = "6";
            String lat = "35.4";
            String lon = "140.6";
            String region = "千葉県東方沖";
            String mag = "4.7";
            String depth = "10km";
            String shindo = "3";
            String type = "";
            String originTime = getDate("yyyy/MM/dd HH:mm:ss", timeFormat, "Asia/Tokyo", originTimeStr);
            jmaEewAction(flags, reportTime, originTime, num, lat, lon, region, mag, depth, getShindoColor(shindo), type);
        }
        broadcastMessage();
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

    private boolean canReceive(Player player, String node) {
        return player.hasPermission("mceew.notify.all") && player.hasPermission(node);
    }

    private void sendConsoleMessage(String message) {
        if (consoleMessageObserver != null) {
            consoleMessageObserver.accept(message);
            return;
        }
        platformScheduler.runGlobal(() -> Bukkit.getConsoleSender().sendMessage(message));
    }

    // TEST SEAM: package-private and behavior-neutral unless explicitly installed by a test.
    void observeConsoleMessages(Consumer<String> observer) {
        consoleMessageObserver = observer;
    }

    private void forEachPlayer(Consumer<Player> action) {
        platformScheduler.forEachPlayer(action);
    }

    private void broadcastMessage() {
        sendConsoleMessage("§eWarning: This is an Earthquake Early Warning test.");
        forEachPlayer(player -> player.sendMessage("§eWarning: This is an Earthquake Early Warning test."));
    }

    private String getDate(String pattern, String timeFormat, String timezone, String originTime) {
        DateTimeFormatter originTime1 = DateTimeFormatter.ofPattern(pattern);
        ZonedDateTime originTime2 = ZonedDateTime.parse(originTime, originTime1.withZone(ZoneId.of(timezone)));
        return originTime2.format(DateTimeFormatter.ofPattern(timeFormat));
    }

    private void playSound(String alertSoundType, double alertSoundVolume, double alertSoundPitch, Player player) {
        if (alertSoundType == null || !SOUND_KEY_PATTERN.matcher(alertSoundType).matches()) {
            getLogger().warning("Unknown sound type: " + alertSoundType);
            return;
        }
        try {
            player.playSound(
                    player.getLocation(),
                    alertSoundType,
                    (float) alertSoundVolume,
                    (float) alertSoundPitch
            );
        } catch (IllegalArgumentException exception) {
            getLogger().warning("Unknown sound type: " + alertSoundType);
        }
    }

    private boolean isFresh(String reportTimeStr, String pattern, ZoneId zone) {
        return isFresh(reportTimeStr, pattern, zone, ZonedDateTime.now(zone));
    }

    // TEST SEAM: explicit time input makes the existing freshness calculation deterministic.
    boolean isFresh(
            String reportTimeStr, String pattern, ZoneId zone, ZonedDateTime now) {
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
            LocalDateTime reportTime = LocalDateTime.parse(reportTimeStr, formatter);
            ZonedDateTime reportZdt = reportTime.atZone(zone);
            long diff = Math.abs(Duration.between(reportZdt, now).toMinutes());
            return diff <= 10;
        } catch (Exception e) {
            return true;
        }
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

    private void handleWebSocketMessage(String message) {
        WolfxMessageRouter.RoutedMessage routed = MESSAGE_ROUTER.route(message);
        switch (routed.getType()) {
            case JMA_EEW:
                if (jpEewBoolean) {
                    jmaEewExecute((JmaEewEvent) MESSAGE_ROUTER.parseRealtime(routed));
                }
                break;
            case JMA_EARTHQUAKE_LIST:
                jmaEqlistExecute(routed.getPayload(), jmaEqlistBoolean);
                break;
            case SICHUAN_EEW:
                if (scEewBoolean) {
                    scEewExecute((RegionalEewEvent) MESSAGE_ROUTER.parseRealtime(routed));
                }
                break;
            case FUJIAN_EEW:
                if (fjEewBoolean) {
                    fjEewExecute((FujianEewEvent) MESSAGE_ROUTER.parseRealtime(routed));
                }
                break;
            case CWA_EEW:
                if (cwaEewBoolean) {
                    cwaEewExecute((RegionalEewEvent) MESSAGE_ROUTER.parseRealtime(routed));
                }
                break;
            case CENC_EEW:
                if (cencEewBoolean) {
                    cencEewExecute((RegionalEewEvent) MESSAGE_ROUTER.parseRealtime(routed));
                }
                break;
            case CHONGQING_EEW:
                if (cqEewBoolean) {
                    cqEewExecute((RegionalEewEvent) MESSAGE_ROUTER.parseRealtime(routed));
                }
                break;
            case CENC_EARTHQUAKE_LIST:
                cencEqlistExecute(routed.getPayload(), cencEqlistBoolean);
                break;
            case HEARTBEAT:
            case UNKNOWN:
                break;
        }
    }

    private void jmaEewExecute(JmaEewEvent event) {
        String type = "";
        String flag = event.getFlag();
        String reportTime = event.getReportTime();
        String num = event.getReportNumber();
        String lat = event.getLatitude();
        String lon = event.getLongitude();
        String region = event.getRegion();
        String mag = event.getMagnitude();
        String depth = event.getDepth() + "km";
        String shindo = event.getMaximumIntensity();
        String originTime = getDate("yyyy/MM/dd HH:mm:ss", timeFormat, "Asia/Tokyo", event.getOriginTime());
        if (event.isTraining()) {
            type = "訓練";
        } else if (event.isAssumption()) {
            type = "仮定震源";
        }
        if (event.isFinalReport()) {
            if (!type.isEmpty()) {
                type = type + " (最終報)";
            } else {
                type = "最終報";
            }
        }
        if (event.isCancelled()) {
            type = "取消";
        }
        if (isFresh(reportTime, "yyyy/MM/dd HH:mm:ss", ZoneId.of("Asia/Tokyo"))) {
            jmaEewAction(flag, reportTime, originTime, num, lat, lon, region, mag, depth, getShindoColor(shindo), type);
        }
    }

    private void jmaEqlistExecute(JsonObject data, boolean enabled) {
        JsonObject latest = data.get("No1").getAsJsonObject();
        String timeStr = latest.get("time_full").getAsString();
        String region = latest.get("location").getAsString();
        String mag = latest.get("magnitude").getAsString();
        String depth = latest.get("depth").getAsString();
        String latitude = latest.get("latitude").getAsString();
        String longitude = latest.get("longitude").getAsString();
        String shindo = latest.get("shindo").getAsString();
        String info = latest.get("info").getAsString();
        String originTime = getDate("yyyy/MM/dd HH:mm:ss", timeFormat, "Asia/Tokyo", timeStr);
        EarthquakeInfoCache.JmaSnapshot snapshot = new EarthquakeInfoCache.JmaSnapshot(
                data.get("md5").getAsString(), originTime, region, mag, depth,
                latitude, longitude, getShindoColor(shindo), info);
        EarthquakeInfoCache.UpdateResult update = earthquakeInfoCache.updateJma(snapshot);
        if (update.shouldNotify(enabled)) {
            String formatted = snapshot.format(jmaEqlistBroadcastMessage);
            sendConsoleMessage(formatted);
            forEachPlayer(player -> {
                if (canReceive(player, "mceew.notify.jma.eqlist")) {
                    player.sendMessage(formatted);
                }
            });
        }
    }

    private void cencEqlistExecute(JsonObject data, boolean enabled) {
        JsonObject latest = data.get("No1").getAsJsonObject();
        String timeStr = latest.get("time").getAsString();
        String originTime = getDate("yyyy-MM-dd HH:mm:ss", timeFormat, "Asia/Shanghai", timeStr);
        String intensity = latest.get("intensity").getAsString();
        EarthquakeInfoCache.CencSnapshot snapshot = EarthquakeInfoCache.CencSnapshot.fromEqlist(
                data, originTime, getIntensityColor(intensity));
        EarthquakeInfoCache.UpdateResult update = earthquakeInfoCache.updateCenc(snapshot);
        if (update.shouldNotify(enabled)) {
            String formatted = snapshot.format(cencEqlistBroadcastMessage);
            sendConsoleMessage(formatted);
            forEachPlayer(player -> {
                if (canReceive(player, "mceew.notify.cenc.eqlist")) {
                    player.sendMessage(formatted);
                }
            });
        }
    }

    private void scEewExecute(RegionalEewEvent event) {
        String reportTime = event.getReportTime();
        String num = event.getReportNumber();
        String lat = event.getLatitude();
        String lon = event.getLongitude();
        String region = event.getRegion();
        String mag = event.getMagnitude();
        String intensity = event.getMaximumIntensity();
        String depth = event.getDepth() + "km";
        String originTime = getDate("yyyy-MM-dd HH:mm:ss", timeFormat, "Asia/Shanghai", event.getOriginTime());
        if (isFresh(reportTime, "yyyy-MM-dd HH:mm:ss", ZoneId.of("Asia/Shanghai"))) {
            scEewAction(reportTime, originTime, num, lat, lon, region, mag, depth, getIntensityColor(intensity));
        }
    }

    private void fjEewExecute(FujianEewEvent event) {
        String type = "";
        String reportTime = event.getReportTime();
        String num = event.getReportNumber();
        String lat = event.getLatitude();
        String lon = event.getLongitude();
        String region = event.getRegion();
        String mag = event.getMagnitude();
        String originTime = getDate("yyyy-MM-dd HH:mm:ss", timeFormat, "Asia/Shanghai", event.getOriginTime());
        if (event.isFinalReport()) {
            type = "最終報";
        }
        if (isFresh(reportTime, "yyyy-MM-dd HH:mm:ss", ZoneId.of("Asia/Shanghai"))) {
            fjEewAction(reportTime, originTime, num, lat, lon, region, mag, type);
        }
    }

    private void cwaEewExecute(RegionalEewEvent event) {
        String reportTime = event.getReportTime();
        String num = event.getReportNumber();
        String lat = event.getLatitude();
        String lon = event.getLongitude();
        String region = event.getRegion();
        String mag = event.getMagnitude();
        String depth = event.getDepth() + "km";
        String shindo = event.getMaximumIntensity();
        String originTime = getDate("yyyy-MM-dd HH:mm:ss", timeFormat, "Asia/Shanghai", event.getOriginTime());
        if (isFresh(reportTime, "yyyy-MM-dd HH:mm:ss", ZoneId.of("Asia/Shanghai"))) {
            cwaEewAction(reportTime, originTime, num, lat, lon, region, mag, depth, getShindoColor(shindo));
        }
    }

    private void cencEewExecute(RegionalEewEvent event) {
        String reportTime = event.getReportTime();
        String num = event.getReportNumber();
        String lat = event.getLatitude();
        String lon = event.getLongitude();
        String region = event.getRegion();
        String mag = event.getMagnitude();
        String intensity = event.getMaximumIntensity();
        String depth = event.getDepth() + "km";
        String originTime = getDate("yyyy-MM-dd HH:mm:ss", timeFormat, "Asia/Shanghai", event.getOriginTime());
        if (isFresh(reportTime, "yyyy-MM-dd HH:mm:ss", ZoneId.of("Asia/Shanghai"))) {
            cencEewAction(reportTime, originTime, num, lat, lon, region, mag, depth, getIntensityColor(intensity));
        }
    }

    private void cqEewExecute(RegionalEewEvent event) {
        String reportTime = event.getReportTime();
        String num = event.getReportNumber();
        String lat = event.getLatitude();
        String lon = event.getLongitude();
        String region = event.getRegion();
        String mag = event.getMagnitude();
        String intensity = event.getMaximumIntensity();
        String depth = event.getDepth() + "km";
        String originTime = getDate("yyyy-MM-dd HH:mm:ss", timeFormat, "Asia/Shanghai", event.getOriginTime());
        if (isFresh(reportTime, "yyyy-MM-dd HH:mm:ss", ZoneId.of("Asia/Shanghai"))) {
            cqEewAction(reportTime, originTime, num, lat, lon, region, mag, depth, getIntensityColor(intensity));
        }
    }

    private void getEewInfo(Boolean flag, CommandSender sender) {
        sender.sendMessage(flag
                ? earthquakeInfoCache.formatCenc(cencEqlistBroadcastMessage)
                : earthquakeInfoCache.formatJma(jmaEqlistBroadcastMessage));
    }

    private void jmaEewAction(String flag, String reportTime, String originTime, String num, String lat, String lon, String region, String mag, String depth, String shindo, String type) {
        if (broadcastBool) {
            if (Objects.equals(flag, "警報")) {
                sendConsoleMessage(
                        alertBroadcastMessage.
                                replaceAll("%flag%", flag).
                                replaceAll("%report_time%", reportTime).
                                replaceAll("%origin_time%", originTime).
                                replaceAll("%num%", num).
                                replaceAll("%lat%", lat).
                                replaceAll("%lon%", lon).
                                replaceAll("%region%", region).
                                replaceAll("%mag%", mag).
                                replaceAll("%depth%", depth).
                                replaceAll("%shindo%", shindo).
                                replaceAll("%type%", type)
                );
            } else {
                sendConsoleMessage(
                        forecastBroadcastMessage.
                                replaceAll("%flag%", flag).
                                replaceAll("%report_time%", reportTime).
                                replaceAll("%origin_time%", originTime).
                                replaceAll("%num%", num).
                                replaceAll("%lat%", lat).
                                replaceAll("%lon%", lon).
                                replaceAll("%region%", region).
                                replaceAll("%mag%", mag).
                                replaceAll("%depth%", depth).
                                replaceAll("%shindo%", shindo).
                                replaceAll("%type%", type)
                );
            }
        }
        forEachPlayer(player -> {
            if (broadcastBool) {
                if (Objects.equals(flag, "警報")) {
                    if (canReceive(player, "mceew.notify.jma.alert")) {
                        player.sendMessage(
                                alertBroadcastMessage.
                                        replaceAll("%flag%", flag).
                                        replaceAll("%report_time%", reportTime).
                                        replaceAll("%origin_time%", originTime).
                                        replaceAll("%num%", num).
                                        replaceAll("%lat%", lat).
                                        replaceAll("%lon%", lon).
                                        replaceAll("%region%", region).
                                        replaceAll("%mag%", mag).
                                        replaceAll("%depth%", depth).
                                        replaceAll("%shindo%", shindo).
                                        replaceAll("%type%", type)
                        );
                    }
                } else {
                    if (canReceive(player, "mceew.notify.jma.forecast")) {
                        player.sendMessage(
                                forecastBroadcastMessage.
                                        replaceAll("%flag%", flag).
                                        replaceAll("%report_time%", reportTime).
                                        replaceAll("%origin_time%", originTime).
                                        replaceAll("%num%", num).
                                        replaceAll("%lat%", lat).
                                        replaceAll("%lon%", lon).
                                        replaceAll("%region%", region).
                                        replaceAll("%mag%", mag).
                                        replaceAll("%depth%", depth).
                                        replaceAll("%shindo%", shindo).
                                        replaceAll("%type%", type)
                        );
                    }
                }
            }
            if (titleBool) {
                if (Objects.equals(flag, "警報")) {
                    if (canReceive(player, "mceew.notify.jma.alert")) {
                        player.sendTitle(
                                alertTitleMessage.
                                        replaceAll("%flag%", flag).
                                        replaceAll("%report_time%", reportTime).
                                        replaceAll("%origin_time%", originTime).
                                        replaceAll("%num%", num).
                                        replaceAll("%lat%", lat).
                                        replaceAll("%lon%", lon).
                                        replaceAll("%region%", region).
                                        replaceAll("%mag%", mag).
                                        replaceAll("%depth%", depth).
                                        replaceAll("%shindo%", shindo).
                                        replaceAll("%type%", type),
                                alertSubtitleMessage.
                                        replaceAll("%flag%", flag).
                                        replaceAll("%report_time%", reportTime).
                                        replaceAll("%origin_time%", originTime).
                                        replaceAll("%num%", num).
                                        replaceAll("%lat%", lat).
                                        replaceAll("%lon%", lon).
                                        replaceAll("%region%", region).
                                        replaceAll("%mag%", mag).
                                        replaceAll("%depth%", depth).
                                        replaceAll("%shindo%", shindo).
                                        replaceAll("%type%", type),
                                10, 70, 20
                        );
                    }
                } else {
                    if (canReceive(player, "mceew.notify.jma.forecast")) {
                        player.sendTitle(
                                forecastTitleMessage.
                                        replaceAll("%flag%", flag).
                                        replaceAll("%report_time%", reportTime).
                                        replaceAll("%origin_time%", originTime).
                                        replaceAll("%num%", num).
                                        replaceAll("%lat%", lat).
                                        replaceAll("%lon%", lon).
                                        replaceAll("%region%", region).
                                        replaceAll("%mag%", mag).
                                        replaceAll("%depth%", depth).
                                        replaceAll("%shindo%", shindo).
                                        replaceAll("%type%", type),
                                forecastSubtitleMessage.
                                        replaceAll("%flag%", flag).
                                        replaceAll("%report_time%", reportTime).
                                        replaceAll("%origin_time%", originTime).
                                        replaceAll("%num%", num).
                                        replaceAll("%lat%", lat).
                                        replaceAll("%lon%", lon).
                                        replaceAll("%region%", region).
                                        replaceAll("%mag%", mag).
                                        replaceAll("%depth%", depth).
                                        replaceAll("%shindo%", shindo).
                                        replaceAll("%type%", type),
                                10, 70, 20
                        );
                    }
                }
            }
            if (alertBool) {
                if (Objects.equals(flag, "警報")) {
                    if (canReceive(player, "mceew.notify.jma.alert")) {
                        playSound(alertAlertSoundType, alertAlertSoundVolume, alertAlertSoundPitch, player);
                    }
                } else {
                    if (canReceive(player, "mceew.notify.jma.forecast")) {
                        playSound(forecastAlertSoundType, forecastAlertSoundVolume, forecastAlertSoundPitch, player);
                    }
                }
            }
        });
    }

    private void scEewAction(String reportTime, String originTime, String num, String lat, String lon, String region, String mag, String depth, String intensity) {
        if (broadcastBool) {
            sendConsoleMessage(
                    sichuanBroadcastMessage.
                            replaceAll("%report_time%", reportTime).
                            replaceAll("%origin_time%", originTime).
                            replaceAll("%num%", num).
                            replaceAll("%lat%", lat).
                            replaceAll("%lon%", lon).
                            replaceAll("%region%", region).
                            replaceAll("%mag%", mag).
                            replaceAll("%depth%", depth).
                            replaceAll("%shindo%", intensity)
            );
        }
        forEachPlayer(player -> {
            if (canReceive(player, "mceew.notify.sc")) {
                if (broadcastBool) {
                    player.sendMessage(
                            sichuanBroadcastMessage.
                                    replaceAll("%report_time%", reportTime).
                                    replaceAll("%origin_time%", originTime).
                                    replaceAll("%num%", num).
                                    replaceAll("%lat%", lat).
                                    replaceAll("%lon%", lon).
                                    replaceAll("%region%", region).
                                    replaceAll("%mag%", mag).
                                    replaceAll("%depth%", depth).
                                    replaceAll("%shindo%", intensity)
                    );
                }
                if (titleBool) {
                    player.sendTitle(
                            sichuanTitleMessage.
                                    replaceAll("%report_time%", reportTime).
                                    replaceAll("%origin_time%", originTime).
                                    replaceAll("%num%", num).
                                    replaceAll("%lat%", lat).
                                    replaceAll("%lon%", lon).
                                    replaceAll("%region%", region).
                                    replaceAll("%mag%", mag).
                                    replaceAll("%depth%", depth).
                                    replaceAll("%shindo%", intensity),
                            sichuanSubtitleMessage.
                                    replaceAll("%report_time%", reportTime).
                                    replaceAll("%origin_time%", originTime).
                                    replaceAll("%num%", num).
                                    replaceAll("%lat%", lat).
                                    replaceAll("%lon%", lon).
                                    replaceAll("%region%", region).
                                    replaceAll("%mag%", mag).
                                    replaceAll("%depth%", depth).
                                    replaceAll("%shindo%", intensity),
                            10, 70, 20
                    );
                }
                if (alertBool) {
                    playSound(scAlertSoundType, scAlertSoundVolume, scAlertSoundPitch, player);
                }
            }
        });
    }

    private void fjEewAction(String reportTime, String originTime, String num, String lat, String lon, String region, String mag, String type) {
        if (broadcastBool) {
            sendConsoleMessage(
                    fjBroadcastMessage.
                            replaceAll("%report_time%", reportTime).
                            replaceAll("%origin_time%", originTime).
                            replaceAll("%num%", num).
                            replaceAll("%lat%", lat).
                            replaceAll("%lon%", lon).
                            replaceAll("%region%", region).
                            replaceAll("%mag%", mag).
                            replaceAll("%type%", type)
            );
        }
        forEachPlayer(player -> {
            if (canReceive(player, "mceew.notify.fj")) {
                if (broadcastBool) {
                    player.sendMessage(
                            fjBroadcastMessage.
                                    replaceAll("%report_time%", reportTime).
                                    replaceAll("%origin_time%", originTime).
                                    replaceAll("%num%", num).
                                    replaceAll("%lat%", lat).
                                    replaceAll("%lon%", lon).
                                    replaceAll("%region%", region).
                                    replaceAll("%mag%", mag).
                                    replaceAll("%type%", type)
                    );
                }
                if (titleBool) {
                    player.sendTitle(
                            fjTitleMessage.
                                    replaceAll("%report_time%", reportTime).
                                    replaceAll("%origin_time%", originTime).
                                    replaceAll("%num%", num).
                                    replaceAll("%lat%", lat).
                                    replaceAll("%lon%", lon).
                                    replaceAll("%region%", region).
                                    replaceAll("%mag%", mag).
                                    replaceAll("%type%", type),
                            fjSubtitleMessage.
                                    replaceAll("%report_time%", reportTime).
                                    replaceAll("%origin_time%", originTime).
                                    replaceAll("%num%", num).
                                    replaceAll("%lat%", lat).
                                    replaceAll("%lon%", lon).
                                    replaceAll("%region%", region).
                                    replaceAll("%mag%", mag).
                                    replaceAll("%type%", type),
                            10, 70, 20
                    );
                }
                if (alertBool) {
                    playSound(fjAlertSoundType, fjAlertSoundVolume, fjAlertSoundPitch, player);
                }
            }
        });
    }

    private void cwaEewAction(String reportTime, String originTime, String num, String lat, String lon, String region, String mag, String depth, String shindo) {
        if (broadcastBool) {
            sendConsoleMessage(
                    cwaBroadcastMessage.
                            replaceAll("%report_time%", reportTime).
                            replaceAll("%origin_time%", originTime).
                            replaceAll("%num%", num).
                            replaceAll("%lat%", lat).
                            replaceAll("%lon%", lon).
                            replaceAll("%region%", region).
                            replaceAll("%mag%", mag).
                            replaceAll("%depth%", depth).
                            replaceAll("%shindo%", shindo)
            );
        }
        forEachPlayer(player -> {
            if (canReceive(player, "mceew.notify.cwa")) {
                if (broadcastBool) {
                    player.sendMessage(
                            cwaBroadcastMessage.
                                    replaceAll("%report_time%", reportTime).
                                    replaceAll("%origin_time%", originTime).
                                    replaceAll("%num%", num).
                                    replaceAll("%lat%", lat).
                                    replaceAll("%lon%", lon).
                                    replaceAll("%region%", region).
                                    replaceAll("%mag%", mag).
                                    replaceAll("%depth%", depth).
                                    replaceAll("%shindo%", shindo)
                    );
                }
                if (titleBool) {
                    player.sendTitle(
                            cwaTitleMessage.
                                    replaceAll("%report_time%", reportTime).
                                    replaceAll("%origin_time%", originTime).
                                    replaceAll("%num%", num).
                                    replaceAll("%lat%", lat).
                                    replaceAll("%lon%", lon).
                                    replaceAll("%region%", region).
                                    replaceAll("%mag%", mag).
                                    replaceAll("%depth%", depth).
                                    replaceAll("%shindo%", shindo),
                            cwaSubtitleMessage.
                                    replaceAll("%report_time%", reportTime).
                                    replaceAll("%origin_time%", originTime).
                                    replaceAll("%num%", num).
                                    replaceAll("%lat%", lat).
                                    replaceAll("%lon%", lon).
                                    replaceAll("%region%", region).
                                    replaceAll("%mag%", mag).
                                    replaceAll("%depth%", depth).
                                    replaceAll("%shindo%", shindo),
                            10, 70, 20
                    );
                }
                if (alertBool) {
                    playSound(cwaAlertSoundType, cwaAlertSoundVolume, cwaAlertSoundPitch, player);
                }
            }
        });
    }

    private void cencEewAction(String reportTime, String originTime, String num, String lat, String lon, String region, String mag, String depth, String intensity) {
        if (broadcastBool) {
            sendConsoleMessage(
                    cencBroadcastMessage.
                            replaceAll("%report_time%", reportTime).
                            replaceAll("%origin_time%", originTime).
                            replaceAll("%num%", num).
                            replaceAll("%lat%", lat).
                            replaceAll("%lon%", lon).
                            replaceAll("%region%", region).
                            replaceAll("%mag%", mag).
                            replaceAll("%depth%", depth).
                            replaceAll("%shindo%", intensity)
            );
        }
        forEachPlayer(player -> {
            if (canReceive(player, "mceew.notify.cenc.eew")) {
                if (broadcastBool) {
                    player.sendMessage(
                            cencBroadcastMessage.
                                    replaceAll("%report_time%", reportTime).
                                    replaceAll("%origin_time%", originTime).
                                    replaceAll("%num%", num).
                                    replaceAll("%lat%", lat).
                                    replaceAll("%lon%", lon).
                                    replaceAll("%region%", region).
                                    replaceAll("%mag%", mag).
                                    replaceAll("%depth%", depth).
                                    replaceAll("%shindo%", intensity)
                    );
                }
                if (titleBool) {
                    player.sendTitle(
                            cencTitleMessage.
                                    replaceAll("%report_time%", reportTime).
                                    replaceAll("%origin_time%", originTime).
                                    replaceAll("%num%", num).
                                    replaceAll("%lat%", lat).
                                    replaceAll("%lon%", lon).
                                    replaceAll("%region%", region).
                                    replaceAll("%mag%", mag).
                                    replaceAll("%depth%", depth).
                                    replaceAll("%shindo%", intensity),
                            cencSubtitleMessage.
                                    replaceAll("%report_time%", reportTime).
                                    replaceAll("%origin_time%", originTime).
                                    replaceAll("%num%", num).
                                    replaceAll("%lat%", lat).
                                    replaceAll("%lon%", lon).
                                    replaceAll("%region%", region).
                                    replaceAll("%mag%", mag).
                                    replaceAll("%depth%", depth).
                                    replaceAll("%shindo%", intensity),
                            10, 70, 20
                    );
                }
                if (alertBool) {
                    playSound(cencAlertSoundType, cencAlertSoundVolume, cencAlertSoundPitch, player);
                }
            }
        });
    }

    private void cqEewAction(String reportTime, String originTime, String num, String lat, String lon, String region, String mag, String depth, String intensity) {
        if (broadcastBool) {
            sendConsoleMessage(
                    cqBroadcastMessage.
                            replaceAll("%report_time%", reportTime).
                            replaceAll("%origin_time%", originTime).
                            replaceAll("%num%", num).
                            replaceAll("%lat%", lat).
                            replaceAll("%lon%", lon).
                            replaceAll("%region%", region).
                            replaceAll("%mag%", mag).
                            replaceAll("%depth%", depth).
                            replaceAll("%shindo%", intensity)
            );
        }
        forEachPlayer(player -> {
            if (canReceive(player, "mceew.notify.cq")) {
                if (broadcastBool) {
                    player.sendMessage(
                            cqBroadcastMessage.
                                    replaceAll("%report_time%", reportTime).
                                    replaceAll("%origin_time%", originTime).
                                    replaceAll("%num%", num).
                                    replaceAll("%lat%", lat).
                                    replaceAll("%lon%", lon).
                                    replaceAll("%region%", region).
                                    replaceAll("%mag%", mag).
                                    replaceAll("%depth%", depth).
                                    replaceAll("%shindo%", intensity)
                    );
                }
                if (titleBool) {
                    player.sendTitle(
                            cqTitleMessage.
                                    replaceAll("%report_time%", reportTime).
                                    replaceAll("%origin_time%", originTime).
                                    replaceAll("%num%", num).
                                    replaceAll("%lat%", lat).
                                    replaceAll("%lon%", lon).
                                    replaceAll("%region%", region).
                                    replaceAll("%mag%", mag).
                                    replaceAll("%depth%", depth).
                                    replaceAll("%shindo%", intensity),
                            cqSubtitleMessage.
                                    replaceAll("%report_time%", reportTime).
                                    replaceAll("%origin_time%", originTime).
                                    replaceAll("%num%", num).
                                    replaceAll("%lat%", lat).
                                    replaceAll("%lon%", lon).
                                    replaceAll("%region%", region).
                                    replaceAll("%mag%", mag).
                                    replaceAll("%depth%", depth).
                                    replaceAll("%shindo%", intensity),
                            10, 70, 20
                    );
                }
                if (alertBool) {
                    playSound(cqAlertSoundType, cqAlertSoundVolume, cqAlertSoundPitch, player);
                }
            }
        });
    }

    private String getShindoColor(String shindo) {
        String[] shindoColor = new String[]{"§f", "§7", "§b", "§9", "§a", "§e", "§6", "§c", "§4", "§d"};
        if (Objects.equals(shindo, "1")) {
            return shindoColor[1] + shindo;
        } else if (Objects.equals(shindo, "2")) {
            return shindoColor[2] + shindo;
        } else if (Objects.equals(shindo, "3")) {
            return shindoColor[3] + shindo;
        } else if (Objects.equals(shindo, "4")) {
            return shindoColor[4] + shindo;
        } else if (Objects.equals(shindo, "5弱") || Objects.equals(shindo, "5-")) {
            return shindoColor[5] + shindo;
        } else if (Objects.equals(shindo, "5強") || Objects.equals(shindo, "5+")) {
            return shindoColor[6] + shindo;
        } else if (Objects.equals(shindo, "6弱") || Objects.equals(shindo, "6-")) {
            return shindoColor[7] + shindo;
        } else if (Objects.equals(shindo, "6強") || Objects.equals(shindo, "6+")) {
            return shindoColor[8] + shindo;
        } else if (Objects.equals(shindo, "7")) {
            return shindoColor[9] + shindo;
        } else {
            return shindoColor[0] + shindo;
        }
    }

    private String getIntensityColor(String intensity) {
        String[] intensityColor = new String[]{"§f", "§7", "§b", "§3", "§9", "§a", "§2", "§e", "§6", "§c", "§4", "§d", "§5"};
        float value = Float.parseFloat(intensity);
        int index = Math.round(value);
        if (index < 0) index = 0;
        if (index >= intensityColor.length) index = intensityColor.length - 1;
        return intensityColor[index] + intensity;
    }

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("§a[MCEEW] Plugin version: v" + version);
            sender.sendMessage("§a[MCEEW] §3/eew§a - Show available commands");
            sender.sendMessage("§a[MCEEW] §3/eew test§a - Send a test EEW alert");
            sender.sendMessage("§a[MCEEW] §3/eew info§a - Display latest earthquake information");
            sender.sendMessage("§a[MCEEW] §3/eew reload§a - Reload plugin configuration");
            return true;
        } else if (args[0].equalsIgnoreCase("reload") && sender.isOp()) {
            if (!prepareAndLoadConfiguration()) {
                sender.sendMessage("§c[MCEEW] Configuration reload failed; the existing file was left unchanged.");
                return true;
            }
            webSocketManager.restart();
            sender.sendMessage("§a[MCEEW] Configuration reloaded successfully.");
            return true;
        } else if (args[0].equalsIgnoreCase("info")) {
            if (args.length == 2) {
                if (args[1].equalsIgnoreCase("jma")) {
                    getEewInfo(false, sender);
                    return true;
                } else if (args[1].equalsIgnoreCase("cenc")) {
                    getEewInfo(true, sender);
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
                    eewTest(0);
                    return true;
                } else if (args[1].equalsIgnoreCase("alert")) {
                    eewTest(1);
                    return true;
                } else if (args[1].equalsIgnoreCase("sc")) {
                    eewTest(2);
                    return true;
                } else if (args[1].equalsIgnoreCase("fj")) {
                    eewTest(3);
                    return true;
                } else if (args[1].equalsIgnoreCase("cwa")) {
                    eewTest(4);
                    return true;
                } else if (args[1].equalsIgnoreCase("cenc")) {
                    eewTest(5);
                    return true;
                } else if (args[1].equalsIgnoreCase("cq")) {
                    eewTest(6);
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

    private synchronized boolean prepareAndLoadConfiguration() {
        try {
            configManager.prepareConfig();
        } catch (ConfigManager.ConfigPreparationException error) {
            return false;
        }
        try {
            reloadConfig();
            loadRuntimeConfiguration();
            return true;
        } catch (RuntimeException error) {
            getLogger().log(java.util.logging.Level.SEVERE,
                    "Configuration was prepared but could not be loaded into the runtime.", error);
            return false;
        }
    }

    private void loadRuntimeConfiguration() {
        jpEewBoolean = getConfig().getBoolean("enable_jp");
        scEewBoolean = getConfig().getBoolean("enable_sc");
        fjEewBoolean = getConfig().getBoolean("enable_fj");
        cwaEewBoolean = getConfig().getBoolean("enable_cwa");
        cencEewBoolean = getConfig().getBoolean("enable_cenceew");
        cqEewBoolean = getConfig().getBoolean("enable_cq");
        broadcastBool = getConfig().getBoolean("Action.broadcast");
        titleBool = getConfig().getBoolean("Action.title");
        alertBool = getConfig().getBoolean("Action.alert");
        jmaEqlistBoolean = getConfig().getBoolean("Action.jma");
        cencEqlistBoolean = getConfig().getBoolean("Action.cenc");
        timeFormat = getConfig().getString("time_format");
        alertBroadcastMessage = Objects.requireNonNull(getConfig().getString("Message.Alert.broadcast")).replace("&", "§");
        alertTitleMessage = Objects.requireNonNull(getConfig().getString("Message.Alert.title")).replace("&", "§");
        alertSubtitleMessage = Objects.requireNonNull(getConfig().getString("Message.Alert.subtitle")).replace("&", "§");
        forecastBroadcastMessage = Objects.requireNonNull(getConfig().getString("Message.Forecast.broadcast")).replace("&", "§");
        forecastTitleMessage = Objects.requireNonNull(getConfig().getString("Message.Forecast.title")).replace("&", "§");
        forecastSubtitleMessage = Objects.requireNonNull(getConfig().getString("Message.Forecast.subtitle")).replace("&", "§");
        jmaEqlistBroadcastMessage = Objects.requireNonNull(getConfig().getString("Message.Jma.broadcast")).replace("&", "§");
        cencEqlistBroadcastMessage = Objects.requireNonNull(getConfig().getString("Message.Cenc.broadcast")).replace("&", "§");
        sichuanBroadcastMessage = Objects.requireNonNull(getConfig().getString("Message.Sichuan.broadcast")).replace("&", "§");
        sichuanTitleMessage = Objects.requireNonNull(getConfig().getString("Message.Sichuan.title")).replace("&", "§");
        sichuanSubtitleMessage = Objects.requireNonNull(getConfig().getString("Message.Sichuan.subtitle")).replace("&", "§");
        fjBroadcastMessage = Objects.requireNonNull(getConfig().getString("Message.Fjea.broadcast")).replace("&", "§");
        fjTitleMessage = Objects.requireNonNull(getConfig().getString("Message.Fjea.title")).replace("&", "§");
        fjSubtitleMessage = Objects.requireNonNull(getConfig().getString("Message.Fjea.subtitle")).replace("&", "§");
        cwaBroadcastMessage = Objects.requireNonNull(getConfig().getString("Message.Cwa.broadcast")).replace("&", "§");
        cwaTitleMessage = Objects.requireNonNull(getConfig().getString("Message.Cwa.title")).replace("&", "§");
        cwaSubtitleMessage = Objects.requireNonNull(getConfig().getString("Message.Cwa.subtitle")).replace("&", "§");
        cencBroadcastMessage = Objects.requireNonNull(getConfig().getString("Message.CencEEW.broadcast")).replace("&", "§");
        cencTitleMessage = Objects.requireNonNull(getConfig().getString("Message.CencEEW.title")).replace("&", "§");
        cencSubtitleMessage = Objects.requireNonNull(getConfig().getString("Message.CencEEW.subtitle")).replace("&", "§");
        cqBroadcastMessage = Objects.requireNonNull(getConfig().getString("Message.Chongqing.broadcast")).replace("&", "§");
        cqTitleMessage = Objects.requireNonNull(getConfig().getString("Message.Chongqing.title")).replace("&", "§");
        cqSubtitleMessage = Objects.requireNonNull(getConfig().getString("Message.Chongqing.subtitle")).replace("&", "§");
        alertAlertSoundType = getConfig().getString("Sound.Alert.type");
        alertAlertSoundVolume = getConfig().getDouble("Sound.Alert.volume");
        alertAlertSoundPitch = getConfig().getDouble("Sound.Alert.pitch");
        forecastAlertSoundType = getConfig().getString("Sound.Forecast.type");
        forecastAlertSoundVolume = getConfig().getDouble("Sound.Forecast.volume");
        forecastAlertSoundPitch = getConfig().getDouble("Sound.Forecast.pitch");
        scAlertSoundType = getConfig().getString("Sound.Sichuan.type");
        scAlertSoundVolume = getConfig().getDouble("Sound.Sichuan.volume");
        scAlertSoundPitch = getConfig().getDouble("Sound.Sichuan.pitch");
        fjAlertSoundType = getConfig().getString("Sound.Fjea.type");
        fjAlertSoundVolume = getConfig().getDouble("Sound.Fjea.volume");
        fjAlertSoundPitch = getConfig().getDouble("Sound.Fjea.pitch");
        cwaAlertSoundType = getConfig().getString("Sound.Cwa.type");
        cwaAlertSoundVolume = getConfig().getDouble("Sound.Cwa.volume");
        cwaAlertSoundPitch = getConfig().getDouble("Sound.Cwa.pitch");
        cencAlertSoundType = getConfig().getString("Sound.CencEEW.type");
        cencAlertSoundVolume = getConfig().getDouble("Sound.CencEEW.volume");
        cencAlertSoundPitch = getConfig().getDouble("Sound.CencEEW.pitch");
        cqAlertSoundType = getConfig().getString("Sound.Chongqing.type");
        cqAlertSoundVolume = getConfig().getDouble("Sound.Chongqing.volume");
        cqAlertSoundPitch = getConfig().getDouble("Sound.Chongqing.pitch");
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
