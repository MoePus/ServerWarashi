package com.moepus.serverwarashi.common.modules.performance.sampling;

import com.moepus.serverwarashi.common.modules.performance.report.PerfReportData;
import com.moepus.serverwarashi.common.modules.performance.report.PerfWorldHotspotIndex;

final class SampleStatistics {
    private static final long MAX_WARMUP_TICKS = 400L;
    private static final int PEAK_BUCKET_TICKS = 40;
    // The current bucket plus 30 complete two-second buckets retains peaks for 60-62 seconds.
    private static final int PEAK_BUCKET_COUNT = 31;
    private static final int MAX_UNSIGNED_SHORT = 0xFFFF;
    private static final int MIN_BURST_SAMPLES = 2;
    private static final double HALF_SAMPLE = 0.5D;
    private static final double NANOS_PER_MILLISECOND = 1_000_000.0D;
    private static final long NO_TICK = Long.MIN_VALUE;

    private final short[] recentTickMaxSamples = new short[PEAK_BUCKET_COUNT];
    private long totalSamples;
    private long activeTick = NO_TICK;
    private int activeTickSamples;
    private long recentPeakBucket = NO_TICK;

    void record(int samples, long tick) {
        if (samples <= 0) {
            return;
        }
        if (tick <= 0L) {
            throw new IllegalArgumentException("Tick must be positive");
        }
        totalSamples += samples;
        if (tick <= MAX_WARMUP_TICKS) {
            return;
        }
        if (activeTick != NO_TICK && tick < activeTick) {
            throw new IllegalArgumentException("Tick must not move backwards");
        }
        if (tick != activeTick) {
            activeTick = tick;
            activeTickSamples = 0;
        }
        activeTickSamples = saturatedAdd(activeTickSamples, samples);
        recordTickMaximum(activeTickSamples, tick);
    }

    PerfReportData.Metric metric(long totalTicks, double nanosPerSample) {
        return metric(totalTicks, totalTicks, nanosPerSample);
    }

    PerfReportData.Metric metric(long currentTick, long measurementTicks,
                                 double nanosPerSample) {
        return metric(SampleMetricMode.STATISTICAL, new SampleMetricContext(
                currentTick, measurementTicks, nanosPerSample, nanosPerSample));
    }

    PerfReportData.Metric metric(SampleMetricMode mode, SampleMetricContext context) {
        double nanosPerSample = context.nanosPerSample(mode);
        double mspt = context.measurementTicks() == 0L ? 0.0D
                : totalSamples * nanosPerSample / NANOS_PER_MILLISECOND / context.measurementTicks();
        int observedMaximum = recentTickMaximum(context.currentTick());
        double maximumSamples = mode == SampleMetricMode.DIRECT_INTERVAL
                ? observedMaximum : statisticalMaximum(observedMaximum);
        double maximum = maximumSamples * nanosPerSample / NANOS_PER_MILLISECOND;
        return new PerfReportData.Metric(mspt, Math.max(mspt, maximum), totalSamples);
    }

    PerfWorldHotspotIndex.Cost hotspotCost(long totalTicks) {
        long scaledMax = Math.round(statisticalMaximum(recentTickMaximum(totalTicks))
                * PerfWorldHotspotIndex.MAX_SAMPLES_SCALE);
        int maxSamplesQ8 = (int) Math.min(Integer.MAX_VALUE, Math.max(0L, scaledMax));
        return new PerfWorldHotspotIndex.Cost(totalSamples, maxSamplesQ8);
    }

    private void recordTickMaximum(int samples, long tick) {
        long bucket = peakBucket(tick);
        advancePeakBuckets(bucket);
        int index = peakIndex(bucket);
        if (samples > unpack(recentTickMaxSamples[index])) {
            recentTickMaxSamples[index] = (short) samples;
        }
    }

    private int recentTickMaximum(long tick) {
        if (tick <= MAX_WARMUP_TICKS) {
            return 0;
        }
        advancePeakBuckets(peakBucket(tick));
        int maximum = 0;
        for (short samples : recentTickMaxSamples) {
            maximum = Math.max(maximum, unpack(samples));
        }
        return maximum;
    }

    private void advancePeakBuckets(long targetBucket) {
        if (recentPeakBucket == NO_TICK) {
            recentPeakBucket = targetBucket;
            return;
        }
        long elapsedBuckets = targetBucket - recentPeakBucket;
        if (elapsedBuckets <= 0L) {
            return;
        }
        if (elapsedBuckets >= PEAK_BUCKET_COUNT) {
            clearAllPeakBuckets();
        } else {
            clearElapsedPeakBuckets(elapsedBuckets);
        }
        recentPeakBucket = targetBucket;
    }

    private void clearAllPeakBuckets() {
        for (int index = 0; index < recentTickMaxSamples.length; index++) {
            recentTickMaxSamples[index] = 0;
        }
    }

    private void clearElapsedPeakBuckets(long elapsedBuckets) {
        for (long offset = 1L; offset <= elapsedBuckets; offset++) {
            recentTickMaxSamples[peakIndex(recentPeakBucket + offset)] = 0;
        }
    }

    private static double statisticalMaximum(int samples) {
        return samples < MIN_BURST_SAMPLES ? 0.0D : samples - HALF_SAMPLE;
    }

    private static int saturatedAdd(int current, int samples) {
        return samples >= MAX_UNSIGNED_SHORT - current ? MAX_UNSIGNED_SHORT : current + samples;
    }

    private static int unpack(short samples) {
        return Short.toUnsignedInt(samples);
    }

    private static long peakBucket(long tick) {
        return (tick - 1L) / PEAK_BUCKET_TICKS;
    }

    private static int peakIndex(long bucket) {
        return Math.floorMod(bucket, PEAK_BUCKET_COUNT);
    }

    double totalWeight() {
        return totalSamples;
    }

    double sampleWeight() {
        return totalSamples;
    }
}
