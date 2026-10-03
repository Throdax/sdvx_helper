package com.sdvxhelper.app.controller.factories;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Custom thread factory for the score viewer app to create daemon threads with
 * a specific naming pattern.
 * 
 * @author Throdax
 * @since 2.0.0
 */
public class ScoreViewerThreadFactory implements ThreadFactory {

    private AtomicInteger threadCount = new AtomicInteger(1);

    /**
     * Creates a new daemon thread with a name like "score-viewer-thread-1",
     * "score-viewer-thread-2", etc.
     * 
     * @param r
     *            the runnable to execute in the new thread
     * @return a new daemon thread with the specified naming pattern
     */
    @Override
    public Thread newThread(Runnable r) {
        Thread t = new Thread(r, "score-viewer-thread-" + threadCount.getAndIncrement());
        t.setDaemon(true);
        return t;
    }

}
