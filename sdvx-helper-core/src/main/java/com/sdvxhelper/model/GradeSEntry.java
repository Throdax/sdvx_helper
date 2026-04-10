package com.sdvxhelper.model;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Grade-S tier data for one level bucket (e.g. {@code "lv17"}, {@code "lv18"}, {@code "lv19"}).
 *
 * <p>Maps to one entry in the Python {@code musiclist['gradeS_lv17']} / {@code gradeS_lv18}
 * / {@code gradeS_lv19} dictionaries.</p>
 *
 * @author sdvx-helper
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

    /** No-argument constructor required by JAXB. */
    public GradeSEntry() {
    }

    /**
     * Constructs an entry.
     *
     * @param levelKey level bucket key
     */
    public GradeSEntry(String levelKey) {
        this.levelKey = levelKey;
    }

    /** @return level bucket key */
    public String getLevelKey() { return levelKey; }

    /** @param levelKey level bucket key */
    public void setLevelKey(String levelKey) { this.levelKey = levelKey; }

    /** @return mutable list of tier mappings */
    public List<TierEntry> getTiers() { return tiers; }

    /** @param tiers tier mappings */
    public void setTiers(List<TierEntry> tiers) { this.tiers = tiers != null ? tiers : new ArrayList<>(); }

    /**
     * Converts the tiers list to a {@link Map} for O(1) lookups.
     *
     * @return title → tier map
     */
    public Map<String, String> toMap() {
        Map<String, String> map = new HashMap<>(tiers.size() * 2);
        for (TierEntry e : tiers) {
            map.put(e.getTitle(), e.getTier());
        }
        return map;
    }

    /**
     * Populates tiers from a {@link Map}.
     *
     * @param map title → tier map
     */
    public void fromMap(Map<String, String> map) {
        tiers = new ArrayList<>(map.size());
        for (Map.Entry<String, String> e : map.entrySet()) {
            tiers.add(new TierEntry(e.getKey(), e.getValue()));
        }
    }

    /**
     * Pair of song title and its tier classification for Grade-S tables.
     */
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class TierEntry {

        @XmlAttribute
        private String title;

        @XmlAttribute
        private String tier;

        /** No-argument constructor required by JAXB. */
        public TierEntry() {
        }

        /**
         * Constructs a tier entry.
         *
         * @param title song title
         * @param tier  tier string (e.g. {@code "SSS"}, {@code "SS"})
         */
        public TierEntry(String title, String tier) {
            this.title = title;
            this.tier = tier;
        }

        /** @return song title */
        public String getTitle() { return title; }

        /** @param title song title */
        public void setTitle(String title) { this.title = title; }

        /** @return tier string */
        public String getTier() { return tier; }

        /** @param tier tier string */
        public void setTier(String tier) { this.tier = tier; }
    }
}
