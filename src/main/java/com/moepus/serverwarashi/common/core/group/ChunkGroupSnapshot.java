package com.moepus.serverwarashi.common.core.group;

import com.moepus.serverwarashi.common.core.ticket.TicketOwner;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Chunk 分组运行期快照缓存容器。
 * 该类在服务器运行期保持单实例，按维度缓存快照数据。
 */
public final class ChunkGroupSnapshot {
    static final SnapshotData EMPTY_SNAPSHOT = new SnapshotData(List.of(), new HashMap<>());

    private final Map<ResourceKey<Level>, SnapshotData> dimensionSnapshots = new HashMap<>();

    ChunkGroupSnapshot() {
    }

    public record ChunkGroupEntry(
            TicketOwner owner,
            Set<Long> chunks,
            OwnerStats stats
    ) {
        public String label() {
            return owner.toString();
        }

        public BlockPos firstChunkAnchor() {
            return ChunkGroupSnapshot.firstChunkAnchor(chunks);
        }
    }

    public static BlockPos firstChunkAnchor(Set<Long> chunks) {
        var iterator = chunks.iterator();
        return iterator.hasNext() ? new ChunkPos(iterator.next()).getWorldPosition() : null;
    }

    public record OwnerStats(int chunkCount, int blockEntityCount, int entityCount) {
    }

    /**
     * 维度快照视图（纯数据）。
     */
    public static final class SnapshotData {
        private final List<ChunkGroupEntry> groups;
        private final HashMap<Long, ChunkLoadInfo> chunkLoadInfoMap;
        private volatile ChunkGroupIndex chunkIndex;

        public SnapshotData(List<ChunkGroupEntry> groups, HashMap<Long, ChunkLoadInfo> chunkLoadInfoMap) {
            this.groups = groups;
            this.chunkLoadInfoMap = chunkLoadInfoMap;
        }

        public List<ChunkGroupEntry> groups() {
            return groups;
        }

        public HashMap<Long, ChunkLoadInfo> chunkLoadInfoMap() {
            return chunkLoadInfoMap;
        }

        /**
         * 为缓存快照和重复采样路径懒构建 chunk 查找表。
         * 一次性查询仍应使用 {@link ChunkGroupService#resolveAtChunk} 的线性扫描。
         */
        public ChunkGroupIndex chunkIndex() {
            ChunkGroupIndex index = chunkIndex;
            if (index == null) {
                index = ChunkGroupIndex.build(groups);
                chunkIndex = index;
            }
            return index;
        }
    }

    public record ChunkLoadInfo(
            int blockEntityCount,
            int entityCount
    ) {
    }

    public enum PauseMode {
        ACTIVE_ONLY,
        ALL;

        boolean accept(boolean paused) {
            return switch (this) {
                case ACTIVE_ONLY -> !paused;
                case ALL -> true;
            };
        }
    }

    public enum SortMode {
        BLOCK_ENTITY,
        ENTITY;

        public Comparator<ChunkGroupEntry> comparator() {
            return switch (this) {
                case ENTITY -> Comparator
                        .comparingInt((ChunkGroupEntry entry) -> entry.stats().entityCount())
                        .reversed()
                        .thenComparing(entry -> entry.owner().toString());
                case BLOCK_ENTITY -> Comparator
                        .comparingInt((ChunkGroupEntry entry) -> entry.stats().blockEntityCount())
                        .reversed()
                        .thenComparing(entry -> entry.owner().toString());
            };
        }
    }

    void clear() {
        dimensionSnapshots.clear();
    }

    SnapshotData getSnapshot(ResourceKey<Level> dimension) {
        return dimensionSnapshots.getOrDefault(dimension, EMPTY_SNAPSHOT);
    }

    void putSnapshot(ResourceKey<Level> dimension, SnapshotData snapshot) {
        dimensionSnapshots.put(dimension, snapshot);
    }
}
