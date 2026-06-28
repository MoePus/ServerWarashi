package com.moepus.serverwarashi.common.core.ticket;

import com.moepus.serverwarashi.common.mixin.accessor.DistanceManagerAccessor;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.Ticket;
import net.minecraft.util.SortedArraySet;

import java.util.Objects;

/**
 * 批量写入暂停掩码，并集中刷新同一个 DistanceManager 的 tracker。
 */
public final class TicketPauseBatch {
    private final DistanceManagerAccessor distanceManager;
    private final LongOpenHashSet modifiedChunks = new LongOpenHashSet();
    private final LongOpenHashSet updatedChunks = new LongOpenHashSet();

    private TicketPauseBatch(DistanceManagerAccessor distanceManager) {
        this.distanceManager = Objects.requireNonNull(distanceManager, "distanceManager");
    }

    public static TicketPauseBatch forLevel(ServerLevel level) {
        return new TicketPauseBatch(TicketUtils.getDistanceManager(level));
    }

    public static TicketPauseBatch forDistanceManager(DistanceManagerAccessor distanceManager) {
        return new TicketPauseBatch(distanceManager);
    }

    /**
     * 低层单 ticket 入口，调用方负责过滤系统 ticket。
     */
    public boolean apply(Ticket<?> ticket, long chunkPos, boolean paused, int reasonMask) {
        if (!TicketPauseService.updateTicketPauseReason(ticket, paused, reasonMask)) {
            return false;
        }
        modifiedChunks.add(chunkPos);
        updatedChunks.add(chunkPos);
        return true;
    }

    public boolean applyChunkTickets(long chunkPos,
                                     SortedArraySet<Ticket<?>> tickets,
                                     boolean paused,
                                     int reasonMask) {
        if (tickets == null || tickets.isEmpty()) {
            return false;
        }

        boolean changed = false;
        for (Ticket<?> ticket : tickets) {
            if (TicketUtils.isSystemTicket(ticket)) {
                continue;
            }
            changed |= apply(ticket, chunkPos, paused, reasonMask);
        }
        return changed;
    }

    public void applyChunks(Iterable<Long> chunks, boolean paused, int reasonMask) {
        Long2ObjectMap<SortedArraySet<Ticket<?>>> ticketMap = TicketUtils.getTickets(distanceManager);
        for (Long chunkPosObj : chunks) {
            long chunkPos = chunkPosObj;
            SortedArraySet<Ticket<?>> tickets = ticketMap.get(chunkPos);
            applyChunkTickets(chunkPos, tickets, paused, reasonMask);
        }
    }

    public void flush() {
        if (!modifiedChunks.isEmpty()) {
            TicketPauseService.updateChunkLevel(distanceManager, modifiedChunks);
            modifiedChunks.clear();
        }
    }

    /**
     * 返回本批次中标记过刷新的不同 chunk 数。
     */
    public int updatedChunks() {
        return updatedChunks.size();
    }
}
