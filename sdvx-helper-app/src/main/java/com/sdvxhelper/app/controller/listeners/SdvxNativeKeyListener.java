package com.sdvxhelper.app.controller.listeners;

import java.util.Map;

import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener;

/**
 * Global keyboard listener that dispatches F-key presses to controller actions.
 *
 * <p>
 * Each hot-key code is mapped to a {@link Runnable} supplied by the caller.
 * This keeps the listener decoupled from {@code MainController} while still
 * allowing it to invoke arbitrary actions.
 * </p>
 *
 * <p>
 * Registered via {@link com.github.kwhat.jnativehook.GlobalScreen}.
 * </p>
 *
 * @author Throdax
 * @since 2.0.0
 */
public final class SdvxNativeKeyListener implements NativeKeyListener {

    private final Map<Integer, Runnable> keyActions;

    /**
     * @param keyActions
     *            map of {@link NativeKeyEvent} key codes to the {@link Runnable}
     *            that should be executed when that key is pressed
     */
    public SdvxNativeKeyListener(Map<Integer, Runnable> keyActions) {
        this.keyActions = keyActions;
    }

    @Override
    public void nativeKeyPressed(NativeKeyEvent e) {
        Runnable action = keyActions.get(e.getKeyCode());
        if (action != null) {
            action.run();
        }
    }
}
