package com.sdvxhelper.service;

import java.io.IOException;
import java.util.List;

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
}
