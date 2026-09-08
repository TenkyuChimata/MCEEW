package jp.wolfx.mceew;

import com.google.gson.JsonObject;
import jp.wolfx.mceew.format.EarthquakeTimeFormatter;
import jp.wolfx.mceew.format.LegacyTextFormatter;
import jp.wolfx.mceew.message.FujianEewEvent;
import jp.wolfx.mceew.message.JmaEewEvent;
import jp.wolfx.mceew.message.RegionalEewEvent;
import jp.wolfx.mceew.message.WolfxMessageRouter;
import jp.wolfx.mceew.notification.NotificationIntentFactory;
import jp.wolfx.mceew.notification.NotificationProfile;
import jp.wolfx.mceew.notification.NotificationSource;
import org.bukkit.configuration.file.FileConfiguration;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Objects;

/**
 * Owns the Bukkit runtime state and the platform-neutral message processing pipeline.
 */
final class BukkitMceewRuntime {
    private static final WolfxMessageRouter MESSAGE_ROUTER =
            new WolfxMessageRouter();

    private final EarthquakeInfoCache earthquakeInfoCache;
    private final BukkitNotificationDispatcher notificationDispatcher;

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

    BukkitMceewRuntime(EarthquakeInfoCache earthquakeInfoCache,
                       BukkitNotificationDispatcher notificationDispatcher) {
        this.earthquakeInfoCache = Objects.requireNonNull(
                earthquakeInfoCache, "earthquakeInfoCache");
        this.notificationDispatcher = Objects.requireNonNull(
                notificationDispatcher, "notificationDispatcher");
    }

