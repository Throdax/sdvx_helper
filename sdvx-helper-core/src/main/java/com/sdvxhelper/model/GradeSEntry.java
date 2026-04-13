package com.sdvxhelper.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;

/**
 * Grade-S tier data for one level bucket (e.g. {@code "lv17"}, {@code "lv18"},
 * {@code "lv19"}).
 *
 * <p>
 * Maps to one entry in the Python {@code musiclist['gradeS_lv17']} /
 * {@code gradeS_lv18} / {@code gradeS_lv19} dictionaries.
 * </p>
 *
 * @author Throdax
 * @since 2.0.0
 */
@XmlAccessorType(XmlAccessType.FIELD)
public class GradeSEntry {

    /** Level bucket key, e.g. {@code "lv17"}. */
    @XmlAttribute
    private String levelKey;

    /** Individual title-to-tier mappings for this level. */
    @XmlElement(name = "tier")
    private List<TierEntry> tiers = new ArrayList<>();

    /**
     * No-argument constructor required by JAXB.
     */
    public GradeSEntry() {
        // Required by JAXB
    }

    /**
     * Constructs an entry with the given level bucket key.
     *
     * @param levelKey
     *            the level bucket key (e.g. {@code "lv17"}, {@code "lv18"})
     */
    public GradeSEntry(String levelKey) {
        this.levelKey = levelKey;
    }

    /**
     * Returns the level bucket key (e.g. {@code "lv17"}, {@code "lv18"}).
     *
     * @return the level bucket key
     */
    public String getLevelKey() {
        return levelKey;
    }

    /**
     * Sets the level bucket key (e.g. {@code "lv17"}, {@code "lv18"}).
     *
     * @param levelKey
     *            the level bucket key to set
     */
    public void setLevelKey(String levelKey) {
        this.levelKey = levelKey;
    }

    /**
     * Returns the mutable list of tier mappings for this level bucket.
     *
     * @return the list of {@link TierEntry} tier mappings
     */
    public List<TierEntry> getTiers() {
        return tiers;
    }

    /**
     * Sets the tier mappings for this level bucket. If {@code null} is supplied an
     * empty list is used instead.
     *
     * @param tiers
     *            the list of {@link TierEntry} tier mappings to set
     */
    public void setTiers(List<TierEntry> tiers) {
        this.tiers = tiers != null ? tiers : new ArrayList<>();
    }

    /**
     * Converts the tiers list to a {@link Map} for O(1) lookups by title.
     *
     * @return a map of song title to tier string for all entries in this level
     *         bucket
     */
    public Map<String, String> toMap() {
        Map<String, String> map = new HashMap<>(tiers.size() * 2);
        for (TierEntry e : tiers) {
            map.put(e.getTitle(), e.getTier());
        }
        return map;
    }

    /**
     * Populates the tiers list from a title-to-tier {@link Map}.
     *
     * @param map
     *            a map of song title to tier string to convert into
     *            {@link TierEntry} elements
     */
    public void fromMap(Map<String, String> map) {
        tiers = new ArrayList<>(map.size());
        for (Map.Entry<String, String> e : map.entrySet()) {
            tiers.add(new TierEntry(e.getKey(), e.getValue()));
        }
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        return prime * result + ((levelKey == null) ? 0 : levelKey.hashCode());
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof GradeSEntry)) {
            return false;
        }
        GradeSEntry other = (GradeSEntry) obj;
        if (levelKey == null) {
            if (other.levelKey != null) {
                return false;
            }
        } else if (!levelKey.equals(other.levelKey)) {
            return false;
        }
        return true;
    }
}
