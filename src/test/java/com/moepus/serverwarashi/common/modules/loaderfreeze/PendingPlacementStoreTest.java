package com.moepus.serverwarashi.common.modules.loaderfreeze;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PendingPlacementStoreTest {
    private static final ResourceLocation BLOCK_A = ResourceLocation.parse("test:a");
    private static final ResourceLocation BLOCK_B = ResourceLocation.parse("test:b");

    @Test
    void samePositionKeepsOnlyNewestCandidate() {
        PendingPlacementStore store = new PendingPlacementStore(2, 3, 10);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        BlockPos pos = new BlockPos(1, 2, 3);

        store.put(new PendingPlacementStore.Placement(Level.OVERWORLD, pos, BLOCK_A, first, 1));
        store.put(new PendingPlacementStore.Placement(Level.OVERWORLD, pos, BLOCK_B, second, 2));

        assertEquals(1, store.size());
        var candidate = store.take(Level.OVERWORLD, pos, 2).orElseThrow();
        assertEquals(BLOCK_B, candidate.blockId());
        assertEquals(second, candidate.playerId());
    }

    @Test
    void evictsOldestPerDimensionAndThenGlobally() {
        PendingPlacementStore store = new PendingPlacementStore(2, 3, 20);
        store.put(simplePlacement(Level.OVERWORLD, 0, 0));
        store.put(simplePlacement(Level.OVERWORLD, 1, 1));
        store.put(simplePlacement(Level.OVERWORLD, 2, 2));

        assertTrue(store.take(Level.OVERWORLD, BlockPos.ZERO, 2).isEmpty());
        assertEquals(2, store.size(Level.OVERWORLD));

        store.put(simplePlacement(Level.NETHER, 3, 3));
        store.put(simplePlacement(Level.NETHER, 4, 4));
        assertEquals(3, store.size());
        assertEquals(2, store.overflowCount());
        assertTrue(store.take(Level.OVERWORLD, new BlockPos(1, 0, 0), 4).isEmpty());
    }

    @Test
    void expiresCandidatesAcrossTickCounterWrap() {
        PendingPlacementStore store = new PendingPlacementStore(2, 3, 3);
        int created = Integer.MAX_VALUE - 1;
        store.put(simplePlacement(Level.OVERWORLD, 0, created));

        assertTrue(store.take(Level.OVERWORLD, BlockPos.ZERO, Integer.MIN_VALUE + 1).isPresent());
        store.put(simplePlacement(Level.OVERWORLD, 0, created));
        assertTrue(store.take(Level.OVERWORLD, BlockPos.ZERO, Integer.MIN_VALUE + 3).isEmpty());
    }

    private static PendingPlacementStore.Placement simplePlacement(
            net.minecraft.resources.ResourceKey<Level> dimension, int x, int tick) {
        return new PendingPlacementStore.Placement(
                dimension, new BlockPos(x, 0, 0), BLOCK_A, UUID.randomUUID(), tick);
    }
}
