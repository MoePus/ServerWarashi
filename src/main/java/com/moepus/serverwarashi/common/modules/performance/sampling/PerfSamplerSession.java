package com.moepus.serverwarashi.common.modules.performance.sampling;

import com.moepus.serverwarashi.common.core.owner.PerfAttributionSnapshot;
import com.moepus.serverwarashi.common.modules.performance.report.PerfReportData;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.SplittableRandom;
import java.util.concurrent.locks.LockSupport;

public final class PerfSamplerSession {
    private static final int SNAPSHOT_TICKS = 20;
    private final Object lock = new Object();
    private final Queue<TickSamples> closedTicks = new ArrayDeque<>();
    private final PerfSampleAggregator aggregator;
    private final int intervalMicros;
    private final SplittableRandom intervalRandom;
    private final long startedAtNanos;
    private final Thread samplerThread;
    private volatile PerfReportData latestReport;
    private TickSamples currentTick;
    private long acceptedTicks;
    private volatile boolean tickOpen;
    private volatile boolean running = true;

    public PerfSamplerSession(long sessionId, int intervalMicros) {
        this.intervalMicros = intervalMicros;
        aggregator = new PerfSampleAggregator(sessionId);
        startedAtNanos = System.nanoTime();
        latestReport = PerfReportData.empty(intervalMicros);
        intervalRandom = new SplittableRandom(sessionId ^ startedAtNanos);
        samplerThread = new Thread(this::runSampler, "ServerWarashi-PerfSampler");
        samplerThread.setDaemon(true);
    }

    public void start() {
        samplerThread.start();
    }

    public void beginTick(PerfAttributionSnapshot attribution) {
        synchronized (lock) {
            currentTick = new TickSamples(attribution);
            tickOpen = true;
        }
    }

    public void updateAttribution(PerfAttributionSnapshot attribution) {
        synchronized (lock) {
            if (tickOpen) {
                currentTick.attribution = attribution;
            }
        }
    }

    public void endTick(long durationNanos) {
        synchronized (lock) {
            if (!tickOpen) {
                return;
            }
            currentTick.durationNanos = durationNanos;
            closedTicks.add(currentTick);
            currentTick = null;
            tickOpen = false;
        }
    }

    public PerfReportData stop() {
        synchronized (lock) {
            currentTick = null;
            tickOpen = false;
        }
        running = false;
        samplerThread.interrupt();
        joinSampler();
        latestReport = aggregator.build(elapsedMillis(), intervalMicros);
        return latestReport;
    }

    public PerfReportData latestReport() {
        return latestReport;
    }

    private void runSampler() {
        long intervalNanos = intervalMicros * 1_000L;
        while (running) {
            sampleCurrent();
            drainClosedTicks();
            LockSupport.parkNanos(jitteredDelay(intervalNanos, intervalRandom.nextLong()));
        }
        drainClosedTicks();
    }

    static long jitteredDelay(long intervalNanos, long randomValue) {
        return intervalNanos / 2L + Math.floorMod(randomValue, intervalNanos);
    }

    private void sampleCurrent() {
        synchronized (lock) {
            if (tickOpen) {
                currentTick.sample(PerfWorkTracker.snapshot());
            }
        }
    }

    private void drainClosedTicks() {
        while (true) {
            TickSamples tick;
            synchronized (lock) {
                tick = closedTicks.poll();
            }
            if (tick == null) {
                return;
            }
            aggregator.accept(tick);
            acceptedTicks++;
            if (acceptedTicks % SNAPSHOT_TICKS == 0L) {
                latestReport = aggregator.build(elapsedMillis(), intervalMicros);
            }
        }
    }

    private long elapsedMillis() {
        return (System.nanoTime() - startedAtNanos) / 1_000_000L;
    }

    private void joinSampler() {
        try {
            samplerThread.join();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while stopping perf sampler", exception);
        }
    }
}
