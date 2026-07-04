package com.moepus.serverwarashi.common.core.ticket;

import com.moepus.serverwarashi.common.mixin.accessor.DistanceManagerAccessor;
import net.minecraft.server.level.ServerLevel;

public final class TicketPauseService {
    private TicketPauseService() {
    }

    public static void clearAutoPause(ServerLevel level) {
        // 对该维度全部 chunk 解除 AUTO 暂停原因
        DistanceManagerAccessor distanceManager = TicketUtils.getDistanceManager(level);
        applyPauseReasonToChunks(
                level,
                distanceManager.getTickets().keySet(),
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
        return batch.flush();
    }
}
