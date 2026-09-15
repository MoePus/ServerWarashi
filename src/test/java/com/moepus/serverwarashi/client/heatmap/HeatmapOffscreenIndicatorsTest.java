package com.moepus.serverwarashi.client.heatmap;

import com.moepus.serverwarashi.api.client.map.MapRenderContext;
import com.moepus.serverwarashi.api.client.map.MapViewport;

import com.moepus.serverwarashi.common.modules.performance.report.PerfReportData;
import com.moepus.serverwarashi.common.modules.weakload.WeakChunkSnapshot;
import com.moepus.serverwarashi.common.modules.weakload.WeakEntityType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static com.moepus.serverwarashi.testsupport.PerfReportFixtures.reportBuilder;

class HeatmapOffscreenIndicatorsTest {
    private static final MapViewport VIEWPORT = new MapViewport(
            new MapViewport.Origin(-80, -80), new MapViewport.Size(160, 160));
    private static final HeatmapOffscreenIndicators.RenderContext CONTEXT =
            context(new MapRenderContext.ScreenArea(
                    new MapRenderContext.ScreenPoint(0, 0),
                    new MapRenderContext.ScreenSize(400, 240)));

    @Test
    void ignoresBelowRedAndVisibleWarnings() {
        var indicators = planWeak(warning(20, 0, 349), warning(0, 0, 2_000));

        assertTrue(indicators.isEmpty());
    }

    @Test
    void placesRightSideDangerAtViewportEdge() {
        var indicator = planWeak(warning(20, 0, 500)).getFirst();

        assertEquals(380, indicator.x());
        assertEquals(120, indicator.y());
        assertEquals(0.0F, indicator.angle(), 0.0001F);
    }

    @Test
    void placesIndicatorsAtTheEdgeOfAMapSubArea() {
        var subArea = new MapRenderContext.ScreenArea(
                new MapRenderContext.ScreenPoint(40, 20),
                new MapRenderContext.ScreenSize(200, 100));

        var indicators = HeatmapOffscreenIndicators.plan(
                null, snapshot(warning(20, 0, 500)), context(subArea));

        assertEquals(220, indicators.getFirst().x());
        assertEquals(70, indicators.getFirst().y());
    }

    @Test
    void keepsOnlyMostDangerousWarningInEachDirection() {
        var indicators = planWeak(
                warning(20, 0, 500), warning(40, 1, 2_000), warning(-20, 0, 700));

        assertEquals(2, indicators.size());
        assertTrue(indicators.stream().anyMatch(indicator -> indicator.entityCount() == 2_000));
        assertTrue(indicators.stream().anyMatch(indicator -> indicator.entityCount() == 700));
    }

    @Test
    void usesAbsoluteColorScaleFromRedToPurple() {
        float red = planWeak(warning(20, 0, 350)).getFirst().color();
        float between = planWeak(warning(20, 0, 425)).getFirst().color();
        float purple = planWeak(warning(20, 0, 500)).getFirst().color();

        assertEquals(0.8F, red, 0.0001F);
        assertEquals(0.9F, between, 0.0001F);
        assertEquals(1.0F, purple, 0.0001F);
    }

    @Test
    void clampsColorAtPurpleAsDangerRises() {
        float purple = planWeak(warning(20, 0, 500)).getFirst().color();
        float extreme = planWeak(warning(20, 0, 4_000)).getFirst().color();

        assertEquals(1.0F, purple, 0.0001F);
        assertEquals(1.0F, extreme, 0.0001F);
    }

    @Test
    void mapsNormalizedColorAcrossTheReservedPalette() {
        assertEquals(0xC840F5FF, HeatmapOffscreenIndicators.indicatorArgb(0.0F));
        assertEquals(0xCC40DC78, HeatmapOffscreenIndicators.indicatorArgb(0.2F));
        assertEquals(0xD0FFD640, HeatmapOffscreenIndicators.indicatorArgb(0.4F));
        assertEquals(0xD4FF9120, HeatmapOffscreenIndicators.indicatorArgb(0.6F));
        assertEquals(0xD8F44336, HeatmapOffscreenIndicators.indicatorArgb(0.8F));
        assertEquals(0xE0CC46FF, HeatmapOffscreenIndicators.indicatorArgb(1.0F));
    }

