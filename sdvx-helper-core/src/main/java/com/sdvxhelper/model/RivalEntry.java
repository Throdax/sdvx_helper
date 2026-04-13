package com.sdvxhelper.model;

import java.util.ArrayList;
import java.util.List;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;

/**
 * Score records for a single rival player.
 *
 * <p>
 * Used within {@link RivalLog} to represent the Python {@code rival_log.pkl}
 * dict entry for one rival name.
 * </p>
 *
 * @author Throdax
 * @since 2.0.0
 */
@XmlAccessorType(XmlAccessType.FIELD)
public class RivalEntry {

    /** The rival player's in-game name. */
    @XmlAttribute
    private String name;

    /** Per-chart best scores for this rival. */
    @XmlElement(name = "score")
    private List<MusicInfo> scores = new ArrayList<>();

    /** No-argument constructor required by JAXB. */
    public RivalEntry() {
    }

    /**
     * Constructs an entry for the given rival.
     *
     * @param name the rival player's in-game name
     */
    public RivalEntry(String name) {
        this.name = name;
    }

    /**
     * Returns the rival player's in-game name.
     *
     * @return the rival player name
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the rival player's in-game name.
     *
     * @param name the rival player name to set
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Returns the mutable list of per-chart best scores for this rival.
     *
     * @return the list of rival {@link MusicInfo} score records
     */
    public List<MusicInfo> getScores() {
        return scores;
    }

    /**
     * Sets the per-chart best scores for this rival.
     * If {@code null} is supplied an empty list is used instead.
     *
     * @param scores the list of rival {@link MusicInfo} score records to set
     */
    public void setScores(List<MusicInfo> scores) {
        this.scores = scores != null ? scores : new ArrayList<>();
    }
}
