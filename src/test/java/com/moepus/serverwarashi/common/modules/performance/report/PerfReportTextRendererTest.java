package com.moepus.serverwarashi.common.modules.performance.report;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

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
}
