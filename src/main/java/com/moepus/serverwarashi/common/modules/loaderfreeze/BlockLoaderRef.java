package com.moepus.serverwarashi.common.modules.loaderfreeze;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.Objects;

/** Identifies one controllable block loader across all of its tickets. */
public record BlockLoaderRef(
        ResourceKey<Level> dimension,
        ResourceLocation controllerId,
        BlockPos ownerPos
) {
    public BlockLoaderRef {
        dimension = Objects.requireNonNull(dimension, "dimension");
        controllerId = Objects.requireNonNull(controllerId, "controllerId");
        ownerPos = Objects.requireNonNull(ownerPos, "ownerPos").immutable();
    }

    public static BlockLoaderRef of(ServerLevel level, ResourceLocation controllerId, BlockPos ownerPos) {
        Objects.requireNonNull(level, "level");
        return new BlockLoaderRef(level.dimension(), controllerId, ownerPos);
    }
}
