package com.sdvxhelper.app.controller.factories;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ScoreViewerThreadFactory}.
 */
class ScoreViewerThreadFactoryTest {

    @Test
    void newThreadReturnsNonNullThread() {
        ScoreViewerThreadFactory factory = new ScoreViewerThreadFactory();
        Assertions.assertNotNull(factory.newThread(() -> {
        }));
    }

    @Test
    void newThreadIsDaemon() {
        ScoreViewerThreadFactory factory = new ScoreViewerThreadFactory();
        Assertions.assertTrue(factory.newThread(() -> {
        }).isDaemon(), "Score viewer threads must be daemon threads");
    }

    @Test
    void firstThreadNameIsScoreViewerThread1() {
        ScoreViewerThreadFactory factory = new ScoreViewerThreadFactory();
        Assertions.assertEquals("score-viewer-thread-1", factory.newThread(() -> {
        }).getName());
    }

    @Test
    void threadNamesIncrement() {
        ScoreViewerThreadFactory factory = new ScoreViewerThreadFactory();
        Thread t1 = factory.newThread(() -> {
        });
        Thread t2 = factory.newThread(() -> {
        });
        Thread t3 = factory.newThread(() -> {
        });
        Assertions.assertEquals("score-viewer-thread-1", t1.getName());
        Assertions.assertEquals("score-viewer-thread-2", t2.getName());
        Assertions.assertEquals("score-viewer-thread-3", t3.getName());
    }

    @Test
    void separateFactoryInstancesStartCounterIndependently() {
        ScoreViewerThreadFactory f1 = new ScoreViewerThreadFactory();
        ScoreViewerThreadFactory f2 = new ScoreViewerThreadFactory();
        f1.newThread(() -> {
        });
        f1.newThread(() -> {
        });
        // f2's counter is independent — it starts at 1
        Thread firstFromF2 = f2.newThread(() -> {
        });
        Assertions.assertEquals("score-viewer-thread-1", firstFromF2.getName());
    }

    @Test
    void newThreadIsNotStarted() {
        ScoreViewerThreadFactory factory = new ScoreViewerThreadFactory();
        Thread thread = factory.newThread(() -> {
        });
        Assertions.assertEquals(Thread.State.NEW, thread.getState());
    }
}
