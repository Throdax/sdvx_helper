package com.sdvxhelper.model.overlay;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlRootElement;

/**
 * JAXB root element for the VF-on-select overlay ({@code vf_onselect.xml}).
 *
 * <p>
 * Serialises to either an empty element when no chart is selected:
 * </p>
 * 
 * <pre>{@code <vf_onselect/>}</pre>
 *
 * <p>
 * or a populated element when a chart is selected:
 * </p>
 * 
 * <pre>{@code
 * <vf_onselect title="…" diff="…" lv="…" score="…" lamp="…" vf="…"/>
 * }</pre>
 *
 * @author Throdax
 * @since 2.0.0
 */
@XmlRootElement(name = "vf_onselect")
@XmlAccessorType(XmlAccessType.FIELD)
public class VfOnSelectOverlay {

    /** Song title of the currently selected chart. */
    @XmlAttribute
    private String title;

    /** Chart difficulty string (e.g. {@code "exh"}, {@code "mxm"}). */
    @XmlAttribute
    private String diff;

    /** Chart level string (integer or {@code "??"}). */
    @XmlAttribute
    private String lv;

    /** Personal-best score for this chart. */
    @XmlAttribute
    private Integer score;

    /** Personal-best clear lamp string. */
    @XmlAttribute
    private String lamp;

    /**
     * Single-chart Volforce formatted as one decimal place (e.g. {@code "17.3"}).
     */
    @XmlAttribute
    private String vf;

    /** No-argument constructor required by JAXB. */
    public VfOnSelectOverlay() {
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
     * @return the personal-best score, or {@code null} if not set
     */
    public Integer getScore() {
        return score;
    }

    /**
     * Sets the personal-best score.
     *
     * @param score
     *            the personal-best score to set
     */
    public void setScore(Integer score) {
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
