package com.moepus.serverwarashi.common.modules.performance.sampling;

import com.moepus.serverwarashi.common.modules.performance.report.PerfReportData;

final class SampleStatistics {
    private static final int WINDOW_TICKS = 20;
    private static final int WINDOW_STAGGER_TICKS = WINDOW_TICKS / 2;
    private static final long NO_WINDOW = Long.MIN_VALUE;
    private static final double MIN_BURST_SAMPLES = 2.0D;
    private static final double HALF_SAMPLE = 0.5D;
    private static final double NANOS_PER_MILLISECOND = 1_000_000.0D;
    private double totalSamples;
    private double maxBurstSamples;
    private long primaryWindow = NO_WINDOW;
    private double primarySamples;
    private long staggeredWindow = NO_WINDOW;
    private double staggeredSamples;
    private double maxEstimatedSamples;

    void record(double samples, long tick) {
        if (samples <= 0.0D) {
            return;
        }
        if (tick <= 0L) {
            throw new IllegalArgumentException("Tick must be positive");
        }
        advanceWindows(tick);
        totalSamples += samples;
        recordBurst(samples);
        primarySamples += samples;
        if (tick > WINDOW_STAGGER_TICKS) {
            staggeredSamples += samples;
        }
    }

    PerfReportData.Metric metric(long totalTicks, double nanosPerSample) {
        advanceWindows(totalTicks);
        double mspt = totalTicks == 0 ? 0.0D
                : totalSamples * nanosPerSample / NANOS_PER_MILLISECOND / totalTicks;
        double estimatedMaxSamples = Math.max(maxEstimatedSamples,
                Math.max(estimateUpperSamples(primarySamples),
                        estimateUpperSamples(staggeredSamples)));
        estimatedMaxSamples = Math.max(estimatedMaxSamples, maxBurstSamples);
        double estimatedMax = estimatedMaxSamples * nanosPerSample / NANOS_PER_MILLISECOND;
        return new PerfReportData.Metric(mspt, Math.max(mspt, estimatedMax), Math.round(totalSamples));
    }

    private void recordBurst(double samples) {
        if (samples < MIN_BURST_SAMPLES) {
            return;
        }
        maxBurstSamples = Math.max(maxBurstSamples, samples - HALF_SAMPLE);
    }

    private static double estimateUpperSamples(double samples) {
        if (samples <= 0.0D) {
            return 0.0D;
        }
        double upperHits = samples + 2.0D * Math.sqrt(samples + 1.0D) + 1.0D;
        return upperHits / WINDOW_TICKS;
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
        maxEstimatedSamples = Math.max(maxEstimatedSamples, estimateUpperSamples(primarySamples));
        primaryWindow = nextWindow;
        primarySamples = 0.0D;
    }

    private void retainStaggeredAndReset(long nextWindow) {
        maxEstimatedSamples = Math.max(maxEstimatedSamples, estimateUpperSamples(staggeredSamples));
        staggeredWindow = nextWindow;
        staggeredSamples = 0.0D;
    }

    double totalWeight() {
        return totalSamples;
    }

    double sampleWeight() {
        return totalSamples;
    }
}
