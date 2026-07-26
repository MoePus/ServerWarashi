package com.moepus.serverwarashi.common.mixin.ticktiming;

import com.moepus.serverwarashi.common.modules.TickTimingHooks;
import com.moepus.serverwarashi.common.modules.performance.sampling.PerfWorkTracker;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.mixinlite.injector.InjectLite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = ServerLevel.class, remap = false)
public abstract class ServerLevelEntityTickMixin {
    @InjectLite(method = "tickNonPassenger(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"))
    private void serverWarashi$beforeEntityTick(Entity entity) {
        if (!PerfWorkTracker.isProfiling) {
            return;
        }
        TickTimingHooks.beforeEntity((ServerLevel) (Object) this, entity);
    }

    @InjectLite(method = "tickNonPassenger(Lnet/minecraft/world/entity/Entity;)V", at = @At("RETURN"))
    private void serverWarashi$afterEntityTick(Entity entity) {
        if (!PerfWorkTracker.isProfiling) {
            return;
        }
        TickTimingHooks.afterEntity();
    }
}
