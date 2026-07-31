package com.moepus.serverwarashi.common.core.owner;

import com.moepus.serverwarashi.common.modules.performance.report.PerfReportData;
import com.moepus.serverwarashi.common.modules.loaderfreeze.BlockLoaderRef;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PerfAttributionSnapshotTest {
    @Test
    void expandsLevel31TargetsByOneBlockTickingChunk() {
        assertEquals(1, LoaderTicketInfluence.blockTickingRadius(31));
        assertEquals(0, LoaderTicketInfluence.blockTickingRadius(32));
        assertEquals(0, LoaderTicketInfluence.blockTickingRadius(33));
        Set<Long> influenced = new HashSet<>();
        for (int z = -2; z <= 2; z++) {
            for (int x = -2; x <= 2; x++) {
                LoaderTicketInfluence.visit(ChunkPos.asLong(x, z),
                        LoaderTicketInfluence.blockTickingRadius(31), influenced::add);
            }
        }

        assertEquals(49, influenced.size());
        assertTrue(influenced.contains(ChunkPos.asLong(-3, -3)));
        assertTrue(influenced.contains(ChunkPos.asLong(3, 3)));
    }

    @Test
    void compressesFilledLoaderSquareIntoOneRegion() {
        Map<Long, List<PerfAttributionSnapshot.Bucket>> chunks = new HashMap<>();
        for (int z = -3; z <= 3; z++) {
            for (int x = -3; x <= 3; x++) {
                chunks.put(ChunkPos.asLong(x, z), List.of(loaderBucket("source")));
            }
        }

        var plan = PerfAttributionSnapshot.create("test:level", chunks).loaderPlan(4_096, 4_096, 4_096);

        assertEquals(0, plan.omittedSources());
        assertEquals(new PerfAttributionSnapshot.Position("test:level", 8, 64, 8),
                plan.sources().getFirst().position());
        assertEquals(List.of(new PerfAttributionSnapshot.LoaderRegion(-3, -3, 7, 7)),
                plan.sources().getFirst().regions());
    }

    @Test
    void mergesEqualRunsVerticallyAndPreservesGaps() {
        Map<Long, List<PerfAttributionSnapshot.Bucket>> chunks = new HashMap<>();
        for (int z = 0; z < 100; z++) {
            for (int x : List.of(0, 1, 2, 10, 11, 12)) {
                chunks.put(ChunkPos.asLong(x, z), List.of(loaderBucket("source")));
            }
        }

        var regions = PerfAttributionSnapshot.create("test:level", chunks)
                .loaderPlan(4_096, 4_096, 4_096).sources().getFirst().regions();

        assertEquals(List.of(
                new PerfAttributionSnapshot.LoaderRegion(0, 0, 3, 100),
                new PerfAttributionSnapshot.LoaderRegion(10, 0, 3, 100)), regions);
    }

    @Test
    void sharesRegionBudgetAcrossSourcesAndReportsOmissions() {
        Map<Long, List<PerfAttributionSnapshot.Bucket>> chunks = new HashMap<>();
        chunks.put(ChunkPos.asLong(0, 0), List.of(loaderBucket("a")));
        chunks.put(ChunkPos.asLong(2, 0), List.of(loaderBucket("a")));
        chunks.put(ChunkPos.asLong(10, 0), List.of(loaderBucket("b")));

        var plan = PerfAttributionSnapshot.create("test:level", chunks).loaderPlan(2, 2, 4_096);

        assertEquals(2, plan.sources().size());
        assertEquals(2, plan.sources().stream().mapToInt(source -> source.regions().size()).sum());
        assertTrue(plan.sources().stream().anyMatch(source -> source.omittedRegions() == 1));
        assertEquals(1, PerfAttributionSnapshot.create("test:level", chunks)
                .loaderPlan(1, 2, 4_096).omittedSources());
    }

    @Test
    void splitsOversizedRectanglesOnBothAxes() {
        Map<Long, List<PerfAttributionSnapshot.Bucket>> chunks = new HashMap<>();
        for (int z = 0; z < 3; z++) {
            for (int x = 0; x < 3; x++) {
                chunks.put(ChunkPos.asLong(x, z), List.of(loaderBucket("source")));
            }
        }

        var regions = PerfAttributionSnapshot.create("test:level", chunks)
                .loaderPlan(10, 10, 2).sources().getFirst().regions();

        assertEquals(4, regions.size());
        assertEquals(9, regions.stream().mapToInt(region -> region.width() * region.height()).sum());
        assertTrue(regions.stream().allMatch(region -> region.width() <= 2 && region.height() <= 2));
    }

    @Test
    void exposesControlOnlyWhenTheSourceLayoutIsComplete() {
        ResourceLocation dimensionId = ResourceLocation.parse("test:level");
        BlockLoaderRef ref = new BlockLoaderRef(
                ResourceKey.create(Registries.DIMENSION, dimensionId),
                ResourceLocation.parse("test:loader"), new BlockPos(8, 64, 8));
        var owner = new PerfAttributionSnapshot.Owner(PerfReportData.AttributionKind.LOADER,
                "loader", "test:loader", false, 0L, null);
        var source = new PerfAttributionSnapshot.Source("source", "test:loader",
                new PerfAttributionSnapshot.Position("test:level", 8, 64, 8), ref);
        var bucket = new PerfAttributionSnapshot.Bucket(owner, source, 0, 0);
        var snapshot = PerfAttributionSnapshot.create("test:level", Map.of(
                        ChunkPos.asLong(0, 0), List.of(bucket),
                        ChunkPos.asLong(2, 0), List.of(bucket)),
                Map.of(ref, new PerfAttributionSnapshot.LoaderControl(ref, false, true)));

        var truncated = snapshot.loaderPlan(1, 1, 4_096).sources().getFirst();
        var complete = snapshot.loaderPlan(1, 2, 4_096).sources().getFirst();

        assertEquals(1, truncated.omittedRegions());
        assertEquals(null, truncated.control());
        assertEquals(new PerfAttributionSnapshot.LoaderControl(ref, false, true), complete.control());
        assertTrue(snapshot.updateLoaderControl(ref, true, false));
        assertEquals(1L, snapshot.loaderControlRevision());
        assertFalse(snapshot.updateLoaderControl(ref, true, false));
        assertEquals(1L, snapshot.loaderControlRevision());
    }

    private static PerfAttributionSnapshot.Bucket loaderBucket(String sourceId) {
        var owner = new PerfAttributionSnapshot.Owner(PerfReportData.AttributionKind.LOADER,
                "loader-" + sourceId, "test:loader", false, 0L, null);
        var source = new PerfAttributionSnapshot.Source(sourceId, "test:loader",
                new PerfAttributionSnapshot.Position("test:level", 8, 64, 8));
        return new PerfAttributionSnapshot.Bucket(owner, source, 0, 0);
    }
}
