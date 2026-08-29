package com.moepus.serverwarashi.common.modules.performance.sampling;

import com.moepus.serverwarashi.common.core.owner.PerfAttributionSnapshot;
import com.moepus.serverwarashi.common.modules.performance.report.PerfReportData;
import com.moepus.serverwarashi.common.modules.performance.report.PerfWorldHotspotIndex;

import java.util.Objects;
import java.util.Queue;
import java.util.SplittableRandom;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.LockSupport;
import java.util.function.LongSupplier;

public final class PerfSamplerSession {
    private static final int REPORT_INTERVAL_TICKS = 20;
    private static final int TICKABLE_ENTRY_KIND = 1;
    private static final int CHUNK_ENTRY_KIND = 1 << 1;
    // stateLock guards clock/currentTick/attempts; aggregationLock guards report construction.
    // Acquire them separately so neither lock is ever nested inside the other.
    private final Object stateLock = new Object();
    private final Object aggregationLock = new Object();
    private final Queue<TickSamples> closedTicks = new ConcurrentLinkedQueue<>();
    private final PerfSampleAggregator aggregator;
    private final int intervalMicros;
    private final long durationNanos;
    private final SplittableRandom intervalRandom;
    private final AnalysisClock analysisClock;
    private final LongSupplier nanoTime;
    private final TickGcTimeTracker gcTimeTracker = new TickGcTimeTracker();
    private final Thread samplerThread;
    private final WindowsTimerResolution timerResolution = new WindowsTimerResolution();
    private volatile PublishedSnapshot latestSnapshot;
    private volatile TickSamples currentTick;
    private TickSamples betweenTicks = new TickSamples(null);
    private long sampleAttempts;
    private long acceptedTicks;
    private int publishedEntryKinds;

    public static PerfSamplerSession create(long sessionId,
                                            int intervalMicros,
                                            Options options) {
        return new PerfSamplerSession(sessionId, intervalMicros, options);
    }

    private PerfSamplerSession(long sessionId,
                               int intervalMicros,
                               Options options) {
        Objects.requireNonNull(options, "options");
        if (intervalMicros <= 0) {
            throw new IllegalArgumentException("Sample interval must be positive");
        }
        this.intervalMicros = intervalMicros;
        durationNanos = options.durationNanos();
        aggregator = new PerfSampleAggregator(sessionId);
        nanoTime = options.nanoTime();
        analysisClock = new AnalysisClock(nanoTime);
        PerfReportData initialReport = options.initialAttribution() == null
                ? PerfReportData.empty(intervalMicros)
                : aggregator.buildLoaderLayoutOnly(options.initialAttribution(), intervalMicros);
        latestSnapshot = new PublishedSnapshot(initialReport,
                PerfWorldHotspotIndex.empty(initialReport.reportId(), 0L, 0L));
        intervalRandom = new SplittableRandom(sessionId ^ nanoTime.getAsLong());
        samplerThread = new Thread(this::runSampler, "ServerWarashi-PerfSampler");
        samplerThread.setDaemon(true);
    }

    public record Options(PerfAttributionSnapshot initialAttribution,
                          long durationNanos,
                          LongSupplier nanoTime) {
        public Options(PerfAttributionSnapshot initialAttribution, long durationNanos) {
            this(initialAttribution, durationNanos, System::nanoTime);
        }

        public static Options unlimited(PerfAttributionSnapshot initialAttribution) {
            return new Options(initialAttribution, Long.MAX_VALUE);
        }

        public Options {
            if (durationNanos <= 0L) {
                throw new IllegalArgumentException("Analysis duration must be positive");
            }
            Objects.requireNonNull(nanoTime, "nanoTime");
        }
    }

    public void start() {
        synchronized (stateLock) {
            analysisClock.start();
        }
        try {
            samplerThread.start();
        } catch (RuntimeException | Error exception) {
            synchronized (stateLock) {
                analysisClock.stop();
            }
            throw exception;
        }
    }

