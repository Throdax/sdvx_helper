package com.sdvxhelper.util;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import jakarta.json.bind.annotation.JsonbProperty;

/**
 * POJO holding the four title-normalisation data structures externalised from
 * the original Python {@code special_titles.py} file.
 *
 * <p>
 * This class is the data model; it is loaded at runtime by
 * {@code SpecialTitlesRepository} from {@code special_titles.json} in the
 * working directory, with a bundled default as a fallback. Editing
 * {@code special_titles.json} does not require recompilation.
 * </p>
 *
 * <p>
 * JSON-B annotations map to the {@code special_titles.json} key names.
 * </p>
 *
 * @author Throdax
 * @since 2.0.0
 */
public class SpecialTitles {

    /**
     * Maps filesystem-safe title strings (with special characters removed or
     * replaced) to the canonical game title string.
     *
     * <p>
     * Example: {@code "archivezip" → "archive::zip"}
     * </p>
     */
    @JsonbProperty("specialTitles")
    private Map<String, String> specialTitles = Collections.emptyMap();

    /**
     * Manual level-rating overrides for charts that are not in the main song-list
     * database.
     *
     * <p>
     * Example: {@code "SomeTitle" → ["APPEND:20", "EXH:18"]}
     * </p>
     */
    @JsonbProperty("directOverrides")
    private Map<String, List<String>> directOverrides = Collections.emptyMap();

    /**
     * Titles to skip / ignore entirely during processing.
     */
    @JsonbProperty("ignoredNames")
    private List<String> ignoredNames = Collections.emptyList();

    /**
     * Titles to remove from lookup results.
     */
    @JsonbProperty("directRemoves")
    private List<String> directRemoves = Collections.emptyList();

    /** No-argument constructor required by JSON-B. */
    public SpecialTitles() {
    }

    // -------------------------------------------------------------------------
    // Lookup helpers
    // -------------------------------------------------------------------------

    /**
     * Looks up the canonical title for the given filesystem-safe key.
     *
     * @param fsafeTitle
     *            filesystem-safe title string
     * @return canonical title, or the input unchanged if no mapping exists
     */
    public String restoreTitle(String fsafeTitle) {
        return specialTitles.getOrDefault(fsafeTitle, fsafeTitle);
    }

    /**
     * Returns {@code true} if the given title should be ignored during processing.
     *
     * @param title
     *            title to check
     * @return {@code true} if the title is in the ignored list
     */
    public boolean isIgnored(String title) {
        return ignoredNames.contains(title);
    }

    /**
     * Returns {@code true} if the given title should be removed from results.
     *
     * @param title
     *            title to check
     * @return {@code true} if the title is in the remove list
     */
    public boolean shouldRemove(String title) {
        return directRemoves.contains(title);
    }

    /**
     * Returns the override rating list for the given title, or an empty list if no
     * override is defined.
     *
     * @param title
     *            title to check
     * @return list of rating override strings (e.g.
     *         {@code ["APPEND:20", "EXH:18"]})
     */
    public List<String> getDirectOverrides(String title) {
        return directOverrides.getOrDefault(title, Collections.emptyList());
    }

    // -------------------------------------------------------------------------
    // Getters and setters (required by JSON-B)
    // -------------------------------------------------------------------------

    /**
     * Returns the special-titles map.
     *
     * @return filesystem-safe title → canonical title map
     */
    public Map<String, String> getSpecialTitles() {
        return specialTitles;
    }

    /**
     * Sets the special-titles map.
     *
     * @param specialTitles
     *            filesystem-safe title → canonical title map
     */
    public void setSpecialTitles(Map<String, String> specialTitles) {
        this.specialTitles = specialTitles != null ? specialTitles : Collections.emptyMap();
    }

    /**
     * Returns the direct-overrides map.
     *
     * @return title → rating-override-list map
     */
    public Map<String, List<String>> getDirectOverrides() {
        return directOverrides;
    }

    /**
     * Sets the direct-overrides map.
     *
     * @param directOverrides
     *            title → rating-override-list map
     */
    public void setDirectOverrides(Map<String, List<String>> directOverrides) {
        this.directOverrides = directOverrides != null ? directOverrides : Collections.emptyMap();
    }

    /**
     * Returns the ignored-names list.
     *
     * @return list of ignored title strings
     */
    public List<String> getIgnoredNames() {
        return ignoredNames;
    }

    /**
     * Sets the ignored-names list.
     *
     * @param ignoredNames
     *            list of ignored title strings
     */
    public void setIgnoredNames(List<String> ignoredNames) {
        this.ignoredNames = ignoredNames != null ? ignoredNames : Collections.emptyList();
    }

    /**
     * Returns the direct-removes list.
     *
     * @return list of titles to remove
     */
    public List<String> getDirectRemoves() {
        return directRemoves;
    }

    /**
     * Sets the direct-removes list.
     *
     * @param directRemoves
     *            list of titles to remove
     */
    public void setDirectRemoves(List<String> directRemoves) {
        this.directRemoves = directRemoves != null ? directRemoves : Collections.emptyList();
    }
}
