package com.sdvxhelper.app.controller.detection;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.sdvxhelper.model.OnePlayData;
import com.sdvxhelper.network.DiscordWebhookClient;
import com.sdvxhelper.service.SdvxLoggerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Unit tests for {@link WebhookDispatcher}.
 * <p>
 * {@link DiscordWebhookClient} and {@link SdvxLoggerService} are mocked so no
 * real network calls are made. All filter logic is exercised by varying the
 * settings map.
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class WebhookDispatcherTest {

    @Mock
    private DiscordWebhookClient discordClient;

    @Mock
    private SdvxLoggerService loggerService;

    private Map<String, String> settings;
    private WebhookDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        settings = new HashMap<>();
        dispatcher = new WebhookDispatcher(discordClient, loggerService, settings);
    }

    // -------------------------------------------------------------------------
    // send — no webhooks configured
    // -------------------------------------------------------------------------

    @Test
    void sendSkipsWhenNoWebhooksConfigured() {
        OnePlayData play = new OnePlayData("Song", 9_000_000, 0, "clear", "exh", "2024-01-01");
        assertDoesNotThrow(() -> dispatcher.send(play, null));
        Mockito.verifyNoInteractions(discordClient);
    }

    @Test
    void sendSkipsWhenNamesConfiguredButNoUrls() {
        settings.put("webhook_names", "['Hook1']");
        OnePlayData play = new OnePlayData("Song", 9_000_000, 0, "clear", "exh", "2024-01-01");
        assertDoesNotThrow(() -> dispatcher.send(play, null));
        Mockito.verifyNoInteractions(discordClient);
    }

    @Test
    void sendSkipsWhenUrlsConfiguredButNoNames() {
        settings.put("webhook_urls", "['https://discord.com/api/webhooks/test']");
        OnePlayData play = new OnePlayData("Song", 9_000_000, 0, "clear", "exh", "2024-01-01");
        assertDoesNotThrow(() -> dispatcher.send(play, null));
        Mockito.verifyNoInteractions(discordClient);
    }

    // -------------------------------------------------------------------------
    // send — webhook fires with default (pass-through) filters
    // -------------------------------------------------------------------------

    @Test
    void sendCallsWebhookWhenConfiguredAndNoFilterSet() throws IOException {
        settings.put("webhook_names", "['Hook1']");
        settings.put("webhook_urls", "['https://discord.com/api/webhooks/test']");
        Mockito.when(loggerService.getBestFor(ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(null);

        OnePlayData play = new OnePlayData("Song", 9_000_000, 0, "clear", "exh", "2024-01-01");
        dispatcher.send(play, null);

        Mockito.verify(discordClient).sendMessage(ArgumentMatchers.eq("https://discord.com/api/webhooks/test"),
                ArgumentMatchers.anyString());
    }

    // -------------------------------------------------------------------------
    // send — lamp filter logic
    // -------------------------------------------------------------------------

    @Test
    void sendSkipsWhenLampFilterExcludesCurrentLamp() throws IOException {
        settings.put("webhook_names", "['Hook1']");
        settings.put("webhook_urls", "['https://discord.com/api/webhooks/test']");
        // LAMP_TABLE = {"puc"(0), "uc"(1), "exh"(2), "hard"(3), "clear"(4),
        // "failed"(5), ""(6)}
        // Lamp index for "clear" is 4. The lamp-flag list must have index 4 = false.
        // Provide 5 flags, index 4 set to false, rest true.
        settings.put("webhook_enable_lamps", "[\"['true','true','true','true','false']\"]");
        Mockito.when(loggerService.getBestFor(ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(null);

        OnePlayData play = new OnePlayData("Song", 9_000_000, 0, "clear", "exh", "2024-01-01");
        dispatcher.send(play, null);

        Mockito.verifyNoInteractions(discordClient);
    }

    @Test
    void sendPassesWhenLampFilterAllowsCurrentLamp() throws IOException {
        settings.put("webhook_names", "['Hook1']");
        settings.put("webhook_urls", "['https://discord.com/api/webhooks/test']");
        // Lamp "puc" is index 0 — set index 0 to true
        settings.put("webhook_enable_lamps", "[\"['true']\"]");
        Mockito.when(loggerService.getBestFor(ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(null);

        OnePlayData play = new OnePlayData("Song", 10_000_000, 0, "puc", "exh", "2024-01-01");
        dispatcher.send(play, null);

        Mockito.verify(discordClient).sendMessage(ArgumentMatchers.anyString(), ArgumentMatchers.anyString());
    }

    // -------------------------------------------------------------------------
    // sendPlaylistSummary — empty session
    // -------------------------------------------------------------------------

    @Test
    void sendPlaylistSummarySkipsEmptySession() {
        assertDoesNotThrow(() -> dispatcher.sendPlaylistSummary(List.of(), List.of()));
        Mockito.verifyNoInteractions(discordClient);
    }

    // -------------------------------------------------------------------------
    // sendPlaylistSummary — no webhooks with playlist enabled
    // -------------------------------------------------------------------------

    @Test
    void sendPlaylistSummarySkipsWhenPlaylistFlagFalse() {
        settings.put("webhook_names", "['Hook1']");
        settings.put("webhook_urls", "['https://discord.com/api/webhooks/test']");
        settings.put("webhook_playlist", "['false']");
        settings.put("webhook_player_name", "Throdax");

        OnePlayData play = new OnePlayData("Song", 9_000_000, 0, "clear", "exh", "2024-01-01");
        dispatcher.sendPlaylistSummary(List.of(play), List.of());

        Mockito.verifyNoInteractions(discordClient);
    }

    @Test
    void sendPlaylistSummaryCallsWebhookWhenFlagTrue() {
        settings.put("webhook_names", "['Hook1']");
        settings.put("webhook_urls", "['https://discord.com/api/webhooks/test']");
        settings.put("webhook_playlist", "['true']");
        settings.put("webhook_player_name", "Player1");

        OnePlayData play = new OnePlayData("Song", 9_000_000, 0, "clear", "exh", "2024-01-01");
        dispatcher.sendPlaylistSummary(List.of(play), List.of());

        Mockito.verify(discordClient).sendMessage(ArgumentMatchers.eq("https://discord.com/api/webhooks/test"),
                ArgumentMatchers.contains("Song"));
    }

    // -------------------------------------------------------------------------
    // sendPlaylistSummary — timestamp formatting
    // -------------------------------------------------------------------------

    @Test
    void sendPlaylistSummaryFormatsTimestampWhenPresent() {
        settings.put("webhook_names", "['Hook1']");
        settings.put("webhook_urls", "['https://discord.com/api/webhooks/test']");
        settings.put("webhook_playlist", "['true']");
        settings.put("webhook_player_name", "Player1");

        OnePlayData play = new OnePlayData("My Song", 9_000_000, 0, "clear", "exh", "2024-01-01");
        List<Duration> timestamps = new ArrayList<>();
        timestamps.add(Duration.ofSeconds(112));

        dispatcher.sendPlaylistSummary(List.of(play), timestamps);

        Mockito.verify(discordClient).sendMessage(ArgumentMatchers.anyString(),
                ArgumentMatchers.contains("01:52 - My Song"));
    }

    @Test
    void sendPlaylistSummaryUsesNumberedPrefixWhenTimestampIsNull() {
        settings.put("webhook_names", "['Hook1']");
        settings.put("webhook_urls", "['https://discord.com/api/webhooks/test']");
        settings.put("webhook_playlist", "['true']");
        settings.put("webhook_player_name", "Player1");

        OnePlayData play = new OnePlayData("My Song", 9_000_000, 0, "clear", "exh", "2024-01-01");
        List<Duration> timestamps = new ArrayList<>();
        timestamps.add(null);

        dispatcher.sendPlaylistSummary(List.of(play), timestamps);

        Mockito.verify(discordClient).sendMessage(ArgumentMatchers.anyString(),
                ArgumentMatchers.contains("01 - My Song"));
    }

    @Test
    void sendPlaylistSummaryUsesNumberedPrefixWhenTimestampsListEmpty() {
        settings.put("webhook_names", "['Hook1']");
        settings.put("webhook_urls", "['https://discord.com/api/webhooks/test']");
        settings.put("webhook_playlist", "['true']");
        settings.put("webhook_player_name", "Player1");

        OnePlayData play = new OnePlayData("My Song", 9_000_000, 0, "clear", "exh", "2024-01-01");

        dispatcher.sendPlaylistSummary(List.of(play), Collections.emptyList());

        Mockito.verify(discordClient).sendMessage(ArgumentMatchers.anyString(),
                ArgumentMatchers.contains("01 - My Song"));
    }

    @Test
    void sendPlaylistSummaryMixesTimestampAndNumberedPrefixes() {
        settings.put("webhook_names", "['Hook1']");
        settings.put("webhook_urls", "['https://discord.com/api/webhooks/test']");
        settings.put("webhook_playlist", "['true']");
        settings.put("webhook_player_name", "Player1");

        OnePlayData play1 = new OnePlayData("Song A", 9_000_000, 0, "clear", "exh", "2024-01-01");
        OnePlayData play2 = new OnePlayData("Song B", 9_000_000, 0, "clear", "exh", "2024-01-01");
        List<Duration> timestamps = new ArrayList<>();
        timestamps.add(Duration.ofSeconds(217));
        timestamps.add(null);

        dispatcher.sendPlaylistSummary(List.of(play1, play2), timestamps);

        Mockito.verify(discordClient).sendMessage(ArgumentMatchers.anyString(),
                ArgumentMatchers.argThat(msg -> msg.contains("03:37 - Song A") && msg.contains("02 - Song B")));
    }

    @Test
    void sendPlaylistSummaryUsesHhMmSsWhenLastTimestampExceedsOneHour() {
        settings.put("webhook_names", "['Hook1']");
        settings.put("webhook_urls", "['https://discord.com/api/webhooks/test']");
        settings.put("webhook_playlist", "['true']");
        settings.put("webhook_player_name", "Player1");

        OnePlayData play1 = new OnePlayData("Song A", 9_000_000, 0, "clear", "exh", "2024-01-01");
        OnePlayData play2 = new OnePlayData("Song B", 9_000_000, 0, "clear", "exh", "2024-01-01");
        List<Duration> timestamps = new ArrayList<>();
        timestamps.add(Duration.ofSeconds(112));
        timestamps.add(Duration.ofSeconds(3720));

        dispatcher.sendPlaylistSummary(List.of(play1, play2), timestamps);

        Mockito.verify(discordClient).sendMessage(ArgumentMatchers.anyString(), ArgumentMatchers
                .argThat(msg -> msg.contains("00:01:52 - Song A") && msg.contains("01:02:00 - Song B")));
    }

    @Test
    void sendPlaylistSummaryUsesMmSsWhenLastTimestampBelowOneHour() {
        settings.put("webhook_names", "['Hook1']");
        settings.put("webhook_urls", "['https://discord.com/api/webhooks/test']");
        settings.put("webhook_playlist", "['true']");
        settings.put("webhook_player_name", "Player1");

        OnePlayData play1 = new OnePlayData("Song A", 9_000_000, 0, "clear", "exh", "2024-01-01");
        OnePlayData play2 = new OnePlayData("Song B", 9_000_000, 0, "clear", "exh", "2024-01-01");
        List<Duration> timestamps = new ArrayList<>();
        timestamps.add(Duration.ofSeconds(112));
        timestamps.add(Duration.ofSeconds(217));

        dispatcher.sendPlaylistSummary(List.of(play1, play2), timestamps);

        Mockito.verify(discordClient).sendMessage(ArgumentMatchers.anyString(),
                ArgumentMatchers.argThat(msg -> msg.contains("01:52 - Song A") && msg.contains("03:37 - Song B")));
    }

    @Test
    void sendPlaylistSummaryUsesHhMmSsForEarlierSongsWhenLastExceedsOneHour() {
        settings.put("webhook_names", "['Hook1']");
        settings.put("webhook_urls", "['https://discord.com/api/webhooks/test']");
        settings.put("webhook_playlist", "['true']");
        settings.put("webhook_player_name", "Player1");

        OnePlayData play1 = new OnePlayData("Song A", 9_000_000, 0, "clear", "exh", "2024-01-01");
        OnePlayData play2 = new OnePlayData("Song B", 9_000_000, 0, "clear", "exh", "2024-01-01");
        List<Duration> timestamps = new ArrayList<>();
        timestamps.add(null);
        timestamps.add(Duration.ofSeconds(3661));

        dispatcher.sendPlaylistSummary(List.of(play1, play2), timestamps);

        Mockito.verify(discordClient).sendMessage(ArgumentMatchers.anyString(),
                ArgumentMatchers.argThat(msg -> msg.contains("01 - Song A") && msg.contains("01:01:01 - Song B")));
    }

    // -------------------------------------------------------------------------
    // setSettings
    // -------------------------------------------------------------------------

    @Test
    void setSettingsUpdatesInternalMap() throws IOException {
        Map<String, String> newSettings = new HashMap<>();
        newSettings.put("webhook_names", "['Updated']");
        newSettings.put("webhook_urls", "['https://discord.com/api/webhooks/new']");
        Mockito.when(loggerService.getBestFor(ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(null);

        dispatcher.setSettings(newSettings);
        OnePlayData play = new OnePlayData("Song", 9_000_000, 0, "clear", "exh", "2024-01-01");
        dispatcher.send(play, null);

        Mockito.verify(discordClient).sendMessage(ArgumentMatchers.eq("https://discord.com/api/webhooks/new"),
                ArgumentMatchers.anyString());
    }

    // -------------------------------------------------------------------------
    // send — multiple webhooks, only applicable ones fire
    // -------------------------------------------------------------------------

    @Test
    void sendFiresOnlyEnabledWebhooks() throws IOException {
        settings.put("webhook_names", "['A', 'B']");
        settings.put("webhook_urls", "['https://hook-a', 'https://hook-b']");
        // Lamp "puc" = index 0. Hook A allows index 0 (true), Hook B does not (false).
        settings.put("webhook_enable_lamps", "[\"['true']\", \"['false']\"]");
        Mockito.when(loggerService.getBestFor(ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(null);

        OnePlayData play = new OnePlayData("Song", 10_000_000, 0, "puc", "exh", "2024-01-01");
        dispatcher.send(play, null);

        Mockito.verify(discordClient).sendMessage(ArgumentMatchers.eq("https://hook-a"), ArgumentMatchers.anyString());
        Mockito.verify(discordClient, Mockito.never()).sendMessage(ArgumentMatchers.eq("https://hook-b"),
                ArgumentMatchers.anyString());
    }
}
