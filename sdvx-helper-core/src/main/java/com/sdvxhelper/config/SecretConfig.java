package com.sdvxhelper.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provides access to all eight sensitive configuration values from
 * {@code params_secret.py}.
 *
 * <p>
 * Resolution order for every secret (first non-blank value wins):
 * </p>
 * <ol>
 * <li>JVM system property ({@code -DENV_KEY=value}) using the
 * {@code UPPER_SNAKE_CASE} key</li>
 * <li>OS environment variable using the same {@code UPPER_SNAKE_CASE} key</li>
 * <li>{@code secrets.properties} file in the JVM working directory (the
 * installation root next to the EXE), looked up with the dot-notation key</li>
 * <li>Build-time baked value from {@link GeneratedSecrets} (XOR-encoded byte
 * arrays — secrets never appear as plain text inside the JAR)</li>
 * </ol>
 *
 * <p>
 * If none of the above sources provides a value an empty string is returned and
 * the dependent feature is silently disabled.
 * </p>
 *
 * @author Throdax
 * @since 2.0.0
 */
public final class SecretConfig {

    private static final Logger log = LoggerFactory.getLogger(SecretConfig.class);

    private static final String PROP_FILE = "secrets.properties";

    private final String maya2Key;
    private final String maya2Url;
    private final String discordClientId;
    private final String webhookRegUrl;
    private final String webhookUnknownUrl;
    private final String webhookUnknownExhUrl;
    private final String webhookUnknownAdvUrl;
    private final String webhookUnknownNovUrl;

    /**
     * Loads all secrets from environment variables / system properties /
     * {@code secrets.properties} / build-time baked values, in that priority order.
     */
    public SecretConfig() {
        this.maya2Key = resolve("MAYA2_KEY", "maya2.key", GeneratedSecrets::getMaya2Key);
        this.maya2Url = resolve("MAYA2_URL", "maya2.url", GeneratedSecrets::getMaya2Url);
        this.discordClientId = resolve("DISCORD_CLIENT_ID", "discord.client.id", GeneratedSecrets::getDiscordClientId);
        this.webhookRegUrl = resolve("WEBHOOK_REG_URL", "webhook.reg.url", GeneratedSecrets::getWebhookRegUrl);
        this.webhookUnknownUrl = resolve("WEBHOOK_UNKNOWN_URL", "webhook.unknown.url",
                GeneratedSecrets::getWebhookUnknownUrl);
        this.webhookUnknownExhUrl = resolve("WEBHOOK_UNKNOWN_EXH_URL", "webhook.unknown.exh.url",
                GeneratedSecrets::getWebhookUnknownExhUrl);
        this.webhookUnknownAdvUrl = resolve("WEBHOOK_UNKNOWN_ADV_URL", "webhook.unknown.adv.url",
                GeneratedSecrets::getWebhookUnknownAdvUrl);
        this.webhookUnknownNovUrl = resolve("WEBHOOK_UNKNOWN_NOV_URL", "webhook.unknown.nov.url",
                GeneratedSecrets::getWebhookUnknownNovUrl);
    }

    // -------------------------------------------------------------------------
    // Public accessors
    // -------------------------------------------------------------------------

    /**
     * Returns the Maya2 HMAC signing key ({@code maya2_key} in Python).
     *
     * @return key string, or empty string if not configured
     */
    public String getMaya2Key() {
        return maya2Key;
    }

    /**
     * Returns the Maya2 private server URL ({@code maya2_url} in Python).
     *
     * @return URL string, or empty string if not configured
     */
    public String getMaya2Url() {
        return maya2Url;
    }

    /**
     * Returns the Discord Rich Presence application client ID
     * ({@code discord_presence_client_id} in Python).
     *
     * @return client ID string, or empty string if not configured
     */
    public String getDiscordClientId() {
        return discordClientId;
    }

    /**
     * Returns the OCR-reporter registration webhook URL ({@code url_webhook_reg} in
     * Python).
     *
     * @return webhook URL, or empty string if not configured
     */
    public String getWebhookRegUrl() {
        return webhookRegUrl;
    }

    /**
     * Returns the default/APPEND unknown-title report webhook URL
     * ({@code url_webhook_unknown} in Python).
     *
     * @return webhook URL, or empty string if not configured
     */
    public String getWebhookUnknownUrl() {
        return webhookUnknownUrl;
    }

    /**
     * Returns the EXH-difficulty unknown-title report webhook URL
     * ({@code url_webhook_unknown_exh} in Python).
     *
     * @return webhook URL, or empty string if not configured
     */
    public String getWebhookUnknownExhUrl() {
        return webhookUnknownExhUrl;
    }

    /**
     * Returns the ADV-difficulty unknown-title report webhook URL
     * ({@code url_webhook_unknown_adv} in Python).
     *
     * @return webhook URL, or empty string if not configured
     */
    public String getWebhookUnknownAdvUrl() {
        return webhookUnknownAdvUrl;
    }

    /**
     * Returns the NOV-difficulty unknown-title report webhook URL
     * ({@code url_webhook_unknown_nov} in Python).
     *
     * @return webhook URL, or empty string if not configured
     */
    public String getWebhookUnknownNovUrl() {
        return webhookUnknownNovUrl;
    }

    // -------------------------------------------------------------------------
    // Private resolution logic
    // -------------------------------------------------------------------------

    /**
     * Resolves a secret value using the four-step priority chain.
     *
     * @param envKey
     *            {@code UPPER_SNAKE_CASE} key used for the JVM system property and
     *            OS environment variable lookups
     * @param propKey
     *            dot-notation key used for the {@code secrets.properties} file
     *            lookup (e.g. {@code "maya2.key"})
     * @param baked
     *            supplier that returns the build-time XOR-decoded value from
     *            {@link GeneratedSecrets}; called only when all other sources are
     *            absent or blank
     * @return resolved secret value, or an empty string if nothing is configured
     */
    private static String resolve(String envKey, String propKey, Supplier<String> baked) {
        // 1. JVM system property (-DENV_KEY=value)
        String value = System.getProperty(envKey);
        if (value != null && !value.isBlank()) {
            return value.trim();
        }

        // 2. OS environment variable
        value = System.getenv(envKey);
        if (value != null && !value.isBlank()) {
            return value.trim();
        }

        // 3. secrets.properties file in the working directory (installation root)
        File propFile = new File(PROP_FILE);
        if (propFile.exists()) {
            Properties props = new Properties();
            try (FileInputStream fis = new FileInputStream(propFile)) {
                props.load(fis);
                value = props.getProperty(propKey);
                if (value != null && !value.isBlank()) {
                    return value.trim();
                }
            } catch (IOException e) {
                log.warn("SecretConfig: failed to read {}", PROP_FILE, e);
            }
        }

        // 4. Build-time baked value (XOR-encoded in GeneratedSecrets)
        String bakedValue = baked.get();
        return bakedValue != null ? bakedValue : "";
    }
}
