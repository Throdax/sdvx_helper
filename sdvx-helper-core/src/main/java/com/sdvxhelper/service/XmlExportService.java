package com.sdvxhelper.service;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;

import com.sdvxhelper.model.MusicInfo;
import com.sdvxhelper.model.OnePlayData;
import com.sdvxhelper.model.overlay.BattleOverlay;
import com.sdvxhelper.model.overlay.HistoryOverlay;
import com.sdvxhelper.model.overlay.OverlayChartEntry;
import com.sdvxhelper.model.overlay.OverlayPlayEntry;
import com.sdvxhelper.model.overlay.TotalVfOverlay;
import com.sdvxhelper.model.overlay.VfOnSelectOverlay;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Generates the OBS overlay XML files consumed by OBS browser sources.
 *
 * <p>
 * Extracts and replaces the manual XML string concatenation scattered across
 * {@code SDVXLogger.gen_history_cursong()}, {@code gen_sdvx_battle()},
 * {@code gen_vf_onselect()}, {@code gen_rival_xml()}, and related methods in
 * the Python code. All output files are written UTF-8 encoded via JAXB
 * marshalling.
 * </p>
 *
 * @author Throdax
 * @since 2.0.0
 */
public class XmlExportService {

    private static final Logger log = LoggerFactory.getLogger(XmlExportService.class);

    private Marshaller marshaller;

    /**
     * Constructs an {@code XmlExportService} and initialises the shared JAXB
     * {@link Marshaller} for all overlay POJO types.
     *
     * @throws IOException
     *             if the JAXB context cannot be created
     */
    public XmlExportService() {
        try {
            JAXBContext ctx = JAXBContext.newInstance(HistoryOverlay.class, BattleOverlay.class,
                    VfOnSelectOverlay.class, TotalVfOverlay.class);
            marshaller = ctx.createMarshaller();
            marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
            marshaller.setProperty(Marshaller.JAXB_ENCODING, "UTF-8");
        } catch (JAXBException e) {
            throw new RuntimeException("Failed to initialise JAXB context for overlay POJOs", e);
        }
    }

    // -------------------------------------------------------------------------
    // History / current-song overlay
    // -------------------------------------------------------------------------

    /**
     * Writes the play-history overlay for the currently selected song. Output:
     * {@code out/history_cursong.xml}
     *
     * @param plays
     *            all plays for the current song, most-recent first
     * @param lv
     *            chart level integer (-1 if unknown)
     * @param outFile
     *            destination file
     * @throws IOException
     *             if the file cannot be written
     */
    public void writeHistoryCurSong(List<OnePlayData> plays, int lv, File outFile) throws IOException {
        HistoryOverlay overlay = new HistoryOverlay();
        for (OnePlayData play : plays) {
            int vfInt = lv > 0 ? VolforceCalculator.computeSingleVf(play.getCurScore(), play.getLamp(), lv) : 0;

            OverlayPlayEntry entry = new OverlayPlayEntry();
            entry.setScore(play.getCurScore());
            entry.setLamp(play.getLamp());
            entry.setDiffScore(String.format(Locale.ROOT, "%+d", play.getDiff()));
            entry.setVf(formatVf(vfInt));
            entry.setDate(MusicInfo.marshalDate(play.getDate()));
            overlay.getPlays().add(entry);
        }
        marshal(overlay, outFile);
        log.debug("Wrote history_cursong.xml ({} plays)", plays.size());
    }

    // -------------------------------------------------------------------------
    // Battle overlay (today's plays)
    // -------------------------------------------------------------------------

    /**
     * Writes today's plays for the battle overlay. Output:
     * {@code out/sdvx_battle.xml}
     *
     * @param todayPlays
     *            plays recorded since the session started
     * @param outFile
     *            destination file
     * @throws IOException
     *             if the file cannot be written
     */
    public void writeSdvxBattle(List<OnePlayData> todayPlays, File outFile) throws IOException {
        BattleOverlay overlay = new BattleOverlay();
        for (OnePlayData play : todayPlays) {
            OverlayPlayEntry entry = new OverlayPlayEntry();
            entry.setTitle(play.getTitle());
            entry.setDiff(play.getDifficulty());
            entry.setScore(play.getCurScore());
            entry.setLamp(play.getLamp());
            entry.setDate(MusicInfo.marshalDate(play.getDate()));
            overlay.getPlays().add(entry);
        }
        marshal(overlay, outFile);
        log.debug("Wrote sdvx_battle.xml ({} plays)", todayPlays.size());
    }

