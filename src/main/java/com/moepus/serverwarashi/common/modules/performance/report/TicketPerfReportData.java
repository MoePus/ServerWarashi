package com.moepus.serverwarashi.common.modules.performance.report;

import com.moepus.serverwarashi.common.core.ticket.TicketOwner;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.time.Duration;
import java.util.Map;

public final class TicketPerfReportData {
    private TicketPerfReportData() {
    }

    public record SingleOwnerReport(Summary summary,
                                    ActorStats blockEntities,
                                    ActorStats entities,
                                    ChunkStats chunks,
                                    Duration elapsed) {
    }

    public record Summary(ResourceKey<Level> dimension,
                          String ownerName,
                          BlockPos ownerPos,
                          int chunkCount,
                          int blockEntityCount,
                          int entityCount,
                          long serverTickCount) {
    }

    public record ActorStats(long totalNanos,
                             Object2LongOpenHashMap<String> spikeNanos,
                             Map<String, String> spikeLabels,
                             Object2LongOpenHashMap<String> typeTotals) {
    }

    public record ChunkStats(long totalNanos,
                             Long2LongOpenHashMap loadTotals) {
    }

    public record OwnerMsptEntry(TicketOwner owner,
                                 int chunkCount,
                                 int blockEntityCount,
                                 int entityCount,
                                 long totalNanos,
                                 BlockPos analyzeAnchor) {
    }
}
