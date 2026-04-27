package com.sdvxhelper.app.controller.factories;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link PlayLogSyncThreadFactory}.
 */
class PlayLogSyncThreadFactoryTest {

    private final PlayLogSyncThreadFactory factory = new PlayLogSyncThreadFactory();

    @Test
    void newThreadReturnsNonNullThread() {
        Thread thread = factory.newThread(() -> {
        });
        Assertions.assertNotNull(thread);
    }

    @Test
    void newThreadIsDaemon() {
        Thread thread = factory.newThread(() -> {
        });
        Assertions.assertTrue(thread.isDaemon(), "Thread must be a daemon thread");
    }

    @Test
    void newThreadHasCorrectName() {
        Thread thread = factory.newThread(() -> {
        });
        Assertions.assertEquals("play-log-sync-bg", thread.getName());
    }

    @Test
    void newThreadAlwaysHasSameNameRegardlessOfCallCount() {
        Thread t1 = factory.newThread(() -> {
        });
        Thread t2 = factory.newThread(() -> {
        });
        Assertions.assertEquals(t1.getName(), t2.getName(),
                "All threads from this factory should share the same fixed name");
    }

    @Test
    void newThreadIsNotStarted() {
        Thread thread = factory.newThread(() -> {
        });
        Assertions.assertEquals(Thread.State.NEW, thread.getState(), "Freshly created thread must be in NEW state");
    }
}
