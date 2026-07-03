package com.sdvxhelper.network;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Instant;
import java.util.UUID;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;

import com.sdvxhelper.model.enums.PlayState;
import com.sdvxhelper.network.ipc.IpcActivity;
import com.sdvxhelper.network.ipc.IpcAssets;
import com.sdvxhelper.network.ipc.IpcFrame;
import com.sdvxhelper.network.ipc.IpcHandshake;
import com.sdvxhelper.network.ipc.IpcSetActivityArgs;
import com.sdvxhelper.network.ipc.IpcTimestamps;
import com.sdvxhelper.util.LampFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages Discord Rich Presence state for the SDVX session via the Discord IPC
 * named-pipe protocol (Windows).
 *
 * <h2>Protocol overview</h2>
 * <p>
 * Discord exposes a local named pipe at {@code \\.\pipe\discord-ipc-N} (N =
 * 0..9, first one that exists wins). Every message is a binary frame:
 * </p>
 * 
 * <pre>
 *   [opcode : 4 bytes, little-endian int]
 *   [length : 4 bytes, little-endian int]
 *   [payload: &lt;length&gt; bytes of UTF-8 JSON]
 * </pre>
 * <p>
 * Opcodes used here:
 * </p>
 * <ul>
 * <li>{@code 0} – HANDSHAKE (sent once after opening the pipe)</li>
 * <li>{@code 1} – FRAME (normal command / response traffic)</li>
 * <li>{@code 2} – CLOSE</li>
 * </ul>
 *
 * <p>
 * Replaces the Python {@code SDVXDiscordPresence} class in
 * {@code discord_presence.py}. Presence updates are throttled to at most once
 * every 5 seconds to avoid hitting Discord rate limits.
 * </p>
 *
 * @author Throdax
 * @since 2.0.0
 */
public class DiscordPresenceClient implements Closeable {

    private static final Logger log = LoggerFactory.getLogger(DiscordPresenceClient.class);

    // -------------------------------------------------------------------------
    // IPC protocol constants
    // -------------------------------------------------------------------------

    /** Pipe name template; N is tried from 0 to {@value #PIPE_MAX_INDEX}. */
    private static final String PIPE_TEMPLATE = "\\\\.\\pipe\\discord-ipc-%d";
    private static final int PIPE_MAX_INDEX = 9;

    /** Discord IPC protocol version sent in the handshake. */
    private static final int IPC_VERSION = 1;

    private static final int OP_HANDSHAKE = 0;
    private static final int OP_FRAME = 1;
    private static final int OP_CLOSE = 2;

    /** Minimum interval between presence updates (milliseconds). */
    private static final long MIN_UPDATE_INTERVAL_MS = 5_000;

    /**
     * Activity name shown as "Playing X" in Discord when
     * {@link #STATUS_DISPLAY_NAME} is used.
     */
    private static final String ACTIVITY_NAME = "Sound Voltex Exceed Gear";

    /**
     * {@code status_display_type} value that makes Discord use the activity
     * {@code name} as the "Playing X" headline (default behaviour).
     */
    private static final int STATUS_DISPLAY_NAME = 0;

    /**
     * {@code status_display_type} value that makes Discord use the activity
     * {@code details} field (the song title) as the "Playing X" headline. Mirrors
     * the Python {@code StatusDisplayType.DETAILS} used when "song as title" is
     * enabled.
     */
    private static final int STATUS_DISPLAY_DETAILS = 2;

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------

    private final String applicationId;

    /** JSON-B serialiser used to convert IPC POJOs to JSON strings. */
    private final Jsonb jsonb = JsonbBuilder.create();

    /** Underlying Windows named pipe handle. */
    private RandomAccessFile pipe;
    private OutputStream pipeOut;
    private InputStream pipeIn;

    private volatile boolean connected = false;
    private long lastUpdateMs = 0;

    /** Epoch-second timestamp captured once when {@link #connect()} succeeds. */
    private long sessionStartTime = 0;

