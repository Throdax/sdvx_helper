package com.sdvxhelper.service;

import com.sdvxhelper.model.MusicInfo;
import com.sdvxhelper.model.OnePlayData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

/**
 * Generates the OBS overlay XML files consumed by OBS browser sources.
 *
 * <p>Extracts and replaces the manual XML string concatenation scattered across
 * {@code SDVXLogger.gen_history_cursong()}, {@code gen_sdvx_battle()},
 * {@code gen_vf_onselect()}, {@code gen_rival_xml()}, and related methods in the
 * Python code.  All output files are written UTF-8 encoded.</p>
 *
 * @author Throdax
 * @since 2.0.0
 */
public class XmlExportService {

    private static final Logger log = LoggerFactory.getLogger(XmlExportService.class);

    /**
     * Constructs the export service.
     */
    public XmlExportService() {
    }

    // -------------------------------------------------------------------------
    // History / current-song overlay
    // -------------------------------------------------------------------------

    /**
     * Writes the play-history overlay for the currently selected song.
     * Output: {@code out/history_cursong.xml}
     *
     * @param plays     all plays for the current song, most-recent first
     * @param lv        chart level integer (-1 if unknown)
     * @param outFile   destination file
     * @throws IOException if the file cannot be written
     */
    public void writeHistoryCurSong(List<OnePlayData> plays, int lv, File outFile) throws IOException {
        ensureParent(outFile);
        try (PrintWriter pw = writer(outFile)) {
            pw.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
            pw.println("<history>");
            for (OnePlayData play : plays) {
                int vf = lv > 0 ? VolforceCalculator.computeSingleVf(play.getCurScore(), play.getLamp(), lv) : 0;
                pw.printf("  <play score=\"%d\" lamp=\"%s\" diff=\"%+d\" vf=\"%s\" date=\"%s\"/>%n",
                        play.getCurScore(),
                        xmlEsc(play.getLamp()),
                        play.getDiff(),
                        formatVf(vf),
                        xmlEsc(play.getDate()));
            }
            pw.println("</history>");
        }
        log.debug("Wrote history_cursong.xml ({} plays)", plays.size());
    }

    // -------------------------------------------------------------------------
    // Battle overlay (today's plays)
    // -------------------------------------------------------------------------

    /**
     * Writes today's plays for the battle overlay.
     * Output: {@code out/sdvx_battle.xml}
     *
     * @param todayPlays plays recorded since the session started
     * @param outFile    destination file
     * @throws IOException if the file cannot be written
     */
    public void writeSdvxBattle(List<OnePlayData> todayPlays, File outFile) throws IOException {
        ensureParent(outFile);
        try (PrintWriter pw = writer(outFile)) {
            pw.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
            pw.println("<battle>");
            for (OnePlayData play : todayPlays) {
                pw.printf("  <play title=\"%s\" diff=\"%s\" score=\"%d\" lamp=\"%s\" date=\"%s\"/>%n",
                        xmlEsc(play.getTitle()),
                        xmlEsc(play.getDifficulty()),
                        play.getCurScore(),
                        xmlEsc(play.getLamp()),
                        xmlEsc(play.getDate()));
            }
            pw.println("</battle>");
        }
        log.debug("Wrote sdvx_battle.xml ({} plays)", todayPlays.size());
    }

    // -------------------------------------------------------------------------
    // VF on select overlay
    // -------------------------------------------------------------------------

    /**
     * Writes Volforce information for the song currently on the select screen.
     * Output: {@code out/vf_onselect.xml}
     *
     * @param info    personal-best info for the selected chart (may be null if unknown)
     * @param outFile destination file
     * @throws IOException if the file cannot be written
     */
    public void writeVfOnSelect(MusicInfo info, File outFile) throws IOException {
        ensureParent(outFile);
        try (PrintWriter pw = writer(outFile)) {
            pw.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
            if (info == null) {
                pw.println("<vf_onselect/>");
            } else {
                pw.printf("<vf_onselect title=\"%s\" diff=\"%s\" lv=\"%s\" score=\"%d\" lamp=\"%s\" vf=\"%s\"/>%n",
                        xmlEsc(info.getTitle()),
                        xmlEsc(info.getDifficulty()),
                        xmlEsc(info.getLv()),
                        info.getBestScore(),
                        xmlEsc(info.getBestLamp()),
                        formatVf(info.getVf()));
            }
        }
        log.debug("Wrote vf_onselect.xml");
    }

    // -------------------------------------------------------------------------
    // Total VF overlay
    // -------------------------------------------------------------------------

    /**
     * Writes the total-Volforce overlay showing the top-N chart breakdown.
     *
     * @param top50      list of top-50 {@link MusicInfo} entries (sorted by VF desc)
     * @param totalVfInt sum of top-50 VF values (integer representation)
     * @param outFile    destination file
     * @throws IOException if the file cannot be written
     */
    public void writeTotalVf(List<MusicInfo> top50, int totalVfInt, File outFile) throws IOException {
        ensureParent(outFile);
        try (PrintWriter pw = writer(outFile)) {
            pw.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
            pw.printf("<total_vf value=\"%.3f\">%n", totalVfInt / 1000.0);
            for (int i = 0; i < top50.size(); i++) {
                MusicInfo m = top50.get(i);
                pw.printf("  <chart rank=\"%d\" title=\"%s\" diff=\"%s\" lv=\"%s\" score=\"%d\" lamp=\"%s\" vf=\"%s\"/>%n",
                        i + 1,
                        xmlEsc(m.getTitle()),
                        xmlEsc(m.getDifficulty()),
                        xmlEsc(m.getLv()),
                        m.getBestScore(),
                        xmlEsc(m.getBestLamp()),
                        formatVf(m.getVf()));
            }
            pw.println("</total_vf>");
        }
        log.debug("Wrote total_vf.xml ({} charts, VF={:.3f})", top50.size(), totalVfInt / 1000.0);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static String formatVf(int vfInt) {
        return String.format(Locale.ROOT, "%.1f", vfInt / 10.0);
    }

    private static String xmlEsc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private static void ensureParent(File file) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            if (!parent.mkdirs()) {
                throw new IOException("Could not create directory: " + parent.getAbsolutePath());
            }
        }
    }

    private static PrintWriter writer(File file) throws IOException {
        return new PrintWriter(file, StandardCharsets.UTF_8);
    }
}
