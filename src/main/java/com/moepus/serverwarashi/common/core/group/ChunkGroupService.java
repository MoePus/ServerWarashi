package com.moepus.serverwarashi.common.core.group;

import com.moepus.serverwarashi.common.core.ticket.TicketOwner;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Chunk 分组查询服务（公共基础设施）。
 * 负责快照刷新、按区块定位分组以及快照视图读取。
 */
public final class ChunkGroupService {
    private static final ChunkGroupSnapshot SNAPSHOT_CACHE = new ChunkGroupSnapshot();

    private ChunkGroupService() {
    }

    public static void clearSnapshots() {
        SNAPSHOT_CACHE.clear();
    }

    public static List<ChunkGroupSnapshot.ChunkGroupEntry> listGroups(ServerLevel level,
                                                                       ChunkGroupSnapshot.PauseMode pauseMode) {
        refresh(level);
        // ALL 用缓存快照；ACTIVE_ONLY 现采（避免把已暂停组算进来）
        if (pauseMode == ChunkGroupSnapshot.PauseMode.ALL) {
            return SNAPSHOT_CACHE.getSnapshot(level.dimension()).groups();
        }
        return ChunkGroupCollector.collect(level, pauseMode).groups();
    }

    public static GroupChunkLookup resolveAtChunk(ServerLevel level,
                                                    long chunkPos,
                                                    ChunkGroupSnapshot.PauseMode pauseMode) {
        refresh(level);
        ChunkGroupSnapshot.SnapshotData snapshot = pauseMode == ChunkGroupSnapshot.PauseMode.ALL
                ? SNAPSHOT_CACHE.getSnapshot(level.dimension())
                : ChunkGroupCollector.collect(level, pauseMode);
        List<ChunkGroupSnapshot.ChunkGroupEntry> groups = snapshot.groups();
        if (groups.isEmpty()) {
            return new GroupChunkLookup(null, new LookupFailure(LookupFailureReason.NO_GROUPS));
        }
        for (ChunkGroupSnapshot.ChunkGroupEntry entry : groups) {
            if (entry.chunks().contains(chunkPos)) {
                return new GroupChunkLookup(entry, null);
            }
        }
        return new GroupChunkLookup(null, new LookupFailure(LookupFailureReason.CHUNK_NOT_FOUND));
    }

    public enum LookupFailureReason {
        NO_GROUPS,
        CHUNK_NOT_FOUND
    }

    public record LookupFailure(LookupFailureReason reason) {
    }

    public record GroupChunkLookup(ChunkGroupSnapshot.ChunkGroupEntry entry, LookupFailure failure) {
    }

    public static void refresh(ServerLevel level) {
        ChunkGroupSnapshot.SnapshotData snapshot = ChunkGroupCollector.collect(level, ChunkGroupSnapshot.PauseMode.ALL);
        SNAPSHOT_CACHE.putSnapshot(level.dimension(), snapshot);
    }

    public static ChunkGroupSnapshot.SnapshotData refreshSnapshot(ServerLevel level,
                                                                   ChunkGroupSnapshot.PauseMode pauseMode) {
        if (pauseMode == ChunkGroupSnapshot.PauseMode.ALL) {
            refresh(level);
            return SNAPSHOT_CACHE.getSnapshot(level.dimension());
        }
        return ChunkGroupCollector.collect(level, pauseMode);
    }

    public static void refreshAll(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            refresh(level);
        }
    }

    /**
     * 现采该维度的活跃 owner→chunks 分组，但不清点 BE/E 实体（廉价）。
     */
    public static Map<TicketOwner, Set<Long>> activeOwnerChunks(ServerLevel level) {
        return ChunkGroupCollector.collectOwners(level, ChunkGroupSnapshot.PauseMode.ACTIVE_ONLY);
    }

    /**
     * 对给定 chunk 集合按需清点 BE/E 统计（昂贵：遍历实体 section）。
     */
    public static ChunkGroupSnapshot.OwnerStats computeStats(ServerLevel level, Set<Long> chunks) {
        return ChunkGroupCollector.computeStats(level, chunks);
    }
}
