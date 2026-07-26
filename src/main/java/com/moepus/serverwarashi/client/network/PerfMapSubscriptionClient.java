package com.moepus.serverwarashi.client.network;

import com.moepus.serverwarashi.common.network.PerfMapSubscriptionPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

public final class PerfMapSubscriptionClient {
    private static boolean mapOpen;

    private PerfMapSubscriptionClient() {
    }

    public static boolean setMapOpen(boolean open) {
        if (mapOpen == open) {
            return false;
        }
        mapOpen = open;
        send(open);
        return true;
    }

    public static void clear() {
        mapOpen = false;
    }

    private static void send(boolean subscribed) {
        var connection = Minecraft.getInstance().getConnection();
        if (connection == null || !NetworkRegistry.hasChannel(
                connection, PerfMapSubscriptionPayload.TYPE.id())) {
            return;
        }
        PacketDistributor.sendToServer(new PerfMapSubscriptionPayload(subscribed));
    }
}
