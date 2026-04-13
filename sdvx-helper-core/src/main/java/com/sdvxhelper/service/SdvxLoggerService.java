package com.sdvxhelper.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.sdvxhelper.model.MusicInfo;
import com.sdvxhelper.model.MusicInfoBuilder;
import com.sdvxhelper.model.OnePlayData;
import com.sdvxhelper.model.PlayLog;
import com.sdvxhelper.model.SongInfo;
import com.sdvxhelper.model.Stats;
import com.sdvxhelper.repository.MusicListRepository;
import com.sdvxhelper.repository.PlayLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Central orchestrator for recording and analysing SDVX play history.
 *
 * <p>
 * Decomposes the Python {@code SDVXLogger} "god class" into focused
 * responsibilities: this service handles play-log push/pop and best-score
 * computation, while VF math is delegated to {@link VolforceCalculator}, XML
 * output to {@link XmlExportService}, and CSV output to
 * {@link CsvExportService}.
 * </p>
 *
 * <p>
 * All dependencies are supplied via constructor injection for testability.
 * </p>
 *
 * @author Throdax
 * @since 2.0.0
 */
public class SdvxLoggerService {

    private static final Logger log = LoggerFactory.getLogger(SdvxLoggerService.class);

    private final PlayLogRepository playLogRepo;
    private final MusicListRepository musicListRepo;

    private PlayLog playLog;
    private List<MusicInfo> bestAllFumen = new ArrayList<>();
    private List<OnePlayData> todayLog = new ArrayList<>();
    private int totalVfInt = 0;
    private Stats stats = new Stats();

    /**
     * Constructs the service and loads existing play-log data.
     *
     * @param playLogRepo
     *            repository for the play-log XML file
     * @param musicListRepo
     *            repository for the music-list XML file
     */
    public SdvxLoggerService(PlayLogRepository playLogRepo, MusicListRepository musicListRepo) {
        this.playLogRepo = playLogRepo;
        this.musicListRepo = musicListRepo;
        this.playLog = playLogRepo.load();
        refreshBestAndVf();
    }

    // -------------------------------------------------------------------------
    // Play-log mutation
    // -------------------------------------------------------------------------

    /**
     * Records a new play and persists it to disk.
     *
     * <p>
     * Updates the best-score list and total VF after recording.
     * </p>
     *
     * @param play
     *            newly completed play record
     * @throws IOException
     *             if the updated play log cannot be persisted
     */
    public void pushPlay(OnePlayData play) throws IOException {
        playLog.getPlays().add(play);
        todayLog.add(play);
        playLogRepo.save(playLog);
        refreshBestAndVf();
        log.info("Recorded play: {}", play);
    }

    /**
     * Removes the most recently added play record (undo / illegal-entry removal).
     *
     * @return the removed play, or {@code null} if the log is empty
     * @throws IOException
     *             if the updated play log cannot be persisted
     */
    public OnePlayData popLastPlay() throws IOException {
        List<OnePlayData> plays = playLog.getPlays();
        if (plays.isEmpty()) {
            log.warn("popLastPlay() called on empty play log");
            return null;
        }
        OnePlayData removed = plays.remove(plays.size() - 1);
        todayLog.remove(removed);
        playLogRepo.save(playLog);
        refreshBestAndVf();
        log.info("Removed last play: {}", removed);
        return removed;
    }

    // -------------------------------------------------------------------------
    // Best-score and VF computation
    // -------------------------------------------------------------------------

    /**
     * Rebuilds the best-score list and total VF from the current play log. Called
     * automatically after any mutation; can also be called explicitly after loading
     * a new music list.
     */
    public void refreshBestAndVf() {
        if (musicListRepo == null)
            return;

        // Rebuild best per (title, difficulty) from play log
        java.util.Map<String, OnePlayData> bestMap = new java.util.LinkedHashMap<>();
        for (OnePlayData p : playLog.getPlays()) {
            String key = p.getTitle() + "___" + p.getDifficulty();
            OnePlayData existing = bestMap.get(key);
            if (existing == null || p.getCurScore() > existing.getCurScore()) {
                bestMap.put(key, p);
            }
        }

        // Convert to MusicInfo list with VF populated
        bestAllFumen = new ArrayList<>();
        for (OnePlayData best : bestMap.values()) {
            MusicInfo info = musicListRepo.findSongInfo(best.getTitle()) != null
                    ? toMusicInfo(best, musicListRepo)
                    : toMusicInfoUnknown(best);
            VolforceCalculator.computeAndSet(info);
            bestAllFumen.add(info);
        }
        Collections.sort(bestAllFumen); // VF descending

        // Compute total VF (top-50 × 10, sum × 10 = integer VF × 100)
        int sum = 0;
        int count = Math.min(VolforceCalculator.TOP_N, bestAllFumen.size());
        for (int i = 0; i < count; i++) {
            sum += bestAllFumen.get(i).getVf();
        }
        totalVfInt = sum * 10; // store as VF_total × 1000 for 3 decimal display

        // Rebuild stats
        stats = new Stats();
        for (MusicInfo m : bestAllFumen) {
            stats.readAll(m);
        }

        log.debug("Refreshed: {} best charts, total VF = {}", bestAllFumen.size(), totalVfInt / 1000.0);
    }

