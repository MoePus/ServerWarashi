package com.moepus.serverwarashi.common.mixin.ticktiming;

import com.moepus.serverwarashi.common.modules.performance.sampling.PerfWorkTracker;
import com.moepus.serverwarashi.common.modules.performance.sampling.TickType;
import net.minecraft.util.thread.BlockableEventLoop;
import org.mixinlite.injector.InjectLite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = BlockableEventLoop.class, remap = false)
public abstract class BlockableEventLoopTaskMixin {
    @InjectLite(method = "doRunTask(Ljava/lang/Runnable;)V", at = @At("HEAD"))
    private void serverWarashi$beforeTask(Runnable task) {
        if (!PerfWorkTracker.isProfiling) {
            return;
        }
        PerfWorkTracker.enterTask((BlockableEventLoop<?>) (Object) this, task);
    }

    @InjectLite(method = "doRunTask(Ljava/lang/Runnable;)V", at = @At("RETURN"))
    private void serverWarashi$afterTask(Runnable task) {
        if (!PerfWorkTracker.isProfiling) {
            return;
        }
        PerfWorkTracker.exit(TickType.TASK);
    }
}
