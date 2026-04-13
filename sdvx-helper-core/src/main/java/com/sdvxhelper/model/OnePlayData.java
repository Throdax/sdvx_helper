package com.sdvxhelper.model;

import java.time.LocalDateTime;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlTransient;
import jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import com.sdvxhelper.model.enums.ScoreRank;

/**
 * Immutable record of a single play (one attempt at one chart).
 *
 * <p>
 * Instances are collected inside a {@link PlayLog} and persisted as XML via
 * JAXB. The natural ordering sorts plays by date ascending so the most recent
 * play is last.
 * </p>
 *
 * <p>
 * Maps to the Python {@code OnePlayData} class in {@code sdvxh_classes.py}.
 * </p>
 *
 * @author Throdax
 * @since 2.0.0
 */
@XmlAccessorType(XmlAccessType.FIELD)
public class OnePlayData implements Comparable<OnePlayData> {

    /** Song title as it appears in the game. */
    @XmlAttribute
    private String title;

    /** Score achieved in this play (0–10 000 000). */
    @XmlAttribute
    private int curScore;

    /** Best score recorded before this play (used to compute {@link #diff}). */
    @XmlAttribute
    private int preScore;

    /**
     * Clear lamp string: one of {@code "puc"}, {@code "uc"}, {@code "exh"},
     * {@code "hard"}, {@code "clear"}, or {@code "failed"}.
     */
    @XmlAttribute
    private String lamp;

    /**
     * Chart difficulty string: one of {@code "nov"}, {@code "adv"}, {@code "exh"},
     * {@code "mxm"}, {@code "inf"}, {@code "grv"}, {@code "hvn"}, {@code "vvd"}, or
     * {@code "xcd"}.
     */
    @XmlAttribute
    private String difficulty;

    /**
     * Date and time when the play was recorded, serialised via
     * {@link LocalDateTimeXMLAdapter}.
     */
    @XmlAttribute
    @XmlJavaTypeAdapter(LocalDateTimeXMLAdapter.class)
    private LocalDateTime date;

    /**
     * Score improvement over the previous best: {@code curScore - preScore}. Stored
     * for display convenience; always recomputed on deserialisation.
     */
    @XmlTransient
    private int diff;

    /**
     * Score rank computed from {@link #curScore}. Not persisted; recomputed when
     * the Volforce is calculated.
     */
    @XmlTransient
    private ScoreRank rank = ScoreRank.NO_VALUE;

    /**
     * Single-chart Volforce value (integer representation, e.g. {@code 369} meaning
     * 36.9). Not persisted; computed by {@code VolforceCalculator}.
     */
    @XmlTransient
    private int vf;

    /** No-argument constructor required by JAXB. */
    public OnePlayData() {
    }

    /**
     * Constructs a new play record.
     *
     * @param title
     *            the song title
     * @param curScore
     *            the score achieved in this play
     * @param preScore
     *            the best score before this play
     * @param lamp
     *            the clear lamp string
     * @param difficulty
     *            the chart difficulty string
     * @param date
     *            the play date-time string ({@code "yyyy-MM-dd HH:mm:ss"} or
     *            {@code "yyyy-MM-dd HH:mm"})
     */
    public OnePlayData(String title, int curScore, int preScore, String lamp, String difficulty, String date) {
        this.title = title;
        this.curScore = curScore;
        this.preScore = preScore;
        this.lamp = lamp;
        this.difficulty = difficulty;
        this.date = MusicInfo.unmarshalDate(date);
        this.diff = curScore - preScore;
    }

    // -------------------------------------------------------------------------
    // Comparable: sort by date ascending
    // -------------------------------------------------------------------------

    /**
     * Compares plays by date ascending (nulls sort first).
     *
     * @param other
     *            the other play record
     * @return negative, zero, or positive as per {@link Comparable#compareTo}
     */
    @Override
    public int compareTo(OnePlayData other) {
        if (this.date == null && other.date == null) {
            return 0;
        }
        if (this.date == null) {
            return -1;
        }
        if (other.date == null) {
            return 1;
        }
        return this.date.compareTo(other.date);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof OnePlayData other)) {
            return false;
        }
        return curScore == other.curScore && preScore == other.preScore && java.util.Objects.equals(title, other.title)
                && java.util.Objects.equals(difficulty, other.difficulty) && java.util.Objects.equals(lamp, other.lamp)
                && java.util.Objects.equals(date, other.date);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(title, curScore, preScore, lamp, difficulty, date);
    }

    @Override
    public String toString() {
        return String.format("OnePlayData{title='%s', diff='%s', cur=%d, pre=%d(%+d), lamp='%s', date='%s'}", title,
                difficulty, curScore, preScore, diff, lamp, date);
    }

    // -------------------------------------------------------------------------
    // Getters and setters
    // -------------------------------------------------------------------------

    /**
     * Returns the song title.
     *
     * @return song title
     */
    public String getTitle() {
        return title;
    }

    /**
     * Sets the song title.
     *
     * @param title
     *            song title
     */
    public void setTitle(String title) {
        this.title = title;
    }

    /**
     * Returns the score achieved in this play.
     *
     * @return play score
     */
    public int getCurScore() {
        return curScore;
    }

    /**
     * Sets the play score.
     *
     * @param curScore
     *            play score
     */
    public void setCurScore(int curScore) {
        this.curScore = curScore;
        this.diff = curScore - preScore;
    }

    /**
     * Returns the best score recorded before this play.
     *
     * @return previous best score
     */
    public int getPreScore() {
        return preScore;
    }

    /**
     * Sets the previous best score.
     *
     * @param preScore
     *            previous best score
     */
    public void setPreScore(int preScore) {
        this.preScore = preScore;
        this.diff = curScore - preScore;
    }

    /**
     * Returns the clear lamp string.
     *
     * @return lamp string
     */
    public String getLamp() {
        return lamp;
    }

    /**
     * Sets the clear lamp string.
     *
     * @param lamp
     *            lamp string
     */
    public void setLamp(String lamp) {
        this.lamp = lamp;
    }

    /**
     * Returns the chart difficulty string.
     *
     * @return difficulty string
     */
    public String getDifficulty() {
        return difficulty;
    }

    /**
     * Sets the chart difficulty string.
     *
     * @param difficulty
     *            difficulty string
     */
    public void setDifficulty(String difficulty) {
        this.difficulty = difficulty;
    }

    /**
     * Returns the date and time when the play was recorded.
     *
     * @return the play date-time, or {@code null} if not set
     */
    public LocalDateTime getDate() {
        return date;
    }

    /**
     * Sets the date and time when the play was recorded.
     *
     * @param date
     *            the play {@link LocalDateTime} to set
     */
    public void setDate(LocalDateTime date) {
        this.date = date;
    }

    /**
     * Returns the score improvement ({@code curScore - preScore}).
     *
     * @return score diff (may be negative)
     */
    public int getDiff() {
        return diff;
    }

    /**
     * Returns the computed score rank.
     *
     * @return score rank
     */
    public ScoreRank getRank() {
        return rank;
    }

    /**
     * Sets the computed score rank.
     *
     * @param rank
     *            score rank
     */
    public void setRank(ScoreRank rank) {
        this.rank = rank;
    }

    /**
     * Returns the single-chart Volforce (integer × 10, e.g. {@code 369} = 36.9 VF).
     *
     * @return Volforce integer
     */
    public int getVf() {
        return vf;
    }

    /**
     * Sets the single-chart Volforce value.
     *
     * @param vf
     *            Volforce integer
     */
    public void setVf(int vf) {
        this.vf = vf;
    }
}