package com.moepus.serverwarashi.client.network;

import com.moepus.serverwarashi.client.config.ClientConfig;
import com.moepus.serverwarashi.common.network.PerfMapSubscriptionPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

public final class PerfMapSubscriptionClient {
    private static final PerfMapSubscriptionState STATE =
            new PerfMapSubscriptionState(PerfMapSubscriptionClient::send);

    private PerfMapSubscriptionClient() {
    }

    public static boolean setMapOpen(boolean open) {
        return STATE.setMapOpen(open);
    }

    public static boolean setAnalysisLive(boolean live) {
        return STATE.setAnalysisLive(live);
    }

    public static boolean refreshWorldOverlayEnabled() {
        return STATE.setWorldOverlayEnabled(ClientConfig.worldOverlayEnabled());
    }

    public static void clear() {
        STATE.clear();
    }

    private static boolean send(PerfMapSubscriptionState.Request request) {
        var connection = Minecraft.getInstance().getConnection();
        if (connection == null || !NetworkRegistry.hasChannel(
                connection, PerfMapSubscriptionPayload.TYPE.id())) {
            return false;
        }
        PacketDistributor.sendToServer(new PerfMapSubscriptionPayload(
                request.mapOpen(), request.worldHotspots()));
        return true;
    }
}
