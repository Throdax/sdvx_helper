package com.sdvxhelper.model;

import java.util.HashMap;
import java.util.Map;

/**
 * Aggregated statistics for a single chart level (1–20).
 *
 * <p>Tracks rank and lamp distribution counts plus scores for average
 * computation.  Maps to the Python {@code OneLevelStat} class in
 * {@code sdvxh_classes.py}.</p>
 *
 * @author Throdax
 * @since 2.0.0
 */
public class OneLevelStat {

    /** The chart level this instance tracks (1–20). */
    private final int lv;

    /**
     * Count of plays per score rank.
     * Keys: {@code "s"}, {@code "aaa_plus"}, {@code "aaa"}, {@code "aa_plus"},
     * {@code "aa"}, {@code "a_plus"}, {@code "a"}, {@code "b"}, {@code "c"}, {@code "d"}.
     */
    private Map<String, Integer> rank;

    /**
     * Count of plays per clear lamp.
     * Keys: {@code "puc"}, {@code "uc"}, {@code "exh"}, {@code "hard"},
     * {@code "clear"}, {@code "failed"}, {@code "noplay"}.
     */
    private Map<String, Integer> lamp;

    /**
     * Best scores indexed by {@code "title___difficulty"}.
     * Used to compute the level average score.
     */
    private Map<String, Integer> scores;

    /** Cached average score; recomputed by {@link #getAverageScore()}. */
    private double averageScore;

    /**
     * Constructs a new instance for the given level and resets all counters.
     *
     * @param lv chart level (1–20)
     */
    public OneLevelStat(int lv) {
        this.lv = lv;
        reset();
    }

    /**
     * Resets all rank/lamp counts, clears the score map and sets the average to zero.
     * Called before each full statistics recomputation.
     */
    public void reset() {
        rank = new HashMap<>();
        lamp = new HashMap<>();
        scores = new HashMap<>();
        averageScore = 0.0;

        for (String r : new String[]{"s", "aaa_plus", "aaa", "aa_plus", "aa", "a_plus", "a", "b", "c", "d"}) {
            rank.put(r, 0);
        }
        for (String l : new String[]{"puc", "uc", "exh", "hard", "clear", "failed", "noplay"}) {
            lamp.put(l, 0);
        }
    }

    /**
     * Reads one {@link MusicInfo} entry and increments the relevant rank/lamp counters.
     * Also records the best score for average computation.
     *
     * @param minfo chart data to incorporate
     */
    public void read(MusicInfo minfo) {
        String rankKey = minfo.getRank().name().toLowerCase();
        rank.merge(rankKey, 1, Integer::sum);
        lamp.merge(minfo.getBestLamp(), 1, Integer::sum);
        scores.put(minfo.getTitle() + "___" + minfo.getDifficulty(), minfo.getBestScore());
    }

    /**
     * Computes and returns the average score across all charts at this level.
     *
     * @return average score, or {@code 0.0} if no charts have been read
     */
    public double getAverageScore() {
        if (scores.isEmpty()) {
            averageScore = 0.0;
            return 0.0;
        }
        long sum = 0;
        for (int s : scores.values()) {
            sum += s;
        }
        averageScore = (double) sum / scores.size();
        return averageScore;
    }

    /**
     * Returns the chart level this instance tracks.
     *
     * @return level (1–20)
     */
    public int getLv() { return lv; }

    /**
     * Returns the rank count map.
     *
     * @return mutable rank count map
     */
    public Map<String, Integer> getRank() { return rank; }

    /**
     * Returns the lamp count map.
     *
     * @return mutable lamp count map
     */
    public Map<String, Integer> getLamp() { return lamp; }

    /**
     * Returns the scores map ({@code "title___difficulty"} → best score).
     *
     * @return mutable scores map
     */
    public Map<String, Integer> getScores() { return scores; }

    /**
     * Returns the last-computed average score (may be stale until
     * {@link #getAverageScore()} is called).
     *
     * @return cached average score
     */
    public double getCachedAverageScore() { return averageScore; }
}
