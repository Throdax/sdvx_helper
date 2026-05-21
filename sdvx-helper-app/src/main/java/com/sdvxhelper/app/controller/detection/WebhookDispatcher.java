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
import com.sdvxhelper.network.DiscordWebhookClient;
import com.sdvxhelper.service.SdvxLoggerService;
import com.sdvxhelper.util.ScoreFormatter;
import com.sdvxhelper.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Dispatches Discord webhook messages for individual plays and session
 * playlists. Respects per-webhook level and lamp filter settings.
 *
 * @author Throdax
 * @since 2.0.0
 */
public class WebhookDispatcher {

    private static final Logger log = LoggerFactory.getLogger(WebhookDispatcher.class);

    private static final String[] LAMP_TABLE = {"puc", "uc", "exh", "hard", "clear", "failed", ""};

    private DiscordWebhookClient discordWebhookClient;
    private SdvxLoggerService loggerService;
    private Map<String, String> settings;

    /**
     * @param discordWebhookClient
     *            HTTP client for posting to Discord
     * @param loggerService
     *            logger service used to look up song metadata
     * @param settings
     *            live settings map
     */
    public WebhookDispatcher(DiscordWebhookClient discordWebhookClient, SdvxLoggerService loggerService,
            Map<String, String> settings) {
        this.discordWebhookClient = discordWebhookClient;
        this.loggerService = loggerService;
        this.settings = settings;
    }

    public void setSettings(Map<String, String> settings) {
        this.settings = settings;
    }

    // -------------------------------------------------------------------------
    // Public dispatchers
    // -------------------------------------------------------------------------

    /**
     * Sends a custom webhook for the given play result, respecting per-webhook
     * level and lamp filters. Attaches a screenshot when configured.
     *
     * @param play
     *            the recorded play
     * @param screenshot
     *            full-frame result screenshot, may be {@code null}
     */
    public void send(OnePlayData play, BufferedImage screenshot) {
        List<String> names = StringUtils.parseListSetting(settings.getOrDefault("webhook_names", "[]"));
        List<String> urls = StringUtils.parseListSetting(settings.getOrDefault("webhook_urls", "[]"));
        if (names.isEmpty() || urls.isEmpty()) {
            log.debug("send: no webhooks configured (names={}, urls={}), skipping", names.size(), urls.size());
            return;
        }
        List<String> enablePics = StringUtils.parseListSetting(settings.getOrDefault("webhook_enable_pics", "[]"));
        List<String> enableLvs = StringUtils.parseListSetting(settings.getOrDefault("webhook_enable_lvs", "[]"));
        List<String> enableLamps = StringUtils.parseListSetting(settings.getOrDefault("webhook_enable_lamps", "[]"));

        int lampIdx = findLampIndex(play.getLamp());
        MusicInfo songInfo = loggerService.getBestFor(play.getTitle(), play.getDifficulty());
        int lv = songInfo != null ? songInfo.getLvAsInt() : -1;
        byte[] screenshotBytes = encodeScreenshot(screenshot);

        for (int i = 0; i < Math.min(names.size(), urls.size()); i++) {
            boolean sendFlag = checkLevelFilter(lv, i, enableLvs);
            sendFlag = sendFlag && checkLampFilter(lampIdx, i, enableLamps);
            if (!sendFlag) {
                continue;
            }
            String url = urls.get(i);
            String msg = buildPlayMessage(play, lv);
            boolean sendPic = i < enablePics.size() && Boolean.parseBoolean(enablePics.get(i));
            dispatchMessage(url, msg, sendPic, screenshotBytes, play.getTitle());
        }
    }

    /**
     * Sends a session playlist summary message to all webhooks that have playlist
     * sending enabled.
     *
     * <p>
     * When a non-null {@link Duration} is present at the same index as a play in
     * {@code timestamps}, the entry is formatted as {@code MM:SS - title}. Entries
     * without a timestamp (e.g. plays recorded before OBS output started) fall back
     * to a sequential number prefix.
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
            log.debug("sendPlaylistSummary: session has no plays, skipping");
            return;
        }
        List<String> names = StringUtils.parseListSetting(settings.getOrDefault("webhook_names", "[]"));
        List<String> urls = StringUtils.parseListSetting(settings.getOrDefault("webhook_urls", "[]"));
        List<String> playlistFlags = StringUtils.parseListSetting(settings.getOrDefault("webhook_playlist", "[]"));
        String playerName = settings.getOrDefault("webhook_player_name", "");

        for (int i = 0; i < Math.min(names.size(), urls.size()); i++) {
            boolean sendPlaylist = i < playlistFlags.size() && Boolean.parseBoolean(playlistFlags.get(i));
            if (!sendPlaylist) {
                continue;
            }
            String msg = buildPlaylistMessage(playerName, sessionPlays, timestamps);
            discordWebhookClient.sendMessage(urls.get(i), msg);
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private String buildPlayMessage(OnePlayData play, int lv) {
        String lvStr = lv >= 0 ? String.valueOf(lv) : "??";
        return String.format("**%s** (%s, Lv%s),   %s,   %s", play.getTitle(), play.getDifficulty(), lvStr,
                ScoreFormatter.formatScore(play.getCurScore()), play.getLamp());
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
            log.warn("Failed to send webhook to {}: {}", url, e.getMessage());
        }
    }

    private int findLampIndex(String lamp) {
        for (int k = 0; k < LAMP_TABLE.length; k++) {
            if (LAMP_TABLE[k].equalsIgnoreCase(lamp)) {
                return k;
            }
        }
        return LAMP_TABLE.length - 1;
    }

    private boolean checkLevelFilter(int lv, int i, List<String> enableLvs) {
        if (lv < 1 || i >= enableLvs.size()) {
            return true;
        }
        List<String> lvFlags = StringUtils.parseListSetting(enableLvs.get(i));
        if (lv - 1 >= lvFlags.size()) {
            return true;
        }
        return Boolean.parseBoolean(lvFlags.get(lv - 1));
    }

    private boolean checkLampFilter(int lampIdx, int i, List<String> enableLamps) {
        if (i >= enableLamps.size()) {
            return true;
        }
        List<String> lampFlags = StringUtils.parseListSetting(enableLamps.get(i));
        if (lampIdx >= lampFlags.size()) {
            return false;
        }
        return Boolean.parseBoolean(lampFlags.get(lampIdx));
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
