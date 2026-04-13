package com.sdvxhelper.model.overlay;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;

import java.util.ArrayList;
import java.util.List;

/**
 * JAXB root element for the play-history overlay ({@code history_cursong.xml}).
 *
 * <p>Serialises to:</p>
 * <pre>{@code
 * <history>
 *   <play score="…" lamp="…" diffScore="…" vf="…" date="…"/>
 *   …
 * </history>
 * }</pre>
 *
 * @author Throdax
 * @since 2.0.0
 */
@XmlRootElement(name = "history")
@XmlAccessorType(XmlAccessType.FIELD)
public class HistoryOverlay {

    /** Individual play entries, most-recent first. */
    @XmlElement(name = "play")
    private List<OverlayPlayEntry> plays = new ArrayList<>();

    /** No-argument constructor required by JAXB. */
    public HistoryOverlay() {
    }

    /**
     * Returns the list of play entries.
     *
     * @return the mutable list of {@link OverlayPlayEntry} play entries
     */
    public List<OverlayPlayEntry> getPlays() {
        return plays;
    }

    /**
     * Sets the list of play entries.
     *
     * @param plays the list of {@link OverlayPlayEntry} play entries to set
     */
    public void setPlays(List<OverlayPlayEntry> plays) {
        this.plays = plays != null ? plays : new ArrayList<>();
    }
}
