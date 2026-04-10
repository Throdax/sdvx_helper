package com.sdvxhelper.network;

import com.sdvxhelper.model.enums.PlayState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.time.Instant;

/**
 * Manages Discord Rich Presence state for the SDVX session.
 *
 * <p>Replaces the Python {@code SDVXDiscordPresence} class in
 * {@code discord_presence.py}.  Communicates with the Discord client via the
 * Discord IPC protocol (Windows named pipe / Unix socket).
 *
 * <p>Presence updates are throttled to at most once every 5 seconds to avoid
 * hitting Discord rate limits.</p>
 *
 * @author sdvx-helper
 * @since 2.0.0
 */
public class DiscordPresenceClient implements Closeable {

    private static final Logger log = LoggerFactory.getLogger(DiscordPresenceClient.class);

    /** Minimum interval between presence updates (milliseconds). */
    private static final long MIN_UPDATE_INTERVAL_MS = 5_000;

    private final String applicationId;
    private volatile boolean connected = false;
    private long lastUpdateMs = 0;

    /**
     * Constructs a Discord Presence client.
     *
     * @param applicationId Discord application client ID
     */
    public DiscordPresenceClient(String applicationId) {
        this.applicationId = applicationId;
    }

    /**
     * Connects to the Discord IPC socket and initialises the presence session.
     *
     * @throws Exception if the connection cannot be established
     */
    public void connect() throws Exception {
        log.info("Connecting to Discord IPC (application ID: {})", applicationId);
        // TODO: Use com.jagrosh:DiscordIPC or hand-rolled Windows named pipe
        // IPCClient client = new IPCClient(Long.parseLong(applicationId));
        // client.connect();
        connected = true;
        log.info("Connected to Discord IPC");
    }

    /**
     * Returns {@code true} if the client is currently connected.
     *
     * @return connection status
     */
    public boolean isConnected() {
        return connected;
    }

    /**
     * Updates the Discord Rich Presence with the given state information.
     *
     * <p>Updates are silently skipped if fewer than 5 seconds have elapsed since
     * the last update.</p>
     *
     * @param state      current play state
     * @param songTitle  currently selected/playing song title (may be {@code null})
     * @param difficulty chart difficulty string (may be {@code null})
     * @param vfDisplay  formatted Volforce string (e.g. {@code "17.255"})
     * @param jacketUrl  URL of the jacket image for the large image asset (may be {@code null})
     */
    public void updatePresence(PlayState state, String songTitle, String difficulty,
                               String vfDisplay, String jacketUrl) {
        if (!connected) {
            log.debug("updatePresence: not connected, skipping");
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastUpdateMs < MIN_UPDATE_INTERVAL_MS) {
            log.debug("updatePresence: throttled ({}ms since last update)", now - lastUpdateMs);
            return;
        }
        lastUpdateMs = now;

        String details = buildDetails(state, songTitle, difficulty);
        String status  = "VF: " + vfDisplay;
        long startTime = Instant.now().getEpochSecond();

        log.debug("updatePresence: state={}, details='{}', status='{}'", state, details, status);

        // TODO: Build RichPresence and call client.sendRichPresence(presence)
    }

    /**
     * Clears the Discord Rich Presence (removes all fields).
     */
    public void clearPresence() {
        if (!connected) return;
        log.debug("clearPresence");
        // TODO: client.sendRichPresence(null) or send empty presence
    }

    /**
     * Disconnects from Discord IPC and cleans up resources.
     */
    @Override
    public void close() {
        if (connected) {
            log.info("Disconnecting from Discord IPC");
            clearPresence();
            // TODO: client.close();
            connected = false;
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static String buildDetails(PlayState state, String title, String difficulty) {
        return switch (state) {
            case SELECTING -> "Selecting" + (title != null ? ": " + truncate(title, 50) : "");
            case PLAYING   -> "Playing" + (title != null ? ": " + truncate(title, 50) : "")
                              + (difficulty != null ? " [" + difficulty.toUpperCase() + "]" : "");
            case RESULT    -> "Viewing results";
            case IDLE      -> "Idle";
        };
    }

    private static String truncate(String s, int maxLen) {
        return s.length() <= maxLen ? s : s.substring(0, maxLen - 1) + "…";
    }
}
