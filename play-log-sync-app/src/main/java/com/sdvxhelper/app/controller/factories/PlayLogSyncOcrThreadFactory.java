package com.sdvxhelper.app.controller.factories;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * {@link ThreadFactory} that produces numbered daemon threads for the per-sync
 * OCR thread pool used by the Play Log Sync tool.
 *
 * <p>
 * Each thread is named {@code play-log-sync-ocr-N} (N = 1, 2, …) and is marked
 * as a daemon so it does not prevent JVM exit if the application is closed
 * while a sync is in progress.
 * </p>
 *
 * @author Throdax
 * @since 2.0.0
 */
public class PlayLogSyncOcrThreadFactory implements ThreadFactory {

    private final AtomicInteger threadNumber = new AtomicInteger(1);

    @Override
    public Thread newThread(Runnable runnable) {
        Thread thread = new Thread(runnable, "play-log-sync-ocr-" + threadNumber.getAndIncrement());
        thread.setDaemon(true);
        return thread;
    }
}
