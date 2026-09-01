package com.moepus.serverwarashi.client.event;

import com.moepus.serverwarashi.ServerWarashi;
import com.moepus.serverwarashi.api.client.map.MapOverlayApi;
import com.moepus.serverwarashi.api.client.map.MapOverlayControls;
import com.moepus.serverwarashi.client.map.MapContextMenuService;
import com.moepus.serverwarashi.client.map.MapOverlayRenderer;
import com.moepus.serverwarashi.client.network.PerfMapSubscriptionClient;
import com.moepus.serverwarashi.client.state.ClientPerfMapState;
import com.moepus.serverwarashi.client.state.ClientPerfStateCoordinator;
import com.moepus.serverwarashi.client.screen.UnknownStackCopyController;
import com.moepus.serverwarashi.client.state.ClientWeakChunkState;
import com.moepus.serverwarashi.client.world.WorldPerfOverlayRenderer;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(value = Dist.CLIENT, modid = ServerWarashi.MODID)
public final class MapOverlayClientEvents {
    private static boolean mapOpen;

    private MapOverlayClientEvents() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        PerfMapSubscriptionClient.refreshWorldOverlayEnabled();
        if (minecraft.level != null) {
            ClientWeakChunkState.tick();
            ClientWeakChunkState.clearIfDimensionMismatch(
                    minecraft.level.dimension().location().toString());
        }
        boolean open = minecraft.level != null
                && MapOverlayApi.isAnyMapOpen(minecraft.screen);
        PerfMapSubscriptionClient.setMapOpen(open);
        if (open == mapOpen) {
            return;
        }
        mapOpen = open;
        if (!open) {
            closeSession();
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        mapOpen = false;
        PerfMapSubscriptionClient.clear();
        ClientPerfStateCoordinator.clearAll();
        UnknownStackCopyController.clearRuntimeState();
        ClientWeakChunkState.clear();
        WorldPerfOverlayRenderer.close();
        MapOverlayControls.clear();
        closeSession();
    }

    private static void closeSession() {
        var report = ClientPerfMapState.snapshot().report();
        if (report == null || report.tickCount() == 0L) {
            ClientWeakChunkState.clear();
        }
        MapContextMenuService.clear();
        MapOverlayRenderer.close();
    }
}
