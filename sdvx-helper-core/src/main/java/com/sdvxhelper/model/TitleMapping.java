package com.sdvxhelper.model;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;

/**
 * A single title mapping used in {@link TitleConvTable}.
 *
 * <p>Maps the local filesystem-safe title string to the corresponding Maya2 server title.</p>
 *
 * @author sdvx-helper
 * @since 2.0.0
 */
@XmlAccessorType(XmlAccessType.FIELD)
public class TitleMapping {

    /** The title as it appears on the local system (filesystem-safe variant). */
    @XmlAttribute
    private String localTitle;

    /** The title as it appears on the Maya2 server. */
    @XmlAttribute
    private String maya2Title;

    /** No-argument constructor required by JAXB. */
    public TitleMapping() {
    }

    /**
     * Constructs a mapping.
     *
     * @param localTitle local title string
     * @param maya2Title Maya2 server title string
     */
    public TitleMapping(String localTitle, String maya2Title) {
        this.localTitle = localTitle;
        this.maya2Title = maya2Title;
    }

    /** @return local title */
    public String getLocalTitle() { return localTitle; }

    /** @param localTitle local title */
    public void setLocalTitle(String localTitle) { this.localTitle = localTitle; }

    /** @return Maya2 title */
    public String getMaya2Title() { return maya2Title; }

    /** @param maya2Title Maya2 title */
    public void setMaya2Title(String maya2Title) { this.maya2Title = maya2Title; }
}
