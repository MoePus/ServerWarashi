package com.moepus.serverwarashi.common.modules.performance.sampling;

import com.moepus.serverwarashi.common.modules.performance.report.PerfReportData;

final class SampleStatistics {
    private static final int WINDOW_TICKS = 20;
    private static final int WINDOW_STAGGER_TICKS = WINDOW_TICKS / 2;
    private static final long NO_WINDOW = Long.MIN_VALUE;
    private static final double MIN_BURST_SAMPLES = 2.0D;
    private static final double HALF_SAMPLE = 0.5D;
    private static final double NANOS_PER_MILLISECOND = 1_000_000.0D;
    private double totalNanos;
    private double sampleWeight;
    private double maxBurstNanos;
    private long primaryWindow = NO_WINDOW;
    private double primaryNanos;
    private double primarySamples;
    private long staggeredWindow = NO_WINDOW;
    private double staggeredNanos;
    private double staggeredSamples;
    private double maxEstimatedMspt;

    void record(double nanos, double samples, long tick) {
        if (nanos <= 0.0D || samples <= 0.0D) {
            return;
        }
        if (tick <= 0L) {
            throw new IllegalArgumentException("Tick must be positive");
        }
        advanceWindows(tick);
        totalNanos += nanos;
        sampleWeight += samples;
        recordBurst(nanos, samples);
        primaryNanos += nanos;
        primarySamples += samples;
        if (tick > WINDOW_STAGGER_TICKS) {
            staggeredNanos += nanos;
            staggeredSamples += samples;
        }
    }

    PerfReportData.Metric metric(long totalTicks) {
        advanceWindows(totalTicks);
        double mspt = totalTicks == 0 ? 0.0D : totalNanos / NANOS_PER_MILLISECOND / totalTicks;
        double estimatedMax = Math.max(maxEstimatedMspt,
                Math.max(estimate(primaryNanos, primarySamples),
                        estimate(staggeredNanos, staggeredSamples)));
        estimatedMax = Math.max(estimatedMax, maxBurstNanos / NANOS_PER_MILLISECOND);
        return new PerfReportData.Metric(mspt, Math.max(mspt, estimatedMax), Math.round(sampleWeight));
    }

    private void recordBurst(double nanos, double samples) {
        if (samples < MIN_BURST_SAMPLES) {
            return;
        }
        double effectiveResolution = nanos / samples;
        double burstNanos = nanos - HALF_SAMPLE * effectiveResolution;
        maxBurstNanos = Math.max(maxBurstNanos, burstNanos);
    }

    private static double estimate(double nanos, double samples) {
        if (samples <= 0.0D) {
            return 0.0D;
        }
        double upperHits = samples + 2.0D * Math.sqrt(samples + 1.0D) + 1.0D;
        double windowMspt = nanos / NANOS_PER_MILLISECOND / WINDOW_TICKS;
        return windowMspt * upperHits / samples;
    }

    private void advanceWindows(long tick) {
        if (tick <= 0L) {
            return;
        }
        long nextPrimary = (tick - 1L) / WINDOW_TICKS;
        if (primaryWindow != nextPrimary) {
            retainPrimaryAndReset(nextPrimary);
        }
        if (tick <= WINDOW_STAGGER_TICKS) {
            return;
        }
        long nextStaggered = (tick - WINDOW_STAGGER_TICKS - 1L) / WINDOW_TICKS;
        if (staggeredWindow != nextStaggered) {
            retainStaggeredAndReset(nextStaggered);
        }
    }

    private void retainPrimaryAndReset(long nextWindow) {
        maxEstimatedMspt = Math.max(maxEstimatedMspt, estimate(primaryNanos, primarySamples));
        primaryWindow = nextWindow;
        primaryNanos = 0.0D;
        primarySamples = 0.0D;
    }

    private void retainStaggeredAndReset(long nextWindow) {
        maxEstimatedMspt = Math.max(maxEstimatedMspt, estimate(staggeredNanos, staggeredSamples));
        staggeredWindow = nextWindow;
        staggeredNanos = 0.0D;
        staggeredSamples = 0.0D;
    }

    double totalNanos() {
        return totalNanos;
    }

    double sampleWeight() {
        return sampleWeight;
    }
}
