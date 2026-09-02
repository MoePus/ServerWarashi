package com.moepus.serverwarashi.common.mixin.ticktiming;

import com.moepus.serverwarashi.common.modules.performance.sampling.PerfWorkTracker;
import com.moepus.serverwarashi.common.modules.performance.sampling.TickType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.mixinlite.injector.InjectLite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = ServerLevel.class, remap = false)
public abstract class ServerLevelEntityTickMixin {
    @InjectLite(
            method = "tickNonPassenger(Lnet/minecraft/world/entity/Entity;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;tick()V"
            ),
            remap = false
    )
    private void serverWarashi$enterEntityTick(Entity entity) {
        if (PerfWorkTracker.isProfiling) {
            PerfWorkTracker.enterEntity((ServerLevel) (Object) this, entity);
        }
    }

    @InjectLite(
            method = "tickNonPassenger(Lnet/minecraft/world/entity/Entity;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;tick()V",
                    shift = At.Shift.AFTER
            ),
            remap = false
    )
    private void serverWarashi$exitEntityTick(Entity entity) {
        if (PerfWorkTracker.isProfiling) {
            PerfWorkTracker.exit(TickType.ENTITY);
        }
    }

    @InjectLite(
            method = "tickPassenger(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;rideTick()V"
            ),
            remap = false
    )
    private void serverWarashi$enterPassengerTick(Entity vehicle, Entity passenger) {
        if (PerfWorkTracker.isProfiling) {
            PerfWorkTracker.enterEntity((ServerLevel) (Object) this, passenger);
        }
    }

    @InjectLite(
            method = "tickPassenger(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;rideTick()V",
                    shift = At.Shift.AFTER
            ),
            remap = false
    )
    private void serverWarashi$exitPassengerTick(Entity vehicle, Entity passenger) {
        if (PerfWorkTracker.isProfiling) {
            PerfWorkTracker.exit(TickType.ENTITY);
        }
    }
}
