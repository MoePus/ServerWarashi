package com.moepus.serverwarashi.common.mixin.accessor;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityInLevelCallback;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = Entity.class, remap = false)
public interface EntityLevelCallbackAccessor {
    @Accessor("levelCallback")
    EntityInLevelCallback serverwarashi$getLevelCallback();
}
