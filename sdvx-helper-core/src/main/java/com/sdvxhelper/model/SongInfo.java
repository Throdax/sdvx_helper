package com.sdvxhelper.model;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;

/**
 * Static metadata for a song entry in the music database.
 *
 * <p>Corresponds to one row in the Python {@code musiclist['titles']} dictionary,
 * which stores {@code [artist, bpm, lv_nov, lv_adv, lv_exh, lv_append]} per title.</p>
 *
 * @author Throdax
 * @since 2.0.0
 */
@XmlAccessorType(XmlAccessType.FIELD)
public class SongInfo {

    /** Artist / composer name. */
    @XmlAttribute
    private String artist;

    /** BPM string (may be a range such as {@code "90-180"}). */
    @XmlAttribute
    private String bpm;

    /** Novice chart level (may be {@code "??"} if unknown). */
    @XmlAttribute
    private String lvNov;

    /** Advanced chart level. */
    @XmlAttribute
    private String lvAdv;

    /** Exhaust chart level. */
    @XmlAttribute
    private String lvExh;

    /** Maximum / Append chart level (may be {@code null} if absent). */
    @XmlAttribute
    private String lvAppend;

    /** No-argument constructor required by JAXB. */
    public SongInfo() {
    }

    /**
     * Constructs a fully populated {@code SongInfo}.
     *
     * @param artist   artist name
     * @param bpm      BPM string
     * @param lvNov    novice level
     * @param lvAdv    advanced level
     * @param lvExh    exhaust level
     * @param lvAppend maximum/append level (nullable)
     */
    public SongInfo(String artist, String bpm, String lvNov, String lvAdv,
                    String lvExh, String lvAppend) {
        this.artist = artist;
        this.bpm = bpm;
        this.lvNov = lvNov;
        this.lvAdv = lvAdv;
        this.lvExh = lvExh;
        this.lvAppend = lvAppend;
    }

    /** @return artist name */
    public String getArtist() { return artist; }

    /** @param artist artist name */
    public void setArtist(String artist) { this.artist = artist; }

    /** @return BPM string */
    public String getBpm() { return bpm; }

    /** @param bpm BPM string */
    public void setBpm(String bpm) { this.bpm = bpm; }

    /** @return novice chart level string */
    public String getLvNov() { return lvNov; }

    /** @param lvNov novice level string */
    public void setLvNov(String lvNov) { this.lvNov = lvNov; }

    /** @return advanced chart level string */
    public String getLvAdv() { return lvAdv; }

    /** @param lvAdv advanced level string */
    public void setLvAdv(String lvAdv) { this.lvAdv = lvAdv; }

    /** @return exhaust chart level string */
    public String getLvExh() { return lvExh; }

    /** @param lvExh exhaust level string */
    public void setLvExh(String lvExh) { this.lvExh = lvExh; }

    /** @return maximum/append chart level string, or {@code null} */
    public String getLvAppend() { return lvAppend; }

    /** @param lvAppend maximum/append level string */
    public void setLvAppend(String lvAppend) { this.lvAppend = lvAppend; }
}
