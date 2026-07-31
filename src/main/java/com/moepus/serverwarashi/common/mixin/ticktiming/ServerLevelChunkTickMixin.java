package com.moepus.serverwarashi.common.mixin.ticktiming;

import com.moepus.serverwarashi.common.modules.performance.sampling.PerfWorkTracker;
import com.moepus.serverwarashi.common.modules.performance.sampling.TickType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import org.mixinlite.injector.MethodScope;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = ServerLevel.class, remap = false)
public abstract class ServerLevelChunkTickMixin {
    @MethodScope(
            method = "tickChunk(Lnet/minecraft/world/level/chunk/LevelChunk;I)V",
            exit = "serverWarashi$exitChunkTick",
            remap = false
    )
    private boolean serverWarashi$enterChunkTick(LevelChunk chunk) {
        return PerfWorkTracker.isProfiling
                && PerfWorkTracker.enterChunk((ServerLevel) (Object) this, chunk);
    }

    private void serverWarashi$exitChunkTick(boolean entered) {
        if (entered) {
            PerfWorkTracker.exit(TickType.CHUNK);
        }
    }
}
