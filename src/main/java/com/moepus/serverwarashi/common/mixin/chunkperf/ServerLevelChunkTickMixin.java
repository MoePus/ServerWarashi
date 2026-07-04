package com.moepus.serverwarashi.common.mixin.chunkperf;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.moepus.serverwarashi.common.modules.performance.TicketPerfMixinHooks;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 在区块 tick 调用点做 branch，避免在 ServerLevel.tickChunk 内部维持共享状态。
 */
@Mixin(value = ServerChunkCache.class, remap = false)

public abstract class ServerLevelChunkTickMixin {
    @WrapOperation(
            method = "tickChunks",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerLevel;tickChunk(Lnet/minecraft/world/level/chunk/LevelChunk;I)V"
            )
    )
    private void onTickChunk(ServerLevel level, LevelChunk chunk, int randomTickSpeed, Operation<Void> original) {
        // 区块 tick 入口分流：交给 hooks 决定走成本采样 / 分析计时 / 原始调用
        TicketPerfMixinHooks.profileChunkTick(
                level,
                chunk,
                randomTickSpeed,
                original::call
        );
    }
}
