package com.sdvxhelper.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link MusicInfo}.
 */
class MusicInfoTest {

    @Test
    void getLvAsIntParsesInteger() {
        MusicInfo m = new MusicInfoBuilder("X").artist("A").bpm("180").difficulty("exh").lv("18").bestScore(9_000_000)
                .bestLamp("clear").build();
        Assertions.assertEquals(18, m.getLvAsInt());
    }

    @Test
    void getLvAsIntReturnsMinusOneForUnknown() {
        MusicInfo m = new MusicInfoBuilder("X").artist("A").bpm("180").difficulty("exh").lv("??").bestScore(0)
                .bestLamp("noplay").build();
        Assertions.assertEquals(-1, m.getLvAsInt());
    }

    @Test
    void sortingByVfDescending() {
        MusicInfo low = new MusicInfoBuilder("Low").artist("A").bpm("100").difficulty("exh").lv("16")
                .bestScore(8_000_000).bestLamp("clear").build();
        low.setVf(200);
        MusicInfo high = new MusicInfoBuilder("High").artist("A").bpm("100").difficulty("exh").lv("20")
                .bestScore(9_900_000).bestLamp("puc").build();
        high.setVf(500);
        MusicInfo mid = new MusicInfoBuilder("Mid").artist("A").bpm("100").difficulty("exh").lv("18")
                .bestScore(9_500_000).bestLamp("uc").build();
        mid.setVf(350);

        List<MusicInfo> list = new ArrayList<>(List.of(low, high, mid));
        Collections.sort(list);

        Assertions.assertEquals("High", list.get(0).getTitle());
        Assertions.assertEquals("Mid", list.get(1).getTitle());
        Assertions.assertEquals("Low", list.get(2).getTitle());
    }

    @Test
    void toStringContainsTitle() {
        MusicInfo m = new MusicInfoBuilder("冥").artist("A").bpm("180").difficulty("exh").lv("20").bestScore(9_000_000)
                .bestLamp("clear").build();
        Assertions.assertTrue(m.toString().contains("冥"));
    }
}
