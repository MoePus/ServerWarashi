package com.moepus.serverwarashi.common.modules.performance.analyze;

import com.moepus.serverwarashi.common.core.owner.PerfAttributionSnapshot;
import com.moepus.serverwarashi.common.modules.loaderfreeze.BlockLoaderRef;
import com.moepus.serverwarashi.common.modules.performance.report.PerfReportData;
import com.moepus.serverwarashi.common.modules.performance.sampling.PerfWorkTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PerfSessionControllerTest {
    @Test
    void waitsForTheNextRealServerTickBeforeStartingSampler() {
        PerfSessionController controller = new PerfSessionController(() -> 1_000);
        assertTrue(controller.start(10, null, null));
        assertFalse(PerfWorkTracker.isProfiling);

        controller.onServerTickPre(null);

        assertTrue(PerfWorkTracker.isProfiling);
        controller.stop();
        assertFalse(PerfWorkTracker.isProfiling);
    }

    @Test
    void readsNeoForgeTickTimeRingAtCurrentTickIndex() {
        long[] tickTimes = {10L, 20L, 30L, 40L};

        assertEquals(30L, PerfSessionController.ringValue(tickTimes, 6));
        assertEquals(10L, PerfSessionController.ringValue(tickTimes, 8));
    }

    @Test
    void startPublishesAttributionBeforeFirstTickAndBlocksClear() {
        PerfSessionController controller = new PerfSessionController(() -> 1_000);
        var owner = new PerfAttributionSnapshot.Owner(
                PerfReportData.AttributionKind.LOADER, "loader", "Loader", false, 0L, null);
        var source = new PerfAttributionSnapshot.Source("source", "Loader", null);
        var attribution = PerfAttributionSnapshot.create("minecraft:overworld", Map.of(
                ChunkPos.asLong(3, 4), List.of(new PerfAttributionSnapshot.Bucket(owner, source, 0, 0))));

        assertTrue(controller.clearLastReport());
        assertTrue(controller.start(10, null, attribution));
        assertFalse(controller.clearLastReport());
        PerfReportData current = controller.currentPublishedSnapshot().report();
        assertEquals(0L, current.tickCount());
        assertEquals(1, current.loaderSources().size());
        controller.stop();
        assertTrue(controller.clearLastReport());
    }

    @Test
    void materializesLiveLoaderControlForLateSubscribers() {
        ResourceLocation dimensionId = ResourceLocation.parse("minecraft:overworld");
        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, dimensionId);
        BlockLoaderRef ref = new BlockLoaderRef(
                dimension, ResourceLocation.parse("test:loader"), new BlockPos(8, 64, 8));
        var owner = new PerfAttributionSnapshot.Owner(
                PerfReportData.AttributionKind.LOADER, "loader", "Loader", false, 0L, null);
        var source = new PerfAttributionSnapshot.Source("source", "Loader",
                new PerfAttributionSnapshot.Position(dimensionId.toString(), 8, 64, 8), ref);
        var attribution = PerfAttributionSnapshot.create(dimensionId.toString(), Map.of(
                        ChunkPos.asLong(0, 0), List.of(new PerfAttributionSnapshot.Bucket(owner, source, 0, 0))),
                Map.of(ref, new PerfAttributionSnapshot.LoaderControl(ref, true, false)));
        var staleControl = new PerfReportData.LoaderControl(
                ref.controllerId(), ref.ownerPos(), false, true);
        var loader = new PerfReportData.LoaderSourceEntry(dimensionId.toString(), "source", "Loader",
                new PerfReportData.LoaderPosition(8, 64, 8), 0,
                List.of(new PerfReportData.LoaderRegion(0, 0, 1, 1)), staleControl);
        var report = new PerfReportData(7L, 0.0D, 0L, 0L, 0L, 0L, 1_000,
                0, 0, 0, 0, List.of(), List.of(), List.of(), List.of(loader));

        PerfReportData refreshed = TicketPerfRuntime.withLiveLoaderControls(report, attribution);

        assertEquals(new PerfReportData.LoaderControl(
                ref.controllerId(), ref.ownerPos(), true, false),
                refreshed.loaderSources().getFirst().control());
        var missing = PerfAttributionSnapshot.create(dimensionId.toString(), Map.of(
                ChunkPos.asLong(0, 0), List.of(new PerfAttributionSnapshot.Bucket(owner, source, 0, 0))));
        assertNull(TicketPerfRuntime.withLiveLoaderControls(report, missing)
                .loaderSources().getFirst().control());
    }
}
