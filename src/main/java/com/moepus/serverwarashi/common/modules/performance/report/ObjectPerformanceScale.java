package com.moepus.serverwarashi.common.modules.performance.report;

public final class ObjectPerformanceScale {
    private static final double LOG_TWO = Math.log(2.0D);
    private static final double MAX_SAMPLE_VALUE = 1_000_000_000.0D;

    private ObjectPerformanceScale() {
    }

    public static double averageSamples(PerfReportData.Metric metric, long tickCount) {
        if (metric == null || tickCount <= 0L || metric.samples() <= 0L) {
            return 0.0D;
        }
        return Math.min(MAX_SAMPLE_VALUE, metric.samples() / (double) tickCount);
    }

    public static double estimatedMaxSamples(PerfReportData.Metric metric, long tickCount) {
        double average = averageSamples(metric, tickCount);
        if (average == 0.0D || !(metric.mspt() > 0.0D)
                || !Double.isFinite(metric.mspt()) || !Double.isFinite(metric.max())) {
            return average;
        }
        double ratio = Math.max(1.0D, metric.max() / metric.mspt());
        return Math.min(MAX_SAMPLE_VALUE, average * ratio);
    }

    public static double effectiveSamples(PerfReportData.Metric metric, long tickCount) {
        double average = averageSamples(metric, tickCount);
        double burst = Math.max(0.0D, estimatedMaxSamples(metric, tickCount) - average);
        return Math.min(MAX_SAMPLE_VALUE, average + Math.log1p(burst) / LOG_TWO);
    }

    public static double effectiveSamples(long samples, int maxSamplesQ8, long tickCount) {
        if (samples <= 0L || tickCount <= 0L) {
            return 0.0D;
        }
        double averageSamples = samples / (double) tickCount;
        double estimatedMaxSamples = maxSamplesQ8 / (double) PerfWorldHotspotIndex.MAX_SAMPLES_SCALE;
        double excess = Math.max(0.0D, estimatedMaxSamples - averageSamples);
        return averageSamples + Math.log1p(excess) / Math.log(2.0D);
    }
}
