package com.sdvxhelper.model;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;

/**
 * Pairs a difficulty name with its {@link DifficultyHashes} for JAXB
 * serialisation.
 *
 * <p>
 * Used to represent one difficulty tier (e.g. {@code "exh"}) within the nested
 * hash maps in {@link MusicList}.
 * </p>
 *
 * @author Throdax
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
        // Required by JAXB
    }

    /**
     * Constructs a group with the given difficulty key and hash entries.
     *
     * @param difficulty
     *            the difficulty key (e.g. {@code "exh"}, {@code "mxm"})
     * @param hashes
     *            the hash entries associated with this difficulty
     */
    public DifficultyHashGroup(String difficulty, DifficultyHashes hashes) {
        this.difficulty = difficulty;
        this.hashes = hashes;
    }

    /**
     * Returns the difficulty key string (e.g. {@code "exh"}, {@code "mxm"}).
     *
     * @return the difficulty key
     */
    public String getDifficulty() {
        return difficulty;
    }

    /**
     * Sets the difficulty key string (e.g. {@code "exh"}, {@code "mxm"}).
     *
     * @param difficulty
     *            the difficulty key to set
     */
    public void setDifficulty(String difficulty) {
        this.difficulty = difficulty;
    }

    /**
     * Returns the hash entries associated with this difficulty.
     *
     * @return the {@link DifficultyHashes} for this difficulty
     */
    public DifficultyHashes getHashes() {
        return hashes;
    }

    /**
     * Sets the hash entries associated with this difficulty.
     *
     * @param hashes
     *            the {@link DifficultyHashes} to set
     */
    public void setHashes(DifficultyHashes hashes) {
        this.hashes = hashes;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        return prime * result + ((difficulty == null) ? 0 : difficulty.hashCode());
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof DifficultyHashGroup)) {
            return false;
        }
        DifficultyHashGroup other = (DifficultyHashGroup) obj;
        if (difficulty == null) {
            if (other.difficulty != null) {
                return false;
            }
        } else if (!difficulty.equals(other.difficulty)) {
            return false;
        }
        return true;
    }

}