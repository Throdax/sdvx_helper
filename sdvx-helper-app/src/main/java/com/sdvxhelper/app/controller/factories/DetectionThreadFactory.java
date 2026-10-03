package com.sdvxhelper.app.controller.factories;

import java.util.concurrent.ThreadFactory;

/**
 * Thread factory for the single-threaded detection-loop executor. Produces
 * daemon threads named {@code "detection-loop"}.
 *
 * @author Throdax
 * @since 2.0.0
 */
public final class DetectionThreadFactory implements ThreadFactory {

    @Override
    public Thread newThread(Runnable r) {
        Thread t = new Thread(r, "detection-loop");
        t.setDaemon(true);
        return t;
    }
}