    void applyConfiguration(FileConfiguration configuration) {
        jpEewBoolean = configuration.getBoolean("enable_jp");
        scEewBoolean = configuration.getBoolean("enable_sc");
        fjEewBoolean = configuration.getBoolean("enable_fj");
        cwaEewBoolean = configuration.getBoolean("enable_cwa");
        cencEewBoolean = configuration.getBoolean("enable_cenceew");
        cqEewBoolean = configuration.getBoolean("enable_cq");
        broadcastBool = configuration.getBoolean("Action.broadcast");
        titleBool = configuration.getBoolean("Action.title");
        alertBool = configuration.getBoolean("Action.alert");
        jmaEqlistBoolean = configuration.getBoolean("Action.jma");
        cencEqlistBoolean = configuration.getBoolean("Action.cenc");
        timeFormat = configuration.getString("time_format");
        alertBroadcastMessage = LegacyTextFormatter.legacyColors(Objects.requireNonNull(configuration.getString("Message.Alert.broadcast")));
        alertTitleMessage = LegacyTextFormatter.legacyColors(Objects.requireNonNull(configuration.getString("Message.Alert.title")));
        alertSubtitleMessage = LegacyTextFormatter.legacyColors(Objects.requireNonNull(configuration.getString("Message.Alert.subtitle")));
        forecastBroadcastMessage = LegacyTextFormatter.legacyColors(Objects.requireNonNull(configuration.getString("Message.Forecast.broadcast")));
        forecastTitleMessage = LegacyTextFormatter.legacyColors(Objects.requireNonNull(configuration.getString("Message.Forecast.title")));
        forecastSubtitleMessage = LegacyTextFormatter.legacyColors(Objects.requireNonNull(configuration.getString("Message.Forecast.subtitle")));
        jmaEqlistBroadcastMessage = LegacyTextFormatter.legacyColors(Objects.requireNonNull(configuration.getString("Message.Jma.broadcast")));
        cencEqlistBroadcastMessage = LegacyTextFormatter.legacyColors(Objects.requireNonNull(configuration.getString("Message.Cenc.broadcast")));
        sichuanBroadcastMessage = LegacyTextFormatter.legacyColors(Objects.requireNonNull(configuration.getString("Message.Sichuan.broadcast")));
        sichuanTitleMessage = LegacyTextFormatter.legacyColors(Objects.requireNonNull(configuration.getString("Message.Sichuan.title")));
        sichuanSubtitleMessage = LegacyTextFormatter.legacyColors(Objects.requireNonNull(configuration.getString("Message.Sichuan.subtitle")));
        fjBroadcastMessage = LegacyTextFormatter.legacyColors(Objects.requireNonNull(configuration.getString("Message.Fjea.broadcast")));
        fjTitleMessage = LegacyTextFormatter.legacyColors(Objects.requireNonNull(configuration.getString("Message.Fjea.title")));
        fjSubtitleMessage = LegacyTextFormatter.legacyColors(Objects.requireNonNull(configuration.getString("Message.Fjea.subtitle")));
        cwaBroadcastMessage = LegacyTextFormatter.legacyColors(Objects.requireNonNull(configuration.getString("Message.Cwa.broadcast")));
        cwaTitleMessage = LegacyTextFormatter.legacyColors(Objects.requireNonNull(configuration.getString("Message.Cwa.title")));
        cwaSubtitleMessage = LegacyTextFormatter.legacyColors(Objects.requireNonNull(configuration.getString("Message.Cwa.subtitle")));
        cencBroadcastMessage = LegacyTextFormatter.legacyColors(Objects.requireNonNull(configuration.getString("Message.CencEEW.broadcast")));
        cencTitleMessage = LegacyTextFormatter.legacyColors(Objects.requireNonNull(configuration.getString("Message.CencEEW.title")));
        cencSubtitleMessage = LegacyTextFormatter.legacyColors(Objects.requireNonNull(configuration.getString("Message.CencEEW.subtitle")));
        cqBroadcastMessage = LegacyTextFormatter.legacyColors(Objects.requireNonNull(configuration.getString("Message.Chongqing.broadcast")));
        cqTitleMessage = LegacyTextFormatter.legacyColors(Objects.requireNonNull(configuration.getString("Message.Chongqing.title")));
        cqSubtitleMessage = LegacyTextFormatter.legacyColors(Objects.requireNonNull(configuration.getString("Message.Chongqing.subtitle")));
        alertAlertSoundType = configuration.getString("Sound.Alert.type");
        alertAlertSoundVolume = configuration.getDouble("Sound.Alert.volume");
        alertAlertSoundPitch = configuration.getDouble("Sound.Alert.pitch");
        forecastAlertSoundType = configuration.getString("Sound.Forecast.type");
        forecastAlertSoundVolume = configuration.getDouble("Sound.Forecast.volume");
        forecastAlertSoundPitch = configuration.getDouble("Sound.Forecast.pitch");
        scAlertSoundType = configuration.getString("Sound.Sichuan.type");
        scAlertSoundVolume = configuration.getDouble("Sound.Sichuan.volume");
        scAlertSoundPitch = configuration.getDouble("Sound.Sichuan.pitch");
        fjAlertSoundType = configuration.getString("Sound.Fjea.type");
        fjAlertSoundVolume = configuration.getDouble("Sound.Fjea.volume");
        fjAlertSoundPitch = configuration.getDouble("Sound.Fjea.pitch");
        cwaAlertSoundType = configuration.getString("Sound.Cwa.type");
        cwaAlertSoundVolume = configuration.getDouble("Sound.Cwa.volume");
        cwaAlertSoundPitch = configuration.getDouble("Sound.Cwa.pitch");
        cencAlertSoundType = configuration.getString("Sound.CencEEW.type");
        cencAlertSoundVolume = configuration.getDouble("Sound.CencEEW.volume");
        cencAlertSoundPitch = configuration.getDouble("Sound.CencEEW.pitch");
        cqAlertSoundType = configuration.getString("Sound.Chongqing.type");
        cqAlertSoundVolume = configuration.getDouble("Sound.Chongqing.volume");
        cqAlertSoundPitch = configuration.getDouble("Sound.Chongqing.pitch");
    }

