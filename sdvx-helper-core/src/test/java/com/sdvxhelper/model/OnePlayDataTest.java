package com.sdvxhelper.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link OnePlayData}.
 */
class OnePlayDataTest {

    @Test
    void diffIsComputedOnConstruction() {
        OnePlayData play = new OnePlayData("Song A", 9_500_000, 9_200_000, "clear", "exh", "2024-01-01 12:00:00");
        Assertions.assertEquals(300_000, play.getDiff());
    }

    @Test
    void negativeDiffWhenScoreDrops() {
        OnePlayData play = new OnePlayData("Song A", 9_000_000, 9_200_000, "failed", "exh", "2024-01-02 18:30:00");
        Assertions.assertEquals(-200_000, play.getDiff());
    }

    @Test
    void equalityBasedOnAllFields() {
        OnePlayData a = new OnePlayData("X", 1000, 900, "clear", "exh", "2024-01-01 10:00:00");
        OnePlayData b = new OnePlayData("X", 1000, 900, "clear", "exh", "2024-01-01 10:00:00");
        Assertions.assertEquals(a, b);
        Assertions.assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void inequalityWhenFieldDiffers() {
        OnePlayData a = new OnePlayData("X", 1000, 900, "clear", "exh", "2024-01-01 10:00:00");
        OnePlayData b = new OnePlayData("X", 1001, 900, "clear", "exh", "2024-01-01 10:00:00");
        Assertions.assertNotEquals(a, b);
    }

    @Test
    void sortingByDateAscending() {
        OnePlayData p1 = new OnePlayData("X", 100, 0, "clear", "exh", "2024-01-03 10:00:00");
        OnePlayData p2 = new OnePlayData("X", 100, 0, "clear", "exh", "2024-01-01 08:00:00");
        OnePlayData p3 = new OnePlayData("X", 100, 0, "clear", "exh", "2024-01-02 09:00:00");

        List<OnePlayData> list = new ArrayList<>(List.of(p1, p2, p3));
        Collections.sort(list);

        Assertions.assertEquals(LocalDateTime.of(2024, 1, 1,  8, 0, 0), list.get(0).getDate());
        Assertions.assertEquals(LocalDateTime.of(2024, 1, 2,  9, 0, 0), list.get(1).getDate());
        Assertions.assertEquals(LocalDateTime.of(2024, 1, 3, 10, 0, 0), list.get(2).getDate());
    }

    @Test
    void settersRecomputeDiff() {
        OnePlayData play = new OnePlayData("X", 5_000_000, 4_000_000, "clear", "exh", "2024-01-01 12:00:00");
        play.setCurScore(6_000_000);
        Assertions.assertEquals(2_000_000, play.getDiff());

        play.setPreScore(5_500_000);
        Assertions.assertEquals(500_000, play.getDiff());
    }

    @Test
    void toStringContainsTitle() {
        OnePlayData play = new OnePlayData("冥", 9_000_000, 8_800_000, "exh", "exh", "2024-01-01 21:05:00");
        Assertions.assertTrue(play.toString().contains("冥"));
    }
}
