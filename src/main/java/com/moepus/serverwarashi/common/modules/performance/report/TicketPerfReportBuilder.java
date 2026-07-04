package com.moepus.serverwarashi.common.modules.performance.report;

import com.moepus.serverwarashi.common.report.ReportButtons;
import com.moepus.serverwarashi.common.report.TableReport;
import com.moepus.serverwarashi.common.report.TopN;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class TicketPerfReportBuilder {
    private static final long SPIKE_MIN_NANOS = 50_000L;
    private static final double MIN_DISPLAY_MSPT = 0.01D;
    private static final int TOP_ROW_LIMIT = 5;
    private static final Comparator<Object2LongOpenHashMap.Entry<String>> BY_VALUE_DESC =
            (a, b) -> Long.compare(b.getLongValue(), a.getLongValue());

    private TicketPerfReportBuilder() {
    }

    public static TableReport buildOwnerMsptPerfReport(ResourceKey<Level> dimension,
                                                       List<TicketPerfReportData.OwnerMsptEntry> entries,
                                                       long serverTickCount,
                                                       Duration elapsed,
                                                       boolean hasSharedChunks) {
        List<List<String>> rows = new ArrayList<>();
        List<List<TableReport.Button>> rowButtons = new ArrayList<>();
        for (TicketPerfReportData.OwnerMsptEntry entry : entries) {
            double entryMspt = mspt(entry.totalNanos(), serverTickCount);
            if (entryMspt < MIN_DISPLAY_MSPT) {
                continue;
            }
            BlockPos ownerPos = entry.owner().representativePos();
            rows.add(List.of(
                    entry.owner().getName(),
                    ReportButtons.formatPos(ownerPos),
                    Integer.toString(entry.chunkCount()),
                    Integer.toString(entry.blockEntityCount()),
                    Integer.toString(entry.entityCount()),
                    String.format(Locale.ROOT, "%.2f", entryMspt)
            ));
            rowButtons.add(TicketPerfMessages.locateButtons(dimension.location(), ownerPos, entry.analyzeAnchor()));
        }
        List<String> summary = new ArrayList<>(List.of(
                "Target: " + dimension.location(),
                "Owners: " + entries.size(),
                "Displayed: " + rows.size(),
                "Elapsed: " + elapsed.toSeconds() + "s",
                "ServerTicks: " + serverTickCount
        ));
        if (hasSharedChunks) {
            summary.add("Shared chunks count once per owner row.");
        }
        return new TableReport(
                "Owner MSPT Report",
                summary,
                List.of(TableReport.section(
                        "Owners",
                        List.of("Owner", "Pos", "Chunks", "BE", "E", "MSPT"),
                        rows,
                        rowButtons
                ))
        );
    }

    public static TableReport buildPerfReport(TicketPerfReportData.SingleOwnerReport report) {
        TicketPerfReportData.Summary summary = report.summary();
        List<TableReport.Section> sections = new ArrayList<>();
        sections.add(buildSummarySection(report));
        addSection(sections, "Top Chunks", List.of("Chunk", "BE+E MSPT"),
                buildTopChunkRows(report.chunks().loadTotals(), summary.serverTickCount()));
        addSection(sections, "Spike Block Entities", List.of("Source", "Max ms"),
                buildSpikeRows(report.blockEntities().spikeNanos(), report.blockEntities().spikeLabels()));
        addSection(sections, "Top Block Entities", List.of("Type", "MSPT"),
                buildTopTypeRows(report.blockEntities().typeTotals(), summary.serverTickCount()));
        addSection(sections, "Spike Entities", List.of("Source", "Max ms"),
                buildSpikeRows(report.entities().spikeNanos(), report.entities().spikeLabels()));
        addSection(sections, "Top Entities", List.of("Type", "MSPT"),
                buildTopTypeRows(report.entities().typeTotals(), summary.serverTickCount()));
        return new TableReport(
                "Serverwarashi Profiler Report",
                List.of("Elapsed: " + report.elapsed().toSeconds() + "s"),
                sections
        );
    }

    private static TableReport.Section buildSummarySection(TicketPerfReportData.SingleOwnerReport report) {
        TicketPerfReportData.Summary summary = report.summary();
        long totalNanos = report.blockEntities().totalNanos()
                + report.entities().totalNanos()
                + report.chunks().totalNanos();
        List<List<String>> rows = List.of(
                List.of("Target", summary.dimension().location().toString()),
                List.of("Owner", summary.ownerName()),
                List.of("Pos", ReportButtons.formatPos(summary.ownerPos())),
                List.of("Chunks", Integer.toString(summary.chunkCount())),
                List.of("BlockEntities", Integer.toString(summary.blockEntityCount())),
                List.of("Entities", Integer.toString(summary.entityCount())),
                List.of("ServerTicks", Long.toString(summary.serverTickCount())),
                List.of("Total MSPT", String.format(Locale.ROOT, "%.2f", mspt(totalNanos, summary.serverTickCount())))
        );
        List<List<TableReport.Button>> buttons = new ArrayList<>();
        TableReport.Button teleport = ReportButtons.teleport(summary.dimension().location(), summary.ownerPos());
        for (List<String> row : rows) {
            buttons.add("Pos".equals(row.getFirst()) && teleport != null ? List.of(teleport) : List.of());
        }
        return TableReport.section("Summary", List.of("Metric", "Value"), rows, buttons);
    }

    private static List<List<String>> buildTopChunkRows(Long2LongOpenHashMap chunkLoadTotals,
                                                        long serverTickCount) {
        List<List<String>> rows = new ArrayList<>();
        Comparator<Long2LongOpenHashMap.Entry> order = (a, b) -> Long.compare(b.getLongValue(), a.getLongValue());
        for (var entry : TopN.select(chunkLoadTotals.long2LongEntrySet(), TOP_ROW_LIMIT, order)) {
            ChunkPos chunkPos = new ChunkPos(entry.getLongKey());
            rows.add(List.of("chunk(" + chunkPos.x + "," + chunkPos.z + ")",
                    String.format(Locale.ROOT, "%.2f", mspt(entry.getLongValue(), serverTickCount))));
        }
        return rows;
    }

    private static List<List<String>> buildSpikeRows(Object2LongOpenHashMap<String> spikeNanos,
                                                     Map<String, String> spikeLabels) {
        List<List<String>> rows = new ArrayList<>();
        List<Object2LongOpenHashMap.Entry<String>> candidates = spikeNanos.object2LongEntrySet().stream()
                .filter(entry -> entry.getLongValue() >= SPIKE_MIN_NANOS)
                .toList();
        for (var entry : TopN.select(candidates, TOP_ROW_LIMIT, BY_VALUE_DESC)) {
            rows.add(List.of(
                    spikeLabels.getOrDefault(entry.getKey(), entry.getKey()),
                    String.format(Locale.ROOT, "%.3f", nanosToMs(entry.getLongValue()))
            ));
        }
        return rows;
    }

    private static List<List<String>> buildTopTypeRows(Object2LongOpenHashMap<String> totals,
                                                       long serverTickCount) {
        List<List<String>> rows = new ArrayList<>();
        for (var entry : TopN.select(totals.object2LongEntrySet(), TOP_ROW_LIMIT, BY_VALUE_DESC)) {
            double typeMspt = mspt(entry.getLongValue(), serverTickCount);
            if (typeMspt >= MIN_DISPLAY_MSPT) {
                rows.add(List.of(entry.getKey(), String.format(Locale.ROOT, "%.2f", typeMspt)));
            }
        }
        return rows;
    }

    private static void addSection(List<TableReport.Section> sections,
                                   String title,
                                   List<String> columns,
                                   List<List<String>> rows) {
        if (!rows.isEmpty()) {
            sections.add(TableReport.section(title, columns, rows, List.of()));
        }
    }

    private static double nanosToMs(long nanos) {
        return nanos / 1_000_000.0;
    }

    private static double mspt(long nanos, long ticks) {
        return ticks == 0 ? 0.0 : nanosToMs(nanos) / ticks;
    }
}
