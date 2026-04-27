package com.sdvxhelper.app.controller.factories;

import java.util.concurrent.ThreadFactory;

/**
 * {@link ThreadFactory} that produces named daemon threads for the Play Log
 * Sync background executor.
 *
 * @author Throdax
 * @since 2.0.0
 */
public class PlayLogSyncThreadFactory implements ThreadFactory {

    @Override
    public Thread newThread(Runnable runnable) {
        Thread thread = new Thread(runnable, "play-log-sync-bg");
        thread.setDaemon(true);
        return thread;
    }
}
