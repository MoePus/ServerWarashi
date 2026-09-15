package com.moepus.serverwarashi.common.modules.performance.sampling;

import com.moepus.serverwarashi.common.core.owner.PerfAttributionSnapshot;
import com.moepus.serverwarashi.common.modules.performance.report.PerfReportData;
import com.moepus.serverwarashi.common.modules.performance.report.PerfWorldHotspotIndex;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.LongSupplier;

public final class PerfSamplerSession {
    private static final int MIN_INTERVAL_MICROS = 1_000;
    private static final int MAX_INTERVAL_MICROS = 100_000;
    private static final int DETAIL_INTERVAL_MICROS = 10_000;
    private final Object stateLock = new Object();
    private final ArrayDeque<TickSamples> closedTicks = new ArrayDeque<>();
    private final PerfSamplerReportSink reportSink;
    private final AsyncSampleIngress detailIngress;
    private final long reportId;
    private final long durationNanos;
    private final AnalysisClock analysisClock;
    private final LongSupplier nanoTime;
    private final PrimaryObservationRing primaryObservations;
    private final TickGcTimeTracker gcTimeTracker = new TickGcTimeTracker();
    private final StacklessPrimarySampler primarySampler;
    private volatile TickSamples currentTick;
    private Thread targetThread;
    private TickSamples betweenTicks = new TickSamples(null);
    private long sampleAttempts;
    private long currentTickId;
    private long completedTickId;
    private volatile long lateDetailSamples;
    private boolean started;
    private boolean stopped;
    public static PerfSamplerSession create(long sessionId,
                                            int intervalMicros,
                                            Options options) {
        return new PerfSamplerSession(sessionId, intervalMicros, options,
                new JfrAsyncStackSampler());
    }
    static PerfSamplerSession createForTest(long sessionId,
                                            int intervalMicros,
                                            Options options,
                                            AsyncStackSampler detailSampler) {
        return new PerfSamplerSession(sessionId, intervalMicros, options, detailSampler);
    }
    private PerfSamplerSession(long sessionId,
                               int intervalMicros,
                               Options options,
                               AsyncStackSampler detailSampler) {
        Objects.requireNonNull(options, "options");
        if (intervalMicros < MIN_INTERVAL_MICROS || intervalMicros > MAX_INTERVAL_MICROS) {
            throw new IllegalArgumentException("Sample interval must be between "
                    + MIN_INTERVAL_MICROS + " and " + MAX_INTERVAL_MICROS + " microseconds");
        }
        reportId = sessionId;
        durationNanos = options.durationNanos();
        nanoTime = options.nanoTime();
        analysisClock = new AnalysisClock(nanoTime);
        primaryObservations = new PrimaryObservationRing(intervalMicros * 1_000L);
        reportSink = new PerfSamplerReportSink(
                sessionId, intervalMicros, options.initialAttribution());
        detailIngress = new AsyncSampleIngress(detailSampler,
                this::recordDetail, this::drainReadyTicks);
        primarySampler = new StacklessPrimarySampler(
                sessionId ^ nanoTime.getAsLong(), intervalMicros,
                this::sampleCurrent, this::drainReadyTicks);
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
        Thread profiled = PerfWorkTracker.profiledThread();
        start(profiled == null ? Thread.currentThread() : profiled);
    }
    public void start(Thread thread) {
        synchronized (stateLock) {
            if (started) {
                throw new IllegalStateException("Perf sampler has already started");
            }
            targetThread = Objects.requireNonNull(thread, "thread");
            analysisClock.start();
            started = true;
        }
        try {
            primarySampler.start();
            detailIngress.start(targetThread, DETAIL_INTERVAL_MICROS);
        } catch (RuntimeException | Error failure) {
            synchronized (stateLock) {
                analysisClock.stop();
                stopped = true;
            }
            primarySampler.wake();
            primarySampler.join();
            throw failure;
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
        primarySampler.wake();
        detailIngress.pause();
        flushCurrentReport(measurement, true);
        return true;
    }
    public boolean resume() {
        synchronized (stateLock) {
            if (!analysisClock.resume()) {
                return false;
            }
        }
        detailIngress.resume(targetThread, DETAIL_INTERVAL_MICROS);
        primarySampler.wake();
        return true;
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
            tick.tickId = ++currentTickId;
            tick.startedAtNanos = nanoTime.getAsLong();
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
            completedTickId = tick.tickId;
            currentTick = null;
            closedTicks.addLast(tick);
        }
    }
    public PerfReportData stop() {
        return stop(false);
    }
    public PerfReportData stop(boolean retainInitialLayout) {
        Measurement measurement;
        synchronized (stateLock) {
            if (stopped) {
                return reportSink.latest().report();
            }
            analysisClock.stop();
            stopped = true;
            measurement = measurementLocked();
        }
        detailIngress.stop();
        primarySampler.wake();
        primarySampler.join();
        discardOpenSamples();
        reportSink.finish(pollClosedTicks(true), buildContext(measurement), retainInitialLayout);
        return reportSink.latest().report();
    }
    public PerfReportData latestReport() {
        return reportSink.latest().report();
    }
    public PerfWorldHotspotIndex latestHotspots() {
        return reportSink.latest().hotspots();
    }
    public PublishedSnapshot latestPublishedSnapshot() {
        return reportSink.latest();
    }
    public long reportId() {
        return reportId;
    }
    public UnknownStackSnapshot unknownStackSnapshot() {
        long completed;
        boolean finished;
        synchronized (stateLock) {
            completed = completedTickId;
            finished = stopped;
        }
        return reportSink.unknownStackSnapshot(reportId, completed, finished);
    }
    String degradationReason() {
        List<String> reasons = new ArrayList<>();
        if (!detailIngress.degradationReason().isEmpty()) {
            reasons.add(detailIngress.degradationReason());
        }
        appendCount(reasons, detailIngress.droppedSamples(),
                "JFR detail samples dropped by queue backpressure");
        appendCount(reasons, lateDetailSamples, "JFR detail samples arrived too late");
        return String.join("; ", reasons);
    }
    void awaitWorkerDrain() {
        detailIngress.awaitDrain();
    }
    static long jitteredDelay(long intervalNanos, long randomValue) {
        return StacklessPrimarySampler.jitteredDelay(intervalNanos, randomValue);
    }
    private StacklessPrimarySampler.Status sampleCurrent() {
        if (analysisClock.state() != AnalysisClock.State.RUNNING) {
            return stateSnapshot();
        }
        TickSamples expected = currentTick;
        long observedAtNanos = nanoTime.getAsLong();
        PerfWorkTracker.Snapshot snapshot = PerfWorkTracker.snapshot();
        if (expected != null && snapshot == null) {
            snapshot = PerfWorkTracker.unknownSnapshot();
        }
        synchronized (stateLock) {
            StacklessPrimarySampler.Status status = samplerStatusLocked();
            if (status != StacklessPrimarySampler.Status.RUNNING) {
                return status;
            }
            sampleAttempts++;
            if (expected != null && currentTick == expected) {
                recordPrimary(expected, snapshot);
                primaryObservations.record(observedAtNanos, expected.tickId,
                        coarseState(snapshot));
            } else if (expected == null && currentTick == null && snapshot != null) {
                recordPrimary(betweenTicks, snapshot);
            }
            return StacklessPrimarySampler.Status.RUNNING;
        }
    }
    private static void recordPrimary(TickSamples tick, PerfWorkTracker.Snapshot snapshot) {
        boolean unknown = snapshot == PerfWorkTracker.unknownSnapshot();
        tick.sample(snapshot, unknown ? PerfReportData.AttributionSource.FALLBACK_UNKNOWN
                        : PerfReportData.AttributionSource.TASK_CONTEXT,
                unknown ? PerfReportData.Confidence.LOW : PerfReportData.Confidence.HIGH);
        if (unknown) {
            tick.unknownSamples++;
        }
    }
    private static PrimaryObservationRing.CoarseState coarseState(
            PerfWorkTracker.Snapshot snapshot) {
        if (snapshot == PerfWorkTracker.unknownSnapshot()) {
            return PrimaryObservationRing.CoarseState.UNKNOWN;
        }
        return snapshot.type() == TickType.HANDLER
                ? PrimaryObservationRing.CoarseState.HANDLER
                : PrimaryObservationRing.CoarseState.TRACKED;
    }
    private void recordDetail(AsyncStackSample sample) {
        synchronized (stateLock) {
            TickSamples target = targetTick(sample.timestampNanos());
            if (target == null) {
                lateDetailSamples++;
            } else {
                PrimaryObservationRing.CoarseState state = primaryObservations.match(
                        sample.timestampNanos(), target.tickId);
                if (state != null) {
                    target.recordDetail(sample, state);
                }
            }
        }
    }
    private TickSamples targetTick(long timestampNanos) {
        TickSamples open = currentTick;
        if (open != null && timestampNanos >= open.startedAtNanos) {
            return open;
        }
        var iterator = closedTicks.descendingIterator();
        while (iterator.hasNext()) {
            TickSamples tick = iterator.next();
            if (timestampNanos >= tick.startedAtNanos) {
                return timestampNanos <= tick.completedAtNanos ? tick : null;
            }
        }
        return null;
    }
    private void drainReadyTicks() {
        Measurement measurement = measurement();
        List<TickSamples> ready = pollClosedTicks(false);
        if (!ready.isEmpty()) {
            reportSink.accept(ready, buildContext(measurement));
        }
    }
    private List<TickSamples> pollClosedTicks(boolean force) {
        List<TickSamples> result = new ArrayList<>();
        synchronized (stateLock) {
            long cutoff = nanoTime.getAsLong() - detailIngress.settlingNanos();
            while (!closedTicks.isEmpty()) {
                TickSamples tick = closedTicks.getFirst();
                if (!force && tick.completedAtNanos > cutoff) {
                    break;
                }
                result.add(closedTicks.removeFirst());
            }
        }
        return result;
    }
    static boolean shouldPublishReport(long acceptedTicks,
                                       boolean refreshRequested,
                                       boolean firstEntryKind) {
        return PerfSamplerReportSink.shouldPublishReport(
                acceptedTicks, refreshRequested, firstEntryKind);
    }

