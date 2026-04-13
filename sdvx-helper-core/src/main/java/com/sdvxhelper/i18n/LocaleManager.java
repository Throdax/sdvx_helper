package com.sdvxhelper.i18n;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sdvxhelper.repository.SettingsRepository;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/**
 * Singleton that manages the application locale and provides locale-change
 * notifications.
 *
 * <p>
 * Available locales are discovered at startup by scanning the
 * {@code resources/i18n/} directory (relative to the working directory) for
 * files matching the pattern {@code messages_<code>.properties}. Any locale
 * code found there is automatically made available, with no hard-coded list of
 * candidates.
 * </p>
 *
 * <p>
 * When {@link #setLocale(String)} is called the new locale is persisted to
 * {@code settings.json} via the {@link SettingsRepository} and all JavaFX
 * listeners attached to {@link #localeProperty()} are notified so that each
 * open {@code *App} can rebuild its scene.
 * </p>
 *
 * @author Throdax
 * @since 2.0.0
 */
public final class LocaleManager {

    private static final Logger log = LoggerFactory.getLogger(LocaleManager.class);

    private static final String BUNDLE_BASE = "i18n/messages";
    private static final String I18N_DIR = "resources/i18n";
    private static final String BUNDLE_PREFIX = "messages_";
    private static final String BUNDLE_SUFFIX = ".properties";

    private static final LocaleManager instance = new LocaleManager();

    private final ObservableList<String> availableLocaleCodes = FXCollections.observableArrayList();
    private final ObjectProperty<Locale> localeProperty = new SimpleObjectProperty<>(Locale.ROOT);

    private SettingsRepository settingsRepository;
    private Map<String, String> settings;
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
        return instance;
    }

    /**
     * Initialises the manager with a settings repository, loading the persisted
     * locale preference. This method is idempotent; subsequent calls have no
     * effect.
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
        String savedCode = settings.getOrDefault("ui_language", "en");
        applyLocaleCode(savedCode);
        log.info("LocaleManager initialised with locale '{}'", getCurrentCode());
    }

    /**
     * Returns the list of locale codes for which a resource bundle file was
     * found in {@value #I18N_DIR} at startup. The list is determined entirely
     * by the files present on disk; no recompilation is needed to add a new
     * language.
     *
     * @return observable list of discovered locale codes (e.g. {@code ["en", "ja", "kr"]})
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

        File i18nDir = new File(I18N_DIR);
        if (!i18nDir.isDirectory()) {
            log.warn("i18n directory not found at '{}', defaulting to 'en'", i18nDir.getAbsolutePath());
            availableLocaleCodes.add("en");
            return;
        }

        FilenameFilter bundleFilter = (_, name) -> name.startsWith(BUNDLE_PREFIX) && name.endsWith(BUNDLE_SUFFIX);

        File[] bundleFiles = i18nDir.listFiles(bundleFilter);
        if (bundleFiles != null) {
            for (File file : bundleFiles) {
                String name = file.getName();
                String code = name.substring(BUNDLE_PREFIX.length(), name.length() - BUNDLE_SUFFIX.length());
                if (!code.isEmpty()) {
                    availableLocaleCodes.add(code);
                    log.debug("Locale discovered: {}", code);
                }
            }
        }

        if (availableLocaleCodes.isEmpty()) {
            log.warn("No locale bundles found in '{}', falling back to bundled default 'en'", i18nDir.getAbsolutePath());
            availableLocaleCodes.add("en");
        }
    }

    private void applyLocaleCode(String code) {
        localeProperty.set(Locale.forLanguageTag(code));
    }
}
