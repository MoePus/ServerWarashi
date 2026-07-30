package com.moepus.serverwarashi.common.watchdog;

import com.moepus.serverwarashi.common.core.owner.OwnerChunks;
import com.moepus.serverwarashi.common.core.owner.TicketOwner;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

public final class ServerWatchdogTicketAppender {
    private ServerWatchdogTicketAppender() {
    }

    public static void appendTicketInfoToReport(CrashReport report, Iterable<ServerLevel> levels) {
        try {
            CrashReportCategory category = report.addCategory("ServerWarashi Ticket Info");
            List<WatchdogTicketFormatter.Dimension> dimensions = new ArrayList<>();
            for (var level : levels) {
                String name = level.dimension().location().toString();
                try {
                    dimensions.add(WatchdogTicketFormatter.Dimension.success(name, activeOwners(level)));
                } catch (Exception exception) {
                    dimensions.add(WatchdogTicketFormatter.Dimension.failure(name, exception));
                }
            }
            category.setDetail("Active ticket owners", WatchdogTicketFormatter.format(dimensions));
        } catch (Exception ignored) {
            // 诊断信息失败不影响崩溃报告保存。
        }
    }

    private static List<WatchdogTicketFormatter.Owner> activeOwners(ServerLevel level) {
        var ownerChunks = OwnerChunks.collectBothMaps(level).activeOnly();
        WatchdogChunkSnapshot chunks = WatchdogChunkSnapshot.capture(level);
        List<WatchdogTicketFormatter.Owner> owners = new ArrayList<>(ownerChunks.size());
        for (var entry : ownerChunks.entrySet()) {
            owners.add(toWatchdogOwner(entry.getKey(), entry.getValue(), chunks));
        }
        owners.sort(Comparator.comparingInt(
                        (WatchdogTicketFormatter.Owner owner) -> owner.counts().blockEntities())
                .reversed()
                .thenComparing(WatchdogTicketFormatter.Owner::name));
        return owners;
    }

    private static WatchdogTicketFormatter.Owner toWatchdogOwner(
            TicketOwner owner, Set<Long> ownerChunks, WatchdogChunkSnapshot snapshot) {
        BlockPos pos = owner.representativePos();
        WatchdogTicketFormatter.Position position = pos == null
                ? null
                : new WatchdogTicketFormatter.Position(pos.getX(), pos.getY(), pos.getZ());
        WatchdogChunkSnapshot.Counts stats = snapshot.count(ownerChunks);
        WatchdogTicketFormatter.Counts counts = new WatchdogTicketFormatter.Counts(
                ownerChunks.size(), stats.blockEntities(), stats.entities());
        return new WatchdogTicketFormatter.Owner(owner.name(), position, counts);
    }
}
