package com.sdvxhelper.app.controller.factories;

import java.util.concurrent.ThreadFactory;

/**
 * Thread factory for the OBS auto-reconnect scheduled executor. Produces daemon
 * threads named {@code "obs-reconnect"}.
 *
 * @author Throdax
 * @since 2.0.0
 */
public final class ObsReconnectThreadFactory implements ThreadFactory {

    @Override
    public Thread newThread(Runnable r) {
        Thread t = new Thread(r, "obs-reconnect");
        t.setDaemon(true);
        return t;
    }
}
