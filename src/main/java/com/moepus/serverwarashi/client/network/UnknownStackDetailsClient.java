package com.moepus.serverwarashi.client.network;

import com.moepus.serverwarashi.common.network.UnknownStackDetailsRequestPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

public final class UnknownStackDetailsClient {
    private UnknownStackDetailsClient() {
    }

    public static boolean canSend() {
        var connection = Minecraft.getInstance().getConnection();
        return connection != null && NetworkRegistry.hasChannel(
                connection, UnknownStackDetailsRequestPayload.TYPE.id());
    }

    public static boolean send(UnknownStackDetailsRequestPayload request) {
        if (!canSend()) {
            return false;
        }
        PacketDistributor.sendToServer(request);
        return true;
    }
}
