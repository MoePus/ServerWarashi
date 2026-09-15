package com.moepus.serverwarashi.common.core.ticket;

import com.moepus.serverwarashi.common.mixin.accessor.DistanceManagerAccessor;
import com.moepus.serverwarashi.common.mixin.accessor.TickingTrackerAccessor;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.Ticket;
import net.minecraft.util.SortedArraySet;
import net.minecraft.world.level.ChunkPos;

/**
 * 批量写入暂停掩码，并集中刷新同一个 DistanceManager 的 tracker。
 */
public final class TicketPauseBatch {
    private final DistanceManagerAccessor distanceManager;
    private final LongOpenHashSet modifiedChunks = new LongOpenHashSet();

    public TicketPauseBatch(DistanceManagerAccessor distanceManager) {
        this.distanceManager = distanceManager;
    }

    public TicketPauseBatch(ServerLevel level) {
        this(TicketUtils.getDistanceManager(level));
    }

    /**
     * 低层单 ticket 入口，调用方负责过滤不可暂停或保护型 ticket。
     */
    public boolean apply(Ticket<?> ticket, long chunkPos, boolean paused, int reasonMask) {
        if (!updateTicketPauseReason(ticket, paused, reasonMask)) {
            return false;
        }
        modifiedChunks.add(chunkPos);
        return true;
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
            int newLevel = ticketSet.isEmpty()
                    ? TicketUtils.NO_TICKET_LEVEL
                    : TicketUtils.strictestMaskedTicket(ticketSet).getTicketLevel();
            // 新 level 数值更小 = 加载强度上升（对应恢复/解除暂停方向），作为 tracker.update 的 isDecreasing 实参
            boolean isDecreasing = newLevel < oldLevel;
            distanceManager.getTicketTracker().update(chunkPos, newLevel, isDecreasing);

            // Region/player tickets live in a separate TickingTracker ticket set. Derive its level independently;
            // using the main set here would inject main-only tickets into the ticking graph.
            var tickingTracker = distanceManager.getTickingTicketsTracker();
            var tickingTicketSet = ((TickingTrackerAccessor) tickingTracker).getTickets().get(chunkPos);
            if (tickingTicketSet != null && !tickingTicketSet.isEmpty()) {
                int oldTickingLevel = tickingTracker.getLevel(new ChunkPos(chunkPos));
                int newTickingLevel = TicketUtils.strictestMaskedTicket(tickingTicketSet).getTicketLevel();
                tickingTracker.update(chunkPos, newTickingLevel, newTickingLevel < oldTickingLevel);
            }
        }
    }

    // 仅修改 pauseMask，不直接改原始 ticket level。
    public static boolean updateTicketPauseReason(Ticket<?> ticket, boolean paused, int reasonMask) {
        PauseableTicket ticketInfo = TicketUtils.asPauseable(ticket);
        // 计算新旧掩码；无变化则直接返回 false
        int before = ticketInfo.serverWarashi$getPauseMask();
        int after = paused ? (before | reasonMask) : (before & ~reasonMask);
        if (before == after) {
            return false;
        }
        ticketInfo.serverWarashi$setPauseMask(after);
        return true;
    }
}
