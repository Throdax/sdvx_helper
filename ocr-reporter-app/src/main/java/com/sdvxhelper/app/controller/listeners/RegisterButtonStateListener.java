package com.sdvxhelper.app.controller.listeners;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;

/**
 * Triggers a register-button state refresh whenever a bound text property
 * changes. A single instance can be reused across multiple properties (title,
 * jacket hash, difficulty).
 *
 * @author Throdax
 * @since 2.0.0
 */
public class RegisterButtonStateListener implements ChangeListener<String> {

    private Runnable onStateChange;

    /**
     * @param onStateChange
     *            callback invoked on every text-property change (e.g.
     *            {@code this::updateRegisterButtonState})
     */
    public RegisterButtonStateListener(Runnable onStateChange) {
        this.onStateChange = onStateChange;
    }

    @Override
    public void changed(ObservableValue<? extends String> observable, String oldValue, String newValue) {
        onStateChange.run();
    }
}
