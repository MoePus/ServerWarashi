package com.moepus.serverwarashi.common.mixin.runtime;

import com.moepus.serverwarashi.common.modules.performance.analyze.TicketPerfRuntime;
import com.moepus.serverwarashi.common.network.PerfReportDelivery;
import com.moepus.serverwarashi.common.network.WeakChunkDelivery;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import org.mixinlite.injector.InjectLite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.function.BooleanSupplier;

@Mixin(value = IntegratedServer.class, remap = false)
public abstract class IntegratedServerRuntimeMixin {
    @InjectLite(method = "tickServer(Ljava/util/function/BooleanSupplier;)V", at = @At("HEAD"))
    private void serverwarashi$beforeIntegratedTick(BooleanSupplier hasTimeLeft) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!minecraft.isPaused() || minecraft.getConnection() == null) {
            return;
        }
        IntegratedServer server = (IntegratedServer) (Object) this;
        TicketPerfRuntime.AutoReport report = TicketPerfRuntime.onServerPaused();
        WeakChunkDelivery.tick(server);
        if (report != null) {
            PerfReportDelivery.toPlayerOrConsole(server, report.playerId(), report.data());
            PerfReportDelivery.publishNow(server, report.data(), false);
            return;
        }
        PerfReportDelivery.tick(server, TicketPerfRuntime.currentReportOrNull(),
                TicketPerfRuntime.isProfiling());
    }
}
