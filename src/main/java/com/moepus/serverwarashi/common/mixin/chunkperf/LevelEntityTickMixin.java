package com.moepus.serverwarashi.common.mixin.chunkperf;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.moepus.serverwarashi.common.modules.performance.TicketPerfMixinHooks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.function.Consumer;

/**
 * 在真实实体 tick 调用点包装 Consumer，避免常驻共享状态对象。
 */
@Mixin(value = ServerLevel.class, remap = false)

public abstract class LevelEntityTickMixin {
    @WrapOperation(
            method = "lambda$tick$2(Lnet/minecraft/world/TickRateManager;Lnet/minecraft/util/profiling/ProfilerFiller;Lnet/minecraft/world/entity/Entity;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerLevel;guardEntityTick(Ljava/util/function/Consumer;Lnet/minecraft/world/entity/Entity;)V"
            )
    )
    private void onGuardEntityTick(ServerLevel level,
                                   Consumer<Entity> consumer,
                                   Entity entity,
                                   Operation<Void> original) {
        // 把实体 tick 的 Consumer 交给 hooks 包装后再执行（仅分析期间计时）
        original.call(level, TicketPerfMixinHooks.wrapEntityTickConsumer(level, consumer, entity), entity);
    }
}
