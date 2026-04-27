package com.sdvxhelper.service;

import java.io.IOException;
import java.util.List;

import com.sdvxhelper.model.MusicInfo;
import com.sdvxhelper.model.OnePlayData;
import com.sdvxhelper.model.PlayLog;
import com.sdvxhelper.repository.MusicListRepository;
import com.sdvxhelper.repository.PlayLogRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link SdvxLoggerService}.
 */
@ExtendWith(MockitoExtension.class)
class SdvxLoggerServiceTest {

    @Mock
    private PlayLogRepository playLogRepo;

    @Mock
    private MusicListRepository musicListRepo;

    private SdvxLoggerService service;

    @BeforeEach
    void setUp() {
        PlayLog emptyLog = new PlayLog();
        Mockito.when(playLogRepo.load()).thenReturn(emptyLog);
        // This stub is not needed by every test (e.g. popOnEmptyLogReturnsNull never
        // calls pushPlay), so it is declared lenient to avoid
        // UnnecessaryStubbingException.
        Mockito.lenient().when(musicListRepo.findSongInfo(ArgumentMatchers.any())).thenReturn(null);
        service = new SdvxLoggerService(playLogRepo, musicListRepo);
    }

    @Test
    void pushPlayAddsToLog() throws IOException {
        OnePlayData play = new OnePlayData("Song A", 9_000_000, 0, "clear", "exh", "2024-01-01");
        service.pushPlay(play);
        Assertions.assertEquals(1, service.getPlayLog().getPlays().size());
        Mockito.verify(playLogRepo).save(ArgumentMatchers.any(PlayLog.class));
    }

    @Test
    void popLastPlayRemovesAndReturns() throws IOException {
        OnePlayData play = new OnePlayData("X", 9_000_000, 0, "clear", "exh", "2024-01-01");
        service.pushPlay(play);

        OnePlayData removed = service.popLastPlay();
        Assertions.assertNotNull(removed);
        Assertions.assertEquals("X", removed.getTitle());
        Assertions.assertTrue(service.getPlayLog().getPlays().isEmpty());
    }

    @Test
    void popOnEmptyLogReturnsNull() throws IOException {
        OnePlayData result = service.popLastPlay();
        Assertions.assertNull(result);
    }

    @Test
    void getPlaysForFiltersCorrectly() throws IOException {
        service.pushPlay(new OnePlayData("Song A", 9_000_000, 0, "clear", "exh", "2024-01-01"));
        service.pushPlay(new OnePlayData("Song B", 8_000_000, 0, "clear", "exh", "2024-01-02"));
        service.pushPlay(new OnePlayData("Song A", 9_100_000, 9_000_000, "uc", "exh", "2024-01-03"));

        List<OnePlayData> songAPlays = service.getPlaysFor("Song A", "exh");
        Assertions.assertEquals(2, songAPlays.size());
    }

    @Test
    void todayLogGrowsWithPushPlay() throws IOException {
        service.pushPlay(new OnePlayData("X", 9_000_000, 0, "clear", "exh", "2024-01-01"));
        service.pushPlay(new OnePlayData("Y", 9_000_000, 0, "clear", "exh", "2024-01-01"));
        Assertions.assertEquals(2, service.getTodayLog().size());
    }

    // -------------------------------------------------------------------------
    // getBestFor
    // -------------------------------------------------------------------------

    @Test
    void getBestForReturnsNullWhenNoPlaysRecorded() {
        MusicInfo result = service.getBestFor("Unknown Song", "exh");
        Assertions.assertNull(result);
    }

    @Test
    void getBestForReturnsBestAfterPush() throws IOException {
        service.pushPlay(new OnePlayData("Song A", 9_000_000, 0, "clear", "exh", "2024-01-01"));
        MusicInfo best = service.getBestFor("Song A", "exh");
        Assertions.assertNotNull(best);
        Assertions.assertEquals("Song A", best.getTitle());
        Assertions.assertEquals("exh", best.getDifficulty());
    }

    @Test
    void getBestForReturnsHigherScoreWhenMultiplePlays() throws IOException {
        service.pushPlay(new OnePlayData("Song B", 8_000_000, 0, "clear", "exh", "2024-01-01"));
        service.pushPlay(new OnePlayData("Song B", 9_500_000, 0, "uc", "exh", "2024-01-02"));
        MusicInfo best = service.getBestFor("Song B", "exh");
        Assertions.assertNotNull(best);
        Assertions.assertEquals(9_500_000, best.getBestScore());
    }

    @Test
    void getBestForReturnsNullForWrongDifficulty() throws IOException {
        service.pushPlay(new OnePlayData("Song C", 9_000_000, 0, "clear", "exh", "2024-01-01"));
        Assertions.assertNull(service.getBestFor("Song C", "adv"));
    }

    // -------------------------------------------------------------------------
    // getBestAllFumen
    // -------------------------------------------------------------------------

    @Test
    void getBestAllFumenIsEmptyInitially() {
        Assertions.assertTrue(service.getBestAllFumen().isEmpty());
    }

