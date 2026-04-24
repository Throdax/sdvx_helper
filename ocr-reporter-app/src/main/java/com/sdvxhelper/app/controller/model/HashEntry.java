package com.sdvxhelper.app.controller.model;

/**
 * Row model for the hash database view.
 */
public class HashEntry {
    private final String title;
    private final String hash;

    /**
     * @param title
     *            song title
     * @param hash
     *            jacket hash value
     */
    public HashEntry(String title, String hash) {
        this.title = title;
        this.hash = hash;
    }

    /** @return the song title */
    public String getTitle() {
        return title;
    }
    /** @return the jacket hash */
    public String getHash() {
        return hash;
    }
}