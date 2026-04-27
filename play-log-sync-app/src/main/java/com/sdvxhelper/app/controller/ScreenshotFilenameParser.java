package com.sdvxhelper.app.controller;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.List;

import com.sdvxhelper.model.OnePlayData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Pure-logic helper for parsing result screenshot filenames and detecting
 * duplicate entries in the play log.
 *
 * <p>
 * Extracted from {@link PlayLogSyncController} to allow unit-testing without
 * JavaFX. All methods are stateless.
 * </p>
 *
 * @author Throdax
 * @since 2.0.0
 */
public final class ScreenshotFilenameParser {

    private static final Logger log = LoggerFactory.getLogger(ScreenshotFilenameParser.class);

    static final DateTimeFormatter SCREENSHOT_DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    /**
     * Must match {@code MusicInfo.DATE_FMT} so that {@link OnePlayData#getDate()}
     * parses correctly.
     */
    private static final DateTimeFormatter PLAY_DATA_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    static final List<String> DIFFICULTIES = List.of("NOV", "ADV", "EXH", "APPEND");

    private ScreenshotFilenameParser() {
        // utility class — not instantiable
    }

    /**
     * Parses a result screenshot filename in the format
     * {@code sdvx_{title}_{DIFF}_{lamp}_{score}_{date}_{time}.png} into a
     * {@link OnePlayData} record.
     *
     * @param filename
     *            the screenshot filename (base name only)
     * @return parsed play data, or {@code null} if the filename cannot be parsed
     */
    public static OnePlayData parse(String filename) {
        String name = filename.endsWith(".png") ? filename.substring(0, filename.length() - 4) : filename;
        String[] parts = name.split("_", -1);
        if (parts.length < 6) {
            log.debug("parse: too few parts ({}) in filename '{}', skipping", parts.length, filename);
            return null;
        }
        // parts[0] = "sdvx"; parts[1..n] = title chunks until a difficulty token
        StringBuilder titleBuilder = new StringBuilder();
        int lastTitleIndex = 1;
        for (int i = 1; i < parts.length; i++) {
            if (DIFFICULTIES.contains(parts[i].toUpperCase())) {
                break;
            }
            if (titleBuilder.length() > 0) {
                titleBuilder.append(' ');
            }
            titleBuilder.append(parts[i]);
            lastTitleIndex = i;
        }
        String title = titleBuilder.toString().trim();
        if (title.isEmpty()) {
            log.debug("parse: title part is empty in filename '{}', skipping", filename);
            return null;
        }
        try {
            int diffIdx = lastTitleIndex + 1;
            if (diffIdx >= parts.length) {
                log.debug("parse: diffIdx ({}) out of range in filename '{}', skipping", diffIdx, filename);
                return null;
            }
            String diff = parts[diffIdx].toLowerCase();

            int lampIdx = diffIdx + 1;
            if (lampIdx < parts.length && "class".equalsIgnoreCase(parts[lampIdx])) {
                lampIdx++;
            }
            if (lampIdx >= parts.length) {
                log.debug("parse: lampIdx ({}) out of range in filename '{}', skipping", lampIdx, filename);
                return null;
            }
            String lamp = parts[lampIdx];

            int scoreIdx = lampIdx + 1;
            int scoreVal = 0;
            if (scoreIdx < parts.length && !parts[scoreIdx].isEmpty()) {
                try {
                    scoreVal = Integer.parseInt(parts[scoreIdx]) * 10000;
                } catch (NumberFormatException e) {
                    scoreVal = 0;
                }
            }

            int dateIdx = scoreIdx + 1;
            int timeIdx = dateIdx + 1;
            if (dateIdx >= parts.length || timeIdx >= parts.length) {
                log.debug("parse: dateIdx ({}) or timeIdx ({}) out of range in filename '{}', skipping", dateIdx,
                        timeIdx, filename);
                return null;
            }
            String dateStr = parts[dateIdx] + "_" + parts[timeIdx].replaceAll("\\.png.*$", "");
            LocalDateTime date;
            try {
                date = LocalDateTime.parse(dateStr, SCREENSHOT_DATE_FMT);
            } catch (DateTimeParseException e) {
                return null;
            }

            return new OnePlayData(title, scoreVal, 0, lamp, diff, date.format(PLAY_DATA_FMT));
        } catch (ArrayIndexOutOfBoundsException e) {
            log.debug("Could not parse filename {}: {}", filename, e.getMessage());
            return null;
        }
    }

    /**
     * Returns {@code true} if a matching play already exists in the log within 120
     * seconds (adjusted for {@code timeOffsetSeconds}).
     *
     * @param logEntries
     *            existing plays in the log
     * @param candidate
     *            candidate play parsed from a screenshot filename
     * @param timeOffsetSeconds
     *            offset applied to log entry timestamps before comparison
     * @return {@code true} if the candidate is considered a duplicate
     */
    public static boolean isSongInLog(List<OnePlayData> logEntries, OnePlayData candidate, int timeOffsetSeconds) {
        if (candidate.getDate() == null) {
            return false;
        }
        for (OnePlayData entry : logEntries) {
            if (entry.getTitle() == null || !entry.getTitle().equals(candidate.getTitle())) {
                continue;
            }
            if (entry.getDifficulty() == null || !entry.getDifficulty().equalsIgnoreCase(candidate.getDifficulty())) {
                continue;
            }
            if (entry.getDate() == null) {
                continue;
            }
            LocalDateTime adjustedDate = entry.getDate().minusSeconds(timeOffsetSeconds);
            long diffSeconds = Math.abs(ChronoUnit.SECONDS.between(adjustedDate, candidate.getDate()));
            if (diffSeconds < 120) {
                return true;
            }
        }
        return false;
    }
}
