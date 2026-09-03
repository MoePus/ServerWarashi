package com.moepus.serverwarashi.common.modules.performance.sampling;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SampleStatisticsTest {
    private static final long FIRST_MEASURED_TICK = 401L;
    private static final double ONE_MILLISECOND = 1_000_000.0D;

    @Test
    void directIntervalUsesRawSingleTickMaximum() {
        SampleStatistics statistics = new SampleStatistics();
        statistics.record(1, FIRST_MEASURED_TICK);
        statistics.record(4, FIRST_MEASURED_TICK + 1L);

        var metric = statistics.metric(SampleMetricMode.DIRECT_INTERVAL,
                new SampleMetricContext(FIRST_MEASURED_TICK + 1L, 2L,
                        10_000_000.0D, 250_000.0D));

        assertEquals(0.625D, metric.mspt(), 0.000_001D);
        assertEquals(1.0D, metric.max(), 0.000_001D);
        assertEquals(5L, metric.samples());
    }

    @Test
    void statisticalMaximumReusesSingleTickHalfSampleEstimate() {
        SampleStatistics statistics = new SampleStatistics();
        statistics.record(20, FIRST_MEASURED_TICK);

        var metric = statistics.metric(FIRST_MEASURED_TICK + 19L, 20L, ONE_MILLISECOND);

        assertEquals(1.0D, metric.mspt(), 0.000_001D);
        assertEquals(19.5D, metric.max(), 0.000_001D);
    }

    @Test
    void samplesSpreadAcrossTicksDoNotProduceAWindowEstimate() {
        SampleStatistics statistics = new SampleStatistics();
        for (long tick = FIRST_MEASURED_TICK; tick < FIRST_MEASURED_TICK + 10L; tick++) {
            statistics.record(1, tick);
        }

        var metric = statistics.metric(FIRST_MEASURED_TICK + 19L, 20L, ONE_MILLISECOND);

        assertEquals(0.5D, metric.mspt(), 0.000_001D);
        assertEquals(metric.mspt(), metric.max(), 0.000_001D);
    }

    @Test
    void combinesMultipleRecordsInTheSameTickBeforeTrackingTheMaximum() {
        SampleStatistics statistics = new SampleStatistics();
        statistics.record(8, FIRST_MEASURED_TICK);
        statistics.record(12, FIRST_MEASURED_TICK);

        var metric = statistics.metric(FIRST_MEASURED_TICK + 19L, 20L, ONE_MILLISECOND);

        assertEquals(1.0D, metric.mspt(), 0.000_001D);
        assertEquals(19.5D, metric.max(), 0.000_001D);
    }

    @Test
    void warmupSamplesNeverBecomeMaximumCandidates() {
        SampleStatistics statistics = new SampleStatistics();
        statistics.record(20, FIRST_MEASURED_TICK - 1L);

        var warmup = statistics.metric(FIRST_MEASURED_TICK - 1L, ONE_MILLISECOND);
        statistics.record(2, FIRST_MEASURED_TICK);
        var measured = statistics.metric(FIRST_MEASURED_TICK, ONE_MILLISECOND);

        assertEquals(warmup.mspt(), warmup.max(), 0.000_001D);
        assertEquals(22.0D / FIRST_MEASURED_TICK, measured.mspt(), 0.000_001D);
        assertEquals(1.5D, measured.max(), 0.000_001D);
    }

    @Test
    void singleTickMaximumExpiresAfterTheRollingWindow() {
        SampleStatistics statistics = new SampleStatistics();
        statistics.record(20, FIRST_MEASURED_TICK);

        var retained = statistics.metric(1_640L, ONE_MILLISECOND);
        var expired = statistics.metric(1_641L, ONE_MILLISECOND);

        assertEquals(19.5D, retained.max(), 0.000_001D);
        assertEquals(20.0D / 1_641.0D, expired.mspt(), 0.000_001D);
        assertEquals(expired.mspt(), expired.max(), 0.000_001D);
        assertEquals(20L, expired.samples());
    }

    @Test
    void recentMaximumReplacesAnExpiredPeak() {
        SampleStatistics statistics = new SampleStatistics();
        statistics.record(20, FIRST_MEASURED_TICK);
        statistics.record(5, 1_620L);

        var metric = statistics.metric(1_641L, ONE_MILLISECOND);

        assertEquals(25.0D / 1_641.0D, metric.mspt(), 0.000_001D);
        assertEquals(4.5D, metric.max(), 0.000_001D);
    }

    @Test
    void unsignedShortStorageRetainsAndSaturatesLargeTickCounts() {
        SampleStatistics retained = new SampleStatistics();
        retained.record(65_535, FIRST_MEASURED_TICK);
        SampleStatistics saturated = new SampleStatistics();
        saturated.record(70_000, FIRST_MEASURED_TICK);

        assertEquals(65_534.5D,
                retained.metric(FIRST_MEASURED_TICK, ONE_MILLISECOND).max(), 0.000_001D);
        assertEquals(65_534.5D,
                saturated.metric(FIRST_MEASURED_TICK, ONE_MILLISECOND).max(), 0.000_001D);
        assertEquals(70_000L,
                saturated.metric(FIRST_MEASURED_TICK, ONE_MILLISECOND).samples());
    }

    @Test
    void oneSampleTickFallsBackToCumulativeAverage() {
        SampleStatistics statistics = new SampleStatistics();
        statistics.record(1, FIRST_MEASURED_TICK);

        var metric = statistics.metric(FIRST_MEASURED_TICK + 19L, 20L, 1_500_000.0D);

        assertEquals(0.075D, metric.mspt(), 0.000_001D);
        assertEquals(metric.mspt(), metric.max(), 0.000_001D);
    }
}
