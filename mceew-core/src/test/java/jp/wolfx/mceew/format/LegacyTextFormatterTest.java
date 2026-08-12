package jp.wolfx.mceew.format;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LegacyTextFormatterTest {
    @Test
    void legacyColorsUnconditionallyReplaceEveryAmpersand() {
        assertEquals("§cTest", LegacyTextFormatter.legacyColors("&cTest"));
        assertEquals("A§B", LegacyTextFormatter.legacyColors("A&B"));
        assertEquals("§§", LegacyTextFormatter.legacyColors("&&"));
    }

    @Test
    void depthSuffixPreservesSimpleStringConcatenation() {
        assertEquals("10km", LegacyTextFormatter.depthKilometers("10"));
        assertEquals("nullkm", LegacyTextFormatter.depthKilometers(null));
    }

    @Test
    void shindoColorsPreserveBoundsAliasesAndFallback() {
        assertEquals("§f0", LegacyTextFormatter.shindo("0"));
        assertEquals("§71", LegacyTextFormatter.shindo("1"));
        assertEquals("§93", LegacyTextFormatter.shindo("3"));
        assertEquals("§e5弱", LegacyTextFormatter.shindo("5弱"));
        assertEquals("§e5-", LegacyTextFormatter.shindo("5-"));
        assertEquals("§65強", LegacyTextFormatter.shindo("5強"));
        assertEquals("§c6弱", LegacyTextFormatter.shindo("6弱"));
        assertEquals("§46+", LegacyTextFormatter.shindo("6+"));
        assertEquals("§d7", LegacyTextFormatter.shindo("7"));
        assertEquals("§funexpected", LegacyTextFormatter.shindo("unexpected"));
        assertEquals("§fnull", LegacyTextFormatter.shindo(null));
    }

    @Test
    void intensityColorsPreserveRoundingClampingAndRawDisplayValue() {
        assertEquals("§f-2", LegacyTextFormatter.intensity("-2"));
        assertEquals("§f0", LegacyTextFormatter.intensity("0"));
        assertEquals("§33", LegacyTextFormatter.intensity("3"));
        assertEquals("§a5", LegacyTextFormatter.intensity("5"));
        assertEquals("§26.1", LegacyTextFormatter.intensity("6.1"));
        assertEquals("§e6.6", LegacyTextFormatter.intensity("6.6"));
        assertEquals("§512", LegacyTextFormatter.intensity("12"));
        assertEquals("§599", LegacyTextFormatter.intensity("99"));
        assertThrows(NumberFormatException.class,
                () -> LegacyTextFormatter.intensity("unexpected"));
    }

    @Test
    void jmaReportTypePreservesTrainingAssumptionFinalAndCancelPrecedence() {
        assertEquals("", LegacyTextFormatter.jmaReportType(false, false, false, false));
        assertEquals("訓練", LegacyTextFormatter.jmaReportType(true, false, false, false));
        assertEquals("訓練", LegacyTextFormatter.jmaReportType(true, true, false, false));
        assertEquals("仮定震源", LegacyTextFormatter.jmaReportType(false, true, false, false));
        assertEquals("最終報", LegacyTextFormatter.jmaReportType(false, false, true, false));
        assertEquals("訓練 (最終報)",
                LegacyTextFormatter.jmaReportType(true, false, true, false));
        assertEquals("仮定震源 (最終報)",
                LegacyTextFormatter.jmaReportType(false, true, true, false));
        assertEquals("取消", LegacyTextFormatter.jmaReportType(true, true, true, true));
    }

    @Test
    void finalReportTypePreservesTheFujianDisplayValue() {
        assertEquals("", LegacyTextFormatter.finalReportType(false));
        assertEquals("最終報", LegacyTextFormatter.finalReportType(true));
    }
}
