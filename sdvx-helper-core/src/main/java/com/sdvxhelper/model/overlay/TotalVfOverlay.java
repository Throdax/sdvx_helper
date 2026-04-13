package com.sdvxhelper.model.overlay;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;

import java.util.ArrayList;
import java.util.List;

/**
 * JAXB root element for the total-Volforce overlay ({@code total_vf.xml}).
 *
 * <p>Serialises to:</p>
 * <pre>{@code
 * <total_vf value="17.255">
 *   <chart rank="1" title="…" diff="…" lv="…" score="…" lamp="…" vf="…"/>
 *   …
 * </total_vf>
 * }</pre>
 *
 * @author Throdax
 * @since 2.0.0
 */
@XmlRootElement(name = "total_vf")
@XmlAccessorType(XmlAccessType.FIELD)
public class TotalVfOverlay {

    /**
     * Total Volforce formatted to three decimal places (e.g. {@code "17.255"}).
     */
    @XmlAttribute
    private String value;

    /** Top-N chart breakdown, sorted by VF descending. */
    @XmlElement(name = "chart")
    private List<OverlayChartEntry> charts = new ArrayList<>();

    /** No-argument constructor required by JAXB. */
    public TotalVfOverlay() {
    }

    /**
     * Returns the total Volforce string.
     *
     * @return the total VF string (e.g. {@code "17.255"})
     */
    public String getValue() {
        return value;
    }

    /**
     * Sets the total Volforce string.
     *
     * @param value the total VF string to set
     */
    public void setValue(String value) {
        this.value = value;
    }

    /**
     * Returns the list of chart breakdown entries.
     *
     * @return the mutable list of {@link OverlayChartEntry} entries
     */
    public List<OverlayChartEntry> getCharts() {
        return charts;
    }

    /**
     * Sets the list of chart breakdown entries.
     *
     * @param charts the list of {@link OverlayChartEntry} entries to set
     */
    public void setCharts(List<OverlayChartEntry> charts) {
        this.charts = charts != null ? charts : new ArrayList<>();
    }
}
