package com.moepus.serverwarashi.common.modules.performance.sampling;

import com.moepus.serverwarashi.common.modules.performance.report.PerfReportData;

import java.util.random.RandomGenerator;

final class SampleStatistics {
    private static final double P98 = 0.98D;
    private final FloatReservoir activeTickValues = new FloatReservoir();
    private double totalNanos;
    private double sampleWeight;
    private long activeTicks;

    void record(double nanos, double samples, RandomGenerator random) {
        if (nanos <= 0.0D) {
            return;
        }
        totalNanos += nanos;
        sampleWeight += samples;
        activeTicks++;
        activeTickValues.add((float) (nanos / 1_000_000.0D), random);
    }

    PerfReportData.Metric metric(long totalTicks) {
        return metric(totalTicks, p98(totalTicks));
    }

    PerfReportData.Metric activeTickMetric(long totalTicks) {
        return metric(totalTicks, activeTickValues.percentile(P98));
    }

    private PerfReportData.Metric metric(long totalTicks, double p98) {
        double mspt = totalTicks == 0 ? 0.0D : totalNanos / 1_000_000.0D / totalTicks;
        return new PerfReportData.Metric(mspt, p98, Math.round(sampleWeight));
    }

    double totalNanos() {
        return totalNanos;
    }

    double sampleWeight() {
        return sampleWeight;
    }

    private double p98(long totalTicks) {
        if (totalTicks == 0 || activeTicks == 0) {
            return 0.0D;
        }
        long zeroTicks = totalTicks - activeTicks;
        long rank = (long) Math.ceil(P98 * totalTicks);
        if (rank <= zeroTicks) {
            return 0.0D;
        }
        double activeQuantile = (rank - zeroTicks) / (double) activeTicks;
        return activeTickValues.percentile(activeQuantile);
    }
}
