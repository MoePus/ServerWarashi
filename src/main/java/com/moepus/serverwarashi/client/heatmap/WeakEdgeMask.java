package com.moepus.serverwarashi.client.heatmap;

import net.minecraft.world.level.ChunkPos;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

final class WeakEdgeMask {
    private static final int EDGE_WEST = 1;
    private static final int EDGE_EAST = 2;
    private static final int EDGE_NORTH = 4;
    private static final int EDGE_SOUTH = 8;

    private WeakEdgeMask() {
    }

    static Map<Long, Integer> find(Set<Long> loaderChunks) {
        Map<Long, Integer> result = new HashMap<>();
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
        return Map.copyOf(result);
    }
}
