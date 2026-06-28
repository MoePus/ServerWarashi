package com.moepus.serverwarashi.common.mixin.accessor;

import it.unimi.dsi.fastutil.longs.LongSortedSet;
import net.minecraft.world.level.entity.EntitySectionStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = EntitySectionStorage.class, remap = false)
@SuppressWarnings("unused")
public interface EntitySectionStorageAccessor {
    @Invoker
    LongSortedSet invokeGetChunkSections(int x, int z);
}
