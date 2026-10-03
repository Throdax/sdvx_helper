package com.sdvxhelper.app.controller;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link OcrReporterThreadFactory}.
 */
class OcrReporterThreadFactoryTest {

    private final OcrReporterThreadFactory factory = new OcrReporterThreadFactory();

    @Test
    void newThreadReturnsNonNullThread() {
        Assertions.assertNotNull(factory.newThread(() -> {
        }));
    }

    @Test
    void newThreadIsDaemon() {
        Assertions.assertTrue(factory.newThread(() -> {
        }).isDaemon(), "OCR threads must be daemon threads");
    }

    @Test
    void firstThreadNameIsOcrThread1() {
        OcrReporterThreadFactory fresh = new OcrReporterThreadFactory();
        Assertions.assertEquals("ocr-thread-1", fresh.newThread(() -> {
        }).getName());
    }

    @Test
    void threadNameIncrements() {
        OcrReporterThreadFactory fresh = new OcrReporterThreadFactory();
        Thread t1 = fresh.newThread(() -> {
        });
        Thread t2 = fresh.newThread(() -> {
        });
        Assertions.assertEquals("ocr-thread-1", t1.getName());
        Assertions.assertEquals("ocr-thread-2", t2.getName());
    }

    @Test
    void newThreadIsNotStarted() {
        Assertions.assertEquals(Thread.State.NEW, factory.newThread(() -> {
        }).getState());
    }
}
