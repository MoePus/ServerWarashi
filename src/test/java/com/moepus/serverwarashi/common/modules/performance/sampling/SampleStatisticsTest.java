package com.moepus.serverwarashi.common.modules.performance.sampling;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SampleStatisticsTest {
    @Test
    void estimatesMaxFromRepeatedHitProbability() {
        SampleStatistics statistics = new SampleStatistics();
        for (long tick = 1L; tick <= 10L; tick++) {
            statistics.record(1_000_000.0D, 1.0D, tick);
        }

        var metric = statistics.metric(20L);

        assertEquals(0.5D, metric.mspt(), 0.000_001D);
        assertEquals(0.882D, metric.max(), 0.002D);
        assertEquals(10L, metric.samples());
    }

    @Test
    void completedWindowMaxDoesNotDecayDuringLaterQuietWindows() {
        SampleStatistics statistics = new SampleStatistics();
        for (long tick = 1L; tick <= 10L; tick++) {
            statistics.record(1_000_000.0D, 1.0D, tick);
        }

        var metric = statistics.metric(100L);

        assertEquals(0.1D, metric.mspt(), 0.000_001D);
        assertEquals(0.882D, metric.max(), 0.002D);
    }

    @Test
    void staggeredWindowCapturesWorkAcrossPrimaryBoundary() {
        SampleStatistics statistics = new SampleStatistics();
        for (long tick = 16L; tick <= 25L; tick++) {
            statistics.record(1_000_000.0D, 1.0D, tick);
        }

        var metric = statistics.metric(30L);

        assertEquals(0.882D, metric.max(), 0.002D);
    }

    @Test
    void primaryWindowCapturesWorkAcrossStaggeredBoundary() {
        SampleStatistics statistics = new SampleStatistics();
        for (long tick = 26L; tick <= 35L; tick++) {
            statistics.record(1_000_000.0D, 1.0D, tick);
        }

        var metric = statistics.metric(40L);

        assertEquals(0.882D, metric.max(), 0.002D);
    }

    @Test
    void staggeredWindowExcludesTheFirstTenTicks() {
        SampleStatistics beforeBoundary = new SampleStatistics();
        for (long tick = 1L; tick <= 10L; tick++) {
            beforeBoundary.record(1_000_000.0D, 1.0D, tick);
        }
        for (long tick = 21L; tick <= 30L; tick++) {
            beforeBoundary.record(1_000_000.0D, 1.0D, tick);
        }

        SampleStatistics onBoundary = new SampleStatistics();
        for (long tick = 11L; tick <= 30L; tick++) {
            onBoundary.record(1_000_000.0D, 1.0D, tick);
        }

        assertEquals(0.882D, beforeBoundary.metric(30L).max(), 0.002D);
        assertEquals(1.508D, onBoundary.metric(30L).max(), 0.002D);
    }

    @Test
    void handlesTwentyMsptWorkWithoutClampingOrOverflow() {
        SampleStatistics statistics = new SampleStatistics();
        for (long tick = 1L; tick <= 20L; tick++) {
            statistics.record(20_000_000.0D, 20.0D, tick);
        }

        var metric = statistics.metric(20L);

        assertEquals(20.0D, metric.mspt(), 0.000_001D);
        assertTrue(Double.isFinite(metric.max()));
        assertEquals(22.052D, metric.max(), 0.002D);
    }

    @Test
    void capturesRareMultiSampleBurstWithinOneTick() {
        SampleStatistics statistics = new SampleStatistics();
        statistics.record(20_000_000.0D, 20.0D, 10L);

        var metric = statistics.metric(20L);

        assertEquals(1.0D, metric.mspt(), 0.000_001D);
        assertEquals(19.5D, metric.max(), 0.000_001D);
    }

    @Test
    void leavesSingleSampleSpikeToWindowEstimator() {
        SampleStatistics statistics = new SampleStatistics();
        statistics.record(1_500_000.0D, 1.0D, 10L);

        var metric = statistics.metric(20L);

        assertEquals(0.075D, metric.mspt(), 0.000_001D);
        assertEquals(0.362D, metric.max(), 0.002D);
    }

    @Test
    void resumesAfterSkippingSeveralEmptyWindows() {
        SampleStatistics statistics = new SampleStatistics();
        for (long tick = 1L; tick <= 10L; tick++) {
            statistics.record(1_000_000.0D, 1.0D, tick);
        }
        statistics.metric(100L);
        for (long tick = 101L; tick <= 105L; tick++) {
            statistics.record(1_000_000.0D, 1.0D, tick);
        }

        var metric = statistics.metric(105L);

        assertEquals(15.0D / 105.0D, metric.mspt(), 0.000_001D);
        assertEquals(0.882D, metric.max(), 0.002D);
    }
}
