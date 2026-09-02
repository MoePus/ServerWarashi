package com.moepus.serverwarashi.client.world;

import net.minecraft.world.phys.Vec3;

import java.util.List;

final class WorldPerfMarkerBuckets {
    static final int BUCKET_WIDTH = 8;
    static final int BUCKET_COUNT = (int) (WorldOverlayMath.OBJECT_FADE_END / BUCKET_WIDTH);
    private static final int SPARK_BUCKET_COUNT =
            (int) (WorldOverlayMath.SPARK_FADE_END / BUCKET_WIDTH);
    private static final int LABEL_BUCKET_COUNT =
            (int) (WorldOverlayMath.LABEL_FADE_END / BUCKET_WIDTH);
    private static final double MAX_DISTANCE_SQUARED =
            WorldOverlayMath.OBJECT_FADE_END * WorldOverlayMath.OBJECT_FADE_END;

    private WorldPerfMarkerBuckets() {
    }

    static Batch order(List<WorldEntityFlameClusters.Marker> markers, Vec3 camera) {
        byte[] markerBuckets = new byte[markers.size()];
        int[] counts = new int[BUCKET_COUNT];
        int visibleCount = classify(markers, camera, markerBuckets, counts);
        WorldEntityFlameClusters.Marker[] ordered =
                new WorldEntityFlameClusters.Marker[visibleCount];
        int[] writeOffsets = writeOffsets(counts);
        for (int index = 0; index < markers.size(); index++) {
            int bucket = markerBuckets[index];
            if (bucket >= 0) {
                ordered[writeOffsets[bucket]++] = markers.get(index);
            }
        }
        int sparkBase = visibleCount - nearCount(counts, SPARK_BUCKET_COUNT);
        int labelBase = visibleCount - nearCount(counts, LABEL_BUCKET_COUNT);
        return new Batch(ordered, sparkBase, labelBase);
    }

    static int bucketForDistanceSquared(double distanceSquared) {
        if (!Double.isFinite(distanceSquared) || distanceSquared < 0.0D
                || distanceSquared > MAX_DISTANCE_SQUARED) {
            return -1;
        }
        if (distanceSquared == MAX_DISTANCE_SQUARED) {
            return BUCKET_COUNT - 1;
        }
        int low = 0;
        int high = BUCKET_COUNT;
        while (low < high) {
            int middle = (low + high) >>> 1;
            double boundary = (middle + 1) * BUCKET_WIDTH;
            if (distanceSquared < boundary * boundary) {
                high = middle;
            } else {
                low = middle + 1;
            }
        }
        return low;
    }

    private static int classify(List<WorldEntityFlameClusters.Marker> markers,
                                Vec3 camera,
                                byte[] markerBuckets,
                                int[] counts) {
        int visibleCount = 0;
        for (int index = 0; index < markers.size(); index++) {
            WorldEntityFlameClusters.Marker marker = markers.get(index);
            int bucket = bucketForDistanceSquared(distanceSquared(marker, camera));
            markerBuckets[index] = (byte) bucket;
            if (bucket >= 0) {
                counts[bucket]++;
                visibleCount++;
            }
        }
        return visibleCount;
    }

    private static int[] writeOffsets(int[] counts) {
        int[] offsets = new int[BUCKET_COUNT];
        int offset = 0;
        for (int bucket = BUCKET_COUNT - 1; bucket >= 0; bucket--) {
            offsets[bucket] = offset;
            offset += counts[bucket];
        }
        return offsets;
    }

    private static int nearCount(int[] counts, int bucketCount) {
        int count = 0;
        for (int bucket = 0; bucket < bucketCount; bucket++) {
            count += counts[bucket];
        }
        return count;
    }

    private static double distanceSquared(WorldEntityFlameClusters.Marker marker, Vec3 camera) {
        double dx = marker.x() - camera.x;
        double dy = marker.y() - camera.y;
        double dz = marker.z() - camera.z;
        return dx * dx + dy * dy + dz * dz;
    }

    record Batch(WorldEntityFlameClusters.Marker[] markers,
                 int sparkBase,
                 int labelBase) {
        int size() {
            return markers.length;
        }
    }
}
