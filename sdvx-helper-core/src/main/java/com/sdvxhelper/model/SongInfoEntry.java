package com.sdvxhelper.model;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;

/**
 * Pairs a song title with its {@link SongInfo} metadata for JAXB serialisation.
 *
 * <p>Used inside {@link MusicList#getTitles()} to represent the Python
 * {@code musiclist['titles']} dictionary as an XML element list.</p>
 *
 * @author Throdax
 * @since 2.0.0
 */
@XmlAccessorType(XmlAccessType.FIELD)
public class SongInfoEntry {

    /** Song title key. */
    @XmlAttribute
    private String title;

    /** Song metadata. */
    @XmlElement(name = "info")
    private SongInfo songInfo;

    /** No-argument constructor required by JAXB. */
    public SongInfoEntry() {
    }

    /**
     * Constructs an entry.
     *
     * @param title    song title
     * @param songInfo song metadata
     */
    public SongInfoEntry(String title, SongInfo songInfo) {
        this.title = title;
        this.songInfo = songInfo;
    }

    /** @return song title */
    public String getTitle() { return title; }

    /** @param title song title */
    public void setTitle(String title) { this.title = title; }

    /** @return song metadata */
    public SongInfo getSongInfo() { return songInfo; }

    /** @param songInfo song metadata */
    public void setSongInfo(SongInfo songInfo) { this.songInfo = songInfo; }
}
