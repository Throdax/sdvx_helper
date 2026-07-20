package com.sdvxhelper.app.controller;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javafx.application.Platform;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Performs the five-step migration from the existing SDVX Helper installation
 * to the new Puni Edition distribution.
 *
 * <p>
 * An instance of this class is constructed once per migration attempt. It runs
 * on a dedicated background thread and reports progress through a
 * {@link MigrationCallback} that is always invoked on the JavaFX Application
 * Thread via {@link Platform#runLater}.
 * </p>
 *
 * <p>
 * The working directory ({@code workDir}) is the directory from which the
 * migrator EXE was launched, i.e. the root of the existing installation
 * ({@code System.getProperty("user.dir")}).
 * </p>
 *
 * @author Filipe Cristino
 * @since 2.0.0
 */
public class MigrationService implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(MigrationService.class);

    /**
     * Files and directories that belong to the migrator itself and must not be
     * copied into the backup folder. The backup directory itself is excluded
     * dynamically via {@link #backupDirName} to support arbitrary installation
     * folder names.
     */
    private static final Set<String> OWN_FILES = Set.of("migrate.exe", "runtime", "app",
            "sdvx_helper_puni_standalone.zip", "migrate_pkl_to_xml.py");

    /**
     * ZIP entry name prefixes that must be skipped during extraction.
     *
     * <p>
     * The {@code runtime/} directory is excluded because the jpackage-bundled Java
     * runtime shipped with {@code migrate.exe} is already present on disk and is
     * the same runtime used by all other SDVX Helper executables. Attempting to
     * overwrite it while the migrator itself is running causes a
     * {@code java.dll is being used by another process} error on Windows.
     * </p>
     */
    private static final Set<String> SKIP_EXTRACT_PREFIXES = Set.of("runtime/", "runtime\\");

    /**
     * Directories introduced by the Java distribution that do not exist in the old
     * Python installation and therefore are not present in the
     * {@code sdvx_helper_old/} backup. These must be deleted explicitly during
     * rollback because copying the backup back does not remove them.
     */
    private static final Set<String> JAVA_ONLY_ENTRIES = Set.of("app");

    /**
     * Known pickle files (relative to {@code sdvx_helper_old/}) and their
     * corresponding output XML names. Each entry is {@code {relative-pkl-path,
     * output-xml-name}}.
     */
    private static final List<String[]> PKL_DESCRIPTORS = Arrays.asList(new String[]{"alllog.pkl", "alllog.xml"},
            new String[]{"resources/musiclist.pkl", "musiclist.xml"},
            new String[]{"out/rival_log.pkl", "rival_log.xml"},
            new String[]{"resources/title_conv_table.pkl", "title_conv_table.xml"});

    private static final MigrationStep[] STEPS = MigrationStep.values();

    private File workDir;
    private MigrationCallback callback;

    /**
     * Name of the backup sub-directory derived from the working directory name. For
     * example, if {@code workDir} is {@code sdvx_helper/}, the backup is created at
     * {@code sdvx_helper_old/}.
     */
    private String backupDirName;

    /**
     * Constructs a new {@code MigrationService} instance.
     *
     * @param workDir
     *            the root working directory (old Python installation root); must
     *            not be {@code null}
     * @param callback
     *            the progress callback invoked on the JavaFX thread; must not be
     *            {@code null}
     */
    public MigrationService(File workDir, MigrationCallback callback) {
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
    private void validateInputs(File workDir, MigrationCallback callback) {
        if (Objects.isNull(workDir)) {
            throw new IllegalArgumentException("workDir must not be null");
        }
        if (Objects.isNull(callback)) {
            throw new IllegalArgumentException("callback must not be null");
        }
    }

    @Override
    public void run() {
        boolean success = true;
        boolean backupCompleted = false;
        try {
            runStep(MigrationStep.BACKUP, 0);
            backupCompleted = true;
            runStep(MigrationStep.EXTRACT, 1);
            runStep(MigrationStep.MIGRATE_PKL, 2);
            runStep(MigrationStep.COPY_SETTINGS, 3);
            runStep(MigrationStep.CLEANUP, 4);
        } catch (IOException ioException) {
            log.error("Migration aborted due to fatal IO error", ioException);
            fireLog("FATAL: " + ioException.getMessage());
            fireLog("");
            success = false;
            if (backupCompleted) {
                try {
                    executeRollback();
                } catch (IOException rollbackEx) {
                    log.error("Rollback failed", rollbackEx);
                    fireLog("ERROR: Rollback also failed — " + rollbackEx.getMessage());
                    fireLog("       Please restore your files manually from " + backupDirName + "/");
                }
            } else {
                removePartialBackup();
            }
        }
        final boolean finalSuccess = success;
        Platform.runLater(() -> callback.onComplete(finalSuccess));
    }

    /**
     * Fires the step-started callback and then executes the given step.
     *
     * @param step
     *            the step to execute
     * @param stepIndex
     *            the zero-based position of this step within the sequence
     * @throws IOException
     *             if the step encounters a fatal I/O error
     */
    private void runStep(MigrationStep step, int stepIndex) throws IOException {
        Platform.runLater(() -> callback.onStepStarted(step, stepIndex, STEPS.length));
        switch (step) {
            case BACKUP :
                executeBackup();
                break;
            case EXTRACT :
                executeExtract();
                break;
            case MIGRATE_PKL :
                executeMigratePkl();
                break;
            case COPY_SETTINGS :
                executeCopySettings();
                break;
            case CLEANUP :
                executeCleanup();
                break;
            default :
                break;
        }
    }

    // -------------------------------------------------------------------------
    // Step implementations
    // -------------------------------------------------------------------------

    /**
     * Copies all non-own files and directories from the working directory to a
     * {@code <name>_old/} sub-directory, preserving the layout of the existing
     * installation.
     *
     * @throws IOException
     *             if creating the backup directory or copying any entry fails
     */
    private void executeBackup() throws IOException {
        File backupDir = new File(workDir, backupDirName);
        if (!backupDir.exists() && !backupDir.mkdirs()) {
            throw new IOException("Could not create backup directory: " + backupDir.getAbsolutePath());
        }
        fireLog("Backup destination: " + backupDir.getAbsolutePath());
        File[] entries = workDir.listFiles();
        if (Objects.isNull(entries)) {
            fireLog("WARN: Working directory returned null listing — skipping backup copy");
            return;
        }
        for (File entry : entries) {
            if (OWN_FILES.contains(entry.getName()) || entry.getName().equals(backupDirName)) {
                fireLog("  skip (own file): " + entry.getName());
                continue;
            }
            copyRecursive(entry, new File(backupDir, entry.getName()));
            fireLog("  copied: " + entry.getName());
        }
        fireLog("Backup complete.");
    }

    /**
     * Extracts {@code sdvx_helper_puni_standalone.zip} from the working directory
     * into the working directory, creating parent directories for each entry as
     * needed.
     *
     * @throws IOException
     *             if the ZIP file is missing or any entry cannot be written
     */
    private void executeExtract() throws IOException {
        File distZip = new File(workDir, "sdvx_helper_puni_standalone.zip");
        if (!distZip.exists()) {
            throw new IOException("Distribution ZIP not found: " + distZip.getAbsolutePath());
        }
        fireLog("Extracting: " + distZip.getAbsolutePath());
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(distZip))) {
            ZipEntry entry = zis.getNextEntry();
            while (!Objects.isNull(entry)) {
                String entryName = entry.getName();
                boolean skip = SKIP_EXTRACT_PREFIXES.stream().anyMatch(entryName::startsWith);
                if (skip) {
                    fireLog("  skip (runtime already present): " + entryName);
                    zis.closeEntry();
                    entry = zis.getNextEntry();
                    continue;
                }
                File target = new File(workDir, entryName);
                if (entry.isDirectory()) {
                    if (!target.exists() && !target.mkdirs()) {
                        throw new IOException("Failed to create directory: " + target.getAbsolutePath());
                    }
                } else {
                    File parentDir = target.getParentFile();
                    if (!parentDir.exists() && !parentDir.mkdirs()) {
                        throw new IOException("Failed to create directory: " + parentDir.getAbsolutePath());
                    }
                    try (FileOutputStream fos = new FileOutputStream(target)) {
                        byte[] buffer = new byte[8192];
                        int bytesRead;
                        while ((bytesRead = zis.read(buffer)) != -1) {
                            fos.write(buffer, 0, bytesRead);
                        }
                    }
                    fireLog("  extracted: " + entryName);
                }
                zis.closeEntry();
                entry = zis.getNextEntry();
            }
        }
        fireLog("Extraction complete.");
    }

    /**
     * Calls the bundled Python migration script once for each known pickle file
     * that actually exists in the {@code sdvx_helper_old/} directory. A missing
     * file or a non-zero script exit code is treated as a soft failure: a warning
     * is logged and the loop continues so that the remaining files are still
     * attempted.
     */
    private void executeMigratePkl() {
        File oldDir = new File(workDir, backupDirName);
        File pythonScript = new File(workDir, "migrate_pkl_to_xml.py");
        if (!pythonScript.exists()) {
            fireLog("WARN: migrate_pkl_to_xml.py not found next to the migrator — skipping pkl migration.");
            fireLog("      You can migrate pickle files manually later using play-log-sync-app.");
            return;
        }
        for (String[] descriptor : PKL_DESCRIPTORS) {
            String relativePklPath = descriptor[0];
            File pklFile = new File(oldDir, relativePklPath);
            if (!pklFile.exists()) {
                fireLog("  skip (not found): " + relativePklPath);
                continue;
            }
            fireLog("  migrating: " + relativePklPath);
            migrateOnePkl(pklFile, workDir, pythonScript);
        }
        fireLog("Pickle migration step complete.");
    }

    /**
     * Launches the Python migration script for a single pickle file, streaming its
     * stdout/stderr to the log callback. A non-zero exit code or an
     * {@link IOException} (e.g. Python not installed) is logged as a warning rather
     * than aborting the migration.
     *
     * @param pklFile
     *            the pickle file to migrate
     * @param outputDir
     *            the directory where the generated XML should be written
     * @param pythonScript
     *            the {@code migrate_pkl_to_xml.py} script file
     */
    private void migrateOnePkl(File pklFile, File outputDir, File pythonScript) {
        ProcessBuilder processBuilder = new ProcessBuilder("python", pythonScript.getAbsolutePath(), "--pkl-file",
                pklFile.getAbsolutePath(), "--output-dir", outputDir.getAbsolutePath());
        processBuilder.redirectErrorStream(true);
        processBuilder.directory(workDir);
        try {
            Process process = processBuilder.start();
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line = br.readLine();
                while (!Objects.isNull(line)) {
                    fireLog("    [python] " + line);
                    line = br.readLine();
                }
            }
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                fireLog("  WARN: script exited with code " + exitCode + " for " + pklFile.getName());
            } else {
                fireLog("  done: " + pklFile.getName());
            }
        } catch (IOException ioException) {
            log.warn("Could not start Python for pkl migration of {}: {}", pklFile.getName(), ioException.getMessage());
            fireLog("  WARN: Python not available — skipping " + pklFile.getName());
            fireLog("        Install Python and re-run, or migrate manually later.");
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while waiting for Python process for {}", pklFile.getName());
            fireLog("  WARN: Migration of " + pklFile.getName() + " was interrupted.");
        }
    }

    /**
     * Copies {@code <backup>/settings.json} to {@code settings.json} in the working
     * directory, preserving the user's existing preferences so they are
     * automatically applied on the first launch of Puni Edition.
     *
     * @throws IOException
     *             if the source settings file does not exist or cannot be copied
     */
    private void executeCopySettings() throws IOException {
        File sourceSettings = new File(new File(workDir, backupDirName), "settings.json");
        File targetSettings = new File(workDir, "settings.json");
        if (!sourceSettings.exists()) {
            fireLog("WARN: " + backupDirName + "/settings.json not found — skipping settings copy.");
            fireLog("      Default settings will be generated on first launch.");
            return;
        }
        Files.copy(sourceSettings.toPath(), targetSettings.toPath(), StandardCopyOption.REPLACE_EXISTING);
        fireLog("Settings copied: " + targetSettings.getAbsolutePath());
    }

    /**
     * Deletes the {@code sdvx_helper_puni_standalone.zip} archive from the working
     * directory now that it has been fully extracted. A failure to delete is logged
     * as a warning because it does not affect the usability of the new
     * installation.
     */
    private void executeCleanup() {
        File distZip = new File(workDir, "sdvx_helper_puni_standalone.zip");
        if (!distZip.exists()) {
            fireLog("Distribution ZIP already removed — nothing to clean up.");
            return;
        }
        if (distZip.delete()) {
            fireLog("Deleted: " + distZip.getName());
        } else {
            fireLog("WARN: Could not delete " + distZip.getName() + " — you may remove it manually.");
        }
    }

    // -------------------------------------------------------------------------
    // Rollback helpers
    // -------------------------------------------------------------------------

    /**
     * Restores the working directory to its pre-migration state after a fatal
     * failure.
     *
     * <p>
     * The rollback performs three actions in order:
     * </p>
     * <ol>
     * <li>Copies everything from the backup directory back into the working
     * directory, overwriting any files that were extracted from the distribution
     * ZIP.</li>
     * <li>Deletes Puni Edition-only directories (see {@link #JAVA_ONLY_ENTRIES})
     * that were created by the extraction but are not part of the existing
     * installation and therefore not present in the backup.</li>
     * <li>Removes the backup directory itself so the working directory is left
     * exactly as it was before the migration started.</li>
     * </ol>
     *
     * @throws IOException
     *             if any file copy or delete operation fails
     */
    private void executeRollback() throws IOException {
        fireLog("--- Rolling back changes\u2026");
        File backupDir = new File(workDir, backupDirName);

        if (backupDir.exists()) {
            fireLog("Restoring files from backup\u2026");
            File[] entries = backupDir.listFiles();
            if (!Objects.isNull(entries)) {
                for (File entry : entries) {
                    File target = new File(workDir, entry.getName());
                    copyRecursive(entry, target);
                    fireLog("  restored: " + entry.getName());
                }
            }
        }

        for (String name : JAVA_ONLY_ENTRIES) {
            File toDelete = new File(workDir, name);
            if (toDelete.exists()) {
                deleteRecursive(toDelete);
                fireLog("  removed (Puni Edition-only): " + name);
            }
        }

        if (backupDir.exists()) {
            deleteRecursive(backupDir);
            fireLog("  removed: " + backupDirName);
        }

        fireLog("Rollback complete. Installation restored to previous state.");
    }

    /**
     * Removes a partially-created backup directory when the backup step itself
     * failed before completing. A failure to delete is logged as a warning rather
     * than thrown.
     */
    private void removePartialBackup() {
        File backupDir = new File(workDir, backupDirName);
        if (!backupDir.exists()) {
            return;
        }
        try {
            deleteRecursive(backupDir);
            fireLog("Cleaned up partial backup directory.");
        } catch (IOException e) {
            fireLog("WARN: Could not remove partial backup at " + backupDirName + "/ — remove it manually.");
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Delegates to {@link MigrationFileUtils#copyRecursive(File, File)}.
     *
     * @param source
     *            the file or directory to copy
     * @param destination
     *            the target path
     * @throws IOException
     *             if any file cannot be created or copied
     */
    private void copyRecursive(File source, File destination) throws IOException {
        MigrationFileUtils.copyRecursive(source, destination);
    }

    /**
     * Delegates to {@link MigrationFileUtils#deleteRecursive(File)}.
     *
     * @param file
     *            the file or directory to delete
     * @throws IOException
     *             if any entry cannot be deleted
     */
    private void deleteRecursive(File file) throws IOException {
        MigrationFileUtils.deleteRecursive(file);
    }

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
