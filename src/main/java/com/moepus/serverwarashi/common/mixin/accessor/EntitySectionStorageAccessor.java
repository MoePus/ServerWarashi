package com.moepus.serverwarashi.common.mixin.accessor;

import it.unimi.dsi.fastutil.longs.LongSortedSet;
import net.minecraft.world.level.entity.EntitySectionStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = EntitySectionStorage.class, remap = false)
public interface EntitySectionStorageAccessor {
    @Accessor("sectionIds")
    LongSortedSet serverwarashi$getSectionIds();
}
