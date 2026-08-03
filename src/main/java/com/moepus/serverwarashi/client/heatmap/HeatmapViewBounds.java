package com.moepus.serverwarashi.client.heatmap;

import com.moepus.serverwarashi.api.client.map.MapViewport;

record HeatmapViewBounds(int minX, int minZ, int maxX, int maxZ) {
    static HeatmapViewBounds from(MapViewport viewport) {
        if (viewport.width() == 0 || viewport.height() == 0) {
            return new HeatmapViewBounds(0, 0, -1, -1);
        }
        return new HeatmapViewBounds(Math.floorDiv(viewport.minX(), 16),
                Math.floorDiv(viewport.minZ(), 16),
                Math.floorDiv(viewport.maxX() - 1, 16),
                Math.floorDiv(viewport.maxZ() - 1, 16));
    }

    boolean valid() {
        return minX <= maxX && minZ <= maxZ;
    }
}
