package com.moepus.serverwarashi.common.mixin.chunkperf;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.moepus.serverwarashi.common.modules.performance.TicketPerfMixinHooks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.TickingBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Iterator;
import java.util.List;

/**
 * 在方块实体迭代器层做轻量包装，未分析时直接返回原始迭代器。
 */
@Mixin(value = Level.class, remap = false)

public abstract class LevelBlockEntityTickMixin {
    @WrapOperation(
            method = "tickBlockEntities",
            at = @At(value = "INVOKE", target = "Ljava/util/List;iterator()Ljava/util/Iterator;", ordinal = 0)
    )
    private Iterator<TickingBlockEntity> onBlockEntityIterator(List<TickingBlockEntity> tickers,
                                                               Operation<Iterator<TickingBlockEntity>> original) {
        // 把方块实体迭代器交给 hooks 包装（仅在分析会话期间才真正计时）
        Level level = (Level) (Object) this;
        if (!(level instanceof ServerLevel serverLevel)) {
            return original.call(tickers);
        }
        return TicketPerfMixinHooks.wrapBlockEntityIterator(serverLevel, original.call(tickers));
    }
}
