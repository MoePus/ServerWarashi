package com.moepus.serverwarashi.common.modules.bucket;

import com.moepus.serverwarashi.common.core.ticket.IPauseableTicket;
import com.moepus.serverwarashi.common.core.ticket.TicketOwnerKey;
import com.moepus.serverwarashi.common.core.ticket.TicketOwnerResolver;
import com.moepus.serverwarashi.common.core.ticket.TicketPauseBatch;
import com.moepus.serverwarashi.common.core.ticket.TicketScanService;
import com.moepus.serverwarashi.common.core.ticket.TicketUtils;
import com.moepus.serverwarashi.common.config.TicketBucketConfig;
import com.moepus.serverwarashi.common.mixin.accessor.DistanceManagerAccessor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.Ticket;

import java.util.ArrayList;
import java.util.List;

/**
 * 自动分桶策略实现。
 */
final class TicketBucketService {
    private final TicketBucketScheduler scheduler = new TicketBucketScheduler();

    void processTickets(ServerLevel level, long age) {
        if (!TicketBucketConfig.enabled() || age % TicketBucketConfig.runEvery() != 0) {
            return;
        }
        DistanceManagerAccessor distanceManager = TicketUtils.getDistanceManager(level);
        var tickets = collectTickets(level, distanceManager);
        if (tickets.isEmpty()) {
            return;
        }

        TicketPauseBatch pauseBatch = TicketPauseBatch.forDistanceManager(distanceManager);
        if (TicketBucketConfig.pauseAll()) {
            for (TicketEntry entry : tickets) {
                pauseBatch.apply(entry.ticket, entry.chunkPos, true, IPauseableTicket.PAUSE_REASON_AUTO);
            }
        } else {
            scheduler.schedule(level, tickets, age, pauseBatch);
        }
        pauseBatch.flush();
    }

    void clearRuntimeState() {
        scheduler.clear();
    }

    /**
     * 收集参与自动分桶的 ticket。
     * 若 chunk 内含有任意保护型 ticket（如 PLAYER），整 chunk 跳过分桶。
     * vanilla 会在玩家 viewDistance 范围内每个 chunk 都注册一个 PLAYER ticket
     * 实例（level=31），借此天然覆盖玩家可见范围，避免削掉这些 chunk 的实体 tick。
     *
     * @return 可参与自动分桶的 ticket 列表
     */
    private List<TicketEntry> collectTickets(ServerLevel level, DistanceManagerAccessor distanceManager) {
        List<TicketEntry> allTickets = new ArrayList<>();
        TicketScanService.visitTicketChunks(distanceManager, (chunkPosLong, tickets) -> {
            // 含保护型 ticket（玩家/强加载/传送门等）的 chunk 整体跳过，不参与分桶
            if (containsChunkProtectionTicket(tickets)) {
                return;
            }

            for (Ticket<?> ticket : tickets) {
                if (TicketUtils.getOriginLevel(ticket) > TicketUtils.MAX_PAUSEABLE_LEVEL) {
                    continue;
                }
                allTickets.add(new TicketEntry(ticket, TicketOwnerResolver.resolveKey(ticket, level), chunkPosLong));
            }
        });
        return allTickets;
    }

    private static boolean containsChunkProtectionTicket(Iterable<? extends Ticket<?>> tickets) {
        for (Ticket<?> ticket : tickets) {
            if (TicketUtils.isChunkProtectionTicket(ticket)) {
                return true;
            }
        }
        return false;
    }

    record TicketEntry(Ticket<?> ticket, TicketOwnerKey ownerKey, long chunkPos) {}
}
