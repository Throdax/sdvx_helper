package com.sdvxhelper.app.controller;

import java.io.File;
import java.io.IOException;
import java.util.Objects;
import java.util.Set;
import javafx.application.Platform;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reverts a completed Puni Edition migration by restoring the contents of the
 * backup directory ({@code <name>_old/}) and removing Puni Edition-only
 * artifacts.
 *
 * <p>
 * An instance is constructed once per revert attempt. It runs on a dedicated
 * background thread and reports progress through a {@link RevertCallback} that
 * is always invoked on the JavaFX Application Thread via
 * {@link Platform#runLater}.
 * </p>
 *
 * <p>
 * After a successful revert the Puni Edition executables ({@code migrate.exe}
 * and the {@code runtime/} folder) are still present because this process is
 * itself running from them. The user is instructed to delete those manually
 * after closing the migrator.
 * </p>
 *
 * @author Throdax
 * @since 2.0.0
 */
public class RevertService implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(RevertService.class);

    /**
     * Puni Edition-only directories that must be removed during revert but are
     * absent from the backup. {@code app/} and {@code runtime/} are intentionally
     * excluded here because the migrator itself runs from those directories and
     * they cannot be deleted while the process is alive.
     */
    private static final Set<String> PUNI_ONLY_ENTRIES = Set.of();

    private File workDir;
    private RevertCallback callback;
    private String backupDirName;

    /**
     * Constructs a new {@code RevertService} instance.
     *
     * @param workDir
     *            the root working directory (Puni Edition installation root); must
     *            not be {@code null}
     * @param callback
     *            the progress callback invoked on the JavaFX thread; must not be
     *            {@code null}
     */
    public RevertService(File workDir, RevertCallback callback) {
        validateInputs(workDir, callback);
        this.workDir = workDir;
        this.backupDirName = workDir.getName() + "_old";
        this.callback = callback;
    }

    /**
     * Validates the constructor arguments, throwing immediately on any {@code null}
     * input.
     *
     * @param workDir
     *            the working directory to validate
     * @param callback
     *            the callback to validate
     */
    private void validateInputs(File workDir, RevertCallback callback) {
        if (Objects.isNull(workDir)) {
            throw new IllegalArgumentException("workDir must not be null");
        }
        if (Objects.isNull(callback)) {
            throw new IllegalArgumentException("callback must not be null");
        }
    }

    @Override
    public void run() {
        try {
            fireLog("Starting revert\u2026");

            File backupDir = new File(workDir, backupDirName);
            if (!backupDir.isDirectory()) {
                throw new IOException("Backup directory not found: " + backupDir.getAbsolutePath());
            }

            restoreBackup(backupDir);
            removePuniEditionArtifacts();
            removeDistributionZip();
            removeBackupDir(backupDir);

            fireLog("");
            fireLog("Revert complete.");
            fireLog("NOTE: The app/ and runtime/ folders cannot be deleted while the migrator");
            fireLog("      is running. Close this application first, then delete them together");
            fireLog("      with migrate.exe.");
            Platform.runLater(() -> callback.onComplete(true));
        } catch (IOException ioException) {
            log.error("Revert aborted due to fatal IO error", ioException);
            fireLog("FATAL: " + ioException.getMessage());
            Platform.runLater(() -> callback.onComplete(false));
        }
    }

    // -------------------------------------------------------------------------
    // Revert steps
    // -------------------------------------------------------------------------

    /**
     * Copies every entry from the backup directory back into the working directory,
     * overwriting any Puni Edition files with the originals from the backup.
     *
     * <p>
     * The {@code log/} directory is intentionally skipped: the log files are
     * written by the running migrator process and the destination
     * {@code log/migrator.log} is locked by Log4j. Old log files from the backup
     * do not need to be restored.
     * </p>
     *
     * @param backupDir
     *            the backup directory to restore from
     * @throws IOException
     *             if any file cannot be copied
     */
    private void restoreBackup(File backupDir) throws IOException {
        fireLog("Restoring files from " + backupDirName + "/\u2026");
        File[] entries = backupDir.listFiles();
        if (Objects.isNull(entries)) {
            fireLog("WARN: Backup directory returned null listing — nothing restored.");
            return;
        }
        for (File entry : entries) {
            if ("log".equalsIgnoreCase(entry.getName()) && entry.isDirectory()) {
                fireLog("  skip (log files remain active): log/");
                continue;
            }
            MigrationFileUtils.copyRecursive(entry, new File(workDir, entry.getName()));
            fireLog("  restored: " + entry.getName());
        }
        fireLog("Restore complete.");
    }

    /**
     * Deletes each directory listed in {@link #PUNI_ONLY_ENTRIES} that exists in
     * the working directory. These directories were introduced by the Puni Edition
     * distribution and are absent from the backup, so restoring the backup alone
     * would not remove them.
     *
     * <p>
     * {@code app/} and {@code runtime/} are deliberately excluded from this set
     * because the migrator process itself runs from those directories and cannot
     * delete them while it is alive. The user is instructed to remove them manually
     * after closing the migrator.
     * </p>
     *
     * @throws IOException
     *             if any entry cannot be deleted
     */
    private void removePuniEditionArtifacts() throws IOException {
        for (String name : PUNI_ONLY_ENTRIES) {
            File toDelete = new File(workDir, name);
            if (toDelete.exists()) {
                MigrationFileUtils.deleteRecursive(toDelete);
                fireLog("  removed (Puni Edition): " + name);
            }
        }
    }

    /**
     * Deletes the distribution ZIP archive if it still exists in the working
     * directory. During a normal migration the cleanup step deletes it, but a
     * failed or partial migration might leave it behind.
     */
    private void removeDistributionZip() {
        File distZip = new File(workDir, "sdvx_helper_puni_standalone.zip");
        if (distZip.exists()) {
            if (distZip.delete()) {
                fireLog("  removed: " + distZip.getName());
            } else {
                fireLog("WARN: Could not delete " + distZip.getName() + " — remove it manually.");
            }
        }
    }

    /**
     * Deletes the backup directory now that all of its contents have been restored
     * to the working directory.
     *
     * @param backupDir
     *            the backup directory to delete
     * @throws IOException
     *             if the directory or any of its remaining contents cannot be
     *             deleted
     */
    private void removeBackupDir(File backupDir) throws IOException {
        MigrationFileUtils.deleteRecursive(backupDir);
        fireLog("  removed: " + backupDirName);
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    /**
     * Fires a log line to the callback on the JavaFX Application Thread and also
     * emits the same text at DEBUG level through SLF4J.
     *
     * @param message
     *            the message to send; must not be {@code null}
     */
    private void fireLog(String message) {
        log.debug(message);
        Platform.runLater(() -> callback.onLog(message));
    }
}
