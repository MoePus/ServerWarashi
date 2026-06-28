package com.moepus.serverwarashi.common.core.group;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;

import java.util.List;

/**
 * 供缓存快照和重复采样路径使用的 chunk 到 group 懒索引。
 */
public final class ChunkGroupIndex {
    private final Long2IntOpenHashMap chunkToGroupIndex;

    private ChunkGroupIndex(Long2IntOpenHashMap chunkToGroupIndex) {
        this.chunkToGroupIndex = chunkToGroupIndex;
    }

    static ChunkGroupIndex build(List<ChunkGroupSnapshot.ChunkGroupEntry> groups) {
        Long2IntOpenHashMap chunkToGroupIndex = new Long2IntOpenHashMap();
        chunkToGroupIndex.defaultReturnValue(-1);
        for (int groupIndex = 0; groupIndex < groups.size(); groupIndex++) {
            for (long chunkPos : groups.get(groupIndex).chunks()) {
                chunkToGroupIndex.put(chunkPos, groupIndex);
            }
        }
        return new ChunkGroupIndex(chunkToGroupIndex);
    }

    public int resolveIndex(long chunkPos) {
        return chunkToGroupIndex.get(chunkPos);
    }
}
