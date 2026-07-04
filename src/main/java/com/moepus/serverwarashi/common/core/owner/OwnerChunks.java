package com.moepus.serverwarashi.common.core.owner;

import com.moepus.serverwarashi.common.core.ticket.IPauseableTicket;
import com.moepus.serverwarashi.common.core.ticket.TicketOwner;
import com.moepus.serverwarashi.common.core.ticket.TicketOwnerResolver;
import com.moepus.serverwarashi.common.core.ticket.TicketScanService;
import com.moepus.serverwarashi.common.core.ticket.TicketUtils;
import com.moepus.serverwarashi.common.mixin.accessor.PersistentEntitySectionManagerAccessor;
import com.moepus.serverwarashi.common.mixin.accessor.ServerLevelAccessor;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
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

public final class OwnerChunks {
    private OwnerChunks() {
    }

    public record Entry(TicketOwner owner, Set<Long> chunks, Stats stats) {
    }

    public record Stats(int chunkCount, int blockEntityCount, int entityCount) {
    }

    public record BothMaps(Map<TicketOwner, Set<Long>> activeOnly, Map<TicketOwner, Set<Long>> all) {
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

    public static List<Entry> collect(ServerLevel level, PauseMode pauseMode) {
        Map<TicketOwner, Set<Long>> ownerMap = collectMap(level, pauseMode);
        return collectFromMap(level, ownerMap);
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
            entries.add(entry(level, owner.getKey(), owner.getValue(), sectionStorage, chunkLoads));
        }
        return entries;
    }

    public static Map<TicketOwner, Set<Long>> collectMap(ServerLevel level, PauseMode pauseMode) {
        Map<TicketOwner, Set<Long>> ownerMap = new HashMap<>();
        TicketScanService.visitTicketChunks(level, (chunkPos, tickets) -> {
            // resolve 可能触发实体/玩家查找，开销大；系统 ticket 先只挑一张候补，仅当该 chunk 没有其他 owner 时才 resolve 这一次
            Ticket<?> fallback = null;
            int fallbackPriority = Integer.MAX_VALUE;
            boolean addedNonSystemOwner = false;
            for (Ticket<?> ticket : tickets) {
                if (!accept(ticket, pauseMode)) {
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
                if (TicketOwnerResolver.UNKNOWN_ENTITY.equals(owner.getName())) {
                    continue;
                }
                ownerMap.computeIfAbsent(owner, ignored -> new HashSet<>()).add(chunkPos);
                addedNonSystemOwner = true;
            }
            if (!addedNonSystemOwner && fallback != null) {
                TicketOwner owner = TicketOwnerResolver.resolve(fallback, level);
                if (!TicketOwnerResolver.UNKNOWN_ENTITY.equals(owner.getName())) {
                    ownerMap.computeIfAbsent(owner, ignored -> new HashSet<>()).add(chunkPos);
                }
            }
        });
        return ownerMap;
    }

    /**
     * 一次遍历同时返回 ACTIVE_ONLY 与 ALL 两个视图，避免双全量扫描。
     * 返回值：record BothMaps(activeOnly, all)
     */
    public static BothMaps collectBothMaps(ServerLevel level) {
        Map<TicketOwner, Set<Long>> activeOnly = new HashMap<>();
        Map<TicketOwner, Set<Long>> all = new HashMap<>();
        TicketScanService.visitTicketChunks(level, (chunkPos, tickets) -> {
            Ticket<?> fallback = null;
            int fallbackPriority = Integer.MAX_VALUE;
            boolean addedNonSystemOwner = false;
            for (Ticket<?> ticket : tickets) {
                boolean paused = ((IPauseableTicket) (Object) ticket).serverWarashi$isPaused();
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
                if (TicketOwnerResolver.UNKNOWN_ENTITY.equals(owner.getName())) {
                    continue;
                }
                all.computeIfAbsent(owner, ignored -> new HashSet<>()).add(chunkPos);
                if (!paused) {
                    activeOnly.computeIfAbsent(owner, ignored -> new HashSet<>()).add(chunkPos);
                }
                addedNonSystemOwner = true;
            }
            if (!addedNonSystemOwner && fallback != null) {
                TicketOwner owner = TicketOwnerResolver.resolve(fallback, level);
                if (!TicketOwnerResolver.UNKNOWN_ENTITY.equals(owner.getName())) {
                    all.computeIfAbsent(owner, ignored -> new HashSet<>()).add(chunkPos);
                    boolean paused = ((IPauseableTicket) (Object) fallback).serverWarashi$isPaused();
                    if (!paused) {
                        activeOnly.computeIfAbsent(owner, ignored -> new HashSet<>()).add(chunkPos);
                    }
                }
            }
        });
        return new BothMaps(activeOnly, all);
    }

    public static Map<TicketOwner, Set<Long>> resolveOwnersAt(ServerLevel level, long chunkPos, PauseMode pauseMode) {
        // 解析chunk命中的owner
        Map<TicketOwner, Set<Long>> owners = new HashMap<>();
        for (var owner : collectMap(level, pauseMode).entrySet()) {
            if (owner.getValue().contains(chunkPos)) {
                owners.put(owner.getKey(), owner.getValue());
            }
        }
        return owners;
    }

    public static Set<Long> resolveChunksAt(ServerLevel level, long chunkPos, PauseMode pauseMode) {
        LongOpenHashSet resolved = new LongOpenHashSet();
        for (Set<Long> chunks : resolveOwnersAt(level, chunkPos, pauseMode).values()) {
            resolved.addAll(chunks);
        }
        return resolved;
    }

    public static Stats stats(ServerLevel level, Set<Long> chunks) {
        EntitySectionStorage<?> sectionStorage = sectionStorage(level);
        return computeStats(level, chunks, sectionStorage, new HashMap<>());
    }

    public static Long2IntOpenHashMap ownerCountByChunk(Iterable<? extends Set<Long>> allOwnerChunks) {
        Long2IntOpenHashMap counts = new Long2IntOpenHashMap();
        for (Set<Long> chunks : allOwnerChunks) {
            for (long chunkPos : chunks) {
                counts.addTo(chunkPos, 1);
            }
        }
        return counts;
    }

    public static BlockPos uniqueChunkAnchor(Set<Long> chunks, Long2IntOpenHashMap ownerCountByChunk) {
        for (long chunkPos : chunks) {
            if (ownerCountByChunk.get(chunkPos) == 1) {
                return new ChunkPos(chunkPos).getWorldPosition();
            }
        }
        return null;
    }

    private static Entry entry(ServerLevel level,
                               TicketOwner owner,
                               Set<Long> chunks,
                               EntitySectionStorage<?> sectionStorage,
                               Map<Long, ChunkLoad> chunkLoads) {
        return new Entry(owner, chunks, computeStats(level, chunks, sectionStorage, chunkLoads));
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

    @SuppressWarnings("ConstantValue")
    private static boolean accept(Ticket<?> ticket, PauseMode pauseMode) {
        boolean paused = ((IPauseableTicket) (Object) ticket).serverWarashi$isPaused();
        return pauseMode.accept(paused) && TicketUtils.getOriginLevel(ticket) <= TicketUtils.MAX_PAUSEABLE_LEVEL;
    }

    private record ChunkLoad(int blockEntityCount, int entityCount) {
    }
}
