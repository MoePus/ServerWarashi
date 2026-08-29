package com.moepus.serverwarashi.common.modules.performance.report;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ObjectPerformanceScaleTest {
    @Test
    void usesAverageSamplesAsIntegerColorAnchors() {
        for (int samples = 1; samples <= 5; samples++) {
            var metric = new PerfReportData.Metric(samples, samples, samples * 20L);
            assertEquals(samples, ObjectPerformanceScale.effectiveSamples(metric, 20L), 0.000_001D);
        }
    }

    @Test
    void addsLogarithmicBurstContribution() {
        var metric = new PerfReportData.Metric(1.0D, 5.0D, 20L);

        assertEquals(1.0D, ObjectPerformanceScale.averageSamples(metric, 20L), 0.000_001D);
        assertEquals(5.0D, ObjectPerformanceScale.estimatedMaxSamples(metric, 20L), 0.000_001D);
        assertEquals(1.0D + Math.log(5.0D) / Math.log(2.0D),
                ObjectPerformanceScale.effectiveSamples(metric, 20L), 0.000_001D);
    }

    @Test
    void compactSampleCountsMatchMetricScale() {
        double expected = 1.0D + Math.log(5.0D) / Math.log(2.0D);
        assertEquals(expected,
                ObjectPerformanceScale.effectiveSamples(20L, 5 * 256, 20L), 0.000_001D);
    }

    @Test
    void rejectsInvalidDensityInputs() {
        assertEquals(0.0D, ObjectPerformanceScale.effectiveSamples(null, 20L));
        assertEquals(0.0D, ObjectPerformanceScale.effectiveSamples(
                new PerfReportData.Metric(1.0D, 1.0D, 10L), 0L));
        assertEquals(1.0D, ObjectPerformanceScale.effectiveSamples(
                new PerfReportData.Metric(Double.NaN, Double.POSITIVE_INFINITY, 20L), 20L));
    }
}
