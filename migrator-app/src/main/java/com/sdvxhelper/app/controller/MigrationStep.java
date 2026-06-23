package com.sdvxhelper.app.controller;

/**
 * Ordered sequence of steps performed by {@link MigrationService} when the user
 * triggers a migration.
 *
 * <p>
 * The steps execute in declaration order: {@code BACKUP} → {@code EXTRACT} →
 * {@code MIGRATE_PKL} → {@code COPY_SETTINGS} → {@code CLEANUP}.
 * </p>
 *
 * @author Filipe Cristino
 * @since 2.0.0
 */
public enum MigrationStep {

    /**
     * Copies all Python installation files (excluding the migrator's own files) to
     * a {@code sdvx_helper_old/} sub-directory.
     */
    BACKUP("Backing up old Python files…"),

    /**
     * Extracts {@code sdvx_helper_java_standalone.zip} into the working directory
     * so the new Java application layout is in place.
     */
    EXTRACT("Extracting new Java distribution…"),

    /**
     * Calls the bundled {@code migrate_pkl_to_xml.py} script once per known pickle
     * file, converting each to its XML counterpart.
     */
    MIGRATE_PKL("Migrating pickle data files…"),

    /**
     * Copies {@code sdvx_helper_old/settings.json} to the new {@code settings.json}
     * so existing user preferences are preserved.
     */
    COPY_SETTINGS("Copying settings…"),

    /**
     * Deletes the {@code sdvx_helper_java_standalone.zip} archive now that it has
     * been fully extracted.
     */
    CLEANUP("Cleaning up…");

    private String displayName;

    /**
     * Creates a step with the given human-readable display name.
     *
     * @param displayName
     *            the label shown in the UI step indicator
     */
    MigrationStep(String displayName) {
        this.displayName = displayName;
    }

    /**
     * Returns the human-readable label that describes this step in the UI.
     *
     * @return the display name; never {@code null}
     */
    public String getDisplayName() {
        return displayName;
    }
}
