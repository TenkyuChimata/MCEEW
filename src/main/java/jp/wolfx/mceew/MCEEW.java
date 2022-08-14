package jp.wolfx.mceew;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
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
import org.checkerframework.checker.nullness.qual.NonNull;
import org.json.JSONObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Objects;
import java.util.TimeZone;

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

    @Override
    public void onEnable() {
        this.load_EEW();
    }

    private void EEW_Update() {
        if(EEW_bool) {
            (new BukkitRunnable() {
                public void run() {
                    try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
                        try {
                            HttpGet request = new HttpGet("https://api.wolfx.jp/nied_eew.json");
                            HttpResponse response = httpclient.execute(request);
                            if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
                                String responseData = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                                JSONObject json = new JSONObject(responseData);
                                if (json.getBoolean("eew")) {
                                    String type = "";
                                    String flag = json.getString("alertflg");
                                    String report_time = json.getString("report_time");
                                    String num = json.getString("report_num");
                                    String lat = json.getString("latitude");
                                    String lon = json.getString("longitude");
                                    String region = json.getString("region_name");
                                    String mag = json.getString("magunitude");
                                    String depth = json.getString("depth");
                                    String shindo = json.getString("calcintensity");
                                    SimpleDateFormat origin_time1 = new SimpleDateFormat("yyyyMMddHHmmss");
                                    origin_time1.setTimeZone(TimeZone.getTimeZone("Asia/Tokyo"));
                                    Date origin_time2 = origin_time1.parse(json.getString("origin_time"));
                                    String origin_time = new SimpleDateFormat(time_format).format(origin_time2);
                                    if (json.getBoolean("is_cancel")) {
                                        type = "取消";
                                    }
                                    if (json.getBoolean("is_training")) {
                                        if (json.getBoolean("is_final")) {
                                            type = "訓練 (最終報)";
                                        } else {
                                            type = "訓練";
                                        }
                                    }
                                    if (json.getBoolean("is_final")) {
                                        type = "最終報";
                                    }
                                    if (notification_bool) {
                                        Bukkit.getLogger().info("[MCEEW] Earthquake Warning detected.");
                                    }
                                    MCEEW.EEW_Action(flag, origin_time, report_time, num, lat, lon, region, mag, depth, shindo, type);
                                } else {
                                    if (notification_bool) {
                                        Bukkit.getLogger().info("[MCEEW] No Earthquake Warning issued.");
                                    }
                                }
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        } catch (ParseException e) {
                            throw new RuntimeException(e);
                        } finally {
                            httpclient.close();
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
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
        MCEEW.EEW_Action(flag, origin_time, report_time, num, lat, lon, region, mag, depth, shindo, type);
    }

    private static void EEW_Action(String flag, String report_time, String origin_time, String num, String lat, String lon, String region, String mag, String depth, String shindo, String type) {
        if (broadcast_bool) {
            Bukkit.broadcastMessage(broadcast_message.replaceAll("%flag%", flag).replaceAll("%report_time%", report_time).replaceAll("%origin_time%", origin_time).replaceAll("%num%", num).replaceAll("%lat%", lat).replaceAll("%lon%", lon).replaceAll("%region%", region).replaceAll("%mag%", mag).replaceAll("%depth%", depth).replaceAll("%shindo%", shindo).replaceAll("%type%", type));
        }
        if (title_bool) {
            for(Player player : Bukkit.getServer().getOnlinePlayers()) {
                player.sendTitle(title_message.replaceAll("%flag%", flag).replaceAll("%report_time%", report_time).replaceAll("%origin_time%", origin_time).replaceAll("%num%", num).replaceAll("%lat%", lat).replaceAll("%lon%", lon).replaceAll("%region%", region).replaceAll("%mag%", mag).replaceAll("%depth%", depth).replaceAll("%shindo%", shindo).replaceAll("%type%", type), subtitle_message.replaceAll("%flag%", flag).replaceAll("%report_time%", report_time).replaceAll("%origin_time%", origin_time).replaceAll("%num%", num).replaceAll("%lat%", lat).replaceAll("%lon%", lon).replaceAll("%region%", region).replaceAll("%mag%", mag).replaceAll("%depth%", depth).replaceAll("%shindo%", shindo).replaceAll("%type%", type));
            }
        }
        if (alert_bool) {
            Sound alert_playedSound = Sound.valueOf(alert_sound_type);
            for(Player player : Bukkit.getServer().getOnlinePlayers()) {
                player.playSound(player.getLocation(), alert_playedSound, (float) alert_sound_volume, (float) alert_sound_pitch);
            }
        }
    }

    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command command, @NonNull String label, String[] args) {
        if (sender.isOp()) {
            if (args.length == 0) {
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
                    throw new RuntimeException(e);
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
