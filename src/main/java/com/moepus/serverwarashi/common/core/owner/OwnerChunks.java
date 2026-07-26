package com.moepus.serverwarashi.common.core.owner;

import com.moepus.serverwarashi.common.core.ticket.TicketScanService;
import com.moepus.serverwarashi.common.core.ticket.TicketUtils;
import com.moepus.serverwarashi.common.mixin.accessor.PersistentEntitySectionManagerAccessor;
import com.moepus.serverwarashi.common.mixin.accessor.ServerLevelAccessor;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.Ticket;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.entity.EntitySection;
import net.minecraft.world.level.entity.EntitySectionStorage;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * owner -> chunks 视图构建器：扫描 DistanceManager 当前 ticket 表，把 chunk 按 owner 分组，
 * 并可为 chunk 集合清点 BE/E 负载（{@link Stats}）。收集本身一律全量（含被暂停 chunk）。
 * 层级：core。上游：performance 定位、watchdog 诊断和未来的 ticket 调度策略。
 * 下游：TicketScanService / TicketOwnerResolver。
 */
public final class OwnerChunks {
    private OwnerChunks() {
    }

    public record Entry(TicketOwner owner, Set<Long> chunks, Stats stats) {
    }

    public record Stats(int chunkCount, int blockEntityCount, int entityCount) {
    }

    public record BothMaps(Map<TicketOwner, Set<Long>> activeOnly, Map<TicketOwner, Set<Long>> all) {
    }

    /**
     * 报表视图选择器（仅 owner 列表报告用）：收集方法本身一律全量收集，
     * ACTIVE_ONLY/ALL 只决定报告展示 {@link BothMaps} 的哪个视图。
     */
    public enum PauseMode {
        ACTIVE_ONLY,
        ALL
    }

    public enum SortMode {
        BLOCK_ENTITY,
        ENTITY;

        public Comparator<Entry> comparator() {
            return switch (this) {
                case ENTITY -> Comparator
                        .comparingInt((Entry entry) -> entry.stats().entityCount())
                        .reversed()
                        .thenComparing(entry -> entry.owner().toString());
                case BLOCK_ENTITY -> Comparator
                        .comparingInt((Entry entry) -> entry.stats().blockEntityCount())
                        .reversed()
                        .thenComparing(entry -> entry.owner().toString());
            };
        }
    }

    public static List<Entry> collect(ServerLevel level) {
        return collectFromMap(level, collectMap(level));
    }

    /**
     * 从预先收集的 ownerMap 构建 Entry 列表（清点 BE/E），避免重复扫描 ticket 表。
     */
    public static List<Entry> collectFromMap(ServerLevel level, Map<TicketOwner, Set<Long>> ownerMap) {
        if (ownerMap.isEmpty()) {
            return List.of();
        }

        EntitySectionStorage<?> sectionStorage = sectionStorage(level);
        Map<Long, ChunkLoad> chunkLoads = new HashMap<>();
        List<Entry> entries = new ArrayList<>(ownerMap.size());
        for (var owner : ownerMap.entrySet()) {
            Set<Long> chunks = owner.getValue();
            entries.add(new Entry(owner.getKey(), chunks, computeStats(level, chunks, sectionStorage, chunkLoads)));
        }
        return entries;
    }

    public static Map<TicketOwner, Set<Long>> collectMap(ServerLevel level) {
        Map<TicketOwner, Set<Long>> ownerMap = new HashMap<>();
        visitOwnerChunks(level, (owner, chunkPos, paused) ->
                ownerMap.computeIfAbsent(owner, ignored -> new HashSet<>()).add(chunkPos));
        return ownerMap;
    }

    /**
     * 一次遍历同时返回 ACTIVE_ONLY 与 ALL 两个视图，避免双全量扫描。
     */
    public static BothMaps collectBothMaps(ServerLevel level) {
        Map<TicketOwner, Set<Long>> activeOnly = new HashMap<>();
        Map<TicketOwner, Set<Long>> all = new HashMap<>();
        visitOwnerChunks(level, (owner, chunkPos, paused) -> {
            all.computeIfAbsent(owner, ignored -> new HashSet<>()).add(chunkPos);
            if (!paused) {
                activeOnly.computeIfAbsent(owner, ignored -> new HashSet<>()).add(chunkPos);
            }
        });
        return new BothMaps(activeOnly, all);
    }

