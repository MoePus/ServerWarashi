package com.moepus.serverwarashi.common.mixin.compat.xaero;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import xaero.map.MapProcessor;
import xaero.map.gui.GuiMap;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

@Mixin(value = GuiMap.class, remap = false)
public interface XaeroGuiMapAccessor {
    @Accessor("cameraX")
    double serverwarashi$getCameraX();

    @Accessor("cameraZ")
    double serverwarashi$getCameraZ();

    @Accessor("scale")
    double serverwarashi$getScale();

    @Accessor("mapProcessor")
    MapProcessor serverwarashi$getMapProcessor();

    @Accessor("rightClickX")
    int serverwarashi$getRightClickX();

    @Accessor("rightClickZ")
    int serverwarashi$getRightClickZ();

    @Accessor("rightClickDim")
    ResourceKey<Level> serverwarashi$getRightClickDimension();
}
