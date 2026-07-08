package com.moepus.serverwarashi.common.modules.performance.report;

import com.moepus.serverwarashi.common.core.owner.OwnerChunks;
import com.moepus.serverwarashi.common.core.ticket.TicketOwner;
import com.moepus.serverwarashi.common.report.ReportButtons;
import com.moepus.serverwarashi.common.report.TableReport;
import com.moepus.serverwarashi.common.report.TableReportRenderer;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * ChunkPerf owner 展示视图。
 */
public final class TicketPerfOwnerOutput {
    private TicketPerfOwnerOutput() {
    }

    /**
     * 崩溃报告专用：纯文本表格，无交互按钮。
     */
    public static String listOwnersPlainText(ServerLevel level,
                                             OwnerChunks.PauseMode pauseMode,
                                             String header,
                                             OwnerChunks.SortMode sortMode) {
        TableReport report = ownerReportPlainText(level, pauseMode, header, sortMode);
        return TableReportRenderer.toComponent(report).getString();
    }

    public static TableReport ownerReport(ServerLevel level,
                                          OwnerChunks.PauseMode pauseMode,
                                          String header,
                                          OwnerChunks.SortMode sortMode) {
        OwnerReportData data = ownerReportData(level, pauseMode, sortMode);
        // 锚点唯一性必须按 ALL 全集（含已暂停 owner）判定：分析按钮执行的 startSingle 就是按 ALL 判定是否多 owner
        Long2IntOpenHashMap ownerCounts = OwnerChunks.ownerCountByChunk(data.allOwnerChunks());
        List<List<TableReport.Button>> rowButtons = new ArrayList<>();
        for (OwnerChunks.Entry entry : data.owners()) {
            BlockPos ownerPos = entry.owner().representativePos();
            rowButtons.add(TicketPerfMessages.locateButtons(
                    level.dimension().location(),
                    ownerPos,
                    OwnerChunks.uniqueChunkAnchor(entry.chunks(), ownerCounts)
            ));
        }
        return buildOwnerReport(level, header, data.owners(), rowButtons);
    }

    private static TableReport ownerReportPlainText(ServerLevel level,
                                                    OwnerChunks.PauseMode pauseMode,
                                                    String header,
                                                    OwnerChunks.SortMode sortMode) {
        OwnerReportData data = ownerReportData(level, pauseMode, sortMode);
        return buildOwnerReport(level, header, data.owners(), List.of());
    }

    private static OwnerReportData ownerReportData(ServerLevel level,
                                                   OwnerChunks.PauseMode pauseMode,
                                                   OwnerChunks.SortMode sortMode) {
        OwnerChunks.BothMaps both = OwnerChunks.collectBothMaps(level);
        Map<TicketOwner, Set<Long>> targetMap = pauseMode == OwnerChunks.PauseMode.ALL
                ? both.all()
                : both.activeOnly();

        List<OwnerChunks.Entry> owners = OwnerChunks.collectFromMap(level, targetMap).stream()
                .sorted(sortMode.comparator())
                .toList();
        return new OwnerReportData(owners, both.all().values());
    }

    private static TableReport buildOwnerReport(ServerLevel level,
                                                String header,
                                                List<OwnerChunks.Entry> owners,
                                                List<List<TableReport.Button>> rowButtons) {
        List<String> columns = List.of("Owner", "Pos", "Chunks", "BE", "E");
        List<List<String>> rows = new ArrayList<>();
        for (OwnerChunks.Entry entry : owners) {
            OwnerChunks.Stats stats = entry.stats();
            BlockPos ownerPos = entry.owner().representativePos();
            rows.add(List.of(
                    entry.owner().getName(),
                    ReportButtons.formatPos(ownerPos),
                    Integer.toString(stats.chunkCount()),
                    Integer.toString(stats.blockEntityCount()),
                    Integer.toString(stats.entityCount())
            ));
        }
        return new TableReport(
                header,
                List.of("Dimension: " + level.dimension().location()),
                List.of(TableReport.section(
                        "Owners",
                        columns,
                        rows,
                        rowButtons
                ))
        );
    }

    private record OwnerReportData(List<OwnerChunks.Entry> owners, Iterable<? extends Set<Long>> allOwnerChunks) {
    }
}
