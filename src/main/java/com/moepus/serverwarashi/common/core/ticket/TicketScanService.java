package com.moepus.serverwarashi.common.core.ticket;

import com.moepus.serverwarashi.common.mixin.accessor.DistanceManagerAccessor;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.Ticket;
import net.minecraft.util.SortedArraySet;

import java.util.Objects;

/**
 * 低分配遍历 DistanceManager 当前 ticket 表的访问器。
 */
public final class TicketScanService {
    private TicketScanService() {
    }

    public static void visitTicketChunks(ServerLevel level, TicketChunkVisitor visitor) {
        DistanceManagerAccessor distanceManager = TicketUtils.getDistanceManager(level);
        visitTicketChunks(distanceManager, visitor);
    }

    public static void visitTicketChunks(DistanceManagerAccessor distanceManager, TicketChunkVisitor visitor) {
        Objects.requireNonNull(distanceManager, "distanceManager");
        Objects.requireNonNull(visitor, "visitor");
        for (Long2ObjectMap.Entry<SortedArraySet<Ticket<?>>> entry :
                TicketUtils.getTickets(distanceManager).long2ObjectEntrySet()) {
            long chunkPos = entry.getLongKey();
            SortedArraySet<Ticket<?>> tickets = entry.getValue();
            if (tickets == null || tickets.isEmpty()) {
                continue;
            }
            visitor.visitChunk(chunkPos, tickets);
        }
    }

    @FunctionalInterface
    public interface TicketChunkVisitor {
        void visitChunk(long chunkPos, SortedArraySet<Ticket<?>> tickets);
    }
}
