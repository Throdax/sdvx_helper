package com.sdvxhelper.ocr;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.sdvxhelper.network.HttpService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Checks whether required Tesseract language data files are present in the
 * tessdata directory and downloads any that are missing from the official
 * {@code tesseract-ocr/tessdata} GitHub repository.
 *
 * <p>
 * Downloads are performed using the project's existing {@link HttpService} (JDK
 * {@code HttpClient}) with a 5-minute timeout per file. Files are written
 * atomically: first to a {@code .tmp} sibling, then renamed to the final name
 * so a partially-downloaded file is never seen by Tesseract.
 * </p>
 *
 * <p>
 * This class is stateless; all methods are safe to call from any thread.
 * </p>
 *
 * @author Throdax
 * @since 2.0.0
 */
public class TesseractLanguageInstaller {

    private static final Logger log = LoggerFactory.getLogger(TesseractLanguageInstaller.class);

    private static final String BASE_URL = "https://raw.githubusercontent.com/tesseract-ocr/tessdata/main/";

    private static final Duration DOWNLOAD_TIMEOUT = Duration.ofMinutes(5);

    /**
     * Utility class — not meant to be instantiated.
     */
    private TesseractLanguageInstaller() {
    }

    /**
     * Ensures all requested Tesseract language packs are present in
     * {@code tessdataDir}.
     *
     * <p>
     * For each language code the method checks whether
     * {@code {tessdataDir}/{lang}.traineddata} already exists. Missing files are
     * downloaded one at a time from the official GitHub raw URL. The tessdata
     * directory is created if it does not exist.
     * </p>
     *
     * @param languages
     *            Tesseract language codes to check (e.g. {@code "jpn"},
     *            {@code "eng"}, {@code "fra"}, {@code "ell"})
     * @param tessdataDir
     *            path to the tessdata directory (e.g. {@code "resources/tessdata"})
     * @return {@code true} if all requested language files are present after the
     *         operation (whether they were already there or successfully
     *         downloaded), {@code false} if any file could not be obtained
     */
    public static boolean ensureLanguages(List<String> languages, String tessdataDir) {
        File dir = new File(tessdataDir);
        log.info("TesseractLanguageInstaller: checking {} language file(s) in '{}'", languages.size(), dir.getPath());

        List<String> missing = collectMissing(languages, dir);
        if (missing.isEmpty()) {
            log.info("TesseractLanguageInstaller: all language files already present - no download needed");
            return true;
        }

        log.info("TesseractLanguageInstaller: {} file(s) missing: {}", missing.size(), missing);

        if (!dir.exists() && !dir.mkdirs()) {
            log.error("TesseractLanguageInstaller: cannot create tessdata directory '{}'", dir.getPath());
            return false;
        }

        HttpService http = new HttpService(DOWNLOAD_TIMEOUT);
        boolean allSucceeded = true;
        for (String lang : missing) {
            boolean ok = downloadLanguage(http, lang, dir);
            if (!ok) {
                allSucceeded = false;
            }
        }

        if (allSucceeded) {
            log.info("TesseractLanguageInstaller: all downloads completed successfully");
        } else {
            log.warn("TesseractLanguageInstaller: one or more language files could not be downloaded");
        }
        return allSucceeded;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private static List<String> collectMissing(List<String> languages, File dir) {
        List<String> missing = new ArrayList<>();
        for (String lang : languages) {
            File target = new File(dir, lang + ".traineddata");
            if (target.exists()) {
                log.debug("TesseractLanguageInstaller: '{}' already present", target.getName());
            } else {
                missing.add(lang);
            }
        }
        return missing;
    }

    private static boolean downloadLanguage(HttpService http, String lang, File dir) {
        String filename = lang + ".traineddata";
        File target = new File(dir, filename);
        File tmp = new File(dir, filename + ".tmp");
        String url = BASE_URL + filename;

        log.info("TesseractLanguageInstaller: downloading '{}' from {}", filename, url);
        try {
            HttpResponse<InputStream> response = http.get(URI.create(url), Map.of(),
                    HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() != 200) {
                log.error("TesseractLanguageInstaller: HTTP {} for '{}'", response.statusCode(), url);
                return false;
            }

            try (InputStream in = response.body(); FileOutputStream fos = new FileOutputStream(tmp)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    fos.write(buffer, 0, read);
                }
            }

            if (tmp.renameTo(target)) {
                log.info("TesseractLanguageInstaller: '{}' installed successfully ({} bytes)", filename,
                        target.length());
                return true;
            } else {
                log.error("TesseractLanguageInstaller: rename from '{}' to '{}' failed", tmp.getName(), filename);
                return false;
            }
        } catch (IOException e) {
            log.error("TesseractLanguageInstaller: download failed for '{}': {}", filename, e.getMessage());
            return false;
        } finally {
            if (tmp.exists()) {
                tmp.delete();
            }
        }
    }
}
