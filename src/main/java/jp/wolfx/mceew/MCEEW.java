package jp.wolfx.mceew;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
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
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public final class MCEEW extends JavaPlugin {
    private static boolean broadcast_bool;
    private static boolean title_bool;
    private static boolean alert_bool;
    private static boolean enable_jma_bool;
    private static boolean notification_bool;
    private static String time_format;
    private static String time_format_final;
    private static String alert_broadcast_message;
    private static String alert_title_message;
    private static String alert_subtitle_message;
    private static String forecast_broadcast_message;
    private static String forecast_title_message;
    private static String forecast_subtitle_message;
    private static String final_broadcast_message;
    private static String sichuan_broadcast_message;
    private static String sichuan_title_message;
    private static String sichuan_subtitle_message;
    private static String alert_alert_sound_type;
    private static double alert_alert_sound_volume;
    private static double alert_alert_sound_pitch;
    private static String forecast_alert_sound_type;
    private static double forecast_alert_sound_volume;
    private static double forecast_alert_sound_pitch;
    private static String sc_alert_sound_type;
    private static double sc_alert_sound_volume;
    private static double sc_alert_sound_pitch;
    private static String[] shindo_color;
    private static String[] intensity_color;
    private static String update_report = null;
    private static String OriginalText = null;
    private static String final_md5 = null;
    private static String EventID = null;
    private static final ArrayList<String> final_info = new ArrayList<>();
    private final String version = this.getDescription().getVersion().replaceAll("-b", "");
    private static final boolean folia = isFolia();

    @Override
    public void onEnable() {
        this.loadEew(true);
        new Metrics(this, 17261);
    }

    private static boolean isFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private void mceewScheduler(boolean eewBoolean, boolean jpEewBoolean, boolean finalBoolean, boolean scEewBoolean, boolean updaterBoolean) {
        if (!folia) {
            if (eewBoolean) {
                if (jpEewBoolean) {
                    Bukkit.getScheduler().runTaskTimerAsynchronously(this, this::eewChecker, 20L, 20L);
                }
                if (finalBoolean) {
                    Bukkit.getScheduler().runTaskTimerAsynchronously(this, this::finalChecker, 20L, 100L);
                }
                if (scEewBoolean) {
                    Bukkit.getScheduler().runTaskTimerAsynchronously(this, this::scEewChecker, 20L, 20L);
                }
            }
            if (updaterBoolean) {
                Bukkit.getScheduler().runTaskAsynchronously(this, this::updater);
            }
        } else {
            Plugin plugin = this;
            Bukkit.getGlobalRegionScheduler().run(this, task -> {
                if (eewBoolean) {
                    if (jpEewBoolean) {
                        Bukkit.getAsyncScheduler().runAtFixedRate(plugin, task14 -> eewChecker(), 1L, 1L, TimeUnit.SECONDS);
                    }
                    if (finalBoolean) {
                        Bukkit.getAsyncScheduler().runAtFixedRate(plugin, task13 -> finalChecker(), 1L, 5L, TimeUnit.SECONDS);
                    }
                    if (scEewBoolean) {
                        Bukkit.getAsyncScheduler().runAtFixedRate(plugin, task1 -> scEewChecker(), 1L, 1L, TimeUnit.SECONDS);
                    }
                }
                if (updaterBoolean) {
                    Bukkit.getAsyncScheduler().runNow(plugin, task12 -> updater());
                }
            });
        }
    }

    private void cancelScheduler() {
        if (!folia) {
            Bukkit.getScheduler().cancelTasks(this);
        } else {
            Bukkit.getGlobalRegionScheduler().cancelTasks(this);
            Bukkit.getAsyncScheduler().cancelTasks(this);
        }
    }

    private static String getAPI(int timeout, int source) {
        String data = null;
        URL url;
        StringBuilder raw_data = new StringBuilder();
        try {
            if (source == 0) {
                url = new URL("https://api.wolfx.jp/jma_eew.json");
            } else if (source == 1) {
                url = new URL("https://api.wolfx.jp/nied_eew.json");
            } else if (source == 2) {
                url = new URL("https://api.wolfx.jp/jma_eqlist.json");
            } else if (source == 3) {
                url = new URL("https://api.wolfx.jp/sc_eew.json");
            } else {
                url = new URL("https://tenkyuchimata.github.io/MCEEW/version.json");
            }
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(timeout);
            connection.setReadTimeout(timeout);
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                throw new IOException();
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    raw_data.append(line);
                }
                data = raw_data.toString();
            }
        } catch (IOException e) {
            if (notification_bool) {
                Bukkit.getLogger().warning("API connection failed, retrying...");
                Bukkit.getLogger().warning(String.valueOf(e));
            }
        }
        return data;
    }

    private void updater() {
        String responseData = getAPI(5000, -1);
        if (responseData != null) {
            JsonObject json = JsonParser.parseString(responseData).getAsJsonObject();
            String api_version = json.get("version").getAsString();
            if (Integer.parseInt(api_version.replaceAll("\\.", "")) > Integer.parseInt(version.replaceAll("\\.", ""))) {
                Bukkit.getLogger().warning("[MCEEW] New plugin version v" + api_version + " detected, Please download a new version from https://acg.kr/mceew");
            } else {
                Bukkit.getLogger().info("[MCEEW] You are running the latest version.");
            }
        }
    }

    private static String getDate(String pattern, String time_format, String timezone, String origin_time) {
        DateTimeFormatter origin_time1 = DateTimeFormatter.ofPattern(pattern);
        ZonedDateTime origin_time2 = ZonedDateTime.parse(origin_time, origin_time1.withZone(ZoneId.of(timezone)));
        return origin_time2.format(DateTimeFormatter.ofPattern(time_format));
    }

    private static void playSound(String alert_sound_type, double alert_sound_volume, double alert_sound_pitch) {
        Sound alert_playedSound = Sound.valueOf(alert_sound_type);
        for (Player player : Bukkit.getServer().getOnlinePlayers()) {
            player.playSound(player.getLocation(), alert_playedSound, (float) alert_sound_volume, (float) alert_sound_pitch);
        }
    }

    private void eewChecker() {
        if (enable_jma_bool) {
            String responseData = getAPI(1500, 0);
            if (responseData != null) {
                JsonObject json = JsonParser.parseString(responseData).getAsJsonObject();
                if (!Objects.equals(json.get("OriginalText").getAsString(), OriginalText)) {
                    String type = "";
                    String flag = json.get("Title").getAsString().substring(7, 9);
                    String report_time = json.get("AnnouncedTime").getAsString();
                    String num = json.get("Serial").getAsString();
                    String lat = json.get("Latitude").getAsString();
                    String lon = json.get("Longitude").getAsString();
                    String region = json.get("Hypocenter").getAsString();
                    String mag = json.get("Magunitude").getAsString();
                    String depth = json.get("Depth").getAsString() + "km";
                    String shindo = json.get("MaxIntensity").getAsString();
                    String origin_time = getDate("yyyy/MM/dd HH:mm:ss", time_format, "Asia/Tokyo", json.get("OriginTime").getAsString());
                    if (json.get("isTraining").getAsBoolean()) {
                        type = "訓練";
                    } else if (json.get("isAssumption").getAsBoolean()) {
                        type = "仮定震源";
                    }
                    if (json.get("isFinal").getAsBoolean()) {
                        if (!type.equals("")) {
                            type = type + " (最終報)";
                        } else {
                            type = "最終報";
                        }
                    }
                    if (json.get("isCancel").getAsBoolean()) {
                        type = "取消";
                    }
                    if (notification_bool) {
                        Bukkit.getLogger().info("[MCEEW] JMA EEW detected.");
                    }
                    if (OriginalText != null) {
                        eewAction(flag, report_time, origin_time, num, lat, lon, region, mag, depth, shindo, type);
                    }
                    OriginalText = json.get("OriginalText").getAsString();
                } else {
                    if (notification_bool) {
                        Bukkit.getLogger().info("[MCEEW] No JMA EEW issued.");
                    }
                }
            }
        } else {
            String responseData = getAPI(1500, 1);
            if (responseData != null) {
                JsonObject json = JsonParser.parseString(responseData).getAsJsonObject();
                if (!Objects.equals(json.get("report_time").getAsString(), update_report)) {
                    String type = "";
                    String flag = json.get("alertflg").getAsString();
                    String report_time = json.get("report_time").getAsString();
                    String num = json.get("report_num").getAsString();
                    String lat = json.get("latitude").getAsString();
                    String lon = json.get("longitude").getAsString();
                    String region = json.get("region_name").getAsString();
                    String mag = json.get("magunitude").getAsString();
                    String depth = json.get("depth").getAsString();
                    String shindo = json.get("calcintensity").getAsString();
                    String origin_time = getDate("yyyyMMddHHmmss", time_format, "Asia/Tokyo", json.get("origin_time").getAsString());
                    if (json.get("is_training").getAsBoolean()) {
                        type = "訓練";
                    }
                    if (json.get("is_final").getAsBoolean()) {
                        if (!type.equals("")) {
                            type = type + " (最終報)";
                        } else {
                            type = "最終報";
                        }
                    }
                    if (json.get("is_cancel").getAsBoolean()) {
                        type = "取消";
                    }
                    if (notification_bool) {
                        Bukkit.getLogger().info("[MCEEW] NIED EEW detected.");
                    }
                    if (update_report != null) {
                        eewAction(flag, report_time, origin_time, num, lat, lon, region, mag, depth, shindo, type);
                    }
                    update_report = report_time;
                } else {
                    if (notification_bool) {
                        Bukkit.getLogger().info("[MCEEW] No NIED EEW issued.");
                    }
                }
            }
        }

    }

    private void finalChecker() {
        String responseData = getAPI(5000, 2);
        if (responseData != null) {
            JsonObject json = JsonParser.parseString(responseData).getAsJsonObject().get("No1").getAsJsonObject();
            if (!(json.get("time").getAsString() + json.get("location").getAsString() + json.get("magnitude").getAsString() + json.get("depth").getAsString() + json.get("shindo").getAsString() + json.get("info").getAsString()).equals(final_md5)) {
                String time_str = json.get("time").getAsString();
                String region = json.get("location").getAsString();
                String mag = json.get("magnitude").getAsString();
                String depth = json.get("depth").getAsString();
                String shindo = json.get("shindo").getAsString();
                String info = json.get("info").getAsString();
                String origin_time = getDate("yyyy/MM/dd HH:mm", time_format_final, "Asia/Tokyo", time_str);
                if (notification_bool) {
                    Bukkit.getLogger().info("[MCEEW] Final report updated.");
                }
                if (final_md5 != null) {
                    finalAction(origin_time, region, mag, depth, shindo, info);
                }
                final_md5 = time_str + region + mag + depth + shindo + info;
                final_info.clear();
                final_info.add(origin_time);
                final_info.add(region);
                final_info.add(mag);
                final_info.add(depth);
                final_info.add(shindo);
                final_info.add(info);
            } else {
                if (notification_bool) {
                    Bukkit.getLogger().info("[MCEEW] No final report update.");
                }
            }
        }
    }

    private void scEewChecker() {
        String responseData = getAPI(1500, 3);
        if (responseData != null) {
            JsonObject json = JsonParser.parseString(responseData).getAsJsonObject();
            if (!Objects.equals(json.get("EventID").getAsString(), EventID)) {
                String report_time = json.get("ReportTime").getAsString();
                String num = json.get("ReportNum").getAsString();
                String lat = json.get("Latitude").getAsString();
                String lon = json.get("Longitude").getAsString();
                String region = json.get("HypoCenter").getAsString();
                String mag = json.get("Magunitude").getAsString();
                String depth = "10km";
                String intensity = String.valueOf(Math.round(Float.parseFloat(json.get("MaxIntensity").getAsString())));
                String origin_time = getDate("yyyy-MM-dd HH:mm:ss", time_format, "Asia/Shanghai", json.get("OriginTime").getAsString());
                if (notification_bool) {
                    Bukkit.getLogger().info("[MCEEW] Sichuan EEW detected.");
                }
                if (EventID != null) {
                    scEewAction(report_time, origin_time, num, lat, lon, region, mag, depth, intensity);
                }
                EventID = json.get("EventID").getAsString();
            } else {
                if (notification_bool) {
                    Bukkit.getLogger().info("[MCEEW] No Sichuan EEW issued.");
                }
            }
        }
    }

    private static void eewTest(int flag) {
        if (flag == 1) {
            String flags = "警報";
            String origin_time_str = "20220316233426";
            String report_time = "2022/03/16 23:36:00";
            String num = "17";
            String lat = "37.6";
            String lon = "141.7";
            String region = "福島県沖";
            String mag = "6.0";
            String depth = "60km";
            String shindo = "5弱";
            String type = "最終報";
            String origin_time = getDate("yyyyMMddHHmmss", time_format, "Asia/Tokyo", origin_time_str);
            eewAction(flags, report_time, origin_time, num, lat, lon, region, mag, depth, shindo, type);
        } else if (flag == 2) {
            String origin_time_str = "2023/01/02 14:20";
            String region = "浦河沖";
            String mag = "4.2";
            String depth = "70km";
            String shindo = "2";
            String info = "この地震による津波の心配はありません。";
            String origin_time = getDate("yyyy/MM/dd HH:mm", time_format_final, "Asia/Tokyo", origin_time_str);
            finalAction(origin_time, region, mag, depth, shindo, info);
        } else if (flag == 3) {
            String origin_time_str = "2023-01-01 21:08:30";
            String report_time = "2023-01-01 21:08:39";
            String num = "1";
            String lat = "32.43";
            String lon = "104.86";
            String region = "四川广元市青川县";
            String mag = "3.2";
            String depth = "10km";
            String intensity = "5";
            String origin_time = getDate("yyyy-MM-dd HH:mm:ss", time_format, "Asia/Shanghai", origin_time_str);
            scEewAction(report_time, origin_time, num, lat, lon, region, mag, depth, intensity);
        } else {
            String flags = "予報";
            String origin_time_str = "20220316233428";
            String report_time = "2022/03/16 23:34:41";
            String num = "1";
            String lat = "37.7";
            String lon = "141.6";
            String region = "福島県沖";
            String mag = "5.3";
            String depth = "50km";
            String shindo = "3";
            String type = "";
            String origin_time = getDate("yyyyMMddHHmmss", time_format, "Asia/Tokyo", origin_time_str);
            eewAction(flags, report_time, origin_time, num, lat, lon, region, mag, depth, shindo, type);
        }
        Bukkit.broadcastMessage("§eWarning: This is a Earthquake Early Warning issued test.");
    }

    private static void eewAction(String flag, String report_time, String origin_time, String num, String lat, String lon, String region, String mag, String depth, String shindo, String type) {
        shindo = getShindoColor(shindo);
        if (broadcast_bool) {
            if (Objects.equals(flag, "警報")) {
                Bukkit.broadcastMessage(
                        alert_broadcast_message.
                                replaceAll("%flag%", flag).
                                replaceAll("%report_time%", report_time).
                                replaceAll("%origin_time%", origin_time).
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
                Bukkit.broadcastMessage(
                        forecast_broadcast_message.
                                replaceAll("%flag%", flag).
                                replaceAll("%report_time%", report_time).
                                replaceAll("%origin_time%", origin_time).
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
        if (title_bool) {
            for (Player player : Bukkit.getServer().getOnlinePlayers()) {
                if (Objects.equals(flag, "警報")) {
                    player.sendTitle(
                            alert_title_message.
                                    replaceAll("%flag%", flag).
                                    replaceAll("%report_time%", report_time).
                                    replaceAll("%origin_time%", origin_time).
                                    replaceAll("%num%", num).
                                    replaceAll("%lat%", lat).
                                    replaceAll("%lon%", lon).
                                    replaceAll("%region%", region).
                                    replaceAll("%mag%", mag).
                                    replaceAll("%depth%", depth).
                                    replaceAll("%shindo%", shindo).
                                    replaceAll("%type%", type),
                            alert_subtitle_message.
                                    replaceAll("%flag%", flag).
                                    replaceAll("%report_time%", report_time).
                                    replaceAll("%origin_time%", origin_time).
                                    replaceAll("%num%", num).
                                    replaceAll("%lat%", lat).
                                    replaceAll("%lon%", lon).
                                    replaceAll("%region%", region).
                                    replaceAll("%mag%", mag).
                                    replaceAll("%depth%", depth).
                                    replaceAll("%shindo%", shindo).
                                    replaceAll("%type%", type),
                            -1, -1, -1
                    );
                } else {
                    player.sendTitle(
                            forecast_title_message.
                                    replaceAll("%flag%", flag).
                                    replaceAll("%report_time%", report_time).
                                    replaceAll("%origin_time%", origin_time).
                                    replaceAll("%num%", num).
                                    replaceAll("%lat%", lat).
                                    replaceAll("%lon%", lon).
                                    replaceAll("%region%", region).
                                    replaceAll("%mag%", mag).
                                    replaceAll("%depth%", depth).
                                    replaceAll("%shindo%", shindo).
                                    replaceAll("%type%", type),
                            forecast_subtitle_message.
                                    replaceAll("%flag%", flag).
                                    replaceAll("%report_time%", report_time).
                                    replaceAll("%origin_time%", origin_time).
                                    replaceAll("%num%", num).
                                    replaceAll("%lat%", lat).
                                    replaceAll("%lon%", lon).
                                    replaceAll("%region%", region).
                                    replaceAll("%mag%", mag).
                                    replaceAll("%depth%", depth).
                                    replaceAll("%shindo%", shindo).
                                    replaceAll("%type%", type),
                            -1, -1, -1
                    );
                }
            }
        }
        if (alert_bool) {
            if (Objects.equals(flag, "警報")) {
                playSound(alert_alert_sound_type, alert_alert_sound_volume, alert_alert_sound_pitch);
            } else {
                playSound(forecast_alert_sound_type, forecast_alert_sound_volume, forecast_alert_sound_pitch);
            }
        }
    }

    private static void scEewAction(String report_time, String origin_time, String num, String lat, String lon, String region, String mag, String depth, String intensity) {
        intensity = ChatColor.translateAlternateColorCodes('&', intensity_color[Integer.parseInt(intensity)]) + intensity;
        if (broadcast_bool) {
            Bukkit.broadcastMessage(
                    sichuan_broadcast_message.
                            replaceAll("%report_time%", report_time).
                            replaceAll("%origin_time%", origin_time).
                            replaceAll("%num%", num).
                            replaceAll("%lat%", lat).
                            replaceAll("%lon%", lon).
                            replaceAll("%region%", region).
                            replaceAll("%mag%", mag).
                            replaceAll("%depth%", depth).
                            replaceAll("%shindo%", intensity)
            );
        }
        if (title_bool) {
            for (Player player : Bukkit.getServer().getOnlinePlayers()) {
                player.sendTitle(
                        sichuan_title_message.
                                replaceAll("%report_time%", report_time).
                                replaceAll("%origin_time%", origin_time).
                                replaceAll("%num%", num).
                                replaceAll("%lat%", lat).
                                replaceAll("%lon%", lon).
                                replaceAll("%region%", region).
                                replaceAll("%mag%", mag).
                                replaceAll("%depth%", depth).
                                replaceAll("%shindo%", intensity),
                        sichuan_subtitle_message.
                                replaceAll("%report_time%", report_time).
                                replaceAll("%origin_time%", origin_time).
                                replaceAll("%num%", num).
                                replaceAll("%lat%", lat).
                                replaceAll("%lon%", lon).
                                replaceAll("%region%", region).
                                replaceAll("%mag%", mag).
                                replaceAll("%depth%", depth).
                                replaceAll("%shindo%", intensity),
                        -1, -1, -1
                );
            }
        }
        if (alert_bool) {
            playSound(sc_alert_sound_type, sc_alert_sound_volume, sc_alert_sound_pitch);
        }
    }

    private static void finalAction(String origin_time, String region, String mag, String depth, String shindo, String info) {
        Bukkit.broadcastMessage(
                final_broadcast_message.
                        replaceAll("%origin_time%", origin_time).
                        replaceAll("%region%", region).
                        replaceAll("%mag%", mag).
                        replaceAll("%depth%", depth).
                        replaceAll("%shindo%", getShindoColor(shindo)).
                        replaceAll("%info%", info)
        );
    }

    private static String getShindoColor(String shindo) {
        if (Objects.equals(shindo, "1")) {
            shindo = ChatColor.translateAlternateColorCodes('&', shindo_color[1]) + shindo;
        } else if (Objects.equals(shindo, "2")) {
            shindo = ChatColor.translateAlternateColorCodes('&', shindo_color[2]) + shindo;
        } else if (Objects.equals(shindo, "3")) {
            shindo = ChatColor.translateAlternateColorCodes('&', shindo_color[3]) + shindo;
        } else if (Objects.equals(shindo, "4")) {
            shindo = ChatColor.translateAlternateColorCodes('&', shindo_color[4]) + shindo;
        } else if (Objects.equals(shindo, "5弱")) {
            shindo = ChatColor.translateAlternateColorCodes('&', shindo_color[5]) + shindo;
        } else if (Objects.equals(shindo, "5強")) {
            shindo = ChatColor.translateAlternateColorCodes('&', shindo_color[6]) + shindo;
        } else if (Objects.equals(shindo, "6弱")) {
            shindo = ChatColor.translateAlternateColorCodes('&', shindo_color[7]) + shindo;
        } else if (Objects.equals(shindo, "6強")) {
            shindo = ChatColor.translateAlternateColorCodes('&', shindo_color[8]) + shindo;
        } else if (Objects.equals(shindo, "7")) {
            shindo = ChatColor.translateAlternateColorCodes('&', shindo_color[9]) + shindo;
        } else {
            shindo = ChatColor.translateAlternateColorCodes('&', shindo_color[0]) + shindo;
        }
        return shindo;
    }

    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            sender.sendMessage("§a[MCEEW] Version: v" + version);
            sender.sendMessage("§a[MCEEW] §3/eew§a - List commands.");
            sender.sendMessage("§a[MCEEW] §3/eew test§a - Run EEW test.");
            sender.sendMessage("§a[MCEEW] §3/eew final§a - Get the final report.");
            sender.sendMessage("§a[MCEEW] §3/eew reload§a - Reload configuration.");
            return true;
        } else if (args[0].equalsIgnoreCase("reload")) {
            if (sender.isOp()) {
                this.loadEew(false);
                sender.sendMessage("§a[MCEEW] Configuration reload successfully.");
                return true;
            }
        } else if (args[0].equalsIgnoreCase("final")) {
            if (final_md5 != null) {
                sender.sendMessage(
                        final_broadcast_message.
                                replaceAll("%origin_time%", final_info.get(0)).
                                replaceAll("%region%", final_info.get(1)).
                                replaceAll("%mag%", final_info.get(2)).
                                replaceAll("%depth%", final_info.get(3)).
                                replaceAll("%shindo%", getShindoColor(final_info.get(4))).
                                replaceAll("%info%", final_info.get(5))
                );
            }
            return true;
        } else if (args[0].equalsIgnoreCase("test")) {
            if (sender.isOp() && args.length == 2) {
                if (args[1].equalsIgnoreCase("forecast")) {
                    eewTest(0);
                    return true;
                } else if (args[1].equalsIgnoreCase("alert")) {
                    eewTest(1);
                    return true;
                } else if (args[1].equalsIgnoreCase("final")) {
                    eewTest(2);
                    return true;
                } else if (args[1].equalsIgnoreCase("sc")) {
                    eewTest(3);
                    return true;
                }
            } else {
                sender.sendMessage("§a[MCEEW] §3/eew test alert§a - Run Alert EEW test.");
                sender.sendMessage("§a[MCEEW] §3/eew test forecast§a - Run Forecast EEW test.");
                sender.sendMessage("§a[MCEEW] §3/eew test final§a - Run Final Report test.");
                sender.sendMessage("§a[MCEEW] §3/eew test sc§a - Run Sichuan EEW test.");
                return true;
            }
        }
        return false;
    }

    private void loadEew(boolean first) {
        this.cancelScheduler();
        this.saveDefaultConfig();
        this.reloadConfig();
        time_format = this.getConfig().getString("time_format");
        time_format_final = this.getConfig().getString("time_format_final");
        broadcast_bool = this.getConfig().getBoolean("Action.broadcast");
        title_bool = this.getConfig().getBoolean("Action.title");
        alert_bool = this.getConfig().getBoolean("Action.alert");
        enable_jma_bool = this.getConfig().getBoolean("enable_jma");
        notification_bool = this.getConfig().getBoolean("Action.notification");
        alert_broadcast_message = ChatColor.translateAlternateColorCodes('&', Objects.requireNonNull(this.getConfig().getString("Message.Alert.broadcast")));
        alert_title_message = ChatColor.translateAlternateColorCodes('&', Objects.requireNonNull(this.getConfig().getString("Message.Alert.title")));
        alert_subtitle_message = ChatColor.translateAlternateColorCodes('&', Objects.requireNonNull(this.getConfig().getString("Message.Alert.subtitle")));
        forecast_broadcast_message = ChatColor.translateAlternateColorCodes('&', Objects.requireNonNull(this.getConfig().getString("Message.Forecast.broadcast")));
        forecast_title_message = ChatColor.translateAlternateColorCodes('&', Objects.requireNonNull(this.getConfig().getString("Message.Forecast.title")));
        forecast_subtitle_message = ChatColor.translateAlternateColorCodes('&', Objects.requireNonNull(this.getConfig().getString("Message.Forecast.subtitle")));
        final_broadcast_message = ChatColor.translateAlternateColorCodes('&', Objects.requireNonNull(this.getConfig().getString("Message.Final.broadcast")));
        sichuan_broadcast_message = ChatColor.translateAlternateColorCodes('&', Objects.requireNonNull(this.getConfig().getString("Message.Sichuan.broadcast")));
        sichuan_title_message = ChatColor.translateAlternateColorCodes('&', Objects.requireNonNull(this.getConfig().getString("Message.Sichuan.title")));
        sichuan_subtitle_message = ChatColor.translateAlternateColorCodes('&', Objects.requireNonNull(this.getConfig().getString("Message.Sichuan.subtitle")));
        shindo_color = this.getConfig().getStringList("Color.Shindo").toArray(new String[0]);
        intensity_color = this.getConfig().getStringList("Color.Intensity").toArray(new String[0]);
        alert_alert_sound_type = this.getConfig().getString("Sound.Alert.type");
        alert_alert_sound_volume = this.getConfig().getDouble("Sound.Alert.volume");
        alert_alert_sound_pitch = this.getConfig().getDouble("Sound.Alert.pitch");
        forecast_alert_sound_type = this.getConfig().getString("Sound.Forecast.type");
        forecast_alert_sound_volume = this.getConfig().getDouble("Sound.Forecast.volume");
        forecast_alert_sound_pitch = this.getConfig().getDouble("Sound.Forecast.pitch");
        sc_alert_sound_type = this.getConfig().getString("Sound.Sichuan.type");
        sc_alert_sound_volume = this.getConfig().getDouble("Sound.Sichuan.volume");
        sc_alert_sound_pitch = this.getConfig().getDouble("Sound.Sichuan.pitch");
        this.mceewScheduler(this.getConfig().getBoolean("EEW"), this.getConfig().getBoolean("enable_jp"), this.getConfig().getBoolean("Action.final"), this.getConfig().getBoolean("enable_sc"), first);
    }

    @Override
    public void onDisable() {
        this.cancelScheduler();
    }
}