    // -------------------------------------------------------------------------
    // VF on select overlay
    // -------------------------------------------------------------------------

    /**
     * Writes Volforce information for the song currently on the select screen.
     * Output: {@code out/vf_onselect.xml}
     *
     * @param info
     *            personal-best info for the selected chart (may be {@code null} if
     *            unknown)
     * @param outFile
     *            destination file
     * @throws IOException
     *             if the file cannot be written
     */
    public void writeVfOnSelect(MusicInfo info, File outFile) throws IOException {
        VfOnSelectOverlay overlay = new VfOnSelectOverlay();
        if (info != null) {
            overlay.setTitle(info.getTitle());
            overlay.setDiff(info.getDifficulty());
            overlay.setLv(info.getLv());
            overlay.setScore(info.getBestScore());
            overlay.setLamp(info.getBestLamp());
            overlay.setVf(formatVf(info.getVf()));
        }
        marshal(overlay, outFile);
        log.debug("Wrote vf_onselect.xml");
    }

    // -------------------------------------------------------------------------
    // Total VF overlay
    // -------------------------------------------------------------------------

    /**
     * Writes the total-Volforce overlay showing the top-N chart breakdown.
     *
     * @param top50
     *            list of top-50 {@link MusicInfo} entries (sorted by VF desc)
     * @param totalVfInt
     *            sum of top-50 VF values (integer representation)
     * @param outFile
     *            destination file
     * @throws IOException
     *             if the file cannot be written
     */
    public void writeTotalVf(List<MusicInfo> top50, int totalVfInt, File outFile) throws IOException {
        TotalVfOverlay overlay = new TotalVfOverlay();
        overlay.setValue(String.format(Locale.ROOT, "%.3f", totalVfInt / 1000.0));
        for (int i = 0; i < top50.size(); i++) {
            MusicInfo m = top50.get(i);
            OverlayChartEntry entry = new OverlayChartEntry();
            entry.setRank(i + 1);
            entry.setTitle(m.getTitle());
            entry.setDiff(m.getDifficulty());
            entry.setLv(m.getLv());
            entry.setScore(m.getBestScore());
            entry.setLamp(m.getBestLamp());
            entry.setVf(formatVf(m.getVf()));
            overlay.getCharts().add(entry);
        }
        marshal(overlay, outFile);
        log.debug("Wrote total_vf.xml ({} charts, VF={})", top50.size(), overlay.getValue());
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Marshals the given JAXB object to the specified file, creating parent
     * directories as needed.
     *
     * @param object
     *            the JAXB-annotated object to serialise
     * @param outFile
     *            the destination file
     * @throws IOException
     *             if the directory cannot be created or marshalling fails
     */
    private void marshal(Object object, File outFile) throws IOException {
        ensureParent(outFile);
        try {
            marshaller.marshal(object, outFile);
        } catch (JAXBException e) {
            throw new IOException("Failed to write overlay XML to " + outFile.getAbsolutePath(), e);
        }
    }

    /**
     * Formats an integer VF value (e.g. {@code 173}) as a one-decimal-place string
     * (e.g. {@code "17.3"}).
     *
     * @param vfInt
     *            the VF integer (value × 10)
     * @return the formatted VF string
     */
    private static String formatVf(int vfInt) {
        return String.format(Locale.ROOT, "%.1f", vfInt / 10.0);
    }

    /**
     * Creates the parent directory of the given file if it does not already exist.
     *
     * @param file
     *            the file whose parent directory must exist
     * @throws IOException
     *             if the directory cannot be created
     */
    private static void ensureParent(File file) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            if (!parent.mkdirs()) {
                throw new IOException("Could not create directory: " + parent.getAbsolutePath());
            }
        }
    }
}