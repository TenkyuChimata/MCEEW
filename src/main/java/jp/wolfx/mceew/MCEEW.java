package jp.wolfx.mceew;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
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
    private static boolean debug_bool;
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
    private static String cwa_broadcast_message;
    private static String cwa_title_message;
    private static String cwa_subtitle_message;
    private static String alert_alert_sound_type;
    private static double alert_alert_sound_volume;
    private static double alert_alert_sound_pitch;
    private static String forecast_alert_sound_type;
    private static double forecast_alert_sound_volume;
    private static double forecast_alert_sound_pitch;
    private static String sc_alert_sound_type;
    private static double sc_alert_sound_volume;
    private static double sc_alert_sound_pitch;
    private static String cwa_alert_sound_type;
    private static double cwa_alert_sound_volume;
    private static double cwa_alert_sound_pitch;
    private static String OriginalText = null;
    private static String final_md5 = null;
    private static String EventID = null;
    private static String cwaTS = null;
    private static String EewDataTS = null;
    private static JsonObject jmaEewData = null;
    private static JsonObject jmaEqlistData = null;
    private static JsonObject scEewData = null;
    private static JsonObject cwaEewData = null;
    private static final ArrayList<String> final_info = new ArrayList<>();
    private static final ArrayList<String> sc_info = new ArrayList<>();
    private static final ArrayList<String> cwa_info = new ArrayList<>();
    private final String version = this.getDescription().getVersion();
    private static final boolean folia = isFolia();

    @Override
    public void onEnable() {
        this.loadEew(true);
        new Metrics(this, 17261);
    }

    private static boolean isFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.scheduler.AsyncScheduler");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private void mceewScheduler(boolean jpEewBoolean, boolean finalBoolean, boolean scEewBoolean, boolean cwaEewBoolean, boolean updaterBoolean) {
        if (!folia) {
            Bukkit.getScheduler().runTaskTimerAsynchronously(this, this::getEewData, 20L, 20L);
            Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> eewChecker(jpEewBoolean, finalBoolean, scEewBoolean, cwaEewBoolean), 20L, 20L);
            if (updaterBoolean) {
                Bukkit.getLogger().info("[MCEEW] Using Bukkit API for Scheduler");
                Bukkit.getScheduler().runTaskAsynchronously(this, this::updater);
            }
        } else {
            Plugin plugin = this;
            Bukkit.getAsyncScheduler().runAtFixedRate(plugin, task1 -> getEewData(), 1L, 1L, TimeUnit.SECONDS);
            Bukkit.getAsyncScheduler().runAtFixedRate(plugin, task2 -> eewChecker(jpEewBoolean, finalBoolean, scEewBoolean, cwaEewBoolean), 1L, 1L, TimeUnit.SECONDS);
            if (updaterBoolean) {
                Bukkit.getLogger().info("[MCEEW] Using Folia API for Scheduler");
                Bukkit.getAsyncScheduler().runNow(plugin, task3 -> updater());
            }
        }
    }

    private void cancelScheduler() {
        if (!folia) {
            Bukkit.getScheduler().cancelTasks(this);
        } else {
            Bukkit.getAsyncScheduler().cancelTasks(this);
        }
    }

    private static String getAPI(int timeout, String uri) {
        String data = null;
        StringBuilder raw_data = new StringBuilder();
        try {
            URL url = new URL(uri);
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
            if (debug_bool) {
                Bukkit.getLogger().warning("[MCEEW] API connection failed, retrying...");
                Bukkit.getLogger().warning(String.valueOf(e));
            }
        }
        return data;
    }

    private void updater() {
        String responseData = getAPI(5000, "https://tenkyuchimata.github.io/MCEEW/version.json");
        if (responseData != null) {
            JsonObject json = JsonParser.parseString(responseData).getAsJsonObject();
            String api_version = json.get("version").getAsString();
            if (Integer.parseInt(api_version.replaceAll("\\.", "")) > Integer.parseInt(version.replaceAll("-b.*", "").replaceAll("\\.", ""))) {
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

    private void getEewData() {
        // Do not use this URL for your program, check out https://api.wolfx.jp instead.
        String responseData = getAPI(1500, "https://api.wolfx.jp/mceew_data.json");
        if (responseData != null) {
            JsonObject json = JsonParser.parseString(responseData).getAsJsonObject();
            if (!Objects.equals(json.get("create_at").getAsString(), EewDataTS)) {
                jmaEewData = json.get("jma_eew").getAsJsonObject();
                jmaEqlistData = json.get("jma_eqlist").getAsJsonObject();
                scEewData = json.get("sc_eew").getAsJsonObject();
                cwaEewData = json.get("cwb_eew").getAsJsonObject();
                EewDataTS = json.get("create_at").getAsString();
            }
        }
    }

    private void eewChecker(boolean jpEewBoolean, boolean finalBoolean, boolean scEewBoolean, boolean cwaEewBoolean) {
        if (jpEewBoolean && jmaEewData != null) {
            jmaEewExecute();
        }
        if (jmaEqlistData != null) {
            jmaEqlistExecute(finalBoolean);
        }
        if (scEewData != null) {
            scEewExecute(scEewBoolean);
        }
        if (cwaEewData != null) {
            cwaEewExecute(cwaEewBoolean);
        }
    }

    private static void jmaEewExecute() {
        if (!Objects.equals(jmaEewData.get("OriginalText").getAsString(), OriginalText)) {
            String type = "";
            String flag = jmaEewData.get("Title").getAsString().substring(7, 9);
            String report_time = jmaEewData.get("AnnouncedTime").getAsString();
            String num = jmaEewData.get("Serial").getAsString();
            String lat = jmaEewData.get("Latitude").getAsString();
            String lon = jmaEewData.get("Longitude").getAsString();
            String region = jmaEewData.get("Hypocenter").getAsString();
            String mag = jmaEewData.get("Magunitude").getAsString();
            String depth = jmaEewData.get("Depth").getAsString() + "km";
            String shindo = jmaEewData.get("MaxIntensity").getAsString();
            String origin_time = getDate("yyyy/MM/dd HH:mm:ss", time_format, "Asia/Tokyo", jmaEewData.get("OriginTime").getAsString());
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
            if (OriginalText != null) {
                if (debug_bool) {
                    Bukkit.getLogger().info("[MCEEW] Japan EEW detected.");
                }
                eewAction(flag, report_time, origin_time, num, lat, lon, region, mag, depth, getShindoColor(shindo), type);
            }
            OriginalText = jmaEewData.get("OriginalText").getAsString();
        } else {
            if (debug_bool) {
                Bukkit.getLogger().info("[MCEEW] No Japan EEW issued.");
            }
        }
    }

    private static void jmaEqlistExecute(Boolean finalBoolean) {
        if (!Objects.equals(jmaEqlistData.get("md5").getAsString(), final_md5)) {
            String time_str = jmaEqlistData.get("No1").getAsJsonObject().get("time").getAsString();
            String region = jmaEqlistData.get("No1").getAsJsonObject().get("location").getAsString();
            String mag = jmaEqlistData.get("No1").getAsJsonObject().get("magnitude").getAsString();
            String depth = jmaEqlistData.get("No1").getAsJsonObject().get("depth").getAsString();
            String latitude = jmaEqlistData.get("No1").getAsJsonObject().get("latitude").getAsString();
            String longitude = jmaEqlistData.get("No1").getAsJsonObject().get("longitude").getAsString();
            String shindo = jmaEqlistData.get("No1").getAsJsonObject().get("shindo").getAsString();
            String info = jmaEqlistData.get("No1").getAsJsonObject().get("info").getAsString();
            String origin_time = getDate("yyyy/MM/dd HH:mm", time_format_final, "Asia/Tokyo", time_str);
            if (final_md5 != null) {
                if (debug_bool) {
                    Bukkit.getLogger().info("[MCEEW] Japan final report updated.");
                }
                if (finalBoolean) {
                    Bukkit.broadcastMessage(
                            final_broadcast_message.
                                    replaceAll("%origin_time%", origin_time).
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
            final_md5 = jmaEqlistData.get("md5").getAsString();
            final_info.clear();
            final_info.add(origin_time);
            final_info.add(region);
            final_info.add(mag);
            final_info.add(depth);
            final_info.add(latitude);
            final_info.add(longitude);
            final_info.add(shindo);
            final_info.add(info);
        } else {
            if (debug_bool) {
                Bukkit.getLogger().info("[MCEEW] No Japan final report update.");
            }
        }
    }

    private static void scEewExecute(Boolean scEewBoolean) {
        if (!Objects.equals(scEewData.get("EventID").getAsString(), EventID)) {
            String report_time = scEewData.get("ReportTime").getAsString();
            String num = scEewData.get("ReportNum").getAsString();
            String lat = scEewData.get("Latitude").getAsString();
            String lon = scEewData.get("Longitude").getAsString();
            String region = scEewData.get("HypoCenter").getAsString();
            String mag = scEewData.get("Magunitude").getAsString();
            String depth;
            if (!scEewData.get("Depth").isJsonNull()) {
                depth = scEewData.get("Depth").getAsString() + "km";
            } else {
                depth = "10km";
            }
            String intensity = String.valueOf(Math.round(Float.parseFloat(scEewData.get("MaxIntensity").getAsString())));
            String origin_time = getDate("yyyy-MM-dd HH:mm:ss", time_format, "Asia/Shanghai", scEewData.get("OriginTime").getAsString());
            if (EventID != null) {
                if (debug_bool) {
                    Bukkit.getLogger().info("[MCEEW] Sichuan EEW detected.");
                }
                if (scEewBoolean) {
                    scEewAction(report_time, origin_time, num, lat, lon, region, mag, depth, getIntensityColor(intensity));
                }
            }
            EventID = scEewData.get("EventID").getAsString();
            sc_info.clear();
            sc_info.add(report_time);
            sc_info.add(origin_time);
            sc_info.add(num);
            sc_info.add(lat);
            sc_info.add(lon);
            sc_info.add(region);
            sc_info.add(mag);
            sc_info.add(depth);
            sc_info.add(intensity);
        } else {
            if (debug_bool) {
                Bukkit.getLogger().info("[MCEEW] No Sichuan EEW issued.");
            }
        }
    }

    private static void cwaEewExecute(Boolean cwaEewBoolean) {
        if (!Objects.equals(cwaEewData.get("ReportTime").getAsString(), cwaTS)) {
            String report_time = cwaEewData.get("ReportTime").getAsString();
            String num = cwaEewData.get("ReportNum").getAsString();
            String lat = cwaEewData.get("Latitude").getAsString();
            String lon = cwaEewData.get("Longitude").getAsString();
            String region = cwaEewData.get("HypoCenter").getAsString();
            String mag = cwaEewData.get("Magunitude").getAsString();
            String depth = cwaEewData.get("Depth").getAsString() + "km";
            String origin_time = getDate("yyyy-MM-dd HH:mm:ss", time_format, "Asia/Shanghai", cwaEewData.get("OriginTime").getAsString());
            if (cwaTS != null) {
                if (debug_bool) {
                    Bukkit.getLogger().info("[MCEEW] Taiwan EEW detected.");
                }
                if (cwaEewBoolean) {
                    cwaEewAction(report_time, origin_time, num, lat, lon, region, mag, depth);
                }
            }
            cwaTS = cwaEewData.get("ReportTime").getAsString();
            cwa_info.clear();
            cwa_info.add(report_time);
            cwa_info.add(origin_time);
            cwa_info.add(num);
            cwa_info.add(lat);
            cwa_info.add(lon);
            cwa_info.add(region);
            cwa_info.add(mag);
            cwa_info.add(depth);
        } else {
            if (debug_bool) {
                Bukkit.getLogger().info("[MCEEW] No Taiwan EEW issued.");
            }
        }
    }

    private static void getEewInfo(int flag, CommandSender sender) {
        if (flag == 1) {
            if (EventID != null) {
                sender.sendMessage(
                        sichuan_broadcast_message.
                                replaceAll("%report_time%", sc_info.get(0)).
                                replaceAll("%origin_time%", sc_info.get(1)).
                                replaceAll("%num%", sc_info.get(2)).
                                replaceAll("%lat%", sc_info.get(3)).
                                replaceAll("%lon%", sc_info.get(4)).
                                replaceAll("%region%", sc_info.get(5)).
                                replaceAll("%mag%", sc_info.get(6)).
                                replaceAll("%depth%", sc_info.get(7)).
                                replaceAll("%shindo%", getIntensityColor(sc_info.get(8)))
                );
            }
        } else if (flag == 2) {
            if (cwaTS != null) {
                sender.sendMessage(
                        cwa_broadcast_message.
                                replaceAll("%report_time%", cwa_info.get(0)).
                                replaceAll("%origin_time%", cwa_info.get(1)).
                                replaceAll("%num%", cwa_info.get(2)).
                                replaceAll("%lat%", cwa_info.get(3)).
                                replaceAll("%lon%", cwa_info.get(4)).
                                replaceAll("%region%", cwa_info.get(5)).
                                replaceAll("%mag%", cwa_info.get(6)).
                                replaceAll("%depth%", cwa_info.get(7))
                );
            }
        } else {
            if (final_md5 != null) {
                sender.sendMessage(
                        final_broadcast_message.
                                replaceAll("%origin_time%", final_info.get(0)).
                                replaceAll("%region%", final_info.get(1)).
                                replaceAll("%mag%", final_info.get(2)).
                                replaceAll("%depth%", final_info.get(3)).
                                replaceAll("%lat%", final_info.get(4)).
                                replaceAll("%lon%", final_info.get(5)).
                                replaceAll("%shindo%", getShindoColor(final_info.get(6))).
                                replaceAll("%info%", final_info.get(7))
                );
            }
        }
    }

    private static void eewTest(int flag) {
        if (flag == 1) {
            String flags = "警報";
            String origin_time_str = "2022/03/16 23:34:26";
            String report_time = "2022/03/16 23:36:00";
            String num = "17";
            String lat = "37.6";
            String lon = "141.7";
            String region = "福島県沖";
            String mag = "6.0";
            String depth = "60km";
            String shindo = "5弱";
            String type = "最終報";
            String origin_time = getDate("yyyy/MM/dd HH:mm:ss", time_format, "Asia/Tokyo", origin_time_str);
            eewAction(flags, report_time, origin_time, num, lat, lon, region, mag, depth, getShindoColor(shindo), type);
        } else if (flag == 2) {
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
            scEewAction(report_time, origin_time, num, lat, lon, region, mag, depth, getIntensityColor(intensity));
        } else if (flag == 3) {
            String origin_time_str = "2023-06-23 03:45:34";
            String report_time = "2023-06-23 03:45:54";
            String num = "1";
            String lat = "24.64";
            String lon = "121.57";
            String region = "宜蘭縣三星鄉";
            String mag = "4.5";
            String depth = "40km";
            String origin_time = getDate("yyyy-MM-dd HH:mm:ss", time_format, "Asia/Shanghai", origin_time_str);
            cwaEewAction(report_time, origin_time, num, lat, lon, region, mag, depth);
        } else {
            String flags = "予報";
            String origin_time_str = "2023/07/01 23:38:53";
            String report_time = "2023/07/01 23:39:50";
            String num = "10";
            String lat = "35.5";
            String lon = "141.2";
            String region = "千葉県東方沖";
            String mag = "5.0";
            String depth = "10km";
            String shindo = "3";
            String type = "";
            String origin_time = getDate("yyyy/MM/dd HH:mm:ss", time_format, "Asia/Tokyo", origin_time_str);
            eewAction(flags, report_time, origin_time, num, lat, lon, region, mag, depth, getShindoColor(shindo), type);
        }
        Bukkit.broadcastMessage("§eWarning: This is a Earthquake Early Warning issued test.");
    }

    private static void eewAction(String flag, String report_time, String origin_time, String num, String lat, String lon, String region, String mag, String depth, String shindo, String type) {
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

    private static void cwaEewAction(String report_time, String origin_time, String num, String lat, String lon, String region, String mag, String depth) {
        if (broadcast_bool) {
            Bukkit.broadcastMessage(
                    cwa_broadcast_message.
                            replaceAll("%report_time%", report_time).
                            replaceAll("%origin_time%", origin_time).
                            replaceAll("%num%", num).
                            replaceAll("%lat%", lat).
                            replaceAll("%lon%", lon).
                            replaceAll("%region%", region).
                            replaceAll("%mag%", mag).
                            replaceAll("%depth%", depth)
            );
        }
        if (title_bool) {
            for (Player player : Bukkit.getServer().getOnlinePlayers()) {
                player.sendTitle(
                        cwa_title_message.
                                replaceAll("%report_time%", report_time).
                                replaceAll("%origin_time%", origin_time).
                                replaceAll("%num%", num).
                                replaceAll("%lat%", lat).
                                replaceAll("%lon%", lon).
                                replaceAll("%region%", region).
                                replaceAll("%mag%", mag).
                                replaceAll("%depth%", depth),
                        cwa_subtitle_message.
                                replaceAll("%report_time%", report_time).
                                replaceAll("%origin_time%", origin_time).
                                replaceAll("%num%", num).
                                replaceAll("%lat%", lat).
                                replaceAll("%lon%", lon).
                                replaceAll("%region%", region).
                                replaceAll("%mag%", mag).
                                replaceAll("%depth%", depth),
                        -1, -1, -1
                );
            }
        }
        if (alert_bool) {
            playSound(cwa_alert_sound_type, cwa_alert_sound_volume, cwa_alert_sound_pitch);
        }
    }

    private static String getShindoColor(String shindo) {
        String[] shindo_color = new String[]{"§f", "§7", "§b", "§9", "§a", "§e", "§6", "§c", "§4", "§d"};
        if (Objects.equals(shindo, "1")) {
            return shindo_color[1] + shindo;
        } else if (Objects.equals(shindo, "2")) {
            return shindo_color[2] + shindo;
        } else if (Objects.equals(shindo, "3")) {
            return shindo_color[3] + shindo;
        } else if (Objects.equals(shindo, "4")) {
            return shindo_color[4] + shindo;
        } else if (Objects.equals(shindo, "5弱")) {
            return shindo_color[5] + shindo;
        } else if (Objects.equals(shindo, "5強")) {
            return shindo_color[6] + shindo;
        } else if (Objects.equals(shindo, "6弱")) {
            return shindo_color[7] + shindo;
        } else if (Objects.equals(shindo, "6強")) {
            return shindo_color[8] + shindo;
        } else if (Objects.equals(shindo, "7")) {
            return shindo_color[9] + shindo;
        } else {
            return shindo_color[0] + shindo;
        }
    }

    private static String getIntensityColor(String intensity) {
        String[] intensity_color = new String[]{"§f", "§7", "§b", "§3", "§9", "§a", "§2", "§e", "§6", "§c", "§4", "§d", "§5"};
        return intensity_color[Integer.parseInt(intensity)] + intensity;
    }

    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            sender.sendMessage("§a[MCEEW] Plugin Version: v" + version);
            sender.sendMessage("§a[MCEEW] §3/eew§a - List commands.");
            sender.sendMessage("§a[MCEEW] §3/eew test§a - Run EEW send test.");
            sender.sendMessage("§a[MCEEW] §3/eew info§a - Get earthquake information.");
            sender.sendMessage("§a[MCEEW] §3/eew reload§a - Reload configuration.");
            return true;
        } else if (args[0].equalsIgnoreCase("reload") && sender.isOp()) {
            this.loadEew(false);
            sender.sendMessage("§a[MCEEW] Configuration reload successfully.");
            return true;
        } else if (args[0].equalsIgnoreCase("info")) {
            if (args.length == 2) {
                if (args[1].equalsIgnoreCase("jma")) {
                    getEewInfo(0, sender);
                    return true;
                } else if (args[1].equalsIgnoreCase("sc")) {
                    getEewInfo(1, sender);
                    return true;
                } else if (args[1].equalsIgnoreCase("cwa")) {
                    getEewInfo(2, sender);
                    return true;
                }
            } else {
                sender.sendMessage("§a[MCEEW] §3/eew info jma§a - Get Japan earthquake information.");
                sender.sendMessage("§a[MCEEW] §3/eew info sc§a - Get Sichuan earthquake information.");
                sender.sendMessage("§a[MCEEW] §3/eew info cwa§a - Get Taiwan earthquake information.");
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
                } else if (args[1].equalsIgnoreCase("cwa")) {
                    eewTest(3);
                    return true;
                }
            } else {
                sender.sendMessage("§a[MCEEW] §3/eew test forecast§a - Run Forecast EEW test.");
                sender.sendMessage("§a[MCEEW] §3/eew test alert§a - Run Alert EEW test.");
                sender.sendMessage("§a[MCEEW] §3/eew test sc§a - Run Sichuan EEW test.");
                sender.sendMessage("§a[MCEEW] §3/eew test cwa§a - Run Taiwan EEW test.");
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
        debug_bool = this.getConfig().getBoolean("Action.debug");
        alert_broadcast_message = Objects.requireNonNull(this.getConfig().getString("Message.Alert.broadcast")).replace("&", "§");
        alert_title_message = Objects.requireNonNull(this.getConfig().getString("Message.Alert.title")).replace("&", "§");
        alert_subtitle_message = Objects.requireNonNull(this.getConfig().getString("Message.Alert.subtitle")).replace("&", "§");
        forecast_broadcast_message = Objects.requireNonNull(this.getConfig().getString("Message.Forecast.broadcast")).replace("&", "§");
        forecast_title_message = Objects.requireNonNull(this.getConfig().getString("Message.Forecast.title")).replace("&", "§");
        forecast_subtitle_message = Objects.requireNonNull(this.getConfig().getString("Message.Forecast.subtitle")).replace("&", "§");
        final_broadcast_message = Objects.requireNonNull(this.getConfig().getString("Message.Final.broadcast")).replace("&", "§");
        sichuan_broadcast_message = Objects.requireNonNull(this.getConfig().getString("Message.Sichuan.broadcast")).replace("&", "§");
        sichuan_title_message = Objects.requireNonNull(this.getConfig().getString("Message.Sichuan.title")).replace("&", "§");
        sichuan_subtitle_message = Objects.requireNonNull(this.getConfig().getString("Message.Sichuan.subtitle")).replace("&", "§");
        cwa_broadcast_message = Objects.requireNonNull(this.getConfig().getString("Message.Taiwan.broadcast")).replace("&", "§");
        cwa_title_message = Objects.requireNonNull(this.getConfig().getString("Message.Taiwan.title")).replace("&", "§");
        cwa_subtitle_message = Objects.requireNonNull(this.getConfig().getString("Message.Taiwan.subtitle")).replace("&", "§");
        alert_alert_sound_type = this.getConfig().getString("Sound.Alert.type");
        alert_alert_sound_volume = this.getConfig().getDouble("Sound.Alert.volume");
        alert_alert_sound_pitch = this.getConfig().getDouble("Sound.Alert.pitch");
        forecast_alert_sound_type = this.getConfig().getString("Sound.Forecast.type");
        forecast_alert_sound_volume = this.getConfig().getDouble("Sound.Forecast.volume");
        forecast_alert_sound_pitch = this.getConfig().getDouble("Sound.Forecast.pitch");
        sc_alert_sound_type = this.getConfig().getString("Sound.Sichuan.type");
        sc_alert_sound_volume = this.getConfig().getDouble("Sound.Sichuan.volume");
        sc_alert_sound_pitch = this.getConfig().getDouble("Sound.Sichuan.pitch");
        cwa_alert_sound_type = this.getConfig().getString("Sound.Taiwan.type");
        cwa_alert_sound_volume = this.getConfig().getDouble("Sound.Taiwan.volume");
        cwa_alert_sound_pitch = this.getConfig().getDouble("Sound.Taiwan.pitch");
        this.mceewScheduler(this.getConfig().getBoolean("enable_jp"), this.getConfig().getBoolean("Action.final"), this.getConfig().getBoolean("enable_sc"), this.getConfig().getBoolean("enable_cwb"), first);
    }

    @Override
    public void onDisable() {
        this.cancelScheduler();
    }
}
