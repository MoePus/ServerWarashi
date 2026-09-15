package com.moepus.serverwarashi.client.state;

import com.moepus.serverwarashi.common.modules.performance.report.PerfReportData;
import com.moepus.serverwarashi.common.network.LoaderControlStatePayload;

public final class ClientPerfMapState {
    private static Snapshot state = new Snapshot(null, 0L, false);

    private ClientPerfMapState() {
    }

    static void update(PerfReportData report, boolean profiling) {
        state = new Snapshot(report, state.revision() + 1L, profiling);
    }

    public static Snapshot snapshot() {
        return state;
    }

    static boolean applyLoaderControl(LoaderControlStatePayload payload) {
        PerfReportData report = state.report();
        if (!payload.hasAuthoritativeState() || report == null
                || report.reportId() != payload.reportId()) {
            return false;
        }
        PerfReportData updated = ClientPerfReportUpdates.applyLoaderControl(report, payload);
        if (updated == report) {
            return false;
        }
        state = new Snapshot(updated, state.revision() + 1L, state.profiling());
        return true;
    }

    static void clear() {
        state = new Snapshot(null, state.revision() + 1L, false);
    }

    public record Snapshot(PerfReportData report, long revision, boolean profiling) {
    }
}
