package com.moepus.serverwarashi.api.client.map;

import java.util.Objects;

/**
 * A visible rectangle in world block coordinates.
 */
public record MapViewport(Origin origin, Size size) {
    public MapViewport {
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(size, "size");
    }

    public static Origin at(int minX, int minZ) {
        return new Origin(minX, minZ);
    }

    public int minX() {
        return origin.x();
    }

    public int minZ() {
        return origin.z();
    }

    public int width() {
        return size.width();
    }

    public int height() {
        return size.height();
    }

    public int maxX() {
        return minX() + width();
    }

    public int maxZ() {
        return minZ() + height();
    }

    public boolean contains(MapViewport other) {
        return other.minX() >= minX() && other.minZ() >= minZ()
                && other.maxX() <= maxX() && other.maxZ() <= maxZ();
    }

    public boolean intersectsChunk(int chunkX, int chunkZ) {
        int chunkMinX = chunkX << 4;
        int chunkMinZ = chunkZ << 4;
        return chunkMinX < maxX() && chunkMinX + 16 > minX()
                && chunkMinZ < maxZ() && chunkMinZ + 16 > minZ();
    }

    public boolean containsPoint(int x, int z) {
        return x >= minX() && z >= minZ()
                && (long) x < (long) minX() + width()
                && (long) z < (long) minZ() + height();
    }

    public MapViewport expandedBySelf() {
        return new MapViewport(
                new Origin(minX() - width(), minZ() - height()),
                new Size(width() * 3, height() * 3));
    }

    public record Origin(int x, int z) {
        public MapViewport withSize(int width, int height) {
            return new MapViewport(this, new Size(width, height));
        }
    }

    public record Size(int width, int height) {
        public Size {
            if (width < 0 || height < 0) {
                throw new IllegalArgumentException("Viewport dimensions must be non-negative");
            }
        }
    }
}
