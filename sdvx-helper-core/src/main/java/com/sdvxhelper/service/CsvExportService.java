package com.sdvxhelper.service;

import com.sdvxhelper.model.MusicInfo;
import com.sdvxhelper.model.OnePlayData;
import com.sdvxhelper.util.ScoreFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Generates CSV export files for play history and best-score data.
 *
 * <p>Replaces the Python {@code gen_best_csv()}, {@code gen_alllog_csv()}, and
 * {@code gen_playcount_csv()} methods in {@code SDVXLogger}.
 *
 * <p>The alllog CSV is exported in Shift-JIS (to match the original Python behaviour)
 * while other CSVs use UTF-8.  Both encodings are configurable via the write methods.</p>
 *
 * @author sdvx-helper
 * @since 2.0.0
 */
public class CsvExportService {

    private static final Logger log = LoggerFactory.getLogger(CsvExportService.class);
    private static final Charset SHIFT_JIS = Charset.forName("Shift_JIS");

    /**
     * Constructs the export service.
     */
    public CsvExportService() {
    }

    // -------------------------------------------------------------------------
    // Best CSV
    // -------------------------------------------------------------------------

    /**
     * Writes a best-scores CSV ({@code out/best_*.csv}) containing one row per chart.
     *
     * <p>Columns: {@code title,difficulty,lv,score,lamp,vf}</p>
     *
     * @param bestList list of personal-best {@link MusicInfo} records (VF desc order)
     * @param outFile  destination file
     * @throws IOException if the file cannot be written
     */
    public void writeBestCsv(List<MusicInfo> bestList, File outFile) throws IOException {
        ensureParent(outFile);
        try (PrintWriter pw = new PrintWriter(outFile, StandardCharsets.UTF_8)) {
            pw.println("title,difficulty,lv,score,lamp,vf");
            for (MusicInfo m : bestList) {
                pw.printf("%s,%s,%s,%d,%s,%s%n",
                        csvEsc(m.getTitle()),
                        csvEsc(m.getDifficulty()),
                        csvEsc(m.getLv()),
                        m.getBestScore(),
                        csvEsc(m.getBestLamp()),
                        ScoreFormatter.formatVf(m.getVf()));
            }
        }
        log.info("Wrote best CSV to {} ({} records)", outFile.getAbsolutePath(), bestList.size());
    }

    // -------------------------------------------------------------------------
    // All-log CSV
    // -------------------------------------------------------------------------

    /**
     * Writes the full play-history CSV ({@code out/alllog_*.csv}) in Shift-JIS
     * to match the original Python output encoding.
     *
     * <p>Columns: {@code title,difficulty,lv,score,lamp,vf,date}</p>
     *
     * @param plays   all play records (date ascending)
     * @param outFile destination file
     * @throws IOException if the file cannot be written
     */
    public void writeAllLogCsv(List<OnePlayData> plays, File outFile) throws IOException {
        ensureParent(outFile);
        try (PrintWriter pw = new PrintWriter(outFile, SHIFT_JIS)) {
            pw.println("title,difficulty,score,lamp,diff,date");
            for (OnePlayData p : plays) {
                pw.printf("%s,%s,%d,%s,%+d,%s%n",
                        csvEsc(p.getTitle()),
                        csvEsc(p.getDifficulty()),
                        p.getCurScore(),
                        csvEsc(p.getLamp()),
                        p.getDiff(),
                        csvEsc(p.getDate()));
            }
        }
        log.info("Wrote alllog CSV to {} ({} records)", outFile.getAbsolutePath(), plays.size());
    }

    // -------------------------------------------------------------------------
    // Play-count CSV
    // -------------------------------------------------------------------------

    /**
     * Writes a play-count-per-day CSV ({@code out/playcount_*.csv}).
     *
     * <p>Columns: {@code date,count}</p>
     *
     * @param plays   all play records
     * @param outFile destination file
     * @throws IOException if the file cannot be written
     */
    public void writePlayCountCsv(List<OnePlayData> plays, File outFile) throws IOException {
        ensureParent(outFile);
        // Count plays per date prefix (first 10 chars = YYYY-MM-DD)
        java.util.Map<String, Long> countByDate = new java.util.LinkedHashMap<>();
        for (OnePlayData p : plays) {
            String date = p.getDate() != null && p.getDate().length() >= 10
                    ? p.getDate().substring(0, 10) : "unknown";
            countByDate.merge(date, 1L, Long::sum);
        }
        try (PrintWriter pw = new PrintWriter(outFile, StandardCharsets.UTF_8)) {
            pw.println("date,count");
            for (java.util.Map.Entry<String, Long> entry : countByDate.entrySet()) {
                pw.printf("%s,%d%n", entry.getKey(), entry.getValue());
            }
        }
        log.info("Wrote playcount CSV to {} ({} days)", outFile.getAbsolutePath(), countByDate.size());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static void ensureParent(File file) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            if (!parent.mkdirs()) {
                throw new IOException("Could not create directory: " + parent.getAbsolutePath());
            }
        }
    }

    /**
     * Wraps a CSV field in quotes and escapes internal double-quotes if the field
     * contains a comma, double-quote, or newline.
     *
     * @param value field value
     * @return CSV-safe string
     */
    private static String csvEsc(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
