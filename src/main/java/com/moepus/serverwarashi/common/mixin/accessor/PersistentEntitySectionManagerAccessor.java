package com.moepus.serverwarashi.common.mixin.accessor;

import net.minecraft.world.level.entity.EntitySectionStorage;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = PersistentEntitySectionManager.class, remap = false)

public interface PersistentEntitySectionManagerAccessor {
    @Accessor
    EntitySectionStorage<?> getSectionStorage();
}
