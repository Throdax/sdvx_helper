package com.sdvxhelper.i18n;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Thin wrapper around {@link ResourceBundle} for internationalised message
 * lookup.
 *
 * <p>
 * Replaces the Python {@code poor_man_resource_bundle.py} custom
 * implementation. Message bundles are stored under
 * {@code src/main/resources/i18n/messages_en.properties} (and {@code _ja}) and
 * follow the standard Java {@link ResourceBundle} naming convention.
 * </p>
 *
 * <p>
 * Usage:
 * </p>
 * 
 * <pre>{@code
 * MessageService ms = new MessageService(Locale.ENGLISH);
 * String label = ms.get("button.start");
 * String formatted = ms.get("status.vf", 36.9);
 * }</pre>
 *
 * @author Throdax
 * @since 2.0.0
 */
public class MessageService {

    private static final Logger log = LoggerFactory.getLogger(MessageService.class);
    private static final String BUNDLE_BASE = "i18n/messages";

    private final ResourceBundle bundle;
    private final Locale locale;

    /**
     * Constructs a {@code MessageService} for the given locale. Falls back to
     * {@link Locale#ENGLISH} if the bundle for the requested locale cannot be
     * found.
     *
     * @param locale
     *            desired locale (e.g. {@code Locale.JAPANESE})
     */
    public MessageService(Locale locale) {
        this.locale = locale;
        ResourceBundle loaded;
        try {
            loaded = ResourceBundle.getBundle(BUNDLE_BASE, locale);
        } catch (MissingResourceException e) {
            log.warn("Message bundle not found for locale '{}', falling back to ENGLISH", locale, e);
            loaded = ResourceBundle.getBundle(BUNDLE_BASE, Locale.ENGLISH);
        }
        this.bundle = loaded;
    }

    /**
     * Returns the message string for the given key, with no substitutions.
     *
     * @param key
     *            message key
     * @return localised message string
     * @throws MissingResourceException
     *             if the key is not found in the bundle
     */
    public String get(String key) {
        try {
            return bundle.getString(key);
        } catch (MissingResourceException e) {
            log.error("Missing message key '{}' for locale '{}'", key, locale);
            return "!" + key + "!";
        }
    }

    /**
     * Returns the message string for the given key, substituting arguments using
     * {@link MessageFormat}.
     *
     * @param key
     *            message key
     * @param args
     *            substitution arguments (used with {@code {0}}, {@code {1}}, …
     *            placeholders)
     * @return formatted, localised message string
     */
    public String get(String key, Object... args) {
        String pattern = get(key);
        try {
            return MessageFormat.format(pattern, args);
        } catch (IllegalArgumentException e) {
            log.warn("Failed to format message '{}' with args", key, e);
            return pattern;
        }
    }

    /**
     * Returns the active locale.
     *
     * @return locale
     */
    public Locale getLocale() {
        return locale;
    }
}