    /**
     * When {@code true}, the current song title is used as the Discord activity
     * {@code name} field (shown as "Playing X"), mirroring the Python
     * {@code StatusDisplayType.DETAILS} mode. Defaults to {@code false} (shows
     * {@link #ACTIVITY_NAME} instead).
     */
    private boolean songAsTitle = false;

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    /**
     * Constructs a Discord Presence client for the given Discord application.
     *
     * @param applicationId
     *            the Discord application client ID (numeric string)
     */
    public DiscordPresenceClient(String applicationId) {
        this.applicationId = applicationId;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Configures whether the current song title replaces the application name in
     * the Discord "Playing X" headline.
     *
     * <p>
     * When {@code true} the activity {@code name} field is set to the song title so
     * Discord shows "Playing &lt;song title&gt;" instead of "Playing Sound Voltex
     * Exceed Gear". Mirrors the Python {@code StatusDisplayType.DETAILS} mode in
     * {@code discord_presence.py}.
     * </p>
     *
     * @param songAsTitle
     *            {@code true} to show the song name as the Discord headline,
     *            {@code false} (default) to show the application name
     */
    public void setSongAsTitle(boolean songAsTitle) {
        this.songAsTitle = songAsTitle;
    }

    /**
     * Opens the Discord IPC named pipe and performs the protocol handshake.
     *
     * <p>
     * Tries pipe indices 0–9 in order and uses the first one that Discord has
     * created. Blocks briefly while reading the {@code READY} response from
     * Discord.
     * </p>
     *
     * @throws IOException
     *             if no Discord pipe is found or the handshake fails
     */
    public void connect() throws IOException {
        pipe = openPipe();
        pipeOut = new PipeOutputStream(pipe);
        pipeIn = new PipeInputStream(pipe);

        log.info("Opened Discord IPC pipe, performing handshake (application ID: {})", applicationId);
        sendHandshake();

        // Read and discard the READY response (opcode 1, cmd DISPATCH / event READY)
        readFrame();
        connected = true;
        sessionStartTime = Instant.now().getEpochSecond();
        log.info("Connected to Discord IPC");
    }

    /**
     * Returns {@code true} if the client is currently connected to the Discord IPC
     * pipe.
     *
     * @return {@code true} if connected, {@code false} otherwise
     */
    public boolean isConnected() {
        return connected;
    }

    /**
     * Updates the Discord Rich Presence with the given state information.
     *
     * <p>
     * Updates are silently skipped if fewer than 5 seconds have elapsed since the
     * last update, or if the client is not connected.
     * </p>
     *
     * <p>
     * Presence layout per state:
     * </p>
     * <ul>
     * <li><b>SELECTING</b> — details: {@code "Selecting Song…"}, state: VF
     * display</li>
     * <li><b>PLAYING</b> — details: song title, state: {@code "Playing: DIFF"} (no
     * level)</li>
     * </ul>
     *
     * @param state
     *            the current play state
     * @param songTitle
     *            the currently selected/playing song title (may be {@code null})
     * @param difficulty
     *            the chart difficulty string (may be {@code null})
     * @param vfDisplay
     *            the formatted Volforce string (e.g. {@code "17.255"})
     * @param jacketUrl
     *            the URL of the jacket image for the large image asset (may be
     *            {@code null})
     */
    public void updatePresence(PlayState state, String songTitle, String difficulty, String vfDisplay,
            String jacketUrl) {
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

        String details = buildDetails(state, songTitle);
        String status = buildState(state, difficulty, vfDisplay);

        log.debug("updatePresence: state={}, details='{}', status='{}'", state, details, status);

        IpcAssets assets = (jacketUrl != null && !jacketUrl.isBlank())
                ? new IpcAssets(jacketUrl, songTitle != null ? songTitle : "")
                : new IpcAssets("sdvx_logo", "SDVX Exceed Gear");

        // The activity "name" is always the app name; Discord ignores it for the
        // headline unless status_display_type = 0. To show the song as the headline we
        // keep the title in "details" and switch status_display_type to DETAILS,
        // mirroring the Python StatusDisplayType.DETAILS approach.
        int statusDisplayType = songAsTitle ? STATUS_DISPLAY_DETAILS : STATUS_DISPLAY_NAME;

        IpcActivity activity = new IpcActivity(ACTIVITY_NAME, details, status, new IpcTimestamps(sessionStartTime),
                assets, statusDisplayType);

        String payload = jsonb.toJson(new IpcFrame("SET_ACTIVITY",
                new IpcSetActivityArgs(ProcessHandle.current().pid(), activity), UUID.randomUUID().toString()));

        try {
            sendFrame(OP_FRAME, payload);
            readFrame(); // consume Discord's ACK
        } catch (IOException e) {
            log.warn("Failed to send Rich Presence update - marking as disconnected", e);
            connected = false;
        }
    }

    /**
     * Updates Discord Rich Presence for the result screen.
     *
     * <p>
     * Presence layout: details shows the song title; state shows
     * {@code "Result: DIFF | LAMP | XXXX"} where {@code XXXX} is the abbreviated
     * score (leading significant digits only). No level and no score difference are
     * shown.
     * </p>
     *
     * @param title
     *            the song title displayed on the result screen
     * @param difficulty
     *            the chart difficulty (e.g. {@code "nov"})
     * @param level
     *            the chart level (unused in the displayed text; kept for signature
     *            compatibility)
     * @param score
     *            the score achieved on this play
     * @param scoreDiff
     *            {@code curScore - preScore} (unused in the displayed text; kept
     *            for signature compatibility)
     * @param lamp
     *            the result lamp (e.g. {@code "exh"} → displayed as
     *            {@code "MAXXIVE"})
     * @param jacketUrl
     *            optional jacket image URL; {@code null} falls back to default
     *            asset
     */
    public void updatePresenceResult(String title, String difficulty, int level, int score, int scoreDiff, String lamp,
            String jacketUrl) {
        if (!connected) {
            log.debug("updatePresenceResult: not connected, skipping");
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastUpdateMs < MIN_UPDATE_INTERVAL_MS) {
            log.debug("updatePresenceResult: throttled ({}ms since last update)", now - lastUpdateMs);
            return;
        }
        lastUpdateMs = now;

        String diffDisplay = difficulty != null ? difficulty.toUpperCase() : "??";
        String lampDisplay = LampFormatter.formatDisplay(lamp);
        String details = title != null ? truncate(title, 50) : "Unknown";
        String status = "Result: " + diffDisplay + " | " + lampDisplay + " | " + formatResultScore(score);

        log.debug("updatePresenceResult: details='{}', status='{}'", details, status);

        IpcAssets assets = (jacketUrl != null && !jacketUrl.isBlank())
                ? new IpcAssets(jacketUrl, title != null ? title : "")
                : new IpcAssets("sdvx_logo", "SDVX Exceed Gear");

        // Keep name = app name and drive the "song as title" headline via
        // status_display_type = DETAILS (the title is already in "details").
        int statusDisplayType = songAsTitle ? STATUS_DISPLAY_DETAILS : STATUS_DISPLAY_NAME;

        IpcActivity activity = new IpcActivity(ACTIVITY_NAME, details, status, new IpcTimestamps(sessionStartTime),
                assets, statusDisplayType);

        String payload = jsonb.toJson(new IpcFrame("SET_ACTIVITY",
                new IpcSetActivityArgs(ProcessHandle.current().pid(), activity), UUID.randomUUID().toString()));

        try {
            sendFrame(OP_FRAME, payload);
            readFrame();
        } catch (IOException e) {
            log.warn("Failed to send Rich Presence result update - marking as disconnected", e);
            connected = false;
        }
    }

    /**
     * Clears the Discord Rich Presence, removing all displayed fields.
     */
    public void clearPresence() {
        if (!connected) {
            log.debug("clearPresence: not connected to Discord, skipping");
            return;
        }
        log.debug("clearPresence");
        String payload = jsonb.toJson(new IpcFrame("SET_ACTIVITY",
                new IpcSetActivityArgs(ProcessHandle.current().pid(), null), UUID.randomUUID().toString()));
        try {
            sendFrame(OP_FRAME, payload);
            readFrame();
        } catch (IOException e) {
            log.warn("Failed to clear Rich Presence", e);
            connected = false;
        }
    }

    /**
     * Clears the presence, sends a CLOSE frame, and closes the named pipe.
     */
    @Override
    public void close() {
        if (!connected) {
            log.debug("close: not connected to Discord, nothing to close");
            return;
        }
        log.info("Disconnecting from Discord IPC");
        clearPresence();
        try {
            sendFrame(OP_CLOSE, "{}");
        } catch (IOException ignored) {
            // Best-effort close
        }
        connected = false;
        closePipe();
    }

    // -------------------------------------------------------------------------
    // IPC – pipe helpers
    // -------------------------------------------------------------------------

    /**
     * Tries each pipe index 0–{@value #PIPE_MAX_INDEX} and returns the first one
     * that Discord has created.
     *
     * @return an open {@link RandomAccessFile} handle to the pipe
     * @throws IOException
     *             if no Discord IPC pipe is available
     */
    private RandomAccessFile openPipe() throws IOException {
        for (int i = 0; i <= PIPE_MAX_INDEX; i++) {
            String pipeName = String.format(PIPE_TEMPLATE, i);
            try {
                RandomAccessFile raf = new RandomAccessFile(pipeName, "rw");
                log.debug("Opened pipe: {}", pipeName);
                return raf;
            } catch (IOException e) {
                log.trace("Pipe {} not available: {}", pipeName, e.getMessage());
            }
        }
        throw new IOException("No Discord IPC pipe found (is Discord running?)");
    }

    /**
     * Closes the underlying pipe handle silently.
     */
    private void closePipe() {
        if (pipe != null) {
            try {
                pipe.close();
            } catch (IOException e) {
                log.trace("Error closing pipe", e);
            }
            pipe = null;
        }
    }

    // -------------------------------------------------------------------------
    // IPC – frame I/O
    // -------------------------------------------------------------------------

    /**
     * Sends the protocol HANDSHAKE frame ({@code opcode = 0}).
     *
     * @throws IOException
     *             if the frame cannot be written to the pipe
     */
    private void sendHandshake() throws IOException {
        String json = jsonb.toJson(new IpcHandshake(IPC_VERSION, applicationId));
        sendFrame(OP_HANDSHAKE, json);
    }

    /**
     * Writes a single IPC frame to the pipe.
     *
     * <p>
     * Frame layout (all little-endian):
     * </p>
     * 
     * <pre>
     *   [4 bytes] opcode
     *   [4 bytes] payload length
     *   [N bytes] UTF-8 JSON payload
     * </pre>
     *
     * @param opcode
     *            the IPC opcode
     * @param payload
     *            the JSON string payload
     * @throws IOException
     *             if the write fails
     */
    private void sendFrame(int opcode, String payload) throws IOException {
        byte[] data = payload.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] header = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putInt(opcode).putInt(data.length)
                .array();
        pipeOut.write(header);
        pipeOut.write(data);
        pipeOut.flush();
        log.trace("-> op={} len={} payload={}", opcode, data.length, payload);
    }

