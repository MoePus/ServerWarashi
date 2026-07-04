package com.moepus.serverwarashi.common.mixin.watchdog;

import com.moepus.serverwarashi.common.core.owner.OwnerChunks;
import com.moepus.serverwarashi.common.modules.performance.report.TicketPerfOwnerOutput;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportType;
import net.minecraft.server.level.ServerLevel;

import java.nio.file.Path;
import java.util.function.Supplier;


final class ServerWatchdogTicketAppender {
    private ServerWatchdogTicketAppender() {
    }

    static boolean saveWithTicketInfo(CrashReport instance,
                                      Path path,
                                      ReportType type,
                                      CrashReportSaveOperation saveOperation,
                                      Supplier<Iterable<ServerLevel>> levelsSupplier) {
        appendTicketInfoToReport(instance, levelsSupplier);
        return saveOperation.save(instance, path, type);
    }

    static void appendTicketInfoToReport(CrashReport report, Supplier<Iterable<ServerLevel>> levelsSupplier) {
        try {
            CrashReportCategory category = report.addCategory("Serverwarashi Ticket Info");
            for (var level : levelsSupplier.get()) {
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

    @FunctionalInterface
    interface CrashReportSaveOperation {
        boolean save(CrashReport instance, Path path, ReportType type);
    }
}