    void handleWebSocketMessage(String message) {
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

    void eewTest(int flag) {
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
        notificationDispatcher.deliverTestWarning(
                "§eWarning: This is an Earthquake Early Warning test.");
    }

    String earthquakeInfo(boolean cenc) {
        return cenc
                ? earthquakeInfoCache.formatCenc(cencEqlistBroadcastMessage)
                : earthquakeInfoCache.formatJma(jmaEqlistBroadcastMessage);
    }

    EarthquakeInfoCache earthquakeInfoCache() {
        return earthquakeInfoCache;
    }

    boolean isFresh(
            String reportTimeStr, String pattern, ZoneId zone, ZonedDateTime now) {
        return EarthquakeTimeFormatter.isFresh(reportTimeStr, pattern, zone, now);
    }

    String getShindoColor(String shindo) {
        return LegacyTextFormatter.shindo(shindo);
    }

    String getIntensityColor(String intensity) {
        return LegacyTextFormatter.intensity(intensity);
    }

    private String getDate(String pattern, String targetFormat, String timezone, String originTime) {
        return EarthquakeTimeFormatter.format(pattern, targetFormat, timezone, originTime);
    }

    private boolean isFresh(String reportTimeStr, String pattern, ZoneId zone) {
        return EarthquakeTimeFormatter.isFresh(reportTimeStr, pattern, zone);
    }

    private void jmaEewExecute(JmaEewEvent event) {
        String type = LegacyTextFormatter.jmaReportType(
                event.isTraining(), event.isAssumption(),
                event.isFinalReport(), event.isCancelled());
        String flag = event.getFlag();
        String reportTime = event.getReportTime();
        String num = event.getReportNumber();
        String lat = event.getLatitude();
        String lon = event.getLongitude();
        String region = event.getRegion();
        String mag = event.getMagnitude();
        String depth = LegacyTextFormatter.depthKilometers(event.getDepth());
        String shindo = event.getMaximumIntensity();
        String originTime = getDate("yyyy/MM/dd HH:mm:ss", timeFormat, "Asia/Tokyo", event.getOriginTime());
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
        NotificationIntentFactory.earthquakeList(
                NotificationSource.JMA_EARTHQUAKE_LIST,
                update == EarthquakeInfoCache.UpdateResult.CHANGED,
                enabled,
                () -> snapshot.format(jmaEqlistBroadcastMessage)
        ).ifPresent(notificationDispatcher::deliverEarthquakeList);
    }

    private void cencEqlistExecute(JsonObject data, boolean enabled) {
        JsonObject latest = data.get("No1").getAsJsonObject();
        String timeStr = latest.get("time").getAsString();
        String originTime = getDate("yyyy-MM-dd HH:mm:ss", timeFormat, "Asia/Shanghai", timeStr);
        String intensity = latest.get("intensity").getAsString();
        EarthquakeInfoCache.CencSnapshot snapshot = EarthquakeInfoCache.CencSnapshot.fromEqlist(
                data, originTime, getIntensityColor(intensity));
        EarthquakeInfoCache.UpdateResult update = earthquakeInfoCache.updateCenc(snapshot);
        NotificationIntentFactory.earthquakeList(
                NotificationSource.CENC_EARTHQUAKE_LIST,
                update == EarthquakeInfoCache.UpdateResult.CHANGED,
                enabled,
                () -> snapshot.format(cencEqlistBroadcastMessage)
        ).ifPresent(notificationDispatcher::deliverEarthquakeList);
    }

    private void scEewExecute(RegionalEewEvent event) {
        String reportTime = event.getReportTime();
        String num = event.getReportNumber();
        String lat = event.getLatitude();
        String lon = event.getLongitude();
        String region = event.getRegion();
        String mag = event.getMagnitude();
        String intensity = event.getMaximumIntensity();
        String depth = LegacyTextFormatter.depthKilometers(event.getDepth());
        String originTime = getDate("yyyy-MM-dd HH:mm:ss", timeFormat, "Asia/Shanghai", event.getOriginTime());
        if (isFresh(reportTime, "yyyy-MM-dd HH:mm:ss", ZoneId.of("Asia/Shanghai"))) {
            scEewAction(reportTime, originTime, num, lat, lon, region, mag, depth, getIntensityColor(intensity));
        }
    }

    private void fjEewExecute(FujianEewEvent event) {
        String type = LegacyTextFormatter.finalReportType(event.isFinalReport());
        String reportTime = event.getReportTime();
        String num = event.getReportNumber();
        String lat = event.getLatitude();
        String lon = event.getLongitude();
        String region = event.getRegion();
        String mag = event.getMagnitude();
        String originTime = getDate("yyyy-MM-dd HH:mm:ss", timeFormat, "Asia/Shanghai", event.getOriginTime());
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
        String depth = LegacyTextFormatter.depthKilometers(event.getDepth());
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
        String depth = LegacyTextFormatter.depthKilometers(event.getDepth());
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
        String depth = LegacyTextFormatter.depthKilometers(event.getDepth());
        String originTime = getDate("yyyy-MM-dd HH:mm:ss", timeFormat, "Asia/Shanghai", event.getOriginTime());
        if (isFresh(reportTime, "yyyy-MM-dd HH:mm:ss", ZoneId.of("Asia/Shanghai"))) {
            cqEewAction(reportTime, originTime, num, lat, lon, region, mag, depth, getIntensityColor(intensity));
        }
    }

    private void jmaEewAction(String flag, String reportTime, String originTime, String num, String lat, String lon, String region, String mag, String depth, String shindo, String type) {
        notificationDispatcher.deliverJma(() -> NotificationIntentFactory.jma(
                flag, reportTime, originTime, num, lat, lon, region, mag, depth, shindo, type,
                broadcastBool, titleBool, alertBool,
                notificationProfile(
                        alertBroadcastMessage, alertTitleMessage, alertSubtitleMessage,
                        alertAlertSoundType, alertAlertSoundVolume, alertAlertSoundPitch),
                notificationProfile(
                        forecastBroadcastMessage, forecastTitleMessage, forecastSubtitleMessage,
                        forecastAlertSoundType, forecastAlertSoundVolume, forecastAlertSoundPitch)));
    }

    private void scEewAction(String reportTime, String originTime, String num, String lat, String lon, String region, String mag, String depth, String intensity) {
        notificationDispatcher.deliverRegional(NotificationSource.SICHUAN_EEW, () -> NotificationIntentFactory.regional(
                NotificationSource.SICHUAN_EEW,
                reportTime, originTime, num, lat, lon, region, mag, depth, intensity,
                broadcastBool, titleBool, alertBool,
                notificationProfile(
                        sichuanBroadcastMessage, sichuanTitleMessage, sichuanSubtitleMessage,
                        scAlertSoundType, scAlertSoundVolume, scAlertSoundPitch)));
    }

    private void fjEewAction(String reportTime, String originTime, String num, String lat, String lon, String region, String mag, String type) {
        notificationDispatcher.deliverRegional(NotificationSource.FUJIAN_EEW, () -> NotificationIntentFactory.fujian(
                reportTime, originTime, num, lat, lon, region, mag, type,
                broadcastBool, titleBool, alertBool,
                notificationProfile(
                        fjBroadcastMessage, fjTitleMessage, fjSubtitleMessage,
                        fjAlertSoundType, fjAlertSoundVolume, fjAlertSoundPitch)));
    }

    private void cwaEewAction(String reportTime, String originTime, String num, String lat, String lon, String region, String mag, String depth, String shindo) {
        notificationDispatcher.deliverRegional(NotificationSource.CWA_EEW, () -> NotificationIntentFactory.regional(
                NotificationSource.CWA_EEW,
                reportTime, originTime, num, lat, lon, region, mag, depth, shindo,
                broadcastBool, titleBool, alertBool,
                notificationProfile(
                        cwaBroadcastMessage, cwaTitleMessage, cwaSubtitleMessage,
                        cwaAlertSoundType, cwaAlertSoundVolume, cwaAlertSoundPitch)));
    }

    private void cencEewAction(String reportTime, String originTime, String num, String lat, String lon, String region, String mag, String depth, String intensity) {
        notificationDispatcher.deliverRegional(NotificationSource.CENC_EEW, () -> NotificationIntentFactory.regional(
                NotificationSource.CENC_EEW,
                reportTime, originTime, num, lat, lon, region, mag, depth, intensity,
                broadcastBool, titleBool, alertBool,
                notificationProfile(
                        cencBroadcastMessage, cencTitleMessage, cencSubtitleMessage,
                        cencAlertSoundType, cencAlertSoundVolume, cencAlertSoundPitch)));
    }

    private void cqEewAction(String reportTime, String originTime, String num, String lat, String lon, String region, String mag, String depth, String intensity) {
        notificationDispatcher.deliverRegional(NotificationSource.CHONGQING_EEW, () -> NotificationIntentFactory.regional(
                NotificationSource.CHONGQING_EEW,
                reportTime, originTime, num, lat, lon, region, mag, depth, intensity,
                broadcastBool, titleBool, alertBool,
                notificationProfile(
                        cqBroadcastMessage, cqTitleMessage, cqSubtitleMessage,
                        cqAlertSoundType, cqAlertSoundVolume, cqAlertSoundPitch)));
    }

    private NotificationProfile notificationProfile(
            String broadcast,
            String title,
            String subtitle,
            String soundKey,
            double soundVolume,
            double soundPitch
    ) {
        return new NotificationProfile(
                broadcast, title, subtitle, soundKey, soundVolume, soundPitch);
    }
}
