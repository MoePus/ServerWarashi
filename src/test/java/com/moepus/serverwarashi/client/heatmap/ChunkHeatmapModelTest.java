package com.moepus.serverwarashi.client.heatmap;

import com.moepus.serverwarashi.common.modules.performance.report.PerfReportData;
import com.moepus.serverwarashi.common.modules.performance.sampling.TickDetailKind;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChunkHeatmapModelTest {
    @Test
    void skipsOnlyWhenBothMetricsAreBelowThreshold() {
        assertFalse(ChunkHeatmapModel.shouldRender(metric(0.499D, 2.999D)));
        assertTrue(ChunkHeatmapModel.shouldRender(metric(0.5D, 0.0D)));
        assertTrue(ChunkHeatmapModel.shouldRender(metric(0.0D, 3.0D)));
    }

    @Test
    void mapsPowerOfTwoSeverityToColorStops() {
        assertEquals(0.65D, ChunkHeatmapModel.severity(metric(0.5D, 0.0D)), 0.0D);
        assertEquals(0.35D, ChunkHeatmapModel.severity(metric(0.0D, 3.0D)), 0.0D);
        assertEquals(0x60FFD640, ChunkHeatmapModel.color(metric(0.5D, 3.0D)));
        assertEquals(0x70FF9120, ChunkHeatmapModel.color(metric(1.0D, 6.0D)));
        assertEquals(0x88F44336, ChunkHeatmapModel.color(metric(2.0D, 12.0D)));
        assertEquals(0xA0BE2DDC, ChunkHeatmapModel.color(metric(4.0D, 24.0D)));
        assertEquals(0xA0BE2DDC, ChunkHeatmapModel.color(metric(40.0D, 240.0D)));
    }

    @Test
    void mapsLoaderLowCostRangeFromGreenToCyan() {
        assertEquals(0x2440DC78, ChunkHeatmapModel.loaderColor(null));
        assertEquals(0x283ADC9D, ChunkHeatmapModel.loaderColor(metric(0.25D, 0.0D)));
        assertEquals(0x2C34DCC3, ChunkHeatmapModel.loaderColor(metric(0.5D, 0.0D)));
    }

    @Test
    void filtersByDimensionAndCoverageAndIndexesHoverMetric() {
        PerfReportData.Metric visibleMetric = metric(1.0D, 4.0D);
        PerfReportData report = report(List.of(
                chunk("minecraft:overworld", 1, 2, visibleMetric),
                chunk("minecraft:the_nether", 1, 2, metric(2.0D, 8.0D)),
                chunk("minecraft:overworld", 50, 50, metric(2.0D, 8.0D)),
                chunk("minecraft:overworld", 2, 2, metric(0.1D, 1.0D))));

        ChunkHeatmapModel.Plan plan = ChunkHeatmapModel.build(report, "minecraft:overworld",
                new HeatmapViewport(16, 32, 48, 32));

        assertEquals(1, plan.quads().size());
        assertEquals(4, plan.vertexCount());
        assertEquals(visibleMetric, plan.metricAt(1, 2));
        assertNull(plan.metricAt(2, 2));
    }

    @Test
    void carriesTopEntitiesAndBlockEntitiesIntoHoverInfo() {
        var cow = new PerfReportData.ObjectCount("example.Cow", "minecraft:cow",
                TickDetailKind.ENTITY, 12, 1, 64, 2);
        var furnace = new PerfReportData.ObjectCount("example.Furnace", "minecraft:furnace",
                TickDetailKind.BLOCK, 4, 3, 65, 4);
        var chunk = new PerfReportData.ChunkEntry("minecraft:overworld", 1, 2, 12, 4,
                List.of(cow), List.of(furnace), metric(1.0D, 4.0D));

        var hover = ChunkHeatmapModel.build(report(List.of(chunk)), "minecraft:overworld",
                new HeatmapViewport(16, 32, 16, 16)).hoverAt(1, 2);

        assertEquals(List.of(cow), hover.topEntities());
        assertEquals(List.of(furnace), hover.topBlockEntities());
    }

    @Test
    void producesAtMostFourVerticesPerReportedChunk() {
        assertEquals(0, ChunkHeatmapModel.build(null, "minecraft:overworld",
                new HeatmapViewport(0, 0, 16, 16)).vertexCount());

        List<PerfReportData.ChunkEntry> chunks = new ArrayList<>();
        for (int index = 0; index < 1_024; index++) {
            chunks.add(chunk("minecraft:overworld", index % 32, index / 32, metric(1.0D, 3.0D)));
        }
        var plan = ChunkHeatmapModel.build(report(chunks), "minecraft:overworld",
                new HeatmapViewport(0, 0, 32 * 16, 32 * 16));

        assertEquals(1_024, plan.quads().size());
        assertEquals(4_096, plan.vertexCount());
    }

    @Test
    void rendersLowCostLoaderChunkInGreenAndProvidesItsSource() {
        var loader = loader("loader-1", "test:chunk_loader",
                new PerfReportData.LoaderRegion(2, 3, 1, 1));
        var plan = ChunkHeatmapModel.build(report(List.of(), List.of(loader)), "minecraft:overworld",
                new HeatmapViewport(0, 0, 64, 64));

        assertEquals(List.of(new ChunkHeatmapModel.Quad(2, 3, 0x2440DC78)), plan.quads());
        assertNull(plan.metricAt(2, 3));
        assertEquals(List.of(new ChunkHeatmapModel.LoaderSource("loader-1", "test:chunk_loader")),
                plan.hoverAt(2, 3).loaderSources());
        assertEquals(List.of(new ChunkHeatmapModel.LoaderIcon(
                "loader-1", "test:chunk_loader", 40, 64, 56)), plan.icons());
    }

    @Test
    void clipsLoaderIconsToDimensionAndCoverage() {
        var region = new PerfReportData.LoaderRegion(0, 0, 1, 1);
        var visible = loaderAt(new LoaderFixture("loader-1", "Visible", "minecraft:overworld",
                new PerfReportData.LoaderPosition(8, 64, 8)), region);
        var outside = loaderAt(new LoaderFixture("loader-2", "Outside", "minecraft:overworld",
                new PerfReportData.LoaderPosition(80, 64, 80)), region);
        var nether = loaderAt(new LoaderFixture("loader-3", "Nether", "minecraft:the_nether",
                new PerfReportData.LoaderPosition(8, 64, 8)), region);

        var plan = ChunkHeatmapModel.build(report(List.of(), List.of(visible, outside, nether)),
                "minecraft:overworld", new HeatmapViewport(0, 0, 64, 64));

        assertEquals(List.of(new ChunkHeatmapModel.LoaderIcon(
                "loader-1", "Visible", 8, 64, 8)), plan.icons());
    }

    @Test
    void keepsIconScreenSizeUntilMapIsVerySmall() {
        assertEquals(7.0F, ChunkHeatmapRenderer.iconHalfWorld(1.0F));
        assertEquals(14.0F, ChunkHeatmapRenderer.iconHalfWorld(0.5F));
        assertEquals(28.0F, ChunkHeatmapRenderer.iconHalfWorld(0.25F));
        assertEquals(32.0F, ChunkHeatmapRenderer.iconHalfWorld(0.1F));
    }

    @Test
    void keepsHeatColorWhenExpensiveChunkIsAlsoLoaderOwned() {
        PerfReportData.Metric expensive = metric(1.0D, 0.0D);
        var loader = loader("loader-1", "test:chunk_loader",
                new PerfReportData.LoaderRegion(2, 3, 1, 1));
        var plan = ChunkHeatmapModel.build(
                report(List.of(chunk("minecraft:overworld", 2, 3, expensive)), List.of(loader)),
                "minecraft:overworld", new HeatmapViewport(0, 0, 64, 64));

        assertEquals(ChunkHeatmapModel.color(expensive), plan.quads().getFirst().argb());
        assertEquals(expensive, plan.hoverAt(2, 3).metric());
    }

    @Test
    void keepsSameNamedLoadersDistinctBySourceId() {
        var region = new PerfReportData.LoaderRegion(2, 3, 1, 1);
        var plan = ChunkHeatmapModel.build(report(List.of(), List.of(
                        loader("loader-1", "test:chunk_loader", region),
                        loader("loader-2", "test:chunk_loader", region))),
                "minecraft:overworld", new HeatmapViewport(0, 0, 64, 64));

        assertEquals(List.of("loader-1", "loader-2"), plan.hoverAt(2, 3).loaderSources().stream()
                .map(ChunkHeatmapModel.LoaderSource::id).toList());
    }

    @Test
    void expandsOnlyVisiblePartOfLoaderRegion() {
        var loader = loader("loader-1", "test:chunk_loader",
                new PerfReportData.LoaderRegion(-3, -3, 7, 7));
        var plan = ChunkHeatmapModel.build(report(List.of(), List.of(loader)), "minecraft:overworld",
                new HeatmapViewport(0, 0, 32, 32));

        assertEquals(4, plan.quads().size());
        assertEquals("test:chunk_loader", plan.hoverAt(0, 0).loaderSources().getFirst().name());
        assertNull(plan.hoverAt(-1, 0));
    }

    @Test
    void boundsOverlappingLoaderAssociationsAndTooltipSources() {
        List<PerfReportData.LoaderSourceEntry> loaders = new ArrayList<>();
        for (int index = 0; index < 10; index++) {
            loaders.add(loader("loader-" + index, "Loader " + index,
                    new PerfReportData.LoaderRegion(0, 0, 100, 100)));
        }

        var plan = ChunkHeatmapModel.build(report(List.of(), loaders), "minecraft:overworld",
                new HeatmapViewport(0, 0, 100 * 16, 100 * 16));
        var hover = plan.hoverAt(0, 0);

        assertEquals(10_000, plan.quads().size());
        assertEquals(4, hover.loaderSources().size());
        assertTrue(hover.omittedLoaderSources() > 0);
        assertTrue(hover.incomplete());
    }

    @Test
    void expandsCoverageByOneViewportOnEverySide() {
        HeatmapViewport viewport = new HeatmapViewport(100, -50, 200, 80);
        HeatmapViewport coverage = viewport.expandedBySelf();

        assertEquals(new HeatmapViewport(-100, -130, 600, 240), coverage);
        assertTrue(coverage.contains(viewport));
        assertTrue(coverage.contains(new HeatmapViewport(300, -50, 100, 80)));
        assertFalse(coverage.contains(new HeatmapViewport(450, -50, 100, 80)));
    }

    private static PerfReportData.Metric metric(double mspt, double p98) {
        return new PerfReportData.Metric(mspt, p98, 1L);
    }

    private static PerfReportData.ChunkEntry chunk(String dimension, int x, int z,
                                                   PerfReportData.Metric metric) {
        return new PerfReportData.ChunkEntry(dimension, x, z, 0, 0, List.of(), List.of(), metric);
    }

    private static PerfReportData report(List<PerfReportData.ChunkEntry> chunks) {
        return report(chunks, List.of());
    }

    private static PerfReportData report(List<PerfReportData.ChunkEntry> chunks,
                                         List<PerfReportData.LoaderSourceEntry> loaderSources) {
        return new PerfReportData(10.0D, 1_000L, 123L, 20L, 10L, 1_000,
                0, 0, 0, 0, chunks, List.of(), List.of(), loaderSources);
    }

    private static PerfReportData.LoaderSourceEntry loader(
            String sourceId, String name, PerfReportData.LoaderRegion... regions) {
        return loaderAt(new LoaderFixture(sourceId, name, "minecraft:overworld",
                new PerfReportData.LoaderPosition(40, 64, 56)), regions);
    }

    private static PerfReportData.LoaderSourceEntry loaderAt(
            LoaderFixture fixture, PerfReportData.LoaderRegion... regions) {
        return new PerfReportData.LoaderSourceEntry(fixture.dimension, fixture.id, fixture.name,
                fixture.position, 0, List.of(regions));
    }

    private record LoaderFixture(String id, String name, String dimension,
                                 PerfReportData.LoaderPosition position) {
    }
}
