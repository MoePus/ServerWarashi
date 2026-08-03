package com.moepus.serverwarashi.api.client.map;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/**
 * A chunk selected through a map integration.
 */
public record MapTarget(ResourceLocation dimension, int chunkX, int chunkZ) {
    public MapTarget {
        dimension = Objects.requireNonNull(dimension, "dimension");
    }
}
