package com.sdvxhelper.service;

import com.sdvxhelper.repository.SpecialTitlesRepository;
import com.sdvxhelper.util.SpecialTitles;

import java.util.List;

/**
 * Normalises song title strings for consistent lookup and display.
 *
 * <p>Wraps the lookup logic previously spread across
 * {@code sdvx_utils.restore_title()} and {@code find_song_rating()} in the Python
 * code.  All data is loaded at startup from {@link SpecialTitlesRepository}.</p>
 *
 * @author sdvx-helper
 * @since 2.0.0
 */
public class TitleNormalizer {

    private final SpecialTitles specialTitles;

    /**
     * Constructs a normaliser and loads title data from the given repository.
     *
     * @param repository source of special-title mappings
     */
    public TitleNormalizer(SpecialTitlesRepository repository) {
        this.specialTitles = repository.load();
    }

    /**
     * Constructs a normaliser from a pre-loaded {@link SpecialTitles} instance.
     * Useful for testing.
     *
     * @param specialTitles pre-loaded data
     */
    public TitleNormalizer(SpecialTitles specialTitles) {
        this.specialTitles = specialTitles;
    }

    /**
     * Restores a filesystem-safe title string to its canonical game title.
     *
     * @param fsafeTitle filesystem-safe title (special characters removed/replaced)
     * @return canonical game title, or the input string unchanged if no mapping exists
     */
    public String restoreTitle(String fsafeTitle) {
        if (fsafeTitle == null) return null;
        return specialTitles.restoreTitle(fsafeTitle);
    }

    /**
     * Returns {@code true} if the given title should be skipped during processing.
     *
     * @param title title to test
     * @return {@code true} if ignored
     */
    public boolean isIgnored(String title) {
        return specialTitles.isIgnored(title);
    }

    /**
     * Returns {@code true} if the given title should be excluded from result output.
     *
     * @param title title to test
     * @return {@code true} if the title should be removed
     */
    public boolean shouldRemove(String title) {
        return specialTitles.shouldRemove(title);
    }

    /**
     * Returns the direct level-rating overrides for the given title.
     *
     * @param title song title
     * @return list of override strings (e.g. {@code ["EXH:20"]}), or empty list
     */
    public List<String> getOverrides(String title) {
        return specialTitles.getDirectOverrides(title);
    }
}
