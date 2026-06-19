package com.sdvxhelper.network.ipc;

import jakarta.json.bind.annotation.JsonbProperty;

/**
 * Represents a Discord Rich Presence activity payload.
 *
 * <p>
 * Serialises to:
 * </p>
 * 
 * <pre>{@code
 * {
 *   "details": "<first line>",
 *   "state":   "<second line>",
 *   "timestamps": { "start": <epoch> },
 *   "assets": { "large_image": "...", "large_text": "..." }
 * }
 * }</pre>
 *
 * @author Throdax
 * @since 2.0.0
 */
public class IpcActivity {

    /**
     * Activity name displayed as the "Playing X" title in Discord. When set, this
     * overrides the application name registered in the Discord Developer Portal for
     * this specific activity update.
     */
    @JsonbProperty("name")
    private String name;

    /**
     * First line of text shown below the application name in the presence widget.
     */
    @JsonbProperty("details")
    private String details;

    /** Second line of text (e.g. Volforce display). */
    @JsonbProperty("state")
    private String state;

    /** Elapsed-time timestamps block. */
    @JsonbProperty("timestamps")
    private IpcTimestamps timestamps;

    /** Image asset block. */
    @JsonbProperty("assets")
    private IpcAssets assets;

    /** No-argument constructor required by JSON-B. */
    public IpcActivity() {
    }

    /**
     * Constructs a fully populated activity payload.
     *
     * @param name
     *            the activity name shown as "Playing X" in Discord; overrides the
     *            Discord Developer Portal application name for this update
     * @param details
     *            the first line of text shown under the application name
     * @param state
     *            the second line of text (e.g. Volforce display)
     * @param timestamps
     *            the elapsed-time {@link IpcTimestamps} block
     * @param assets
     *            the image {@link IpcAssets} block
     */
    public IpcActivity(String name, String details, String state, IpcTimestamps timestamps, IpcAssets assets) {
        this.name = name;
        this.details = details;
        this.state = state;
        this.timestamps = timestamps;
        this.assets = assets;
    }

    /**
     * Returns the activity name shown as "Playing X" in Discord.
     *
     * @return the activity name
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the activity name shown as "Playing X" in Discord.
     *
     * @param name
     *            the activity name to set
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Returns the first line of text shown under the application name.
     *
     * @return the details string
     */
    public String getDetails() {
        return details;
    }

    /**
     * Sets the first line of text shown under the application name.
     *
     * @param details
     *            the details string to set
     */
    public void setDetails(String details) {
        this.details = details;
    }

    /**
     * Returns the second line of text in the presence widget.
     *
     * @return the state string
     */
    public String getState() {
        return state;
    }

    /**
     * Sets the second line of text in the presence widget.
     *
     * @param state
     *            the state string to set
     */
    public void setState(String state) {
        this.state = state;
    }

    /**
     * Returns the elapsed-time timestamps block.
     *
     * @return the {@link IpcTimestamps} block
     */
    public IpcTimestamps getTimestamps() {
        return timestamps;
    }

    /**
     * Sets the elapsed-time timestamps block.
     *
     * @param timestamps
     *            the {@link IpcTimestamps} block to set
     */
    public void setTimestamps(IpcTimestamps timestamps) {
        this.timestamps = timestamps;
    }

    /**
     * Returns the image asset block.
     *
     * @return the {@link IpcAssets} block
     */
    public IpcAssets getAssets() {
        return assets;
    }

    /**
     * Sets the image asset block.
     *
     * @param assets
     *            the {@link IpcAssets} block to set
     */
    public void setAssets(IpcAssets assets) {
        this.assets = assets;
    }
}