    // -------------------------------------------------------------------------
    // Query methods
    // -------------------------------------------------------------------------

    /**
     * Returns all plays for the given song title and difficulty, most-recent last.
     *
     * @param title
     *            song title
     * @param difficulty
     *            chart difficulty string
     * @return matching play records
     */
    public List<OnePlayData> getPlaysFor(String title, String difficulty) {
        List<OnePlayData> result = new ArrayList<>();
        for (OnePlayData p : playLog.getPlays()) {
            if (title.equals(p.getTitle()) && difficulty.equals(p.getDifficulty())) {
                result.add(p);
            }
        }
        return result;
    }

    /**
     * Returns the personal-best {@link MusicInfo} for the given title and
     * difficulty, or {@code null} if no play has been recorded.
     *
     * @param title
     *            song title
     * @param difficulty
     *            chart difficulty string
     * @return personal-best info or {@code null}
     */
    public MusicInfo getBestFor(String title, String difficulty) {
        for (MusicInfo m : bestAllFumen) {
            if (title.equals(m.getTitle()) && difficulty.equals(m.getDifficulty())) {
                return m;
            }
        }
        return null;
    }

    /**
     * Returns the top-N best charts sorted by VF descending.
     *
     * @return unmodifiable view of the best-chart list
     */
    public List<MusicInfo> getBestAllFumen() {
        return Collections.unmodifiableList(bestAllFumen);
    }

    /**
     * Returns the total Volforce as an integer × 1000 (e.g. {@code 17255} = 17.255
     * VF).
     *
     * @return total VF integer
     */
    public int getTotalVfInt() {
        return totalVfInt;
    }

    /**
     * Returns the session statistics.
     *
     * @return current {@link Stats}
     */
    public Stats getStats() {
        return stats;
    }

    /**
     * Returns the plays recorded in the current session (today's plays).
     *
     * @return today's play list
     */
    public List<OnePlayData> getTodayLog() {
        return Collections.unmodifiableList(todayLog);
    }

    /**
     * Returns the full play log.
     *
     * @return play log
     */
    public PlayLog getPlayLog() {
        return playLog;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Converts a best play record to a {@link MusicInfo} with metadata from the
     * music list.
     * 
     * @param best
     *            best play record to convert
     * @param repo
     *            music list repository for metadata lookup
     * @return converted MusicInfo with metadata and VF unset (caller should compute
     *         VF after conversion)
     */
    private static MusicInfo toMusicInfo(OnePlayData best, MusicListRepository repo) {
        SongInfo songInfo = repo.findSongInfo(best.getTitle());
        if (songInfo == null) {
            throw new IllegalArgumentException(
                    "No SongInfo found for title '" + best.getTitle() + "' — use toMusicInfoUnknown() instead");
        }

        String level = resolveLevel(best.getDifficulty(), songInfo);
        return new MusicInfoBuilder(best.getTitle()).artist(songInfo.getArtist()).bpm(songInfo.getBpm())
                .difficulty(best.getDifficulty()).lv(level).bestScore(best.getCurScore()).bestLamp(best.getLamp())
                .date(best.getDate()).build();
    }

    /**
     * Converts a best play record to a {@link MusicInfo} without metadata (for
     * titles not found in the music list).
     * 
     * @param best
     *            best play record to convert
     * @return converted MusicInfo with only title, difficulty, score, lamp, and
     *         date set; VF unset (caller should compute VF after conversion)
     */
    private static MusicInfo toMusicInfoUnknown(OnePlayData best) {
        return new MusicInfoBuilder(best.getTitle()).difficulty(best.getDifficulty()).bestScore(best.getCurScore())
                .bestLamp(best.getLamp()).date(best.getDate()).build();
    }

    /**
     * Resolves the chart level string for the given difficulty from the song info.
     * 
     * @param difficulty
     *            chart difficulty string
     * @param songInfo
     *            song info to resolve from
     * @return level string or "??" if not found
     */
    private static String resolveLevel(String difficulty, SongInfo songInfo) {

        if (songInfo == null) {
            return "??";
        }

        return switch (difficulty.toLowerCase()) {
            case "nov" -> songInfo.getLvNov();
            case "adv" -> songInfo.getLvAdv();
            case "exh" -> songInfo.getLvExh();
            default -> songInfo.getLvAppend() != null ? songInfo.getLvAppend() : "??";
        };
    }
}
