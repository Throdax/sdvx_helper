package com.sdvxhelper.model;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;

import java.util.ArrayList;
import java.util.List;

/**
 * Score records for a single rival player.
 *
 * <p>Used within {@link RivalLog} to represent the Python
 * {@code rival_log.pkl} dict entry for one rival name.</p>
 *
 * @author sdvx-helper
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
     * @param name rival player name
     */
    public RivalEntry(String name) {
        this.name = name;
    }

    /** @return rival player name */
    public String getName() { return name; }

    /** @param name rival player name */
    public void setName(String name) { this.name = name; }

    /** @return mutable list of rival score records */
    public List<MusicInfo> getScores() { return scores; }

    /** @param scores rival score records */
    public void setScores(List<MusicInfo> scores) { this.scores = scores != null ? scores : new ArrayList<>(); }
}
