package com.sdvxhelper.model;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;

/**
 * Pairs a difficulty name with its {@link DifficultyHashes} for JAXB serialisation.
 *
 * <p>Used to represent one difficulty tier (e.g. {@code "exh"}) within the nested
 * hash maps in {@link MusicList}.</p>
 *
 * @author sdvx-helper
 * @since 2.0.0
 */
@XmlAccessorType(XmlAccessType.FIELD)
public class DifficultyHashGroup {

    /** Difficulty key string (e.g. {@code "exh"}, {@code "mxm"}). */
    @XmlAttribute
    private String difficulty;

    /** Hash entries for this difficulty. */
    @XmlElement(name = "hashes")
    private DifficultyHashes hashes = new DifficultyHashes();

    /** No-argument constructor required by JAXB. */
    public DifficultyHashGroup() {
    }

    /**
     * Constructs a group.
     *
     * @param difficulty difficulty key
     * @param hashes     hash entries
     */
    public DifficultyHashGroup(String difficulty, DifficultyHashes hashes) {
        this.difficulty = difficulty;
        this.hashes = hashes;
    }

    /** @return difficulty key */
    public String getDifficulty() { return difficulty; }

    /** @param difficulty difficulty key */
    public void setDifficulty(String difficulty) { this.difficulty = difficulty; }

    /** @return difficulty hash entries */
    public DifficultyHashes getHashes() { return hashes; }

    /** @param hashes difficulty hash entries */
    public void setHashes(DifficultyHashes hashes) { this.hashes = hashes; }
}
