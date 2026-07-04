package com.moepus.serverwarashi.common.modules.performance.report;

import com.moepus.serverwarashi.common.core.owner.OwnerChunks;
import com.moepus.serverwarashi.common.core.ticket.TicketOwner;
import com.moepus.serverwarashi.common.report.ReportButtons;
import com.moepus.serverwarashi.common.report.TableReport;
import com.moepus.serverwarashi.common.report.TableReportRenderer;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
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
     * 纯文本（无客户端 mod）回退视图：渲染与结构化报告同源的 owner 表，行内动作始终可点击。
     */
    public static Component listOwners(ServerLevel level,
                                       OwnerChunks.PauseMode pauseMode,
                                       String header,
                                       OwnerChunks.SortMode sortMode) {
        return TableReportRenderer.toComponent(ownerReport(level, pauseMode, header, sortMode));
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
        // 一次遍历获取 ACTIVE_ONLY 与 ALL 两个视图，避免双全量扫描
        OwnerChunks.BothMaps both = OwnerChunks.collectBothMaps(level);
        Map<TicketOwner, Set<Long>> targetMap = pauseMode == OwnerChunks.PauseMode.ALL
                ? both.all()
                : both.activeOnly();

        List<OwnerChunks.Entry> owners = OwnerChunks.collectFromMap(level, targetMap).stream()
                .sorted(sortMode.comparator())
                .toList();
        // 锚点唯一性必须在 ALL 宇宙判定：分析按钮执行的 startSingle 按 ALL 判多 owner
        Long2IntOpenHashMap ownerCounts = OwnerChunks.ownerCountByChunk(both.all().values());
        List<List<String>> rows = new ArrayList<>();
        List<List<TableReport.Button>> rowButtons = new ArrayList<>();
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
            rowButtons.add(TicketPerfMessages.locateButtons(
                    level.dimension().location(),
                    ownerPos,
                    OwnerChunks.uniqueChunkAnchor(entry.chunks(), ownerCounts)
            ));
        }
        List<String> columns = List.of("Owner", "Pos", "Chunks", "BE", "E");
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

    private static TableReport ownerReportPlainText(ServerLevel level,
                                                    OwnerChunks.PauseMode pauseMode,
                                                    String header,
                                                    OwnerChunks.SortMode sortMode) {
        OwnerChunks.BothMaps both = OwnerChunks.collectBothMaps(level);
        Map<TicketOwner, Set<Long>> targetMap = pauseMode == OwnerChunks.PauseMode.ALL
                ? both.all()
                : both.activeOnly();

        List<OwnerChunks.Entry> owners = OwnerChunks.collectFromMap(level, targetMap).stream()
                .sorted(sortMode.comparator())
                .toList();

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
        List<String> columns = List.of("Owner", "Pos", "Chunks", "BE", "E");
        return new TableReport(
                header,
                List.of("Dimension: " + level.dimension().location()),
                List.of(TableReport.section(
                        "Owners",
                        columns,
                        rows,
                        List.of()
                ))
        );
    }

}