    public boolean pause() {
        Measurement measurement;
        synchronized (stateLock) {
            if (!analysisClock.pause()) {
                return false;
            }
            measurement = measurementLocked();
        }
        LockSupport.unpark(samplerThread);
        flushCurrentReport(measurement);
        return true;
    }

    public boolean resume() {
        boolean changed;
        synchronized (stateLock) {
            changed = analysisClock.resume();
        }
        if (changed) {
            LockSupport.unpark(samplerThread);
        }
        return changed;
    }

    public boolean hasReachedDuration() {
        synchronized (stateLock) {
            return analysisClock.elapsedNanos() >= durationNanos;
        }
    }

    public void beginTick(PerfAttributionSnapshot attribution) {
        synchronized (stateLock) {
            if (analysisClock.state() != AnalysisClock.State.RUNNING) {
                throw new IllegalStateException("Cannot begin a tick while sampling is not running");
            }
            TickSamples tick = new TickSamples(attribution);
            tick.mergeSamples(betweenTicks);
            betweenTicks = new TickSamples(null);
            tick.gcStartMillis = gcTimeTracker.snapshot();
            currentTick = tick;
        }
    }

    public void updateAttribution(PerfAttributionSnapshot attribution, boolean refreshReport) {
        synchronized (stateLock) {
            TickSamples tick = currentTick;
            if (tick != null) {
                tick.attribution = attribution;
                tick.refreshReport |= refreshReport;
            }
        }
    }

    public void endTick(long durationNanos) {
        synchronized (stateLock) {
            TickSamples tick = currentTick;
            if (tick == null) {
                return;
            }
            tick.durationNanos = durationNanos;
            tick.gcTimeMillis = gcTimeTracker.elapsedSince(tick.gcStartMillis);
            tick.completedAtNanos = nanoTime.getAsLong();
            currentTick = null;
            closedTicks.add(tick);
        }
    }

    public PerfReportData stop() {
        return stop(false);
    }

    public PerfReportData stop(boolean retainInitialLayout) {
        Measurement measurement;
        synchronized (stateLock) {
            currentTick = null;
            betweenTicks = new TickSamples(null);
            analysisClock.stop();
            measurement = measurementLocked();
        }
        LockSupport.unpark(samplerThread);
        joinSampler();
        synchronized (aggregationLock) {
            drainClosedTicks(measurement);
            if (acceptedTicks > 0L) {
                latestSnapshot = buildSnapshot(measurement);
            } else if (!retainInitialLayout) {
                PerfReportData empty = PerfReportData.empty(intervalMicros);
                latestSnapshot = new PublishedSnapshot(empty,
                        PerfWorldHotspotIndex.empty(0L, 0L, 0L));
            }
        }
        return latestSnapshot.report();
    }

    public PerfReportData latestReport() {
        return latestSnapshot.report();
    }

    public PerfWorldHotspotIndex latestHotspots() {
        return latestSnapshot.hotspots();
    }

    public PublishedSnapshot latestPublishedSnapshot() {
        return latestSnapshot;
    }

    private void runSampler() {
        long intervalNanos = intervalMicros * 1_000L;
        try {
            while (true) {
                SamplerStatus status = sampleCurrent();
                drainClosedTicksIfPresent();
                if (status == SamplerStatus.STOPPED) {
                    return;
                }
                if (status == SamplerStatus.IDLE) {
                    timerResolution.disable();
                    LockSupport.park();
                    continue;
                }
                timerResolution.enable();
                waitForNextAttempt(jitteredDelay(intervalNanos, intervalRandom.nextLong()));
            }
        } finally {
            timerResolution.disable();
            drainClosedTicksIfPresent();
        }
    }

    private void waitForNextAttempt(long delayNanos) {
        LockSupport.parkNanos(delayNanos);
    }

