package com.moepus.serverwarashi.common.modules.performance.report;

public final class ChunkPerformanceRisk {
    public static final double MSPT_THRESHOLD = 0.5D;
    public static final double MAX_THRESHOLD = 3.0D;
    public static final double RED_THRESHOLD = 4.0D;
    private static final double LOG_TWO = Math.log(2.0D);
    private static final double MAX_BURST_CONTRIBUTION = RED_THRESHOLD - 0.25D;

    private ChunkPerformanceRisk() {
    }

    public static boolean isRedOrHotter(PerfReportData.Metric metric) {
        return metric != null && severity(metric) >= RED_THRESHOLD;
    }

    public static double severity(PerfReportData.Metric metric) {
        double normalizedMspt = metric.mspt() / MSPT_THRESHOLD;
        double burst = Math.max(0.0D, metric.max() - metric.mspt()) / MAX_THRESHOLD;
        double burstContribution = Math.min(
                MAX_BURST_CONTRIBUTION, Math.log1p(burst) / LOG_TWO);
        return normalizedMspt + burstContribution;
    }
}
