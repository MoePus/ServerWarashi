package com.moepus.serverwarashi.common.network;

import com.moepus.serverwarashi.common.modules.performance.report.ObjectPerformanceScale;
import com.moepus.serverwarashi.common.modules.performance.report.PerfWorldHotspotIndex;

import java.util.Comparator;
import java.util.List;

final class PerfWorldHotspotSelector {
    static final int DRAW_RADIUS = 96;
    static final int PREFETCH_RADIUS = 128;
    private static final double DRAW_RADIUS_SQUARED = DRAW_RADIUS * DRAW_RADIUS;
    private static final double PREFETCH_RADIUS_SQUARED = PREFETCH_RADIUS * PREFETCH_RADIUS;
    private static final Comparator<PerfWorldHotspotIndex.Entry> STABLE_ORDER = Comparator
            .comparingInt((PerfWorldHotspotIndex.Entry entry) -> entry.key().kind().ordinal())
            .thenComparingInt(entry -> entry.key().entityId())
            .thenComparingLong(entry -> entry.key().blockPosition());

    private PerfWorldHotspotSelector() {
    }

    static List<PerfWorldHotspotIndex.Entry> select(PerfWorldHotspotIndex index,
                                                     View view,
                                                     int limit) {
        if (index == null || index.tickCount() == 0L || limit <= 0) {
            return List.of();
        }
        Comparator<PerfWorldHotspotIndex.Entry> priority = Comparator
                .comparingInt((PerfWorldHotspotIndex.Entry entry) ->
                        distanceSquared(entry, view) <= DRAW_RADIUS_SQUARED ? 0 : 1)
                .thenComparing(Comparator.comparingDouble(
                        (PerfWorldHotspotIndex.Entry entry) -> effectiveSamples(entry, index))
                        .reversed())
                .thenComparing(Comparator.comparingLong(
                        PerfWorldHotspotIndex.Entry::samples).reversed())
                .thenComparing(STABLE_ORDER);
        return index.entries().stream()
                .filter(entry -> view.dimension().equals(entry.dimension()))
                .filter(entry -> distanceSquared(entry, view) <= PREFETCH_RADIUS_SQUARED)
                .sorted(priority)
                .limit(limit)
                .toList();
    }

    private static double effectiveSamples(PerfWorldHotspotIndex.Entry entry,
                                           PerfWorldHotspotIndex index) {
        return ObjectPerformanceScale.effectiveSamples(
                entry.samples(), entry.maxSamplesQ8(), index.tickCount());
    }

    private static double distanceSquared(PerfWorldHotspotIndex.Entry entry, View view) {
        double dx = entry.x() + 0.5D - view.centerX();
        double dz = entry.z() + 0.5D - view.centerZ();
        return dx * dx + dz * dz;
    }

    record View(String dimension, double centerX, double centerZ) {
    }
}
