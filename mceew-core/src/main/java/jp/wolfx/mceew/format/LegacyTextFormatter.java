package jp.wolfx.mceew.format;

import java.util.Objects;

/**
 * Preserves MCEEW's existing legacy Minecraft display transformations.
 */
public final class LegacyTextFormatter {
    private static final String[] SHINDO_COLORS = {
            "§f", "§7", "§b", "§9", "§a", "§e", "§6", "§c", "§4", "§d"
    };
    private static final String[] INTENSITY_COLORS = {
            "§f", "§7", "§b", "§3", "§9", "§a", "§2",
            "§e", "§6", "§c", "§4", "§d", "§5"
    };

    private LegacyTextFormatter() {
    }

    public static String legacyColors(String value) {
        return value.replace("&", "§");
    }

    public static String depthKilometers(String depth) {
        return depth + "km";
    }

    public static String shindo(String shindo) {
        if (Objects.equals(shindo, "1")) {
            return SHINDO_COLORS[1] + shindo;
        } else if (Objects.equals(shindo, "2")) {
            return SHINDO_COLORS[2] + shindo;
        } else if (Objects.equals(shindo, "3")) {
            return SHINDO_COLORS[3] + shindo;
        } else if (Objects.equals(shindo, "4")) {
            return SHINDO_COLORS[4] + shindo;
        } else if (Objects.equals(shindo, "5弱") || Objects.equals(shindo, "5-")) {
            return SHINDO_COLORS[5] + shindo;
        } else if (Objects.equals(shindo, "5強") || Objects.equals(shindo, "5+")) {
            return SHINDO_COLORS[6] + shindo;
        } else if (Objects.equals(shindo, "6弱") || Objects.equals(shindo, "6-")) {
            return SHINDO_COLORS[7] + shindo;
        } else if (Objects.equals(shindo, "6強") || Objects.equals(shindo, "6+")) {
            return SHINDO_COLORS[8] + shindo;
        } else if (Objects.equals(shindo, "7")) {
            return SHINDO_COLORS[9] + shindo;
        } else {
            return SHINDO_COLORS[0] + shindo;
        }
    }

    public static String intensity(String intensity) {
        float value = Float.parseFloat(intensity);
        int index = Math.round(value);
        if (index < 0) {
            index = 0;
        }
        if (index >= INTENSITY_COLORS.length) {
            index = INTENSITY_COLORS.length - 1;
        }
        return INTENSITY_COLORS[index] + intensity;
    }

    public static String jmaReportType(
            boolean training,
            boolean assumption,
            boolean finalReport,
            boolean cancelled
    ) {
        String type = "";
        if (training) {
            type = "訓練";
        } else if (assumption) {
            type = "仮定震源";
        }
        if (finalReport) {
            if (!type.isEmpty()) {
                type = type + " (最終報)";
            } else {
                type = "最終報";
            }
        }
        if (cancelled) {
            type = "取消";
        }
        return type;
    }

    public static String finalReportType(boolean finalReport) {
        return finalReport ? "最終報" : "";
    }
}
