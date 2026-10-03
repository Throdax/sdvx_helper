package com.sdvxhelper.model;

import java.time.LocalDateTime;

/**
 * Builder for {@link MusicInfo}.
 *
 * <p>
 * All fields mirror those of {@link MusicInfo}. Only {@code title} is
 * mandatory; every other field has a sensible default so that call sites only
 * need to set what they actually know.
 * </p>
 *
 * <p>
 * Usage:
 * </p>
 * 
 * <pre>{@code
 * MusicInfo info = new MusicInfoBuilder("冥").artist("Camellia").bpm("200").difficulty("mxm").lv("20")
 * 		.bestScore(9_900_000).bestLamp("puc").date("2024-01-01").build();
 * }</pre>
 *
 * <p>
 * The {@code date} setter is overloaded: pass a {@link LocalDateTime} directly
 * or a formatted string ({@code "yyyy-MM-dd HH:mm"} or bare
 * {@code "yyyy-MM-dd"}) and it will be parsed via
 * {@link MusicInfo#unmarshalDate(String)}.
 * </p>
 *
 * @author Throdax
 * @since 2.0.0
 */
public class MusicInfoBuilder {

    // Mandatory
    private final String title;

    // Optional – defaults
    private String artist = "";
    private String bpm = "";
    private String difficulty = "";
    private String lv = "??";
    private int bestScore = 0;
    private String bestLamp = "";
    private LocalDateTime date = null;
    private String sTier = "";
    private String pTier = "";

    /**
     * Constructs a builder with the mandatory song title.
     *
     * @param title
     *            the song title (must not be {@code null})
     */
    public MusicInfoBuilder(String title) {
        if (title == null) {
            throw new IllegalArgumentException("title must not be null");
        }
        this.title = title;
    }

    /**
     * Sets the artist name.
     *
     * @param artist
     *            the artist name
     * @return this builder
     */
    public MusicInfoBuilder artist(String artist) {
        this.artist = artist != null ? artist : "";
        return this;
    }

    /**
     * Sets the BPM string (may be a range, e.g. {@code "120-200"}).
     *
     * @param bpm
     *            the BPM string
     * @return this builder
     */
    public MusicInfoBuilder bpm(String bpm) {
        this.bpm = bpm != null ? bpm : "";
        return this;
    }

    /**
     * Sets the chart difficulty string (e.g. {@code "exh"}, {@code "mxm"}).
     *
     * @param difficulty
     *            the chart difficulty string
     * @return this builder
     */
    public MusicInfoBuilder difficulty(String difficulty) {
        this.difficulty = difficulty != null ? difficulty : "";
        return this;
    }

    /**
     * Sets the chart level string (integer or {@code "??"} for unknown).
     *
     * @param lv
     *            the chart level string
     * @return this builder
     */
    public MusicInfoBuilder lv(String lv) {
        this.lv = lv != null ? lv : "??";
        return this;
    }

    /**
     * Sets the personal-best score for this chart.
     *
     * @param bestScore
     *            the personal-best score
     * @return this builder
     */
    public MusicInfoBuilder bestScore(int bestScore) {
        this.bestScore = bestScore;
        return this;
    }

    /**
     * Sets the clear lamp for the personal-best play.
     *
     * @param bestLamp
     *            the personal-best clear lamp
     * @return this builder
     */
    public MusicInfoBuilder bestLamp(String bestLamp) {
        this.bestLamp = bestLamp != null ? bestLamp : "";
        return this;
    }

    /**
     * Sets the date of the personal-best play from a {@link LocalDateTime}.
     *
     * @param date
     *            the personal-best date
     * @return this builder
     */
    public MusicInfoBuilder date(LocalDateTime date) {
        this.date = date;
        return this;
    }

    /**
     * Sets the date of the personal-best play from a formatted string
     * ({@code "yyyy-MM-dd HH:mm"} or bare {@code "yyyy-MM-dd"}). Unparseable values
     * are silently ignored (date remains {@code null}).
     *
     * @param dateString
     *            the date string to parse
     * @return this builder
     */
    public MusicInfoBuilder date(String dateString) {
        this.date = MusicInfo.unmarshalDate(dateString);
        return this;
    }

    /**
     * Sets the grade-S tier classification (e.g. {@code "SSS"}, {@code "SS"},
     * {@code "S"}).
     *
     * @param sTier
     *            the grade-S tier string
     * @return this builder
     */
    public MusicInfoBuilder sTier(String sTier) {
        this.sTier = sTier != null ? sTier : "";
        return this;
    }

    /**
     * Sets the perfect-UC tier classification.
     *
     * @param pTier
     *            the perfect-UC tier string
     * @return this builder
     */
    public MusicInfoBuilder pTier(String pTier) {
        this.pTier = pTier != null ? pTier : "";
        return this;
    }

    /**
     * Builds and returns a new {@link MusicInfo} instance.
     *
     * @return a new {@link MusicInfo} populated with this builder's values
     */
    public MusicInfo build() {
        MusicInfo info = new MusicInfo();
        info.setTitle(title);
        info.setArtist(artist);
        info.setBpm(bpm);
        info.setDifficulty(difficulty);
        info.setLv(lv);
        info.setBestScore(bestScore);
        info.setBestLamp(bestLamp);
        info.setDate(date);
        info.setSTier(sTier);
        info.setPTier(pTier);
        return info;
    }
}