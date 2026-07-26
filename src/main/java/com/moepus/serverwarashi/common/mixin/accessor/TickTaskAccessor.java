package com.moepus.serverwarashi.common.mixin.accessor;

import net.minecraft.server.TickTask;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = TickTask.class, remap = false)
public interface TickTaskAccessor {
    @Accessor("runnable")
    Runnable serverWarashi$getRunnable();
}