    private void flushCurrentReport(Measurement measurement, boolean force) {
        reportSink.flush(pollClosedTicks(force), buildContext(measurement));
    }

    private PerfSamplerReportSink.BuildContext buildContext(Measurement measurement) {
        return new PerfSamplerReportSink.BuildContext(
                measurement.elapsedNanos, measurement.sampleAttempts, samplingMetadata());
    }

    private PerfReportData.SamplingMetadata samplingMetadata() {
        return new PerfReportData.SamplingMetadata(
                "Stackless primary + " + detailIngress.backendName(),
                PerfReportData.DurationSemantics.PRIMARY_SAMPLE_DENSITY,
                PerfReportData.Confidence.HIGH, detailIngress.droppedSamples(),
                degradationReason());
    }

    private StacklessPrimarySampler.Status stateSnapshot() {
        synchronized (stateLock) {
            return samplerStatusLocked();
        }
    }

    private StacklessPrimarySampler.Status samplerStatusLocked() {
        AnalysisClock.State state = analysisClock.state();
        if (state == AnalysisClock.State.STOPPED) {
            return StacklessPrimarySampler.Status.STOPPED;
        }
        return state == AnalysisClock.State.RUNNING
                && analysisClock.elapsedNanos() < durationNanos
                ? StacklessPrimarySampler.Status.RUNNING
                : StacklessPrimarySampler.Status.IDLE;
    }

    private Measurement measurement() {
        synchronized (stateLock) {
            return measurementLocked();
        }
    }

    private Measurement measurementLocked() {
        long elapsed = Math.min(analysisClock.elapsedNanos(), durationNanos);
        return new Measurement(elapsed, sampleAttempts);
    }

    private void discardOpenSamples() {
        synchronized (stateLock) {
            currentTick = null;
            betweenTicks = new TickSamples(null);
        }
    }

    private static void appendCount(List<String> reasons, long count, String label) {
        if (count > 0L) {
            reasons.add(count + " " + label);
        }
    }

    private record Measurement(long elapsedNanos, long sampleAttempts) {
    }
    public record PublishedSnapshot(PerfReportData report, PerfWorldHotspotIndex hotspots) {
    }

}
