package com.sdvxhelper.network.ipc;

import jakarta.json.bind.annotation.JsonbProperty;

/**
 * Represents the image-asset block inside a Discord Rich Presence
 * {@link IpcActivity}.
 *
 * <p>
 * Serialises to:
 * </p>
 * 
 * <pre>{@code {"large_image":"<url-or-key>","large_text":"<tooltip>"}}</pre>
 *
 * @author Throdax
 * @since 2.0.0
 */
public class IpcAssets {

    /** Key or URL of the large image displayed in the presence widget. */
    @JsonbProperty("large_image")
    private String largeImage;

    /** Tooltip text shown when hovering over the large image. */
    @JsonbProperty("large_text")
    private String largeText;

    /** No-argument constructor required by JSON-B. */
    public IpcAssets() {
    }

    /**
     * Constructs an assets block with the given image key and tooltip.
     *
     * @param largeImage
     *            the key or URL of the large image asset
     * @param largeText
     *            the tooltip text for the large image
     */
    public IpcAssets(String largeImage, String largeText) {
        this.largeImage = largeImage;
        this.largeText = largeText;
    }

    /**
     * Returns the key or URL of the large image asset.
     *
     * @return the large image key or URL
     */
    public String getLargeImage() {
        return largeImage;
    }

    /**
     * Sets the key or URL of the large image asset.
     *
     * @param largeImage
     *            the large image key or URL to set
     */
    public void setLargeImage(String largeImage) {
        this.largeImage = largeImage;
    }

    /**
     * Returns the tooltip text shown when hovering over the large image.
     *
     * @return the large image tooltip text
     */
    public String getLargeText() {
        return largeText;
    }

    /**
     * Sets the tooltip text shown when hovering over the large image.
     *
     * @param largeText
     *            the large image tooltip text to set
     */
    public void setLargeText(String largeText) {
        this.largeText = largeText;
    }
}
