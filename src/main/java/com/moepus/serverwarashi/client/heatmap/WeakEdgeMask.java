package com.moepus.serverwarashi.client.heatmap;

import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.world.level.ChunkPos;

final class WeakEdgeMask {
    private static final int EDGE_WEST = 1;
    private static final int EDGE_EAST = 2;
    private static final int EDGE_NORTH = 4;
    private static final int EDGE_SOUTH = 8;

    private WeakEdgeMask() {
    }

    static Long2IntMap find(LongSet loaderChunks) {
        Long2IntMap result = new Long2IntOpenHashMap();
        for (long chunk : loaderChunks) {
            int x = ChunkPos.getX(chunk);
            int z = ChunkPos.getZ(chunk);
            int mask = 0;
            if (!loaderChunks.contains(ChunkPos.asLong(x - 1, z))) {
                mask |= EDGE_WEST;
            }
            if (!loaderChunks.contains(ChunkPos.asLong(x + 1, z))) {
                mask |= EDGE_EAST;
            }
            if (!loaderChunks.contains(ChunkPos.asLong(x, z - 1))) {
                mask |= EDGE_NORTH;
            }
            if (!loaderChunks.contains(ChunkPos.asLong(x, z + 1))) {
                mask |= EDGE_SOUTH;
            }
            if (mask != 0) {
                result.put(chunk, mask);
            }
        }
        return result;
    }
}
