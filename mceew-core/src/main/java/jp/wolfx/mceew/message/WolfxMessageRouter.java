package jp.wolfx.mceew.message;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.Objects;

/**
 * Identifies Wolfx wire messages and parses real-time EEW payloads on demand.
 */
public final class WolfxMessageRouter {
    public enum MessageType {
        JMA_EEW,
        JMA_EARTHQUAKE_LIST,
        SICHUAN_EEW,
        FUJIAN_EEW,
        CWA_EEW,
        CENC_EEW,
        CHONGQING_EEW,
        CENC_EARTHQUAKE_LIST,
        HEARTBEAT,
        UNKNOWN
    }

    public static final class RoutedMessage {
        private final MessageType type;
        private final String wireType;
        private final JsonObject payload;

        private RoutedMessage(MessageType type, String wireType, JsonObject payload) {
            this.type = type;
            this.wireType = wireType;
            this.payload = payload;
        }

        public MessageType getType() {
            return type;
        }

        String getWireType() {
            return wireType;
        }

        public JsonObject getPayload() {
            return payload;
        }
    }

    public RoutedMessage route(String message) {
        JsonObject payload = JsonParser.parseString(message).getAsJsonObject();
        String wireType = payload.get("type").getAsString();
        return new RoutedMessage(messageType(wireType), wireType, payload);
    }

    public RealtimeEewEvent parseRealtime(RoutedMessage message) {
        Objects.requireNonNull(message, "message");
        switch (message.getType()) {
            case JMA_EEW:
                return parseJma(message.getPayload());
            case SICHUAN_EEW:
                return parseSichuan(message.getPayload());
            case FUJIAN_EEW:
                return parseFujian(message.getPayload());
            case CWA_EEW:
                return parseCwa(message.getPayload());
            case CENC_EEW:
                return parseCenc(message.getPayload());
            case CHONGQING_EEW:
                return parseChongqing(message.getPayload());
            default:
                throw new IllegalArgumentException(
                        "Not a real-time EEW message: " + message.getWireType());
        }
    }

    private MessageType messageType(String wireType) {
        switch (wireType) {
            case "jma_eew":
                return MessageType.JMA_EEW;
            case "jma_eqlist":
                return MessageType.JMA_EARTHQUAKE_LIST;
            case "sc_eew":
                return MessageType.SICHUAN_EEW;
            case "fj_eew":
                return MessageType.FUJIAN_EEW;
            case "cwa_eew":
                return MessageType.CWA_EEW;
            case "cenc_eew":
                return MessageType.CENC_EEW;
            case "cq_eew":
                return MessageType.CHONGQING_EEW;
            case "cenc_eqlist":
                return MessageType.CENC_EARTHQUAKE_LIST;
            case "heartbeat":
                return MessageType.HEARTBEAT;
            default:
                return MessageType.UNKNOWN;
        }
    }

    private JmaEewEvent parseJma(JsonObject data) {
        String flag = data.get("Title").getAsString().substring(7, 9);
        String reportTime = data.get("AnnouncedTime").getAsString();
        String reportNumber = data.get("Serial").getAsString();
        String latitude = data.get("Latitude").getAsString();
        String longitude = data.get("Longitude").getAsString();
        String region = data.get("Hypocenter").getAsString();
        String magnitude = data.get("Magunitude").getAsString();
        String depth = data.get("Depth").getAsString();
        String maximumIntensity = data.get("MaxIntensity").getAsString();
        String originTime = data.get("OriginTime").getAsString();
        boolean training = data.get("isTraining").getAsBoolean();
        boolean assumption = data.get("isAssumption").getAsBoolean();
        boolean finalReport = data.get("isFinal").getAsBoolean();
        boolean cancelled = data.get("isCancel").getAsBoolean();
        return new JmaEewEvent(
                flag, reportTime, originTime, reportNumber, latitude, longitude,
                region, magnitude, depth, maximumIntensity,
                training, assumption, finalReport, cancelled);
    }

