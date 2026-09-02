package com.moepus.serverwarashi.common.modules.performance.report;

import java.util.List;

public final class PerfReportProjection {
    private PerfReportProjection() {
    }

    public static PerfReportData forMap(PerfReportData report) {
        return project(report, report.loaderSources(), true);
    }

    public static PerfReportData forWorld(PerfReportData report) {
        return project(report, List.of(), false);
    }

    private static PerfReportData project(
            PerfReportData report,
            List<PerfReportData.LoaderSourceEntry> loaders,
            boolean includeLoaderOmissions) {
        return new PerfReportData(report.reportId(), report.serverMspt(), report.elapsedMillis(),
                report.generatedAtEpochMillis(), report.tickCount(), report.totalSamples(),
                report.intervalMicros(), report.sampling(), report.omittedChunks(), 0, 0,
                includeLoaderOmissions ? report.omittedLoaderSources() : 0,
                report.chunks(), List.of(), List.of(), loaders);
    }
}
