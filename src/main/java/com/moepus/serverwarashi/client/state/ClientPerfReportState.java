package com.moepus.serverwarashi.client.state;

import com.moepus.serverwarashi.common.modules.performance.report.PerfReportData;

public final class ClientPerfReportState {
    private static Snapshot state = new Snapshot(null, 0L, false);

    private ClientPerfReportState() {
    }

    public static void update(PerfReportData nextReport, boolean nextProfiling) {
        state = new Snapshot(nextReport, state.revision() + 1L, nextProfiling);
    }

    public static Snapshot snapshot() {
        return state;
    }

    public static void clear() {
        state = new Snapshot(null, state.revision() + 1L, false);
    }

    public record Snapshot(PerfReportData report, long revision, boolean profiling) {
    }
}
