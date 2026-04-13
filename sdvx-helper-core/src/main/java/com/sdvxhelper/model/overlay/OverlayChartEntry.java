package com.sdvxhelper.model.overlay;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;

/**
 * JAXB element representing one chart entry inside the total-VF overlay.
 *
 * <p>
 * Serialises to:
 * </p>
 * 
 * <pre>{@code
 * <chart rank="…" title="…" diff="…" lv="…" score="…" lamp="…" vf="…"/>
 * }</pre>
 *
 * @author Throdax
 * @since 2.0.0
 */
@XmlAccessorType(XmlAccessType.FIELD)
public class OverlayChartEntry {

    /** 1-based rank in the top-50 list. */
    @XmlAttribute
    private int rank;

    /** Song title. */
    @XmlAttribute
    private String title;

    /** Chart difficulty string (e.g. {@code "exh"}, {@code "mxm"}). */
    @XmlAttribute
    private String diff;

    /** Chart level string. */
    @XmlAttribute
    private String lv;

    /** Personal-best score. */
    @XmlAttribute
    private int score;

    /** Personal-best clear lamp string. */
    @XmlAttribute
    private String lamp;

    /**
     * Single-chart Volforce formatted as one decimal place (e.g. {@code "17.3"}).
     */
    @XmlAttribute
    private String vf;

    /** No-argument constructor required by JAXB. */
    public OverlayChartEntry() {
    }

    /**
     * Returns the 1-based rank of this chart in the top-50 list.
     *
     * @return the rank
     */
    public int getRank() {
        return rank;
    }

    /**
     * Sets the 1-based rank of this chart in the top-50 list.
     *
     * @param rank
     *            the rank to set
     */
    public void setRank(int rank) {
        this.rank = rank;
    }

    /**
     * Returns the song title.
     *
     * @return the song title
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
     * Returns the chart level string.
     *
     * @return the chart level string
     */
    public String getLv() {
        return lv;
    }

    /**
     * Sets the chart level string.
     *
     * @param lv
     *            the chart level string to set
     */
    public void setLv(String lv) {
        this.lv = lv;
    }

    /**
     * Returns the personal-best score.
     *
     * @return the personal-best score
     */
    public int getScore() {
        return score;
    }

    /**
     * Sets the personal-best score.
     *
     * @param score
     *            the personal-best score to set
     */
    public void setScore(int score) {
        this.score = score;
    }

    /**
     * Returns the personal-best clear lamp string.
     *
     * @return the lamp string
     */
    public String getLamp() {
        return lamp;
    }

    /**
     * Sets the personal-best clear lamp string.
     *
     * @param lamp
     *            the lamp string to set
     */
    public void setLamp(String lamp) {
        this.lamp = lamp;
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
}
