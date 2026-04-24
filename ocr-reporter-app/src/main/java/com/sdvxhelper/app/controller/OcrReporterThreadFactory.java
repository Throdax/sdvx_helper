package com.sdvxhelper.app.controller;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public class OcrReporterThreadFactory implements ThreadFactory {

    private AtomicInteger threadCount = new AtomicInteger(1);

    @Override
    public Thread newThread(Runnable r) {
        Thread t = new Thread(r, "ocr-thread-" + threadCount.getAndIncrement());
        t.setDaemon(true);
        return t;
    }

}
