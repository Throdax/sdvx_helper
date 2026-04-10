package com.sdvxhelper.model;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * JAXB root element wrapping the title-conversion table for Maya2 name normalisation.
 *
 * <p>Serialised to / deserialised from {@code title_conv_table.xml}.
 * Replaces the Python {@code resources/title_conv_table.pkl} pickle file.</p>
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

    /** @return mutable list of title mappings */
    public List<TitleMapping> getEntries() { return entries; }

    /** @param entries title mappings */
    public void setEntries(List<TitleMapping> entries) { this.entries = entries != null ? entries : new ArrayList<>(); }

    /**
     * Converts the entries list to a {@link Map} keyed by local title for O(1) lookups.
     *
     * @return local title → Maya2 title map
     */
    public Map<String, String> toMap() {
        Map<String, String> map = new HashMap<>(entries.size() * 2);
        for (TitleMapping m : entries) {
            map.put(m.getLocalTitle(), m.getMaya2Title());
        }
        return map;
    }

    /**
     * Populates entries from a {@link Map}.
     *
     * @param map local title → Maya2 title map
     */
    public void fromMap(Map<String, String> map) {
        entries = new ArrayList<>(map.size());
        for (Map.Entry<String, String> e : map.entrySet()) {
            entries.add(new TitleMapping(e.getKey(), e.getValue()));
        }
    }
}
