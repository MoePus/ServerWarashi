package com.moepus.serverwarashi.common.core.ticket;

import com.moepus.serverwarashi.common.mixin.accessor.DistanceManagerAccessor;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.Ticket;
import net.minecraft.util.SortedArraySet;

public final class TicketPauseService {
    private TicketPauseService() {
    }

    public static void clearAutoPause(ServerLevel level) {
        // 对该维度全部 chunk 解除 AUTO 暂停原因
        applyPauseReasonToChunks(
                level,
                TicketUtils.getTickets(TicketUtils.getDistanceManager(level)).keySet(),
                false,
                IPauseableTicket.PAUSE_REASON_AUTO
        );
    }

    public static int applyPauseReasonToChunks(ServerLevel level,
                                               Iterable<Long> chunks,
                                               boolean paused,
                                               int reasonMask) {
        TicketPauseBatch batch = TicketPauseBatch.forLevel(level);
        batch.applyChunks(chunks, paused, reasonMask);
        batch.flush();
        return batch.updatedChunks();
    }

    /**
     * 根据实际 masked level 变化更新 tracker。
     * 旧 level 取 tracker 当前值，新 level 取所有 ticket 的 masked 最小值；
     * 暂停会让 source level 变大，恢复会让 source level 变小。
     */
    public static void updateChunkLevel(DistanceManagerAccessor distanceManager, LongOpenHashSet modifiedChunks) {
        var ticketMap = TicketUtils.getTickets(distanceManager);
        for (long chunkPos : modifiedChunks) {
            SortedArraySet<Ticket<?>> ticketSet = ticketMap.get(chunkPos);
            if (ticketSet == null) {
                continue;
            }
            // 旧 level 取 tracker 当前值；新 level 取该 chunk 全票的 masked 最小
            int oldLevel = distanceManager.getTicketTracker().getLevel(chunkPos);
            int newLevel = getTicketLevel(ticketSet);
            // 新 level 更小表示在"降级/恢复"方向
            boolean isDecreasing = newLevel < oldLevel;
            distanceManager.getTicketTracker().update(chunkPos, newLevel, isDecreasing);
            // 仍有 ticket 时同步更新 ticking tracker
            if (!ticketSet.isEmpty()) {
                distanceManager.getTickingTicketsTracker().update(chunkPos, newLevel, isDecreasing);
            }
        }
    }

    private static int getTicketLevel(SortedArraySet<Ticket<?>> tickets) {
        int level = TicketUtils.NO_TICKET_LEVEL;
        for (Ticket<?> ticket : tickets) {
            level = Math.min(level, ticket.getTicketLevel());
        }
        return level;
    }

    // 仅修改 pauseMask，不直接改原始 ticket level。
    public static boolean updateTicketPauseReason(Ticket<?> ticket, boolean paused, int reasonMask) {
        if (ticket == null) {
            return false;
        }
        IPauseableTicket ticketInfo = (IPauseableTicket) (Object) ticket;
        // 计算新旧掩码；无变化则直接返回 false
        int before = ticketInfo.serverWarashi$getPauseMask();
        int after = applyPauseMask(before, paused, reasonMask);
        if (before == after) {
            return false;
        }
        ticketInfo.serverWarashi$setPauseMask(after);
        // 仅当 mixin 标记需要更新 level 时才返回 true（并清脏标记）
        boolean needUpdate = ticketInfo.serverWarashi$needUpdate();
        if (needUpdate) {
            ticketInfo.serverWarashi$clearDirty();
        }
        return needUpdate;
    }

    static int applyPauseMask(int before, boolean paused, int reasonMask) {
        return paused ? (before | reasonMask) : (before & ~reasonMask);
    }
}
