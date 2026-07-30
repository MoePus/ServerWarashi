package com.moepus.serverwarashi.common.modules.performance.sampling;

import com.moepus.serverwarashi.common.core.owner.PerfAttributionSnapshot;
import com.moepus.serverwarashi.common.modules.performance.report.PerfReportData;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.SplittableRandom;
import java.util.concurrent.locks.LockSupport;

public final class PerfSamplerSession {
    private static final int REPORT_INTERVAL_TICKS = 20;
    private static final int TICKABLE_ENTRY_KIND = 1;
    private static final int CHUNK_ENTRY_KIND = 1 << 1;
    private final Object lock = new Object();
    private final Queue<TickSamples> closedTicks = new ArrayDeque<>();
    private final PerfSampleAggregator aggregator;
    private final int intervalMicros;
    private final SplittableRandom intervalRandom;
    private final long startedAtNanos;
    private final Thread samplerThread;
    private volatile PerfReportData latestReport;
    private volatile TickSamples currentTick;
    private long acceptedTicks;
    private int publishedEntryKinds;
    private boolean reportRefreshRequested;
    private volatile boolean running = true;

    public PerfSamplerSession(long sessionId,
                              int intervalMicros,
                              PerfAttributionSnapshot initialAttribution) {
        if (intervalMicros <= 0) {
            throw new IllegalArgumentException("Sample interval must be positive");
        }
        this.intervalMicros = intervalMicros;
        aggregator = new PerfSampleAggregator(sessionId);
        startedAtNanos = System.nanoTime();
        latestReport = initialAttribution == null
                ? PerfReportData.empty(intervalMicros)
                : aggregator.buildLoaderLayoutOnly(initialAttribution, intervalMicros);
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
        }
        LockSupport.unpark(samplerThread);
    }

    public void updateAttribution(PerfAttributionSnapshot attribution, boolean refreshReport) {
        synchronized (lock) {
            if (currentTick != null) {
                currentTick.attribution = attribution;
            }
            if (refreshReport) {
                reportRefreshRequested = true;
            }
        }
    }

    public void endTick(long durationNanos) {
        synchronized (lock) {
            TickSamples tick = currentTick;
            if (tick == null) {
                return;
            }
            tick.durationNanos = durationNanos;
            closedTicks.add(tick);
            currentTick = null;
        }
        LockSupport.unpark(samplerThread);
    }

    public PerfReportData stop() {
        return stop(false);
    }

    public PerfReportData stop(boolean retainInitialLayout) {
        synchronized (lock) {
            currentTick = null;
        }
        running = false;
        LockSupport.unpark(samplerThread);
        joinSampler();
        if (acceptedTicks > 0L) {
            latestReport = aggregator.build(elapsedMillis(), intervalMicros);
        } else if (!retainInitialLayout) {
            latestReport = PerfReportData.empty(intervalMicros);
        }
        return latestReport;
    }

    public PerfReportData latestReport() {
        return latestReport;
    }

    private void runSampler() {
        long intervalNanos = intervalMicros * 1_000L;
        while (running) {
            if (!hasSamplingWork()) {
                LockSupport.park();
                continue;
            }
            drainClosedTicks();
            TickSamples expected = currentTick;
            if (expected == null
                    || !waitForDelay(initialDelay(intervalNanos, intervalRandom.nextLong()), expected)) {
                continue;
            }
            while (running && currentTick == expected) {
                sampleCurrent(expected);
                long delay = jitteredDelay(intervalNanos, intervalRandom.nextLong());
                if (!waitForDelay(delay, expected)) {
                    break;
                }
            }
        }
        drainClosedTicks();
    }

    private boolean hasSamplingWork() {
        synchronized (lock) {
            return currentTick != null || !closedTicks.isEmpty();
        }
    }

    static long initialDelay(long intervalNanos, long randomValue) {
        return Math.floorMod(randomValue, intervalNanos);
    }

    static long jitteredDelay(long intervalNanos, long randomValue) {
        return intervalNanos / 2L + Math.floorMod(randomValue, intervalNanos);
    }

    private void sampleCurrent(TickSamples expected) {
        PerfWorkTracker.Snapshot snapshot = PerfWorkTracker.snapshot();
        synchronized (lock) {
            if (currentTick == expected) {
                expected.sample(snapshot);
            }
        }
    }

    private boolean waitForDelay(long delayNanos, TickSamples expected) {
        long deadline = System.nanoTime() + delayNanos;
        while (running && currentTick == expected) {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0L) {
                return true;
            }
            LockSupport.parkNanos(remaining);
        }
        return running && currentTick == expected;
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
            boolean refreshReport = consumeReportRefreshRequest();
            int entryKinds = entryKinds(tick);
            boolean firstEntryKind = (entryKinds & ~publishedEntryKinds) != 0;
            if (shouldPublishReport(acceptedTicks, refreshReport, firstEntryKind)) {
                latestReport = aggregator.build(elapsedMillis(), intervalMicros);
            }
            publishedEntryKinds |= entryKinds;
        }
    }

    static boolean shouldPublishReport(long acceptedTicks,
                                       boolean refreshRequested,
                                       boolean firstEntryKind) {
        return acceptedTicks % REPORT_INTERVAL_TICKS == 0L
                || refreshRequested || firstEntryKind;
    }

    private static int entryKinds(TickSamples tick) {
        int kinds = tick.tickables.isEmpty() ? 0 : TICKABLE_ENTRY_KIND;
        return tick.chunks.isEmpty() ? kinds : kinds | CHUNK_ENTRY_KIND;
    }

    private boolean consumeReportRefreshRequest() {
        synchronized (lock) {
            boolean requested = reportRefreshRequested;
            reportRefreshRequested = false;
            return requested;
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
