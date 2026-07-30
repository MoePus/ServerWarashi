package com.moepus.serverwarashi.common.mixin.accessor;

import net.minecraft.server.level.ChunkMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = ChunkMap.class, remap = false)
public interface ChunkMapAccessor {
    @Accessor("serverViewDistance")
    int getServerViewDistance();
}
