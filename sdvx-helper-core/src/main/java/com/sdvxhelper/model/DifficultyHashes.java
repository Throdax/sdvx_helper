package com.sdvxhelper.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;

/**
 * Maps song titles to their perceptual-hash (or SHA) strings for one difficulty
 * tier.
 *
 * <p>
 * Used internally by {@link MusicList} to represent the nested dict structure
 * of {@code musiclist['jacket'][difficulty]} in the Python code. JAXB
 * serialises this as a list of {@link HashEntry} elements.
 * </p>
 *
 * @author Throdax
 * @since 2.0.0
 */
@XmlAccessorType(XmlAccessType.FIELD)
public class DifficultyHashes {

    /** Individual title-to-hash mappings. */
    @XmlElement(name = "entry")
    private List<HashEntry> entries = new ArrayList<>();

    /**
     * No-argument constructor required by JAXB.
     */
    public DifficultyHashes() {
        // Required by JAXB
    }

    /**
     * Returns all hash entries for this difficulty tier.
     *
     * @return mutable list of {@link HashEntry}
     */
    public List<HashEntry> getEntries() {
        return entries;
    }

    /**
     * Replaces the entries list.
     *
     * @param entries
     *            new entries
     */
    public void setEntries(List<HashEntry> entries) {
        this.entries = entries != null ? entries : new ArrayList<>();
    }

    /**
     * Converts the entry list to a {@link Map} for O(1) lookups by title.
     *
     * @return title → hash map
     */
    public Map<String, String> toMap() {
        Map<String, String> map = new HashMap<>(entries.size() * 2);
        for (HashEntry e : entries) {
            map.put(e.getTitle(), e.getHash());
        }
        return map;
    }

    /**
     * Populates entries from a {@link Map}.
     *
     * @param map
     *            title → hash map
     */
    public void fromMap(Map<String, String> map) {
        entries = new ArrayList<>(map.size());
        for (Map.Entry<String, String> e : map.entrySet()) {
            entries.add(new HashEntry(e.getKey(), e.getValue()));
        }
    }

}
