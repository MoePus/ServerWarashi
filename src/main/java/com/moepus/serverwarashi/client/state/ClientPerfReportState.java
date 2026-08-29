package com.moepus.serverwarashi.client.state;

import com.moepus.serverwarashi.common.modules.performance.report.PerfReportData;
import com.moepus.serverwarashi.common.network.LoaderControlStatePayload;

public final class ClientPerfReportState {
    private static final ClientPerfReportStore<Snapshot> STORE =
            new ClientPerfReportStore<>(Snapshot::new);

    private ClientPerfReportState() {
    }

    static void update(PerfReportData nextReport, boolean nextProfiling) {
        STORE.update(nextReport, nextProfiling);
    }

    public static Snapshot snapshot() {
        return STORE.snapshot();
    }

    static boolean applyLoaderControl(LoaderControlStatePayload payload) {
        return STORE.applyLoaderControl(payload);
    }

    static void clear() {
        STORE.clear();
    }

    public record Snapshot(PerfReportData report, long revision, boolean profiling)
            implements ClientPerfReportStore.View {
    }
}
