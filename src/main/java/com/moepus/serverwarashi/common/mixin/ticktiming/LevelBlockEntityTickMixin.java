package com.moepus.serverwarashi.common.mixin.ticktiming;

import com.moepus.serverwarashi.common.modules.performance.sampling.PerfWorkTracker;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.mixinlite.injector.InjectLite;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "net.minecraft.world.level.chunk.LevelChunk$BoundTickingBlockEntity", remap = false)
public abstract class LevelBlockEntityTickMixin {
    @Shadow
    @Final
    private BlockEntity blockEntity;

    @InjectLite(
            method = "tick()V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/entity/BlockEntityTicker;tick(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/entity/BlockEntity;)V"
            )
    )
    private void serverWarashi$beforeBlockEntityTick() {
        if (!PerfWorkTracker.isProfiling) {
            return;
        }
        PerfWorkTracker.enterBlockEntity(blockEntity);
    }

    @InjectLite(
            method = "tick()V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/entity/BlockEntityTicker;tick(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/entity/BlockEntity;)V",
                    shift = At.Shift.AFTER
            )
    )
    private void serverWarashi$afterBlockEntityTick() {
        if (!PerfWorkTracker.isProfiling) {
            return;
        }
        PerfWorkTracker.exitBlockEntity(blockEntity);
    }
}