    static long jitteredDelay(long intervalNanos, long randomValue) {
        return intervalNanos / 2L + Math.floorMod(randomValue, intervalNanos);
    }

    private SamplerStatus sampleCurrent() {
        if (analysisClock.state() != AnalysisClock.State.RUNNING) {
            return stateSnapshot();
        }
        TickSamples expected = currentTick;
        PerfWorkTracker.Snapshot snapshot = PerfWorkTracker.snapshot();
        if (snapshot == null) {
            snapshot = PerfStackAttributor.capture(PerfWorkTracker.profiledThread());
        } else if (snapshot.type() == TickType.ENTITY) {
            PerfWorkTracker.Snapshot handler =
                    PerfStackAttributor.captureTickHandler(PerfWorkTracker.profiledThread());
            if (handler != null) {
                snapshot = handler;
            }
        }
        synchronized (stateLock) {
            SamplerStatus status = samplerStatusLocked();
            if (status != SamplerStatus.RUNNING) {
                return status;
            }
            sampleAttempts++;
            if (expected != null && currentTick == expected) {
                expected.sample(snapshot);
            } else if (expected == null && currentTick == null && snapshot != null) {
                betweenTicks.sample(snapshot);
            }
            return SamplerStatus.RUNNING;
        }
    }

    private void drainClosedTicks(Measurement measurement) {
        while (true) {
            TickSamples tick = closedTicks.poll();
            if (tick == null) {
                return;
            }
            aggregator.accept(tick);
            acceptedTicks++;
            int entryKinds = entryKinds(tick);
            boolean firstEntryKind = (entryKinds & ~publishedEntryKinds) != 0;
            if (shouldPublishReport(acceptedTicks, tick.refreshReport, firstEntryKind)) {
                latestSnapshot = buildSnapshot(measurement);
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

    private long activeElapsedNanosLocked() {
        return Math.min(analysisClock.elapsedNanos(), durationNanos);
    }

    private SamplerStatus stateSnapshot() {
        synchronized (stateLock) {
            return samplerStatusLocked();
        }
    }

    private SamplerStatus samplerStatusLocked() {
        AnalysisClock.State state = analysisClock.state();
        if (state == AnalysisClock.State.STOPPED) {
            return SamplerStatus.STOPPED;
        }
        return state == AnalysisClock.State.RUNNING
                && analysisClock.elapsedNanos() < durationNanos
                ? SamplerStatus.RUNNING : SamplerStatus.IDLE;
    }

    private Measurement measurement() {
        synchronized (stateLock) {
            return measurementLocked();
        }
    }

    private Measurement measurementLocked() {
        return new Measurement(activeElapsedNanosLocked(), sampleAttempts);
    }

    private void drainClosedTicksIfPresent() {
        if (closedTicks.isEmpty()) {
            return;
        }
        Measurement measurement = measurement();
        synchronized (aggregationLock) {
            drainClosedTicks(measurement);
        }
    }

    private void flushCurrentReport(Measurement measurement) {
        synchronized (aggregationLock) {
            drainClosedTicks(measurement);
            if (acceptedTicks > 0L) {
                latestSnapshot = buildSnapshot(measurement);
            }
        }
    }

    private PublishedSnapshot buildSnapshot(Measurement measurement) {
        PerfSampleAggregator.BuildSnapshot build = aggregator.buildSnapshot(
                measurement.elapsedNanos(), measurement.sampleAttempts(), intervalMicros);
        return new PublishedSnapshot(build.report(), build.hotspots());
    }

    private void joinSampler() {
        try {
            samplerThread.join();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while stopping perf sampler", exception);
        }
    }

    private record Measurement(long elapsedNanos, long sampleAttempts) {
    }

    public record PublishedSnapshot(PerfReportData report, PerfWorldHotspotIndex hotspots) {
    }

    private enum SamplerStatus {
        RUNNING,
        IDLE,
        STOPPED
    }
}
