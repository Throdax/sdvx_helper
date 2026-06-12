package com.sdvxhelper.app.controller.detection;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.imageio.ImageIO;

import com.sdvxhelper.model.MusicInfo;
import com.sdvxhelper.model.OnePlayData;
import com.sdvxhelper.model.WebhookConfig;
import com.sdvxhelper.network.DiscordWebhookClient;
import com.sdvxhelper.service.SdvxPlayLogService;
import com.sdvxhelper.util.LampFormatter;
import com.sdvxhelper.util.ScoreFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Dispatches Discord webhook messages for individual plays and session
 * playlists.
 *
 * <p>
 * Webhook destinations are supplied as a pre-loaded {@link List} of
 * {@link WebhookConfig} objects. Each config already encodes per-level and
 * per-lamp filter maps, so this class simply checks those maps rather than
 * parsing raw setting strings.
 * </p>
 *
 * <p>
 * The global {@code webhook_player_name} is read from the settings map and is
 * not stored inside individual configs.
 * </p>
 *
 * @author Throdax
 * @since 2.0.0
 */
public class WebhookDispatcher {

    private static final Logger log = LoggerFactory.getLogger(WebhookDispatcher.class);

    /**
     * Maps Python/Java internal lamp values (lower-case) to the canonical lamp keys
     * used in {@link WebhookConfig#isLampEnabled(String)}. Index order mirrors
     * Python's {@code LAMP_TABLE}.
     */
    private static final String[] PYTHON_LAMP_VALUES = {"puc", "uc", "exh", "hard", "clear", "failed"};
    private static final String[] LAMP_KEYS_MAP = {"PUC", "UC", "MAXXIVE", "HARD", "CLEAR", "FAILED"};

    private DiscordWebhookClient discordWebhookClient;
    private SdvxPlayLogService loggerService;
    private Map<String, String> settings;
    private List<WebhookConfig> webhookConfigs;

    /**
     * @param discordWebhookClient
     *            HTTP client for posting to Discord
     * @param loggerService
     *            used to look up song metadata (level)
     * @param settings
     *            live settings map (used for {@code webhook_player_name})
     * @param webhookConfigs
     *            loaded webhook configurations; may be {@code null} or empty
     */
    public WebhookDispatcher(DiscordWebhookClient discordWebhookClient, SdvxPlayLogService loggerService,
            Map<String, String> settings, List<WebhookConfig> webhookConfigs) {
        this.discordWebhookClient = discordWebhookClient;
        this.loggerService = loggerService;
        this.settings = settings;
        this.webhookConfigs = webhookConfigs;
    }

    /**
     * Updates the settings map (e.g. after a settings reload).
     *
     * @param settings
     *            new settings map
     */
    public void setSettings(Map<String, String> settings) {
        this.settings = settings;
    }

    /**
     * Replaces the active webhook configuration list.
     *
     * @param webhookConfigs
     *            new list of configs; may be {@code null} or empty
     */
    public void setWebhookConfigs(List<WebhookConfig> webhookConfigs) {
        this.webhookConfigs = webhookConfigs;
    }

    // -------------------------------------------------------------------------
    // Public dispatchers
    // -------------------------------------------------------------------------

    /**
     * Sends a result-notification webhook for the given play to every configured
     * destination that passes the level and lamp filters.
     *
     * @param play
     *            the recorded play
     * @param screenshot
     *            full-frame result screenshot; may be {@code null}
     */
    public void send(OnePlayData play, BufferedImage screenshot) {
        if (webhookConfigs == null || webhookConfigs.isEmpty()) {
            log.debug("send: no webhook configs loaded, skipping");
            return;
        }

        MusicInfo songInfo = loggerService.getBestFor(play.getTitle(), play.getDifficulty());
        int lv = songInfo != null ? songInfo.getLvAsInt() : -1;
        String lampKey = toLampKey(play.getLamp());
        byte[] screenshotBytes = encodeScreenshot(screenshot);

        for (WebhookConfig config : webhookConfigs) {
            if (!config.isLevelEnabled(lv)) {
                log.debug("send: webhook '{}' filtered out - level {} not enabled", config.getName(), lv);
                continue;
            }
            if (!config.isLampEnabled(lampKey)) {
                log.debug("send: webhook '{}' filtered out - lamp '{}' not enabled", config.getName(), lampKey);
                continue;
            }
            String msg = buildPlayMessage(play, lv);
            dispatchMessage(config.getUrl(), msg, config.isSendScreenshot(), screenshotBytes, play.getTitle());
        }
    }

