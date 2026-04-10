package com.sdvxhelper.i18n;

import com.sdvxhelper.repository.SettingsRepository;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * Singleton that manages the application locale and provides locale-change notifications.
 *
 * <p>Available locales are discovered at startup by probing {@link ResourceBundle#getBundle}
 * for each candidate code.  This approach is fat-JAR-safe and does not require
 * classpath directory scanning.</p>
 *
 * <p>When {@link #setLocale(String)} is called the new locale is persisted to
 * {@code settings.json} via the {@link SettingsRepository} and all JavaFX listeners
 * attached to {@link #localeProperty()} are notified so that each open
 * {@code *App} can rebuild its scene.</p>
 *
 * @author Throdax
 * @since 2.0.0
 */
public final class LocaleManager {

    private static final Logger log = LoggerFactory.getLogger(LocaleManager.class);

    private static final String BUNDLE_BASE = "i18n/messages";
    private static final List<String> CANDIDATE_LOCALES = Arrays.asList("en", "ja");

    private static volatile LocaleManager instance;

    private final ObservableList<String> availableLocaleCodes = FXCollections.observableArrayList();
    private final ObjectProperty<Locale> localeProperty = new SimpleObjectProperty<>(Locale.ENGLISH);

    private SettingsRepository settingsRepository;
    private Map<String, Object> settings;
    private boolean initialized = false;

    private LocaleManager() {
        discoverLocales();
    }

    /**
     * Returns the singleton instance.
     *
     * @return shared {@code LocaleManager} instance
     */
    public static LocaleManager getInstance() {
        if (instance == null) {
            synchronized (LocaleManager.class) {
                if (instance == null) {
                    instance = new LocaleManager();
                }
            }
        }
        return instance;
    }

    /**
     * Initialises the manager with a settings repository, loading the persisted
     * locale preference.  This method is idempotent; subsequent calls have no effect.
     *
     * @param settingsRepo repository used to load and persist the locale setting
     */
    public void init(SettingsRepository settingsRepo) {
        if (initialized) {
            return;
        }
        initialized = true;
        this.settingsRepository = settingsRepo;
        this.settings = settingsRepo.load();
        String savedCode = (String) settings.getOrDefault("ui_language", "en");
        applyLocaleCode(savedCode);
        log.info("LocaleManager initialised with locale '{}'", getCurrentCode());
    }

    /**
     * Returns the list of locale codes for which a resource bundle exists.
     *
     * @return observable list of locale codes (e.g. {@code ["en", "ja"]})
     */
    public ObservableList<String> getAvailableLocaleCodes() {
        return availableLocaleCodes;
    }

    /**
     * Returns the locale property that fires change events when the locale changes.
     * JavaFX {@code *App} classes should observe this to trigger scene rebuilds.
     *
     * @return locale property
     */
    public ObjectProperty<Locale> localeProperty() {
        return localeProperty;
    }

    /**
     * Returns the currently active locale.
     *
     * @return current locale
     */
    public Locale getCurrentLocale() {
        return localeProperty.get();
    }

    /**
     * Returns the language code of the currently active locale (e.g. {@code "en"}).
     *
     * @return two-letter language code
     */
    public String getCurrentCode() {
        return localeProperty.get().getLanguage();
    }

    /**
     * Sets the active locale by language code, persists the choice to settings, and
     * notifies all registered listeners.
     *
     * @param code two-letter language code (e.g. {@code "en"}, {@code "ja"})
     */
    public void setLocale(String code) {
        applyLocaleCode(code);
        if (settings != null && settingsRepository != null) {
            settings.put("ui_language", code);
            try {
                settingsRepository.save(settings);
            } catch (IOException e) {
                log.warn("Failed to persist locale preference '{}'", code, e);
            }
        }
    }

    /**
     * Returns a {@link ResourceBundle} for the currently active locale.
     *
     * @return resource bundle
     */
    public ResourceBundle getBundle() {
        return ResourceBundle.getBundle(BUNDLE_BASE, localeProperty.get());
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private void discoverLocales() {
        availableLocaleCodes.clear();
        for (String code : CANDIDATE_LOCALES) {
            Locale locale = localeForCode(code);
            try {
                ResourceBundle.getBundle(BUNDLE_BASE, locale);
                availableLocaleCodes.add(code);
                log.debug("Locale available: {}", code);
            } catch (MissingResourceException e) {
                log.debug("Locale not available: {}", code);
            }
        }
        if (availableLocaleCodes.isEmpty()) {
            availableLocaleCodes.add("en");
        }
    }

    private void applyLocaleCode(String code) {
        localeProperty.set(localeForCode(code));
    }

    private static Locale localeForCode(String code) {
        if ("ja".equals(code)) {
            return Locale.JAPANESE;
        }
        return Locale.forLanguageTag(code);
    }
}
