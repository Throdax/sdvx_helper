package com.sdvxhelper.model.overlay;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;

/**
 * JAXB element representing one play record inside the history and battle
 * overlay XML documents.
 *
 * <p>
 * History output: {@code <play score="…" lamp="…" diff="…" vf="…" date="…"/>}
 * </p>
 * <p>
 * Battle output: {@code <play title="…" diff="…" score="…" lamp="…" date="…"/>}
 * </p>
 *
 * @author Throdax
 * @since 2.0.0
 */
@XmlAccessorType(XmlAccessType.FIELD)
public class OverlayPlayEntry {

    /** Song title (used in the battle overlay). */
    @XmlAttribute
    private String title;

    /** Chart difficulty string (e.g. {@code "exh"}, {@code "mxm"}). */
    @XmlAttribute
    private String diff;

    /** Score achieved in this play. */
    @XmlAttribute
    private int score;

    /** Clear lamp string (e.g. {@code "puc"}, {@code "clear"}). */
    @XmlAttribute
    private String lamp;

    /**
     * Score delta vs the previous best (e.g. {@code "+500"}); formatted as a signed
     * integer string (used in the history overlay).
     */
    @XmlAttribute
    private String diffScore;

    /**
     * Single-chart Volforce formatted as one decimal place (e.g. {@code "17.3"});
     * used in the history overlay.
     */
    @XmlAttribute
    private String vf;

    /** Play date-time string ({@code "yyyy-MM-dd HH:mm:ss"}). */
    @XmlAttribute
    private String date;

    /** No-argument constructor required by JAXB. */
    public OverlayPlayEntry() {
    }

    /**
     * Returns the song title.
     *
     * @return the song title, or {@code null} if not set
     */
    public String getTitle() {
        return title;
    }

    /**
     * Sets the song title.
     *
     * @param title
     *            the song title to set
     */
    public void setTitle(String title) {
        this.title = title;
    }

    /**
     * Returns the chart difficulty string.
     *
     * @return the difficulty string
     */
    public String getDiff() {
        return diff;
    }

    /**
     * Sets the chart difficulty string.
     *
     * @param diff
     *            the difficulty string to set
     */
    public void setDiff(String diff) {
        this.diff = diff;
    }

    /**
     * Returns the play score.
     *
     * @return the play score
     */
    public int getScore() {
        return score;
    }

    /**
     * Sets the play score.
     *
     * @param score
     *            the play score to set
     */
    public void setScore(int score) {
        this.score = score;
    }

    /**
     * Returns the clear lamp string.
     *
     * @return the lamp string
     */
    public String getLamp() {
        return lamp;
    }

    /**
     * Sets the clear lamp string.
     *
     * @param lamp
     *            the lamp string to set
     */
    public void setLamp(String lamp) {
        this.lamp = lamp;
    }

    /**
     * Returns the signed score delta versus the previous best.
     *
     * @return the formatted score delta string (e.g. {@code "+500"})
     */
    public String getDiffScore() {
        return diffScore;
    }

    /**
     * Sets the signed score delta versus the previous best.
     *
     * @param diffScore
     *            the formatted score delta string to set
     */
    public void setDiffScore(String diffScore) {
        this.diffScore = diffScore;
    }

    /**
     * Returns the single-chart Volforce string.
     *
     * @return the Volforce string (e.g. {@code "17.3"})
     */
    public String getVf() {
        return vf;
    }

    /**
     * Sets the single-chart Volforce string.
     *
     * @param vf
     *            the Volforce string to set
     */
    public void setVf(String vf) {
        this.vf = vf;
    }

    /**
     * Returns the play date-time string.
     *
     * @return the date-time string
     */
    public String getDate() {
        return date;
    }

    /**
     * Sets the play date-time string.
     *
     * @param date
     *            the date-time string to set
     */
    public void setDate(String date) {
        this.date = date;
    }
}
