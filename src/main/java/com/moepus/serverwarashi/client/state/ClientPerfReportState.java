package com.moepus.serverwarashi.client.state;

import com.moepus.serverwarashi.common.modules.performance.report.PerfReportData;

public final class ClientPerfReportState {
    private static PerfReportData report;
    private static long revision;
    private static boolean profiling;

    private ClientPerfReportState() {
    }

    public static void update(PerfReportData nextReport, boolean nextProfiling) {
        report = nextReport;
        profiling = nextProfiling;
        revision++;
    }

    public static Snapshot snapshot() {
        return new Snapshot(report, revision, profiling);
    }

    public static void clear() {
        report = null;
        profiling = false;
        revision++;
    }

    public record Snapshot(PerfReportData report, long revision, boolean profiling) {
    }
}
