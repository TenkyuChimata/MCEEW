package jp.wolfx.mceew.format;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PlaceholderRendererTest {
    @Test
    void rendersEveryCurrentPlaceholderInCallerOrder() {
        assertEquals(
                "警報|report|origin|46|37.6|137.2|region|7.4|10km|§d7|最終報|info",
                PlaceholderRenderer.render(
                        "%flag%|%report_time%|%origin_time%|%num%|%lat%|%lon%|"
                                + "%region%|%mag%|%depth%|%shindo%|%type%|%info%",
                        "%flag%", "警報",
                        "%report_time%", "report",
                        "%origin_time%", "origin",
                        "%num%", "46",
                        "%lat%", "37.6",
                        "%lon%", "137.2",
                        "%region%", "region",
                        "%mag%", "7.4",
                        "%depth%", "10km",
                        "%shindo%", "§d7",
                        "%type%", "最終報",
                        "%info%", "info"));
    }

    @Test
    void repeatedPlaceholdersAndReplacementOrderRemainObservable() {
        assertEquals("5.0|5.0", PlaceholderRenderer.render(
                "%region%|%region%",
                "%region%", "%mag%",
                "%mag%", "5.0"));
    }

    @Test
    void dollarReplacementPreservesReplaceAllFailure() {
        assertThrows(IndexOutOfBoundsException.class, () -> PlaceholderRenderer.render(
                "%region%", "%region%", "$1"));
    }

    @Test
    void backslashReplacementIsStillConsumedByReplaceAll() {
        assertEquals("AB", PlaceholderRenderer.render(
                "%region%", "%region%", "A\\B"));
    }
}
