package com.sdvxhelper.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;

/**
 * JAXB root element wrapping the title-conversion table for Maya2 name
 * normalisation.
 *
 * <p>
 * Serialised to / deserialised from {@code title_conv_table.xml}. Replaces the
 * Python {@code resources/title_conv_table.pkl} pickle file.
 * </p>
 *
 * @author Throdax
 * @since 2.0.0
 */
@XmlRootElement(name = "TitleConversionTable")
@XmlAccessorType(XmlAccessType.FIELD)
public class TitleConvTable {

    /** Individual local-to-Maya2 title mappings. */
    @XmlElement(name = "entry")
    private List<TitleMapping> entries = new ArrayList<>();

    /**
     * Returns the mutable list of local-to-Maya2 title mappings.
     *
     * @return the list of {@link TitleMapping} entries
     */
    public List<TitleMapping> getEntries() {
        return entries;
    }

    /**
     * Sets the list of local-to-Maya2 title mappings.
     * If {@code null} is supplied an empty list is used instead.
     *
     * @param entries the list of {@link TitleMapping} entries to set
     */
    public void setEntries(List<TitleMapping> entries) {
        this.entries = entries != null ? entries : new ArrayList<>();
    }

    /**
     * Converts the entries list to a {@link Map} keyed by local title for O(1)
     * lookups.
     *
     * @return a map of local title to Maya2 title for all entries in this table
     */
    public Map<String, String> toMap() {
        Map<String, String> map = new HashMap<>(entries.size() * 2);
        for (TitleMapping m : entries) {
            map.put(m.getLocalTitle(), m.getMaya2Title());
        }
        return map;
    }

    /**
     * Populates the entries list from a local-title-to-Maya2-title {@link Map}.
     *
     * @param map a map of local title to Maya2 title to convert into
     *            {@link TitleMapping} elements
     */
    public void fromMap(Map<String, String> map) {
        entries = new ArrayList<>(map.size());
        for (Map.Entry<String, String> e : map.entrySet()) {
            entries.add(new TitleMapping(e.getKey(), e.getValue()));
        }
    }
}
