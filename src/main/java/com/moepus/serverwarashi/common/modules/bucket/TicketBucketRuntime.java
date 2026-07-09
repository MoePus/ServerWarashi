package com.moepus.serverwarashi.common.modules.bucket;

import com.moepus.serverwarashi.common.core.ticket.TicketPauseService;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.HashSet;
import java.util.Set;

/**
 * 自动分桶模块对外入口。
 * 层级：入口层。
 * 上游：Commands / analyze。下游：TicketBucketService。
 */
public final class TicketBucketRuntime {
    private static long tickAge;
    private static final Set<ResourceKey<Level>> ANALYSIS_SUSPENDED_LEVELS = new HashSet<>();
    private static final TicketBucketService SERVICE = new TicketBucketService();

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
        SERVICE.processTickets(level, tickAge);
    }

    public static void clearRuntimeState() {
        tickAge = 0L;
        ANALYSIS_SUSPENDED_LEVELS.clear();
        SERVICE.clearRuntimeState();
    }

}
