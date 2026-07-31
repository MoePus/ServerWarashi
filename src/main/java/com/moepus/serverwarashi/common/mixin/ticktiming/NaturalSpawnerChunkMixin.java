package com.moepus.serverwarashi.common.mixin.ticktiming;

import com.moepus.serverwarashi.common.modules.performance.sampling.PerfWorkTracker;
import com.moepus.serverwarashi.common.modules.performance.sampling.TickType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.chunk.LevelChunk;
import org.mixinlite.injector.MethodScope;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = NaturalSpawner.class, remap = false)
public abstract class NaturalSpawnerChunkMixin {
    @MethodScope(
            method = "spawnForChunk(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/LevelChunk;Lnet/minecraft/world/level/NaturalSpawner$SpawnState;ZZZ)V",
            exit = "serverWarashi$exitNaturalSpawn",
            remap = false
    )
    private static boolean serverWarashi$enterNaturalSpawn(ServerLevel level, LevelChunk chunk) {
        return PerfWorkTracker.isProfiling && PerfWorkTracker.enterChunk(level, chunk);
    }

    private static void serverWarashi$exitNaturalSpawn(boolean entered) {
        if (entered) {
            PerfWorkTracker.exit(TickType.CHUNK);
        }
    }
}
