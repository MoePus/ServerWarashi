package com.moepus.serverwarashi.client.heatmap;

import com.moepus.serverwarashi.common.modules.performance.report.PerfReportData;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LoaderRegionIconsTest {
    @Test
    void placesRectangleAnchorAtDeterministicCenterChunk() {
        assertEquals(List.of(anchor(3, 3)), anchors(region(2, 3, 4, 2)));
    }

    @Test
    void placesConcaveAreaAnchorInsideTheAreaNearestItsCentroid() {
        assertEquals(List.of(anchor(0, 1)), anchors(
                region(0, 0, 1, 3),
                region(1, 2, 2, 1)));
    }

    @Test
    void mergesEdgeConnectedAndOverlappingRectangles() {
        assertEquals(1, anchors(
                region(0, 0, 2, 2),
                region(2, 1, 2, 1),
                region(3, 1, 2, 2)).size());
    }

    @Test
    void keepsDiagonalAndSeparatedAreasAsDistinctComponents() {
        assertEquals(List.of(anchor(0, 0), anchor(1, 1), anchor(4, 0)), anchors(
                region(0, 0, 1, 1),
                region(1, 1, 1, 1),
                region(4, 0, 1, 1)));
    }

    @Test
    void handlesNegativeCoordinatesAndBreaksCenterTiesTowardLowerCoordinates() {
        assertEquals(List.of(anchor(-3, -2)), anchors(region(-4, -2, 4, 2)));
    }

    private static List<LoaderRegionIcons.Anchor> anchors(
            PerfReportData.LoaderRegion... regions) {
        return LoaderRegionIcons.anchors(List.of(regions));
    }

    private static LoaderRegionIcons.Anchor anchor(int x, int z) {
        return new LoaderRegionIcons.Anchor(x, z);
    }

    private static PerfReportData.LoaderRegion region(int x, int z, int width, int height) {
        return new PerfReportData.LoaderRegion(x, z, width, height);
    }
}
