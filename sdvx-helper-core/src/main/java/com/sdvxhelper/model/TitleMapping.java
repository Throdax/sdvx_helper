package com.sdvxhelper.model;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;

/**
 * A single title mapping used in {@link TitleConvTable}.
 *
 * <p>
 * Maps the local filesystem-safe title string to the corresponding Maya2 server
 * title.
 * </p>
 *
 * @author Throdax
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

    /**
     * No-argument constructor required by JAXB.
     */
    public TitleMapping() {
    }

    /**
     * Constructs a mapping between a local title and its Maya2 server equivalent.
     *
     * @param localTitle
     *            the title as it appears on the local system (filesystem-safe
     *            variant)
     * @param maya2Title
     *            the title as it appears on the Maya2 server
     */
    public TitleMapping(String localTitle, String maya2Title) {
        this.localTitle = localTitle;
        this.maya2Title = maya2Title;
    }

    /**
     * Returns the title as it appears on the local system (filesystem-safe
     * variant).
     *
     * @return the local title string
     */
    public String getLocalTitle() {
        return localTitle;
    }

    /**
     * Sets the title as it appears on the local system (filesystem-safe variant).
     *
     * @param localTitle
     *            the local title string to set
     */
    public void setLocalTitle(String localTitle) {
        this.localTitle = localTitle;
    }

    /**
     * Returns the title as it appears on the Maya2 server.
     *
     * @return the Maya2 server title string
     */
    public String getMaya2Title() {
        return maya2Title;
    }

    /**
     * Sets the title as it appears on the Maya2 server.
     *
     * @param maya2Title
     *            the Maya2 server title string to set
     */
    public void setMaya2Title(String maya2Title) {
        this.maya2Title = maya2Title;
    }
}
