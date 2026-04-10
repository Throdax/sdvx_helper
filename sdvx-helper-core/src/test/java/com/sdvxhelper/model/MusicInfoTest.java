package com.sdvxhelper.model;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Assertions;

/**
 * Unit tests for {@link MusicInfo}.
 */
class MusicInfoTest {

    @Test
    void getLvAsIntParsesInteger() {
        MusicInfo m = new MusicInfo("X", "A", "180", "exh", "18", 9_000_000, "clear", "", "", "");
        Assertions.assertEquals(18, m.getLvAsInt());
    }

    @Test
    void getLvAsIntReturnsMinusOneForUnknown() {
        MusicInfo m = new MusicInfo("X", "A", "180", "exh", "??", 0, "noplay", "", "", "");
        Assertions.assertEquals(-1, m.getLvAsInt());
    }

    @Test
    void sortingByVfDescending() {
        MusicInfo low = new MusicInfo("Low", "A", "100", "exh", "16", 8_000_000, "clear", "", "", "");
        low.setVf(200);
        MusicInfo high = new MusicInfo("High", "A", "100", "exh", "20", 9_900_000, "puc", "", "", "");
        high.setVf(500);
        MusicInfo mid = new MusicInfo("Mid", "A", "100", "exh", "18", 9_500_000, "uc", "", "", "");
        mid.setVf(350);

        List<MusicInfo> list = new ArrayList<>(List.of(low, high, mid));
        Collections.sort(list);

        Assertions.assertEquals("High", list.get(0).getTitle());
        Assertions.assertEquals("Mid",  list.get(1).getTitle());
        Assertions.assertEquals("Low",  list.get(2).getTitle());
    }

    @Test
    void toStringContainsTitle() {
        MusicInfo m = new MusicInfo("冥", "A", "180", "exh", "20", 9_000_000, "clear", "", "", "");
        Assertions.assertTrue(m.toString().contains("冥"));
    }
}
