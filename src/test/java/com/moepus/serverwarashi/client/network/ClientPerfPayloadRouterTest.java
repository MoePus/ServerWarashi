package com.moepus.serverwarashi.client.network;

import com.moepus.serverwarashi.client.state.ClientPerfMapState;
import com.moepus.serverwarashi.client.state.ClientPerfStateCoordinator;
import com.moepus.serverwarashi.common.modules.performance.report.PerfReportData;
import com.moepus.serverwarashi.common.network.PerfSpatialSnapshotPayload;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ClientPerfPayloadRouterTest {
    @AfterEach
    void clearState() {
        ClientPerfStateCoordinator.clearAll();
    }

    @Test
    void authoritativeSpatialStopUpdatesStateAndEndsLiveAnalysis() {
        PerfReportData report = new PerfReportData(
                9L, 20.0D, 1_000L, 5_000L, 20L, 10L, 1_000,
                0, 0, 0, 0, List.of(), List.of(), List.of(), List.of());
        var payload = new PerfSpatialSnapshotPayload(report, false);
        List<Boolean> liveChanges = new ArrayList<>();

        ClientPerfPayloadRouter.applySpatialSnapshot(payload, liveChanges::add);

        assertEquals(report, ClientPerfMapState.snapshot().report());
        assertFalse(ClientPerfMapState.snapshot().profiling());
        assertEquals(List.of(false), liveChanges);
    }
}
