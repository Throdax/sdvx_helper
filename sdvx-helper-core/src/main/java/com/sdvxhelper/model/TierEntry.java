package com.sdvxhelper.model;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;

/**
 * Pairs a song title with its tier classification for Grade-S tables.
 *
 * <p>
 * Used as an element within {@link GradeSEntry} to represent one song's tier
 * rating (e.g. {@code "SSS"}, {@code "SS"}, {@code "S"}) at a given chart
 * level.
 * </p>
 * 
 * @author Throdax
 * @since 2.0.0
 */
@XmlAccessorType(XmlAccessType.FIELD)
public class TierEntry {

    @XmlAttribute
    private String title;

    @XmlAttribute
    private String tier;

    /** No-argument constructor required by JAXB. */
    public TierEntry() {
    }

    /**
     * Constructs a tier entry pairing the given title with its tier classification.
     *
     * @param title
     *            the song title key
     * @param tier
     *            the tier classification string (e.g. {@code "SSS"}, {@code "SS"},
     *            {@code "S"})
     */
    public TierEntry(String title, String tier) {
        this.title = title;
        this.tier = tier;
    }

    /**
     * Returns the song title key.
     *
     * @return the song title
     */
    public String getTitle() {
        return title;
    }

    /**
     * Sets the song title key.
     *
     * @param title
     *            the song title to set
     */
    public void setTitle(String title) {
        this.title = title;
    }

    /**
     * Returns the tier classification string (e.g. {@code "SSS"}, {@code "SS"},
     * {@code "S"}).
     *
     * @return the tier classification string
     */
    public String getTier() {
        return tier;
    }

    /**
     * Sets the tier classification string (e.g. {@code "SSS"}, {@code "SS"},
     * {@code "S"}).
     *
     * @param tier
     *            the tier classification string to set
     */
    public void setTier(String tier) {
        this.tier = tier;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((tier == null) ? 0 : tier.hashCode());
        return prime * result + ((title == null) ? 0 : title.hashCode());
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof TierEntry)) {
            return false;
        }
        TierEntry other = (TierEntry) obj;
        if (tier == null) {
            if (other.tier != null) {
                return false;
            }
        } else if (!tier.equals(other.tier)) {
            return false;
        }
        if (title == null) {
            if (other.title != null) {
                return false;
            }
        } else if (!title.equals(other.title)) {
            return false;
        }
        return true;
    }
}