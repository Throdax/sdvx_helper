package com.sdvxhelper.network.ipc;

import jakarta.json.bind.annotation.JsonbProperty;

/**
 * Represents the elapsed-time timestamps block inside a Discord Rich Presence
 * {@link IpcActivity}.
 *
 * <p>
 * Serialises to:
 * </p>
 * 
 * <pre>{@code {"start":<unix-epoch-seconds>}}</pre>
 *
 * @author Throdax
 * @since 2.0.0
 */
public class IpcTimestamps {

    /** Unix epoch seconds at which the current activity started. */
    @JsonbProperty("start")
    private long start;

    /** No-argument constructor required by JSON-B. */
    public IpcTimestamps() {
    }

    /**
     * Constructs a timestamps block with the given start time.
     *
     * @param start
     *            Unix epoch seconds for the start of the current activity
     */
    public IpcTimestamps(long start) {
        this.start = start;
    }

    /**
     * Returns the Unix epoch seconds at which the current activity started.
     *
     * @return the activity start time in epoch seconds
     */
    public long getStart() {
        return start;
    }

    /**
     * Sets the Unix epoch seconds at which the current activity started.
     *
     * @param start
     *            the activity start time in epoch seconds to set
     */
    public void setStart(long start) {
        this.start = start;
    }
}
