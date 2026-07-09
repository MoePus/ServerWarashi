package com.moepus.serverwarashi.common.mixin.watchdog;

import com.moepus.serverwarashi.common.core.owner.OwnerChunks;
import com.moepus.serverwarashi.common.modules.performance.report.TicketPerfOwnerOutput;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.server.level.ServerLevel;


final class ServerWatchdogTicketAppender {
    private ServerWatchdogTicketAppender() {
    }

    static void appendTicketInfoToReport(CrashReport report, Iterable<ServerLevel> levels) {
        try {
            CrashReportCategory category = report.addCategory("Serverwarashi Ticket Info");
            for (var level : levels) {
                String ticketInfo = TicketPerfOwnerOutput.listOwnersPlainText(
                        level,
                        OwnerChunks.PauseMode.ACTIVE_ONLY,
                        "Running ticket owners:",
                        OwnerChunks.SortMode.BLOCK_ENTITY
                );
                category.setDetail("Dimension " + level.dimension().location(), ticketInfo);
            }
        } catch (Exception ignored) {
            // 诊断信息失败不影响崩溃报告保存。
        }
    }
}
