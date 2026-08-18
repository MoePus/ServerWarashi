package com.moepus.serverwarashi.common.modules.performance.report;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChunkPerformanceRiskTest {
    @Test
    void identifiesRedByMetricWithoutClientClasses() {
        assertFalse(ChunkPerformanceRisk.isRedOrHotter(
                new PerfReportData.Metric(1.0D, 1.0D, 10L)));
        assertTrue(ChunkPerformanceRisk.isRedOrHotter(
                new PerfReportData.Metric(2.0D, 2.0D, 10L)));
    }

    @Test
    void aSingleBurstRemainsBelowRed() {
        assertFalse(ChunkPerformanceRisk.isRedOrHotter(
                new PerfReportData.Metric(0.0D, 50.0D, 1L)));
    }
}
