package com.moepus.serverwarashi.common.mixin.accessor;

import net.minecraft.util.ClassInstanceMultiMap;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntitySection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = EntitySection.class, remap = false)
public interface EntitySectionAccessor<T extends EntityAccess> {
    @Accessor("storage")
    ClassInstanceMultiMap<T> serverwarashi$getStorage();
}
