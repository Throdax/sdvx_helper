package com.sdvxhelper.model;

import com.sdvxhelper.model.enums.ScoreRank;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlTransient;

/**
 * Represents the personal-best information for one specific chart (song + difficulty).
 *
 * <p>Each instance corresponds to a single chart entry, so the same song will have
 * separate {@code MusicInfo} objects for its EXH and MXM charts.  The natural ordering
 * sorts by Volforce descending (highest VF first), consistent with the Python implementation.
 *
 * <p>Maps to the Python {@code MusicInfo} class in {@code sdvxh_classes.py}.</p>
 *
 * @author Throdax
 * @since 2.0.0
 */
@XmlAccessorType(XmlAccessType.FIELD)
public class MusicInfo implements Comparable<MusicInfo> {

    /** Song title. */
    @XmlAttribute
    private String title;

    /** Artist / composer name. */
    @XmlAttribute
    private String artist;

    /** BPM displayed on the song select screen (may be a range, e.g. {@code "120-200"}). */
    @XmlAttribute
    private String bpm;

    /** Chart difficulty string (e.g. {@code "exh"}, {@code "mxm"}). */
    @XmlAttribute
    private String difficulty;

    /**
     * Chart level.  Stored as {@code Object} because non-integer levels
     * (e.g. {@code "??"}) exist for unknown entries.  In practice, the value is
     * either an {@code Integer} or a {@code String}.
     */
    @XmlAttribute
    private String lv;

    /** Personal-best score for this chart. */
    @XmlAttribute
    private int bestScore;

    /** Clear lamp for the personal-best play. */
    @XmlAttribute
    private String bestLamp;

    /** Date of the personal-best play (ISO-8601). */
    @XmlAttribute
    private String date;

    /** Grade-S tier classification (e.g. {@code "SSS"}, {@code "SS"}, {@code "S"}). */
    @XmlAttribute
    private String sTier;

    /** Perfect-UC tier classification. */
    @XmlAttribute
    private String pTier;

    /** Computed score rank; not persisted. */
    @XmlTransient
    private ScoreRank rank = ScoreRank.NO_VALUE;

    /**
     * Single-chart Volforce (integer × 10, e.g. {@code 369} = 36.9 VF).
     * Not persisted; computed by {@code VolforceCalculator}.
     */
    @XmlTransient
    private int vf;

    /** No-argument constructor required by JAXB. */
    public MusicInfo() {
    }

    /**
     * Full constructor.
     *
     * @param title      song title
     * @param artist     artist name
     * @param bpm        BPM string
     * @param difficulty chart difficulty string
     * @param lv         chart level (integer as string, or {@code "??"})
     * @param bestScore  personal-best score
     * @param bestLamp   personal-best clear lamp
     * @param date       date of personal best (ISO-8601)
     * @param sTier      grade-S tier classification
     * @param pTier      perfect-UC tier classification
     */
    public MusicInfo(String title, String artist, String bpm, String difficulty,
                     String lv, int bestScore, String bestLamp,
                     String date, String sTier, String pTier) {
        this.title = title;
        this.artist = artist;
        this.bpm = bpm;
        this.difficulty = difficulty;
        this.lv = lv;
        this.bestScore = bestScore;
        this.bestLamp = bestLamp;
        this.date = date;
        this.sTier = sTier;
        this.pTier = pTier;
    }

    // -------------------------------------------------------------------------
    // Comparable: sort by VF descending (higher VF is "less than" for descending order)
    // -------------------------------------------------------------------------

    /**
     * Sorts by Volforce descending so that the highest-VF chart sorts first when
     * placed in a sorted collection.
     *
     * @param other the other {@code MusicInfo}
     * @return negative if this VF is higher, positive if lower
     */
    @Override
    public int compareTo(MusicInfo other) {
        return Integer.compare(other.vf, this.vf);
    }

    @Override
    public String toString() {
        return String.format("MusicInfo{title='%s', diff='%s', lv=%s, score=%d, lamp='%s', vf=%d}",
                title, difficulty, lv, bestScore, bestLamp, vf);
    }

    // -------------------------------------------------------------------------
    // Getters and setters
    // -------------------------------------------------------------------------

    /** @return song title */
    public String getTitle() { return title; }

    /** @param title song title */
    public void setTitle(String title) { this.title = title; }

    /** @return artist name */
    public String getArtist() { return artist; }

    /** @param artist artist name */
    public void setArtist(String artist) { this.artist = artist; }

    /** @return BPM string */
    public String getBpm() { return bpm; }

    /** @param bpm BPM string */
    public void setBpm(String bpm) { this.bpm = bpm; }

    /** @return chart difficulty string */
    public String getDifficulty() { return difficulty; }

    /** @param difficulty chart difficulty string */
    public void setDifficulty(String difficulty) { this.difficulty = difficulty; }

    /**
     * Returns the chart level as a string.
     * Parse with {@link Integer#parseInt(String)} only after confirming it is numeric.
     *
     * @return chart level string
     */
    public String getLv() { return lv; }

    /** @param lv chart level string */
    public void setLv(String lv) { this.lv = lv; }

    /**
     * Convenience method: returns the chart level as an {@code int}, or {@code -1}
     * if the level string is non-numeric (e.g. {@code "??"}).
     *
     * @return integer level, or -1 for unknown levels
     */
    public int getLvAsInt() {
        try {
            return Integer.parseInt(lv);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** @return personal-best score */
    public int getBestScore() { return bestScore; }

    /** @param bestScore personal-best score */
    public void setBestScore(int bestScore) { this.bestScore = bestScore; }

    /** @return personal-best clear lamp */
    public String getBestLamp() { return bestLamp; }

    /** @param bestLamp personal-best clear lamp */
    public void setBestLamp(String bestLamp) { this.bestLamp = bestLamp; }

    /** @return personal-best date */
    public String getDate() { return date; }

    /** @param date personal-best date */
    public void setDate(String date) { this.date = date; }

    /** @return grade-S tier */
    public String getSTier() { return sTier; }

    /** @param sTier grade-S tier */
    public void setSTier(String sTier) { this.sTier = sTier; }

    /** @return perfect-UC tier */
    public String getPTier() { return pTier; }

    /** @param pTier perfect-UC tier */
    public void setPTier(String pTier) { this.pTier = pTier; }

    /** @return computed score rank */
    public ScoreRank getRank() { return rank; }

    /** @param rank computed score rank */
    public void setRank(ScoreRank rank) { this.rank = rank; }

    /**
     * Returns the single-chart Volforce (integer representation).
     *
     * @return VF integer (e.g. {@code 369} represents 36.9 VF)
     */
    public int getVf() { return vf; }

    /** @param vf Volforce integer */
    public void setVf(int vf) { this.vf = vf; }
}
