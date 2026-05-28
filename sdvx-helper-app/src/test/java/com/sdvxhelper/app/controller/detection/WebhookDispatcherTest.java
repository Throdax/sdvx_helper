package com.sdvxhelper.app.controller.detection;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.sdvxhelper.model.OnePlayData;
import com.sdvxhelper.model.WebhookConfig;
import com.sdvxhelper.model.WebhookConfigBuilder;
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
 *
 * <p>
 * {@link DiscordWebhookClient} and {@link SdvxLoggerService} are mocked so no
 * real network calls are made. Webhook configurations are built via
 * {@link WebhookConfigBuilder} and supplied directly to the dispatcher.
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class WebhookDispatcherTest {

    private static final String HOOK_URL = "https://discord.com/api/webhooks/test";

    @Mock
    private DiscordWebhookClient discordClient;

    @Mock
    private SdvxLoggerService loggerService;

    private Map<String, String> settings;
    private WebhookDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        settings = new HashMap<>();
        dispatcher = new WebhookDispatcher(discordClient, loggerService, settings, null);
    }

    // -------------------------------------------------------------------------
    // send — no webhook configs
    // -------------------------------------------------------------------------

    @Test
    void sendSkipsWhenWebhookConfigsIsNull() {
        OnePlayData play = new OnePlayData("Song", 9_000_000, 0, "clear", "exh", "2024-01-01");
        assertDoesNotThrow(() -> dispatcher.send(play, null));
        Mockito.verifyNoInteractions(discordClient);
    }

    @Test
    void sendSkipsWhenWebhookConfigsIsEmpty() {
        dispatcher.setWebhookConfigs(Collections.emptyList());
        OnePlayData play = new OnePlayData("Song", 9_000_000, 0, "clear", "exh", "2024-01-01");
        assertDoesNotThrow(() -> dispatcher.send(play, null));
        Mockito.verifyNoInteractions(discordClient);
    }

    // -------------------------------------------------------------------------
    // send — webhook fires with default (pass-through) filters
    // -------------------------------------------------------------------------

    @Test
    void sendCallsWebhookWhenConfiguredAndAllFiltersEnabled() throws IOException {
        WebhookConfig config = new WebhookConfigBuilder().name("Hook1").url(HOOK_URL).build();
        dispatcher.setWebhookConfigs(List.of(config));
        Mockito.when(loggerService.getBestFor(ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(null);

        OnePlayData play = new OnePlayData("Song", 9_000_000, 0, "clear", "exh", "2024-01-01");
        dispatcher.send(play, null);

        Mockito.verify(discordClient).sendMessage(ArgumentMatchers.eq(HOOK_URL), ArgumentMatchers.anyString());
    }

    // -------------------------------------------------------------------------
    // send — lamp filter logic
    // -------------------------------------------------------------------------

    @Test
    void sendSkipsWhenLampFilterExcludesCurrentLamp() {
        WebhookConfig config = new WebhookConfigBuilder().name("Hook1").url(HOOK_URL).enabledLamp("CLEAR", false)
                .build();
        dispatcher.setWebhookConfigs(List.of(config));
        Mockito.when(loggerService.getBestFor(ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(null);

        OnePlayData play = new OnePlayData("Song", 9_000_000, 0, "clear", "exh", "2024-01-01");
        dispatcher.send(play, null);

        Mockito.verifyNoInteractions(discordClient);
    }

    @Test
    void sendPassesWhenLampFilterAllowsCurrentLamp() throws IOException {
        WebhookConfig config = new WebhookConfigBuilder().name("Hook1").url(HOOK_URL).enabledLamp("PUC", true).build();
        dispatcher.setWebhookConfigs(List.of(config));
        Mockito.when(loggerService.getBestFor(ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(null);

        OnePlayData play = new OnePlayData("Song", 10_000_000, 0, "puc", "exh", "2024-01-01");
        dispatcher.send(play, null);

        Mockito.verify(discordClient).sendMessage(ArgumentMatchers.anyString(), ArgumentMatchers.anyString());
    }

    @Test
    void sendMapsExhLampToMaxxiveKey() throws IOException {
        WebhookConfig config = new WebhookConfigBuilder().name("Hook1").url(HOOK_URL).enabledLamp("MAXXIVE", true)
                .build();
        dispatcher.setWebhookConfigs(List.of(config));
        Mockito.when(loggerService.getBestFor(ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(null);

        OnePlayData play = new OnePlayData("Song", 9_800_000, 0, "exh", "exh", "2024-01-01");
        dispatcher.send(play, null);

        Mockito.verify(discordClient).sendMessage(ArgumentMatchers.anyString(), ArgumentMatchers.anyString());
    }

    // -------------------------------------------------------------------------
    // send — result message content
    // -------------------------------------------------------------------------

    @Test
    void sendMessageContainsScoreAndDiffInSdvxFormat() throws IOException {
        WebhookConfig config = new WebhookConfigBuilder().name("Hook1").url(HOOK_URL).build();
        dispatcher.setWebhookConfigs(List.of(config));
        Mockito.when(loggerService.getBestFor(ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(null);

        // curScore=9_000_000 → "**900**,0000" diff=+1_000_000 → "+100,0000"
        OnePlayData play = new OnePlayData("Song", 9_000_000, 8_000_000, "clear", "exh", "2024-01-01");
        dispatcher.send(play, null);

        Mockito.verify(discordClient).sendMessage(ArgumentMatchers.anyString(),
                ArgumentMatchers.argThat(msg -> msg.contains("**900**,0000") && msg.contains("+100,0000")));
    }

    @Test
    void sendMessageShowsNegativeDiffWhenScoreDropped() throws IOException {
        WebhookConfig config = new WebhookConfigBuilder().name("Hook1").url(HOOK_URL).build();
        dispatcher.setWebhookConfigs(List.of(config));
        Mockito.when(loggerService.getBestFor(ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(null);

        // curScore=393_081, preScore=9_583_334 → diff=-9_190_253 → "-919,0253"
        OnePlayData play = new OnePlayData("Song", 393_081, 9_583_334, "failed", "exh", "2024-01-01");
        dispatcher.send(play, null);

        Mockito.verify(discordClient).sendMessage(ArgumentMatchers.anyString(),
                ArgumentMatchers.argThat(msg -> msg.contains("**39**,3081") && msg.contains("-919,0253")));
    }

    @Test
    void sendMessageShowsDiffEqualToScoreWhenPreScoreIsZero() throws IOException {
        WebhookConfig config = new WebhookConfigBuilder().name("Hook1").url(HOOK_URL).build();
        dispatcher.setWebhookConfigs(List.of(config));
        Mockito.when(loggerService.getBestFor(ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(null);

        // curScore=393_081, preScore=0 → diff=+393_081 → "+39,3081"
        OnePlayData play = new OnePlayData("Unknown", 393_081, 0, "failed", "exh", "2024-01-01");
        dispatcher.send(play, null);

        Mockito.verify(discordClient).sendMessage(ArgumentMatchers.anyString(),
                ArgumentMatchers.argThat(msg -> msg.contains("**39**,3081") && msg.contains("+39,3081")));
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
    // sendPlaylistSummary — playlist flag
    // -------------------------------------------------------------------------

    @Test
    void sendPlaylistSummarySkipsWhenPlaylistFlagFalse() {
        WebhookConfig config = new WebhookConfigBuilder().name("Hook1").url(HOOK_URL).sendPlaylist(false).build();
        dispatcher.setWebhookConfigs(List.of(config));
        settings.put("webhook_player_name", "Throdax");

        OnePlayData play = new OnePlayData("Song", 9_000_000, 0, "clear", "exh", "2024-01-01");
        dispatcher.sendPlaylistSummary(List.of(play), List.of());

        Mockito.verifyNoInteractions(discordClient);
    }

    @Test
    void sendPlaylistSummaryCallsWebhookWhenFlagTrue() {
        WebhookConfig config = new WebhookConfigBuilder().name("Hook1").url(HOOK_URL).sendPlaylist(true).build();
        dispatcher.setWebhookConfigs(List.of(config));
        settings.put("webhook_player_name", "Player1");

        OnePlayData play = new OnePlayData("Song", 9_000_000, 0, "clear", "exh", "2024-01-01");
        dispatcher.sendPlaylistSummary(List.of(play), List.of());

        Mockito.verify(discordClient).sendMessage(ArgumentMatchers.eq(HOOK_URL), ArgumentMatchers.contains("Song"));
    }

    // -------------------------------------------------------------------------
    // sendPlaylistSummary — timestamp formatting
    // -------------------------------------------------------------------------

    @Test
    void sendPlaylistSummaryFormatsTimestampWhenPresent() {
        WebhookConfig config = new WebhookConfigBuilder().name("Hook1").url(HOOK_URL).sendPlaylist(true).build();
        dispatcher.setWebhookConfigs(List.of(config));
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
        WebhookConfig config = new WebhookConfigBuilder().name("Hook1").url(HOOK_URL).sendPlaylist(true).build();
        dispatcher.setWebhookConfigs(List.of(config));
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
        WebhookConfig config = new WebhookConfigBuilder().name("Hook1").url(HOOK_URL).sendPlaylist(true).build();
        dispatcher.setWebhookConfigs(List.of(config));
        settings.put("webhook_player_name", "Player1");

        OnePlayData play = new OnePlayData("My Song", 9_000_000, 0, "clear", "exh", "2024-01-01");

        dispatcher.sendPlaylistSummary(List.of(play), Collections.emptyList());

        Mockito.verify(discordClient).sendMessage(ArgumentMatchers.anyString(),
                ArgumentMatchers.contains("01 - My Song"));
    }

    @Test
    void sendPlaylistSummaryMixesTimestampAndNumberedPrefixes() {
        WebhookConfig config = new WebhookConfigBuilder().name("Hook1").url(HOOK_URL).sendPlaylist(true).build();
        dispatcher.setWebhookConfigs(List.of(config));
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
        WebhookConfig config = new WebhookConfigBuilder().name("Hook1").url(HOOK_URL).sendPlaylist(true).build();
        dispatcher.setWebhookConfigs(List.of(config));
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
        WebhookConfig config = new WebhookConfigBuilder().name("Hook1").url(HOOK_URL).sendPlaylist(true).build();
        dispatcher.setWebhookConfigs(List.of(config));
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
        WebhookConfig config = new WebhookConfigBuilder().name("Hook1").url(HOOK_URL).sendPlaylist(true).build();
        dispatcher.setWebhookConfigs(List.of(config));
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
    // setWebhookConfigs — dynamic update
    // -------------------------------------------------------------------------

    @Test
    void setWebhookConfigsUpdatesActiveConfigs() throws IOException {
        dispatcher.setWebhookConfigs(Collections.emptyList());
        Mockito.when(loggerService.getBestFor(ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(null);

        WebhookConfig newConfig = new WebhookConfigBuilder().name("Updated").url("https://discord.com/api/webhooks/new")
                .build();
        dispatcher.setWebhookConfigs(List.of(newConfig));

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
        WebhookConfig hookA = new WebhookConfigBuilder().name("A").url("https://hook-a").enabledLamp("PUC", true)
                .build();
        WebhookConfig hookB = new WebhookConfigBuilder().name("B").url("https://hook-b").enabledLamp("PUC", false)
                .build();
        dispatcher.setWebhookConfigs(List.of(hookA, hookB));
        Mockito.when(loggerService.getBestFor(ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(null);

        OnePlayData play = new OnePlayData("Song", 10_000_000, 0, "puc", "exh", "2024-01-01");
        dispatcher.send(play, null);

        Mockito.verify(discordClient).sendMessage(ArgumentMatchers.eq("https://hook-a"), ArgumentMatchers.anyString());
        Mockito.verify(discordClient, Mockito.never()).sendMessage(ArgumentMatchers.eq("https://hook-b"),
                ArgumentMatchers.anyString());
    }

    @Test
    void sendSkipsWhenNoWebhookConfigsAndPlaylistNotCalled() {
        OnePlayData play = new OnePlayData("Song", 9_000_000, 0, "clear", "exh", "2024-01-01");
        assertDoesNotThrow(() -> dispatcher.sendPlaylistSummary(List.of(play), List.of()));
        Mockito.verifyNoInteractions(discordClient);
    }
}
