package com.moepus.serverwarashi.common.mixin.accessor;

import net.minecraft.world.level.entity.ChunkEntities;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(value = ChunkEntities.class, remap = false)
public interface ChunkEntitiesAccessor<T> {
    @Accessor("entities")
    List<T> serverwarashi$getEntities();
}
