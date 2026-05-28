package com.sdvxhelper.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Stores the configuration for a single Discord webhook destination.
 *
 * <p>
 * Each instance covers one webhook endpoint including its URL, send flags, and
 * per-level / per-lamp filter maps. A player may configure multiple webhooks
 * (e.g. one public channel, one personal DM). The global player name is kept
 * separately in the main settings.
 * </p>
 *
 * <p>
 * Use {@link WebhookConfigBuilder} to construct instances. The no-argument
 * constructor is reserved for use by {@link WebhookConfigBuilder}.
 * </p>
 *
 * @author Throdax
 * @since 2.0.0
 */
public class WebhookConfig {

    private String name;
    private String url;
    private boolean sendScreenshot;
    private boolean sendPlaylist;

    /**
     * Ordered map of level keys ("1"–"20") to their enabled state. Serialised as
     * {@code [{"1": true}, {"2": true}, …]}.
     */
    private LinkedHashMap<String, Boolean> enabledLevels;

    /**
     * Ordered map of lamp keys ("PUC", "UC", "MAXXIVE", "HARD", "CLEAR", "FAILED")
     * to their enabled state. Serialised as {@code [{"PUC": true}, …]}.
     */
    private LinkedHashMap<String, Boolean> enabledLamp;

    /**
     * No-argument constructor. Reserved for use by {@link WebhookConfigBuilder}.
     * Application code must use the builder.
     */
    public WebhookConfig() {
        enabledLevels = new LinkedHashMap<>();
        enabledLamp = new LinkedHashMap<>();
    }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    /**
     * Returns the user-visible label for this webhook.
     *
     * @return name
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the Discord webhook URL.
     *
     * @return URL string
     */
    public String getUrl() {
        return url;
    }

    /**
     * Returns whether a screenshot should be attached to result notifications.
     *
     * @return true if screenshot should be sent
     */
    public boolean isSendScreenshot() {
        return sendScreenshot;
    }

    /**
     * Returns whether the session playlist should be posted to this webhook on app
     * exit.
     *
     * @return true if playlist should be sent
     */
    public boolean isSendPlaylist() {
        return sendPlaylist;
    }

    /**
     * Returns an unmodifiable view of the level-filter map. Keys are string level
     * numbers ("1"–"20"), values are the enabled state.
     *
     * @return unmodifiable level-filter map
     */
    public Map<String, Boolean> getEnabledLevels() {
        return Collections.unmodifiableMap(enabledLevels);
    }

    /**
     * Returns an unmodifiable view of the lamp-filter map. Keys are lamp names
     * ("PUC", "UC", "MAXXIVE", "HARD", "CLEAR", "FAILED"), values are the enabled
     * state.
     *
     * @return unmodifiable lamp-filter map
     */
    public Map<String, Boolean> getEnabledLamp() {
        return Collections.unmodifiableMap(enabledLamp);
    }

    // -------------------------------------------------------------------------
    // Setters (plain value fields — used by WebhookConfigBuilder)
    // -------------------------------------------------------------------------

    /**
     * Sets the webhook name.
     *
     * @param name
     *            display label
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Sets the Discord webhook URL.
     *
     * @param url
     *            full webhook URL
     */
    public void setUrl(String url) {
        this.url = url;
    }

    /**
     * Sets whether a screenshot should be attached to result notifications.
     *
     * @param sendScreenshot
     *            true to attach screenshots
     */
    public void setSendScreenshot(boolean sendScreenshot) {
        this.sendScreenshot = sendScreenshot;
    }

    /**
     * Sets whether the session playlist should be posted on app exit.
     *
     * @param sendPlaylist
     *            true to send playlist
     */
    public void setSendPlaylist(boolean sendPlaylist) {
        this.sendPlaylist = sendPlaylist;
    }

    // -------------------------------------------------------------------------
    // Collection mutation (no setters; use add methods per POJO rules)
    // -------------------------------------------------------------------------

    /**
     * Adds or updates the enabled state for the given level key.
     *
     * @param levelKey
     *            string level number, e.g. {@code "15"}
     * @param enabled
     *            true if results at this level should trigger the webhook
     */
    public void addEnabledLevel(String levelKey, boolean enabled) {
        enabledLevels.put(levelKey, enabled);
    }

    /**
     * Bulk-adds level filter entries from the given map.
     *
     * @param levels
     *            map of level keys to enabled states
     */
    public void addEnabledLevels(Map<String, Boolean> levels) {
        enabledLevels.putAll(levels);
    }

    /**
     * Adds or updates the enabled state for the given lamp key.
     *
     * @param lampKey
     *            lamp name, one of {@code PUC}, {@code UC}, {@code MAXXIVE},
     *            {@code HARD}, {@code CLEAR}, {@code FAILED}
     * @param enabled
     *            true if results with this lamp should trigger the webhook
     */
    public void addEnabledLamp(String lampKey, boolean enabled) {
        enabledLamp.put(lampKey, enabled);
    }

    /**
     * Bulk-adds lamp filter entries from the given map.
     *
     * @param lamps
     *            map of lamp keys to enabled states
     */
    public void addEnabledLamps(Map<String, Boolean> lamps) {
        enabledLamp.putAll(lamps);
    }

    // -------------------------------------------------------------------------
    // Filter query helpers
    // -------------------------------------------------------------------------

    /**
     * Returns {@code true} if a result at the given numeric level should be sent to
     * this webhook.
     *
     * <p>
     * When the level is unknown ({@code level < 1}) or not present in the filter
     * map, this method defaults to {@code true} (allow).
     * </p>
     *
     * @param level
     *            chart level (1–20), or {@code -1} if unknown
     * @return true if the level passes the filter
     */
    public boolean isLevelEnabled(int level) {
        if (level < 1) {
            return true;
        }
        return Boolean.TRUE.equals(enabledLevels.getOrDefault(String.valueOf(level), Boolean.TRUE));
    }

    /**
     * Returns {@code true} if a result with the given lamp key should be sent to
     * this webhook.
     *
     * <p>
     * When the lamp key is absent from the filter map, this method defaults to
     * {@code false} (block), mirroring Python's behaviour.
     * </p>
     *
     * @param lampKey
     *            normalised lamp key, e.g. {@code "CLEAR"}
     * @return true if the lamp passes the filter
     */
    public boolean isLampEnabled(String lampKey) {
        return Boolean.TRUE.equals(enabledLamp.getOrDefault(lampKey, Boolean.FALSE));
    }

    // -------------------------------------------------------------------------
    // Object overrides
    // -------------------------------------------------------------------------

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other instanceof WebhookConfig that) {
            return sendScreenshot == that.sendScreenshot && sendPlaylist == that.sendPlaylist
                    && Objects.equals(name, that.name) && Objects.equals(url, that.url)
                    && Objects.equals(enabledLevels, that.enabledLevels)
                    && Objects.equals(enabledLamp, that.enabledLamp);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, url, sendScreenshot, sendPlaylist, enabledLevels, enabledLamp);
    }

    @Override
    public String toString() {
        return "WebhookConfig{" + "name='" + name + '\'' + ", url="
                + (url != null && !url.isBlank() ? "[configured]" : "null") + ", sendScreenshot=" + sendScreenshot
                + ", sendPlaylist=" + sendPlaylist + ", enabledLevels=" + enabledLevels.size() + " entries"
                + ", enabledLamp=" + enabledLamp.size() + " entries" + '}';
    }
}
