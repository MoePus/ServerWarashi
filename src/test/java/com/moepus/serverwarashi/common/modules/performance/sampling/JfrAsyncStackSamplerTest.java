package com.moepus.serverwarashi.common.modules.performance.sampling;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class JfrAsyncStackSamplerTest {
    private static final long BURST_NANOS = TimeUnit.MILLISECONDS.toNanos(50L);

    @Test
    void streamsBoundedStacksFromTargetWithoutSynchronousStackAccess() throws Exception {
        assumeTrue(JfrAsyncStackSampler.isAvailable());
        AtomicBoolean running = new AtomicBoolean(true);
        AtomicLong progress = new AtomicLong();
        Thread target = new Thread(() -> spin(running, progress), "jfr-sampling-target");
        target.start();
        CountDownLatch sampled = new CountDownLatch(1);
        AtomicReference<AsyncStackSample> captured = new AtomicReference<>();
        JfrAsyncStackSampler sampler = new JfrAsyncStackSampler();
        try {
            sampler.start(target, 1_000, sample -> {
                captured.compareAndSet(null, sample);
                sampled.countDown();
            });
            assertTrue(sampled.await(5L, TimeUnit.SECONDS));
            assertTrue(progress.get() != 0L);
            assertTrue(captured.get().stack().length > 0);
            assertTrue(captured.get().stack().length <= 96);
            assertFalse(sampler.backendName().isBlank());
        } finally {
            sampler.stop();
            running.set(false);
            target.join();
        }
    }

    @Test
    void drainsAShortBurstWhenStopped() throws Exception {
        assumeTrue(JfrAsyncStackSampler.isAvailable());
        AtomicBoolean running = new AtomicBoolean(true);
        AtomicLong progress = new AtomicLong();
        Thread target = new Thread(() -> spin(running, progress), "jfr-tail-drain-target");
        target.start();
        CountDownLatch sampled = new CountDownLatch(1);
        JfrAsyncStackSampler sampler = new JfrAsyncStackSampler();
        try {
            sampler.start(target, 1_000, sample -> sampled.countDown());
            TimeUnit.NANOSECONDS.sleep(BURST_NANOS);
            sampler.stop();
            assertTrue(sampled.await(0L, TimeUnit.SECONDS));
        } finally {
            sampler.stop();
            running.set(false);
            target.join();
        }
    }

    private static void spin(AtomicBoolean running, AtomicLong progress) {
        long value = 0L;
        while (running.get()) {
            value = value * 31L + 17L;
            progress.lazySet(value | 1L);
        }
    }

}
