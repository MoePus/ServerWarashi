package com.moepus.serverwarashi;

import com.moepus.serverwarashi.common.command.WarashiCommands;
import com.moepus.serverwarashi.common.modules.performance.analyze.TicketPerfRuntime;
import com.moepus.serverwarashi.common.modules.weakload.WeakChunkClearJobs;
import com.moepus.serverwarashi.common.network.PerfReportDelivery;
import com.moepus.serverwarashi.common.network.WeakChunkDelivery;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

@EventBusSubscriber(modid = ServerWarashi.MODID)
public final class ServerWarashiEvents {
    private ServerWarashiEvents() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        WarashiCommands.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        TicketPerfRuntime.clearRuntimeState();
        PerfReportDelivery.clearRuntimeState();
        WeakChunkDelivery.clearRuntimeState();
        WeakChunkClearJobs.clear();
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PerfReportDelivery.onLogout(player);
        }
    }
}
