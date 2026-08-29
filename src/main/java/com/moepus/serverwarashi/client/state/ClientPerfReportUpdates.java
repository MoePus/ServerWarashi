package com.moepus.serverwarashi.client.state;

import com.moepus.serverwarashi.common.modules.performance.report.PerfReportData;
import com.moepus.serverwarashi.common.network.LoaderControlStatePayload;

import java.util.ArrayList;
import java.util.List;

final class ClientPerfReportUpdates {
    private ClientPerfReportUpdates() {
    }

    static PerfReportData applyLoaderControl(
            PerfReportData report, LoaderControlStatePayload payload) {
        List<PerfReportData.LoaderSourceEntry> updated = new ArrayList<>(report.loaderSources().size());
        boolean changed = false;
        for (PerfReportData.LoaderSourceEntry source : report.loaderSources()) {
            if (!matches(source, payload)) {
                updated.add(source);
                continue;
            }
            var control = new PerfReportData.LoaderControl(
                    payload.controllerId(), payload.ownerPos(),
                    payload.manuallyFrozen(), payload.working());
            PerfReportData.LoaderSourceEntry next = source.withControl(control);
            updated.add(next);
            changed |= !next.equals(source);
        }
        return changed ? report.withLoaderSources(updated) : report;
    }

    private static boolean matches(PerfReportData.LoaderSourceEntry source,
                                   LoaderControlStatePayload payload) {
        PerfReportData.LoaderControl control = source.control();
        return control != null
                && source.dimension().equals(payload.dimension().toString())
                && control.controllerId().equals(payload.controllerId())
                && control.ownerPos().equals(payload.ownerPos());
    }
}