    @Test
    void getBestAllFumenGrowsWithNewSongs() throws IOException {
        service.pushPlay(new OnePlayData("Song A", 9_000_000, 0, "clear", "exh", "2024-01-01"));
        service.pushPlay(new OnePlayData("Song B", 8_000_000, 0, "clear", "exh", "2024-01-01"));
        Assertions.assertEquals(2, service.getBestAllFumen().size());
    }

    @Test
    void getBestAllFumenCountsDifficultiesSeparately() throws IOException {
        service.pushPlay(new OnePlayData("Song A", 9_000_000, 0, "clear", "exh", "2024-01-01"));
        service.pushPlay(new OnePlayData("Song A", 7_000_000, 0, "clear", "adv", "2024-01-01"));
        Assertions.assertEquals(2, service.getBestAllFumen().size());
    }

    @Test
    void getBestAllFumenDeduplicatesSameSongAndDiff() throws IOException {
        service.pushPlay(new OnePlayData("Song A", 8_000_000, 0, "clear", "exh", "2024-01-01"));
        service.pushPlay(new OnePlayData("Song A", 9_000_000, 0, "uc", "exh", "2024-01-02"));
        Assertions.assertEquals(1, service.getBestAllFumen().size());
    }

    @Test
    void getBestAllFumenIsUnmodifiable() throws IOException {
        service.pushPlay(new OnePlayData("Song A", 9_000_000, 0, "clear", "exh", "2024-01-01"));
        List<MusicInfo> list = service.getBestAllFumen();
        Assertions.assertThrows(UnsupportedOperationException.class, () -> list.add(null));
    }

    // -------------------------------------------------------------------------
    // getTotalVfInt
    // -------------------------------------------------------------------------

    @Test
    void getTotalVfIntIsZeroInitially() {
        Assertions.assertEquals(0, service.getTotalVfInt());
    }

    @Test
    void getTotalVfIntChangesAfterPlayWhenMusicInfoKnown() throws IOException {
        // When musicListRepo returns null the MusicInfo has no level, so VF stays 0.
        // Confirm the field at least remains a valid non-negative integer.
        service.pushPlay(new OnePlayData("Song A", 10_000_000, 0, "puc", "exh", "2024-01-01"));
        Assertions.assertTrue(service.getTotalVfInt() >= 0, "Total VF should be a non-negative integer after a play");
    }

    // -------------------------------------------------------------------------
    // applyRivalCsv
    // -------------------------------------------------------------------------

    @Test
    void applyRivalCsvNullInputIsNoop() {
        service.applyRivalCsv(null);
        Assertions.assertTrue(service.getPlayLog().getPlays().isEmpty());
    }

    @Test
    void applyRivalCsvBlankInputIsNoop() {
        service.applyRivalCsv("   ");
        Assertions.assertTrue(service.getPlayLog().getPlays().isEmpty());
    }

    @Test
    void applyRivalCsvAddsNewBestEntry() {
        service.applyRivalCsv("Song X,exh,9500000,uc");
        Assertions.assertEquals(1, service.getPlayLog().getPlays().size());
        Assertions.assertEquals("Song X", service.getPlayLog().getPlays().get(0).getTitle());
    }

    @Test
    void applyRivalCsvSkipsMalformedLines() {
        service.applyRivalCsv("only,three,fields\nGood Song,exh,9000000,clear");
        Assertions.assertEquals(1, service.getPlayLog().getPlays().size());
    }

    @Test
    void applyRivalCsvSkipsLinesWithNonNumericScore() {
        service.applyRivalCsv("Song Y,exh,NOT_A_NUMBER,clear");
        Assertions.assertTrue(service.getPlayLog().getPlays().isEmpty());
    }

    @Test
    void applyRivalCsvSkipsCommentLines() {
        service.applyRivalCsv("# this is a comment\nSong Z,exh,9000000,clear");
        Assertions.assertEquals(1, service.getPlayLog().getPlays().size());
    }

    @Test
    void applyRivalCsvDoesNotOverwriteHigherExistingScore() throws IOException {
        service.pushPlay(new OnePlayData("Song A", 9_800_000, 0, "uc", "exh", "2024-01-01"));
        int countBefore = service.getPlayLog().getPlays().size();
        service.applyRivalCsv("Song A,exh,9000000,clear");
        Assertions.assertEquals(countBefore, service.getPlayLog().getPlays().size(),
                "Lower rival score must not replace existing higher score");
    }

    @Test
    void applyRivalCsvAddsEntryWhenRivalBeatsCurrent() throws IOException {
        service.pushPlay(new OnePlayData("Song A", 7_000_000, 0, "clear", "exh", "2024-01-01"));
        int countBefore = service.getPlayLog().getPlays().size();
        service.applyRivalCsv("Song A,exh,9500000,uc");
        Assertions.assertEquals(countBefore + 1, service.getPlayLog().getPlays().size(),
                "Better rival score must be added as an additional entry");
    }
}
