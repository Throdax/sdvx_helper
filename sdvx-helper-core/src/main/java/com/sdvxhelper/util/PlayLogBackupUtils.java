package com.sdvxhelper.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Comparator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class that manages rolling backups of {@code alllog.xml} before any
 * overwriting write operation.
 *
 * <p>
 * Each call to {@link #backup(File)} copies the current file into a
 * {@code backup/} sub-directory that sits next to the source file (i.e. a
 * sibling of {@code resources/}, {@code out/}, {@code log/}, etc. in the
 * typical installation layout). The copy is named
 * {@code alllog.xml.yyyyMMdd_HHmmss}.
 * </p>
 *
 * <p>
 * The sub-directory is capped at {@link #MAX_BACKUP_COUNT} files. When the cap
 * would be exceeded, the oldest backup is deleted first, so the folder never
 * grows beyond the configured limit.
 * </p>
 *
 * @author Throdax
 * @since 2.0.0
 */
public class PlayLogBackupUtils {

    private static final Logger log = LoggerFactory.getLogger(PlayLogBackupUtils.class);

    /**
     * Maximum number of backup files retained in the {@code backup/} sub-directory.
     * When a new backup would exceed this count the oldest existing backup is
     * deleted first.
     */
    public static final int MAX_BACKUP_COUNT = 20;

    private static final DateTimeFormatter BACKUP_TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    /**
     * Utility class — not meant to be instantiated.
     */
    private PlayLogBackupUtils() {
    }

    /**
     * Creates a timestamped backup copy of {@code playLogFile} inside a
     * {@code backup/} sub-directory adjacent to the file. If the sub-directory does
     * not yet exist it is created. If the number of existing backups for the same
     * base filename is already at {@link #MAX_BACKUP_COUNT}, the oldest backup is
     * deleted before the new one is written, keeping the folder size bounded.
     *
     * <p>
     * When {@code playLogFile} does not exist yet (first run before any save) the
     * method returns immediately without creating any file or directory.
     * </p>
     *
     * @param playLogFile
     *            the file to back up (typically {@code alllog.xml})
     * @throws IOException
     *             if the backup directory cannot be created or the file copy fails
     */
    public static void backup(File playLogFile) throws IOException {
        if (!playLogFile.exists()) {
            log.debug("backup: '{}' does not exist yet, skipping pre-save backup", playLogFile.getAbsolutePath());
            return;
        }

        File backupDir = new File(playLogFile.getParentFile(), "backup");
        Files.createDirectories(backupDir.toPath());

        pruneOldestBackups(backupDir, playLogFile.getName());

        String timestamp = LocalDateTime.now().format(BACKUP_TIMESTAMP_FORMAT);
        File backupFile = new File(backupDir, playLogFile.getName() + "." + timestamp);
        Files.copy(playLogFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        log.info("backup: created '{}' in '{}'", backupFile.getName(), backupDir.getAbsolutePath());
    }

    /**
     * Deletes the oldest backup files in {@code backupDir} whose names start with
     * {@code baseFilename + "."} until the count of remaining backups is strictly
     * below {@link #MAX_BACKUP_COUNT}, leaving room for the new backup that the
     * caller is about to write.
     *
     * @param backupDir
     *            the directory containing the backup files
     * @param baseFilename
     *            the original filename (e.g. {@code "alllog.xml"}) used as prefix
     *            for identifying backups that belong to this file
     */
    private static void pruneOldestBackups(File backupDir, String baseFilename) {
        File[] backups = backupDir.listFiles(f -> f.getName().startsWith(baseFilename + "."));
        if (backups == null || backups.length < MAX_BACKUP_COUNT) {
            return;
        }

        Arrays.sort(backups, Comparator.comparingLong(File::lastModified));
        int toDelete = backups.length - MAX_BACKUP_COUNT + 1;
        for (int i = 0; i < toDelete; i++) {
            if (backups[i].delete()) {
                log.info("pruneOldestBackups: deleted old backup '{}'", backups[i].getName());
            } else {
                log.warn("pruneOldestBackups: failed to delete '{}'", backups[i].getName());
            }
        }
    }
}
