package com.sdvxhelper.model;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builder for {@link WebhookConfig}.
 *
 * <p>
 * Required because {@link WebhookConfig} has more than five configurable
 * fields. Defaults all 20 levels and all 6 lamps to {@code true} (enabled)
 * unless overridden.
 * </p>
 *
 * <pre>{@code
 * WebhookConfig cfg = new WebhookConfigBuilder().name("SDVX").url("https://discord.com/api/webhooks/...")
 * 		.sendScreenshot(true).sendPlaylist(true).build();
 * }</pre>
 *
 * @author Throdax
 * @since 2.0.0
 */
public class WebhookConfigBuilder {

    /** Ordered lamp key names used when building default lamp-filter entries. */
    public static final List<String> LAMP_KEYS = Arrays.asList("PUC", "UC", "MAXXIVE", "HARD", "CLEAR", "SKILL CLEAR",
            "FAILED");

    private String name = "";
    private String url = "";
    private boolean sendScreenshot = false;
    private boolean sendPlaylist = false;
    private LinkedHashMap<String, Boolean> enabledLevels;
    private LinkedHashMap<String, Boolean> enabledLamp;

    /**
     * Constructs a builder with all 20 levels and all 6 lamps pre-set to
     * {@code true}.
     */
    public WebhookConfigBuilder() {
        enabledLevels = buildDefaultLevels(true);
        enabledLamp = buildDefaultLamp(true);
    }

    // -------------------------------------------------------------------------
    // Value-field setters (return this for chaining)
    // -------------------------------------------------------------------------

    /**
     * Sets the webhook display name.
     *
     * @param name
     *            label shown in the UI list
     * @return this builder
     */
    public WebhookConfigBuilder name(String name) {
        this.name = name;
        return this;
    }

    /**
     * Sets the full Discord webhook URL.
     *
     * @param url
     *            webhook URL
     * @return this builder
     */
    public WebhookConfigBuilder url(String url) {
        this.url = url;
        return this;
    }

    /**
     * Controls whether a result screenshot is attached to webhook messages.
     *
     * @param sendScreenshot
     *            true to attach screenshots
     * @return this builder
     */
    public WebhookConfigBuilder sendScreenshot(boolean sendScreenshot) {
        this.sendScreenshot = sendScreenshot;
        return this;
    }

    /**
     * Controls whether the session playlist is posted on app exit.
     *
     * @param sendPlaylist
     *            true to send the playlist
     * @return this builder
     */
    public WebhookConfigBuilder sendPlaylist(boolean sendPlaylist) {
        this.sendPlaylist = sendPlaylist;
        return this;
    }

    // -------------------------------------------------------------------------
    // Filter-map population
    // -------------------------------------------------------------------------

    /**
     * Sets the enabled state for a single level.
     *
     * @param levelKey
     *            string level number, e.g. {@code "15"}
     * @param enabled
     *            true to include results at this level
     * @return this builder
     */
    public WebhookConfigBuilder enabledLevel(String levelKey, boolean enabled) {
        enabledLevels.put(levelKey, enabled);
        return this;
    }

    /**
     * Replaces the entire level-filter map.
     *
     * @param levels
     *            map of level keys to enabled states
     * @return this builder
     */
    public WebhookConfigBuilder enabledLevels(Map<String, Boolean> levels) {
        enabledLevels = new LinkedHashMap<>(levels);
        return this;
    }

    /**
     * Sets the enabled state for a single lamp key.
     *
     * @param lampKey
     *            one of {@code PUC}, {@code UC}, {@code MAXXIVE}, {@code HARD},
     *            {@code CLEAR}, {@code FAILED}
     * @param enabled
     *            true to include results with this lamp
     * @return this builder
     */
    public WebhookConfigBuilder enabledLamp(String lampKey, boolean enabled) {
        enabledLamp.put(lampKey, enabled);
        return this;
    }

    /**
     * Replaces the entire lamp-filter map.
     *
     * @param lamps
     *            map of lamp keys to enabled states
     * @return this builder
     */
    public WebhookConfigBuilder enabledLamps(Map<String, Boolean> lamps) {
        enabledLamp = new LinkedHashMap<>(lamps);
        return this;
    }

    // -------------------------------------------------------------------------
    // Build
    // -------------------------------------------------------------------------

    /**
     * Constructs and returns the {@link WebhookConfig}.
     *
     * @return new webhook configuration
     */
    public WebhookConfig build() {
        WebhookConfig config = new WebhookConfig();
        config.setName(name);
        config.setUrl(url);
        config.setSendScreenshot(sendScreenshot);
        config.setSendPlaylist(sendPlaylist);
        config.addEnabledLevels(enabledLevels);
        config.addEnabledLamps(enabledLamp);
        return config;
    }

    // -------------------------------------------------------------------------
    // Static factory helpers
    // -------------------------------------------------------------------------

    /**
     * Builds a default level-filter map with all 20 levels set to
     * {@code defaultValue}.
     *
     * @param defaultValue
     *            initial state for every level
     * @return ordered map with keys "1"–"20"
     */
    public static LinkedHashMap<String, Boolean> buildDefaultLevels(boolean defaultValue) {
        LinkedHashMap<String, Boolean> map = new LinkedHashMap<>();
        for (int lv = 1; lv <= 20; lv++) {
            map.put(String.valueOf(lv), defaultValue);
        }
        return map;
    }

    /**
     * Builds a default lamp-filter map with all 6 lamps set to
     * {@code defaultValue}.
     *
     * @param defaultValue
     *            initial state for every lamp
     * @return ordered map with keys PUC, UC, MAXXIVE, HARD, CLEAR, SKILL CLEAR,
     *         FAILED
     */
    public static LinkedHashMap<String, Boolean> buildDefaultLamp(boolean defaultValue) {
        LinkedHashMap<String, Boolean> map = new LinkedHashMap<>();
        for (String key : LAMP_KEYS) {
            map.put(key, defaultValue);
        }
        return map;
    }
}
