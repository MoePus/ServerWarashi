package com.moepus.serverwarashi.common.mixin.ticktiming;

import com.moepus.serverwarashi.common.modules.performance.sampling.PerfWorkTracker;
import com.moepus.serverwarashi.common.modules.performance.sampling.TickType;
import net.minecraft.util.thread.BlockableEventLoop;
import org.mixinlite.injector.MethodScope;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = BlockableEventLoop.class, remap = false)
public abstract class BlockableEventLoopTaskMixin {
    @MethodScope(
            method = "doRunTask(Ljava/lang/Runnable;)V",
            exit = "serverWarashi$exitTask",
            remap = false
    )
    private boolean serverWarashi$enterTask(Runnable task) {
        return PerfWorkTracker.isProfiling
                && PerfWorkTracker.enterTask((BlockableEventLoop<?>) (Object) this, task);
    }

    private void serverWarashi$exitTask(boolean entered) {
        if (entered) {
            PerfWorkTracker.exit(TickType.TASK);
        }
    }
}
