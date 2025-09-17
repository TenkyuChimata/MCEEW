package jp.wolfx.mceew;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bstats.bukkit.Metrics;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.concurrent.CompletionStage;

public final class MCEEW extends JavaPlugin {
    private int configVersion;
    private final int currentConfig = 8;
    private boolean jpEewBoolean;
    private boolean scEewBoolean;
    private boolean fjEewBoolean;
    private boolean cwaEewBoolean;
    private boolean cencEewBoolean;
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
    private String jmaEqlistMd5 = null;
    private String cencEqlistMd5 = null;
    private JsonObject jmaEqlistData = null;
    private JsonObject cencEqlistData = null;
    private final ArrayList<String> jmaEqlistInfo = new ArrayList<>();
    private final ArrayList<String> cencEqlistInfo = new ArrayList<>();
    private final String version = getDescription().getVersion();
    private final HttpClient client = HttpClient.newHttpClient();
    private final boolean folia = isFolia();

    @Override
    public void onEnable() {
        loadEew(true);
        new Metrics(this, 17261);
    }

    private boolean isFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.scheduler.AsyncScheduler");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
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
        Bukkit.broadcastMessage("§eWarning: This is an Earthquake Early Warning test.");
    }

    private boolean canReceive(Player player, String node) {
        return player.hasPermission("mceew.notify.all") && player.hasPermission(node);
    }

    private String getDate(String pattern, String timeFormat, String timezone, String originTime) {
        DateTimeFormatter originTime1 = DateTimeFormatter.ofPattern(pattern);
        ZonedDateTime originTime2 = ZonedDateTime.parse(originTime, originTime1.withZone(ZoneId.of(timezone)));
        return originTime2.format(DateTimeFormatter.ofPattern(timeFormat));
    }

    private void playSound(String alertSoundType, double alertSoundVolume, double alertSoundPitch, Player player) {
        NamespacedKey key = NamespacedKey.minecraft(alertSoundType);
        Sound alertPlayedSound = Registry.SOUNDS.get(key);
        if (alertPlayedSound == null) {
            getLogger().warning("Unknown sound type: " + alertSoundType);
            return;
        }
        player.playSound(player.getLocation(), alertPlayedSound, (float) alertSoundVolume, (float) alertSoundPitch);
    }

    private void cancelScheduler() {
        if (!folia) {
            Bukkit.getScheduler().cancelTasks(this);
        } else {
            Bukkit.getAsyncScheduler().cancelTasks(this);
        }
    }

    private void mceewScheduler(boolean first) {
        if (!folia) {
            Bukkit.getScheduler().runTaskAsynchronously(this, () -> wsClient(first));
            if (first) {
                getLogger().info("Using Bukkit API for scheduler.");
                Bukkit.getScheduler().runTaskAsynchronously(this, this::updater);
            }
        } else {
            Plugin plugin = this;
            Bukkit.getAsyncScheduler().runNow(plugin, task1 -> wsClient(first));
            if (first) {
                getLogger().info("Using Folia API for scheduler.");
                Bukkit.getAsyncScheduler().runNow(plugin, task2 -> updater());
            }
        }
    }

    private void updater() {
        StringBuilder rawData = new StringBuilder();
        try {
            URL url = new URL("https://tenkyuchimata.github.io/MCEEW/version.json");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                throw new IOException();
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    rawData.append(line);
                }
                JsonObject json = JsonParser.parseString(rawData.toString()).getAsJsonObject();
                String apiVersion = json.get("version").getAsString();
                if (Integer.parseInt(apiVersion.replaceAll("\\.", "")) > Integer.parseInt(version.replaceAll("-b.*", "").replaceAll("\\.", ""))) {
                    getLogger().warning("New plugin version v" + apiVersion + " detected, Please download a new version from https://acg.kr/mceew");
                } else {
                    getLogger().info("You are running the latest plugin version.");
                }
            }
        } catch (IOException e) {
            getLogger().warning("Failed to check for plugin updates.");
            getLogger().warning(String.valueOf(e));
        }
        if (currentConfig > configVersion) {
            getLogger().warning("Configuration update detected, please delete the MCEEW configuration file to update it.");
        }
    }

    private void wsReconnect(Boolean type) {
        getLogger().warning("Trying to reconnect to WebSocket API...");
        try {
            TimeUnit.SECONDS.sleep(5);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (type) {
            wsClient(false);
        } else {
            loadEew(false);
        }
    }

    private void wsClient(Boolean first) {
        try {
            WebSocket.Builder builder = client.newWebSocketBuilder();
            WebSocket webSocket = builder.buildAsync(URI.create("wss://ws-api.wolfx.jp/all_eew"), new WebSocket.Listener() {
                private final StringBuilder messageBuffer = new StringBuilder();

                public void onOpen(WebSocket webSocket) {
                    getLogger().info("Connected to WebSocket API.");
                    webSocket.request(1);
                }

                public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                    messageBuffer.append(data);
                    if (last) {
                        String completeMessage = messageBuffer.toString();
                        messageBuffer.setLength(0);
                        JsonObject json = JsonParser.parseString(completeMessage).getAsJsonObject();
                        if (!Objects.equals(json.get("type").getAsString(), "heartbeat")) {
                            if (Objects.equals(json.get("type").getAsString(), "jma_eew") && jpEewBoolean) {
                                jmaEewExecute(json);
                            }
                            if (Objects.equals(json.get("type").getAsString(), "jma_eqlist")) {
                                jmaEqlistData = json;
                                jmaEqlistExecute(jmaEqlistBoolean);
                            }
                            if (Objects.equals(json.get("type").getAsString(), "sc_eew") && scEewBoolean) {
                                scEewExecute(json);
                            }
                            if (Objects.equals(json.get("type").getAsString(), "fj_eew") && fjEewBoolean) {
                                fjEewExecute(json);
                            }
                            if (Objects.equals(json.get("type").getAsString(), "cwa_eew") && cwaEewBoolean) {
                                cwaEewExecute(json);
                            }
                            if (Objects.equals(json.get("type").getAsString(), "cenc_eew") && cencEewBoolean) {
                                cencEewExecute(json);
                            }
                            if (Objects.equals(json.get("type").getAsString(), "cenc_eqlist")) {
                                cencEqlistData = json;
                                cencEqlistExecute(cencEqlistBoolean);
                            }
                        }
                    }
                    webSocket.request(1);
                    return null;
                }

                @Override
                public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
                    getLogger().warning("WebSocket API connection closed unexpectedly.");
                    getLogger().warning(reason);
                    wsReconnect(true);
                    return null;
                }

                public void onError(WebSocket webSocket, Throwable error) {
                    getLogger().warning("Failed to connect to WebSocket API.");
                    getLogger().warning(error.getMessage());
                    wsReconnect(false);
                }
            }).join();
            if (first) {
                webSocket.sendText("query_jmaeqlist", true);
                webSocket.sendText("query_cenceqlist", true);
            }
        } catch (Exception e) {
            getLogger().warning("Failed to connect to WebSocket API.");
            getLogger().warning(String.valueOf(e));
            wsReconnect(false);
        }
    }

    private void jmaEewExecute(JsonObject jmaEewData) {
        String type = "";
        String flag = jmaEewData.get("Title").getAsString().substring(7, 9);
        String reportTime = jmaEewData.get("AnnouncedTime").getAsString();
        String num = jmaEewData.get("Serial").getAsString();
        String lat = jmaEewData.get("Latitude").getAsString();
        String lon = jmaEewData.get("Longitude").getAsString();
        String region = jmaEewData.get("Hypocenter").getAsString();
        String mag = jmaEewData.get("Magunitude").getAsString();
        String depth = jmaEewData.get("Depth").getAsString() + "km";
        String shindo = jmaEewData.get("MaxIntensity").getAsString();
        String originTime = getDate("yyyy/MM/dd HH:mm:ss", timeFormat, "Asia/Tokyo", jmaEewData.get("OriginTime").getAsString());
        if (jmaEewData.get("isTraining").getAsBoolean()) {
            type = "訓練";
        } else if (jmaEewData.get("isAssumption").getAsBoolean()) {
            type = "仮定震源";
        }
        if (jmaEewData.get("isFinal").getAsBoolean()) {
            if (!type.isEmpty()) {
                type = type + " (最終報)";
            } else {
                type = "最終報";
            }
        }
        if (jmaEewData.get("isCancel").getAsBoolean()) {
            type = "取消";
        }
        jmaEewAction(flag, reportTime, originTime, num, lat, lon, region, mag, depth, getShindoColor(shindo), type);
    }

    private void jmaEqlistExecute(Boolean jmaEqlistBoolean) {
        String timeStr = jmaEqlistData.get("No1").getAsJsonObject().get("time_full").getAsString();
        String region = jmaEqlistData.get("No1").getAsJsonObject().get("location").getAsString();
        String mag = jmaEqlistData.get("No1").getAsJsonObject().get("magnitude").getAsString();
        String depth = jmaEqlistData.get("No1").getAsJsonObject().get("depth").getAsString();
        String latitude = jmaEqlistData.get("No1").getAsJsonObject().get("latitude").getAsString();
        String longitude = jmaEqlistData.get("No1").getAsJsonObject().get("longitude").getAsString();
        String shindo = jmaEqlistData.get("No1").getAsJsonObject().get("shindo").getAsString();
        String info = jmaEqlistData.get("No1").getAsJsonObject().get("info").getAsString();
        String originTime = getDate("yyyy/MM/dd HH:mm:ss", timeFormat, "Asia/Tokyo", timeStr);
        if (jmaEqlistMd5 != null && jmaEqlistBoolean) {
            Bukkit.getConsoleSender().sendMessage(
                    jmaEqlistBroadcastMessage.
                            replaceAll("%origin_time%", originTime).
                            replaceAll("%region%", region).
                            replaceAll("%mag%", mag).
                            replaceAll("%depth%", depth).
                            replaceAll("%lat%", latitude).
                            replaceAll("%lon%", longitude).
                            replaceAll("%shindo%", getShindoColor(shindo)).
                            replaceAll("%info%", info)
            );
            for (Player player : Bukkit.getServer().getOnlinePlayers()) {
                if (canReceive(player, "mceew.notify.jma.eqlist")) {
                    player.sendMessage(
                            jmaEqlistBroadcastMessage.
                                    replaceAll("%origin_time%", originTime).
                                    replaceAll("%region%", region).
                                    replaceAll("%mag%", mag).
                                    replaceAll("%depth%", depth).
                                    replaceAll("%lat%", latitude).
                                    replaceAll("%lon%", longitude).
                                    replaceAll("%shindo%", getShindoColor(shindo)).
                                    replaceAll("%info%", info)
                    );
                }
            }
        }
        jmaEqlistMd5 = jmaEqlistData.get("md5").getAsString();
        jmaEqlistInfo.clear();
        jmaEqlistInfo.add(originTime);
        jmaEqlistInfo.add(region);
        jmaEqlistInfo.add(mag);
        jmaEqlistInfo.add(depth);
        jmaEqlistInfo.add(latitude);
        jmaEqlistInfo.add(longitude);
        jmaEqlistInfo.add(shindo);
        jmaEqlistInfo.add(info);
    }

    private void cencEqlistExecute(Boolean cencEqlistBoolean) {
        String type = cencEqlistData.get("No1").getAsJsonObject().get("type").getAsString();
        String timeStr = cencEqlistData.get("No1").getAsJsonObject().get("time").getAsString();
        String region = cencEqlistData.get("No1").getAsJsonObject().get("location").getAsString();
        String mag = cencEqlistData.get("No1").getAsJsonObject().get("magnitude").getAsString();
        String depth = cencEqlistData.get("No1").getAsJsonObject().get("depth").getAsString();
        String latitude = cencEqlistData.get("No1").getAsJsonObject().get("latitude").getAsString();
        String longitude = cencEqlistData.get("No1").getAsJsonObject().get("longitude").getAsString();
        String originTime = getDate("yyyy-MM-dd HH:mm:ss", timeFormat, "Asia/Shanghai", timeStr);
        String intensity = cencEqlistData.get("No1").getAsJsonObject().get("intensity").getAsString();
        depth += "km";
        if (Objects.equals(type, "reviewed")) {
            type = "正式测定";
        } else {
            type = "自动测定";
        }
        if (cencEqlistMd5 != null && cencEqlistBoolean) {
            Bukkit.getConsoleSender().sendMessage(
                    cencEqlistBroadcastMessage.
                            replaceAll("%flag%", type).
                            replaceAll("%origin_time%", originTime).
                            replaceAll("%region%", region).
                            replaceAll("%mag%", mag).
                            replaceAll("%depth%", depth).
                            replaceAll("%lat%", latitude).
                            replaceAll("%lon%", longitude).
                            replaceAll("%shindo%", getIntensityColor(intensity))
            );
            for (Player player : Bukkit.getServer().getOnlinePlayers()) {
                if (canReceive(player, "mceew.notify.cenc.eqlist")) {
                    player.sendMessage(
                            cencEqlistBroadcastMessage.
                                    replaceAll("%flag%", type).
                                    replaceAll("%origin_time%", originTime).
                                    replaceAll("%region%", region).
                                    replaceAll("%mag%", mag).
                                    replaceAll("%depth%", depth).
                                    replaceAll("%lat%", latitude).
                                    replaceAll("%lon%", longitude).
                                    replaceAll("%shindo%", getIntensityColor(intensity))
                    );
                }
            }
        }
        cencEqlistMd5 = cencEqlistData.get("md5").getAsString();
        cencEqlistInfo.clear();
        cencEqlistInfo.add(type);
        cencEqlistInfo.add(originTime);
        cencEqlistInfo.add(region);
        cencEqlistInfo.add(mag);
        cencEqlistInfo.add(depth);
        cencEqlistInfo.add(latitude);
        cencEqlistInfo.add(longitude);
        cencEqlistInfo.add(intensity);
    }

    private void scEewExecute(JsonObject scEewData) {
        String reportTime = scEewData.get("ReportTime").getAsString();
        String num = scEewData.get("ReportNum").getAsString();
        String lat = scEewData.get("Latitude").getAsString();
        String lon = scEewData.get("Longitude").getAsString();
        String region = scEewData.get("HypoCenter").getAsString();
        String mag = scEewData.get("Magunitude").getAsString();
        String intensity = String.valueOf(Math.round(Float.parseFloat(scEewData.get("MaxIntensity").getAsString())));
        String depth;
        if (!scEewData.get("Depth").isJsonNull()) {
            depth = scEewData.get("Depth").getAsString() + "km";
        } else {
            depth = "10km";
        }
        String originTime = getDate("yyyy-MM-dd HH:mm:ss", timeFormat, "Asia/Shanghai", scEewData.get("OriginTime").getAsString());
        scEewAction(reportTime, originTime, num, lat, lon, region, mag, depth, getIntensityColor(intensity));
    }

    private void fjEewExecute(JsonObject fjEewData) {
        String type = "";
        String reportTime = fjEewData.get("ReportTime").getAsString();
        String num = fjEewData.get("ReportNum").getAsString();
        String lat = fjEewData.get("Latitude").getAsString();
        String lon = fjEewData.get("Longitude").getAsString();
        String region = fjEewData.get("HypoCenter").getAsString();
        String mag = fjEewData.get("Magunitude").getAsString();
        String originTime = getDate("yyyy-MM-dd HH:mm:ss", timeFormat, "Asia/Shanghai", fjEewData.get("OriginTime").getAsString());
        if (fjEewData.get("isFinal").getAsBoolean()) {
            type = "最終報";
        }
        fjEewAction(reportTime, originTime, num, lat, lon, region, mag, type);
    }

    private void cwaEewExecute(JsonObject cwaEewData) {
        String reportTime = cwaEewData.get("ReportTime").getAsString();
        String num = cwaEewData.get("ReportNum").getAsString();
        String lat = cwaEewData.get("Latitude").getAsString();
        String lon = cwaEewData.get("Longitude").getAsString();
        String region = cwaEewData.get("HypoCenter").getAsString();
        String mag = cwaEewData.get("Magunitude").getAsString();
        String depth = cwaEewData.get("Depth").getAsString() + "km";
        String shindo = cwaEewData.get("MaxIntensity").getAsString();
        String originTime = getDate("yyyy-MM-dd HH:mm:ss", timeFormat, "Asia/Shanghai", cwaEewData.get("OriginTime").getAsString());
        cwaEewAction(reportTime, originTime, num, lat, lon, region, mag, depth, getShindoColor(shindo));
    }

    private void cencEewExecute(JsonObject cencEewData) {
        String reportTime = cencEewData.get("ReportTime").getAsString();
        String num = cencEewData.get("ReportNum").getAsString();
        String lat = cencEewData.get("Latitude").getAsString();
        String lon = cencEewData.get("Longitude").getAsString();
        String region = cencEewData.get("HypoCenter").getAsString();
        String mag = cencEewData.get("Magnitude").getAsString();
        String intensity = String.valueOf(Math.round(Float.parseFloat(cencEewData.get("MaxIntensity").getAsString())));
        String depth;
        if (!cencEewData.get("Depth").isJsonNull()) {
            depth = cencEewData.get("Depth").getAsString() + "km";
        } else {
            depth = "10km";
        }
        String originTime = getDate("yyyy-MM-dd HH:mm:ss", timeFormat, "Asia/Shanghai", cencEewData.get("OriginTime").getAsString());
        cencEewAction(reportTime, originTime, num, lat, lon, region, mag, depth, getIntensityColor(intensity));
    }

    private void getEewInfo(Boolean flag, CommandSender sender) {
        if (flag) {
            if (cencEqlistMd5 != null) {
                sender.sendMessage(
                        cencEqlistBroadcastMessage.
                                replaceAll("%flag%", cencEqlistInfo.get(0)).
                                replaceAll("%origin_time%", cencEqlistInfo.get(1)).
                                replaceAll("%region%", cencEqlistInfo.get(2)).
                                replaceAll("%mag%", cencEqlistInfo.get(3)).
                                replaceAll("%depth%", cencEqlistInfo.get(4)).
                                replaceAll("%lat%", cencEqlistInfo.get(5)).
                                replaceAll("%lon%", cencEqlistInfo.get(6)).
                                replaceAll("%shindo%", getIntensityColor(cencEqlistInfo.get(7)))
                );
            }
        } else {
            if (jmaEqlistMd5 != null) {
                sender.sendMessage(
                        jmaEqlistBroadcastMessage.
                                replaceAll("%origin_time%", jmaEqlistInfo.get(0)).
                                replaceAll("%region%", jmaEqlistInfo.get(1)).
                                replaceAll("%mag%", jmaEqlistInfo.get(2)).
                                replaceAll("%depth%", jmaEqlistInfo.get(3)).
                                replaceAll("%lat%", jmaEqlistInfo.get(4)).
                                replaceAll("%lon%", jmaEqlistInfo.get(5)).
                                replaceAll("%shindo%", getShindoColor(jmaEqlistInfo.get(6))).
                                replaceAll("%info%", jmaEqlistInfo.get(7))
                );
            }
        }
    }

    private void jmaEewAction(String flag, String reportTime, String originTime, String num, String lat, String lon, String region, String mag, String depth, String shindo, String type) {
        if (broadcastBool) {
            if (Objects.equals(flag, "警報")) {
                Bukkit.getConsoleSender().sendMessage(
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
                Bukkit.getConsoleSender().sendMessage(
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
        for (Player player : Bukkit.getServer().getOnlinePlayers()) {
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
                        player.showTitle(
                                Title.title(
                                        Component.text(alertTitleMessage.
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
                                                replaceAll("%type%", type)),
                                        Component.text(alertSubtitleMessage.
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
                                                replaceAll("%type%", type))
                                )
                        );
                    }
                } else {
                    if (canReceive(player, "mceew.notify.jma.forecast")) {
                        player.showTitle(
                                Title.title(
                                        Component.text(forecastTitleMessage.
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
                                                replaceAll("%type%", type)),
                                        Component.text(forecastSubtitleMessage.
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
                                                replaceAll("%type%", type))
                                )
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
        }
    }

    private void scEewAction(String reportTime, String originTime, String num, String lat, String lon, String region, String mag, String depth, String intensity) {
        if (broadcastBool) {
            Bukkit.getConsoleSender().sendMessage(
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
        for (Player player : Bukkit.getServer().getOnlinePlayers()) {
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
                    player.showTitle(
                            Title.title(
                                    Component.text(sichuanTitleMessage.
                                            replaceAll("%report_time%", reportTime).
                                            replaceAll("%origin_time%", originTime).
                                            replaceAll("%num%", num).
                                            replaceAll("%lat%", lat).
                                            replaceAll("%lon%", lon).
                                            replaceAll("%region%", region).
                                            replaceAll("%mag%", mag).
                                            replaceAll("%depth%", depth).
                                            replaceAll("%shindo%", intensity)),
                                    Component.text(sichuanSubtitleMessage.
                                            replaceAll("%report_time%", reportTime).
                                            replaceAll("%origin_time%", originTime).
                                            replaceAll("%num%", num).
                                            replaceAll("%lat%", lat).
                                            replaceAll("%lon%", lon).
                                            replaceAll("%region%", region).
                                            replaceAll("%mag%", mag).
                                            replaceAll("%depth%", depth).
                                            replaceAll("%shindo%", intensity))
                            )
                    );
                }
                if (alertBool) {
                    playSound(scAlertSoundType, scAlertSoundVolume, scAlertSoundPitch, player);
                }
            }
        }
    }

    private void fjEewAction(String reportTime, String originTime, String num, String lat, String lon, String region, String mag, String type) {
        if (broadcastBool) {
            Bukkit.getConsoleSender().sendMessage(
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
        for (Player player : Bukkit.getServer().getOnlinePlayers()) {
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
                    player.showTitle(
                            Title.title(
                                    Component.text(fjTitleMessage.
                                            replaceAll("%report_time%", reportTime).
                                            replaceAll("%origin_time%", originTime).
                                            replaceAll("%num%", num).
                                            replaceAll("%lat%", lat).
                                            replaceAll("%lon%", lon).
                                            replaceAll("%region%", region).
                                            replaceAll("%mag%", mag).
                                            replaceAll("%type%", type)),
                                    Component.text(fjSubtitleMessage.
                                            replaceAll("%report_time%", reportTime).
                                            replaceAll("%origin_time%", originTime).
                                            replaceAll("%num%", num).
                                            replaceAll("%lat%", lat).
                                            replaceAll("%lon%", lon).
                                            replaceAll("%region%", region).
                                            replaceAll("%mag%", mag).
                                            replaceAll("%type%", type))
                            )
                    );
                }
                if (alertBool) {
                    playSound(fjAlertSoundType, fjAlertSoundVolume, fjAlertSoundPitch, player);
                }
            }
        }
    }

    private void cwaEewAction(String reportTime, String originTime, String num, String lat, String lon, String region, String mag, String depth, String shindo) {
        if (broadcastBool) {
            Bukkit.getConsoleSender().sendMessage(
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
        for (Player player : Bukkit.getServer().getOnlinePlayers()) {
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
                    player.showTitle(
                            Title.title(
                                    Component.text(cwaTitleMessage.
                                            replaceAll("%report_time%", reportTime).
                                            replaceAll("%origin_time%", originTime).
                                            replaceAll("%num%", num).
                                            replaceAll("%lat%", lat).
                                            replaceAll("%lon%", lon).
                                            replaceAll("%region%", region).
                                            replaceAll("%mag%", mag).
                                            replaceAll("%depth%", depth).
                                            replaceAll("%shindo%", shindo)),
                                    Component.text(cwaSubtitleMessage.
                                            replaceAll("%report_time%", reportTime).
                                            replaceAll("%origin_time%", originTime).
                                            replaceAll("%num%", num).
                                            replaceAll("%lat%", lat).
                                            replaceAll("%lon%", lon).
                                            replaceAll("%region%", region).
                                            replaceAll("%mag%", mag).
                                            replaceAll("%depth%", depth).
                                            replaceAll("%shindo%", shindo))
                            )
                    );
                }
                if (alertBool) {
                    playSound(cwaAlertSoundType, cwaAlertSoundVolume, cwaAlertSoundPitch, player);
                }
            }
        }
    }

    private void cencEewAction(String reportTime, String originTime, String num, String lat, String lon, String region, String mag, String depth, String intensity) {
        if (broadcastBool) {
            Bukkit.getConsoleSender().sendMessage(
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
        for (Player player : Bukkit.getServer().getOnlinePlayers()) {
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
                    player.showTitle(
                            Title.title(
                                    Component.text(cencTitleMessage.
                                            replaceAll("%report_time%", reportTime).
                                            replaceAll("%origin_time%", originTime).
                                            replaceAll("%num%", num).
                                            replaceAll("%lat%", lat).
                                            replaceAll("%lon%", lon).
                                            replaceAll("%region%", region).
                                            replaceAll("%mag%", mag).
                                            replaceAll("%depth%", depth).
                                            replaceAll("%shindo%", intensity)),
                                    Component.text(cencSubtitleMessage.
                                            replaceAll("%report_time%", reportTime).
                                            replaceAll("%origin_time%", originTime).
                                            replaceAll("%num%", num).
                                            replaceAll("%lat%", lat).
                                            replaceAll("%lon%", lon).
                                            replaceAll("%region%", region).
                                            replaceAll("%mag%", mag).
                                            replaceAll("%depth%", depth).
                                            replaceAll("%shindo%", intensity))
                            )
                    );
                }
                if (alertBool) {
                    playSound(cencAlertSoundType, cencAlertSoundVolume, cencAlertSoundPitch, player);
                }
            }
        }
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

    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String @NotNull [] args) {
        if (args.length == 0) {
            sender.sendMessage("§a[MCEEW] Plugin version: v" + version);
            sender.sendMessage("§a[MCEEW] §3/eew§a - Show available commands");
            sender.sendMessage("§a[MCEEW] §3/eew test§a - Send a test EEW alert");
            sender.sendMessage("§a[MCEEW] §3/eew info§a - Display latest earthquake information");
            sender.sendMessage("§a[MCEEW] §3/eew reload§a - Reload plugin configuration");
            return true;
        } else if (args[0].equalsIgnoreCase("reload") && sender.isOp()) {
            loadEew(false);
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
                }
            } else {
                sender.sendMessage("§a[MCEEW] §3/eew test forecast§a - Send JMA forecast EEW test.");
                sender.sendMessage("§a[MCEEW] §3/eew test alert§a - Send JMA alert EEW test.");
                sender.sendMessage("§a[MCEEW] §3/eew test sc§a - Send Sichuan EEW test.");
                sender.sendMessage("§a[MCEEW] §3/eew test fj§a - Send Taiwan/Fujian EEW test.");
                sender.sendMessage("§a[MCEEW] §3/eew test cwa§a - Send Taiwan CWA EEW test.");
                sender.sendMessage("§a[MCEEW] §3/eew test cenc§a - Send China CENC EEW test.");
                return true;
            }
        }
        return false;
    }

    private void loadEew(Boolean first) {
        cancelScheduler();
        saveDefaultConfig();
        reloadConfig();
        jpEewBoolean = getConfig().getBoolean("enable_jp");
        scEewBoolean = getConfig().getBoolean("enable_sc");
        fjEewBoolean = getConfig().getBoolean("enable_fj");
        cwaEewBoolean = getConfig().getBoolean("enable_cwa");
        cencEewBoolean = getConfig().getBoolean("enable_cenceew");
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
        configVersion = getConfig().getInt("config-version");
        mceewScheduler(first);
    }

    @Override
    public void onDisable() {
        cancelScheduler();
    }
}
