package com.moepus.serverwarashi.client.state;

import com.moepus.serverwarashi.common.modules.performance.report.PerfReportData;
import com.moepus.serverwarashi.common.modules.performance.report.PerfReportProjection;
import com.moepus.serverwarashi.common.modules.performance.report.PerfWorldHotspotIndex;
import com.moepus.serverwarashi.common.modules.performance.sampling.TickDetailKind;
import com.moepus.serverwarashi.common.modules.performance.sampling.TickType;
import com.moepus.serverwarashi.common.network.PerfWorldHotspotPayload;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientPerfStateCoordinatorTest {
    @AfterEach
    void clear() {
        ClientPerfStateCoordinator.clearAll();
    }

    @Test
    void fullAndWorldSnapshotsUpdateOnlyTheirOwnedState() {
        PerfReportData full = report();
        ClientPerfStateCoordinator.applyFullReport(full, true);

        assertEquals(full, ClientPerfReportState.snapshot().report());
        assertTrue(ClientPerfMapState.snapshot().report().tickables().isEmpty());
        assertEquals(1, ClientPerfMapState.snapshot().report().loaderSources().size());

        ClientPerfStateCoordinator.applySpatialSnapshot(
                PerfReportProjection.forWorld(full), false);

        assertEquals(full, ClientPerfReportState.snapshot().report());
        assertFalse(ClientPerfMapState.snapshot().profiling());
        assertEquals(1, ClientPerfMapState.snapshot().report().chunks().size());
        assertTrue(ClientPerfMapState.snapshot().report().loaderSources().isEmpty());
    }

    @Test
    void clearAllResetsReportMapAndWorldStores() {
        ClientPerfStateCoordinator.applyFullReport(report(), true);
        var entry = new PerfWorldHotspotPayload.Entry(
                PerfWorldHotspotIndex.Key.entity(7), TickDetailKind.ENTITY, 1, 1L, 2L, 256);
        ClientPerfStateCoordinator.applyWorldDelta(new PerfWorldHotspotPayload(
                1L, 0L, 1L, 20L, 1_000_000L,
                ResourceLocation.parse("minecraft:overworld"), true,
                List.of(entry), List.of()));

        ClientPerfStateCoordinator.clearAll();

        assertNull(ClientPerfReportState.snapshot().report());
        assertNull(ClientPerfMapState.snapshot().report());
        assertTrue(ClientPerfWorldState.snapshot().hotspots().isEmpty());
    }

    private static PerfReportData report() {
        PerfReportData.Metric metric = new PerfReportData.Metric(1.0D, 2.0D, 2L);
        return new PerfReportData(1L, 20.0D, 1_000L, 5_000L,
                20L, 100L, 1_000, 0, 0, 0, 0,
                List.of(new PerfReportData.ChunkEntry("minecraft:overworld", 0, 0,
                        1, 0, List.of(), List.of(), metric)),
                List.of(new PerfReportData.TickableEntry(TickType.ENTITY,
                        "minecraft:overworld", "Cow", "minecraft:cow",
                        TickDetailKind.ENTITY, 7, true, 0, 64, 0, false, "", metric)),
                List.of(), List.of(new PerfReportData.LoaderSourceEntry(
                        "minecraft:overworld", "loader", "Loader", null, 0, List.of())));
    }
}
