package com.sdvxhelper.model;

import java.util.ArrayList;
import java.util.List;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;

/**
 * JAXB root element wrapping the complete play history.
 *
 * <p>
 * Serialised to / deserialised from {@code alllog.xml}. Replaces the Python
 * {@code alllog.pkl} pickle file.
 * </p>
 *
 * <p>
 * Example XML structure:
 * </p>
 * 
 * <pre>{@code
 * <PlayLog>
 *     <play title="冥" curScore="9950000" ... />
 *     <play title="..." ... />
 * </PlayLog>
 * }</pre>
 *
 * @author Throdax
 * @since 2.0.0
 */
@XmlRootElement(name = "PlayLog")
@XmlAccessorType(XmlAccessType.FIELD)
public class PlayLog {

    /** All recorded play entries, ordered by date ascending after a sort. */
    @XmlElement(name = "play")
    private List<OnePlayData> plays = new ArrayList<>();

    /**
     * Returns the list of play records.
     *
     * @return mutable list of {@link OnePlayData}
     */
    public List<OnePlayData> getPlays() {
        return plays;
    }

    /**
     * Replaces the play list.
     *
     * @param plays
     *            new list of play records
     */
    public void setPlays(List<OnePlayData> plays) {
        this.plays = plays != null ? plays : new ArrayList<>();
    }
}
