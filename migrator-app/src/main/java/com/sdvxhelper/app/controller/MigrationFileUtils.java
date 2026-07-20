package com.sdvxhelper.app.controller;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Objects;

/**
 * Static file-system utilities shared by {@link MigrationService} and
 * {@link RevertService}.
 *
 * <p>
 * Both services perform recursive copy and recursive delete operations on the
 * installation directory. This class centralises those helpers so the logic is
 * defined exactly once.
 * </p>
 *
 * @author Throdax
 * @since 2.0.0
 */
public final class MigrationFileUtils {

    /**
     * Utility class — not meant to be instantiated.
     */
    private MigrationFileUtils() {
    }

    /**
     * Recursively copies {@code source} to {@code destination}. If {@code source}
     * is a directory, all of its children are copied recursively; if it is a
     * regular file it is copied directly with
     * {@link StandardCopyOption#REPLACE_EXISTING}.
     *
     * @param source
     *            the file or directory to copy; must not be {@code null}
     * @param destination
     *            the target path; must not be {@code null}
     * @throws IOException
     *             if any directory cannot be created or any file cannot be copied
     */
    public static void copyRecursive(File source, File destination) throws IOException {
        if (source.isDirectory()) {
            if (!destination.exists() && !destination.mkdirs()) {
                throw new IOException("Could not create directory: " + destination.getAbsolutePath());
            }
            File[] children = source.listFiles();
            if (!Objects.isNull(children)) {
                for (File child : children) {
                    copyRecursive(child, new File(destination, child.getName()));
                }
            }
        } else {
            File parentDir = destination.getParentFile();
            if (!parentDir.exists() && !parentDir.mkdirs()) {
                throw new IOException("Could not create parent directory: " + parentDir.getAbsolutePath());
            }
            Files.copy(source.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * Recursively deletes {@code file} and all of its children if it is a
     * directory.
     *
     * @param file
     *            the file or directory to delete; must not be {@code null}
     * @throws IOException
     *             if any entry cannot be deleted
     */
    public static void deleteRecursive(File file) throws IOException {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (!Objects.isNull(children)) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        if (!file.delete()) {
            throw new IOException("Could not delete: " + file.getAbsolutePath());
        }
    }
}
