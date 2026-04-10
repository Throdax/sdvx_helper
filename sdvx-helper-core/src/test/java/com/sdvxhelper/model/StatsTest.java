package com.sdvxhelper.model;

import com.sdvxhelper.model.enums.ScoreRank;
import org.junit.jupiter.api.Test;

import org.junit.jupiter.api.Assertions;

/**
 * Unit tests for {@link Stats} and {@link OneLevelStat}.
 */
class StatsTest {

    private MusicInfo makeInfo(String lv, int score, String lamp) {
        MusicInfo m = new MusicInfo("T", "A", "180", "exh", lv, score, lamp, "", "", "");
        m.setRank(ScoreRank.fromScore(score));
        return m;
    }

    @Test
    void nonIntegerLevelIsSkipped() {
        Stats stats = new Stats();
        MusicInfo m = makeInfo("??", 9_000_000, "clear");
        stats.readAll(m);
        // All level buckets should remain empty
        for (int i = 1; i <= 20; i++) {
            Assertions.assertEquals(0, stats.getStatForLevel(i).getScores().size());
        }
    }

    @Test
    void readAllIncrementsCorrectLevelBucket() {
        Stats stats = new Stats();
        stats.readAll(makeInfo("18", 9_900_000, "puc"));
        OneLevelStat lv18 = stats.getStatForLevel(18);
        Assertions.assertEquals(1, lv18.getScores().size());
        Assertions.assertEquals(1, lv18.getRank().get("s"));
        Assertions.assertEquals(1, lv18.getLamp().get("puc"));
    }

    @Test
    void resetAllClearsAllBuckets() {
        Stats stats = new Stats();
        stats.readAll(makeInfo("15", 9_000_000, "clear"));
        stats.resetAll();
        Assertions.assertEquals(0, stats.getStatForLevel(15).getScores().size());
    }

    @Test
    void averageScoreComputedCorrectly() {
        OneLevelStat lvStat = new OneLevelStat(20);
        // Use distinct titles so each read() produces a different scores-map key.
        MusicInfo m1 = new MusicInfo("SongA", "A", "180", "exh", "20", 9_000_000, "clear", "", "", "");
        m1.setRank(ScoreRank.fromScore(9_000_000));
        MusicInfo m2 = new MusicInfo("SongB", "A", "180", "exh", "20", 8_000_000, "clear", "", "", "");
        m2.setRank(ScoreRank.fromScore(8_000_000));
        lvStat.read(m1);
        lvStat.read(m2);
        Assertions.assertEquals(8_500_000.0, lvStat.getAverageScore(), 1.0);
    }

    @Test
    void getStatForLevelThrowsForOutOfRange() {
        Stats stats = new Stats();
        Assertions.assertThrows(IndexOutOfBoundsException.class, () -> stats.getStatForLevel(0));
        Assertions.assertThrows(IndexOutOfBoundsException.class, () -> stats.getStatForLevel(21));
    }
}
