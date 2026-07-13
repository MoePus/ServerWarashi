package com.moepus.serverwarashi.common.modules.bucket;

import com.moepus.serverwarashi.common.config.TicketBucketConfig;
import com.moepus.serverwarashi.common.core.ticket.PauseableTicket;
import com.moepus.serverwarashi.common.core.owner.TicketOwnerKey;
import com.moepus.serverwarashi.common.core.owner.TicketOwnerResolver;
import com.moepus.serverwarashi.common.core.ticket.TicketPauseBatch;
import com.moepus.serverwarashi.common.core.ticket.TicketPauseService;
import com.moepus.serverwarashi.common.core.ticket.TicketScanService;
import com.moepus.serverwarashi.common.core.ticket.TicketUtils;
import com.moepus.serverwarashi.common.mixin.accessor.DistanceManagerAccessor;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.Ticket;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 自动分桶模块对外入口：推进全局时钟、收集可分桶 ticket 并交给调度器。
 * 层级：入口层。
 * 上游：ServerWarashiEvents / Commands。下游：TicketBucketScheduler。
 */
public final class TicketBucketRuntime {
    private static long tickAge;
    private static final Set<ResourceKey<Level>> ANALYSIS_SUSPENDED_LEVELS = new HashSet<>();
    private static final TicketBucketScheduler SCHEDULER = new TicketBucketScheduler();

    private TicketBucketRuntime() {
    }

    public static void onServerTickPre() {
        tickAge++;
    }

    public static void onLevelTickPre(ServerLevel level, boolean hasActiveAnalysisSession) {
        if (hasActiveAnalysisSession) {
            // 该维度正在分析：首个 tick 先清掉 AUTO 暂停让分析看到真实负载，之后各 tick 跳过分桶
            if (ANALYSIS_SUSPENDED_LEVELS.add(level.dimension())) {
                TicketPauseService.clearAutoPause(level);
            }
            return;
        }
        ANALYSIS_SUSPENDED_LEVELS.remove(level.dimension());
        processTickets(level, tickAge);
    }

    public static void clearRuntimeState() {
        tickAge = 0L;
        ANALYSIS_SUSPENDED_LEVELS.clear();
        SCHEDULER.clear();
    }

    private static void processTickets(ServerLevel level, long age) {
        if (!TicketBucketConfig.enabled() || age % TicketBucketConfig.runEvery() != 0) {
            return;
        }
        DistanceManagerAccessor distanceManager = TicketUtils.getDistanceManager(level);
        TicketPauseBatch pauseBatch = new TicketPauseBatch(distanceManager);
        List<TicketEntry> tickets = collectTickets(level, distanceManager, pauseBatch);
        if (tickets.isEmpty()) {
            pauseBatch.flush();
            return;
        }

        if (TicketBucketConfig.pauseAll()) {
            for (TicketEntry entry : tickets) {
                pauseBatch.apply(entry.ticket(), entry.chunkPos(), true, PauseableTicket.PAUSE_REASON_AUTO);
            }
        } else {
            SCHEDULER.schedule(level, tickets, age, pauseBatch);
        }
        pauseBatch.flush();
    }

    /**
     * 收集参与自动分桶的 ticket。
     * 若 chunk 内含有任意保护型 ticket（如 PLAYER），整 chunk 跳过分桶。
     * vanilla 会在玩家 viewDistance 范围内每个 chunk 都注册一个 PLAYER ticket
     * 实例（level=31），借此天然覆盖玩家可见范围，避免削掉这些 chunk 的实体 tick。
     */
    private static List<TicketEntry> collectTickets(ServerLevel level,
                                                     DistanceManagerAccessor distanceManager,
                                                     TicketPauseBatch pauseBatch) {
        List<TicketEntry> allTickets = new ArrayList<>();
        TicketScanService.visitTicketChunks(distanceManager, (chunkPosLong, tickets) -> {
            // 含保护型 ticket（玩家/强加载/传送门等）的 chunk 整体跳过，不参与分桶
            if (containsChunkProtectionTicket(tickets)) {
                for (Ticket<?> ticket : tickets) {
                    pauseBatch.apply(ticket, chunkPosLong, false, PauseableTicket.PAUSE_REASON_AUTO);
                }
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

    record TicketEntry(Ticket<?> ticket, TicketOwnerKey ownerKey, long chunkPos) {
    }
}
