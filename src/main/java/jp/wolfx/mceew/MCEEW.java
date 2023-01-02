package jp.wolfx.mceew;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Objects;
import java.util.TimeZone;
import org.bstats.bukkit.Metrics;

public final class MCEEW extends JavaPlugin {

    private static boolean EEW_bool;
    private static boolean broadcast_bool;
    private static boolean title_bool;
    private static boolean alert_bool;
    private static boolean notification_bool;
    private static String time_format;
    private static String broadcast_message;
    private static String title_message;
    private static String subtitle_message;
    private static String alert_sound_type;
    private static double alert_sound_volume;
    private static double alert_sound_pitch;
    private static String update_report = null;

    @Override
    public void onEnable() {
        this.load_EEW();
        new Metrics(this, 17261);
    }

    private void EEW_Update() {
        if(EEW_bool) {
            RequestConfig httpclient_config = RequestConfig.custom()
                    .setConnectTimeout(1500)
                    .setConnectionRequestTimeout(1500)
                    .setSocketTimeout(1500)
                    .build();
            (new BukkitRunnable() {
                public void run() {
                    CloseableHttpClient httpclient = HttpClients.custom()
                            .setDefaultRequestConfig(httpclient_config)
                            .build();
                    try {
                        HttpGet request = new HttpGet("https://api.wolfx.jp/nied_eew.json");
                        HttpResponse response = httpclient.execute(request);
                        if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
                            String responseData = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                            JsonObject json = JsonParser.parseString(responseData).getAsJsonObject();
                            if (json.get("eew").getAsBoolean() && !Objects.equals(json.get("report_time").getAsString(), update_report)) {
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
                                SimpleDateFormat origin_time1 = new SimpleDateFormat("yyyyMMddHHmmss");
                                origin_time1.setTimeZone(TimeZone.getTimeZone("Asia/Tokyo"));
                                Date origin_time2 = origin_time1.parse(json.get("origin_time").getAsString());
                                String origin_time = new SimpleDateFormat(time_format).format(origin_time2);
                                if (json.get("is_cancel").getAsBoolean()) {
                                    type = "取消";
                                }
                                if (json.get("is_training").getAsBoolean()) {
                                    if (json.get("is_final").getAsBoolean()) {
                                        type = "訓練 (最終報)";
                                    } else {
                                        type = "訓練";
                                    }
                                }
                                if (json.get("is_final").getAsBoolean()) {
                                    type = "最終報";
                                }
                                if (notification_bool) {
                                    Bukkit.getLogger().info("[MCEEW] Earthquake Warning detected.");
                                }
                                MCEEW.EEW_Action(flag, report_time, origin_time, num, lat, lon, region, mag, depth, shindo, type);
                                update_report = report_time;
                            } else {
                                if (notification_bool) {
                                    Bukkit.getLogger().info("[MCEEW] No Earthquake Warning issued.");
                                }
                            }
                        }
                    } catch (IllegalStateException | IOException | ParseException e) {
                        if (notification_bool) {
                            Bukkit.getLogger().warning("[MCEEW] NIED API Connection failed, retrying..");
                        }
                    } finally {
                        try {
                            httpclient.close();
                        } catch (IOException e) {
                            if (notification_bool) {
                                Bukkit.getLogger().warning("[MCEEW] Unable to close connection, retrying..");
                            }
                        }
                    }
                }
            }).runTaskTimerAsynchronously(this, 20L, 20L);
        }
    }

    private void EEW_Test() throws ParseException {
        String flag = "警報";
        String origin_time_str = "20110311144617";
        String report_time = "2011/03/11 14:48:36";
        String num = "15";
        String lat = "38.1";
        String lon = "142.6";
        String region = "三陸沖";
        String mag = "8.1";
        String depth = "10km";
        String shindo = "6弱";
        String type = "最終報";
        SimpleDateFormat origin_time1 = new SimpleDateFormat("yyyyMMddHHmmss");
        origin_time1.setTimeZone(TimeZone.getTimeZone("Asia/Tokyo"));
        Date origin_time2 = origin_time1.parse(origin_time_str);
        String origin_time = new SimpleDateFormat(time_format).format(origin_time2);
        MCEEW.EEW_Action(flag, report_time, origin_time, num, lat, lon, region, mag, depth, shindo, type);
    }

    private static void EEW_Action(String flag, String report_time, String origin_time, String num, String lat, String lon, String region, String mag, String depth, String shindo, String type) {
        if (broadcast_bool) {
            Bukkit.broadcastMessage(broadcast_message.replaceAll("%flag%", flag).replaceAll("%report_time%", report_time).replaceAll("%origin_time%", origin_time).replaceAll("%num%", num).replaceAll("%lat%", lat).replaceAll("%lon%", lon).replaceAll("%region%", region).replaceAll("%mag%", mag).replaceAll("%depth%", depth).replaceAll("%shindo%", shindo).replaceAll("%type%", type));
        }
        if (title_bool) {
            for(Player player : Bukkit.getServer().getOnlinePlayers()) {
                player.sendTitle(title_message.replaceAll("%flag%", flag).replaceAll("%report_time%", report_time).replaceAll("%origin_time%", origin_time).replaceAll("%num%", num).replaceAll("%lat%", lat).replaceAll("%lon%", lon).replaceAll("%region%", region).replaceAll("%mag%", mag).replaceAll("%depth%", depth).replaceAll("%shindo%", shindo).replaceAll("%type%", type), subtitle_message.replaceAll("%flag%", flag).replaceAll("%report_time%", report_time).replaceAll("%origin_time%", origin_time).replaceAll("%num%", num).replaceAll("%lat%", lat).replaceAll("%lon%", lon).replaceAll("%region%", region).replaceAll("%mag%", mag).replaceAll("%depth%", depth).replaceAll("%shindo%", shindo).replaceAll("%type%", type), -1, -1, -1);
            }
        }
        if (alert_bool) {
            Sound alert_playedSound = Sound.valueOf(alert_sound_type);
            for(Player player : Bukkit.getServer().getOnlinePlayers()) {
                player.playSound(player.getLocation(), alert_playedSound, (float) alert_sound_volume, (float) alert_sound_pitch);
            }
        }
    }

    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (sender.isOp()) {
            if (args.length == 0) {
                sender.sendMessage("[MCEEW] Version: v" + this.getDescription().getVersion());
                sender.sendMessage("[MCEEW] List commands /eew");
                sender.sendMessage("[MCEEW] Run EEW test /eew test");
                sender.sendMessage("[MCEEW] Reload configuration /eew reload");
                return true;
            } else if (args[0].equalsIgnoreCase("reload")) {
                this.load_EEW();
                sender.sendMessage("[MCEEW] Configure reload successfully!");
                return true;
            } else if (args[0].equalsIgnoreCase("test")) {
                try {
                    this.EEW_Test();
                    return true;
                } catch (ParseException e) {
                    Bukkit.getLogger().warning("[MCEEW] Unknown error.");
                }
            }
        }
        return false;
    }

    private void load_EEW() {
        Bukkit.getScheduler().cancelTasks(this);
        this.saveDefaultConfig();
        this.reloadConfig();
        EEW_bool = this.getConfig().getBoolean("EEW");
        time_format = this.getConfig().getString("time_format");
        broadcast_bool = this.getConfig().getBoolean("Action.broadcast");
        title_bool = this.getConfig().getBoolean("Action.title");
        alert_bool = this.getConfig().getBoolean("Action.alert");
        notification_bool = this.getConfig().getBoolean("Action.notification");
        broadcast_message = ChatColor.translateAlternateColorCodes('&', Objects.requireNonNull(this.getConfig().getString("Message.broadcast")));
        title_message = ChatColor.translateAlternateColorCodes('&', Objects.requireNonNull(this.getConfig().getString("Message.title")));
        subtitle_message = ChatColor.translateAlternateColorCodes('&', Objects.requireNonNull(this.getConfig().getString("Message.subtitle")));
        alert_sound_type = this.getConfig().getString("Sound.type");
        alert_sound_volume = this.getConfig().getDouble("Sound.volume");
        alert_sound_pitch = this.getConfig().getDouble("Sound.pitch");
        this.EEW_Update();
    }

    @Override
    public void onDisable() {
        Bukkit.getScheduler().cancelTasks(this);
    }
}
