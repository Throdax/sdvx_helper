package com.sdvxhelper.app.controller.factories;

import java.util.concurrent.ThreadFactory;

/**
 * Thread factory for the single-threaded one-off UI action executor. Produces
 * daemon threads named {@code "ui-action"}.
 *
 * <p>
 * This executor is deliberately separate from the
 * {@link DetectionThreadFactory} executor, which is permanently occupied for
 * the entire detection session by {@code DetectionEngine.runDetectionLoop()}'s
 * {@code while} loop. Submitting a one-off task (e.g. the F4 "Save Volforce"
 * button handler) to that shared executor would silently queue it behind the
 * infinite loop task, so it would never actually run for as long as detection
 * is active — with no exception, no log entry, and no user-visible feedback of
 * any kind.
 * </p>
 *
 * @author Throdax
 * @since 2.0.0
 */
public final class ActionThreadFactory implements ThreadFactory {

    @Override
    public Thread newThread(Runnable r) {
        Thread t = new Thread(r, "ui-action");
        t.setDaemon(true);
        return t;
    }
}
