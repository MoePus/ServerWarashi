package com.moepus.serverwarashi.common.modules.performance.sampling;

import org.junit.jupiter.api.Test;

import java.util.SplittableRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SampleStatisticsTest {
    @Test
    void msptUsesAllTicksAndP98IncludesZeroTicks() {
        SampleStatistics statistics = new SampleStatistics();
        statistics.record(10_000_000.0D, 2.0D, new SplittableRandom(1L));

        var sparse = statistics.metric(100L);
        assertEquals(0.1D, sparse.mspt(), 0.000_001D);
        assertEquals(0.0D, sparse.p98(), 0.0D);
        assertEquals(2L, sparse.samples());

        var dense = statistics.metric(20L);
        assertEquals(10.0D, dense.p98(), 0.000_001D);

        var active = statistics.activeTickMetric(100L);
        assertEquals(0.1D, active.mspt(), 0.000_001D);
        assertEquals(10.0D, active.p98(), 0.000_001D);
    }
}
