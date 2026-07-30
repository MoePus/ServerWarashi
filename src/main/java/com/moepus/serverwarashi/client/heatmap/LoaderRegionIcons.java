package com.moepus.serverwarashi.client.heatmap;

import com.moepus.serverwarashi.common.modules.performance.report.PerfReportData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class LoaderRegionIcons {
    private LoaderRegionIcons() {
    }

    static List<Anchor> anchors(List<PerfReportData.LoaderRegion> regions) {
        if (regions.isEmpty()) {
            return List.of();
        }
        DisjointSet components = new DisjointSet(regions.size());
        for (int left = 0; left < regions.size(); left++) {
            for (int right = left + 1; right < regions.size(); right++) {
                if (connected(regions.get(left), regions.get(right))) {
                    components.union(left, right);
                }
            }
        }
        Map<Integer, List<PerfReportData.LoaderRegion>> grouped = new LinkedHashMap<>();
        for (int index = 0; index < regions.size(); index++) {
            grouped.computeIfAbsent(components.find(index), ignored -> new ArrayList<>())
                    .add(regions.get(index));
        }
        return grouped.values().stream().map(LoaderRegionIcons::anchor).toList();
    }

    private static boolean connected(PerfReportData.LoaderRegion left,
                                     PerfReportData.LoaderRegion right) {
        long leftMaxX = maxX(left);
        long rightMaxX = maxX(right);
        long leftMaxZ = maxZ(left);
        long rightMaxZ = maxZ(right);
        boolean overlapsX = overlaps(left.minChunkX(), leftMaxX, right.minChunkX(), rightMaxX);
        boolean overlapsZ = overlaps(left.minChunkZ(), leftMaxZ, right.minChunkZ(), rightMaxZ);
        return overlapsX && withinOne(left.minChunkZ(), leftMaxZ, right.minChunkZ(), rightMaxZ)
                || overlapsZ && withinOne(left.minChunkX(), leftMaxX, right.minChunkX(), rightMaxX);
    }

    private static boolean overlaps(long leftMin, long leftMax, long rightMin, long rightMax) {
        return leftMin <= rightMax && rightMin <= leftMax;
    }

    private static boolean withinOne(long leftMin, long leftMax, long rightMin, long rightMax) {
        return leftMin <= rightMax + 1L && rightMin <= leftMax + 1L;
    }

    private static Anchor anchor(List<PerfReportData.LoaderRegion> regions) {
        double totalArea = 0.0D;
        double weightedX = 0.0D;
        double weightedZ = 0.0D;
        for (PerfReportData.LoaderRegion region : regions) {
            double area = (double) region.width() * region.height();
            totalArea += area;
            weightedX += area * (region.minChunkX() + (region.width() - 1) / 2.0D);
            weightedZ += area * (region.minChunkZ() + (region.height() - 1) / 2.0D);
        }
        double centerX = weightedX / totalArea;
        double centerZ = weightedZ / totalArea;
        Anchor best = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (PerfReportData.LoaderRegion region : regions) {
            Anchor candidate = closestInside(region, centerX, centerZ);
            double distance = squaredDistance(candidate, centerX, centerZ);
            if (best == null || distance < bestDistance
                    || distance == bestDistance && candidate.compareTo(best) < 0) {
                best = candidate;
                bestDistance = distance;
            }
        }
        return best;
    }

    private static Anchor closestInside(PerfReportData.LoaderRegion region,
                                        double centerX,
                                        double centerZ) {
        return new Anchor(nearest(centerX, region.minChunkX(), maxX(region)),
                nearest(centerZ, region.minChunkZ(), maxZ(region)));
    }

    private static int nearest(double value, long min, long max) {
        double clamped = Math.max(min, Math.min(max, value));
        long lower = (long) Math.floor(clamped);
        long upper = (long) Math.ceil(clamped);
        return (int) (clamped - lower <= upper - clamped ? lower : upper);
    }

    private static double squaredDistance(Anchor anchor, double x, double z) {
        double dx = anchor.chunkX - x;
        double dz = anchor.chunkZ - z;
        return dx * dx + dz * dz;
    }

    private static long maxX(PerfReportData.LoaderRegion region) {
        return (long) region.minChunkX() + region.width() - 1L;
    }

    private static long maxZ(PerfReportData.LoaderRegion region) {
        return (long) region.minChunkZ() + region.height() - 1L;
    }

    record Anchor(int chunkX, int chunkZ) implements Comparable<Anchor> {
        @Override
        public int compareTo(Anchor other) {
            int xOrder = Integer.compare(chunkX, other.chunkX);
            return xOrder != 0 ? xOrder : Integer.compare(chunkZ, other.chunkZ);
        }
    }

    private static final class DisjointSet {
        private final int[] parent;

        private DisjointSet(int size) {
            parent = new int[size];
            for (int index = 0; index < size; index++) {
                parent[index] = index;
            }
        }

        int find(int value) {
            int root = value;
            while (parent[root] != root) {
                root = parent[root];
            }
            while (parent[value] != value) {
                int next = parent[value];
                parent[value] = root;
                value = next;
            }
            return root;
        }

        void union(int left, int right) {
            int leftRoot = find(left);
            int rightRoot = find(right);
            if (leftRoot != rightRoot) {
                parent[rightRoot] = leftRoot;
            }
        }
    }
}
