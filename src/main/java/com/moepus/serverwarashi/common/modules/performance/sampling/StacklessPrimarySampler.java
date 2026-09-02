package com.moepus.serverwarashi.common.modules.performance.sampling;

import java.util.SplittableRandom;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Supplier;

final class StacklessPrimarySampler {
    private final long intervalNanos;
    private final SplittableRandom intervalRandom;
    private final Supplier<Status> sample;
    private final Runnable idleWork;
    private final WindowsTimerResolution timerResolution = new WindowsTimerResolution();
    private final Thread thread;

    StacklessPrimarySampler(long seed,
                            int intervalMicros,
                            Supplier<Status> sample,
                            Runnable idleWork) {
        intervalNanos = intervalMicros * 1_000L;
        intervalRandom = new SplittableRandom(seed);
        this.sample = sample;
        this.idleWork = idleWork;
        thread = new Thread(this::run, "ServerWarashi-PerfSampler");
        thread.setDaemon(true);
    }

    void start() {
        thread.start();
    }

    void wake() {
        LockSupport.unpark(thread);
    }

    void join() {
        try {
            thread.join();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while stopping perf sampler", exception);
        }
    }

    private void run() {
        try {
            while (runOnce()) {
            }
        } finally {
            timerResolution.disable();
            idleWork.run();
        }
    }

    private boolean runOnce() {
        Status status = sample.get();
        idleWork.run();
        if (status == Status.STOPPED) {
            return false;
        }
        if (status == Status.IDLE) {
            timerResolution.disable();
            LockSupport.park();
            return true;
        }
        timerResolution.enable();
        LockSupport.parkNanos(jitteredDelay(intervalNanos, intervalRandom.nextLong()));
        return true;
    }

    static long jitteredDelay(long intervalNanos, long randomValue) {
        return intervalNanos / 2L + Math.floorMod(randomValue, intervalNanos);
    }

    enum Status {
        RUNNING,
        IDLE,
        STOPPED
    }
}
