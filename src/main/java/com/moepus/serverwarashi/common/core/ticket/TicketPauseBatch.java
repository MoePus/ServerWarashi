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
        if (!updateTicketPauseReason(ticket, paused, reasonMask)) {
            return false;
        }
        modifiedChunks.add(chunkPos);
        return true;
    }

    public void applyChunks(Iterable<Long> chunks, boolean paused, int reasonMask) {
        Long2ObjectMap<SortedArraySet<Ticket<?>>> ticketMap = distanceManager.getTickets();
        for (Long chunkPosObj : chunks) {
            long chunkPos = chunkPosObj;
            SortedArraySet<Ticket<?>> tickets = ticketMap.get(chunkPos);
            if (tickets == null || tickets.isEmpty()) {
                continue;
            }
            for (Ticket<?> ticket : tickets) {
                if (TicketUtils.isSystemTicket(ticket)) {
                    continue;
                }
                apply(ticket, chunkPos, paused, reasonMask);
            }
        }
    }

    public int flush() {
        int count = modifiedChunks.size();
        if (count > 0) {
            updateChunkLevel(distanceManager, modifiedChunks);
            modifiedChunks.clear();
        }
        return count;
    }

    /**
     * 根据实际 masked level 变化更新 tracker。
     * 旧 level 取 tracker 当前值，新 level 取所有 ticket 的 masked 最小值；
     * 暂停会让 source level 变大，恢复会让 source level 变小。
     */
    public static void updateChunkLevel(DistanceManagerAccessor distanceManager, LongOpenHashSet modifiedChunks) {
        var ticketMap = distanceManager.getTickets();
        for (long chunkPos : modifiedChunks) {
            SortedArraySet<Ticket<?>> ticketSet = ticketMap.get(chunkPos);
            if (ticketSet == null) {
                continue;
            }
            // 旧 level 取 tracker 当前值；新 level 取该 chunk 全票的 masked 最小
            int oldLevel = distanceManager.getTicketTracker().getLevel(chunkPos);
            int newLevel = getTicketLevel(ticketSet);
            // 新 level 数值更小 = 加载强度上升（对应恢复/解除暂停方向），作为 tracker.update 的 isDecreasing 实参
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
        IPauseableTicket ticketInfo = (IPauseableTicket) (Object) ticket;
        // 计算新旧掩码；无变化则直接返回 false
        int before = ticketInfo.serverWarashi$getPauseMask();
        int after = applyPauseMask(before, paused, reasonMask);
        if (before == after) {
            return false;
        }
        ticketInfo.serverWarashi$setPauseMask(after);
        return true;
    }

    static int applyPauseMask(int before, boolean paused, int reasonMask) {
        return paused ? (before | reasonMask) : (before & ~reasonMask);
    }
}
