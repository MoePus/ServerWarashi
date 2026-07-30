package com.moepus.serverwarashi.common.mixin.runtime;

import com.moepus.serverwarashi.common.modules.performance.analyze.TicketPerfRuntime;
import com.moepus.serverwarashi.common.network.PerfReportDelivery;
import com.moepus.serverwarashi.common.network.WeakChunkDelivery;
import net.minecraft.client.server.IntegratedServer;
import org.mixinlite.injector.InjectLite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = IntegratedServer.class, remap = false)
public abstract class IntegratedServerRuntimeMixin {
    @InjectLite(method = "tickPaused()V", at = @At("RETURN"))
    private void serverwarashi$tickWhilePaused() {
        IntegratedServer server = (IntegratedServer) (Object) this;
        WeakChunkDelivery.tick(server);
        PerfReportDelivery.tick(server, TicketPerfRuntime.currentReportOrNull(),
                TicketPerfRuntime.isProfiling());
    }
}
