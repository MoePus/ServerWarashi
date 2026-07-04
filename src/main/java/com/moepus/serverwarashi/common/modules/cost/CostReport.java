package com.moepus.serverwarashi.common.modules.cost;

import com.moepus.serverwarashi.common.config.TicketPerfConfig;
import com.moepus.serverwarashi.common.core.owner.OwnerChunks;
import com.moepus.serverwarashi.common.core.ticket.TicketOwner;
import com.moepus.serverwarashi.common.modules.performance.report.TicketPerfMessages;
import com.moepus.serverwarashi.common.report.ReportButtons;
import com.moepus.serverwarashi.common.report.TableReport;
import com.moepus.serverwarashi.common.report.TopN;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
/**
 * Resident Chunk Cost 报告构建（cost 模块的渲染分片）。
 */
public final class CostReport {
    private CostReport() {
    }

    public static TableReport build(net.minecraft.server.level.ServerLevel level,
                                    Map<TicketOwner, Set<Long>> activeOwners,
                                    int maxOwners,
                                    Long2IntOpenHashMap anchorOwnerCounts) {
        ResourceLocation dimension = level.dimension().location();
        // 算各 owner 成本（廉价：仅 map 查询）、过滤掉无成本的，按成本降序取 Top maxOwners
        Comparator<OwnerCost> costOrder = Comparator.comparingLong(OwnerCost::costNanos).reversed()
                .thenComparing(cost -> cost.owner().toString());
        List<OwnerCost> allCosts = activeOwners.entrySet().stream()
                .map(entry -> ownerCost(entry.getKey(), entry.getValue(), level))
                .filter(cost -> cost.costNanos() > 0L)
                .toList();
        List<OwnerCost> costs = TopN.select(allCosts, maxOwners, costOrder);

        // 逐 owner 拼成表格行 + 行内传送/分析按钮；仅对入选 Top-N 调 statsProvider 清点 BE/E（昂贵）
        List<List<String>> rows = new ArrayList<>();
        List<List<TableReport.Button>> rowButtons = new ArrayList<>();
        for (OwnerCost cost : costs) {
            OwnerChunks.Stats stats = OwnerChunks.stats(level, cost.chunks());
            BlockPos ownerPos = cost.owner().representativePos();
            rows.add(List.of(
                    cost.owner().getName(),
                    ReportButtons.formatPos(ownerPos),
                    Integer.toString(stats.chunkCount()),
                    Integer.toString(stats.blockEntityCount()),
                    Integer.toString(stats.entityCount()),
                    formatMspt(cost.chunkNanos()),
                    formatMspt(cost.blockEntityNanos()),
                    formatMspt(cost.entityNanos()),
                    formatMspt(cost.costNanos()),
                    cost.knownChunks() + "/" + cost.chunks().size()
            ));
            // 传送 + 分析按钮由 TicketPerfMessages 统一装配
            BlockPos analyzeAnchor = OwnerChunks.uniqueChunkAnchor(cost.chunks(), anchorOwnerCounts);
            rowButtons.add(TicketPerfMessages.locateButtons(dimension, ownerPos, analyzeAnchor));
        }

        // 头部元信息：维度 + 活跃 owner 数/已采样 owner 数
        List<String> metadata = List.of(
                "Dimension: " + dimension,
                "Owners: " + activeOwners.size() + " active, " + costs.size() + " sampled"
        );
        return new TableReport(
                "Resident Chunk Cost",
                metadata,
                List.of(TableReport.section(
                        "Top active owners",
                        List.of("Owner", "Pos", "Chunks", "BE", "E", "ChunkMSPT", "BEMSPT", "EMSPT", "MSPT", "Known"),
                        rows,
                        rowButtons
                ))
        );
    }

    private static OwnerCost ownerCost(TicketOwner owner,
                                       Set<Long> chunks,
                                       net.minecraft.server.level.ServerLevel level) {
        // 累加 owner 下已知成本的 chunk，并统计已知 chunk 数（用于显示覆盖度）
        long chunkTotal = 0L;
        long blockEntityTotal = 0L;
        long entityTotal = 0L;
        int known = 0;
        for (long chunk : chunks) {
            ChunkCostRuntime.CostSample cost = ChunkCostRuntime.costSampleForChunk(level, chunk);
            if (cost.hasKnownCost()) {
                chunkTotal += knownNanos(cost.chunkNanos());
                blockEntityTotal += knownNanos(cost.blockEntityNanos());
                entityTotal += knownNanos(cost.entityNanos());
                known++;
            }
        }
        return new OwnerCost(owner, chunks, chunkTotal, blockEntityTotal, entityTotal, known);
    }

    private static String formatMspt(long nanos) {
        return String.format(Locale.ROOT, "%.3f", nanos / 1_000_000.0);
    }

    private static long knownNanos(long nanos) {
        return nanos == ChunkCostRuntime.UNKNOWN_COST_NANOS ? 0L : nanos;
    }

    private record OwnerCost(TicketOwner owner,
                             Set<Long> chunks,
                             long chunkNanos,
                             long blockEntityNanos,
                             long entityNanos,
                             int knownChunks) {
        private long costNanos() {
            return chunkNanos + blockEntityNanos + entityNanos;
        }
    }
}