    /**
     * Reads and returns one IPC frame from the pipe, discarding the payload.
     *
     * @return the JSON payload string of the received frame
     * @throws IOException
     *             if the read fails or the pipe is closed
     */
    private String readFrame() throws IOException {
        byte[] header = pipeIn.readNBytes(8);
        if (header.length < 8) {
            throw new IOException("Discord IPC pipe closed unexpectedly");
        }
        ByteBuffer buf = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
        int opcode = buf.getInt();
        int length = buf.getInt();
        byte[] data = length > 0 ? pipeIn.readNBytes(length) : new byte[0];
        String json = new String(data, java.nio.charset.StandardCharsets.UTF_8);
        log.trace("<- op={} len={} payload={}", opcode, length, json);
        return json;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Builds the Rich Presence {@code details} line (first visible line) from the
     * current play state.
     *
     * <ul>
     * <li>{@code SELECTING} — {@code "Selecting Song..."}</li>
     * <li>{@code PLAYING} — the song title, or {@code "Unknown"} when absent</li>
     * <li>{@code RESULT} — {@code "Viewing results"}</li>
     * <li>{@code IDLE} — {@code "Idle"}</li>
     * </ul>
     *
     * @param state
     *            the current play state
     * @param title
     *            the song title (may be {@code null})
     * @return the formatted details string
     */
    private static String buildDetails(PlayState state, String title) {
        return switch (state) {
            case SELECTING -> "Selecting Song...";
            case PLAYING -> title != null && !title.isBlank() ? truncate(title, 50) : "Unknown";
            case RESULT -> "Viewing results";
            case IDLE -> "Idle";
        };
    }

    /**
     * Builds the Rich Presence {@code state} line (second visible line) from the
     * current play state.
     *
     * <ul>
     * <li>{@code SELECTING} — {@code "VF: <vfDisplay>"}</li>
     * <li>{@code PLAYING} — {@code "Playing: DIFF"} (no level)</li>
     * <li>Other states — {@code "VF: <vfDisplay>"}</li>
     * </ul>
     *
     * @param state
     *            the current play state
     * @param difficulty
     *            the chart difficulty string (may be {@code null})
     * @param vfDisplay
     *            the formatted Volforce string
     * @return the formatted state string
     */
    private static String buildState(PlayState state, String difficulty, String vfDisplay) {
        if (state == PlayState.PLAYING) {
            String diffDisplay = difficulty != null && !difficulty.isBlank() ? difficulty.toUpperCase() : "??";
            return "Playing: " + diffDisplay;
        }
        return "VF: " + vfDisplay;
    }

    /**
     * Abbreviates a raw SDVX score to its leading significant digits, matching
     * Python's {@code formated_score} logic in {@code discord_presence.py:171}.
     *
     * <ul>
     * <li>8-digit score → first 4 digits (e.g. {@code 99580000} →
     * {@code "9958"})</li>
     * <li>7-digit score → first 3 digits</li>
     * <li>6-digit score → first 2 digits</li>
     * <li>5-digit score → first 1 digit</li>
     * <li>otherwise → full string</li>
     * </ul>
     *
     * @param score
     *            raw integer score
     * @return abbreviated display string
     */
    private static String formatResultScore(int score) {
        String str = String.valueOf(score);
        int len = str.length();
        if (len == 8) {
            return str.substring(0, 4);
        }
        if (len == 7) {
            return str.substring(0, 3);
        }
        if (len == 6) {
            return str.substring(0, 2);
        }
        if (len == 5) {
            return str.substring(0, 1);
        }
        return str;
    }

    /**
     * Truncates a string to at most {@code maxLen} characters, appending {@code …}
     * if truncated.
     *
     * @param s
     *            the string to truncate
     * @param maxLen
     *            the maximum allowed length
     * @return the (possibly truncated) string
     */
    private static String truncate(String s, int maxLen) {
        return s.length() <= maxLen ? s : s.substring(0, maxLen - 1) + "…";
    }

    // -------------------------------------------------------------------------
    // Pipe stream adapters
    // -------------------------------------------------------------------------

    /**
     * {@link OutputStream} adapter backed by a {@link RandomAccessFile} pipe
     * handle.
     */
    private static final class PipeOutputStream extends OutputStream {
        private final RandomAccessFile raf;

        private PipeOutputStream(RandomAccessFile raf) {
            this.raf = raf;
        }

        @Override
        public void write(int b) throws IOException {
            raf.write(b);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            raf.write(b, off, len);
        }
    }

    /**
     * {@link InputStream} adapter backed by a {@link RandomAccessFile} pipe handle.
     */
    private static final class PipeInputStream extends InputStream {
        private final RandomAccessFile raf;

        private PipeInputStream(RandomAccessFile raf) {
            this.raf = raf;
        }

        @Override
        public int read() throws IOException {
            return raf.read();
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            return raf.read(b, off, len);
        }
    }
}