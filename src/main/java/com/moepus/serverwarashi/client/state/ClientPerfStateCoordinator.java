package com.moepus.serverwarashi.client.state;

import com.moepus.serverwarashi.common.modules.performance.report.PerfReportData;
import com.moepus.serverwarashi.common.modules.performance.report.PerfReportProjection;
import com.moepus.serverwarashi.common.network.LoaderControlStatePayload;
import com.moepus.serverwarashi.common.network.PerfWorldHotspotPayload;

public final class ClientPerfStateCoordinator {
    private ClientPerfStateCoordinator() {
    }

    public static void applyFullReport(PerfReportData report, boolean profiling) {
        ClientPerfReportState.update(report, profiling);
        ClientPerfMapState.update(PerfReportProjection.forMap(report), profiling);
    }

    public static void applySpatialSnapshot(PerfReportData report, boolean profiling) {
        ClientPerfMapState.update(report, profiling);
    }

    public static boolean applyWorldDelta(PerfWorldHotspotPayload payload) {
        return ClientPerfWorldState.apply(payload);
    }

    public static void applyLoaderControl(LoaderControlStatePayload payload) {
        ClientPerfReportState.applyLoaderControl(payload);
        ClientPerfMapState.applyLoaderControl(payload);
    }

    public static void clearAll() {
        ClientPerfReportState.clear();
        ClientPerfMapState.clear();
        ClientPerfWorldState.clear();
    }
}
