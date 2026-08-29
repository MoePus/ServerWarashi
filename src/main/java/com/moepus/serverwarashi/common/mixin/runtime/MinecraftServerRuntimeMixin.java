package com.moepus.serverwarashi.common.mixin.runtime;

import com.moepus.serverwarashi.common.modules.performance.analyze.TicketPerfRuntime;
import com.moepus.serverwarashi.common.network.PerfReportDelivery;
import com.moepus.serverwarashi.common.network.WeakChunkDelivery;
import net.minecraft.server.MinecraftServer;
import org.mixinlite.injector.InjectLite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.function.BooleanSupplier;

@Mixin(value = MinecraftServer.class, remap = false)
public abstract class MinecraftServerRuntimeMixin {
    @InjectLite(method = "tickServer(Ljava/util/function/BooleanSupplier;)V", at = @At("HEAD"))
    private void serverwarashi$beginServerTick(BooleanSupplier hasTimeLeft) {
        MinecraftServer server = (MinecraftServer) (Object) this;
        pushCurrentReport(server);
        WeakChunkDelivery.tick(server);
        TicketPerfRuntime.onServerTickPre(server);
    }

    @InjectLite(method = "tickServer(Ljava/util/function/BooleanSupplier;)V", at = @At("RETURN"))
    private void serverwarashi$finishServerTick(BooleanSupplier hasTimeLeft) {
        MinecraftServer server = (MinecraftServer) (Object) this;
        TicketPerfRuntime.AutoReport report = TicketPerfRuntime.onServerTickPost(server);
        if (report == null) {
            pushCurrentReport(server);
            return;
        }
        PerfReportDelivery.toPlayerOrConsole(server, report.playerId(), report.data());
        TicketPerfRuntime.DeliverySnapshot snapshot = TicketPerfRuntime.currentDeliverySnapshot();
        PerfReportDelivery.publishNow(server, report.data(),
                snapshot.hotspots(), false);
    }

    private static void pushCurrentReport(MinecraftServer server) {
        TicketPerfRuntime.DeliverySnapshot snapshot = TicketPerfRuntime.currentDeliverySnapshot();
        PerfReportDelivery.tick(server, snapshot.report(), snapshot.hotspots(),
                TicketPerfRuntime.isProfiling());
    }
}
