package com.sdvxhelper.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlTransient;
import jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import com.sdvxhelper.model.enums.ScoreRank;

/**
 * Represents the personal-best information for one specific chart (song +
 * difficulty).
 *
 * <p>
 * Each instance corresponds to a single chart entry, so the same song will have
 * separate {@code MusicInfo} objects for its EXH and MXM charts. The natural
 * ordering sorts by Volforce descending (highest VF first), consistent with the
 * Python implementation.
 *
 * <p>
 * Maps to the Python {@code MusicInfo} class in {@code sdvxh_classes.py}.
 * </p>
 *
 * @author Throdax
 * @since 2.0.0
 */
@XmlAccessorType(XmlAccessType.FIELD)
public class MusicInfo implements Comparable<MusicInfo> {

    /** Song title. */
    @XmlAttribute
    private String title;

    /** Artist / composer name. */
    @XmlAttribute
    private String artist;

    /**
     * BPM displayed on the song select screen (may be a range, e.g.
     * {@code "120-200"}).
     */
    @XmlAttribute
    private String bpm;

    /** Chart difficulty string (e.g. {@code "exh"}, {@code "mxm"}). */
    @XmlAttribute
    private String difficulty;

    /**
     * Chart level. Stored as {@code Object} because non-integer levels (e.g.
     * {@code "??"}) exist for unknown entries. In practice, the value is either an
     * {@code Integer} or a {@code String}.
     */
    @XmlAttribute
    private String lv;

    /** Personal-best score for this chart. */
    @XmlAttribute
    private int bestScore;

    /** Clear lamp for the personal-best play. */
    @XmlAttribute
    private String bestLamp;

    /** Date of the personal-best play; serialised via {@link LocalDateTimeXMLAdapter}. */
    @XmlAttribute
    @XmlJavaTypeAdapter(LocalDateTimeXMLAdapter.class)
    private LocalDateTime date;

    /**
     * Grade-S tier classification (e.g. {@code "SSS"}, {@code "SS"}, {@code "S"}).
     */
    @XmlAttribute
    private String sTier;

    /** Perfect-UC tier classification. */
    @XmlAttribute
    private String pTier;

    /** Computed score rank; not persisted. */
    @XmlTransient
    private ScoreRank rank = ScoreRank.NO_VALUE;

    /**
     * Single-chart Volforce (integer × 10, e.g. {@code 369} = 36.9 VF). Not
     * persisted; computed by {@code VolforceCalculator}.
     */
    @XmlTransient
    private int vf;

    /** No-argument constructor required by JAXB. */
    public MusicInfo() {
        // Required by JAXB
    }

    // -------------------------------------------------------------------------
    // Comparable: sort by VF descending (higher VF is "less than" for descending
    // order)
    // -------------------------------------------------------------------------

    /**
     * Sorts by Volforce descending so that the highest-VF chart sorts first when
     * placed in a sorted collection.
     *
     * @param other the other {@code MusicInfo}
     * @return negative if this VF is higher, positive if lower
     */
    @Override
    public int compareTo(MusicInfo other) {
        return Integer.compare(other.vf, this.vf);
    }

    @Override
    public String toString() {
        return String.format("MusicInfo{title='%s', diff='%s', lv=%s, score=%d, lamp='%s', vf=%d}", title, difficulty, lv, bestScore, bestLamp, vf);
    }

    // -------------------------------------------------------------------------
    // Getters and setters
    // -------------------------------------------------------------------------

    /**
     * Returns the song title.
     *
     * @return the song title
     */
    public String getTitle() {
        return title;
    }

    /**
     * Sets the song title.
     *
     * @param title the song title to set
     */
    public void setTitle(String title) {
        this.title = title;
    }

    /**
     * Returns the artist name.
     *
     * @return the artist name
     */
    public String getArtist() {
        return artist;
    }

    /**
     * Sets the artist name.
     *
     * @param artist the artist name to set
     */
    public void setArtist(String artist) {
        this.artist = artist;
    }

    /**
     * Returns the BPM string as displayed on the song select screen (may be a
     * range, e.g. {@code "120-200"}).
     *
     * @return the BPM string
     */
    public String getBpm() {
        return bpm;
    }

    /**
     * Sets the BPM string.
     *
     * @param bpm the BPM string to set
     */
    public void setBpm(String bpm) {
        this.bpm = bpm;
    }

    /**
     * Returns the chart difficulty string (e.g. {@code "exh"}, {@code "mxm"}).
     *
     * @return the chart difficulty string
     */
    public String getDifficulty() {
        return difficulty;
    }

    /**
     * Sets the chart difficulty string (e.g. {@code "exh"}, {@code "mxm"}).
     *
     * @param difficulty the chart difficulty string to set
     */
    public void setDifficulty(String difficulty) {
        this.difficulty = difficulty;
    }

    /**
     * Returns the chart level as a string. Parse with
     * {@link Integer#parseInt(String)} only after confirming it is numeric.
     *
     * @return the chart level string (integer or {@code "??"} for unknown)
     */
    public String getLv() {
        return lv;
    }

    /**
     * Sets the chart level string.
     *
     * @param lv the chart level string to set (integer or {@code "??"} for unknown)
     */
    public void setLv(String lv) {
        this.lv = lv;
    }

