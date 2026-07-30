package com.moepus.serverwarashi.common.mixin.accessor;

import net.minecraft.util.ClassInstanceMultiMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;
import java.util.Map;

@Mixin(value = ClassInstanceMultiMap.class, remap = false)
public interface ClassInstanceMultiMapAccessor<T> {
    @Accessor("allInstances")
    List<T> serverwarashi$getAllInstances();

    @Accessor("byClass")
    Map<Class<?>, List<T>> serverwarashi$getByClass();
}
