package com.moepus.serverwarashi.client.state;

import com.moepus.serverwarashi.common.modules.performance.report.PerfReportData;
import com.moepus.serverwarashi.common.network.LoaderControlStatePayload;

final class ClientPerfReportStore<S extends ClientPerfReportStore.View> {
    private final Factory<S> factory;
    private S state;

    ClientPerfReportStore(Factory<S> factory) {
        this.factory = factory;
        state = factory.create(null, 0L, false);
    }

    void update(PerfReportData nextReport, boolean nextProfiling) {
        state = factory.create(nextReport, state.revision() + 1L, nextProfiling);
    }

    boolean applyLoaderControl(LoaderControlStatePayload payload) {
        PerfReportData report = state.report();
        if (!payload.hasAuthoritativeState() || report == null
                || report.reportId() != payload.reportId()) {
            return false;
        }
        PerfReportData updated = ClientPerfReportUpdates.applyLoaderControl(report, payload);
        if (updated == report) {
            return false;
        }
        state = factory.create(updated, state.revision() + 1L, state.profiling());
        return true;
    }

    void clear() {
        state = factory.create(null, state.revision() + 1L, false);
    }

    S snapshot() {
        return state;
    }

    interface View {
        PerfReportData report();

        long revision();

        boolean profiling();
    }

    @FunctionalInterface
    interface Factory<S> {
        S create(PerfReportData report, long revision, boolean profiling);
    }
}
