package com.moepus.serverwarashi.client.state;

import com.moepus.serverwarashi.common.modules.performance.report.PerfWorldHotspotIndex;
import com.moepus.serverwarashi.common.modules.performance.sampling.TickDetailKind;
import com.moepus.serverwarashi.common.network.PerfWorldHotspotPayload;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientPerfWorldStateTest {
    private static final ResourceLocation DIMENSION =
            ResourceLocation.parse("minecraft:overworld");
    private static final PerfWorldHotspotIndex.Key KEY = PerfWorldHotspotIndex.Key.entity(17);

    @AfterEach
    void clearState() {
        ClientPerfWorldState.clear();
    }

    @Test
    void scalarOnlyDeltaRefreshesMetricWithoutRepeatingEntry() {
        var entry = new PerfWorldHotspotPayload.Entry(
                KEY, TickDetailKind.ENTITY, 4, 1L, 20L, 512);
        assertTrue(ClientPerfWorldState.apply(payload(0L, 20L, 20L, 1_000_000L,
                true, List.of(entry), List.of())));
        assertEquals(1.0D, metric().mspt(), 1.0E-9D);

        assertTrue(ClientPerfWorldState.apply(payload(20L, 40L, 40L, 1_200_000L,
                false, List.of(), List.of())));
        assertEquals(0.6D, metric().mspt(), 1.0E-9D);
        assertEquals(1, ClientPerfWorldState.snapshot().hotspots().size());
    }

    @Test
    void rejectsDeltaWithWrongBaseAndAppliesRemoval() {
        var entry = new PerfWorldHotspotPayload.Entry(
                KEY, TickDetailKind.ENTITY, 4, 1L, 2L, 256);
        ClientPerfWorldState.apply(payload(
                0L, 20L, 20L, 1_000_000L, true, List.of(entry), List.of()));
        assertFalse(ClientPerfWorldState.apply(payload(
                19L, 40L, 40L, 1_000_000L, false, List.of(), List.of())));
        assertTrue(ClientPerfWorldState.apply(payload(
                20L, 40L, 40L, 1_000_000L, false, List.of(), List.of(KEY))));
        assertTrue(ClientPerfWorldState.snapshot().hotspots().isEmpty());
    }

    @Test
    void rejectsOlderFullSnapshotButAcceptsANewStreamEpoch() {
        var entry = new PerfWorldHotspotPayload.Entry(
                KEY, TickDetailKind.ENTITY, 4, 1L, 2L, 256);
        assertTrue(ClientPerfWorldState.apply(new PerfWorldHotspotPayload(
                7L, 0L, 20L, 20L, 1_000_000L, DIMENSION,
                true, List.of(entry), List.of())));

        assertFalse(ClientPerfWorldState.apply(new PerfWorldHotspotPayload(
                7L, 0L, 10L, 10L, 1_000_000L, DIMENSION,
                true, List.of(), List.of())));
        assertEquals(20L, ClientPerfWorldState.snapshot().protocolRevision());

        assertTrue(ClientPerfWorldState.apply(new PerfWorldHotspotPayload(
                8L, 0L, 1L, 1L, 1_000_000L, DIMENSION,
                true, List.of(), List.of())));
        assertEquals(8L, ClientPerfWorldState.snapshot().streamEpoch());
        assertEquals(1L, ClientPerfWorldState.snapshot().protocolRevision());
    }

    @Test
    void dividesEntityCostByTicksSinceItAppeared() {
        var entry = new PerfWorldHotspotPayload.Entry(
                KEY, TickDetailKind.ENTITY, 4, 91L, 20L, 512);

        assertTrue(ClientPerfWorldState.apply(payload(
                0L, 100L, 100L, 1_000_000L, true, List.of(entry), List.of())));

        assertEquals(2.0D, metric().mspt(), 1.0E-9D);
        assertEquals(10L, ClientPerfWorldState.snapshot().hotspots().get(0).measurementTicks());
    }

    private static com.moepus.serverwarashi.common.modules.performance.report.PerfReportData.Metric metric() {
        return ClientPerfWorldState.snapshot().hotspots().get(0).metric();
    }

    private static PerfWorldHotspotPayload payload(
            long baseRevision,
            long revision,
            long tickCount,
            long nanosPerSample,
            boolean full,
            List<PerfWorldHotspotPayload.Entry> upserts,
            List<PerfWorldHotspotIndex.Key> removals) {
        return new PerfWorldHotspotPayload(7L, baseRevision, revision, tickCount,
                nanosPerSample, DIMENSION, full, upserts, removals);
    }
}
