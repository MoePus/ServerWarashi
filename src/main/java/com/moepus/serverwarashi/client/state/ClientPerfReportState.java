package com.moepus.serverwarashi.client.state;

import com.moepus.serverwarashi.common.modules.performance.report.PerfReportData;
import com.moepus.serverwarashi.common.network.LoaderControlStatePayload;

import java.util.ArrayList;
import java.util.List;

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

    public static boolean applyLoaderControl(LoaderControlStatePayload payload) {
        PerfReportData report = state.report();
        if (!payload.hasAuthoritativeState() || report == null
                || report.reportId() != payload.reportId()) {
            return false;
        }
        List<PerfReportData.LoaderSourceEntry> updated = new ArrayList<>(report.loaderSources().size());
        boolean matched = false;
        boolean changed = false;
        for (PerfReportData.LoaderSourceEntry source : report.loaderSources()) {
            if (!matches(source, payload)) {
                updated.add(source);
                continue;
            }
            matched = true;
            PerfReportData.LoaderControl control = new PerfReportData.LoaderControl(
                    payload.controllerId(), payload.ownerPos(),
                    payload.manuallyFrozen(), payload.working());
            PerfReportData.LoaderSourceEntry next = source.withControl(control);
            updated.add(next);
            changed |= !next.equals(source);
        }
        if (!matched || !changed) {
            return false;
        }
        state = new Snapshot(report.withLoaderSources(updated), state.revision() + 1L, state.profiling());
        return true;
    }

    public static void clear() {
        state = new Snapshot(null, state.revision() + 1L, false);
    }

    private static boolean matches(PerfReportData.LoaderSourceEntry source,
                                   LoaderControlStatePayload payload) {
        PerfReportData.LoaderControl control = source.control();
        return control != null
                && source.dimension().equals(payload.dimension().toString())
                && control.controllerId().equals(payload.controllerId())
                && control.ownerPos().equals(payload.ownerPos());
    }

    public record Snapshot(PerfReportData report, long revision, boolean profiling) {
    }
}