    /**
     * Convenience method: returns the chart level as an {@code int}, or {@code -1}
     * if the level string is non-numeric (e.g. {@code "??"}).
     *
     * @return the integer chart level, or {@code -1} for unknown levels
     */
    public int getLvAsInt() {
        try {
            return Integer.parseInt(lv);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * Returns the personal-best score for this chart.
     *
     * @return the personal-best score
     */
    public int getBestScore() {
        return bestScore;
    }

    /**
     * Sets the personal-best score for this chart.
     *
     * @param bestScore the personal-best score to set
     */
    public void setBestScore(int bestScore) {
        this.bestScore = bestScore;
    }

    /**
     * Returns the clear lamp for the personal-best play.
     *
     * @return the personal-best clear lamp
     */
    public String getBestLamp() {
        return bestLamp;
    }

    /**
     * Sets the clear lamp for the personal-best play.
     *
     * @param bestLamp the personal-best clear lamp to set
     */
    public void setBestLamp(String bestLamp) {
        this.bestLamp = bestLamp;
    }

    // -------------------------------------------------------------------------
    // Date – LocalDateTime with JAXB bridge
    // -------------------------------------------------------------------------

    /** Format shared by {@link #marshalDate} and {@link #unmarshalDate}, matching {@link LocalDateTimeXMLAdapter}. */
    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** Fallback format that accepts values without seconds (e.g. {@code "2024-01-01 21:05"}). */
    private static final DateTimeFormatter DATE_FMT_SHORT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /**
     * Returns the date of the personal-best play as a {@link LocalDateTime}.
     *
     * @return the personal-best date, or {@code null} if not set
     */
    public LocalDateTime getDate() {
        return date;
    }

    /**
     * Sets the date of the personal-best play.
     *
     * @param date the personal-best {@link LocalDateTime} to set
     */
    public void setDate(LocalDateTime date) {
        this.date = date;
    }

    /**
     * Converts a {@link LocalDateTime} to its canonical string representation
     * ({@code "yyyy-MM-dd HH:mm:ss"}), matching the format used by
     * {@link LocalDateTimeXMLAdapter}.
     *
     * @param dateTime the date-time to format, may be {@code null}
     * @return the formatted string, or {@code null} if {@code dateTime} is {@code null}
     */
    public static String marshalDate(LocalDateTime dateTime) {
        return dateTime != null ? dateTime.format(DATE_FMT) : null;
    }

    /**
     * Parses a date string into a {@link LocalDateTime}.
     * Accepts both the full format {@code "yyyy-MM-dd HH:mm:ss"} (used by
     * {@link LocalDateTimeXMLAdapter}) and the short form {@code "yyyy-MM-dd HH:mm"}.
     * A bare date ({@code "yyyy-MM-dd"}) is resolved to midnight ({@code 00:00:00}).
     *
     * @param value the date string to parse, may be {@code null} or blank
     * @return the parsed {@link LocalDateTime}, or {@code null} if the value is
     *         {@code null}, blank, or cannot be parsed
     */
    public static LocalDateTime unmarshalDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            if (value.length() == 10) {
                // bare "yyyy-MM-dd" → midnight
                return LocalDateTime.parse(value + " 00:00:00", DATE_FMT);
            }
            if (value.length() == 16) {
                // "yyyy-MM-dd HH:mm" — no seconds
                return LocalDateTime.parse(value, DATE_FMT_SHORT);
            }
            return LocalDateTime.parse(value, DATE_FMT);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /**
     * Returns the grade-S tier classification (e.g. {@code "SSS"}, {@code "SS"},
     * {@code "S"}).
     *
     * @return the grade-S tier string
     */
    public String getSTier() {
        return sTier;
    }

    /**
     * Sets the grade-S tier classification.
     *
     * @param sTier the grade-S tier string to set
     */
    public void setSTier(String sTier) {
        this.sTier = sTier;
    }

    /**
     * Returns the perfect-UC tier classification.
     *
     * @return the perfect-UC tier string
     */
    public String getPTier() {
        return pTier;
    }

    /**
     * Sets the perfect-UC tier classification.
     *
     * @param pTier the perfect-UC tier string to set
     */
    public void setPTier(String pTier) {
        this.pTier = pTier;
    }

    /**
     * Returns the computed score rank for this chart.
     *
     * @return the {@link ScoreRank} for this chart's personal-best score
     */
    public ScoreRank getRank() {
        return rank;
    }

    /**
     * Sets the computed score rank for this chart.
     *
     * @param rank the {@link ScoreRank} to set
     */
    public void setRank(ScoreRank rank) {
        this.rank = rank;
    }

    /**
     * Returns the single-chart Volforce (integer representation).
     *
     * @return the VF integer (e.g. {@code 369} represents 36.9 VF)
     */
    public int getVf() {
        return vf;
    }

    /**
     * Sets the single-chart Volforce (integer representation).
     *
     * @param vf the VF integer to set (e.g. {@code 369} represents 36.9 VF)
     */
    public void setVf(int vf) {
        this.vf = vf;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((artist == null) ? 0 : artist.hashCode());
        result = prime * result + ((date == null) ? 0 : date.hashCode());
        result = prime * result + ((lv == null) ? 0 : lv.hashCode());
        result = prime * result + ((rank == null) ? 0 : rank.hashCode());
        return prime * result + ((title == null) ? 0 : title.hashCode());
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof MusicInfo)) {
            return false;
        }
        MusicInfo other = (MusicInfo) obj;
        if (artist == null) {
            if (other.artist != null) {
                return false;
            }
        } else if (!artist.equals(other.artist)) {
            return false;
        }
        if (date == null) {
            if (other.date != null) {
                return false;
            }
        } else if (!date.equals(other.date)) {
            return false;
        }
        if (lv == null) {
            if (other.lv != null) {
                return false;
            }
        } else if (!lv.equals(other.lv)) {
            return false;
        }
        if (rank != other.rank) {
            return false;
        }
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
