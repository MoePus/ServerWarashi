package com.moepus.serverwarashi.client.event;

import com.moepus.serverwarashi.ServerWarashi;
import com.moepus.serverwarashi.client.compat.xaero.XaeroHeatmapOverlay;
import com.moepus.serverwarashi.client.compat.xaero.XaeroPerfControls;
import com.moepus.serverwarashi.client.network.PerfMapSubscriptionClient;
import com.moepus.serverwarashi.client.state.ClientPerfReportState;
import com.moepus.serverwarashi.client.state.ClientWeakChunkState;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.LoadingModList;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(value = Dist.CLIENT, modid = ServerWarashi.MODID)
public final class XaeroHeatmapClientEvents {
    private static final boolean XAERO_LOADED =
            LoadingModList.get().getModFileById("xaeroworldmap") != null;
    private static boolean mapOpen;

    private XaeroHeatmapClientEvents() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (Minecraft.getInstance().level != null) {
            ClientWeakChunkState.tick();
            ClientWeakChunkState.clearIfDimensionMismatch(
                    Minecraft.getInstance().level.dimension().location().toString());
        }
        boolean open = XAERO_LOADED && Minecraft.getInstance().level != null
                && XaeroHeatmapOverlay.isMapOpen(Minecraft.getInstance().screen);
        PerfMapSubscriptionClient.setMapOpen(open);
        if (open == mapOpen) {
            return;
        }
        mapOpen = open;
        if (!open) {
            ClientWeakChunkState.clear();
            XaeroHeatmapOverlay.close();
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        mapOpen = false;
        PerfMapSubscriptionClient.clear();
        ClientPerfReportState.clear();
        ClientWeakChunkState.clear();
        XaeroPerfControls.clear();
        if (XAERO_LOADED) {
            XaeroHeatmapOverlay.close();
        }
    }
}
