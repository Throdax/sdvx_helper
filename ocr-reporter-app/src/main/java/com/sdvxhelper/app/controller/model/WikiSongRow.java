package com.sdvxhelper.app.controller.model;

/**
 * One row in the BemaniWiki song table ({@code tblMusic}).
 */
public class WikiSongRow {
    private final String title;
    private final String artist;
    private final String bpm;
    private final String nov;
    private final String adv;
    private final String exh;
    private final String append;

    /**
     * @param title
     *            song title
     * @param artist
     *            artist name
     * @param bpm
     *            BPM string
     * @param nov
     *            novice level
     * @param adv
     *            advanced level
     * @param exh
     *            exhaust level
     * @param append
     *            maximum/append level (may be {@code null})
     */
    public WikiSongRow(String title, String artist, String bpm, String nov, String adv, String exh, String append) {
        this.title = title;
        this.artist = artist;
        this.bpm = bpm;
        this.nov = nov;
        this.adv = adv;
        this.exh = exh;
        this.append = append;
    }

    /** @return the song title */
    public String getTitle() {
        return title;
    }
    /** @return the artist name */
    public String getArtist() {
        return artist;
    }
    /** @return the BPM string */
    public String getBpm() {
        return bpm;
    }
    /** @return novice chart level */
    public String getNov() {
        return nov;
    }
    /** @return advanced chart level */
    public String getAdv() {
        return adv;
    }
    /** @return exhaust chart level */
    public String getExh() {
        return exh;
    }
    /** @return maximum/append chart level */
    public String getAppend() {
        return append;
    }
}