    /**
     * Sends a session playlist summary to every webhook that has playlist sending
     * enabled.
     *
     * <p>
     * Each entry is formatted as {@code MM:SS - title} (or {@code HH:MM:SS} when
     * any timestamp is ≥ 1 hour). Entries whose timestamp is {@code null} (play
     * before OBS output started) fall back to a sequential {@code 01 - title}
     * prefix.
     * </p>
     *
     * @param sessionPlays
     *            ordered list of plays recorded this session
     * @param timestamps
     *            per-play elapsed durations since OBS output started; entries may
     *            be {@code null} when no output was active for that play
     */
    public void sendPlaylistSummary(List<OnePlayData> sessionPlays, List<Duration> timestamps) {
        if (sessionPlays.isEmpty()) {
            log.info("sendPlaylistSummary: session has no plays, skipping");
            return;
        }
        if (webhookConfigs == null || webhookConfigs.isEmpty()) {
            log.info("sendPlaylistSummary: no webhook configs loaded, skipping");
            return;
        }

        String playerName = settings.getOrDefault("webhook_player_name", "");

        for (WebhookConfig config : webhookConfigs) {
            if (!config.isSendPlaylist()) {
                log.info("sendPlaylistSummary: webhook '{}' has send_playlist=false, skipping", config.getName());
                continue;
            }
            String msg = buildPlaylistMessage(playerName, sessionPlays, timestamps);
            boolean ok = discordWebhookClient.sendMessage(config.getUrl(), msg);
            if (ok) {
                log.info("sendPlaylistSummary: sent {} entries to webhook '{}'", sessionPlays.size(), config.getName());
            } else {
                log.warn("sendPlaylistSummary: webhook '{}' returned a failure response - check URL", config.getName());
            }
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Converts a Python/Java internal lamp value to the canonical lamp key used in
     * {@link WebhookConfig}.
     *
     * @param lamp
     *            raw lamp string (e.g. {@code "clear"}, {@code "exh"})
     * @return canonical key (e.g. {@code "CLEAR"}, {@code "MAXXIVE"})
     */
    private String toLampKey(String lamp) {
        if (Objects.isNull(lamp)) {
            return "FAILED";
        }
        String lower = lamp.toLowerCase();
        for (int i = 0; i < PYTHON_LAMP_VALUES.length; i++) {
            if (PYTHON_LAMP_VALUES[i].equals(lower)) {
                return LAMP_KEYS_MAP[i];
            }
        }
        return lamp.toUpperCase();
    }

    private String buildPlayMessage(OnePlayData play, int lv) {
        String lvStr = lv >= 0 ? String.valueOf(lv) : "??";
        return String.format("**%s** (%s, Lv%s),   %s (%s),   %s", play.getTitle(), play.getDifficulty(), lvStr,
                ScoreFormatter.formatScoreBold(play.getCurScore()), ScoreFormatter.formatDiff(play.getDiff()),
                LampFormatter.formatDisplay(play.getLamp()));
    }

    private String buildPlaylistMessage(String playerName, List<OnePlayData> plays, List<Duration> timestamps) {
        boolean useHours = resolveUseHours(timestamps);
        StringBuilder msg = new StringBuilder();
        msg.append("Session playlist for ").append(playerName).append(" (").append(plays.size()).append(" songs):\n");
        for (int j = 0; j < plays.size(); j++) {
            Duration timestamp = (j < timestamps.size()) ? timestamps.get(j) : null;
            String prefix = Objects.nonNull(timestamp)
                    ? formatTimestamp(timestamp, useHours)
                    : String.format("%02d", j + 1);
            msg.append(prefix).append(" - ").append(plays.get(j).getTitle()).append("\n");
        }
        return msg.toString();
    }

    private boolean resolveUseHours(List<Duration> timestamps) {
        for (int i = timestamps.size() - 1; i >= 0; i--) {
            Duration last = timestamps.get(i);
            if (Objects.nonNull(last)) {
                return last.toHours() >= 1;
            }
        }
        return false;
    }

    private String formatTimestamp(Duration duration, boolean useHours) {
        long totalSeconds = duration.getSeconds();
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        if (useHours) {
            return String.format("%02d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format("%02d:%02d", minutes, seconds);
    }

    private void dispatchMessage(String url, String msg, boolean sendPic, byte[] screenshotBytes, String title) {
        try {
            if (sendPic && screenshotBytes != null) {
                discordWebhookClient.sendMessageWithImage(url, msg, screenshotBytes, title + ".png");
            } else {
                discordWebhookClient.sendMessage(url, msg);
            }
        } catch (IOException e) {
            log.warn("Failed to send webhook to configured URL: {}", e.getMessage());
        }
    }

    private byte[] encodeScreenshot(BufferedImage screenshot) {
        if (screenshot == null) {
            log.debug("encodeScreenshot: screenshot is null, will send text-only webhook");
            return null;
        }
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(screenshot, "png", baos);
            return baos.toByteArray();
        } catch (IOException e) {
            log.debug("Could not encode screenshot for webhook: {}", e.getMessage());
            return null;
        }
    }
}
