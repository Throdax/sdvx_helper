package com.sdvxhelper.model;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;

/**
 * A single title-to-hash mapping used within {@link DifficultyHashes}.
 *
 * @author Throdax
 * @since 2.0.0
 */
@XmlAccessorType(XmlAccessType.FIELD)
public class HashEntry {

    /** Song title key. */
    @XmlAttribute
    private String title;

    /** Perceptual hash or SHA hex string. */
    @XmlAttribute
    private String hash;

    /**
     * No-argument constructor required by JAXB.
     */
    public HashEntry() {
        // Required by JAXB
    }

    /**
     * Constructs a new title-to-hash mapping.
     *
     * @param title
     *            the song title key
     * @param hash
     *            the perceptual hash or SHA hex string
     */
    public HashEntry(String title, String hash) {
        this.title = title;
        this.hash = hash;
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
     * Returns the perceptual hash or SHA hex string.
     *
     * @return the hash string
     */
    public String getHash() {
        return hash;
    }

    /**
     * Sets the perceptual hash or SHA hex string.
     *
     * @param hash
     *            the hash string to set
     */
    public void setHash(String hash) {
        this.hash = hash;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        return prime * result + ((title == null) ? 0 : title.hashCode());
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof HashEntry)) {
            return false;
        }
        HashEntry other = (HashEntry) obj;
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
