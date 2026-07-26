package com.moepus.serverwarashi.common.mixin.ticktiming;

import com.moepus.serverwarashi.common.modules.TickTimingHooks;
import com.moepus.serverwarashi.common.modules.performance.sampling.PerfWorkTracker;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import org.mixinlite.injector.InjectLite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = ServerLevel.class, remap = false)
public abstract class ServerLevelChunkTickMixin {
    @InjectLite(method = "tickChunk(Lnet/minecraft/world/level/chunk/LevelChunk;I)V", at = @At("HEAD"))
    private void serverWarashi$beforeChunkTick(LevelChunk chunk, int randomTickSpeed) {
        if (!PerfWorkTracker.isProfiling) {
            return;
        }
        TickTimingHooks.beforeChunk((ServerLevel) (Object) this, chunk);
    }

    @InjectLite(method = "tickChunk(Lnet/minecraft/world/level/chunk/LevelChunk;I)V", at = @At("RETURN"))
    private void serverWarashi$afterChunkTick(LevelChunk chunk, int randomTickSpeed) {
        if (!PerfWorkTracker.isProfiling) {
            return;
        }
        TickTimingHooks.afterChunk();
    }
}