    @Test
    void includesRedPerformanceChunksWithoutWeakWarnings() {
        var indicators = HeatmapOffscreenIndicators.plan(
                report(chunk(20, 0, metric(2.0D, 2.0D))), null, CONTEXT);

        assertEquals(1, indicators.size());
        assertEquals(0.8F, indicators.getFirst().color(), 0.0001F);
    }

    @Test
    void includesPurplePerformanceChunksWithoutWeakWarnings() {
        var indicators = HeatmapOffscreenIndicators.plan(
                report(chunk(20, 0, metric(4.0D, 4.0D))), null, CONTEXT);

        assertEquals(1, indicators.size());
        assertEquals(0, indicators.getFirst().entityCount());
    }

    @Test
    void includesChunksWhoseCombinedRiskReachesPurple() {
        var indicators = HeatmapOffscreenIndicators.plan(
                report(chunk(20, 0, metric(2.0D, 2.0D))),
                snapshot(warning(20, 0, 350)), CONTEXT);

        assertEquals(1, indicators.size());
        assertEquals(350, indicators.getFirst().entityCount());
    }

    @Test
    void ignoresPerformanceChunksBelowRed() {
        var indicators = HeatmapOffscreenIndicators.plan(
                report(chunk(20, 0, metric(1.0D, 6.0D))), null, CONTEXT);

        assertTrue(indicators.isEmpty());
    }

    @Test
    void dangerousModelAcceptsAggregatedWeakChunksBeyondWireBatchLimit() {
        List<WeakChunkSnapshot.ChunkEntry> chunks = java.util.stream.IntStream.range(0, 9)
                .mapToObj(index -> warning(index, 0, 500))
                .toList();

        var result = DangerousChunkModel.build(null,
                new DangerousChunkModel.WeakChunks("minecraft:overworld", chunks),
                "minecraft:overworld");

        assertEquals(9, result.size());
    }

    private static List<HeatmapOffscreenIndicators.Indicator> planWeak(
            WeakChunkSnapshot.ChunkEntry... chunks) {
        return HeatmapOffscreenIndicators.plan(null, snapshot(chunks), CONTEXT);
    }

    private static HeatmapOffscreenIndicators.RenderContext context(
            MapRenderContext.ScreenArea screen) {
        var visible = new MapRenderContext.VisibleArea(VIEWPORT, screen);
        return new HeatmapOffscreenIndicators.RenderContext(
                "minecraft:overworld", visible, new MapRenderContext.WorldPoint(8.0D, 8.0D));
    }

    private static WeakChunkSnapshot snapshot(WeakChunkSnapshot.ChunkEntry... chunks) {
        return new WeakChunkSnapshot("minecraft:overworld", 1L, List.of(chunks));
    }

    private static PerfReportData report(PerfReportData.ChunkEntry... chunks) {
        return reportBuilder()
                .serverTiming(10.0D, 1_000L, 123L)
                .sampleWindow(20L, 10L, 1_000)
                .chunks(List.of(chunks))
                .build();
    }

    private static PerfReportData.ChunkEntry chunk(int x, int z, PerfReportData.Metric metric) {
        return new PerfReportData.ChunkEntry("minecraft:overworld", x, z,
                0, 0, List.of(), List.of(), metric);
    }

    private static PerfReportData.Metric metric(double mspt, double max) {
        return new PerfReportData.Metric(mspt, max, 1L);
    }

    private static WeakChunkSnapshot.ChunkEntry warning(int chunkX, int chunkZ, int entityCount) {
        return new WeakChunkSnapshot.ChunkEntry(
                chunkX, chunkZ, entityCount, Map.of(
                new WeakEntityType(WeakEntityType.Kind.ITEM, "minecraft:cobblestone"), entityCount));
    }
}
