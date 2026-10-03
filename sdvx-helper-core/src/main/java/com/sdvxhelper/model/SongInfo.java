package com.sdvxhelper.model;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;

/**
 * Static metadata for a song entry in the music database.
 *
 * <p>
 * Corresponds to one row in the Python {@code musiclist['titles']} dictionary,
 * which stores {@code [artist, bpm, lv_nov, lv_adv, lv_exh, lv_append]} per
 * title.
 * </p>
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
     * @param artist
     *            the artist or composer name
     * @param bpm
     *            the BPM string (may be a range, e.g. {@code "90-180"})
     * @param lvNov
     *            the novice chart level string
     * @param lvAdv
     *            the advanced chart level string
     * @param lvExh
     *            the exhaust chart level string
     * @param lvAppend
     *            the maximum/append chart level string, or {@code null} if absent
     */
    public SongInfo(String artist, String bpm, String lvNov, String lvAdv, String lvExh, String lvAppend) {
        this.artist = artist;
        this.bpm = bpm;
        this.lvNov = lvNov;
        this.lvAdv = lvAdv;
        this.lvExh = lvExh;
        this.lvAppend = lvAppend;
    }

    /**
     * Returns the artist or composer name.
     *
     * @return the artist name
     */
    public String getArtist() {
        return artist;
    }

    /**
     * Sets the artist or composer name.
     *
     * @param artist
     *            the artist name to set
     */
    public void setArtist(String artist) {
        this.artist = artist;
    }

    /**
     * Returns the BPM string (may be a range, e.g. {@code "90-180"}).
     *
     * @return the BPM string
     */
    public String getBpm() {
        return bpm;
    }

    /**
     * Sets the BPM string.
     *
     * @param bpm
     *            the BPM string to set
     */
    public void setBpm(String bpm) {
        this.bpm = bpm;
    }

    /**
     * Returns the novice chart level string (may be {@code "??"} if unknown).
     *
     * @return the novice chart level string
     */
    public String getLvNov() {
        return lvNov;
    }

    /**
     * Sets the novice chart level string.
     *
     * @param lvNov
     *            the novice chart level string to set
     */
    public void setLvNov(String lvNov) {
        this.lvNov = lvNov;
    }

    /**
     * Returns the advanced chart level string.
     *
     * @return the advanced chart level string
     */
    public String getLvAdv() {
        return lvAdv;
    }

    /**
     * Sets the advanced chart level string.
     *
     * @param lvAdv
     *            the advanced chart level string to set
     */
    public void setLvAdv(String lvAdv) {
        this.lvAdv = lvAdv;
    }

    /**
     * Returns the exhaust chart level string.
     *
     * @return the exhaust chart level string
     */
    public String getLvExh() {
        return lvExh;
    }

    /**
     * Sets the exhaust chart level string.
     *
     * @param lvExh
     *            the exhaust chart level string to set
     */
    public void setLvExh(String lvExh) {
        this.lvExh = lvExh;
    }

    /**
     * Returns the maximum/append chart level string.
     *
     * @return the maximum/append chart level string, or {@code null} if absent
     */
    public String getLvAppend() {
        return lvAppend;
    }

    /**
     * Sets the maximum/append chart level string.
     *
     * @param lvAppend
     *            the maximum/append chart level string to set, or {@code null} if
     *            absent
     */
    public void setLvAppend(String lvAppend) {
        this.lvAppend = lvAppend;
    }

    /**
     * Returns the chart level string for the given difficulty code.
     *
     * <p>
     * Dispatches to the per-difficulty getter that matches {@code difficulty}.
     * Append-tier difficulties (mxm, inf, grv, hvn, vvd, xcd) fall back to the EXH
     * level when no dedicated append level is stored.
     * </p>
     *
     * @param difficulty
     *            chart difficulty code (e.g. {@code "nov"}, {@code "adv"},
     *            {@code "exh"}, {@code "mxm"}, {@code "inf"}, etc.),
     *            case-insensitive; {@code null} returns the EXH level
     * @return the chart level string for the requested difficulty, or the EXH level
     *         as fallback when the code is unrecognised
     */
    public String getLvForDifficulty(String difficulty) {
        if (difficulty == null) {
            return getLvExh();
        }
        return switch (difficulty.toLowerCase()) {
            case "nov" -> getLvNov();
            case "adv" -> getLvAdv();
            case "append", "mxm", "inf", "grv", "hvn", "vvd", "xcd" -> lvAppend != null ? getLvAppend() : getLvExh();
            default -> getLvExh();
        };
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((artist == null) ? 0 : artist.hashCode());
        result = prime * result + ((bpm == null) ? 0 : bpm.hashCode());
        result = prime * result + ((lvAdv == null) ? 0 : lvAdv.hashCode());
        result = prime * result + ((lvAppend == null) ? 0 : lvAppend.hashCode());
        result = prime * result + ((lvExh == null) ? 0 : lvExh.hashCode());
        return prime * result + ((lvNov == null) ? 0 : lvNov.hashCode());
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof SongInfo)) {
            return false;
        }
        SongInfo other = (SongInfo) obj;
        if (artist == null) {
            if (other.artist != null) {
                return false;
            }
        } else if (!artist.equals(other.artist)) {
            return false;
        }
        if (bpm == null) {
            if (other.bpm != null) {
                return false;
            }
        } else if (!bpm.equals(other.bpm)) {
            return false;
        }
        if (lvAdv == null) {
            if (other.lvAdv != null) {
                return false;
            }
        } else if (!lvAdv.equals(other.lvAdv)) {
            return false;
        }
        if (lvAppend == null) {
            if (other.lvAppend != null) {
                return false;
            }
        } else if (!lvAppend.equals(other.lvAppend)) {
            return false;
        }
        if (lvExh == null) {
            if (other.lvExh != null) {
                return false;
            }
        } else if (!lvExh.equals(other.lvExh)) {
            return false;
        }
        if (lvNov == null) {
            if (other.lvNov != null) {
                return false;
            }
        } else if (!lvNov.equals(other.lvNov)) {
            return false;
        }
        return true;
    }
}
