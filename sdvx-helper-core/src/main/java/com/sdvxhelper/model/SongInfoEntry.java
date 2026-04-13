package com.sdvxhelper.model;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;

/**
 * Pairs a song title with its {@link SongInfo} metadata for JAXB serialisation.
 *
 * <p>
 * Used inside {@link MusicList#getTitles()} to represent the Python
 * {@code musiclist['titles']} dictionary as an XML element list.
 * </p>
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
     * Constructs an entry pairing the given title with its metadata.
     *
     * @param title    the song title key
     * @param songInfo the {@link SongInfo} metadata for this song
     */
    public SongInfoEntry(String title, SongInfo songInfo) {
        this.title = title;
        this.songInfo = songInfo;
    }

    /**
     * Returns the song title key.
     *
     * @return the song title
     */
    public String getTitle() {
        return title;
    }

    /**
     * Sets the song title key.
     *
     * @param title the song title to set
     */
    public void setTitle(String title) {
        this.title = title;
    }

    /**
     * Returns the song metadata associated with this title.
     *
     * @return the {@link SongInfo} metadata
     */
    public SongInfo getSongInfo() {
        return songInfo;
    }

    /**
     * Sets the song metadata associated with this title.
     *
     * @param songInfo the {@link SongInfo} metadata to set
     */
    public void setSongInfo(SongInfo songInfo) {
        this.songInfo = songInfo;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        return prime * result + ((title == null) ? 0 : title.hashCode());
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof SongInfoEntry)) {
            return false;
        }
        SongInfoEntry other = (SongInfoEntry) obj;
        if (title == null) {
            if (other.title != null) {
                return false;
            }
        } else if (!title.equals(other.title)) {
            return false;
        }
        return true;
    }
}