    public static Map<TicketOwner, Set<Long>> resolveOwnersAt(ServerLevel level, long chunkPos) {
        // 解析chunk命中的owner
        Map<TicketOwner, Set<Long>> owners = new HashMap<>();
        for (var owner : collectMap(level).entrySet()) {
            if (owner.getValue().contains(chunkPos)) {
                owners.put(owner.getKey(), owner.getValue());
            }
        }
        return owners;
    }

    public static Set<Long> resolveChunksAt(ServerLevel level, long chunkPos) {
        LongOpenHashSet resolved = new LongOpenHashSet();
        for (Set<Long> chunks : resolveOwnersAt(level, chunkPos).values()) {
            resolved.addAll(chunks);
        }
        return resolved;
    }

    public static Stats stats(ServerLevel level, Set<Long> chunks) {
        EntitySectionStorage<?> sectionStorage = sectionStorage(level);
        return computeStats(level, chunks, sectionStorage, new HashMap<>());
    }

    private static Stats computeStats(ServerLevel level,
                                      Set<Long> chunks,
                                      EntitySectionStorage<?> sectionStorage,
                                      Map<Long, ChunkLoad> chunkLoads) {
        int totalBlockEntities = 0;
        int totalEntities = 0;
        for (long chunkPos : chunks) {
            ChunkLoad load = chunkLoads.computeIfAbsent(
                    chunkPos,
                    pos -> countChunkLoad(level, pos, sectionStorage)
            );
            totalBlockEntities += load.blockEntityCount();
            totalEntities += load.entityCount();
        }
        return new Stats(chunks.size(), totalBlockEntities, totalEntities);
    }

    private static EntitySectionStorage<?> sectionStorage(ServerLevel level) {
        PersistentEntitySectionManager<Entity> entityManager = ((ServerLevelAccessor) level).getEntityManager();
        return ((PersistentEntitySectionManagerAccessor) entityManager).getSectionStorage();
    }

    private static ChunkLoad countChunkLoad(ServerLevel level,
                                            long chunkPos,
                                            EntitySectionStorage<?> sectionStorage) {
        ChunkPos pos = new ChunkPos(chunkPos);
        ChunkAccess chunk = level.getChunk(pos.x, pos.z, ChunkStatus.FULL, false);
        int blockEntityCount = chunk == null ? 0 : chunk.getBlockEntitiesPos().size();
        int entityCount = sectionStorage.getExistingSectionsInChunk(chunkPos)
                .mapToInt(EntitySection::size)
                .sum();
        return new ChunkLoad(blockEntityCount, entityCount);
    }

    /**
     * 核心 ticket→owner 遍历：过滤 origin level、解析 owner、处理 chunk protection fallback。
     * consumer 接收 (owner, chunkPos, paused)，由调用方决定写入哪些 map。
     */
    private static void visitOwnerChunks(ServerLevel level, OwnerChunkConsumer consumer) {
        TicketScanService.visitTicketChunks(level, (chunkPos, tickets) -> {
            Ticket<?> fallback = null;
            int fallbackPriority = Integer.MAX_VALUE;
            boolean addedNonChunkProtectionOwner = false;
            for (Ticket<?> ticket : tickets) {
                if (TicketUtils.getOriginLevel(ticket) > TicketUtils.MAX_PAUSEABLE_LEVEL) {
                    continue;
                }
                if (TicketUtils.isChunkProtectionTicket(ticket)) {
                    int priority = ticket.getType() == TicketType.FORCED ? 0 : 255;
                    if (priority < fallbackPriority) {
                        fallback = ticket;
                        fallbackPriority = priority;
                    }
                    continue;
                }
                TicketOwner owner = TicketOwnerResolver.resolve(ticket, level);
                if (TicketOwnerResolver.UNKNOWN_ENTITY.equals(owner.name())) {
                    continue;
                }
                boolean paused = TicketUtils.asPauseable(ticket).serverWarashi$isPaused();
                consumer.accept(owner, chunkPos, paused);
                addedNonChunkProtectionOwner = true;
            }
            if (!addedNonChunkProtectionOwner && fallback != null) {
                TicketOwner owner = TicketOwnerResolver.resolve(fallback, level);
                if (!TicketOwnerResolver.UNKNOWN_ENTITY.equals(owner.name())) {
                    consumer.accept(owner, chunkPos, false);
                }
            }
        });
    }

    @FunctionalInterface
    private interface OwnerChunkConsumer {
        void accept(TicketOwner owner, long chunkPos, boolean paused);
    }

    private record ChunkLoad(int blockEntityCount, int entityCount) {
    }
}
