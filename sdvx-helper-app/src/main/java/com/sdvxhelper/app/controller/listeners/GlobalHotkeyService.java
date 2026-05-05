package com.sdvxhelper.app.controller.listeners;

import java.util.LinkedHashMap;
import java.util.Map;
import javafx.application.Platform;

import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Registers application-wide global hotkeys (F4–F9) using the Win32
 * {@code RegisterHotKey} API via JNA.
 *
 * <h2>Why not JNativeHook?</h2>
 * <p>
 * JNativeHook installs a low-level {@code WH_KEYBOARD_LL} system hook that
 * intercepts every keystroke on the OS. This generates hundreds of internal
 * exceptions per second under a debugger, causing the IDE to crawl.
 * {@code RegisterHotKey} fires only for the specific key combinations that were
 * registered and generates no such noise.
 * </p>
 *
 * <h2>OS guard</h2>
 * <p>
 * {@code RegisterHotKey} is a Windows-only API. On non-Windows systems this
 * service logs a warning and becomes a no-op; no JNA call is ever made.
 * </p>
 *
 * <h2>Threading model</h2>
 * <p>
 * {@code RegisterHotKey} binds hotkeys to the calling thread's message queue.
 * This service starts a dedicated daemon thread ({@code hotkey-pump}) that
 * registers all keys, then blocks in a Win32 {@code GetMessage} loop. When
 * {@link #stop()} is called, a {@code WM_QUIT} message is posted to that thread
 * so it exits cleanly and unregisters all hotkeys.
 * </p>
 *
 * @author Throdax
 * @since 2.0.0
 */
public class GlobalHotkeyService {

    private static final Logger log = LoggerFactory.getLogger(GlobalHotkeyService.class);

    // -------------------------------------------------------------------------
    // Win32 constants
    // -------------------------------------------------------------------------

    private static final int WM_HOTKEY = 0x0312;
    private static final int WM_QUIT = 0x0012;
    private static final int MOD_NONE = 0x0000;

    // -------------------------------------------------------------------------
    // Virtual-key codes exposed so callers can build the action map by name
    // -------------------------------------------------------------------------

    /** Virtual-key code for the F4 key. */
    public static final int VK_F4 = 0x73;
    /** Virtual-key code for the F5 key. */
    public static final int VK_F5 = 0x74;
    /** Virtual-key code for the F6 key. */
    public static final int VK_F6 = 0x75;
    /** Virtual-key code for the F7 key. */
    public static final int VK_F7 = 0x76;
    /** Virtual-key code for the F8 key. */
    public static final int VK_F8 = 0x77;
    /** Virtual-key code for the F9 key. */
    public static final int VK_F9 = 0x78;

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------

    /** Win32 thread ID of the message-pump thread; 0 when not running. */
    private volatile int pumpThreadId = 0;

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Starts a background message-pump thread and registers the supplied hotkeys
     * with the Win32 {@code RegisterHotKey} API.
     *
     * <p>
     * Each entry in {@code vkToAction} maps a virtual-key code (e.g.
     * {@link #VK_F4}) to the {@link Runnable} to invoke on the JavaFX application
     * thread when that key is pressed.
     * </p>
     *
     * <p>
     * On non-Windows systems this method logs a warning and returns immediately
     * without making any Win32 call.
     * </p>
     *
     * @param vkToAction
     *            ordered map of VK code → action; insertion order determines the
     *            hotkey IDs assigned by Windows
     */
    public void start(Map<Integer, Runnable> vkToAction) {
        if (!isWindows()) {
            log.warn(
                    "Global hotkeys are only supported on Windows; hotkeys will not be registered on this OS (os.name={})",
                    System.getProperty("os.name"));
            return;
        }
        Map<Integer, Runnable> snapshot = new LinkedHashMap<>(vkToAction);
        Thread pumpThread = new Thread(() -> runMessagePump(snapshot), "hotkey-pump");
        pumpThread.setDaemon(true);
        pumpThread.start();
    }

    /**
     * Posts a {@code WM_QUIT} message to the pump thread, causing it to exit the
     * {@code GetMessage} loop, unregister all hotkeys, and terminate.
     *
     * <p>
     * Safe to call even when the service was not started (e.g. on non-Windows
     * systems).
     * </p>
     */
    public void stop() {
        int threadId = pumpThreadId;
        if (threadId == 0) {
            return;
        }
        User32.INSTANCE.PostThreadMessage(threadId, WM_QUIT, new WinDef.WPARAM(0), new WinDef.LPARAM(0));
    }

    // -------------------------------------------------------------------------
    // Private implementation
    // -------------------------------------------------------------------------

    /**
     * Runs on the {@code hotkey-pump} daemon thread.
     *
     * <p>
     * Registers all hotkeys, blocks in {@code GetMessage}, dispatches
     * {@code WM_HOTKEY} events to the JavaFX thread, and unregisters hotkeys when
     * the loop exits (via {@code WM_QUIT}).
     * </p>
     *
     * @param vkToAction
     *            ordered map of VK code → JavaFX runnable
     */
    private void runMessagePump(Map<Integer, Runnable> vkToAction) {
        pumpThreadId = Kernel32.INSTANCE.GetCurrentThreadId();

        Map<Integer, Runnable> idToAction = new LinkedHashMap<>();
        int nextId = 1;
        for (Map.Entry<Integer, Runnable> entry : vkToAction.entrySet()) {
            boolean registered = User32.INSTANCE.RegisterHotKey(null, nextId, MOD_NONE, entry.getKey());
            if (registered) {
                idToAction.put(nextId, entry.getValue());
                log.debug("Registered global hotkey id={} VK=0x{}", nextId, Integer.toHexString(entry.getKey()));
            } else {
                log.warn("Failed to register global hotkey VK=0x{} — another application may own this key",
                        Integer.toHexString(entry.getKey()));
            }
            nextId++;
        }
        log.info("Global hotkeys active (F4–F9)");

        // GetMessage returns: 0 on WM_QUIT, -1 on error, positive for normal messages.
        // Exiting on <= 0 correctly handles both WM_QUIT and unexpected errors.
        WinUser.MSG msg = new WinUser.MSG();
        while (User32.INSTANCE.GetMessage(msg, null, 0, 0) > 0) {
            if (msg.message == WM_HOTKEY) {
                Runnable action = idToAction.get(msg.wParam.intValue());
                if (action != null) {
                    Platform.runLater(action);
                }
            }
        }

        for (int i = 1; i < nextId; i++) {
            User32.INSTANCE.UnregisterHotKey(null, i);
        }
        log.info("Global hotkeys unregistered");
        pumpThreadId = 0;
    }

    /**
     * Returns {@code true} when the application is running on a Windows OS family
     * (e.g. Windows 10, Windows 11).
     *
     * @return {@code true} on Windows; {@code false} otherwise
     */
    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("windows");
    }
}