    private RegionalEewEvent parseSichuan(JsonObject data) {
        String reportTime = data.get("ReportTime").getAsString();
        String reportNumber = data.get("ReportNum").getAsString();
        String latitude = data.get("Latitude").getAsString();
        String longitude = data.get("Longitude").getAsString();
        String region = data.get("HypoCenter").getAsString();
        String magnitude = data.get("Magunitude").getAsString();
        String maximumIntensity = String.valueOf(Math.round(
                Float.parseFloat(data.get("MaxIntensity").getAsString())));
        String depth = data.get("Depth").isJsonNull()
                ? "10"
                : data.get("Depth").getAsString();
        String originTime = data.get("OriginTime").getAsString();
        return new RegionalEewEvent(
                RegionalEewEvent.Source.SICHUAN,
                reportTime, originTime, reportNumber, latitude, longitude,
                region, magnitude, depth, maximumIntensity);
    }

    private FujianEewEvent parseFujian(JsonObject data) {
        String reportTime = data.get("ReportTime").getAsString();
        String reportNumber = data.get("ReportNum").getAsString();
        String latitude = data.get("Latitude").getAsString();
        String longitude = data.get("Longitude").getAsString();
        String region = data.get("HypoCenter").getAsString();
        String magnitude = data.get("Magunitude").getAsString();
        String originTime = data.get("OriginTime").getAsString();
        boolean finalReport = data.get("isFinal").getAsBoolean();
        return new FujianEewEvent(
                reportTime, originTime, reportNumber, latitude, longitude,
                region, magnitude, finalReport);
    }

    private RegionalEewEvent parseCwa(JsonObject data) {
        String reportTime = data.get("ReportTime").getAsString();
        String reportNumber = data.get("ReportNum").getAsString();
        String latitude = data.get("Latitude").getAsString();
        String longitude = data.get("Longitude").getAsString();
        String region = data.get("HypoCenter").getAsString();
        String magnitude = data.get("Magunitude").getAsString();
        String depth = data.get("Depth").getAsString();
        String maximumIntensity = data.get("MaxIntensity").getAsString();
        String originTime = data.get("OriginTime").getAsString();
        return new RegionalEewEvent(
                RegionalEewEvent.Source.CWA,
                reportTime, originTime, reportNumber, latitude, longitude,
                region, magnitude, depth, maximumIntensity);
    }

    private RegionalEewEvent parseCenc(JsonObject data) {
        String reportTime = data.get("ReportTime").getAsString();
        String reportNumber = data.get("ReportNum").getAsString();
        String latitude = data.get("Latitude").getAsString();
        String longitude = data.get("Longitude").getAsString();
        String region = data.get("HypoCenter").getAsString();
        String magnitude = data.get("Magnitude").getAsString();
        String maximumIntensity = String.valueOf(Math.round(
                Float.parseFloat(data.get("MaxIntensity").getAsString())));
        String depth = data.get("Depth").isJsonNull()
                ? "10"
                : data.get("Depth").getAsString();
        String originTime = data.get("OriginTime").getAsString();
        return new RegionalEewEvent(
                RegionalEewEvent.Source.CENC,
                reportTime, originTime, reportNumber, latitude, longitude,
                region, magnitude, depth, maximumIntensity);
    }

    private RegionalEewEvent parseChongqing(JsonObject data) {
        String reportTime = data.get("ReportTime").getAsString();
        String reportNumber = data.get("ReportNum").getAsString();
        String latitude = data.get("Latitude").getAsString();
        String longitude = data.get("Longitude").getAsString();
        String region = data.get("HypoCenter").getAsString();
        String magnitude = data.get("Magnitude").getAsString();
        String maximumIntensity = String.valueOf(Math.round(
                Float.parseFloat(data.get("MaxIntensity").getAsString())));
        String depth = data.get("Depth").isJsonNull()
                ? "10"
                : data.get("Depth").getAsString();
        String originTime = data.get("OriginTime").getAsString();
        return new RegionalEewEvent(
                RegionalEewEvent.Source.CHONGQING,
                reportTime, originTime, reportNumber, latitude, longitude,
                region, magnitude, depth, maximumIntensity);
    }
}
