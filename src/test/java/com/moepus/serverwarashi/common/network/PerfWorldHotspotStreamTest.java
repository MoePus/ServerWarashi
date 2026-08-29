package com.moepus.serverwarashi.common.network;

import com.moepus.serverwarashi.common.modules.performance.report.PerfWorldHotspotIndex;
import com.moepus.serverwarashi.common.modules.performance.sampling.TickDetailKind;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PerfWorldHotspotStreamTest {
    private static final ResourceLocation DIMENSION =
            ResourceLocation.parse("minecraft:overworld");
    private static final ResourceLocation STONE = ResourceLocation.parse("minecraft:stone");

    @Test
    void scalarRefreshUsesEmptyDeltaUntilObjectSamplesChange() {
        PerfWorldHotspotStream stream = stream();
        stream.update(index(1L, 20L, 1_000_000L, entry(0, 64, 0, 4L)));
        var full = stream.emit(null, DIMENSION, 8.0D, 8.0D, 32);
        assertTrue(full.payload().full());
        assertEquals(1, full.payload().upserts().size());

        stream.update(index(1L, 40L, 1_100_000L, entry(0, 64, 0, 4L)));
        var scalarOnly = stream.emit(full.cursor(), DIMENSION, 8.0D, 8.0D, 32);
        assertFalse(scalarOnly.payload().full());
        assertTrue(scalarOnly.payload().upserts().isEmpty());
        assertTrue(scalarOnly.payload().removals().isEmpty());
        assertEquals(40L, scalarOnly.payload().tickCount());

        stream.update(index(1L, 60L, 1_100_000L, entry(0, 64, 0, 5L)));
        var changed = stream.emit(scalarOnly.cursor(), DIMENSION, 8.0D, 8.0D, 32);
        assertEquals(1, changed.payload().upserts().size());
        assertEquals(5L, changed.payload().upserts().get(0).samples());
    }

    @Test
    void removalAndCenterMovementAreHandledWithoutPerPlayerObjectState() {
        PerfWorldHotspotStream stream = stream();
        stream.update(index(2L, 20L, 1_000_000L, entry(0, 64, 0, 4L)));
        var full = stream.emit(null, DIMENSION, 8.0D, 8.0D, 32);

        stream.update(index(2L, 40L, 1_000_000L));
        var removed = stream.emit(full.cursor(), DIMENSION, 8.0D, 8.0D, 32);
        assertEquals(List.of(PerfWorldHotspotIndex.Key.blockEntity(0, 64, 0)),
                removed.payload().removals());

        var moved = stream.emit(removed.cursor(),
                ResourceLocation.parse("minecraft:the_nether"), 24.0D, 8.0D, 32);
        assertTrue(moved.payload().full());
    }

    @Test
    void playerMovementUpdatesPrefetchSetWithDelta() {
        PerfWorldHotspotStream stream = stream();
        stream.update(index(4L, 20L, 1_000_000L, entry(0, 64, 0, 4L)));
        var full = stream.emit(null, DIMENSION, 0.0D, 0.0D, 32);

        stream.update(index(4L, 40L, 1_000_000L, entry(0, 64, 0, 4L)));
        var moved = stream.emit(full.cursor(), DIMENSION, 256.0D, 0.0D, 32);

        assertFalse(moved.payload().full());
        assertEquals(List.of(PerfWorldHotspotIndex.Key.blockEntity(0, 64, 0)),
                moved.payload().removals());
    }

    @Test
    void drawCircleWinsBudgetBeforeHotterPrefetchRing() {
        PerfWorldHotspotIndex.Entry near = entry(0, 64, 0, 1L);
        PerfWorldHotspotIndex.Entry ring = entry(110, 64, 0, 100L);
        var selected = PerfWorldHotspotSelector.select(
                index(3L, 20L, 1_000_000L, near, ring),
                new PerfWorldHotspotSelector.View(DIMENSION.toString(), 8.0D, 8.0D), 1);
        assertEquals(List.of(near), selected);
    }

    @Test
    void prefetchRadiusStopsAtOneHundredTwentyEightBlocks() {
        PerfWorldHotspotIndex.Entry inside = entry(127, 64, 0, 1L);
        PerfWorldHotspotIndex.Entry outside = entry(128, 64, 0, 2L);
        var selected = PerfWorldHotspotSelector.select(
                index(5L, 20L, 1_000_000L, inside, outside),
                new PerfWorldHotspotSelector.View(DIMENSION.toString(), 0.0D, 0.5D), 10);
        assertEquals(List.of(inside), selected);
    }

    @Test
    void unresolvedRegistryEntriesDoNotConsumeTheObjectBudget() {
        ResourceLocation missing = ResourceLocation.parse("missing:unknown_block");
        PerfWorldHotspotStream stream = new PerfWorldHotspotStream(
                (kind, id) -> id.equals(missing) ? -1 : 7);
        PerfWorldHotspotIndex.Entry unresolved = entry(0, 64, 0, 100L, missing);
        PerfWorldHotspotIndex.Entry valid = entry(1, 64, 0, 1L, STONE);
        stream.update(index(6L, 20L, 1_000_000L, unresolved, valid));

        var emission = stream.emit(null, DIMENSION, 0.0D, 0.0D, 1);

        assertEquals(1, emission.payload().upserts().size());
        assertEquals(valid.key(), emission.payload().upserts().get(0).key());
    }

    private static PerfWorldHotspotIndex index(long epoch,
                                                long revision,
                                                long nanosPerSample,
                                                PerfWorldHotspotIndex.Entry... entries) {
        return new PerfWorldHotspotIndex(
                epoch, revision, revision, nanosPerSample, List.of(entries));
    }

    private static PerfWorldHotspotStream stream() {
        return new PerfWorldHotspotStream((kind, id) -> 1);
    }

    private static PerfWorldHotspotIndex.Entry entry(int x, int y, int z, long samples) {
        return entry(x, y, z, samples, STONE);
    }

    private static PerfWorldHotspotIndex.Entry entry(
            int x, int y, int z, long samples, ResourceLocation detailId) {
        return new PerfWorldHotspotIndex.Entry(
                PerfWorldHotspotIndex.Key.blockEntity(x, y, z), DIMENSION.toString(), x, y, z,
                TickDetailKind.BLOCK, detailId, 1L, samples, (int) samples * 256);
    }
}
