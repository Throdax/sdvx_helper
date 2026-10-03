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
 * @author Throdax
 * @since 2.0.0
 */
public enum MigrationStep {

    /**
     * Copies all existing installation files (excluding the migrator's own files)
     * to a {@code <name>_old/} sub-directory as a safety backup.
     */
    BACKUP("Backing up existing installation\u2026"),

    /**
     * Extracts {@code sdvx_helper_puni_standalone.zip} into the working directory
     * so the new Puni Edition application layout is in place.
     */
    EXTRACT("Extracting Puni Edition\u2026"),

    /**
     * Calls the bundled {@code migrate_pkl_to_xml.py} script once per known pickle
     * file, converting each to its XML counterpart.
     */
    MIGRATE_PKL("Migrating data files\u2026"),

    /**
     * Copies {@code <backup>/settings.json} to the new {@code settings.json} so
     * existing user preferences are preserved.
     */
    COPY_SETTINGS("Copying settings\u2026"),

    /**
     * Deletes the {@code sdvx_helper_puni_standalone.zip} archive now that it has
     * been fully extracted.
     */
    CLEANUP("Cleaning up\u2026");

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
