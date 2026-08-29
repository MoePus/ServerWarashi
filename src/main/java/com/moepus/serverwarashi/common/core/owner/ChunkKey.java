package com.moepus.serverwarashi.common.core.owner;

import com.moepus.serverwarashi.common.util.PackedPositionHash;

record ChunkKey(String dimension, long chunkPos) {
    @Override
    public int hashCode() {
        return 31 * dimension.hashCode() + PackedPositionHash.hash(chunkPos);
    }
}
