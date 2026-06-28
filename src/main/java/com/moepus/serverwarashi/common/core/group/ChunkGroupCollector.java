package com.moepus.serverwarashi.common.core.group;

import com.moepus.serverwarashi.common.core.ticket.IPauseableTicket;
import com.moepus.serverwarashi.common.core.ticket.TicketOwner;
import com.moepus.serverwarashi.common.core.ticket.TicketOwnerResolver;
import com.moepus.serverwarashi.common.core.ticket.TicketScanService;
import com.moepus.serverwarashi.common.core.ticket.TicketUtils;
import com.moepus.serverwarashi.common.mixin.accessor.EntitySectionStorageAccessor;
import com.moepus.serverwarashi.common.mixin.accessor.PersistentEntitySectionManagerAccessor;
import com.moepus.serverwarashi.common.mixin.accessor.ServerLevelAccessor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.Ticket;
import net.minecraft.util.SortedArraySet;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.entity.EntitySection;
import net.minecraft.world.level.entity.EntitySectionStorage;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Chunk 分组快照采集器。
 * 负责从运行时状态中采集分组与统计快照。
 */
final class ChunkGroupCollector {
    private ChunkGroupCollector() {
    }

    static ChunkGroupSnapshot.SnapshotData collect(ServerLevel level,
                                                   ChunkGroupSnapshot.PauseMode pauseMode) {
        HashMap<TicketOwner, Set<Long>> ownerMap = collectTicketOwners(level, pauseMode);
        if (ownerMap.isEmpty()) {
            return ChunkGroupSnapshot.EMPTY_SNAPSHOT;
        }
        // 2) 经 accessor 链拿到实体分区存储，用于统计每 chunk 实体数
        PersistentEntitySectionManager<Entity> entityManager = ((ServerLevelAccessor) level).getEntityManager();
        PersistentEntitySectionManagerAccessor entityAccessor = (PersistentEntitySectionManagerAccessor) entityManager;
        EntitySectionStorage<?> sectionStorage = entityAccessor.getSectionStorage();
        EntitySectionStorageAccessor sectionAccessor = (EntitySectionStorageAccessor) sectionStorage;
        HashMap<Long, ChunkGroupSnapshot.ChunkLoadInfo> chunkLoadInfoMap =
                collectChunkLoadInfo(level, ownerMap, sectionAccessor, sectionStorage);
        List<ChunkGroupSnapshot.ChunkGroupEntry> groups = buildGroupEntries(ownerMap, chunkLoadInfoMap);
        return new ChunkGroupSnapshot.SnapshotData(groups, chunkLoadInfoMap);
    }

    private static HashMap<TicketOwner, Set<Long>> collectTicketOwners(ServerLevel level,
                                                                          ChunkGroupSnapshot.PauseMode pauseMode) {
        HashMap<TicketOwner, Set<Long>> ownerMap = new HashMap<>();
        TicketScanService.visitTicketChunks(level, (chunkPos, tickets) -> {
            Ticket<?> selectedTicket = selectTicket(tickets, pauseMode);
            if (selectedTicket == null) {
                return;
            }
            TicketOwner owner = TicketOwnerResolver.resolve(selectedTicket, level);
            if (TicketOwnerResolver.UNKNOWN_ENTITY.equals(owner.getName())) {
                return;
            }
            ownerMap.computeIfAbsent(owner, ignored -> new HashSet<>()).add(chunkPos);
        });

        return ownerMap;
    }

    private static HashMap<Long, ChunkGroupSnapshot.ChunkLoadInfo> collectChunkLoadInfo(
            ServerLevel level,
            HashMap<TicketOwner, Set<Long>> ownerMap,
            EntitySectionStorageAccessor sectionAccessor,
            EntitySectionStorage<?> sectionStorage
    ) {
        HashMap<Long, ChunkGroupSnapshot.ChunkLoadInfo> chunkLoadInfoMap = new HashMap<>();
        for (Set<Long> chunks : ownerMap.values()) {
            for (long chunkPos : chunks) {
                if (chunkLoadInfoMap.containsKey(chunkPos)) {
                    continue;
                }
                chunkLoadInfoMap.put(chunkPos, countChunkLoad(level, chunkPos, sectionAccessor, sectionStorage));
            }
        }
        return chunkLoadInfoMap;
    }

    /**
     * 仅采集 owner→chunks 分组，不清点 BE/E 实体。供只关心 Top-N 的消费者（如驻留成本 HUD）
     * 先按廉价指标选组，再对入选组用 {@link #computeStats} 按需清点，避免对全部组都做昂贵的实体遍历。
     */
    static Map<TicketOwner, Set<Long>> collectOwners(ServerLevel level,
                                                        ChunkGroupSnapshot.PauseMode pauseMode) {
        return collectTicketOwners(level, pauseMode);
    }

