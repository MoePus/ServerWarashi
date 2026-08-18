package com.moepus.serverwarashi.common.modules.performance.report;

import org.junit.jupiter.api.Test;

import com.moepus.serverwarashi.common.modules.performance.sampling.TickDetailKind;
import com.moepus.serverwarashi.common.modules.performance.sampling.TickType;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class PerfReportTextRendererTest {
    @Test
    void reportsRowsHiddenByTextLimit() {
        PerfReportData.Metric metric = new PerfReportData.Metric(1.0D, 2.0D, 3L);
        List<PerfReportData.ChunkEntry> chunks = new ArrayList<>();
        for (int index = 0; index < 11; index++) {
            chunks.add(new PerfReportData.ChunkEntry("test", index, index, 0, 0,
                    List.of(), List.of(), metric));
        }
        PerfReportData report = new PerfReportData(20.0D, 1_000L, 123_000L, 20L, 100L, 1_000,
                0, 0, 0, chunks, List.of(), List.of());

        assertTrue(PerfReportTextRenderer.render(report).getString().contains("... 1 omitted"));
    }

    @Test
    void printsFullHandlerNameWithoutATooltip() {
        PerfReportData.Metric metric = new PerfReportData.Metric(1.0D, 2.0D, 3L);
        String fullName = "com.example.ServerEvents#onTick(ServerTickEvent.Pre)";
        var handler = new PerfReportData.TickableEntry(TickType.HANDLER, "", "ServerEvents#onTick",
                "", TickDetailKind.NONE, -1, false, 0, 0, 0, false, fullName, metric);
        PerfReportData report = new PerfReportData(20.0D, 1_000L, 123_000L, 20L, 100L, 1_000,
                0, 0, 0, List.of(), List.of(handler), List.of());

        assertTrue(PerfReportTextRenderer.render(report).getString().contains(fullName));
    }

    @Test
    void printsUnknownTaskWithoutAnEmptyEventLoopSuffix() {
        PerfReportData.Metric metric = new PerfReportData.Metric(1.0D, 2.0D, 3L);
        var unknown = new PerfReportData.TickableEntry(TickType.TASK, "", "Unknown",
                "", TickDetailKind.NONE, -1, false, 0, 0, 0, false, "", metric);
        PerfReportData report = new PerfReportData(20.0D, 1_000L, 123_000L, 20L, 100L, 1_000,
                0, 0, 0, List.of(), List.of(unknown), List.of());

        String text = PerfReportTextRenderer.render(report).getString();
        assertTrue(text.contains("TASK Unknown "));
        assertFalse(text.contains("Unknown @"));
    }

    @Test
    void printsRollingGcWithoutAnEmptyDimensionSuffix() {
        var gc = new PerfReportData.TickableEntry(TickType.GC, "", "Rolling 1s",
                "", TickDetailKind.NONE, -1, false, 0, 0, 0, false, "",
                new PerfReportData.Metric(2.5D, 20.0D, 20L));
        PerfReportData report = new PerfReportData(20.0D, 1_000L, 123_000L, 20L, 100L, 1_000,
                0, 0, 0, List.of(), List.of(gc), List.of());

        String text = PerfReportTextRenderer.render(report).getString();
        assertTrue(text.contains("GC Rolling 1s"));
        assertFalse(text.contains("Rolling 1s @"));
    }
}
