package com.moepus.serverwarashi.common.mixin.accessor;

import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntitySection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.world.level.entity.PersistentEntitySectionManager$Callback", remap = false)
public interface PersistentEntityCallbackAccessor {
    @Accessor("currentSection")
    EntitySection<? extends EntityAccess> serverwarashi$getCurrentSection();

    @Accessor("currentSection")
    void serverwarashi$setCurrentSection(EntitySection<? extends EntityAccess> section);
}
