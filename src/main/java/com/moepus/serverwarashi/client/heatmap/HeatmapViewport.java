package com.moepus.serverwarashi.client.heatmap;

public record HeatmapViewport(int minX, int minZ, int width, int height) {
    public HeatmapViewport {
        if (width < 0 || height < 0) {
            throw new IllegalArgumentException("Viewport dimensions must be non-negative");
        }
    }

    public int maxX() {
        return minX + width;
    }

    public int maxZ() {
        return minZ + height;
    }

    public boolean contains(HeatmapViewport other) {
        return other.minX >= minX && other.minZ >= minZ
                && other.maxX() <= maxX() && other.maxZ() <= maxZ();
    }

    public boolean intersectsChunk(int chunkX, int chunkZ) {
        int chunkMinX = chunkX << 4;
        int chunkMinZ = chunkZ << 4;
        return chunkMinX < maxX() && chunkMinX + 16 > minX
                && chunkMinZ < maxZ() && chunkMinZ + 16 > minZ;
    }

    public boolean containsPoint(int x, int z) {
        return x >= minX && z >= minZ
                && (long) x < (long) minX + width
                && (long) z < (long) minZ + height;
    }

    public HeatmapViewport expandedBySelf() {
        return new HeatmapViewport(minX - width, minZ - height, width * 3, height * 3);
    }
}
