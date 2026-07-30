package com.moepus.serverwarashi.client.network;

import com.moepus.serverwarashi.common.network.PerfMapSubscriptionPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

public final class PerfMapSubscriptionClient {
    private static boolean requestedOpen;
    private static boolean subscribed;

    private PerfMapSubscriptionClient() {
    }

    public static boolean setMapOpen(boolean open) {
        requestedOpen = open;
        if (subscribed == requestedOpen) {
            return false;
        }
        if (!send(requestedOpen)) {
            return false;
        }
        subscribed = requestedOpen;
        return true;
    }

    public static void clear() {
        requestedOpen = false;
        subscribed = false;
    }

    private static boolean send(boolean subscribed) {
        var connection = Minecraft.getInstance().getConnection();
        if (connection == null || !NetworkRegistry.hasChannel(
                connection, PerfMapSubscriptionPayload.TYPE.id())) {
            return false;
        }
        PacketDistributor.sendToServer(new PerfMapSubscriptionPayload(subscribed));
        return true;
    }
}
