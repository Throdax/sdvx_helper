package com.sdvxhelper.model;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Assertions;

/**
 * Unit tests for {@link OnePlayData}.
 */
class OnePlayDataTest {

    @Test
    void diffIsComputedOnConstruction() {
        OnePlayData play = new OnePlayData("Song A", 9_500_000, 9_200_000, "clear", "exh", "2024-01-01");
        Assertions.assertEquals(300_000, play.getDiff());
    }

    @Test
    void negativeDiffWhenScoreDrops() {
        OnePlayData play = new OnePlayData("Song A", 9_000_000, 9_200_000, "failed", "exh", "2024-01-02");
        Assertions.assertEquals(-200_000, play.getDiff());
    }

    @Test
    void equalityBasedOnAllFields() {
        OnePlayData a = new OnePlayData("X", 1000, 900, "clear", "exh", "2024-01-01");
        OnePlayData b = new OnePlayData("X", 1000, 900, "clear", "exh", "2024-01-01");
        Assertions.assertEquals(a, b);
        Assertions.assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void inequalityWhenFieldDiffers() {
        OnePlayData a = new OnePlayData("X", 1000, 900, "clear", "exh", "2024-01-01");
        OnePlayData b = new OnePlayData("X", 1001, 900, "clear", "exh", "2024-01-01");
        Assertions.assertNotEquals(a, b);
    }

    @Test
    void sortingByDateAscending() {
        OnePlayData p1 = new OnePlayData("X", 100, 0, "clear", "exh", "2024-01-03");
        OnePlayData p2 = new OnePlayData("X", 100, 0, "clear", "exh", "2024-01-01");
        OnePlayData p3 = new OnePlayData("X", 100, 0, "clear", "exh", "2024-01-02");

        List<OnePlayData> list = new ArrayList<>(List.of(p1, p2, p3));
        Collections.sort(list);

        Assertions.assertEquals("2024-01-01", list.get(0).getDate());
        Assertions.assertEquals("2024-01-02", list.get(1).getDate());
        Assertions.assertEquals("2024-01-03", list.get(2).getDate());
    }

    @Test
    void settersRecomputeDiff() {
        OnePlayData play = new OnePlayData("X", 5_000_000, 4_000_000, "clear", "exh", "2024-01-01");
        play.setCurScore(6_000_000);
        Assertions.assertEquals(2_000_000, play.getDiff());

        play.setPreScore(5_500_000);
        Assertions.assertEquals(500_000, play.getDiff());
    }

    @Test
    void toStringContainsTitle() {
        OnePlayData play = new OnePlayData("冥", 9_000_000, 8_800_000, "exh", "exh", "2024-01-01");
        Assertions.assertTrue(play.toString().contains("冥"));
    }
}
