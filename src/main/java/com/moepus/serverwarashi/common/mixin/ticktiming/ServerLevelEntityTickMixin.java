package com.moepus.serverwarashi.common.mixin.ticktiming;

import com.moepus.serverwarashi.common.modules.performance.sampling.PerfWorkTracker;
import com.moepus.serverwarashi.common.modules.performance.sampling.TickType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.mixinlite.injector.MethodScope;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = ServerLevel.class, remap = false)
public abstract class ServerLevelEntityTickMixin {
    @MethodScope(
            method = "tickNonPassenger(Lnet/minecraft/world/entity/Entity;)V",
            exit = "serverWarashi$exitEntityTick",
            remap = false
    )
    private boolean serverWarashi$enterEntityTick(Entity entity) {
        return PerfWorkTracker.isProfiling
                && PerfWorkTracker.enterEntity((ServerLevel) (Object) this, entity);
    }

    private void serverWarashi$exitEntityTick(boolean entered) {
        if (entered) {
            PerfWorkTracker.exit(TickType.ENTITY);
        }
    }
}
