package com.sdvxhelper.model;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;

import java.util.ArrayList;
import java.util.List;

/**
 * JAXB root element wrapping all rival score data.
 *
 * <p>Serialised to / deserialised from {@code rival_log.xml}.
 * Replaces the Python {@code rival_log.pkl} pickle file.</p>
 *
 * @author Throdax
 * @since 2.0.0
 */
@XmlRootElement(name = "RivalLog")
@XmlAccessorType(XmlAccessType.FIELD)
public class RivalLog {

    /** One entry per tracked rival. */
    @XmlElement(name = "rival")
    private List<RivalEntry> rivals = new ArrayList<>();

    /** @return mutable list of rival entries */
    public List<RivalEntry> getRivals() { return rivals; }

    /** @param rivals rival entries */
    public void setRivals(List<RivalEntry> rivals) { this.rivals = rivals != null ? rivals : new ArrayList<>(); }
}
