package com.sdvxhelper.model.overlay;

import java.util.ArrayList;
import java.util.List;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;

/**
 * JAXB root element for the battle overlay ({@code sdvx_battle.xml}).
 *
 * <p>
 * Serialises to:
 * </p>
 * 
 * <pre>{@code
 * <battle>
 *   <play title="…" diff="…" score="…" lamp="…" date="…"/>
 *   …
 * </battle>
 * }</pre>
 *
 * @author Throdax
 * @since 2.0.0
 */
@XmlRootElement(name = "battle")
@XmlAccessorType(XmlAccessType.FIELD)
public class BattleOverlay {

    /** Plays recorded since the session started, in chronological order. */
    @XmlElement(name = "play")
    private List<OverlayPlayEntry> plays = new ArrayList<>();

    /** No-argument constructor required by JAXB. */
    public BattleOverlay() {
    }

    /**
     * Returns the list of today's play entries.
     *
     * @return the mutable list of {@link OverlayPlayEntry} play entries
     */
    public List<OverlayPlayEntry> getPlays() {
        return plays;
    }

    /**
     * Sets the list of today's play entries.
     *
     * @param plays
     *            the list of {@link OverlayPlayEntry} play entries to set
     */
    public void setPlays(List<OverlayPlayEntry> plays) {
        this.plays = plays != null ? plays : new ArrayList<>();
    }
}
