package com.sdvxhelper.model;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;

/**
 * A single title-to-hash mapping used within {@link DifficultyHashes}.
 *
 * @author sdvx-helper
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

    /** No-argument constructor required by JAXB. */
    public HashEntry() {
    }

    /**
     * Constructs a new mapping.
     *
     * @param title song title
     * @param hash  hash string
     */
    public HashEntry(String title, String hash) {
        this.title = title;
        this.hash = hash;
    }

    /** @return song title */
    public String getTitle() { return title; }

    /** @param title song title */
    public void setTitle(String title) { this.title = title; }

    /** @return hash string */
    public String getHash() { return hash; }

    /** @param hash hash string */
    public void setHash(String hash) { this.hash = hash; }
}
