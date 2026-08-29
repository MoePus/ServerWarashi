package com.moepus.serverwarashi.common.util;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class PackedPositionHashTest {
    @Test
    void separatesKnownChunkPosLongHashCollision() {
        long origin = ChunkPos.asLong(0, 0);
        long diagonal = ChunkPos.asLong(1, 1);

        assertEquals(Long.hashCode(origin), Long.hashCode(diagonal));
        assertNotEquals(PackedPositionHash.hash(origin), PackedPositionHash.hash(diagonal));
    }

    @Test
    void separatesKnownBlockPosLongHashCollision() {
        long east = BlockPos.asLong(1, 0, 0);
        long up = BlockPos.asLong(0, 64, 0);

        assertEquals(Long.hashCode(east), Long.hashCode(up));
        assertNotEquals(PackedPositionHash.hash(east), PackedPositionHash.hash(up));
    }
}
