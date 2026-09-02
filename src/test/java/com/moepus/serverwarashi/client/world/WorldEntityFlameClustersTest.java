package com.moepus.serverwarashi.client.world;

import com.moepus.serverwarashi.common.modules.performance.report.PerfReportData;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeout;

class WorldEntityFlameClustersTest {
    @Test
    void mergesNearbyEntitiesAcrossSpatialHashCellBoundaries() {
        List<WorldEntityFlameClusters.Marker> markers = WorldEntityFlameClusters.cluster(List.of(
                input(1, new Point(1.8D, 0.0D), metric(0.4D, 0.8D, 1L)),
                input(2, new Point(2.6D, 0.0D), metric(0.6D, 1.2D, 1L)),
                input(3, new Point(8.0D, 0.0D), metric(0.5D, 0.5D, 1L))), 1L);

        List<Integer> sizes = markers.stream().map(WorldEntityFlameClusters.Marker::memberCount)
                .sorted().toList();
        WorldEntityFlameClusters.Marker merged = markers.stream()
                .filter(marker -> marker.memberCount() == 2).findFirst().orElseThrow();

        assertEquals(List.of(1, 2), sizes);
        assertEquals(1.0D, merged.metric().mspt(), 0.000_001D);
        assertEquals(2.0D, merged.metric().max(), 0.000_001D);
        assertEquals(2L, merged.metric().samples());
        assertEquals(2.0D, merged.flameStrength(), 0.000_001D);
        assertEquals(2.28D, merged.x(), 0.000_001D);
    }

    @Test
    void limitsChainClustersToMaximumHorizontalSpan() {
        List<WorldEntityFlameClusters.Input> inputs = new ArrayList<>();
        for (int id = 0; id < 6; id++) {
            inputs.add(input(id, new Point(id, 0.0D), metric(1.0D, 1.0D, 1L)));
        }

        List<Integer> sizes = WorldEntityFlameClusters.cluster(inputs, 1L).stream()
                .map(WorldEntityFlameClusters.Marker::memberCount)
                .sorted(Comparator.reverseOrder()).toList();

        assertEquals(List.of(5, 1), sizes);
    }

    @Test
    void doesNotMergeVerticallySeparatedEntities() {
        var lower = input(1, new AABB(-0.3D, 64.0D, -0.3D, 0.3D, 65.0D, 0.3D));
        var upper = input(2, new AABB(-0.3D, 66.0D, -0.3D, 0.3D, 67.0D, 0.3D));

        assertEquals(2, WorldEntityFlameClusters.cluster(List.of(lower, upper), 1L).size());
    }

    @Test
    void singleMarkerMatchesOneElementCluster() {
        var input = input(7, new Point(2.0D, 3.0D), metric(0.4D, 1.2D, 3L));
        var clustered = WorldEntityFlameClusters.cluster(List.of(input), 2L).get(0);
        var single = WorldEntityFlameClusters.single(input, 2L);

        assertEquals(clustered.metric(), single.metric());
        assertEquals(clustered.visual(), single.visual());
        assertEquals(clustered.x(), single.x(), 0.000_001D);
        assertEquals(clustered.y(), single.y(), 0.000_001D);
        assertEquals(clustered.z(), single.z(), 0.000_001D);
    }

    @Test
    void combinesRatesFromMembersWithDifferentMeasurementWindows() {
        var recent = new WorldEntityFlameClusters.Input(
                bounds(new Point(0.0D, 0.0D)), metric(1.0D, 1.0D, 10L), 10L, 1);
        var old = new WorldEntityFlameClusters.Input(
                bounds(new Point(0.2D, 0.0D)), metric(1.0D, 1.0D, 100L), 100L, 2);

        WorldEntityFlameClusters.Marker marker =
                WorldEntityFlameClusters.cluster(List.of(recent, old), 100L).get(0);

        assertEquals(2.0D, marker.metric().mspt(), 0.000_001D);
        assertEquals(2.0D, marker.flameStrength(), 0.000_001D);
    }

    @Test
    void historicalMaximumDoesNotPinFlameStrength() {
        var input = new WorldEntityFlameClusters.Input(
                bounds(new Point(0.0D, 0.0D)), metric(0.02D, 0.07D, 200L), 100L, 1);

        WorldEntityFlameClusters.Marker marker = WorldEntityFlameClusters.single(input, 100L);

        assertEquals(2.0D, marker.flameStrength(), 0.000_001D);
    }

    @Test
    void combinesPreviousAndCurrentAnchorsForTickInterpolation() {
        var first = new WorldEntityFlameClusters.Input(
                bounds(new Point(0.0D, 0.0D)), metric(1.0D, 1.0D, 1L),
                1L, 1, -1.0D, 0.0D, 0.5D);
        var second = new WorldEntityFlameClusters.Input(
                bounds(new Point(0.5D, 0.0D)), metric(1.0D, 1.0D, 1L),
                1L, 2, -1.5D, -0.5D, 0.5D);

        WorldEntityFlameClusters.Marker marker =
                WorldEntityFlameClusters.cluster(List.of(first, second), 1L).get(0);

        assertEquals(0.25D, marker.x(), 0.000_001D);
        assertEquals(-1.0D, marker.previousX(), 0.000_001D);
        assertEquals(0.5D, marker.previousZ(), 0.000_001D);
        assertEquals(65.8D, marker.previousY(), 0.000_001D);
    }

    @Test
    void clustersTheMaximumCoincidentInputBudgetWithoutPairMaterialization() {
        List<WorldEntityFlameClusters.Input> inputs = new ArrayList<>();
        for (int id = 0; id < WorldEntityFlameClusters.MAX_CLUSTER_INPUTS + 1; id++) {
            inputs.add(input(id, new Point(0.0D, 0.0D), metric(1.0D, 1.0D, 1L)));
        }

        List<WorldEntityFlameClusters.Marker> markers = assertTimeout(Duration.ofSeconds(2),
                () -> WorldEntityFlameClusters.cluster(inputs, 1L));

        assertEquals(1, markers.size());
        assertEquals(WorldEntityFlameClusters.MAX_CLUSTER_INPUTS, markers.get(0).memberCount());
    }

    private static WorldEntityFlameClusters.Input input(
            int id, Point point, PerfReportData.Metric metric) {
        return new WorldEntityFlameClusters.Input(bounds(point), metric, id);
    }

    private static AABB bounds(Point point) {
        return new AABB(point.x() - 0.3D, 64.0D, point.z() - 0.3D,
                point.x() + 0.3D, 65.8D, point.z() + 0.3D);
    }

    private static WorldEntityFlameClusters.Input input(int id, AABB bounds) {
        return new WorldEntityFlameClusters.Input(bounds, metric(1.0D, 1.0D, 1L), id);
    }

    private static PerfReportData.Metric metric(double mspt, double max, long samples) {
        return new PerfReportData.Metric(mspt, max, samples);
    }

    private record Point(double x, double z) {
    }
}
