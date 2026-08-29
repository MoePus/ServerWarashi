package com.moepus.serverwarashi.client.world;

import com.moepus.serverwarashi.common.modules.performance.report.ObjectPerformanceScale;
import com.moepus.serverwarashi.common.modules.performance.report.PerfReportData;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class WorldEntityFlameClusters {
    static final double MERGE_GAP = 0.65D;
    static final double MAX_HORIZONTAL_SPAN = 4.0D;
    static final double MAX_VERTICAL_SPAN = 3.0D;
    static final int MAX_CLUSTER_INPUTS = 4_096;
    private static final double VERTICAL_GAP = 0.75D;
    private static final double CELL_SIZE = 2.0D;

    private WorldEntityFlameClusters() {
    }

    static List<Marker> cluster(List<Input> inputs, long tickCount) {
        if (inputs.isEmpty()) {
            return List.of();
        }
        int limit = Math.min(inputs.size(), MAX_CLUSTER_INPUTS);
        List<Input> ordered = new ArrayList<>(inputs.subList(0, limit));
        ordered.sort(Comparator.comparingInt(Input::stableId));
        return new ClusterBuilder(ordered).collect().stream()
                .map(group -> marker(group, tickCount)).toList();
    }

    static Marker single(Input input, long tickCount) {
        double mspt = positiveFinite(input.metric().mspt());
        double maximum = positiveFinite(input.metric().max());
        long samples = Math.max(0L, input.metric().samples());
        PerfReportData.Metric metric = new PerfReportData.Metric(
                mspt, Math.max(mspt, maximum), samples);
        var anchor = new Anchor(centerX(input.bounds()), input.bounds().maxY,
                centerZ(input.bounds()));
        var visual = new Visual(ObjectPerformanceScale.effectiveSamples(metric, tickCount),
                updatePhase(0x811C9DC5, input.stableId()) & 0x7FFF, 1);
        return new Marker(anchor, metric, visual);
    }

    private static Marker marker(List<Input> group, long tickCount) {
        group.sort(Comparator.comparingInt(Input::stableId));
        double totalMspt = 0.0D;
        double totalMax = 0.0D;
        long totalSamples = 0L;
        double totalWeight = 0.0D;
        double x = 0.0D;
        double z = 0.0D;
        double y = -Double.MAX_VALUE;
        int phase = 0x811C9DC5;
        for (Input input : group) {
            double mspt = positiveFinite(input.metric().mspt());
            double maximum = positiveFinite(input.metric().max());
            double weight = Math.max(mspt, 0.001D);
            totalMspt += mspt;
            totalMax += maximum;
            totalSamples = saturatedAdd(totalSamples, Math.max(0L, input.metric().samples()));
            totalWeight += weight;
            x += centerX(input.bounds()) * weight;
            z += centerZ(input.bounds()) * weight;
            y = Math.max(y, input.bounds().maxY);
            phase = updatePhase(phase, input.stableId());
        }
        PerfReportData.Metric metric = new PerfReportData.Metric(
                totalMspt, Math.max(totalMspt, totalMax), totalSamples);
        return new Marker(new Anchor(x / totalWeight, y, z / totalWeight), metric,
                new Visual(ObjectPerformanceScale.effectiveSamples(metric, tickCount),
                        phase & 0x7FFF, group.size()));
    }

    private static double positiveFinite(double value) {
        return Double.isFinite(value) && value > 0.0D ? value : 0.0D;
    }

    private static long saturatedAdd(long left, long right) {
        return Long.MAX_VALUE - left < right ? Long.MAX_VALUE : left + right;
    }

    private static int updatePhase(int phase, int stableId) {
        return (phase ^ stableId) * 0x01000193;
    }

    private static double centerX(AABB bounds) {
        return (bounds.minX + bounds.maxX) * 0.5D;
    }

    private static double centerZ(AABB bounds) {
        return (bounds.minZ + bounds.maxZ) * 0.5D;
    }

    private static int cell(double coordinate) {
        return (int) Math.floor(coordinate / CELL_SIZE);
    }

    record Input(AABB bounds, PerfReportData.Metric metric, int stableId) {
    }

    record Anchor(double x, double y, double z) {
    }

    record Visual(double effectiveSamples, int phase, int memberCount) {
    }

    record Marker(Anchor anchor, PerfReportData.Metric metric, Visual visual) {
        double x() {
            return anchor.x();
        }

        double y() {
            return anchor.y();
        }

        double z() {
            return anchor.z();
        }

        double effectiveSamples() {
            return visual.effectiveSamples();
        }

        int phase() {
            return visual.phase();
        }

        int memberCount() {
            return visual.memberCount();
        }
    }

    private static final class ClusterBuilder {
        private final List<Input> inputs;
        private final Long2ObjectMap<IntArrayList> cells = new Long2ObjectOpenHashMap<>();
        private final List<Cluster> clusters = new ArrayList<>();
        private final IntArrayList candidates = new IntArrayList();
        private final int[] visited;
        private int visitToken;

        private ClusterBuilder(List<Input> inputs) {
            this.inputs = inputs;
            this.visited = new int[inputs.size()];
        }

        private List<List<Input>> collect() {
            for (Input input : inputs) {
                int clusterId = nearestCluster(input);
                if (clusterId < 0) {
                    clusterId = clusters.size();
                    clusters.add(new Cluster(input));
                } else {
                    clusters.get(clusterId).add(input);
                }
                index(clusterId);
            }
            return clusters.stream().map(Cluster::members).toList();
        }

        private int nearestCluster(Input input) {
            candidates.clear();
            visitToken++;
            visitCells(input.bounds(), (cellX, cellY, cellZ) -> {
                IntArrayList occupants = cells.get(BlockPos.asLong(cellX, cellY, cellZ));
                if (occupants == null) {
                    return;
                }
                for (int clusterId : occupants) {
                    if (visited[clusterId] != visitToken) {
                        visited[clusterId] = visitToken;
                        candidates.add(clusterId);
                    }
                }
            });
            candidates.sort(null);
            int nearest = -1;
            double nearestDistance = Double.MAX_VALUE;
            for (int clusterId : candidates) {
                Cluster cluster = clusters.get(clusterId);
                double distance = cluster.distanceSquared(input);
                if (cluster.fits(input) && distance <= MERGE_GAP * MERGE_GAP
                        && distance < nearestDistance) {
                    nearest = clusterId;
                    nearestDistance = distance;
                }
            }
            return nearest;
        }

        private void index(int clusterId) {
            Cluster cluster = clusters.get(clusterId);
            visitCells(cluster.bounds(), (cellX, cellY, cellZ) -> {
                long key = BlockPos.asLong(cellX, cellY, cellZ);
                if (cluster.indexedCells.add(key)) {
                    cells.computeIfAbsent(key, ignored -> new IntArrayList()).add(clusterId);
                }
            });
        }

        private static void visitCells(AABB bounds, CellVisitor visitor) {
            int minX = cell(bounds.minX - MERGE_GAP);
            int maxX = cell(bounds.maxX + MERGE_GAP);
            int minY = cell(bounds.minY - VERTICAL_GAP);
            int maxY = cell(bounds.maxY + VERTICAL_GAP);
            int minZ = cell(bounds.minZ - MERGE_GAP);
            int maxZ = cell(bounds.maxZ + MERGE_GAP);
            for (int x = minX; x <= maxX; x++) {
                for (int y = minY; y <= maxY; y++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        visitor.visit(x, y, z);
                    }
                }
            }
        }
    }

    private static final class Cluster {
        private final List<Input> members = new ArrayList<>();
        private final LongSet indexedCells = new LongOpenHashSet();
        private AABB bounds;
        private double minX;
        private double maxX;
        private double minY;
        private double maxY;
        private double minZ;
        private double maxZ;

        private Cluster(Input input) {
            bounds = input.bounds();
            minX = maxX = centerX(input.bounds());
            minY = maxY = centerY(input.bounds());
            minZ = maxZ = centerZ(input.bounds());
            members.add(input);
        }

        private List<Input> members() {
            return members;
        }

        private AABB bounds() {
            return bounds;
        }

        private boolean fits(Input input) {
            double x = centerX(input.bounds());
            double y = centerY(input.bounds());
            double z = centerZ(input.bounds());
            double spanX = Math.max(maxX, x) - Math.min(minX, x);
            double spanY = Math.max(maxY, y) - Math.min(minY, y);
            double spanZ = Math.max(maxZ, z) - Math.min(minZ, z);
            return spanX <= MAX_HORIZONTAL_SPAN && spanZ <= MAX_HORIZONTAL_SPAN
                    && spanY <= MAX_VERTICAL_SPAN;
        }

        private double distanceSquared(Input input) {
            AABB other = input.bounds();
            double vertical = gap(bounds.minY, bounds.maxY, other.minY, other.maxY);
            if (vertical > VERTICAL_GAP) {
                return Double.MAX_VALUE;
            }
            double dx = gap(bounds.minX, bounds.maxX, other.minX, other.maxX);
            double dz = gap(bounds.minZ, bounds.maxZ, other.minZ, other.maxZ);
            return dx * dx + vertical * vertical + dz * dz;
        }

        private void add(Input input) {
            AABB other = input.bounds();
            bounds = new AABB(Math.min(bounds.minX, other.minX), Math.min(bounds.minY, other.minY),
                    Math.min(bounds.minZ, other.minZ), Math.max(bounds.maxX, other.maxX),
                    Math.max(bounds.maxY, other.maxY), Math.max(bounds.maxZ, other.maxZ));
            double x = centerX(other);
            double y = centerY(other);
            double z = centerZ(other);
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
            minZ = Math.min(minZ, z);
            maxZ = Math.max(maxZ, z);
            members.add(input);
        }
    }

    private static double centerY(AABB bounds) {
        return (bounds.minY + bounds.maxY) * 0.5D;
    }

    private static double gap(double firstMin, double firstMax, double secondMin, double secondMax) {
        return Math.max(0.0D, Math.max(firstMin - secondMax, secondMin - firstMax));
    }

    @FunctionalInterface
    private interface CellVisitor {
        void visit(int x, int y, int z);
    }
}
