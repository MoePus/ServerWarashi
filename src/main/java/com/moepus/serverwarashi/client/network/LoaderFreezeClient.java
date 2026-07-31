package com.moepus.serverwarashi.client.network;

import com.moepus.serverwarashi.common.network.LoaderFreezeActionPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

public final class LoaderFreezeClient {
    private LoaderFreezeClient() {
    }

    public static boolean canSend() {
        var connection = Minecraft.getInstance().getConnection();
        return connection != null
                && NetworkRegistry.hasChannel(connection, LoaderFreezeActionPayload.TYPE.id());
    }

    public static boolean send(LoaderFreezeActionPayload payload) {
        if (!canSend()) {
            return false;
        }
        PacketDistributor.sendToServer(payload);
        return true;
    }
}