    /**
     * 对给定 chunk 集合按需清点 BE/E，聚合成 {@link ChunkGroupSnapshot.OwnerStats}。
     * 与 {@link #collectChunkLoadInfo} 共用 {@link #countChunkLoad} 的单 chunk 统计逻辑。
     */
    static ChunkGroupSnapshot.OwnerStats computeStats(ServerLevel level, Set<Long> chunks) {
        PersistentEntitySectionManager<Entity> entityManager = ((ServerLevelAccessor) level).getEntityManager();
        PersistentEntitySectionManagerAccessor entityAccessor = (PersistentEntitySectionManagerAccessor) entityManager;
        EntitySectionStorage<?> sectionStorage = entityAccessor.getSectionStorage();
        EntitySectionStorageAccessor sectionAccessor = (EntitySectionStorageAccessor) sectionStorage;
        int totalBlockEntities = 0;
        int totalEntities = 0;
        for (long chunkPos : chunks) {
            ChunkGroupSnapshot.ChunkLoadInfo info = countChunkLoad(level, chunkPos, sectionAccessor, sectionStorage);
            totalBlockEntities += info.blockEntityCount();
            totalEntities += info.entityCount();
        }
        return new ChunkGroupSnapshot.OwnerStats(chunks.size(), totalBlockEntities, totalEntities);
    }

    private static ChunkGroupSnapshot.ChunkLoadInfo countChunkLoad(ServerLevel level,
                                                                   long chunkPos,
                                                                   EntitySectionStorageAccessor sectionAccessor,
                                                                   EntitySectionStorage<?> sectionStorage) {
        ChunkPos pos = new ChunkPos(chunkPos);
        int blockEntityCount = 0;
        ChunkAccess chunk = level.getChunk(pos.x, pos.z, ChunkStatus.FULL, false);
        if (chunk != null) {
            blockEntityCount = chunk.getBlockEntitiesPos().size();
        }
        int entityCount = 0;
        var sectionIterator = sectionAccessor.invokeGetChunkSections(pos.x, pos.z).iterator();
        while (sectionIterator.hasNext()) {
            EntitySection<?> section = sectionStorage.getSection(sectionIterator.nextLong());
            if (section != null) {
                entityCount += section.size();
            }
        }
        return new ChunkGroupSnapshot.ChunkLoadInfo(blockEntityCount, entityCount);
    }

    private static List<ChunkGroupSnapshot.ChunkGroupEntry> buildGroupEntries(
            HashMap<TicketOwner, Set<Long>> ownerMap,
            HashMap<Long, ChunkGroupSnapshot.ChunkLoadInfo> chunkLoadInfoMap
    ) {
        List<ChunkGroupSnapshot.ChunkGroupEntry> entries = new ArrayList<>(ownerMap.size());
        for (var entry : ownerMap.entrySet()) {
            int totalBlockEntities = 0;
            int totalEntities = 0;
            for (long chunkPos : entry.getValue()) {
                ChunkGroupSnapshot.ChunkLoadInfo info = chunkLoadInfoMap.get(chunkPos);
                if (info != null) {
                    totalBlockEntities += info.blockEntityCount();
                    totalEntities += info.entityCount();
                }
            }
            entries.add(new ChunkGroupSnapshot.ChunkGroupEntry(
                    entry.getKey(),
                    entry.getValue(),
                    new ChunkGroupSnapshot.OwnerStats(entry.getValue().size(), totalBlockEntities, totalEntities)
            ));
        }
        entries.sort(ChunkGroupSnapshot.SortMode.BLOCK_ENTITY.comparator());
        return entries;
    }

    @SuppressWarnings("ConstantValue")
    private static Ticket<?> selectTicket(SortedArraySet<Ticket<?>> tickets,
                                          ChunkGroupSnapshot.PauseMode pauseMode) {
        Ticket<?> fallback = null;
        for (Ticket<?> ticket : tickets) {
            boolean paused = ((IPauseableTicket) (Object) ticket).serverWarashi$isPaused();
            if (!pauseMode.accept(paused)) {
                continue;
            }
            if (TicketUtils.getOriginLevel(ticket) > TicketUtils.MAX_PAUSEABLE_LEVEL) {
                continue;
            }
            if (!TicketUtils.isSystemTicket(ticket)) {
                return ticket;
            }
            if (fallback == null || systemTicketPriority(ticket) < systemTicketPriority(fallback)) {
                fallback = ticket;
            }
        }
        return fallback;
    }

    /**
     * ticket优先级,用于区分BE和E归属
     */
    private static int systemTicketPriority(Ticket<?> ticket) {
        var type = ticket.getType();
        if (type == net.minecraft.server.level.TicketType.FORCED) {
            return 0;
        }
        return 255;
    }
}
